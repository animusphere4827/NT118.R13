import {hashPassword, verifyPassword} from './passwords.js';
import rateLimit from 'express-rate-limit';
import {z} from 'zod';
import {query, insert, transaction} from './db.js';
import {verifyFirebaseIdToken, firebaseConfigured} from './firebase.js';
import {
  createSession, rotateSession, revokeSession, sendSession, clearSessionCookies
} from './sessions.js';

const authLimit = rateLimit({windowMs: 15 * 60000, limit: 30});
const refreshLimit = rateLimit({windowMs: 15 * 60000, limit: 60});
const credentials = z.object({
  email: z.email().max(254).transform(value => value.toLowerCase()),
  password: z.string().min(12).max(128)
});
const refreshBody = z.object({
  refresh_token: z.string().regex(/^[A-Za-z0-9_-]{64}$/).optional()
}).strict();

function fail(status, message) {
  throw Object.assign(new Error(message), {status});
}

export function registerAuthRoutes(app) {
  app.post('/api/auth/register', authLimit, async (req, res) => {
    const body = credentials.extend({
      full_name: z.string().trim().min(1).max(150)
    }).parse(req.body);
    const passwordHash = await hashPassword(body.password);
    const id = await insert(
      'INSERT INTO users(email,password_hash,full_name) VALUES(?,?,?)',
      [body.email, passwordHash, body.full_name]
    );
    res.status(201).json({id});
  });

  app.post('/api/auth/login', authLimit, async (req, res) => {
    const body = credentials.parse(req.body);
    const [user] = await query('SELECT * FROM users WHERE email=?', [body.email]);
    const valid = await verifyPassword(body.password, user?.password_hash);
    if (!user || !valid || user.status !== 'ACTIVE') fail(401, 'Email hoặc mật khẩu không đúng');

    const session = await transaction(async db => {
      const [current] = await query('SELECT * FROM users WHERE id=? FOR UPDATE', [user.id], db);
      if (!current || current.status !== 'ACTIVE' || current.password_hash !== user.password_hash) {
        fail(401, 'Email hoặc mật khẩu không đúng');
      }
      return createSession(current, db);
    });
    sendSession(res, session);
  });

  app.post('/api/auth/firebase', authLimit, async (req, res) => {
    if (!firebaseConfigured()) fail(503, 'Máy chủ chưa cấu hình Firebase');
    const {id_token} = z.object({id_token: z.string().min(20).max(8192)}).strict().parse(req.body);

    let claims;
    try {
      claims = await verifyFirebaseIdToken(id_token);
    } catch {
      fail(401, 'Token Google không hợp lệ hoặc đã hết hạn');
    }
    if (claims.firebase?.sign_in_provider !== 'google.com') {
      fail(401, 'Chỉ hỗ trợ đăng nhập Google qua Firebase');
    }
    const parsedEmail = z.email().max(254).safeParse(claims.email);
    if (!parsedEmail.success || claims.email_verified !== true) {
      fail(401, 'Email Google chưa được xác minh');
    }

    const email = parsedEmail.data.toLowerCase();
    const picture = typeof claims.picture === 'string' &&
      /^https:\/\//.test(claims.picture) && claims.picture.length <= 2048 ? claims.picture : null;
    const name = (typeof claims.name === 'string' && claims.name.trim()
      ? claims.name.trim() : email.split('@')[0]).slice(0, 150);

    const result = await transaction(async db => {
      await query('SELECT pg_advisory_xact_lock(hashtext(?))', [email], db);
      let [user] = await query('SELECT * FROM users WHERE firebase_uid=? FOR UPDATE', [claims.sub], db);
      let created = false;

      if (!user) {
        [user] = await query('SELECT * FROM users WHERE email=? FOR UPDATE', [email], db);
        if (user) {
          if (user.firebase_uid) fail(409, 'Email này đã liên kết với một tài khoản Google khác');
          if (user.role === 'ADMIN') fail(409, 'Tài khoản quản trị chỉ đăng nhập bằng mật khẩu');
          [user] = await query(
            'UPDATE users SET firebase_uid=?,avatar_url=COALESCE(avatar_url,?) WHERE id=? RETURNING *',
            [claims.sub, picture, user.id], db
          );
        } else {
          [user] = await query(
            'INSERT INTO users(email,firebase_uid,full_name,avatar_url) VALUES(?,?,?,?) RETURNING *',
            [email, claims.sub, name, picture], db
          );
          created = true;
        }
      }
      if (user.role === 'ADMIN') fail(409, 'Tài khoản quản trị chỉ đăng nhập bằng mật khẩu');
      if (user.status !== 'ACTIVE') fail(401, 'Tài khoản đã bị khóa hoặc vô hiệu hóa');
      return {session: await createSession(user, db), created};
    });
    sendSession(res, result.session, result.created ? 201 : 200, {is_new_user: result.created});
  });

  app.post('/api/auth/refresh', refreshLimit, async (req, res) => {
    const body = refreshBody.parse(req.body ?? {});
    const token = body.refresh_token ?? req.cookies.refresh_token;
    if (!token || !refreshBody.shape.refresh_token.safeParse(token).success) {
      fail(401, 'Cần refresh token hợp lệ');
    }
    const session = await rotateSession(token);
    if (!session) {
      clearSessionCookies(res);
      fail(401, 'Phiên đăng nhập đã hết hạn hoặc bị thu hồi');
    }
    sendSession(res, session);
  });

  app.post('/api/auth/logout', async (req, res) => {
    const body = refreshBody.parse(req.body ?? {});
    const token = body.refresh_token ?? (req.get('authorization') ? undefined : req.cookies.refresh_token);
    await revokeSession(req, token);
    clearSessionCookies(res);
    res.sendStatus(204);
  });
}
