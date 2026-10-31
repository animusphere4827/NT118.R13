import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import request from 'supertest';
import {applyMigrations} from '../backend/migration-runner.js';

const schema = 'b21_' + randomUUID().replaceAll('-','');
const bootstrap = new pg.Client({connectionString:process.env.DATABASE_URL});
await bootstrap.connect();
await bootstrap.query('BEGIN');
await bootstrap.query('CREATE SCHEMA ' + schema);
await bootstrap.query('SET LOCAL search_path TO ' + schema);
await applyMigrations(bootstrap);
await bootstrap.query('COMMIT');
const connection = new URL(process.env.DATABASE_URL);
connection.searchParams.set('options','-c search_path=' + schema + ' -c TimeZone=UTC');
process.env.DATABASE_URL = connection.toString();
const {app} = await import('../backend/app.js');
const {query,insert,pool} = await import('../backend/db.js');
const {loadPath} = await import('../backend/course.js');
const api = request(app);
after(async () => {
  await pool.end();
  await bootstrap.query('DROP SCHEMA ' + schema + ' CASCADE');
  await bootstrap.end();
});

test('B2.1 Course and Practical Reading use published DB content and private progress',async t => {
  assert.equal((await query('SELECT current_schema() AS name'))[0].name,schema);
  const users = [];
  for (let i=0;i<2;i++) {
    const credentials = {email:'reader'+i+'@example.com',password:'Reading-test-password-123'};
    const registered = await api.post('/api/auth/register').send({...credentials,full_name:'Reader '+i});
    assert.equal(registered.status,201);
    const loggedIn = await api.post('/api/auth/login').send(credentials);
    assert.equal(loggedIn.status,200);
    users.push({id:registered.body.id,token:loggedIn.body.token});
  }
  const student = {Authorization:'Bearer '+users[0].token};
  const other = {Authorization:'Bearer '+users[1].token};
  const getPath = headers => api.get('/api/learn/path').set(headers);

  await t.test('empty path and reading catalog, authentication and strict query validation',async () => {
    assert.equal((await api.get('/api/learn/path')).status,401);
    assert.equal((await api.get('/api/learn/readings')).status,401);
    assert.deepEqual((await getPath(student)).body,{units:[],completed:0,total:0,current_lesson_id:null});
    const empty = await api.get('/api/learn/readings').set(student);
    assert.deepEqual(empty.body,{total:0,rows:[],page:1,limit:20});
    for (const suffix of ['?page=0','?limit=101','?part_id=5','?type=INVALID','?page=1&page=2','?bad=true']) {
      assert.equal((await api.get('/api/learn/readings'+suffix).set(student)).status,422,suffix);
    }
    assert.equal((await api.get('/api/learn/readings/not-an-id').set(student)).status,422);
    assert.equal((await api.get('/api/learn/readings/2147483648').set(student)).status,422);
    assert.equal((await api.get('/api/learn/readings/999').set(student)).status,404);
    assert.equal((await api.put('/api/learn/readings/1/progress').send({completed:true})).status,401);
    assert.equal((await api.post('/api/learn/readings/1/lookups').send({keyword:'invoice'})).status,401);
  });

  await query("INSERT INTO toeic_parts(id,name,test_question_count) VALUES(5,'Part 5',30),(6,'Part 6',16),(7,'Part 7',54)");
  const topic = await insert("INSERT INTO vocabulary_topics(name,status) VALUES('Office','PUBLISHED')");
  const vocabulary = await insert(`INSERT INTO vocabularies(topic_id,word,meaning_vi,status)
    VALUES(?,'invoice','hóa đơn','PUBLISHED')`,[topic]);
  await query("INSERT INTO vocabularies(topic_id,word,meaning_vi,status) VALUES(?,'secret','bí mật','DRAFT')",[topic]);
  const grammarTopic = await insert("INSERT INTO grammar_topics(name,status) VALUES('Tenses','PUBLISHED')");
  const grammar = await insert(`INSERT INTO grammar_lessons(topic_id,title,content,status)
    VALUES(?,'Present tense','Grammar content','PUBLISHED')`,[grammarTopic]);

  async function makeReading(part,title,status='PUBLISHED',mockOnly=false) {
    const group = await insert('INSERT INTO question_groups(part_id,title) VALUES(?,?)',[part,title]);
    await query(`INSERT INTO reading_passages(group_id,position,passage_type,title,content,translation_vi)
      VALUES(?,1,'EMAIL','Invoice letter',?,'Bản dịch riêng')`,[group,"An invoice is ready. The customer's budget is approved."]);
    if (part===7) await query(`INSERT INTO reading_passages(group_id,position,passage_type,title,content)
      VALUES(?,2,'NOTICE','Payment notice','Another invoice will arrive tomorrow.')`,[group]);
    const question = await insert(`INSERT INTO questions(part_id,group_id,question_text,explanation,status)
      VALUES(?,?,'Private question','Private answer explanation',?)`,[part,group,status]);
    if (!mockOnly) {
      const set = await insert('INSERT INTO practice_sets(part_id,title,status) VALUES(?,?,?)',[part,title,status]);
      await query('INSERT INTO practice_set_questions(practice_set_id,question_id,position) VALUES(?,?,1)',[set,question]);
    } else {
      const mock = await insert("INSERT INTO mock_tests(title,status) VALUES(?,'PUBLISHED')",[title]);
      await query('INSERT INTO mock_test_questions(mock_test_id,question_id,position) VALUES(?,?,1)',[mock,question]);
    }
    return group;
  }
  const reading = await makeReading(7,'Office reading');
  const part6 = await makeReading(6,'Part 6 reading');
  const draft = await makeReading(7,'Hidden draft','DRAFT');
  const mockOnly = await makeReading(7,'Mock-only reading','PUBLISHED',true);
  const q5 = await insert(`INSERT INTO questions(part_id,question_text,explanation,status)
    VALUES(5,'Example question','Private explanation','PUBLISHED')`);
  const practice = await insert("INSERT INTO practice_sets(part_id,title,status) VALUES(5,'Part 5 practice','PUBLISHED')");
  await query('INSERT INTO practice_set_questions(practice_set_id,question_id,position) VALUES(?,?,1)',[practice,q5]);
  const mock = await insert("INSERT INTO mock_tests(title,status) VALUES('Course mock','PUBLISHED')");
  await query('INSERT INTO mock_test_questions(mock_test_id,question_id,position) VALUES(?,?,1)',[mock,q5]);
  const unit = await insert("INSERT INTO course_units(slug,title,position,status) VALUES('office','Office',1,'PUBLISHED')");
  const lessons = [];
  for (const [index,[type,column,id]] of [
    ['VOCABULARY','vocabulary_topic_id',topic],['GRAMMAR','grammar_lesson_id',grammar],
    ['READING','reading_group_id',reading],['PRACTICE_PART5','practice_set_id',practice],['MOCK_TEST','mock_test_id',mock]
  ].entries()) {
    lessons.push(await insert(`INSERT INTO course_lessons(unit_id,seed_key,title,type,minutes,position,status,${column})
      VALUES(?,?,?,?,10,?,'PUBLISHED',?)`,[unit,'lesson-'+index,type,type,index+1,id]));
  }

  await t.test('ordered course status, locked detail and published content URLs',async () => {
    const response = await getPath(student);
    assert.equal(response.headers['cache-control'],'no-store');
    assert.equal(response.body.total,5);
    const rows = response.body.units[0].lessons;
    assert.deepEqual(rows.map(row=>row.id),lessons);
    assert.deepEqual(rows.map(row=>row.status),['IN_PROGRESS','LOCKED','LOCKED','LOCKED','LOCKED']);
    assert.equal(rows[2].content_id,reading);
    assert.equal(rows[2].content_url,'/api/learn/readings/'+reading);
    assert.equal((await api.get('/api/learn/path/lessons/'+lessons[0]).set(student)).status,200);
    assert.equal((await api.get('/api/learn/path/lessons/'+lessons[1]).set(student)).status,409);
    assert.equal((await api.get('/api/learn/path/lessons/99999').set(student)).status,404);
    await query("UPDATE course_lessons SET status='DRAFT' WHERE id=?",[lessons[1]]);
    assert.equal((await getPath(student)).body.total,4);
    assert.equal((await api.get('/api/learn/path/lessons/'+lessons[1]).set(student)).status,404);
    await query("UPDATE course_lessons SET status='PUBLISHED' WHERE id=?",[lessons[1]]);
    await query("UPDATE vocabulary_topics SET status='DRAFT' WHERE id=?",[topic]);
    assert.equal((await getPath(student)).body.total,4);
    await query("UPDATE vocabulary_topics SET status='PUBLISHED' WHERE id=?",[topic]);
  });

  await t.test('reading filters, exact totals on empty pages and no duplicate groups',async () => {
    const response = await api.get('/api/learn/readings?part_id=7&type=NOTICE&limit=1').set(student);
    assert.equal(response.status,200,JSON.stringify(response.body));
    assert.equal(response.body.total,1);
    assert.equal(response.body.rows[0].id,reading);
    assert.equal(response.body.rows[0].passage_count,2);
    assert.deepEqual(response.body.rows[0].passage_types,['EMAIL','NOTICE']);
    const beyond = await api.get('/api/learn/readings?part_id=7&page=999').set(student);
    assert.equal(beyond.body.total,1);
    assert.deepEqual(beyond.body.rows,[]);
    assert.equal((await api.get('/api/learn/readings?part_id=6').set(student)).body.rows[0].id,part6);
    assert.equal((await api.get('/api/learn/readings?search=Invoice').set(student)).body.total,2);
    assert.equal((await api.get('/api/learn/readings?search=%25').set(student)).body.total,0);
    for (const hidden of [draft,mockOnly]) {
      assert.equal((await api.get('/api/learn/readings/'+hidden).set(student)).status,404);
      assert.equal((await api.put('/api/learn/readings/'+hidden+'/progress').set(student).send({completed:true})).status,404);
      assert.equal((await api.post('/api/learn/readings/'+hidden+'/lookups').set(student).send({keyword:'invoice'})).status,404);
    }
  });

  await t.test('reading detail orders passages, translation is opt-in and answer keys stay private',async () => {
    const response = await api.get('/api/learn/readings/'+reading).set(student);
    assert.equal(response.status,200);
    assert.deepEqual(response.body.passages.map(p=>p.position),[1,2]);
    const text = JSON.stringify(response.body);
    for (const privateField of ['translation_vi','question_text','explanation','is_correct','Private answer']) {
      assert.ok(!text.includes(privateField),privateField);
    }
    const translated = await api.get('/api/learn/readings/'+reading+'?translation=true').set(student);
    assert.equal(translated.body.passages[0].translation_vi,'Bản dịch riêng');
    assert.equal((await api.get('/api/learn/readings/'+reading+'?translation=1').set(student)).status,422);
  });

  await t.test('in-article lookups normalize words, return dictionary entries and keep history private',async () => {
    const lookup = keyword=>api.post('/api/learn/readings/'+reading+'/lookups').set(student).send({keyword});
    const result = await lookup('INVOICE');
    assert.equal(result.status,201,JSON.stringify(result.body));
    assert.equal(result.body.rows[0].id,vocabulary);
    assert.equal(result.body.lookup.lookup_count,1);
    const concurrent = await Promise.all([lookup('invoice'),lookup('invoice'),lookup('invoice')]);
    assert.ok(concurrent.every(result=>result.status===201));
    const [history] = await query('SELECT * FROM lookup_history WHERE user_id=?',[users[0].id]);
    assert.equal(history.lookup_count,4);
    assert.equal(history.source,'READING');
    assert.equal((await api.get('/api/learn/lookups').set(other)).body.total,0);
    for (const word of ['voice','invoice%','outside','invoice ready','']) {
      assert.equal((await lookup(word)).status,422,word);
    }
    const unknown = await lookup('ready');
    assert.equal(unknown.status,201);
    assert.deepEqual(unknown.body.rows,[]);
    assert.equal(unknown.body.lookup.vocabulary_id,null);
    assert.equal((await lookup('customer’s')).status,201);
    await query("UPDATE vocabulary_topics SET status='DRAFT' WHERE id=?",[topic]);
    assert.deepEqual((await lookup('invoice')).body.rows,[]);
    await query("UPDATE vocabulary_topics SET status='PUBLISHED' WHERE id=?",[topic]);
    const before = (await query('SELECT lookup_count FROM lookup_history WHERE id=?',[history.id]))[0].lookup_count;
    await api.get('/api/learn/readings/'+reading).set(student);
    assert.equal((await query('SELECT lookup_count FROM lookup_history WHERE id=?',[history.id]))[0].lookup_count,before);
  });

  await t.test('progress unlocks sequentially, concurrent reading completion is idempotent and account-scoped',async () => {
    await api.put('/api/learn/vocabulary/'+vocabulary+'/progress').set(student).send({status:'MASTERED',is_favorite:false});
    assert.equal((await getPath(student)).body.current_lesson_id,lessons[0]);
    const lessonUrl='/api/learn/path/lessons/'+lessons[0]+'/vocabulary';
    assert.equal((await api.post(lessonUrl+'/start').set(student).send({})).status,200);
    await api.put(lessonUrl+'/study').set(student).send({vocabulary_ids:[vocabulary]});
    const check=await api.post(lessonUrl+'/checks').set(student).send({});
    const checked=await api.post('/api/learn/vocabulary-checks/'+check.body.id+'/submit').set(student)
      .send({answers:[{vocabulary_id:vocabulary,answer:'invoice'}]});
    assert.equal(checked.status,200,JSON.stringify(checked.body));
    assert.equal(checked.body.lesson_completed,true);
    assert.equal((await getPath(student)).body.current_lesson_id,lessons[1]);
    await api.put('/api/learn/grammar/'+grammar+'/progress').set(student).send({progress_percent:100});
    assert.equal((await getPath(student)).body.current_lesson_id,lessons[2]);
    const progress = body=>api.put('/api/learn/readings/'+reading+'/progress').set(student).send(body);
    assert.equal((await progress({completed:false})).status,422);
    assert.equal((await progress({completed:true,user_id:users[1].id})).status,422);
    const results = await Promise.all([progress({completed:true}),progress({completed:true}),progress({completed:true})]);
    assert.ok(results.every(result=>result.status===200));
    assert.equal(new Set(results.map(result=>result.body.completed_at)).size,1);
    assert.equal((await query('SELECT COUNT(*) AS n FROM user_reading_progress'))[0].n,1);
    assert.equal((await getPath(student)).body.current_lesson_id,lessons[3]);
    assert.equal((await api.get('/api/learn/readings/'+reading).set(other)).body.completed_at,null);
    assert.equal((await getPath(other)).body.completed,0);
    await query(`INSERT INTO practice_attempts(user_id,practice_set_id,status,total_questions,deadline_at)
      VALUES(?,?,'IN_PROGRESS',1,now()+interval '10 minutes')`,[users[0].id,practice]);
    assert.equal((await getPath(student)).body.current_lesson_id,lessons[3]);
    await query("UPDATE practice_attempts SET status='SUBMITTED',accuracy=80 WHERE user_id=?",[users[0].id]);
    assert.equal((await getPath(student)).body.current_lesson_id,lessons[4]);
    const mockAttempt=await insert(`INSERT INTO mock_test_attempts(user_id,mock_test_id,status,total_questions,deadline_at)
      VALUES(?,?,'SUBMITTED',1,now())`,[users[0].id,mock]);
    assert.equal((await getPath(student)).body.completed,4);
    await query(`INSERT INTO mock_test_answers(attempt_id,question_id,position,snapshot,selected_label)
      VALUES(?,?,1,'{}','A')`,[mockAttempt,q5]);
    const done = (await getPath(student)).body;
    assert.equal(done.completed,5);
    assert.equal(done.current_lesson_id,null);
    assert.ok(done.units[0].lessons.every(lesson=>lesson.status==='DONE'));
  });

  await t.test('a larger curriculum is loaded in one database query',async () => {
    await query(`INSERT INTO course_lessons(unit_id,seed_key,title,type,minutes,position,status,grammar_lesson_id)
      SELECT ?,'scale-' || n,'Grammar ' || n,'GRAMMAR',10,n,'PUBLISHED',?
      FROM generate_series(100,299) AS n`,[unit,grammar]);
    const original = pool.query;
    let statements = 0;
    pool.query = function (...args) {
      statements++;
      return original.apply(this,args);
    };
    try {
      const path = await loadPath(users[0].id);
      assert.equal(path.total,205);
      assert.equal(path.completed,205);
      assert.equal(statements,1);
    } finally {
      pool.query = original;
      await query("DELETE FROM course_lessons WHERE seed_key LIKE 'scale-%'");
    }
  });

  await t.test('unpublishing content immediately hides readings and course lessons',async () => {
    await query("UPDATE practice_sets SET status='DRAFT' WHERE part_id=7");
    assert.equal((await api.get('/api/learn/readings/'+reading).set(student)).status,404);
    assert.equal((await getPath(student)).body.total,4);
    await query("UPDATE course_units SET status='DRAFT' WHERE id=?",[unit]);
    assert.equal((await getPath(student)).body.total,0);
    assert.equal((await api.get('/api/learn/path/lessons/'+lessons[0]).set(student)).status,404);
    await query("UPDATE users SET status='LOCKED' WHERE id=?",[users[0].id]);
    assert.equal((await getPath(student)).status,401);
    assert.equal((await api.get('/api/learn/readings').set(student)).status,401);
  });
});
