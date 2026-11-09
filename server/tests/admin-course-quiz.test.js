import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import request from 'supertest';
import {applyMigrations} from '../backend/migration-runner.js';

const schema='acq_'+randomUUID().replaceAll('-','');
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
const {query,pool}=await import('../backend/db.js');
const api=request(app);
after(async()=>{await pool.end();await bootstrap.query('DROP SCHEMA '+schema+' CASCADE');await bootstrap.end();});

const fill={type:'FILL_BLANK',prompt:'Điền giới từ',payload:{text:'Submit the report _____ Friday.'},solution:{answers:['by']},explanation:'By + mốc thời gian.'};

test('admin manages quizzes and the learning path',async t=>{
  const users=[];
  for(let index=0;index<2;index++){
    const credentials={email:'acq'+index+'@example.com',password:'Course-quiz-password-1'};
    const created=await api.post('/api/auth/register').send({...credentials,full_name:'User '+index});
    if(index===0)await query("UPDATE users SET role='ADMIN' WHERE id=?",[created.body.id]);
    const login=await api.post('/api/auth/login').send(credentials);
    users.push({Authorization:'Bearer '+login.body.token,'X-Requested-With':'TOEICAdmin'});
  }
  const [admin,student]=users;
  const post=(path,body)=>api.post('/api/admin/'+path).set(admin).send(body);
  const put=async(path,body)=>{const current=await api.get('/api/admin/'+path).set(admin);return api.put('/api/admin/'+path).set(admin).set('If-Match',current.body.edit_version).send(body);};
  const remove=async path=>{const current=await api.get('/api/admin/'+path).set(admin);return api.delete('/api/admin/'+path).set(admin).set('If-Match',current.body.edit_version);};

  await t.test('metadata exposes the new resources and quiz history is read-only',async()=>{
    const meta=(await api.get('/api/admin/meta').set(admin)).body;
    for(const name of ['quiz_sets','quiz_questions','course_units','course_lessons'])assert.ok(meta.resources[name],name);
    assert.ok(meta.readOnly.includes('quiz_attempts')&&meta.readOnly.includes('quiz_answers'));
    assert.equal((await api.get('/api/admin/quiz_attempts').set(admin)).status,200);
    assert.equal((await post('quiz_attempts',{})).status,405);
    assert.equal((await api.get('/api/admin/quiz_sets').set(student)).status,403);
  });

  let quiz,question;
  await t.test('quiz sets need valid questions before publishing',async()=>{
    quiz=(await post('quiz_sets',{code:'office-prepositions',title:'Giới từ văn phòng',status:'DRAFT'})).body.id;
    assert.ok(quiz);
    assert.equal((await post('quiz_sets',{code:'Có dấu',title:'Sai mã'})).status,422);
    assert.equal((await put('quiz_sets/'+quiz,{code:'office-prepositions',title:'Giới từ văn phòng',status:'PUBLISHED'})).status,422);
    const badFill=await post('quiz_questions',{...fill,quiz_set_id:quiz,position:1,payload:{text:'No blank here.'}});
    assert.equal(badFill.status,422);
    const badMatch=await post('quiz_questions',{quiz_set_id:quiz,position:1,type:'MATCH',prompt:'Nối',explanation:'x',
      payload:{left:[{id:'l1',text:'a'},{id:'l2',text:'b'}],right:[{id:'r1',text:'c'},{id:'r2',text:'d'}]},solution:{pairs:[{left_id:'l1',right_id:'r1'}]}});
    assert.equal(badMatch.status,422);
    question=(await post('quiz_questions',{...fill,quiz_set_id:quiz,position:1})).body.id;
    assert.ok(question);
    assert.equal((await post('quiz_questions',{...fill,quiz_set_id:quiz,position:1})).status,409);
    assert.equal((await put('quiz_sets/'+quiz,{code:'office-prepositions',title:'Giới từ văn phòng',status:'PUBLISHED'})).status,200);
    const list=await api.get('/api/learn/quizzes').set(student);
    assert.equal(list.body.rows.find(row=>row.id===quiz)?.question_count,1);
  });

  await t.test('quiz content with learner attempts cannot be deleted',async()=>{
    const attempt=await api.post('/api/learn/quizzes/'+quiz+'/attempts').set(student);
    assert.equal(attempt.status,201);
    assert.equal((await remove('quiz_questions/'+question)).status,409);
    assert.equal((await remove('quiz_sets/'+quiz)).status,409);
    const history=(await api.get('/api/admin/quiz_attempts').set(admin)).body;
    assert.equal(history.total,1);
    assert.equal(history.rows[0].quiz_set_id,quiz);
    const spare=(await post('quiz_sets',{code:'spare-quiz',title:'Bộ tạm'})).body.id;
    assert.equal((await remove('quiz_sets/'+spare)).status,204);
  });

  await t.test('course lessons point to exactly one matching content item',async()=>{
    const topic=(await post('vocabulary_topics',{name:'Admin topic',status:'PUBLISHED'})).body.id;
    await post('vocabularies',{topic_id:topic,word:'agenda',meaning_vi:'chương trình họp',status:'PUBLISHED'});
    const unit=(await post('course_units',{slug:'admin-unit',title:'Phần do admin tạo',position:900,unlock_group:'admin-unit',status:'PUBLISHED'})).body.id;
    assert.ok(unit);
    assert.equal((await post('course_lessons',{unit_id:unit,title:'Thiếu nội dung',type:'GRAMMAR',position:1,status:'PUBLISHED'})).status,422);
    const part6=(await post('practice_sets',{part_id:6,title:'Bộ Part 6'})).body.id;
    assert.equal((await post('course_lessons',{unit_id:unit,title:'Sai Part',type:'PRACTICE_PART5',position:1,practice_set_id:part6})).status,422);
    const lesson=await post('course_lessons',{unit_id:unit,title:'Từ vựng họp',type:'VOCABULARY',position:1,status:'PUBLISHED',
      vocabulary_topic_id:topic,grammar_lesson_id:999});
    assert.equal(lesson.status,201);
    const [row]=await query('SELECT seed_key,grammar_lesson_id FROM course_lessons WHERE id=?',[lesson.body.id]);
    assert.match(row.seed_key,/^admin:/);
    assert.equal(row.grammar_lesson_id,null);
    const path=(await api.get('/api/learn/path').set(student)).body;
    const shown=path.units.find(item=>item.id===unit);
    assert.equal(shown.lessons[0].title,'Từ vựng họp');
    assert.equal(shown.lessons[0].status,'IN_PROGRESS');
    assert.equal((await put('course_units/'+unit,{slug:'renamed',title:'x',position:900,unlock_group:'admin-unit',status:'PUBLISHED'})).status,422);
    assert.equal((await api.post('/api/learn/path/lessons/'+lesson.body.id+'/vocabulary/start').set(student).send({})).status,200);
    assert.equal((await remove('course_lessons/'+lesson.body.id)).status,409);
    assert.equal((await remove('course_units/'+unit)).status,409);
    assert.equal((await put('course_lessons/'+lesson.body.id,{unit_id:unit,title:'Ẩn bài',type:'VOCABULARY',position:1,status:'DRAFT',vocabulary_topic_id:topic})).status,200);
    const hidden=(await api.get('/api/learn/path').set(student)).body.units.find(item=>item.id===unit);
    assert.equal(hidden,undefined);
  });
});
