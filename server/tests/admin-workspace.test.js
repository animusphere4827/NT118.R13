import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import pg from 'pg';
import request from 'supertest';
import {applyMigrations} from '../backend/migration-runner.js';

const schema='b23_'+randomUUID().replaceAll('-','');
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
const options=()=>[...'ABCD'].map((label,index)=>({label,option_text:'Option '+label,is_correct:index===0}));
const reading=()=>({title:'Office payment',part_id:7,instructions:'Read the messages.',status:'PUBLISHED',passages:[
  {title:'Invoice',passage_type:'EMAIL',content:'An invoice will arrive tomorrow.',translation_vi:'Hóa đơn sẽ đến ngày mai.',image_url:''},
  {title:'Payment notice',passage_type:'NOTICE',content:'Please pay by Friday.',translation_vi:'',image_url:''}
],questions:[{question_type:'DETAIL',question_text:'When should we pay?',explanation:'The notice says by Friday.',difficulty:'EASY',options:options()},
  {question_type:'DETAIL',question_text:'What will arrive?',explanation:'The email says an invoice.',difficulty:'EASY',options:options()}]});
const readingForm=record=>({title:record.title,part_id:record.part_id,instructions:record.instructions,status:record.status,
  passages:record.passages.map(({id,title,passage_type,content,translation_vi,image_url})=>({id,title,passage_type,content,translation_vi,image_url})),
  questions:record.questions.map(({id,question_type,question_text,explanation,difficulty,options})=>({id,question_type,question_text,explanation,difficulty,options}))});
const examForm=record=>({title:record.title,description:record.description,time_limit:record.time_limit,status:record.status,question_ids:record.question_ids,
  ...(record.test_format?{test_format:record.test_format}:{part_id:record.part_id})});

test('B2.3 admin authoring, publishing, stale updates and student visibility',async t=>{
  assert.equal((await query('SELECT current_schema() AS name'))[0].name,schema);
  const users=[];
  for(let index=0;index<2;index++){
    const credentials={email:'workspace'+index+'@example.com',password:'Workspace-password-123'};
    const created=await api.post('/api/auth/register').send({...credentials,full_name:'Workspace '+index});
    assert.equal(created.status,201);
    if(index===0)await query("UPDATE users SET role='ADMIN' WHERE id=?",[created.body.id]);
    const login=await api.post('/api/auth/login').send(credentials);
    users.push({Authorization:'Bearer '+login.body.token,'X-Requested-With':'TOEICAdmin'});
  }
  const [admin,student]=users;
  await query("INSERT INTO toeic_parts(id,name,test_question_count) VALUES(5,'Part 5',30),(6,'Part 6',16),(7,'Part 7',54)");
  const get=path=>api.get('/api'+path).set(admin);
  const post=(path,body)=>api.post('/api'+path).set(admin).send(body);
  const put=(path,body,version)=>api.put('/api'+path).set(admin).set(version?{'If-Match':version}:{}).send(body);
  let group,exam,attempt,word,topic;

  await t.test('admin routes enforce roles and metadata contains usable validation',async()=>{
    assert.equal((await api.get('/api/admin/content/readings/1')).status,401);
    assert.equal((await api.post('/api/admin/content/readings').set(student).send(reading())).status,403);
    assert.equal((await get('/admin/content/exams/bad/1')).status,404);
    assert.equal((await get('/admin/content/readings/2147483648')).status,422);
    const meta=await get('/admin/meta');
    assert.equal(meta.status,200);
    assert.equal(meta.headers['cache-control'],'no-store');
    const name=meta.body.resources.vocabularies.fields.find(field=>field.name==='word');
    assert.equal(name.required,true);
    assert.equal(name.maxLength,150);
    assert.ok(meta.body.resources.mock_tests.fields.some(field=>field.name==='test_format'));
  });

  await t.test('vocabulary CRUD supports literal search, filters and optimistic conflict protection',async()=>{
    topic=(await post('/admin/vocabulary_topics',{name:'Office',status:'PUBLISHED'})).body.id;
    const body={topic_id:topic,word:'invoice%_literal',meaning_vi:'hóa đơn',status:'PUBLISHED'};
    word=(await post('/admin/vocabularies',body)).body.id;
    assert.ok(word);
    assert.equal((await post('/admin/vocabularies',{...body,word:'x'.repeat(151)})).status,422);
    assert.equal((await post('/admin/vocabularies',{...body,topic_id:2147483648})).status,422);
    const filtered=await get('/admin/vocabularies?topic_id='+topic+'&status=PUBLISHED&search=%25_');
    assert.equal(filtered.body.total,1);
    assert.equal(filtered.body.rows[0].id,word);
    const detail=(await get('/admin/vocabularies/'+word)).body;
    assert.equal((await put('/admin/vocabularies/'+word,{...body,meaning_vi:'hóa đơn mới'},detail.edit_version)).status,200);
    assert.equal((await put('/admin/vocabularies/'+word,{...body,meaning_vi:'stale overwrite'},detail.edit_version)).status,409);
    assert.equal((await api.get('/api/learn/catalog/vocabularies/'+word).set(student)).body.meaning_vi,'hóa đơn mới');
    await put('/admin/vocabulary_topics/'+topic,{name:'Office',status:'DRAFT'});
    assert.equal((await api.get('/api/learn/catalog/vocabularies/'+word).set(student)).status,404);
    assert.equal((await api.get('/api/learn/catalog/vocabularies?topic_id='+topic).set(student)).body.total,0);
    await put('/admin/vocabulary_topics/'+topic,{name:'Office',status:'PUBLISHED'});
    const empty=await get('/admin/vocabularies?topic_id='+topic+'&page=999');
    assert.equal(empty.body.total,1);assert.deepEqual(empty.body.rows,[]);
    assert.equal((await get('/admin/vocabularies?status=INVALID')).status,422);
    assert.equal((await get('/admin/vocabularies?bad_filter=1')).status,422);
    assert.equal((await get('/admin/vocabularies?search=%23'+word)).body.rows[0].id,word);
  });

  await t.test('reading authoring saves the entire group atomically and rejects invalid nested content',async()=>{
    const invalid=reading();invalid.questions[0].options[1].is_correct=true;
    assert.equal((await post('/admin/content/readings',invalid)).status,422);
    assert.equal((await query('SELECT COUNT(*) AS n FROM question_groups'))[0].n,0);
    assert.equal((await post('/admin/content/readings',{...reading(),part_id:6})).status,422);
    const created=await post('/admin/content/readings',reading());
    assert.equal(created.status,201,JSON.stringify(created.body));
    group=created.body;
    assert.equal(group.passages.length,2);assert.equal(group.questions.length,2);
    assert.equal(group.questions[0].options.filter(option=>option.is_correct).length,1);
    assert.ok(group.edit_version);
    const missing=await put('/admin/content/readings/'+group.id,readingForm(group));
    assert.equal(missing.status,428);
    const foreign=readingForm(group);foreign.passages[0].id=999999;
    assert.equal((await put('/admin/content/readings/'+group.id,foreign,group.edit_version)).status,422);
    assert.equal((await get('/admin/content/readings/'+group.id)).body.edit_version,group.edit_version);
  });

  await t.test('exam builder keeps order and publication makes the reading visible to students',async()=>{
    const body={title:'Office practice',description:'Grouped authoring test',part_id:7,time_limit:600,status:'PUBLISHED',question_ids:group.questions.map(q=>q.id).reverse()};
    const created=await post('/admin/content/exams/practice',body);
    assert.equal(created.status,201,JSON.stringify(created.body));
    exam=created.body;
    assert.deepEqual(exam.question_ids,body.question_ids);
    const publicReading=await api.get('/api/learn/readings/'+group.id).set(student);
    assert.equal(publicReading.status,200);assert.equal(publicReading.body.passages.length,2);
    const listed=await get('/admin/practice_sets?search=Office');
    assert.equal(listed.body.rows[0].question_count,2);
    assert.equal((await post('/admin/content/exams/practice',{...body,part_id:5})).status,422);
    assert.equal((await post('/admin/content/exams/practice',{...body,question_ids:[body.question_ids[0],body.question_ids[0]]})).status,422);
    assert.equal((await post('/admin/content/exams/practice',{...body,question_ids:[999999]})).status,422);
    assert.equal((await post('/admin/practice_set_questions',{practice_set_id:exam.id,question_id:body.question_ids[0],position:3})).status,409);
  });

  await t.test('published reading updates are atomic and preserve existing attempt snapshots',async()=>{
    const started=await api.post('/api/learn/practice/'+exam.id+'/attempts').set(student).send({});
    assert.equal(started.status,201);attempt=started.body.id;
    const old=await api.get('/api/learn/practice/attempts/'+attempt).set(student);
    const edited=readingForm(group);
    edited.passages=edited.passages.reverse();edited.passages[0].content='Updated notice: please pay on Monday.';
    edited.questions[0].explanation='New explanation after start';
    const saved=await put('/admin/content/readings/'+group.id,edited,group.edit_version);
    assert.equal(saved.status,200,JSON.stringify(saved.body));
    const stale=await put('/admin/content/readings/'+group.id,edited,group.edit_version);
    assert.equal(stale.status,409);
    group=saved.body;
    assert.equal(group.passages[0].id,edited.passages[0].id);
    const current=await api.get('/api/learn/readings/'+group.id).set(student);
    assert.equal(current.body.passages[0].content,edited.passages[0].content);
    const submitted=await api.post('/api/learn/practice/attempts/'+attempt+'/submit').set(student).send({answers:[]});
    assert.equal(submitted.status,200);
    const review=await api.get('/api/learn/practice/attempts/'+attempt).set(student);
    assert.equal(review.body.questions[0].passages[0].content,old.body.questions[0].passages[0].content);
    const remove=readingForm(group);remove.questions=[];
    assert.equal((await put('/admin/content/readings/'+group.id,remove,group.edit_version)).status,422);
    assert.equal((await put('/admin/content/readings/'+group.id,{...readingForm(group),status:'DRAFT'},group.edit_version)).status,409);
  });

  await t.test('stale concurrent exam saves cannot overwrite each other; dependencies and history survive deletion',async()=>{
    exam=(await get('/admin/content/exams/practice/'+exam.id)).body;
    const body=examForm(exam);
    const responses=await Promise.all([
      put('/admin/content/exams/practice/'+exam.id,{...body,title:'Version one'},exam.edit_version),
      put('/admin/content/exams/practice/'+exam.id,{...body,title:'Version two'},exam.edit_version)
    ]);
    assert.deepEqual(responses.map(response=>response.status).sort(),[200,409]);
    exam=responses.find(response=>response.status===200).body;
    const draft=await put('/admin/content/exams/practice/'+exam.id,{...examForm(exam),status:'DRAFT'},exam.edit_version);
    assert.equal(draft.status,200);exam=draft.body;
    const hidden=await put('/admin/content/readings/'+group.id,{...readingForm(group),status:'DRAFT'},group.edit_version);
    assert.equal(hidden.status,200);group=hidden.body;
    assert.equal((await api.get('/api/learn/readings/'+group.id).set(student)).status,404);
    const before=(await get('/admin/content/readings/'+group.id)).body;
    assert.equal((await api.delete('/api/admin/content/readings/'+group.id).set(admin).set('If-Match',before.edit_version)).status,409);
    assert.equal((await get('/admin/content/readings/'+group.id)).body.questions.length,2);
    assert.equal((await api.delete('/api/admin/practice_sets/'+exam.id).set(admin)).status,409);
    assert.equal((await api.get('/api/learn/practice/attempts/'+attempt).set(student)).status,200);
  });

  await t.test('new unused draft readings can be deleted as a unit with an audit record',async()=>{
    const created=await post('/admin/content/readings',{...reading(),title:'Disposable draft',status:'DRAFT'});
    assert.equal(created.status,201);
    const row=created.body;
    assert.equal((await api.delete('/api/admin/content/readings/'+row.id).set(admin).set('If-Match','"'+row.edit_version+'"')).status,204);
    assert.equal((await get('/admin/content/readings/'+row.id)).status,404);
    assert.equal((await query('SELECT COUNT(*) AS n FROM questions WHERE group_id=?',[row.id]))[0].n,0);
    assert.equal((await query("SELECT COUNT(*) AS n FROM audit_logs WHERE resource='question_groups' AND action='DELETE' AND record_id=?",[row.id]))[0].n,1);
  });

  await t.test('full papers require 100 ready questions, correct Part distribution and 75 minutes',async()=>{
    const imported=spawnSync(process.execPath,['scripts/import-content.js'],{cwd:fileURLToPath(new URL('../',import.meta.url)),env:{...process.env},encoding:'utf8',timeout:30000});
    assert.equal(imported.status,0,imported.stderr);
    const [source]=await query("SELECT id FROM mock_tests WHERE title LIKE '%số 1' ORDER BY id LIMIT 1");
    const questions=await query('SELECT question_id FROM mock_test_questions WHERE mock_test_id=? ORDER BY position',[source.id]);
    const body={title:'Full authoring test',description:'100 question paper',test_format:'FULL',time_limit:4500,status:'PUBLISHED',question_ids:questions.map(q=>q.question_id)};
    assert.equal((await post('/admin/content/exams/mock',{...body,question_ids:body.question_ids.slice(1)})).status,422);
    assert.equal((await post('/admin/content/exams/mock',{...body,time_limit:600})).status,422);
    const created=await post('/admin/content/exams/mock',body);
    assert.equal(created.status,201,JSON.stringify(created.body));
    assert.equal(created.body.entries.length,100);
    assert.equal(created.body.test_format,'FULL');
    const genericBody={title:'Retitled full paper',description:'Compatibility',status:'PUBLISHED',time_limit:4500};
    assert.equal((await put('/admin/mock_tests/'+created.body.id,genericBody)).status,200);
    assert.equal((await get('/admin/mock_tests/'+created.body.id)).body.test_format,'FULL');
    const [q]=await query('SELECT * FROM questions WHERE id=?',[body.question_ids[0]]);
    const optionRows=await query('SELECT label,option_text,is_correct FROM question_options WHERE question_id=? ORDER BY label',[q.id]);
    assert.equal((await put('/admin/questions/'+q.id,{part_id:6,group_id:group.id,lesson_id:q.lesson_id,question_type:q.question_type,
      question_text:q.question_text,explanation:q.explanation,difficulty:q.difficulty,status:q.status,options:optionRows})).status,422);
    const [extra]=await query("SELECT id,group_id FROM questions WHERE part_id=7 AND status='PUBLISHED' AND NOT(id=ANY(?::integer[])) ORDER BY id LIMIT 1",[body.question_ids]);
    const bad=await post('/admin/content/exams/mock',{...body,question_ids:[...body.question_ids.slice(1),extra.id]});
    assert.equal(bad.status,422);assert.match(bad.body.error,/30\/16\/54/);
    const changed=await put('/admin/questions/'+q.id,{part_id:7,group_id:extra.group_id,lesson_id:q.lesson_id,question_type:q.question_type,question_text:q.question_text,explanation:q.explanation,difficulty:q.difficulty,status:q.status,options:optionRows});
    assert.equal(changed.status,409);assert.match(changed.body.error,/đề đầy đủ/);
  });
});
