import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import YAML from 'yaml';

test('VPS deployment persists database and avatars without opening PostgreSQL',async()=>{
  const compose=YAML.parse(await readFile(new URL('../compose.yaml',import.meta.url),'utf8'));
  assert.equal(compose.services.postgres.ports,undefined);
  assert.deepEqual(compose.services.app.ports,['127.0.0.1:3000:3000']);
  assert.ok(compose.services.app.volumes.includes('avatar-data:/app/storage/avatars'));
  assert.ok(Object.hasOwn(compose.volumes,'avatar-data'));
  assert.equal(compose.services.app.environment.MEDIA_DRIVER,'${MEDIA_DRIVER:-local}');
  const dockerfile=await readFile(new URL('../Dockerfile',import.meta.url),'utf8');
  assert.ok(dockerfile.includes('chown -R node:node /app/storage'));
  assert.ok(dockerfile.includes('USER node'));
});
