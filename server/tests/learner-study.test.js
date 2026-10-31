import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import request from 'supertest';
import {applyMigrations} from '../backend/migration-runner.js';

const schema='learner_'+randomUUID().replaceAll('-','');
const bootstrap=new pg.Client({connectionString:process.env.DATABASE_URL});
await bootstrap.connect();await bootstrap.query('BEGIN');await bootstrap.query('CREATE SCHEMA '+schema);
await bootstrap.query('SET LOCAL search_path TO '+schema);await applyMigrations(bootstrap);await bootstrap.query('COMMIT');
const connection=new URL(process.env.DATABASE_URL);connection.searchParams.set('options','-c search_path='+schema+' -c TimeZone=UTC');process.env.DATABASE_URL=connection.toString();
const {app}=await import('../backend/app.js');const {query,insert,pool}=await import('../backend/db.js');
const {streaks}=await import('../backend/learner.js');const api=request(app);
after(async()=>{await pool.end();await bootstrap.query('DROP SCHEMA '+schema+' CASCADE');await bootstrap.end();});

test('Study analytics, verified grammar, badges and private mistake review',async t=>{
  const users=[];
  for(let i=0;i<2;i++){
    const credentials={email:'study'+i+'@example.com',password:'Study-password-1234'};
    const registered=await api.post('/api/auth/register').send({...credentials,full_name:'Student '+i});assert.equal(registered.status,201);
    const login=await api.post('/api/auth/login').send(credentials);assert.equal(login.status,200);
    users.push({id:registered.body.id,headers:{Authorization:'Bearer '+login.body.token}});
  }
  const own=users[0].headers,other=users[1].headers;
  await t.test('new learner has no invented scores or achievements and every route is authenticated',async()=>{
    for(const route of ['/api/learn/analytics','/api/learn/badges','/api/learn/mistakes','/api/learn/grammar/1/workspace'])assert.equal((await api.get(route)).status,401,route);
    assert.equal((await api.put('/api/auth/me/featured-badge').send({badge_code:null})).status,401);
    const stats=await api.get('/api/learn/analytics').set(own);assert.equal(stats.status,200,JSON.stringify(stats.body));
    assert.equal(stats.body.accuracy,null);assert.equal(stats.body.current_streak,0);assert.deepEqual(stats.body.activity,[]);
    const badges=await api.get('/api/learn/badges').set(own);assert.equal(badges.status,200,JSON.stringify(badges.body));assert.equal(badges.body.unlocked_count,0);
    assert.equal((await api.put('/api/auth/me/featured-badge').set(own).send({badge_code:'FIRST_MOCK'})).status,409);
    assert.equal((await api.put('/api/auth/me/featured-badge').set(own).send({badge_code:'UNKNOWN'})).status,404);
    assert.equal((await api.put('/api/auth/me/featured-badge').set(own).send({badge_code:null,user_id:users[1].id})).status,422);
    assert.equal((await api.get('/api/learn/mistakes?page=0').set(own)).status,422);
  });
  await query("INSERT INTO toeic_parts(id,name,test_question_count) VALUES(5,'Part 5',30)");
  const topic=await insert("INSERT INTO grammar_topics(name,status) VALUES('Grammar','PUBLISHED')");
  const lesson=await insert("INSERT INTO grammar_lessons(topic_id,title,content,status) VALUES(?,'Present tense','Theory','PUBLISHED')",[topic]);
  const questions=[];
  for(let i=0;i<5;i++){
    const id=await insert("INSERT INTO questions(part_id,lesson_id,question_text,explanation,status) VALUES(5,?,?,?,'PUBLISHED')",[lesson,'Question '+i,'Private explanation '+i]);questions.push(id);
    for(const label of ['A','B','C','D'])await query('INSERT INTO question_options(question_id,label,option_text,is_correct) VALUES(?,?,?,?)',[id,label,'Option '+label,label==='A']);
  }
  await t.test('grammar completion needs coverage and an actual passing check, never a client score',async()=>{
    const workspace=await api.get('/api/learn/grammar/'+lesson+'/workspace').set(own);assert.equal(workspace.status,200);
    assert.equal(workspace.body.questions.length,5);assert.ok(!JSON.stringify(workspace.body.questions).includes('Private explanation'));assert.ok(!JSON.stringify(workspace.body.questions).includes('"is_correct"'));
    assert.equal((await api.put('/api/learn/grammar/'+lesson+'/progress').set(own).send({progress_percent:100})).status,409);
    for(const id of questions.slice(0,4))assert.equal((await api.post('/api/learn/grammar/'+lesson+'/check').set(own).send({question_id:id,label:'A'})).status,200);
    assert.equal((await api.get('/api/learn/grammar/'+lesson+'/workspace').set(own)).body.progress.passed,false);
    const result=await api.post('/api/learn/grammar/'+lesson+'/check').set(own).send({question_id:questions[4],label:'B'});
    assert.equal(result.body.is_correct,false);assert.equal(result.body.correct_label,'A');assert.equal(result.body.progress.passed,true);
    assert.equal((await query('SELECT progress_percent FROM user_grammar_progress WHERE user_id=? AND lesson_id=?',[users[0].id,lesson]))[0].progress_percent,100);
    assert.equal((await api.get('/api/learn/grammar/'+lesson+'/workspace').set(other)).body.progress.answered,0);
    assert.equal((await api.post('/api/learn/grammar/'+lesson+'/check').set(own).send({question_id:2147483647,label:'A'})).status,404);
    await query("UPDATE grammar_topics SET status='DRAFT' WHERE id=?",[topic]);
    assert.equal((await api.get('/api/learn/grammar/'+lesson+'/workspace').set(own)).status,404);
    assert.equal((await api.post('/api/learn/grammar/'+lesson+'/check').set(own).send({question_id:questions[0],label:'A'})).status,404);
    await query("UPDATE grammar_topics SET status='PUBLISHED' WHERE id=?",[topic]);
  });
  const mock=await insert("INSERT INTO mock_tests(title,status) VALUES('Mini','PUBLISHED')");
  const practice=await insert("INSERT INTO practice_sets(part_id,title,status) VALUES(5,'Practice','PUBLISHED')");
  async function attempt(kind,correct,day='now()'){
    const table=kind==='mock'?'mock_test_attempts':'practice_attempts',answers=kind==='mock'?'mock_test_answers':'practice_answers',column=kind==='mock'?'mock_test_id':'practice_set_id';
    const id=await insert(`INSERT INTO ${table}(user_id,${column},content_title,status,total_questions,correct_count,accuracy,deadline_at,submitted_at,result)
      VALUES(?,?,'Test','SUBMITTED',1,?,?,now(),${day},?::jsonb)`,[users[0].id,kind==='mock'?mock:practice,correct?1:0,correct?100:0,JSON.stringify({correct_count:correct?1:0,total_questions:1,accuracy:correct?100:0})]);
    await query(`INSERT INTO ${answers}(attempt_id,question_id,position,snapshot,selected_label,is_correct) VALUES(?,?,1,?::jsonb,?,?)`,[id,questions[0],JSON.stringify({id:questions[0],part_id:5,question_type:'WORD_FORM',question_text:'Saved question',explanation:'Saved explanation',options:[{label:'A',option_text:'Correct',is_correct:true}]}),correct?'A':'B',correct]);return id;
  }
  await t.test('analytics and mistakes use only submitted owned snapshots, a later correct answer resolves the mistake',async()=>{
    const wrong=await attempt('mock',false,"now()-interval '1 hour'");
    let mistakes=await api.get('/api/learn/mistakes').set(own);assert.equal(mistakes.status,200,JSON.stringify(mistakes.body));assert.equal(mistakes.body.total,1);assert.equal(mistakes.body.rows[0].attempt_id,wrong);assert.equal(mistakes.body.rows[0].explanation,'Saved explanation');
    assert.equal((await api.get('/api/learn/mistakes?page=999').set(own)).body.total,1);
    assert.equal((await api.get('/api/learn/mistakes').set(other)).body.total,0);
    await attempt('practice',true);
    mistakes=await api.get('/api/learn/mistakes').set(own);assert.equal(mistakes.body.total,0);
    const stats=await api.get('/api/learn/analytics').set(own);assert.equal(stats.body.total_questions,2);assert.equal(stats.body.accuracy,50);assert.equal(stats.body.by_part[0].correct,1);assert.equal(stats.body.current_streak,1);
    assert.equal((await api.get('/api/learn/analytics').set(other)).body.total_questions,0);
  });
  await t.test('concurrent badge checks award once, featured badges are owned and notifications delete privately',async()=>{
    const responses=await Promise.all([api.get('/api/learn/badges').set(own),api.get('/api/learn/badges').set(own),api.get('/api/learn/badges').set(own)]);
    assert.ok(responses.every(r=>r.status===200),JSON.stringify(responses.map(r=>r.body)));
    assert.equal(responses[0].body.unlocked_count,1);
    assert.equal((await query("SELECT COUNT(*) AS n FROM notifications WHERE user_id=? AND type='ACHIEVEMENT'",[users[0].id]))[0].n,1);
    assert.equal((await api.put('/api/auth/me/featured-badge').set(own).send({badge_code:'FIRST_MOCK'})).status,204);
    assert.equal((await api.get('/api/learn/badges').set(own)).body.featured_code,'FIRST_MOCK');
    assert.equal((await api.get('/api/learn/badges').set(other)).body.featured_code,null);
    const [notification]=await query('SELECT id FROM notifications WHERE user_id=?',[users[0].id]);
    assert.equal((await api.delete('/api/learn/notifications/'+notification.id).set(other)).status,404);
    assert.equal((await api.delete('/api/learn/notifications/'+notification.id).set(own)).status,204);
    await api.get('/api/learn/badges').set(own);
    assert.equal((await query("SELECT COUNT(*) AS n FROM notifications WHERE user_id=? AND type='ACHIEVEMENT'",[users[0].id]))[0].n,0);
  });
  await t.test('reviewing a flashcard again or deleting its deck preserves previous study days',async()=>{
    const deck=await insert("INSERT INTO flashcard_decks(user_id,name) VALUES(?,'Study day deck')",[users[0].id]);
    const card=await insert("INSERT INTO flashcards(deck_id,front_text,back_text) VALUES(?,'invoice','hóa đơn')",[deck]);
    await query("UPDATE flashcards SET last_reviewed_at=clock_timestamp()-interval '1 day' WHERE id=?",[card]);
    assert.equal((await api.post('/api/learn/cards/'+card+'/review').set(own).send({quality:5})).status,200);
    assert.equal((await query('SELECT COUNT(*) AS n FROM flashcard_review_days WHERE user_id=?',[users[0].id]))[0].n,2);
    assert.equal((await api.delete('/api/learn/decks/'+deck).set(own)).status,204);
    assert.equal((await query('SELECT COUNT(*) AS n FROM flashcard_review_days WHERE user_id=?',[users[0].id]))[0].n,2);
    assert.equal((await api.get('/api/learn/analytics').set(own)).body.current_streak,2);
  });
  await t.test('streaks deduplicate dates, tolerate yesterday, and reset across gaps',()=>{
    assert.deepEqual(streaks(['2026-10-27','2026-10-28','2026-10-28','2026-10-29'],'2026-10-30'),{current_streak:3,longest_streak:3});
    assert.deepEqual(streaks(['2026-10-27','2026-10-28','2026-10-29'],'2026-10-31'),{current_streak:0,longest_streak:3});
    assert.deepEqual(streaks([],'2026-10-31'),{current_streak:0,longest_streak:0});
  });
});
