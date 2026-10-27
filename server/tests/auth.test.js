import {test, after} from 'node:test';
import assert from 'node:assert/strict';
import {createHash, randomUUID} from 'node:crypto';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import {app} from '../backend/app.js';
import {pool, query} from '../backend/db.js';

const api = request(app);
after(() => pool.end());

test('B1 authentication and session lifecycle', async t => {
  const email = 'auth-' + randomUUID() + '@example.com';
  const password = 'Local-test-password-' + randomUUID();
  const login = () => api.post('/api/auth/login').send({email, password});
  const me = token => api.get('/api/auth/me').auth(token, {type: 'bearer'});
  const refresh = token => api.post('/api/auth/refresh').send({refresh_token: token});

  try {
    await t.test('health endpoints reach PostgreSQL', async () => {
      for (const path of ['/health', '/api/health']) {
        const response = await api.get(path);
        assert.equal(response.status, 200);
        assert.equal(response.body.database, 'postgresql');
      }
    });

    await t.test('registration validates input and always creates a student', async () => {
      assert.equal((await api.post('/api/auth/register').send({email, password: 'short', full_name: 'Test'})).status, 422);
      const registered = await api.post('/api/auth/register').send({email, password, full_name: 'Auth Learner', role: 'ADMIN'});
      assert.equal(registered.status, 201);
      assert.equal((await api.post('/api/auth/register').send({email, password, full_name: 'Duplicate'})).status, 409);
      const [user] = await query('SELECT role,password_hash FROM users WHERE email=?', [email]);
      assert.equal(user.role, 'STUDENT');
      assert.notEqual(user.password_hash, password);
    });

    await t.test('login returns backward compatible tokens and a hashed refresh token', async () => {
      assert.equal((await api.post('/api/auth/login').send({email, password: 'Wrong-password-12345'})).status, 401);
      const response = await login();
      assert.equal(response.status, 200);
      const body = response.body;
      assert.equal(body.token, body.access_token);
      assert.equal(body.token_type, 'Bearer');
      assert.equal(body.expires_in, 900);
      assert.match(body.refresh_token, /^[A-Za-z0-9_-]{64}$/);
      assert.ok(!('password_hash' in body.user));
      const cookies = response.headers['set-cookie'];
      assert.ok(cookies.every(cookie => cookie.includes('HttpOnly') && cookie.includes('SameSite=Strict')));
      const hash = createHash('sha256').update(body.refresh_token).digest('hex');
      const [stored] = await query('SELECT token_hash FROM auth_refresh_tokens WHERE token_hash=?', [hash]);
      assert.equal(stored.token_hash, hash);
      assert.notEqual(stored.token_hash, body.refresh_token);
      assert.equal((await me(body.token)).status, 200);
      assert.equal((await api.get('/api/admin/dashboard').auth(body.token, {type: 'bearer'})).status, 403);
    });

    await t.test('refresh rotates the token and reuse revokes the whole session', async () => {
      const first = (await login()).body;
      const second = await refresh(first.refresh_token);
      assert.equal(second.status, 200);
      assert.notEqual(second.body.refresh_token, first.refresh_token);
      assert.equal((await me(second.body.token)).status, 200);
      assert.equal((await refresh(first.refresh_token)).status, 401);
      assert.equal((await me(first.token)).status, 401);
      assert.equal((await me(second.body.token)).status, 401);
      assert.equal((await refresh(second.body.refresh_token)).status, 401);
    });

    await t.test('logout revokes access and refresh only for the current session', async () => {
      const first = (await login()).body;
      const second = (await login()).body;
      assert.equal((await api.post('/api/auth/logout').auth(first.token, {type: 'bearer'})).status, 204);
      assert.equal((await me(first.token)).status, 401);
      assert.equal((await refresh(first.refresh_token)).status, 401);
      assert.equal((await me(second.token)).status, 200);
      assert.equal((await api.post('/api/auth/logout').send({refresh_token: second.refresh_token})).status, 204);
      assert.equal((await me(second.token)).status, 401);
      assert.equal((await api.post('/api/auth/logout')).status, 204);
    });

    await t.test('an expired access token can be refreshed but cannot authenticate', async () => {
      const session = (await login()).body;
      const claims = jwt.decode(session.token);
      const expired = jwt.sign({sub: claims.sub, sid: claims.sid, type: 'access'}, process.env.JWT_SECRET, {
        expiresIn: -1, issuer: 'toeic-studio', audience: 'toeic-app', algorithm: 'HS256'
      });
      assert.equal((await me(expired)).status, 401);
      const next = await refresh(session.refresh_token);
      assert.equal(next.status, 200);
      assert.equal((await me(next.body.token)).status, 200);
      assert.equal((await api.post('/api/auth/logout').auth(expired, {type: 'bearer'})).status, 204);
      assert.equal((await me(next.body.token)).status, 401);
    });

    await t.test('expired sessions and locked users cannot refresh', async () => {
      const expired = (await login()).body;
      const sid = jwt.decode(expired.token).sid;
      await query("UPDATE auth_sessions SET created_at=now()-interval '2 days',expires_at=now()-interval '1 day' WHERE id=?", [sid]);
      assert.equal((await me(expired.token)).status, 401);
      assert.equal((await refresh(expired.refresh_token)).status, 401);

      const locked = (await login()).body;
      await query("UPDATE users SET status='LOCKED' WHERE email=?", [email]);
      assert.equal((await me(locked.token)).status, 401);
      assert.equal((await refresh(locked.refresh_token)).status, 401);
      assert.equal((await login()).status, 401);
      await query("UPDATE users SET status='ACTIVE' WHERE email=?", [email]);
      assert.equal((await me(locked.token)).status, 401);
    });

    await t.test('browser cookies support refresh and logout with CSRF protection', async () => {
      const browser = request.agent(app);
      const loggedIn = await browser.post('/api/auth/login').send({email, password});
      assert.equal(loggedIn.status, 200);
      assert.equal((await browser.post('/api/auth/refresh')).status, 403);
      const rotated = await browser.post('/api/auth/refresh').set('X-Requested-With', 'TOEICAdmin');
      assert.equal(rotated.status, 200);
      assert.equal((await browser.post('/api/auth/logout')).status, 403);
      assert.equal((await browser.post('/api/auth/logout').set('X-Requested-With', 'TOEICAdmin')).status, 204);
      assert.equal((await browser.get('/api/auth/me')).status, 401);
      assert.equal((await me(rotated.body.token)).status, 401);
    });

    await t.test('concurrent refresh cannot issue two usable sessions', async () => {
      const session = (await login()).body;
      const results = await Promise.all([refresh(session.refresh_token), refresh(session.refresh_token)]);
      assert.deepEqual(results.map(result => result.status).sort(), [200, 401]);
      const accepted = results.find(result => result.status === 200).body;
      assert.equal((await me(accepted.token)).status, 401);
    });

    await t.test('invalid credentials never become database identifiers', async () => {
      assert.equal((await api.get('/api/auth/me')).status, 401);
      assert.equal((await me('invalid')).status, 401);
      assert.equal((await refresh('x'.repeat(64))).status, 401);
      assert.equal((await refresh('invalid')).status, 422);
      assert.equal((await api.post('/api/auth/refresh')).status, 401);
    });
  } finally {
    await query('DELETE FROM users WHERE email=?', [email]);
  }
});
