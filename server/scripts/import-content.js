/**
 * Nhập bộ nội dung TV3 (server/content) vào PostgreSQL theo schema hiện có.
 * Chạy trong thư mục server, sau pnpm migrate và pnpm seed: node --env-file=.env scripts/import-content.js
 * Chạy lại an toàn: chủ đề trùng tên được dùng lại, từ đã có bị bỏ qua, đề/bộ luyện trùng tiêu đề bị bỏ qua.
 * Kiểm tra nội dung trước; có lỗi thì dừng, không ghi gì vào database.
 */
import {query,insert,transaction,pool} from '../backend/db.js';
import {validateContent,loadContent} from './validate-content.js';

const LABELS=['A','B','C','D'];

async function importVocabulary(vocabulary,db){
 let topics=0,words=0;
 for(const t of vocabulary.topics){
  const topicId=await insert("INSERT INTO vocabulary_topics(name,description,status) VALUES(?,?,'PUBLISHED') ON CONFLICT (name) DO UPDATE SET description=EXCLUDED.description,status='PUBLISHED'",[t.name,t.description||''],db);
  topics++;
  for(const [word,pos,ipa,meaning,example] of t.words){
   const rows=await query("INSERT INTO vocabularies(topic_id,word,ipa,part_of_speech,meaning_vi,example_sentence,status) VALUES(?,?,?,?,?,?,'PUBLISHED') ON CONFLICT (topic_id,word) DO NOTHING RETURNING id",[topicId,word,ipa,pos,meaning,example],db);
   words+=rows.length;
  }
 }
 return {topics,words};
}

async function importGrammar(grammar,db){
 const lessonIds=new Map();let created=0;
 for(const t of grammar.topics){
  const topicId=await insert("INSERT INTO grammar_topics(name,description,status) VALUES(?,?,'PUBLISHED') ON CONFLICT (name) DO UPDATE SET description=EXCLUDED.description,status='PUBLISHED'",[t.name,t.description||''],db);
  for(const l of t.lessons){
   const [existing]=await query('SELECT id FROM grammar_lessons WHERE topic_id=? AND title=?',[topicId,l.title],db);
   if(existing){lessonIds.set(l.key,existing.id);continue;}
   lessonIds.set(l.key,await insert("INSERT INTO grammar_lessons(topic_id,title,content,difficulty,status) VALUES(?,?,?,?,'PUBLISHED')",[topicId,l.title,l.content,l.difficulty],db));
   created++;
  }
 }
 return {lessonIds,created};
}

async function insertQuestion(q,part,groupId,lessonIds,db){
 const id=await insert("INSERT INTO questions(part_id,group_id,lesson_id,question_type,question_text,explanation,difficulty,status) VALUES(?,?,?,?,?,?,?,'PUBLISHED')",[part,groupId,q.lesson?lessonIds.get(q.lesson)??null:null,q.type,q.q,q.explanation,q.difficulty||'MEDIUM'],db);
 for(let i=0;i<4;i++)await query('INSERT INTO question_options(question_id,label,option_text,is_correct) VALUES(?,?,?,?)',[id,LABELS[i],q.options[i],LABELS[i]===q.answer],db);
 return id;
}

async function insertGroup(g,part,lessonIds,db){
 const groupId=await insert('INSERT INTO question_groups(part_id,title,instructions) VALUES(?,?,?)',[part,g.title,g.instructions||''],db);
 const passages=part===6?[g.passage]:g.passages;
 for(const [i,p] of passages.entries())await query('INSERT INTO reading_passages(group_id,position,passage_type,title,content,translation_vi) VALUES(?,?,?,?,?,?)',[groupId,i+1,p.type,p.title||'',p.content,p.translation_vi||''],db);
 const ids=[];for(const q of g.questions)ids.push(await insertQuestion(q,part,groupId,lessonIds,db));
 return ids;
}

async function importTest(test,lessonIds){
 return transaction(async db=>{
  const [existing]=await query('SELECT id FROM mock_tests WHERE title=?',[test.title],db);
  if(existing)return null;
  const mockId=await insert("INSERT INTO mock_tests(title,description,time_limit,status) VALUES(?,?,?,'DRAFT')",[test.title,test.description||'',test.time_limit],db);
  const ids=[];
  for(const q of test.part5)ids.push(await insertQuestion(q,5,null,lessonIds,db));
  for(const g of test.part6)ids.push(...await insertGroup(g,6,lessonIds,db));
  for(const g of test.part7)ids.push(...await insertGroup(g,7,lessonIds,db));
  for(const [i,qid] of ids.entries())await query('INSERT INTO mock_test_questions(mock_test_id,question_id,position) VALUES(?,?,?)',[mockId,qid,i+1],db);
  await query("UPDATE mock_tests SET status='PUBLISHED' WHERE id=?",[mockId],db);
  return {id:mockId,questions:ids.length};
 });
}

async function importPracticeSet(set,lessonIds){
 return transaction(async db=>{
  const [existing]=await query('SELECT id FROM practice_sets WHERE title=?',[set.title],db);
  if(existing)return null;
  const setId=await insert("INSERT INTO practice_sets(part_id,title,description,time_limit,status) VALUES(?,?,?,?,'DRAFT')",[set.part,set.title,set.description||'',set.time_limit],db);
  const ids=[];
  if(set.part===5)for(const q of set.questions)ids.push(await insertQuestion(q,5,null,lessonIds,db));
  else for(const g of set.groups)ids.push(...await insertGroup(g,set.part,lessonIds,db));
  for(const [i,qid] of ids.entries())await query('INSERT INTO practice_set_questions(practice_set_id,question_id,position) VALUES(?,?,?)',[setId,qid,i+1],db);
  await query("UPDATE practice_sets SET status='PUBLISHED' WHERE id=?",[setId],db);
  return {id:setId,questions:ids.length};
 });
}

try{
 const content=loadContent();
 const {errors,totals}=validateContent(content);
 if(errors.length){for(const e of errors)console.error(`LỖI ${e}`);throw new Error(`Nội dung có ${errors.length} lỗi, chưa import`);}
 const parts=await query('SELECT id FROM toeic_parts');
 if(parts.length<3)throw new Error('Chưa có toeic_parts 5–7: chạy pnpm seed trước');
 const vocab=await transaction(db=>importVocabulary(content.vocabulary,db));
 const grammar=await transaction(db=>importGrammar(content.grammar,db));
 console.log(`Từ vựng: ${vocab.topics} chủ đề, thêm mới ${vocab.words}/${totals.words} từ`);
 console.log(`Ngữ pháp: thêm mới ${grammar.created} bài`);
 for(const t of content.tests){const r=await importTest(t,grammar.lessonIds);console.log(r?`Đề "${t.title}": id ${r.id}, ${r.questions} câu`:`Đề "${t.title}": đã có, bỏ qua`);}
 for(const s of content.practice.sets){const r=await importPracticeSet(s,grammar.lessonIds);console.log(r?`Bộ luyện "${s.title}": id ${r.id}, ${r.questions} câu`:`Bộ luyện "${s.title}": đã có, bỏ qua`);}
}catch(e){console.error(e.message);process.exitCode=1;}
finally{await pool.end();}
