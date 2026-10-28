import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import YAML from 'yaml';

test('OpenAPI and compose configuration parse and describe the account endpoints',async()=>{
  const doc=YAML.parse(await readFile(new URL('../../docs/openapi.yaml',import.meta.url),'utf8'));
  for(const route of ['/auth/password/forgot','/auth/password/verify','/auth/password/reset','/auth/me/avatar']){
    assert.ok(doc.paths[route]?.post,route);
  }
  assert.ok(doc.paths['/auth/me'].put);
  const pattern=doc.paths['/auth/password/verify'].post.requestBody.content['application/json'].schema.properties.otp.pattern;
  assert.equal(new RegExp(pattern).test('123456'),true);
  const compose=YAML.parse(await readFile(new URL('../compose.yaml',import.meta.url),'utf8'));
  assert.ok(compose.services.app.environment.SMTP_HOST);
  assert.ok(compose.services.app.environment.S3_BUCKET);
  assert.ok(compose.services.app.healthcheck);
  const https=YAML.parse(await readFile(new URL('../compose.https.yaml',import.meta.url),'utf8'));
  assert.ok(https.services.caddy.ports.includes('443:443'));
});
