import {test} from 'node:test';
import assert from 'node:assert/strict';
import {api} from '../frontend/api.js';

const response = (status, body = {}) => ({
  status, ok: status >= 200 && status < 300, json: async () => body
});

test('admin retries after refresh and sends the browser CSRF header', async t => {
  const original = globalThis.fetch;
  t.after(() => {globalThis.fetch = original;});
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({url, options});
    if (calls.length === 1) return response(401, {error: 'Expired'});
    if (url === '/api/auth/refresh') return response(200);
    return response(200, {id: 1});
  };
  assert.deepEqual(await api('/auth/me'), {id: 1});
  assert.deepEqual(calls.map(call => call.url), ['/api/auth/me', '/api/auth/refresh', '/api/auth/me']);
  assert.ok(calls.every(call => call.options.headers['X-Requested-With'] === 'TOEICAdmin'));
});

test('concurrent admin requests share a single pending refresh', async t => {
  const original = globalThis.fetch;
  t.after(() => {globalThis.fetch = original;});
  let refreshCalls = 0;
  let refreshed = false;
  globalThis.fetch = async url => {
    if (url === '/api/auth/refresh') {
      refreshCalls++;
      await new Promise(resolve => setTimeout(resolve, 10));
      refreshed = true;
      return response(200);
    }
    return refreshed ? response(200, {ok: true}) : response(401, {error: 'Expired'});
  };
  const results = await Promise.all([api('/auth/me'), api('/admin/meta')]);
  assert.equal(refreshCalls, 1);
  assert.ok(results.every(result => result.ok));
});

test('failed login and failed refresh do not create retry loops', async t => {
  const original = globalThis.fetch;
  t.after(() => {globalThis.fetch = original;});
  const calls = [];
  globalThis.fetch = async url => {
    calls.push(url);
    return response(401, {error: 'Denied'});
  };
  await assert.rejects(api('/auth/login', 'POST', {}), /Denied/);
  assert.deepEqual(calls, ['/api/auth/login']);
  calls.length = 0;
  await assert.rejects(api('/auth/me'), /Denied/);
  assert.deepEqual(calls, ['/api/auth/me', '/api/auth/refresh']);
});
