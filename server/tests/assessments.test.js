import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import pg from 'pg';
import request from 'supertest';
import {applyMigrations} from '../backend/migration-runner.js';

const schema='b22_'+randomUUID().replaceAll('-','');
const bootstrap=new pg.Client({connectionString:process.env.DATABASE_URL});
await bootstrap.connect();
await bootstrap.query('BEGIN');
await bootstrap.query('CREATE SCHEMA '+schema);
await bootstrap.query('SET LOCAL search_path TO '+schema);
await applyMigrations(bootstrap);
await bootstrap.query('COMMIT');
const connection=new URL(process.env.DATABASE_URL);
connection.searchParams.set('options','-c search_path='+schema+' -c TimeZone=UTC');
process.env.DATABASE_URL=connection.toString();
const {app}=await import('../backend/app.js');
const {pool,query,insert}=await import('../backend/db.js');
const {importQuizzes,loadQuizContent}=await import('../backend/quiz-content.js');
const {finalizeExpiredReadingAttempts}=await import('../backend/assessments.js');
const {finalizeExpiredQuizzes}=await import('../backend/quizzes.js');
const api=request(app);
after(async()=>{
  await pool.end();
  await bootstrap.query('DROP SCHEMA '+schema+' CASCADE');
  await bootstrap.end();
});

async function countQueries(work) {
  const original=pg.Client.prototype.query;
  const statements=[];
  pg.Client.prototype.query=function(...args) {
    statements.push(typeof args[0]==='string' ? args[0] : args[0].text);
    return original.apply(this,args);
  };
  try {return {result:await work(),statements};}
  finally {pg.Client.prototype.query=original;}
}

test('B2.2 quiz feedback, 100-question exams, drafts, deadlines and retries',async t=>{
  assert.equal((await query('SELECT current_schema() AS name'))[0].name,schema);
  const headers=[];
  const users=[];
  for (let i=0;i<2;i++) {
    const credentials={email:'assessment'+i+'@example.com',password:'Assessment-password-123'};
    const registered=await api.post('/api/auth/register').send({...credentials,full_name:'Assessment '+i});
    assert.equal(registered.status,201);
    const login=await api.post('/api/auth/login').send(credentials);
    assert.equal(login.status,200);
    users.push(registered.body.id);
    headers.push({Authorization:'Bearer '+login.body.token});
  }
  const [student,other]=headers;
  await query("INSERT INTO toeic_parts(id,name,test_question_count) VALUES(5,'Part 5',30),(6,'Part 6',16),(7,'Part 7',54)");
  const child=spawnSync(process.execPath,['scripts/import-content.js'],{
    cwd:fileURLToPath(new URL('../',import.meta.url)),env:{...process.env},encoding:'utf8',timeout:30000
  });
  assert.equal(child.status,0,child.stderr);
  const quizContent=await loadQuizContent();
  assert.equal((await importQuizzes(quizContent)).added,3);
  const quizzes=await query('SELECT * FROM quiz_sets ORDER BY id');
  const [mock]=await query('SELECT id,test_format FROM mock_tests ORDER BY id LIMIT 1');
  assert.equal(mock.test_format,'FULL');
  const [practice]=await query('SELECT id FROM practice_sets WHERE part_id=5 ORDER BY id LIMIT 1');
  const start=(kind,id)=>api.post('/api/learn/'+kind+'/'+id+'/attempts').set(student).send({});
  const quizStart=id=>start('quizzes',id);
  const quizAnswers=id=>query('SELECT * FROM quiz_answers WHERE attempt_id=? ORDER BY position',[id]);
  const readingAnswers=(kind,id)=>query(`SELECT * FROM ${kind==='mock' ? 'mock_test_answers' : 'practice_answers'} WHERE attempt_id=? ORDER BY position`,[id]);
  const solution=q=>q.type==='FILL_BLANK' ? q.solution.answers[0] : q.solution;

  await t.test('quiz import is atomic, repeatable and rejects invalid private keys',async()=>{
    assert.equal((await importQuizzes(quizContent)).added,0);
    const invalid=structuredClone(quizContent);
    invalid.sets[1].code='new-invalid-set';
    invalid.sets[1].questions[0].solution.pairs[0].right_id='foreign';
    await assert.rejects(importQuizzes(invalid));
    assert.equal((await query('SELECT COUNT(*) AS n FROM quiz_sets'))[0].n,3);
  });

  await t.test('all attempt routes require authentication and reject invalid IDs and filters',async()=>{
    for (const path of ['/quizzes','/quiz/attempts','/mock/attempts','/practice/attempts']) {
      assert.equal((await api.get('/api/learn'+path)).status,401,path);
    }
    assert.equal((await api.post('/api/learn/quizzes/1/attempts').send({})).status,401);
    assert.equal((await api.patch('/api/learn/mock/attempts/1/answers').send({})).status,401);
    for (const path of ['/quizzes?type=BAD','/quizzes?limit=101','/quiz/attempts?status=BAD',
      '/mock/attempts?page=0','/practice/attempts?content_id=2147483648','/quizzes/2147483648','/quiz/attempts/no']) {
      assert.equal((await api.get('/api/learn'+path).set(student)).status,422,path);
    }
    assert.equal((await start('mock',999999)).status,404);
    assert.equal((await api.post('/api/learn/mock/attempts/999999/submit').set(student).send({answers:[]})).status,404);
  });

  await t.test('quiz catalog exposes only metadata and correctly paginates beyond the last page',async()=>{
    const catalog=await api.get('/api/learn/quizzes?type=MATCH').set(student);
    assert.equal(catalog.status,200);
    assert.equal(catalog.body.total,1);
    assert.equal(catalog.body.rows[0].question_count,3);
    assert.ok(!JSON.stringify(catalog.body).includes('solution'));
    const detail=await api.get('/api/learn/quizzes/'+quizzes[0].id).set(student);
    assert.equal(detail.body.question_count,3);
    assert.ok(!('questions' in detail.body));
    const empty=await api.get('/api/learn/quizzes?page=999').set(student);
    assert.equal(empty.body.total,3);
    assert.deepEqual(empty.body.rows,[]);
    await query("UPDATE quiz_sets SET status='DRAFT' WHERE id=?",[quizzes[0].id]);
    assert.equal((await quizStart(quizzes[0].id)).status,404);
    await query("UPDATE quiz_sets SET status='PUBLISHED' WHERE id=?",[quizzes[0].id]);
    const [lesson]=await query('SELECT id,topic_id FROM grammar_lessons ORDER BY id LIMIT 1');
    const linked=await insert(`INSERT INTO quiz_sets(code,title,grammar_lesson_id,status)
      VALUES('linked-grammar','Linked grammar',?,'PUBLISHED')`,[lesson.id]);
    const q=quizContent.sets[0].questions[0];
    await query(`INSERT INTO quiz_questions(quiz_set_id,position,type,prompt,payload,solution,explanation)
      VALUES(?,1,?,?,?,?,?)`,[linked,q.type,q.prompt,JSON.stringify(q.payload),JSON.stringify(q.solution),q.explanation]);
    assert.equal((await api.get('/api/learn/quizzes?lesson_id='+lesson.id).set(student)).body.total,1);
    await query("UPDATE grammar_topics SET status='DRAFT' WHERE id=?",[lesson.topic_id]);
    assert.equal((await api.get('/api/learn/quizzes/'+linked).set(student)).status,404);
    assert.equal((await quizStart(linked)).status,404);
    assert.equal((await api.get('/api/learn/quizzes?lesson_id='+lesson.id).set(student)).body.total,0);
    await query("UPDATE grammar_topics SET status='PUBLISHED' WHERE id=?",[lesson.topic_id]);
  });

  let fillAttempt;
  let fillRows;
  await t.test('quiz snapshots are stable and private, even when published content changes later',async()=>{
    const begun=await quizStart(quizzes[0].id);
    assert.equal(begun.status,201);
    assert.ok(begun.body.deadline_at && begun.body.server_time);
    fillAttempt=begun.body.id;
    fillRows=await quizAnswers(fillAttempt);
    const path='/api/learn/quiz/attempts/'+fillAttempt;
    const first=await api.get(path).set(student);
    assert.equal(first.status,200);
    assert.ok(first.body.questions.every(q=>q.feedback===null));
    assert.ok(!JSON.stringify(first.body).includes('solution'));
    assert.ok(!JSON.stringify(first.body).includes('explanation'));
    assert.equal((await api.get(path).set(other)).status,404);
    assert.equal((await api.post(path+'/answers').set(other).send({question_id:fillRows[0].question_id,answer:'by'})).status,404);
    await query('UPDATE quiz_questions SET solution=? WHERE id=?',[JSON.stringify({answers:['changed-after-start']}),fillRows[0].question_id]);
    const again=await api.get(path).set(student);
    assert.deepEqual(again.body.questions,first.body.questions);
  });

  await t.test('quiz feedback is first-answer-only and duplicate/concurrent requests cannot change grades',async()=>{
    const path='/api/learn/quiz/attempts/'+fillAttempt;
    const send=(question,answer)=>api.post(path+'/answers').set(student).send({question_id:question,answer});
    const wrong=await send(fillRows[0].question_id,'wrong');
    assert.equal(wrong.status,200);
    assert.equal(wrong.body.is_correct,false);
    assert.deepEqual(wrong.body.correct_answer,fillRows[0].snapshot.solution);
    assert.deepEqual((await send(fillRows[0].question_id,'wrong')).body,wrong.body);
    assert.equal((await send(fillRows[0].question_id,'by')).status,409);
    assert.equal((await send(99999,'by')).status,422);
    const concurrent=await Promise.all([
      send(fillRows[1].question_id,solution(fillRows[1].snapshot)),send(fillRows[1].question_id,'wrong')
    ]);
    assert.deepEqual(concurrent.map(r=>r.status).sort(),[200,409]);
    const view=await api.get(path).set(student);
    assert.equal(view.body.questions[2].feedback,null);
    assert.ok(view.body.questions[0].feedback.explanation);
    const submitted=await api.post(path+'/submit').set(student).send({answers:[{
      question_id:fillRows[2].question_id,answer:solution(fillRows[2].snapshot)
    }]});
    assert.equal(submitted.status,200);
    assert.ok([1,2].includes(submitted.body.correct_count));
    const retry=await api.post(path+'/submit').set(student).send({});
    assert.deepEqual(retry.body,submitted.body);
    const review=await api.get(path).set(student);
    assert.equal(review.body.attempt.status,'SUBMITTED');
    assert.ok(review.body.questions.every(q=>q.feedback));
  });

  await t.test('MATCH and ORDER accept correct structures and reject foreign, repeated or missing IDs atomically',async()=>{
    for (const set of quizzes.slice(1)) {
      const begun=await quizStart(set.id);
      assert.equal(begun.status,201);
      const id=begun.body.id;
      const rows=await quizAnswers(id);
      const q=rows[0].snapshot;
      const bad=q.type==='MATCH' ? {pairs:q.solution.pairs.map(p=>({...p,right_id:'foreign'}))} : {order:['foreign',...q.solution.order.slice(1)]};
      const path='/api/learn/quiz/attempts/'+id;
      assert.equal((await api.post(path+'/answers').set(student).send({question_id:rows[0].question_id,answer:bad})).status,422);
      assert.equal((await quizAnswers(id))[0].answered_at,null);
      const body={answers:rows.map(row=>({question_id:row.question_id,answer:solution(row.snapshot)}))};
      const result=await api.post(path+'/submit').set(student).send(body);
      assert.equal(result.status,200,JSON.stringify(result.body));
      assert.equal(result.body.correct_count,3);
      assert.equal(result.body.accuracy,100);
      assert.equal((await api.get(path).set(student)).body.questions[0].feedback.is_correct,true);
    }
  });

  await t.test('quiz completion rolls back invalid batches and preserves answers submitted before expiry',async()=>{
    const begun=await quizStart(quizzes[1].id);
    const id=begun.body.id;
    const rows=await quizAnswers(id);
    const path='/api/learn/quiz/attempts/'+id;
    const bad={answers:[{question_id:rows[0].question_id,answer:solution(rows[0].snapshot)},
      {question_id:rows[1].question_id,answer:{pairs:[]}}]};
    assert.equal((await api.post(path+'/submit').set(student).send({answers:[bad.answers[0],bad.answers[0]]})).status,422);
    assert.equal((await api.post(path+'/submit').set(student).send(bad)).status,422);
    assert.ok((await quizAnswers(id)).every(row=>row.answer===null));
    assert.equal((await api.post(path+'/answers').set(student).send({question_id:rows[0].question_id,answer:solution(rows[0].snapshot)})).status,200);
    await query("UPDATE quiz_attempts SET deadline_at=now()-interval '1 second' WHERE id=?",[id]);
    assert.equal((await api.post(path+'/answers').set(student).send({question_id:rows[1].question_id,answer:solution(rows[1].snapshot)})).status,409);
    const review=await api.get(path).set(student);
    assert.equal(review.body.attempt.status,'SUBMITTED');
    assert.equal(review.body.attempt.result.correct_count,1);
    assert.equal(review.body.attempt.result.expired,true);
    assert.ok(review.body.questions.every(q=>q.feedback));
    const second=(await quizStart(quizzes[2].id)).body.id;
    await query("UPDATE quiz_attempts SET deadline_at=now()-interval '1 second' WHERE id=?",[second]);
    assert.equal(await finalizeExpiredQuizzes(25),1);
    assert.equal(await finalizeExpiredQuizzes(25),0);
    assert.equal((await query('SELECT correct_count FROM quiz_attempts WHERE id=?',[second]))[0].correct_count,0);
  });

  let mockAttempt;
  let mockRows;
  await t.test('a real 100-question Reading paper starts with a bounded number of queries and no answer leak',async()=>{
    const {result,statements}=await countQueries(()=>start('mock',mock.id));
    assert.equal(result.status,201,JSON.stringify(result.body));
    assert.ok(statements.length<=12,'Unexpected per-question database queries: '+statements.length);
    assert.equal(new Date(result.body.deadline_at)-new Date(result.body.started_at),75*60*1000);
    mockAttempt=result.body.id;
    mockRows=await readingAnswers('mock',mockAttempt);
    assert.equal(mockRows.length,100);
    assert.deepEqual([5,6,7].map(part=>mockRows.filter(row=>row.snapshot.part_id===part).length),[30,16,54]);
    const response=await api.get('/api/learn/mock/attempts/'+mockAttempt).set(student);
    assert.equal(response.status,200);
    assert.equal(response.body.questions.length,100);
    const text=JSON.stringify(response.body.questions);
    for (const field of ['is_correct','explanation','translation_vi','snapshot']) assert.ok(!text.includes('"'+field+'"'),field);
    assert.equal((await api.get('/api/learn/mock/attempts/'+mockAttempt).set(other)).status,404);
    assert.equal((await api.post('/api/learn/mock/attempts/'+mockAttempt+'/submit').set(other).send({answers:[]})).status,404);
    await query("UPDATE questions SET explanation='Changed after snapshot' WHERE id=?",[mockRows[0].question_id]);
    await query('UPDATE question_options SET is_correct=(label=?) WHERE question_id=?',[
      mockRows[0].snapshot.options.find(o=>!o.is_correct).label,mockRows[0].question_id
    ]);
  });

  await t.test('100 correct answers produce Part 5/6/7 totals, frozen explanations and one result notification',async()=>{
    const body={answers:mockRows.map(row=>({question_id:row.question_id,label:row.snapshot.options.find(o=>o.is_correct).label}))};
    const path='/api/learn/mock/attempts/'+mockAttempt;
    const {result,statements}=await countQueries(()=>api.post(path+'/submit').set(student).send(body));
    assert.equal(result.status,200,JSON.stringify(result.body));
    assert.ok(statements.length<=10,'Unexpected per-question database queries: '+statements.length);
    assert.equal(result.body.correct_count,100);
    assert.deepEqual([result.body.part5_correct,result.body.part6_correct,result.body.part7_correct],[30,16,54]);
    assert.ok(result.body.by_part.every(part=>part.accuracy===100));
    const retried=await api.post(path+'/submit').set(student).send({answers:[]});
    assert.deepEqual(retried.body,result.body);
    const review=await api.get(path).set(student);
    assert.equal(review.body.questions[0].explanation,mockRows[0].snapshot.explanation);
    assert.ok(review.body.questions[0].options.some(o=>o.is_correct));
    await query('UPDATE mock_test_attempts SET result=NULL WHERE id=?',[mockAttempt]);
    const legacy=await api.get(path).set(student);
    assert.deepEqual(legacy.body.attempt.result,result.body);
    assert.equal((await query("SELECT COUNT(*) AS n FROM notifications WHERE user_id=? AND data->>'attempt_id'=?",[users[0],String(mockAttempt)]))[0].n,1);
    assert.equal((await api.patch(path+'/answers').set(student).send({answer_version:0,answers:[body.answers[0]]})).status,409);
  });

  await t.test('drafts use optimistic versions, preserve review flags and reject foreign or duplicate answers',async()=>{
    const id=(await start('practice',practice.id)).body.id;
    const rows=await readingAnswers('practice',id);
    const path='/api/learn/practice/attempts/'+id;
    const save=body=>api.patch(path+'/answers').set(student).send(body);
    const correct=rows[0].snapshot.options.find(o=>o.is_correct).label;
    assert.equal((await save({answer_version:0,answers:[{question_id:99999,label:'A'}]})).status,422);
    assert.equal((await save({answer_version:0,answers:[{question_id:rows[0].question_id,label:'A'},{question_id:rows[0].question_id,label:'B'}]})).status,422);
    assert.equal((await save({answer_version:0,answers:[{question_id:rows[0].question_id}]})).status,422);
    const concurrent=await Promise.all([
      save({answer_version:0,answers:[{question_id:rows[0].question_id,label:correct,marked_for_review:true}]}),
      save({answer_version:0,answers:[{question_id:rows[1].question_id,label:'B'}]})
    ]);
    assert.deepEqual(concurrent.map(r=>r.status).sort(),[200,409]);
    let view=await api.get(path).set(student);
    assert.equal(view.body.attempt.answer_version,1);
    const saved=await save({answer_version:1,answers:[{question_id:rows[0].question_id,label:correct,marked_for_review:true}]});
    assert.equal(saved.body.answer_version,2);
    const clear=await save({answer_version:2,answers:[{question_id:rows[1].question_id,label:null}]});
    assert.equal(clear.status,200);
    view=await api.get(path).set(student);
    assert.equal(view.body.questions[0].selected_label,correct);
    assert.equal(view.body.questions[0].marked_for_review,true);
    assert.equal(view.body.questions[1].selected_label,null);
    assert.equal((await save({answer_version:3,answers:[{question_id:rows[0].question_id,marked_for_review:false}]})).status,200);
    assert.equal((await api.get(path).set(student)).body.questions[0].marked_for_review,false);
    assert.equal((await api.patch(path+'/answers').set(other).send({answer_version:3,answers:[{question_id:rows[0].question_id,label:'A'}]})).status,404);
    assert.equal((await api.post(path+'/submit').set(student).send({answer_version:1,answers:[]})).status,409);
    await query("UPDATE practice_attempts SET deadline_at=now()-interval '1 second' WHERE id=?",[id]);
    assert.equal((await save({answer_version:3,answers:[{question_id:rows[0].question_id,label:'B'}]})).status,409);
    const late=await api.post(path+'/submit').set(student).send({answers:rows.map(row=>({question_id:row.question_id,label:row.snapshot.options.find(o=>o.is_correct).label}))});
    assert.equal(late.status,200);
    assert.equal(late.body.expired,true);
    assert.equal(late.body.correct_count,1);
    const review=await api.get(path).set(student);
    assert.equal(review.body.questions[0].is_correct,true);
    assert.equal(review.body.questions[1].selected_label,null);
  });

  await t.test('concurrent exam submission returns the same immutable result and cannot duplicate notifications',async()=>{
    const id=(await start('mock',mock.id)).body.id;
    const path='/api/learn/mock/attempts/'+id+'/submit';
    const responses=await Promise.all([
      api.post(path).set(student).send({answers:[]}),api.post(path).set(student).send({answers:[]})
    ]);
    assert.ok(responses.every(r=>r.status===200));
    assert.deepEqual(responses[0].body,responses[1].body);
    assert.equal((await query("SELECT COUNT(*) AS n FROM notifications WHERE user_id=? AND data->>'attempt_id'=?",[users[0],String(id)]))[0].n,1);
  });

  await t.test('expired attempts finalize on read or maintenance; locked work is skipped',async()=>{
    const id=(await start('practice',practice.id)).body.id;
    const next=(await start('practice',practice.id)).body.id;
    await query("UPDATE practice_attempts SET deadline_at=now()-interval '2 seconds' WHERE id=?",[id]);
    await query("UPDATE practice_attempts SET deadline_at=now()-interval '1 second' WHERE id=?",[next]);
    const lock=await pool.connect();
    try {
      await lock.query('BEGIN');
      await lock.query('SELECT id FROM practice_attempts WHERE id=$1 FOR UPDATE',[id]);
      assert.equal(await finalizeExpiredReadingAttempts(1),1);
      assert.equal((await query('SELECT status FROM practice_attempts WHERE id=?',[next]))[0].status,'SUBMITTED');
      assert.equal((await query('SELECT status FROM practice_attempts WHERE id=?',[id]))[0].status,'IN_PROGRESS');
    } finally {
      await lock.query('ROLLBACK');
      lock.release();
    }
    assert.equal(await finalizeExpiredReadingAttempts(25),1);
    assert.equal(await finalizeExpiredReadingAttempts(25),0);
    const second=(await start('practice',practice.id)).body.id;
    await query("UPDATE practice_attempts SET deadline_at=now()-interval '1 second' WHERE id=?",[second]);
    const view=await api.get('/api/learn/practice/attempts/'+second).set(student);
    assert.equal(view.body.attempt.status,'SUBMITTED');
    assert.equal(view.body.attempt.result.correct_count,0);
  });

  await t.test('history is account-scoped, filterable and keeps totals for empty pages',async()=>{
    for (const kind of ['quiz','practice','mock']) {
      const list=await api.get('/api/learn/'+kind+'/attempts?status=SUBMITTED&limit=1').set(student);
      assert.equal(list.status,200);
      assert.ok(list.body.total>0);
      assert.equal(list.body.rows.length,1);
      assert.ok(list.body.rows.every(row=>!('snapshot' in row) && !('result' in row)));
      const empty=await api.get('/api/learn/'+kind+'/attempts?status=SUBMITTED&page=999').set(student);
      assert.equal(empty.body.total,list.body.total);
      assert.deepEqual(empty.body.rows,[]);
      assert.equal((await api.get('/api/learn/'+kind+'/attempts').set(other)).body.total,0);
      const fk=kind==='quiz' ? 'quiz_set_id' : kind==='practice' ? 'practice_set_id' : 'mock_test_id';
      const content=list.body.rows[0][fk];
      const filtered=await api.get('/api/learn/'+kind+'/attempts?content_id='+content).set(student);
      assert.ok(filtered.body.rows.length>0);
      assert.ok(filtered.body.rows.every(row=>row[fk]===content));
    }
    await query("UPDATE users SET status='LOCKED' WHERE id=?",[users[0]]);
    assert.equal((await api.get('/api/learn/quizzes').set(student)).status,401);
  });
});
