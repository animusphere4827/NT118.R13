import {Router} from 'express';
import {z} from 'zod';
import {query,insert,transaction} from './db.js';
import {parseId} from './resources.js';

const configs = {
  practice:{attempt:'practice_attempts',answers:'practice_answers',parent:'practice_sets',link:'practice_set_questions',fk:'practice_set_id'},
  mock:{attempt:'mock_test_attempts',answers:'mock_test_answers',parent:'mock_tests',link:'mock_test_questions',fk:'mock_test_id'}
};
const id = z.number().int().min(1).max(2147483647);
const label = z.enum(['A','B','C','D']).nullable();
const answer = z.object({question_id:id,label}).strict();
const submitBody = z.object({answers:z.array(answer).max(200).default([]),answer_version:z.number().int().min(0).optional()}).strict();
const draftAnswer = z.object({question_id:id,label:label.optional(),marked_for_review:z.boolean().optional()})
  .strict().refine(value => value.label!==undefined || value.marked_for_review!==undefined,'Missing answer or review flag');
const draftBody = z.object({answer_version:z.number().int().min(0),answers:z.array(draftAnswer).min(1).max(200)}).strict();
const historyQuery = z.object({
  page:z.coerce.number().int().min(1).max(100000).default(1),
  limit:z.coerce.number().int().min(1).max(100).default(20),
  content_id:z.coerce.number().int().min(1).max(2147483647).optional(),
  status:z.enum(['IN_PROGRESS','SUBMITTED']).optional()
}).strict();
const fail = (status,message) => { throw Object.assign(new Error(message),{status}); };
const snapshot = value => typeof value==='string' ? JSON.parse(value) : value;
const accuracy = (correct,total) => total ? Number((correct/total*100).toFixed(2)) : 0;

function assertUnique(answers) {
  if (new Set(answers.map(row=>row.question_id)).size!==answers.length) fail(422,'Câu trả lời bị trùng');
}

export function publicReadingQuestion(value,review=false) {
  const q = snapshot(value);
  const visible = {};
  for (const key of ['id','part_id','group_id','lesson_id','question_type','question_text','difficulty','revision','position','group_title','group_instructions']) {
    if (q[key]!==undefined) visible[key]=q[key];
  }
  visible.options = q.options.map(option => ({label:option.label,option_text:option.option_text,
    ...(review ? {is_correct:option.is_correct} : {})}));
  if (q.passages) visible.passages=q.passages.map(p => {
    const {translation_vi,...rest}=p;
    return review ? {...rest,translation_vi} : rest;
  });
  if (review) visible.explanation=q.explanation;
  return visible;
}

async function contentSnapshots(c,parentId,db) {
  const rows = await query(`SELECT q.*,m.position,g.title AS group_title,g.instructions AS group_instructions,
      g.part_id AS group_part_id FROM ${c.link} m JOIN questions q ON q.id=m.question_id
    LEFT JOIN question_groups g ON g.id=q.group_id WHERE m.${c.fk}=? ORDER BY m.position`,[parentId],db);
  if (!rows.length || rows.length>200 || rows.some(q=>q.status!=='PUBLISHED')) fail(409,'Bài luyện chưa sẵn sàng');
  const options = await query(`SELECT question_id,label,option_text,is_correct FROM question_options
    WHERE question_id=ANY(?::integer[]) ORDER BY question_id,label`,[rows.map(q=>q.id)],db);
  const groupIds = [...new Set(rows.map(q=>q.group_id).filter(Boolean))];
  const passages = groupIds.length ? await query(`SELECT group_id,position,passage_type,title,content,translation_vi,image_url
    FROM reading_passages WHERE group_id=ANY(?::integer[]) ORDER BY group_id,position`,[groupIds],db) : [];
  const optionsById = new Map();
  const passagesById = new Map();
  for (const option of options) {
    const {question_id,...data}=option;
    if (!optionsById.has(question_id)) optionsById.set(question_id,[]);
    optionsById.get(question_id).push(data);
  }
  for (const passage of passages) {
    const {group_id,...data}=passage;
    if (!passagesById.has(group_id)) passagesById.set(group_id,[]);
    passagesById.get(group_id).push(data);
  }
  for (const q of rows) {
    q.options = optionsById.get(q.id) || [];
    if (q.options.length!==4 || q.options.filter(o=>o.is_correct).length!==1) fail(409,'Câu hỏi chưa đủ đáp án hợp lệ');
    if (q.part_id===5 && q.group_id!==null) fail(409,'Câu Part 5 không được gắn đoạn văn');
    if (q.part_id!==5) {
      q.passages = passagesById.get(q.group_id) || [];
      if (q.group_part_id!==q.part_id || !q.passages.length ||
          (q.part_id===6 && q.passages.length!==1) || q.passages.some((p,i)=>p.position!==i+1)) {
        fail(409,'Nhóm Reading chưa đủ đoạn văn hợp lệ');
      }
    }
  }
  return rows;
}

async function lockedAttempt(c,id,userId,db,skipLocked=false) {
  const [attempt] = await query(`SELECT * FROM ${c.attempt} WHERE id=?
    ${userId===undefined ? '' : 'AND user_id=?'} FOR UPDATE ${skipLocked ? 'SKIP LOCKED' : ''}`,
  [id,...(userId===undefined ? [] : [userId])],db);
  if (!attempt && !skipLocked) fail(404,'Không tìm thấy bài làm');
  return attempt;
}

async function serverTime(db) {
  return (await query('SELECT clock_timestamp() AS time',[],db))[0].time;
}

function resultFor(kind,attempt,answers,expired) {
  const parts = [5,6,7].map(part_id => {
    const rows = answers.filter(row=>snapshot(row.snapshot).part_id===part_id);
    const correct_count=rows.filter(row=>row.is_correct).length;
    return {part_id,total_questions:rows.length,correct_count,accuracy:accuracy(correct_count,rows.length)};
  });
  const correct = answers.filter(row=>row.is_correct).length;
  return {
    attempt_id:attempt.id,kind,correct_count:correct,total_questions:answers.length,
    accuracy:accuracy(correct,answers.length),by_part:parts,
    ...(kind==='mock' ? {part5_correct:parts[0].correct_count,part6_correct:parts[1].correct_count,part7_correct:parts[2].correct_count} : {}),
    expired,score_note:'Tỷ lệ đúng luyện tập; không phải điểm TOEIC chuẩn ETS'
  };
}

async function finishAttempt(kind,c,attempt,answers,incoming,expired,db) {
  if (attempt.status==='SUBMITTED') {
    return attempt.result || resultFor(kind,attempt,answers,new Date(attempt.submitted_at)>new Date(attempt.deadline_at));
  }
  const selected = new Map(incoming.map(row=>[row.question_id,row.label]));
  for (const row of answers) {
    const q = snapshot(row.snapshot);
    if (!expired && selected.has(row.question_id)) row.selected_label=selected.get(row.question_id);
    row.is_correct=Boolean(row.selected_label && q.options.some(option=>option.label===row.selected_label && option.is_correct));
  }
  await query(`UPDATE ${c.answers} a SET selected_label=v.label,is_correct=v.correct
    FROM jsonb_to_recordset(?::jsonb) AS v(id integer,label char(1),correct boolean) WHERE a.id=v.id`,
  [JSON.stringify(answers.map(row=>({id:row.id,label:row.selected_label,correct:row.is_correct})))],db);
  const result = resultFor(kind,attempt,answers,expired);
  await query(`UPDATE ${c.attempt} SET status='SUBMITTED',correct_count=?,accuracy=?,submitted_at=clock_timestamp(),result=?
    ${kind==='mock' ? ',part5_correct=?,part6_correct=?,part7_correct=?' : ''} WHERE id=?`,
  [result.correct_count,result.accuracy,JSON.stringify(result),
    ...(kind==='mock' ? [result.part5_correct,result.part6_correct,result.part7_correct] : []),attempt.id],db);
  if (kind==='mock') await query(`INSERT INTO notifications(user_id,type,title,body,data) VALUES(?,'RESULT',?,?,?)`,
  [attempt.user_id,'Đã chấm bài thi thử',`Bạn đúng ${result.correct_count}/${result.total_questions} câu (${result.accuracy}%).`,
    JSON.stringify({screen:'mock_result',attempt_id:attempt.id})],db);
  return result;
}

function validateIncoming(answers,stored) {
  const valid = new Map(stored.map(row=>[row.question_id,snapshot(row.snapshot)]));
  for (const row of answers) {
    const q=valid.get(row.question_id);
    if (!q || (row.label!==null && row.label!==undefined && !q.options.some(option=>option.label===row.label))) {
      fail(422,'Đáp án không thuộc bài làm');
    }
  }
}

export async function finalizeExpiredReadingAttempts(batchSize=25) {
  if (!Number.isInteger(batchSize) || batchSize<1 || batchSize>100) throw new Error('Invalid attempt batch size');
  let completed=0;
  for (const [kind,c] of Object.entries(configs)) {
    const expired = await query(`SELECT id FROM ${c.attempt} WHERE status='IN_PROGRESS'
      AND deadline_at<=clock_timestamp() ORDER BY deadline_at LIMIT ? FOR UPDATE SKIP LOCKED`,[batchSize]);
    for (const row of expired) {
      completed += await transaction(async db => {
        const attempt=await lockedAttempt(c,row.id,undefined,db,true);
        if (!attempt || attempt.status==='SUBMITTED' || new Date(attempt.deadline_at)>await serverTime(db)) return 0;
        const answers=await query(`SELECT * FROM ${c.answers} WHERE attempt_id=? ORDER BY position`,[attempt.id],db);
        await finishAttempt(kind,c,attempt,answers,[],true,db);
        return 1;
      });
    }
  }
  return completed;
}

export function registerAssessmentRoutes(app) {
  const router = Router();
  for (const [kind,c] of Object.entries(configs)) {
    router.post(`/${kind}/:id/attempts`,async (req,res) => {
      const parentId=parseId(req.params.id);
      const result=await transaction(async db => {
        await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ');
        const [parent]=await query(`SELECT * FROM ${c.parent} WHERE id=? FOR SHARE`,[parentId],db);
        if (!parent || parent.status!=='PUBLISHED') fail(404,'Bài luyện chưa xuất bản');
        const rows=await contentSnapshots(c,parentId,db);
        if (kind==='practice' && rows.some(q=>q.part_id!==parent.part_id)) fail(409,'Bộ luyện có câu không cùng Part');
        const id=await insert(`WITH timing AS (SELECT clock_timestamp() AS started)
          INSERT INTO ${c.attempt}(user_id,${c.fk},content_title,total_questions,started_at,deadline_at)
          SELECT ?,?,?,?,started,started+make_interval(secs=>?::integer) FROM timing`,
        [req.user.id,parentId,parent.title,rows.length,parent.time_limit],db);
        await query(`INSERT INTO ${c.answers}(attempt_id,question_id,position,snapshot)
          SELECT ?,v.question_id,v.position,v.snapshot FROM jsonb_to_recordset(?::jsonb)
          AS v(question_id integer,position integer,snapshot jsonb)`,
        [id,JSON.stringify(rows.map(q=>({question_id:q.id,position:q.position,snapshot:q})))],db);
        const [attempt]=await query(`SELECT id,started_at,deadline_at,answer_version,clock_timestamp() AS server_time
          FROM ${c.attempt} WHERE id=?`,[id],db);
        return attempt;
      });
      res.status(201).json(result);
    });

    router.get(`/${kind}/attempts`,async (req,res) => {
      const {page,limit,content_id,status}=historyQuery.parse(req.query);
      const filters=['user_id=?'];
      const params=[req.user.id];
      if (content_id) { filters.push(`${c.fk}=?`); params.push(content_id); }
      if (status) { filters.push('status=?'); params.push(status); }
      const [data]=await query(`WITH filtered AS (
          SELECT id,${c.fk},content_title,status,total_questions,correct_count,accuracy,started_at,deadline_at,submitted_at,answer_version
          FROM ${c.attempt} WHERE ${filters.join(' AND ')}
        ), page AS (SELECT * FROM filtered ORDER BY started_at DESC,id DESC LIMIT ? OFFSET ?)
        SELECT (SELECT COUNT(*) FROM filtered) AS total,
          COALESCE((SELECT jsonb_agg(page ORDER BY started_at DESC,id DESC) FROM page),'[]'::jsonb) AS rows`,
      [...params,limit,(page-1)*limit]);
      res.json({...data,page,limit});
    });

    router.get(`/${kind}/attempts/:id`,async (req,res) => {
      const id=parseId(req.params.id);
      const result=await transaction(async db => {
        let attempt=await lockedAttempt(c,id,req.user.id,db);
        const now=await serverTime(db);
        const answers=await query(`SELECT * FROM ${c.answers} WHERE attempt_id=? ORDER BY position`,[id],db);
        if (attempt.status==='IN_PROGRESS' && new Date(attempt.deadline_at)<=now) {
          await finishAttempt(kind,c,attempt,answers,[],true,db);
          [attempt]=await query(`SELECT * FROM ${c.attempt} WHERE id=?`,[id],db);
        }
        const review=attempt.status==='SUBMITTED';
        if (review && !attempt.result) {
          attempt.result=resultFor(kind,attempt,answers,new Date(attempt.submitted_at)>new Date(attempt.deadline_at));
        }
        return {attempt,server_time:now,questions:answers.map(row=>({
          ...publicReadingQuestion(row.snapshot,review),selected_label:row.selected_label,
          marked_for_review:row.marked_for_review,...(review ? {is_correct:Boolean(row.is_correct)} : {})
        }))};
      });
      res.json(result);
    });

    router.patch(`/${kind}/attempts/:id/answers`,async (req,res) => {
      const id=parseId(req.params.id);
      const body=draftBody.parse(req.body);
      assertUnique(body.answers);
      const result=await transaction(async db => {
        const attempt=await lockedAttempt(c,id,req.user.id,db);
        const now=await serverTime(db);
        if (attempt.status==='SUBMITTED' || new Date(attempt.deadline_at)<=now) fail(409,'Bài làm đã nộp hoặc hết giờ');
        if (body.answer_version!==attempt.answer_version) fail(409,'Đáp án đã thay đổi, hãy tải lại bài làm');
        const stored=await query(`SELECT * FROM ${c.answers} WHERE attempt_id=? ORDER BY position`,[id],db);
        validateIncoming(body.answers,stored);
        const existing=new Map(stored.map(row=>[row.question_id,row]));
        const updates=body.answers.map(row=>({id:existing.get(row.question_id).id,
          label:row.label===undefined ? existing.get(row.question_id).selected_label : row.label,
          marked:row.marked_for_review ?? existing.get(row.question_id).marked_for_review}));
        await query(`UPDATE ${c.answers} a SET selected_label=v.label,marked_for_review=v.marked
          FROM jsonb_to_recordset(?::jsonb) AS v(id integer,label char(1),marked boolean) WHERE a.id=v.id`,[JSON.stringify(updates)],db);
        await query(`UPDATE ${c.attempt} SET answer_version=answer_version+1 WHERE id=?`,[id],db);
        return {id,answer_version:attempt.answer_version+1,deadline_at:attempt.deadline_at,server_time:now};
      });
      res.json(result);
    });

    router.post(`/${kind}/attempts/:id/submit`,async (req,res) => {
      const id=parseId(req.params.id);
      const body=submitBody.parse(req.body);
      assertUnique(body.answers);
      const result=await transaction(async db => {
        const attempt=await lockedAttempt(c,id,req.user.id,db);
        const stored=await query(`SELECT * FROM ${c.answers} WHERE attempt_id=? ORDER BY position`,[id],db);
        if (attempt.status==='SUBMITTED') return finishAttempt(kind,c,attempt,stored,[],false,db);
        if (body.answer_version!==undefined && body.answer_version!==attempt.answer_version) fail(409,'Đáp án đã thay đổi, hãy tải lại bài làm');
        validateIncoming(body.answers,stored);
        const expired=new Date(attempt.deadline_at)<=await serverTime(db);
        return finishAttempt(kind,c,attempt,stored,body.answers,expired,db);
      });
      res.json(result);
    });
  }
  app.use('/api/learn',(req,res,next)=>{res.set('Cache-Control','no-store');next();},router);
}
