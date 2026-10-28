import {createHash, randomBytes, randomUUID} from 'node:crypto';
import jwt from 'jsonwebtoken';
import {query, transaction} from './db.js';

const accessSeconds = 15 * 60;
const refreshSeconds = 30 * 24 * 60 * 60;
const production = process.env.NODE_ENV === 'production';
const issuer = 'toeic-studio';
const audience = 'toeic-app';
const cookieOptions = {httpOnly: true, secure: production, sameSite: 'strict'};

if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 32) {
  throw new Error('JWT_SECRET must contain at least 32 characters');
}

export const publicUser = user => ({
  id: user.id,
  email: user.email,
  full_name: user.full_name,
  avatar_url: user.avatar_url,
  role: user.role,
  status: user.status,
  target_score: user.target_score
});

const hashToken = token => createHash('sha256').update(token).digest('hex');
const newRefreshToken = () => randomBytes(48).toString('base64url');

function tokens(user, sessionId, refreshToken, expiresAt) {
  const accessToken = jwt.sign(
    {sub: String(user.id), sid: sessionId, type: 'access'},
    process.env.JWT_SECRET,
    {algorithm: 'HS256', expiresIn: accessSeconds, issuer, audience}
  );

  return {
    token: accessToken,
    access_token: accessToken,
    refresh_token: refreshToken,
    token_type: 'Bearer',
    expires_in: accessSeconds,
    refresh_expires_in: Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000)),
    user: publicUser(user)
  };
}

export async function createSession(user, db) {
  const sessionId = randomUUID();
  const refreshToken = newRefreshToken();
  const [session] = await query(
    'INSERT INTO auth_sessions(id,user_id,expires_at) VALUES(?,?,now()+make_interval(secs=>?::int)) RETURNING expires_at',
    [sessionId, user.id, refreshSeconds], db
  );

  await query(
    'INSERT INTO auth_refresh_tokens(token_hash,session_id) VALUES(?,?)',
    [hashToken(refreshToken), sessionId], db
  );
  await query('UPDATE users SET last_login_at=now() WHERE id=?', [user.id], db);
  return tokens(user, sessionId, refreshToken, session.expires_at);
}

export async function rotateSession(refreshToken) {
  return transaction(async db => {
    const [candidate] = await query(
      'SELECT s.user_id FROM auth_sessions s JOIN auth_refresh_tokens t ON t.session_id=s.id WHERE t.token_hash=?',
      [hashToken(refreshToken)], db
    );
    if (!candidate) return null;
    const [user] = await query('SELECT * FROM users WHERE id=? FOR UPDATE', [candidate.user_id], db);
    const [session] = await query(
      'SELECT *,expires_at>now() AS active FROM auth_sessions WHERE id=(SELECT session_id FROM auth_refresh_tokens WHERE token_hash=?) FOR UPDATE',
      [hashToken(refreshToken)], db
    );
    if (!session || session.revoked_at || !session.active) return null;
    const [stored] = await query('SELECT used_at FROM auth_refresh_tokens WHERE token_hash=?',[hashToken(refreshToken)],db);
    if (stored.used_at || !user || user.status !== 'ACTIVE') {
      await query('UPDATE auth_sessions SET revoked_at=now() WHERE id=?', [session.id], db);
      return null;
    }

    const nextToken = newRefreshToken();
    await query('UPDATE auth_refresh_tokens SET used_at=now() WHERE token_hash=?', [hashToken(refreshToken)], db);
    await query(
      'INSERT INTO auth_refresh_tokens(token_hash,session_id) VALUES(?,?)',
      [hashToken(nextToken), session.id], db
    );
    return tokens(user, session.id, nextToken, session.expires_at);
  });
}

export function readAccessToken(req) {
  const header = req.get('authorization');
  if (header) {
    const match = /^Bearer ([^\s]+)$/i.exec(header);
    return match ? match[1] : null;
  }
  return req.cookies.token;
}

export function verifyAccessToken(token, ignoreExpiration = false) {
  const payload = jwt.verify(token, process.env.JWT_SECRET, {
    algorithms: ['HS256'], issuer, audience, ignoreExpiration
  });
  if (payload.type !== 'access' || typeof payload.sid !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(payload.sid) || !/^[1-9]\d*$/.test(payload.sub)) {
    throw new Error('Invalid access token');
  }
  return payload;
}

export async function auth(req, res, next) {
  let payload;
  try {
    payload = verifyAccessToken(readAccessToken(req));
  } catch {
    return res.status(401).json({error: 'Vui lòng đăng nhập lại'});
  }

  try {
    const [user] = await query(
      `SELECT u.id,u.email,u.full_name,u.avatar_url,u.role,u.status,u.target_score
       FROM users u JOIN auth_sessions s ON s.user_id=u.id
       WHERE s.id=? AND u.id=? AND s.revoked_at IS NULL AND s.expires_at>now() AND u.status='ACTIVE'`,
      [payload.sid, payload.sub]
    );
    if (!user) return res.status(401).json({error: 'Phiên đăng nhập không hợp lệ'});
    req.user = user;
    req.sessionId = payload.sid;
    next();
  } catch (error) {
    next(error);
  }
}

export async function revokeSession(req, refreshToken) {
  if (refreshToken) {
    await query(
      `UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,now())
       WHERE id=(SELECT session_id FROM auth_refresh_tokens WHERE token_hash=?)`,
      [hashToken(refreshToken)]
    );
    return;
  }

  let payload;
  try {
    payload = verifyAccessToken(readAccessToken(req), true);
  } catch {
    return;
  }
  await query(
    'UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE id=? AND user_id=?',
    [payload.sid, payload.sub]
  );
}

export function sendSession(res, session, status = 200, extra = {}) {
  res.cookie('token', session.access_token, {...cookieOptions, path: '/', maxAge: accessSeconds * 1000});
  res.cookie('refresh_token', session.refresh_token, {
    ...cookieOptions, path: '/api/auth', maxAge: session.refresh_expires_in * 1000
  });
  res.set('Cache-Control','no-store').status(status).json({...session, ...extra});
}

export function clearSessionCookies(res) {
  res.clearCookie('token', {...cookieOptions, path: '/'});
  res.clearCookie('refresh_token', {...cookieOptions, path: '/api/auth'});
}
