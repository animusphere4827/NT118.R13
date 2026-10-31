import {Router} from 'express';
import {z} from 'zod';
import {query,transaction} from './db.js';
import {parseId} from './resources.js';
const fail=(status,message)=>{throw Object.assign(new Error(message),{status});};
async function lessonFor(id,db) {
  const [lesson]=await query(`SELECT l.* FROM grammar_lessons l JOIN grammar_topics t ON t.id=l.topic_id
    WHERE l.id=? AND l.status='PUBLISHED' AND t.status='PUBLISHED'`,[id],db);
  if(!lesson) fail(404,'Không tìm thấy bài ngữ pháp');
  return lesson;
}
async function progressFor(userId,id,db) {
  const [progress]=await query(`SELECT COUNT(*) AS total,COUNT(p.question_id) AS answered,
    COUNT(*) FILTER(WHERE p.is_correct) AS correct FROM questions q
    LEFT JOIN grammar_answer_progress p ON p.question_id=q.id AND p.lesson_id=q.lesson_id AND p.user_id=?
    WHERE q.lesson_id=? AND q.status='PUBLISHED'`,[userId,id],db);
  const passed=progress.total>0&&progress.answered===progress.total&&progress.correct*100>=progress.total*80;
  return {...progress,passed,required_percent:80};
}
export function registerGrammarStudyRoutes(app) {
  const router=Router();
  router.use((req,res,next)=>{res.set('Cache-Control','no-store');next();});
  router.get('/grammar/:id/workspace',async(req,res)=>{
    const id=parseId(req.params.id);
    const data=await transaction(async db=>{
      await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY');
      const lesson=await lessonFor(id,db);
      const questions=await query(`SELECT q.id,q.question_text,q.part_id,q.group_id,p.is_correct AS previously_correct
        FROM questions q LEFT JOIN grammar_answer_progress p ON p.question_id=q.id AND p.lesson_id=q.lesson_id AND p.user_id=?
        WHERE q.lesson_id=? AND q.status='PUBLISHED' ORDER BY q.id LIMIT 201`,[req.user.id,id],db);
      if(questions.length>200)fail(409,'Bài ngữ pháp quá lớn, cần chia nhỏ nội dung');
      const ids=questions.map(q=>q.id),groups=[...new Set(questions.map(q=>q.group_id).filter(Boolean))];
      const options=ids.length?await query('SELECT question_id,label,option_text FROM question_options WHERE question_id=ANY(?::int[]) ORDER BY question_id,label',[ids],db):[];
      const passages=groups.length?await query('SELECT group_id,position,title,content,image_url FROM reading_passages WHERE group_id=ANY(?::int[]) ORDER BY group_id,position',[groups],db):[];
      return {lesson,progress:await progressFor(req.user.id,id,db),questions:questions.map(q=>({...q,
        options:options.filter(o=>o.question_id===q.id).map(({question_id,...o})=>o),passages:passages.filter(p=>p.group_id===q.group_id)}))};
    });
    res.json(data);
  });
  router.post('/grammar/:id/check',async(req,res)=>{
    const id=parseId(req.params.id),body=z.object({question_id:z.number().int().positive().max(2147483647),label:z.enum(['A','B','C','D'])}).strict().parse(req.body);
    res.json(await transaction(async db=>{
      await lessonFor(id,db);
      await db.query('SELECT pg_advisory_xact_lock($1,$2)',[req.user.id,id]);
      const [question]=await query("SELECT id,explanation FROM questions WHERE id=? AND lesson_id=? AND status='PUBLISHED'",[body.question_id,id],db);
      if(!question)fail(404,'Không tìm thấy bài tập');
      const options=await query('SELECT label,is_correct FROM question_options WHERE question_id=?',[question.id],db);
      if(options.length!==4||options.filter(o=>o.is_correct).length!==1)fail(409,'Bài tập chưa có đáp án hợp lệ');
      const correct=options.some(o=>o.label===body.label&&o.is_correct);
      await query(`INSERT INTO grammar_answer_progress(user_id,question_id,lesson_id,is_correct) VALUES(?,?,?,?)
        ON CONFLICT(user_id,question_id) DO UPDATE SET lesson_id=EXCLUDED.lesson_id,is_correct=EXCLUDED.is_correct,checked_at=clock_timestamp()`,[req.user.id,question.id,id,correct],db);
      const progress=await progressFor(req.user.id,id,db);
      if(progress.passed) await query(`INSERT INTO user_grammar_progress(user_id,lesson_id,progress_percent) VALUES(?,?,100)
        ON CONFLICT(user_id,lesson_id) DO UPDATE SET progress_percent=100 WHERE user_grammar_progress.progress_percent<100`,[req.user.id,id],db);
      return {is_correct:correct,correct_label:options.find(o=>o.is_correct).label,explanation:question.explanation,progress};
    }));
  });
  router.put('/grammar/:id/progress',async(req,res)=>{
    const id=parseId(req.params.id),{progress_percent}=z.object({progress_percent:z.number().int().min(0).max(100)}).strict().parse(req.body);
    await transaction(async db=>{
      await lessonFor(id,db);const progress=await progressFor(req.user.id,id,db);
      if(progress_percent===100&&progress.total>0&&!progress.passed)fail(409,'Làm đủ bài tập và đạt ít nhất 80% để hoàn thành');
      await query(`INSERT INTO user_grammar_progress(user_id,lesson_id,progress_percent) VALUES(?,?,?)
        ON CONFLICT(user_id,lesson_id) DO UPDATE SET progress_percent=GREATEST(user_grammar_progress.progress_percent,EXCLUDED.progress_percent)`,[req.user.id,id,progress_percent],db);
    });res.sendStatus(204);
  });
  app.use('/api/learn',router);
}
