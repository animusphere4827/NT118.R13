import {randomUUID,randomInt} from 'node:crypto';
import {Router} from 'express';
import {z} from 'zod';
import {query,transaction} from './db.js';
import {parseId} from './resources.js';
import {loadPath} from './course.js';

const BATCH_SIZE=10;
const PASS_PERCENT=80;
const wordId=z.number().int().positive().max(2147483647);
const studiedBody=z.object({vocabulary_ids:z.array(wordId).min(1).max(BATCH_SIZE)})
  .strict().refine(b=>new Set(b.vocabulary_ids).size===b.vocabulary_ids.length,'Không được lặp từ');
const answerBody=z.object({answers:z.array(z.object({
  vocabulary_id:wordId,answer:z.string().max(300)
}).strict()).min(1).max(BATCH_SIZE)})
  .strict().refine(b=>new Set(b.answers.map(a=>a.vocabulary_id)).size===b.answers.length,'Không được lặp đáp án');
const fail=(status,message)=>{const error=new Error(message);error.status=status;throw error;};
export const normalizeWord=value=>value.normalize('NFKC').toLocaleLowerCase('en-US')
  .replace(/[’‘]/g,"'").trim().replace(/\s+/gu,' ');

async function lessonFor(userId,id,db) {
  const path=await loadPath(userId,db);
  const lesson=path.units.flatMap(unit=>unit.lessons).find(row=>row.id===id);
  if (!lesson || lesson.type!=='VOCABULARY') fail(404,'Không tìm thấy bài từ vựng');
  if (lesson.status==='LOCKED') fail(409,'Hoàn thành bài trước trong phần này để mở khóa');
  return lesson;
}

async function ownProgress(userId,lessonId,db) {
  const [progress]=await query(`SELECT * FROM course_vocabulary_progress
    WHERE user_id=? AND lesson_id=? FOR UPDATE`,[userId,lessonId],db);
  if (!progress) fail(409,'Hãy bắt đầu bài học trước');
  return progress;
}

const batchFor=progress=>progress.words.slice(progress.passed_count,progress.passed_count+BATCH_SIZE);

function publicCheck(check) {
  return {
    id:check.id,required_percent:PASS_PERCENT,total:check.questions.length,
    questions:check.questions.map(word=>({vocabulary_id:word.id,prompt:word.meaning_vi,
      part_of_speech:word.part_of_speech,
      hint:word.word.replace(/\p{L}+/gu,part=>[...part].map((letter,index)=>index?'_':letter).join(''))})),
    result:check.result??null
  };
}

async function view(progress,db) {
  const [check]=await query(`SELECT * FROM vocabulary_lesson_checks
    WHERE progress_id=? AND submitted_at IS NULL`,[progress.id],db);
  const words=batchFor(progress);
  return {
    lesson_id:progress.lesson_id,total_words:progress.words.length,
    passed_words:progress.passed_count,batch_size:BATCH_SIZE,required_percent:PASS_PERCENT,
    batch_number:Math.min(Math.floor(progress.passed_count/BATCH_SIZE)+1,Math.ceil(progress.words.length/BATCH_SIZE)),
    total_batches:Math.ceil(progress.words.length/BATCH_SIZE),completed_at:progress.completed_at,
    state:progress.completed_at?'COMPLETED':check?'CHECKING':'STUDYING',
    words:check?[]:words,studied_ids:progress.studied_ids,
    check:check?publicCheck(check):null
  };
}

export function registerVocabularyCourseRoutes(app) {
  const router=Router();
  router.use((req,res,next)=>{res.set('Cache-Control','no-store');next();});

  router.post('/path/lessons/:id/vocabulary/start',async(req,res)=>{
    const lessonId=parseId(req.params.id);
    z.object({}).strict().parse(req.body??{});
    const result=await transaction(async db=>{
      await db.query('SELECT pg_advisory_xact_lock($1,$2)',[req.user.id,lessonId]);
      const lesson=await lessonFor(req.user.id,lessonId,db);
      let [progress]=await query(`SELECT * FROM course_vocabulary_progress
        WHERE user_id=? AND lesson_id=? FOR UPDATE`,[req.user.id,lessonId],db);
      if (!progress) {
        const words=await query(`SELECT id,word,meaning_vi,ipa,part_of_speech,example_sentence,audio_url,image_url
          FROM vocabularies WHERE topic_id=? AND status='PUBLISHED' ORDER BY id LIMIT 5001`,[lesson.content_id],db);
        if (!words.length) fail(409,'Bài học chưa có từ vựng');
        if (words.length>5000) fail(409,'Chủ đề quá lớn, cần chia nhỏ nội dung trước khi học');
        if (words.some(word=>!word.word.trim() || !word.meaning_vi.trim())) fail(409,'Bài học có từ thiếu nội dung, cần biên soạn lại');
        [progress]=await query(`INSERT INTO course_vocabulary_progress(user_id,lesson_id,words)
          VALUES(?,?,?::jsonb) RETURNING *`,[req.user.id,lessonId,JSON.stringify(words)],db);
      }
      return view(progress,db);
    });
    res.json(result);
  });

  router.get('/path/lessons/:id/vocabulary',async(req,res)=>{
    const lessonId=parseId(req.params.id);
    res.json(await transaction(async db=>{
      await lessonFor(req.user.id,lessonId,db);
      return view(await ownProgress(req.user.id,lessonId,db),db);
    }));
  });

  router.get('/path/lessons/:id/vocabulary/review',async(req,res)=>{
    const lessonId=parseId(req.params.id);
    const {page}=z.object({page:z.coerce.number().int().min(1).max(100000).default(1)}).strict().parse(req.query);
    res.json(await transaction(async db=>{
      await lessonFor(req.user.id,lessonId,db);
      const progress=await ownProgress(req.user.id,lessonId,db);
      return {rows:progress.words.slice((page-1)*BATCH_SIZE,page*BATCH_SIZE),
        total:progress.words.length,page,limit:BATCH_SIZE};
    }));
  });

  router.put('/path/lessons/:id/vocabulary/study',async(req,res)=>{
    const lessonId=parseId(req.params.id);
    const body=studiedBody.parse(req.body);
    res.json(await transaction(async db=>{
      await lessonFor(req.user.id,lessonId,db);
      const progress=await ownProgress(req.user.id,lessonId,db);
      if (progress.completed_at) {
        if (body.vocabulary_ids.some(id=>!progress.words.some(word=>word.id===id))) fail(422,'Từ không thuộc bài học');
        return view(progress,db);
      }
      const batch=batchFor(progress);
      if (body.vocabulary_ids.some(id=>!batch.some(word=>word.id===id))) fail(422,'Từ không thuộc lượt học hiện tại');
      const [active]=await query('SELECT id FROM vocabulary_lesson_checks WHERE progress_id=? AND submitted_at IS NULL',[progress.id],db);
      if (active) fail(409,'Hãy nộp bài kiểm tra đang làm trước');
      progress.studied_ids=[...new Set([...progress.studied_ids,...body.vocabulary_ids])];
      await query('UPDATE course_vocabulary_progress SET studied_ids=? WHERE id=?',[progress.studied_ids,progress.id],db);
      return view(progress,db);
    }));
  });

  router.post('/path/lessons/:id/vocabulary/checks',async(req,res)=>{
    const lessonId=parseId(req.params.id);
    z.object({}).strict().parse(req.body??{});
    res.json(await transaction(async db=>{
      await lessonFor(req.user.id,lessonId,db);
      const progress=await ownProgress(req.user.id,lessonId,db);
      if (progress.completed_at) fail(409,'Bài học đã hoàn thành');
      const [active]=await query('SELECT * FROM vocabulary_lesson_checks WHERE progress_id=? AND submitted_at IS NULL',[progress.id],db);
      if (active) return publicCheck(active);
      const questions=batchFor(progress);
      if (questions.some(word=>!progress.studied_ids.includes(word.id))) fail(409,'Học hết các từ trong lượt này trước khi kiểm tra');
      for (let i=questions.length-1;i>0;i--) {
        const j=randomInt(i+1);
        [questions[i],questions[j]]=[questions[j],questions[i]];
      }
      const [check]=await query(`INSERT INTO vocabulary_lesson_checks(id,progress_id,batch_start,questions)
        VALUES(?,?,?,?::jsonb) RETURNING *`,[randomUUID(),progress.id,progress.passed_count,JSON.stringify(questions)],db);
      return publicCheck(check);
    }));
  });

  router.post('/vocabulary-checks/:id/submit',async(req,res)=>{
    const id=z.uuid().parse(req.params.id);
    const body=answerBody.parse(req.body);
    res.json(await transaction(async db=>{
      const [owner]=await query(`SELECT p.lesson_id FROM vocabulary_lesson_checks c
        JOIN course_vocabulary_progress p ON p.id=c.progress_id WHERE c.id=? AND p.user_id=?`,[id,req.user.id],db);
      if (!owner) fail(404,'Không tìm thấy bài kiểm tra');
      await lessonFor(req.user.id,owner.lesson_id,db);
      const progress=await ownProgress(req.user.id,owner.lesson_id,db);
      const [check]=await query('SELECT * FROM vocabulary_lesson_checks WHERE id=? FOR UPDATE',[id],db);
      const answers=[...body.answers].sort((a,b)=>a.vocabulary_id-b.vocabulary_id);
      if (check.result) {
        if (answers.length!==check.answers.length || answers.some((answer,i)=>
          answer.vocabulary_id!==check.answers[i].vocabulary_id || answer.answer!==check.answers[i].answer)) {
          fail(409,'Bài đã nộp, không thể thay đổi đáp án');
        }
        return check.result;
      }
      if (check.batch_start!==progress.passed_count) fail(409,'Lượt học đã thay đổi');
      if (answers.length!==check.questions.length || answers.some(a=>!check.questions.some(q=>q.id===a.vocabulary_id))) {
        fail(422,'Cần trả lời đúng danh sách từ của bài kiểm tra');
      }
      const review=check.questions.map(word=>{
        const selected=answers.find(a=>a.vocabulary_id===word.id).answer;
        return {vocabulary_id:word.id,prompt:word.meaning_vi,answer:selected,correct_answer:word.word,
          is_correct:normalizeWord(selected)===normalizeWord(word.word)};
      });
      const correct=review.filter(row=>row.is_correct).length;
      const passed=correct*100>=check.questions.length*PASS_PERCENT;
      if (passed) {
        progress.passed_count+=check.questions.length;
        progress.completed_at=progress.passed_count===progress.words.length?new Date():null;
        progress.studied_ids=[];
        await query(`UPDATE course_vocabulary_progress SET passed_count=?,studied_ids='{}',
          completed_at=CASE WHEN ?::boolean THEN clock_timestamp() ELSE NULL END WHERE id=?`,
        [progress.passed_count,Boolean(progress.completed_at),progress.id],db);
      }
      const result={check_id:id,lesson_id:owner.lesson_id,total:review.length,correct_count:correct,
        required_correct:Math.ceil(review.length*PASS_PERCENT/100),passed,lesson_completed:Boolean(progress.completed_at),
        passed_words:progress.passed_count,total_words:progress.words.length,review};
      await query(`UPDATE vocabulary_lesson_checks SET answers=?::jsonb,result=?::jsonb,submitted_at=clock_timestamp()
        WHERE id=?`,[JSON.stringify(answers),JSON.stringify(result),id],db);
      return result;
    }));
  });

  router.get('/vocabulary-checks/:id',async(req,res)=>{
    const id=z.uuid().parse(req.params.id);
    const [check]=await query(`SELECT c.*,p.lesson_id FROM vocabulary_lesson_checks c
      JOIN course_vocabulary_progress p ON p.id=c.progress_id WHERE c.id=? AND p.user_id=?`,[id,req.user.id]);
    if (!check) fail(404,'Không tìm thấy bài kiểm tra');
    await lessonFor(req.user.id,check.lesson_id);
    res.json(publicCheck(check));
  });

  app.use('/api/learn',router);
}
