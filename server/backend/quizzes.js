import {Router} from 'express';
import {z} from 'zod';
import {query,insert,transaction} from './db.js';
import {parseId} from './resources.js';
import {quizSnapshot,publicQuizQuestion,gradeQuizAnswer,quizFeedback} from './quiz-model.js';

const questionId=z.number().int().min(1).max(2147483647);
const answerBody=z.object({question_id:questionId,answer:z.unknown().refine(value=>value!==undefined,'Missing answer')}).strict();
const submitBody=z.object({answers:z.array(answerBody).max(50).default([])}).strict();
const paging={page:z.coerce.number().int().min(1).max(100000).default(1),limit:z.coerce.number().int().min(1).max(100).default(20)};
const filterId=z.coerce.number().int().min(1).max(2147483647).optional();
const catalogQuery=z.object({...paging,topic_id:filterId,lesson_id:filterId,type:z.enum(['FILL_BLANK','MATCH','ORDER']).optional()}).strict();
const historyQuery=z.object({...paging,content_id:filterId,status:z.enum(['IN_PROGRESS','SUBMITTED']).optional()}).strict();
const visible=`s.status='PUBLISHED' AND EXISTS (SELECT 1 FROM quiz_questions q WHERE q.quiz_set_id=s.id)
  AND (s.vocabulary_topic_id IS NULL OR EXISTS (SELECT 1 FROM vocabulary_topics t WHERE t.id=s.vocabulary_topic_id AND t.status='PUBLISHED'))
  AND (s.grammar_lesson_id IS NULL OR EXISTS (SELECT 1 FROM grammar_lessons l JOIN grammar_topics t ON t.id=l.topic_id
    WHERE l.id=s.grammar_lesson_id AND l.status='PUBLISHED' AND t.status='PUBLISHED'))`;
const fail=(status,message)=>{throw Object.assign(new Error(message),{status});};
const percent=(correct,total)=>Number((correct/total*100).toFixed(2));

async function getAttempt(id,userId,db,skipLocked=false) {
  const [attempt]=await query(`SELECT * FROM quiz_attempts WHERE id=? ${userId===undefined ? '' : 'AND user_id=?'}
    FOR UPDATE ${skipLocked ? 'SKIP LOCKED' : ''}`,[id,...(userId===undefined ? [] : [userId])],db);
  if (!attempt && !skipLocked) fail(404,'Không tìm thấy bài làm');
  return attempt;
}
const getAnswers=(id,db)=>query('SELECT * FROM quiz_answers WHERE attempt_id=? ORDER BY position',[id],db);
const now=async db=>(await query('SELECT clock_timestamp() AS time',[],db))[0].time;

async function finishQuiz(attempt,rows,incoming,expired,db) {
  if (attempt.status==='SUBMITTED') return attempt.result;
  const changes=new Map(incoming.map(row=>[row.question_id,row.answer]));
  for (const row of rows) {
    if (expired || !changes.has(row.question_id)) continue;
    const graded=gradeQuizAnswer(row.snapshot,changes.get(row.question_id));
    if (row.answered_at && JSON.stringify(row.answer)!==JSON.stringify(graded.answer)) fail(409,'Câu hỏi đã chấm, không thể đổi đáp án');
    row.answer=graded.answer;
    row.is_correct=graded.is_correct;
  }
  await query(`UPDATE quiz_answers a SET answer=v.answer,is_correct=COALESCE(v.correct,FALSE),
      answered_at=CASE WHEN v.answer IS NULL THEN a.answered_at ELSE COALESCE(a.answered_at,clock_timestamp()) END
    FROM jsonb_to_recordset(?::jsonb) AS v(id integer,answer jsonb,correct boolean) WHERE a.id=v.id`,
  [JSON.stringify(rows.map(row=>({id:row.id,answer:row.answer,correct:row.is_correct})))],db);
  const correct=rows.filter(row=>row.is_correct).length;
  const result={attempt_id:attempt.id,kind:'quiz',total_questions:rows.length,correct_count:correct,
    accuracy:percent(correct,rows.length),expired,by_type:['FILL_BLANK','MATCH','ORDER'].map(type=>{
      const questions=rows.filter(row=>row.snapshot.type===type);
      const right=questions.filter(row=>row.is_correct).length;
      return {type,total_questions:questions.length,correct_count:right,accuracy:questions.length ? percent(right,questions.length) : 0};
    })};
  await query(`UPDATE quiz_attempts SET status='SUBMITTED',correct_count=?,accuracy=?,submitted_at=clock_timestamp(),result=? WHERE id=?`,
  [correct,result.accuracy,JSON.stringify(result),attempt.id],db);
  return result;
}

export async function finalizeExpiredQuizzes(batchSize=25) {
  if (!Number.isInteger(batchSize) || batchSize<1 || batchSize>100) throw new Error('Invalid attempt batch size');
  const expired=await query(`SELECT id FROM quiz_attempts WHERE status='IN_PROGRESS' AND deadline_at<=clock_timestamp()
    ORDER BY deadline_at LIMIT ? FOR UPDATE SKIP LOCKED`,[batchSize]);
  let completed=0;
  for (const row of expired) completed+=await transaction(async db=>{
    const attempt=await getAttempt(row.id,undefined,db,true);
    if (!attempt || attempt.status==='SUBMITTED' || new Date(attempt.deadline_at)>await now(db)) return 0;
    await finishQuiz(attempt,await getAnswers(attempt.id,db),[],true,db);
    return 1;
  });
  return completed;
}

export function registerQuizRoutes(app) {
  const router=Router();
  router.get('/quizzes',async (req,res)=>{
    const {page,limit,topic_id,lesson_id,type}=catalogQuery.parse(req.query);
    const where=[visible];
    const params=[];
    if (topic_id) {where.push('s.vocabulary_topic_id=?');params.push(topic_id);}
    if (lesson_id) {where.push('s.grammar_lesson_id=?');params.push(lesson_id);}
    if (type) {where.push('EXISTS (SELECT 1 FROM quiz_questions q WHERE q.quiz_set_id=s.id AND q.type=?)');params.push(type);}
    const [result]=await query(`WITH filtered AS (
        SELECT s.id,s.title,s.description,s.time_limit,s.vocabulary_topic_id,s.grammar_lesson_id,
          (SELECT COUNT(*) FROM quiz_questions q WHERE q.quiz_set_id=s.id) AS question_count,
          ARRAY(SELECT DISTINCT q.type FROM quiz_questions q WHERE q.quiz_set_id=s.id ORDER BY q.type) AS types
        FROM quiz_sets s WHERE ${where.join(' AND ')}
      ), page AS (SELECT * FROM filtered ORDER BY id LIMIT ? OFFSET ?)
      SELECT (SELECT COUNT(*) FROM filtered) AS total,
        COALESCE((SELECT jsonb_agg(page ORDER BY id) FROM page),'[]'::jsonb) AS rows`,[...params,limit,(page-1)*limit]);
    res.json({...result,page,limit});
  });

  router.get('/quizzes/:id',async (req,res)=>{
    const [set]=await query(`SELECT s.id,s.title,s.description,s.time_limit,s.vocabulary_topic_id,s.grammar_lesson_id,
      (SELECT COUNT(*) FROM quiz_questions q WHERE q.quiz_set_id=s.id) AS question_count,
      ARRAY(SELECT DISTINCT q.type FROM quiz_questions q WHERE q.quiz_set_id=s.id ORDER BY q.type) AS types
      FROM quiz_sets s WHERE s.id=? AND ${visible}`,[parseId(req.params.id)]);
    if (!set) fail(404,'Không tìm thấy bộ quiz đã xuất bản');
    res.json(set);
  });

  router.post('/quizzes/:id/attempts',async (req,res)=>{
    const parentId=parseId(req.params.id);
    const result=await transaction(async db=>{
      await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ');
      const [set]=await query(`SELECT s.* FROM quiz_sets s WHERE s.id=? AND ${visible} FOR SHARE`,[parentId],db);
      if (!set) fail(404,'Không tìm thấy bộ quiz đã xuất bản');
      const rows=await query('SELECT * FROM quiz_questions WHERE quiz_set_id=? ORDER BY position',[parentId],db);
      if (!rows.length || rows.length>50) fail(409,'Bộ quiz chưa sẵn sàng');
      let snapshots;
      try {snapshots=rows.map(quizSnapshot);}
      catch {fail(409,'Nội dung quiz không hợp lệ, hãy báo quản trị viên');}
      const id=await insert(`WITH timing AS (SELECT clock_timestamp() AS started)
        INSERT INTO quiz_attempts(user_id,quiz_set_id,content_title,total_questions,started_at,deadline_at)
        SELECT ?,?,?,?,started,started+make_interval(secs=>?::integer) FROM timing`,[req.user.id,parentId,set.title,rows.length,set.time_limit],db);
      await query(`INSERT INTO quiz_answers(attempt_id,question_id,position,snapshot)
        SELECT ?,v.question_id,v.position,v.snapshot FROM jsonb_to_recordset(?::jsonb)
        AS v(question_id integer,position integer,snapshot jsonb)`,
      [id,JSON.stringify(rows.map((row,index)=>({question_id:row.id,position:row.position,snapshot:snapshots[index]})))],db);
      const [attempt]=await query('SELECT id,started_at,deadline_at,clock_timestamp() AS server_time FROM quiz_attempts WHERE id=?',[id],db);
      return attempt;
    });
    res.status(201).json(result);
  });

  router.get('/quiz/attempts',async (req,res)=>{
    const {page,limit,content_id,status}=historyQuery.parse(req.query);
    const where=['user_id=?'];
    const params=[req.user.id];
    if (content_id) {where.push('quiz_set_id=?');params.push(content_id);}
    if (status) {where.push('status=?');params.push(status);}
    const [result]=await query(`WITH filtered AS (
        SELECT id,quiz_set_id,content_title,status,total_questions,correct_count,accuracy,started_at,deadline_at,submitted_at
        FROM quiz_attempts WHERE ${where.join(' AND ')}
      ), page AS (SELECT * FROM filtered ORDER BY started_at DESC,id DESC LIMIT ? OFFSET ?)
      SELECT (SELECT COUNT(*) FROM filtered) AS total,
        COALESCE((SELECT jsonb_agg(page ORDER BY started_at DESC,id DESC) FROM page),'[]'::jsonb) AS rows`,
    [...params,limit,(page-1)*limit]);
    res.json({...result,page,limit});
  });

  router.get('/quiz/attempts/:id',async (req,res)=>{
    const id=parseId(req.params.id);
    const result=await transaction(async db=>{
      let attempt=await getAttempt(id,req.user.id,db);
      const time=await now(db);
      let rows=await getAnswers(id,db);
      if (attempt.status==='IN_PROGRESS' && new Date(attempt.deadline_at)<=time) {
        await finishQuiz(attempt,rows,[],true,db);
        [attempt]=await query('SELECT * FROM quiz_attempts WHERE id=?',[id],db);
        rows=await getAnswers(id,db);
      }
      return {attempt,server_time:time,questions:rows.map(row=>({
        ...publicQuizQuestion(row.snapshot,row.position),answer:row.answer,
        feedback:row.answered_at || attempt.status==='SUBMITTED' ? quizFeedback(row) : null
      }))};
    });
    res.json(result);
  });

  router.post('/quiz/attempts/:id/answers',async (req,res)=>{
    const id=parseId(req.params.id);
    const body=answerBody.parse(req.body);
    const result=await transaction(async db=>{
      const attempt=await getAttempt(id,req.user.id,db);
      const [row]=await query('SELECT * FROM quiz_answers WHERE attempt_id=? AND question_id=?',[id,body.question_id],db);
      if (!row) fail(422,'Câu hỏi không thuộc bài làm');
      const graded=gradeQuizAnswer(row.snapshot,body.answer);
      if (row.answered_at) {
        if (JSON.stringify(row.answer)!==JSON.stringify(graded.answer)) fail(409,'Câu hỏi đã chấm, không thể đổi đáp án');
        return quizFeedback(row);
      }
      if (attempt.status==='SUBMITTED' || new Date(attempt.deadline_at)<=await now(db)) fail(409,'Bài làm đã nộp hoặc hết giờ');
      await query('UPDATE quiz_answers SET answer=?,is_correct=?,answered_at=clock_timestamp() WHERE id=?',
      [JSON.stringify(graded.answer),graded.is_correct,row.id],db);
      return quizFeedback({...row,...graded});
    });
    res.json(result);
  });

  router.post('/quiz/attempts/:id/submit',async (req,res)=>{
    const id=parseId(req.params.id);
    const body=submitBody.parse(req.body);
    if (new Set(body.answers.map(row=>row.question_id)).size!==body.answers.length) fail(422,'Câu trả lời bị trùng');
    const result=await transaction(async db=>{
      const attempt=await getAttempt(id,req.user.id,db);
      const rows=await getAnswers(id,db);
      if (attempt.status==='SUBMITTED') return attempt.result;
      const ids=new Set(rows.map(row=>row.question_id));
      if (body.answers.some(row=>!ids.has(row.question_id))) fail(422,'Câu hỏi không thuộc bài làm');
      return finishQuiz(attempt,rows,body.answers,new Date(attempt.deadline_at)<=await now(db),db);
    });
    res.json(result);
  });
  app.use('/api/learn',(req,res,next)=>{res.set('Cache-Control','no-store');next();},router);
}
