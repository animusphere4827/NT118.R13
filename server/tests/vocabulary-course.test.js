import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import request from 'supertest';
import {applyMigrations} from '../backend/migration-runner.js';

const schema='vocabulary_course_'+randomUUID().replaceAll('-','');
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
const {query,insert,pool,transaction}=await import('../backend/db.js');
const {loadPath}=await import('../backend/course.js');
const api=request(app);
after(async()=>{
  await pool.end();
  await bootstrap.query('DROP SCHEMA '+schema+' CASCADE');
  await bootstrap.end();
});

test('Vocabulary course separates study completion from personal mastery',async t=>{
  const users=[];
  for (let i=0;i<2;i++) {
    const credentials={email:'vocab'+i+'@example.com',password:'Vocab-course-password-123'};
    const registration=await api.post('/api/auth/register').send({...credentials,full_name:'Learner '+i});
    assert.equal(registration.status,201);
    const login=await api.post('/api/auth/login').send(credentials);
    assert.equal(login.status,200);
    users.push({id:registration.body.id,headers:{Authorization:'Bearer '+login.body.token}});
  }
  const student=users[0].headers,other=users[1].headers;
  const unit=await insert(`INSERT INTO course_units(slug,title,position,status,unlock_group)
    VALUES('vocabulary','Vocabulary',10,'PUBLISHED','vocabulary')`);
  const topic=await insert("INSERT INTO vocabulary_topics(name,status) VALUES('Office','PUBLISHED')");
  const words=[];
  for(let i=0;i<23;i++) {
    words.push({id:await insert(`INSERT INTO vocabularies(topic_id,word,meaning_vi,status)
      VALUES(?,?,?,'PUBLISHED')`,[topic,'office word '+i,'Nghĩa '+i]),answer:'office word '+i});
  }
  const lesson=await insert(`INSERT INTO course_lessons(unit_id,seed_key,title,type,minutes,position,status,vocabulary_topic_id)
    VALUES(?,'office','Office','VOCABULARY',20,1,'PUBLISHED',?)`,[unit,topic]);
  const next=await insert(`INSERT INTO course_lessons(unit_id,seed_key,title,type,minutes,position,status,vocabulary_topic_id)
    VALUES(?,'office-next','Next','VOCABULARY',20,2,'PUBLISHED',?)`,[unit,topic]);
  const base='/api/learn/path/lessons/'+lesson+'/vocabulary';
  const path=async()=> (await api.get('/api/learn/path').set(student)).body;
  const start=()=>api.post(base+'/start').set(student).send({});
  const study=ids=>api.put(base+'/study').set(student).send({vocabulary_ids:ids});
  const check=()=>api.post(base+'/checks').set(student).send({});
  const submit=(id,answers,headers=student)=>api.post('/api/learn/vocabulary-checks/'+id+'/submit').set(headers).send({answers});
  const answerFor=q=>({vocabulary_id:q.vocabulary_id,answer:words.find(w=>w.id===q.vocabulary_id).answer});

  await t.test('authentication, ownership, locked lessons, forged bodies and mastery flags',async()=>{
    assert.equal((await api.post(base+'/start').send({})).status,401);
    assert.equal((await api.post(base+'/start').set(student).send({user_id:users[1].id})).status,422);
    assert.equal((await api.post('/api/learn/path/lessons/'+next+'/vocabulary/start').set(student).send({})).status,409);
    assert.equal((await api.get(base).set(student)).status,409);
    await query(`INSERT INTO user_vocabulary_progress(user_id,vocabulary_id,status,is_favorite)
      SELECT ?,id,'MASTERED',TRUE FROM vocabularies WHERE topic_id=?`,[users[0].id,topic]);
    assert.equal((await path()).completed,0);
  });

  await t.test('concurrent start resumes one frozen lesson with ten words',async()=>{
    const responses=await Promise.all([start(),start(),start()]);
    assert.ok(responses.every(r=>r.status===200),JSON.stringify(responses.map(r=>r.body)));
    assert.equal((await query('SELECT COUNT(*) AS n FROM course_vocabulary_progress'))[0].n,1);
    assert.equal(responses[0].body.total_words,23);
    assert.equal(responses[0].body.words.length,10);
    assert.deepEqual(responses[0].body.words.map(w=>w.id),words.slice(0,10).map(w=>w.id));
    assert.equal(responses[0].body.state,'STUDYING');
    assert.equal(responses[0].headers['cache-control'],'no-store');
    assert.equal((await api.get(base).set(other)).status,409);
  });

  let firstCheck;
  await t.test('study is idempotent and scoped to the current batch, check requires coverage',async()=>{
    assert.equal((await check()).status,409);
    assert.equal((await study([words[10].id])).status,422);
    assert.equal((await study([words[0].id,words[0].id])).status,422);
    assert.equal((await study([words[0].id])).status,200);
    assert.equal((await study([words[0].id])).body.studied_ids.length,1);
    assert.equal((await check()).status,409);
    await study(words.slice(0,10).map(w=>w.id));
    const results=await Promise.all([check(),check()]);
    assert.ok(results.every(r=>r.status===200));
    assert.equal(results[0].body.id,results[1].body.id);
    firstCheck=results[0].body;
    assert.equal(firstCheck.total,10);
    assert.ok(firstCheck.questions.every(q=>!('word'in q)&&!('answer'in q)&&!('correct_answer'in q)));
    const state=(await api.get(base).set(student)).body;
    assert.equal(state.state,'CHECKING');
    assert.deepEqual(state.words,[]);
    assert.equal(state.check.id,firstCheck.id);
    assert.equal((await study([words[0].id])).status,409);
    assert.equal((await api.get('/api/learn/vocabulary-checks/'+firstCheck.id).set(other)).status,404);
    assert.equal((await submit(firstCheck.id,firstCheck.questions.map(answerFor),other)).status,404);
    assert.equal((await submit(firstCheck.id,[])).status,422);
    assert.equal((await submit(firstCheck.id,firstCheck.questions.slice(1).map(answerFor))).status,422);
    const foreign=firstCheck.questions.map(answerFor);foreign[0].vocabulary_id=words[10].id;
    assert.equal((await submit(firstCheck.id,foreign)).status,422);
  });

  await t.test('seven of ten fails, feedback persists and a changed resubmission is rejected',async()=>{
    const answers=firstCheck.questions.map((q,i)=>({...answerFor(q),...(i<3?{answer:''}:{})}));
    const failed=await submit(firstCheck.id,answers);
    assert.equal(failed.status,200,JSON.stringify(failed.body));
    assert.equal(failed.body.passed,false);
    assert.equal(failed.body.correct_count,7);
    assert.equal(failed.body.required_correct,8);
    assert.equal(failed.body.passed_words,0);
    assert.equal((await path()).completed,0);
    const replay=await submit(firstCheck.id,[...answers].reverse());
    assert.deepEqual(replay.body,failed.body);
    assert.equal((await submit(firstCheck.id,firstCheck.questions.map(answerFor))).status,409);
    const saved=await api.get('/api/learn/vocabulary-checks/'+firstCheck.id).set(student);
    assert.deepEqual(saved.body.result,failed.body);
    assert.equal((await api.get(base).set(student)).body.state,'STUDYING');
  });

  await t.test('eight of ten passes once under concurrent submission, without awarding mastery',async()=>{
    const retry=await check();
    assert.notEqual(retry.body.id,firstCheck.id);
    const answers=retry.body.questions.map((q,i)=>({...answerFor(q),...(i<2?{answer:'wrong'}:{})}));
    const responses=await Promise.all([submit(retry.body.id,answers),submit(retry.body.id,answers)]);
    assert.ok(responses.every(r=>r.status===200));
    assert.deepEqual(responses[0].body,responses[1].body);
    assert.equal(responses[0].body.passed,true);
    assert.equal(responses[0].body.passed_words,10);
    assert.equal(responses[0].body.lesson_completed,false);
    const state=(await api.get(base).set(student)).body;
    assert.equal(state.batch_number,2);
    assert.equal(state.words.length,10);
    assert.deepEqual(state.studied_ids,[]);
    assert.equal((await check()).status,409);
    assert.equal((await study([words[0].id])).status,422);
  });

  await t.test('restart and admin edits do not replace the learning snapshot',async()=>{
    await query("UPDATE vocabularies SET word='changed by admin' WHERE id=?",[words[10].id]);
    await insert("INSERT INTO vocabularies(topic_id,word,meaning_vi,status) VALUES(?,'new extra','Thêm mới','PUBLISHED')",[topic]);
    const state=await start();
    assert.equal(state.body.total_words,23);
    assert.equal(state.body.words[0].word,words[10].answer);
    await study(words.slice(10,20).map(w=>w.id));
    const second=await check();
    const result=await submit(second.body.id,second.body.questions.map(q=>({...answerFor(q),answer:'  '+answerFor(q).answer.toUpperCase().replaceAll(' ','  ')+'  '})));
    assert.equal(result.body.passed,true);
    assert.equal(result.body.passed_words,20);
  });

  await t.test('small final batch rounds up, completed lessons stay done after review or new words',async()=>{
    await study(words.slice(20).map(w=>w.id));
    const last=await check();
    assert.equal(last.body.total,3);
    const failed=await submit(last.body.id,last.body.questions.map((q,i)=>({...answerFor(q),...(i===0?{answer:'wrong'}:{})})));
    assert.equal(failed.body.required_correct,3);
    assert.equal(failed.body.passed,false);
    const retry=await check();
    const done=await submit(retry.body.id,retry.body.questions.map(answerFor));
    assert.equal(done.body.lesson_completed,true);
    const before=(await start()).body;
    assert.equal(before.state,'COMPLETED');
    assert.ok(before.completed_at);
    await query("UPDATE user_vocabulary_progress SET status='NEW',is_favorite=FALSE WHERE user_id=?",[users[0].id]);
    await insert("INSERT INTO vocabularies(topic_id,word,meaning_vi,status) VALUES(?,'another extra','Thêm nữa','PUBLISHED')",[topic]);
    const after=(await start()).body;
    assert.equal(after.completed_at,before.completed_at);
    assert.equal(after.total_words,23);
    assert.equal((await path()).current_lesson_id,next);
    assert.equal((await path()).completed,1);
    const review=await api.get(base+'/review?page=3').set(student);
    assert.equal(review.status,200);
    assert.equal(review.body.rows.length,3);
    assert.equal(review.body.total,23);
    assert.equal((await api.get(base+'/review?page=999').set(student)).body.rows.length,0);
    assert.equal((await api.get(base+'/review?page=0').set(student)).status,422);
    assert.equal((await api.get(base+'/review').set(other)).status,409);
    assert.equal((await start()).body.completed_at,before.completed_at);
    assert.equal((await check()).status,409);
    assert.equal((await api.get('/api/learn/path').set(other)).body.completed,0);
    const individual=await query('SELECT status,is_favorite FROM user_vocabulary_progress WHERE user_id=?',[users[0].id]);
    assert.ok(individual.every(row=>row.status==='NEW'&&!row.is_favorite));
  });

  await t.test('skill tracks open independently and recommendations alternate while custom units stay ordered',async()=>{
    const grammarUnit=await insert(`INSERT INTO course_units(slug,title,position,status,unlock_group)
      VALUES('grammar','Grammar',20,'PUBLISHED','grammar')`);
    const grammarTopic=await insert("INSERT INTO grammar_topics(name,status) VALUES('Tenses','PUBLISHED')");
    const grammar=await insert("INSERT INTO grammar_lessons(topic_id,title,content,status) VALUES(?,'Tense','Text','PUBLISHED')",[grammarTopic]);
    const grammarLesson=await insert(`INSERT INTO course_lessons(unit_id,seed_key,title,type,minutes,position,status,grammar_lesson_id)
      VALUES(?,'grammar-first','Grammar','GRAMMAR',10,1,'PUBLISHED',?)`,[grammarUnit,grammar]);
    const state=await path();
    assert.equal(state.current_lesson_id,grammarLesson);
    assert.equal(state.units[0].lessons[1].status,'IN_PROGRESS');
    assert.equal(state.units[1].lessons[0].status,'IN_PROGRESS');
    assert.equal((await api.get('/api/learn/path/lessons/'+grammarLesson).set(student)).status,200);
    for (let index=0;index<2;index++) {
      const customUnit=await insert(`INSERT INTO course_units(slug,title,position,status)
        VALUES(?,? ,?,'PUBLISHED')`,['custom-'+index,'Custom '+index,30+index]);
      await insert(`INSERT INTO course_lessons(unit_id,seed_key,title,type,minutes,position,status,grammar_lesson_id)
        VALUES(?,?,?,'GRAMMAR',10,1,'PUBLISHED',?)`,[customUnit,'custom-'+index,'Custom lesson',grammar]);
    }
    const custom=(await path()).units.filter(unit=>unit.unlock_group==='course');
    assert.deepEqual(custom.map(unit=>unit.lessons[0].status),['IN_PROGRESS','LOCKED']);
    await query("UPDATE vocabulary_topics SET status='DRAFT' WHERE id=?",[topic]);
    assert.equal((await start()).status,404);
    assert.equal((await api.put('/api/learn/vocabulary/'+words[0].id+'/progress').set(student).send({status:'MASTERED',is_favorite:false})).status,404);
    await query("UPDATE vocabulary_topics SET status='PUBLISHED' WHERE id=?",[topic]);
    const original=pool.query;let statements=0;
    pool.query=function(...args){statements++;return original.apply(this,args);};
    try {await loadPath(users[0].id);assert.equal(statements,1);} finally {pool.query=original;}
  });

  await t.test('admin distinguishes course completion from memory flags without exposing word snapshots',async()=>{
    assert.equal((await api.get('/api/admin/course_vocabulary_progress').set(other)).status,403);
    await query("UPDATE users SET role='ADMIN' WHERE id=?",[users[0].id]);
    const response=await api.get('/api/admin/course_vocabulary_progress').set(student);
    assert.equal(response.status,200,JSON.stringify(response.body));
    assert.equal(response.body.rows[0].total_words,23);
    assert.equal(response.body.rows[0].passed_count,23);
    assert.ok(response.body.rows[0].completed_at);
    assert.ok(!('words' in response.body.rows[0]));
    assert.equal((await api.put('/api/admin/course_vocabulary_progress/1').set(student).send({passed_count:0})).status,405);
    await query("UPDATE users SET role='STUDENT' WHERE id=?",[users[0].id]);
  });

  await t.test('migration preserves previously completed vocabulary topics exactly once',async()=>{
    await transaction(async db=>{
      const migrationSchema='vocab_legacy_'+randomUUID().replaceAll('-','');
      await db.query('CREATE SCHEMA '+migrationSchema);
      await db.query('SET LOCAL search_path TO '+migrationSchema);
      await applyMigrations(db);
      await db.query('DROP TABLE vocabulary_lesson_checks,course_vocabulary_progress');
      await db.query('ALTER TABLE course_units DROP COLUMN unlock_group');
      await db.query('DELETE FROM schema_migrations WHERE version=9');
      await db.query("INSERT INTO users(id,email,password_hash,full_name) VALUES(1,'old@example.com','hash','Old')");
      await db.query("INSERT INTO vocabulary_topics(id,name,status) VALUES(1,'Old','PUBLISHED')");
      await db.query("INSERT INTO vocabularies(id,topic_id,word,meaning_vi,status) VALUES(1,1,'old','Cũ','PUBLISHED'),(2,1,'new','Mới','PUBLISHED')");
      await db.query("INSERT INTO course_units(id,slug,title,position,status) VALUES(1,'vocabulary','Vocabulary',1,'PUBLISHED')");
      await db.query("INSERT INTO course_lessons(unit_id,seed_key,title,type,minutes,position,status,vocabulary_topic_id) VALUES(1,'old','Old','VOCABULARY',10,1,'PUBLISHED',1)");
      await db.query("INSERT INTO user_vocabulary_progress(user_id,vocabulary_id,status) VALUES(1,1,'MASTERED'),(1,2,'MASTERED')");
      await applyMigrations(db);
      await applyMigrations(db);
      const {rows}=await db.query('SELECT source,passed_count,completed_at FROM course_vocabulary_progress');
      assert.equal(rows.length,1);
      assert.equal(rows[0].source,'LEGACY');
      assert.equal(rows[0].passed_count,2);
      assert.ok(rows[0].completed_at);
      await db.query('SET LOCAL search_path TO '+schema);
      await db.query('DROP SCHEMA '+migrationSchema+' CASCADE');
    });
  });
});
