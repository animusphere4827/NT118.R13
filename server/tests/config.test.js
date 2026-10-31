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
  for(const route of ['/learn/path','/learn/path/lessons/{id}','/learn/readings','/learn/readings/{id}']) {
    assert.ok(doc.paths[route]?.get,route);
  }
  assert.ok(doc.paths['/learn/readings/{id}/progress'].put);
  assert.ok(doc.paths['/learn/readings/{id}/lookups'].post);
  assert.ok(doc.components.schemas.CourseLesson.required.includes('content_id'));
  assert.ok(doc.components.schemas.ReadingSummary.properties.completed_at.nullable);
  assert.ok(doc.paths['/learn/{kind}/attempts/{id}/answers'].patch);
  for(const route of ['/learn/quizzes','/learn/quizzes/{id}','/learn/quiz/attempts','/learn/quiz/attempts/{id}']) {
    assert.ok(doc.paths[route]?.get,route);
  }
  for(const route of ['/learn/quizzes/{id}/attempts','/learn/quiz/attempts/{id}/answers','/learn/quiz/attempts/{id}/submit']) {
    assert.ok(doc.paths[route]?.post,route);
  }
  assert.ok(doc.components.schemas.QuizAnswer.required.includes('answer'));
  assert.ok(doc.components.schemas.ReadingDraft.required.includes('answer_version'));
  assert.ok(doc.paths['/admin/content/readings'].post);
  assert.ok(doc.paths['/admin/content/readings/{id}'].put);
  assert.ok(doc.paths['/admin/content/readings/{id}'].delete);
  assert.ok(doc.paths['/admin/content/exams/{kind}'].post);
  assert.ok(doc.paths['/admin/content/exams/{kind}/{id}'].put);
  assert.equal(doc.components.parameters.EditVersion.required,true);
  assert.equal(doc.components.schemas.ReadingAuthoring.properties.is_practical_reading.type,'boolean');
  assert.ok(doc.paths['/learn/{kind}/attempts'].get.parameters.some(p=>p.name==='status'));
  const pattern=doc.paths['/auth/password/verify'].post.requestBody.content['application/json'].schema.properties.otp.pattern;
  assert.equal(new RegExp(pattern).test('123456'),true);
  const compose=YAML.parse(await readFile(new URL('../compose.yaml',import.meta.url),'utf8'));
  assert.ok(compose.services.app.environment.SMTP_HOST);
  assert.ok(compose.services.app.environment.S3_BUCKET);
  assert.ok(compose.services.app.healthcheck);
  const https=YAML.parse(await readFile(new URL('../compose.https.yaml',import.meta.url),'utf8'));
  assert.ok(https.services.caddy.ports.includes('443:443'));
});
