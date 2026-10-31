import {Router} from 'express';
import {z} from 'zod';
import {query,insert,transaction} from './db.js';
import {resources,parseId} from './resources.js';
import {assertEditVersion,editVersion,lockContent} from './admin-data.js';

const id=z.number().int().min(1).max(2147483647);
const status=z.enum(['DRAFT','PUBLISHED']);
const passageBody=resources.reading_passages.schema.omit({group_id:true,position:true}).extend({id:id.optional()}).strict();
const questionBody=resources.questions.schema.omit({part_id:true,group_id:true,lesson_id:true,status:true}).extend({id:id.optional()}).strict();
const uniqueIds=rows=>new Set(rows.filter(row=>row.id).map(row=>row.id)).size===rows.filter(row=>row.id).length;
const readingBody=z.object({part_id:z.number().int().min(6).max(7),title:z.string().trim().min(1).max(200),
  instructions:z.string().max(60000).default(''),status,is_practical_reading:z.boolean().default(false),
  passages:z.array(passageBody).min(1).max(3).refine(uniqueIds,'Đoạn văn bị trùng ID'),
  questions:z.array(questionBody).max(50).refine(uniqueIds,'Câu hỏi bị trùng ID')
}).strict().refine(body=>body.part_id!==6 || body.passages.length===1,'Part 6 chỉ có một đoạn văn')
  .refine(body=>body.status!=='PUBLISHED' || body.questions.length>0,'Bài xuất bản cần câu hỏi');
const examBody=z.object({title:z.string().trim().min(1).max(200),description:z.string().max(60000).default(''),
  time_limit:z.number().int().min(60).max(14400),status,part_id:z.number().int().min(5).max(7).optional(),
  test_format:z.enum(['MINI','FULL']).optional(),question_ids:z.array(id).max(200)
    .refine(rows=>new Set(rows).size===rows.length,'Câu hỏi trong đề bị trùng')}).strict();
const fail=(code,message)=>{throw Object.assign(new Error(message),{status:code});};
const config={practice:{table:'practice_sets',link:'practice_set_questions',fk:'practice_set_id'},
  mock:{table:'mock_tests',link:'mock_test_questions',fk:'mock_test_id'}};
const kindOf=value=>{if(!config[value])fail(404,'Loại đề không tồn tại');return config[value];};
const audit=(db,user,action,resource,record)=>query('INSERT INTO audit_logs(user_id,action,resource,record_id) VALUES(?,?,?,?)',
  [user,action,resource,record],db);

export async function questionSummaries(ids,db) {
  if (!ids.length) return [];
  const rows=await query(`SELECT q.id AS question_id,q.question_text,q.part_id,q.group_id,q.status,g.title AS group_title,
      g.part_id AS group_part,
      (SELECT COUNT(*) FROM question_options o WHERE o.question_id=q.id) AS option_count,
      (SELECT COUNT(*) FROM question_options o WHERE o.question_id=q.id AND o.is_correct) AS correct_count,
      (SELECT COUNT(*) FROM reading_passages p WHERE p.group_id=q.group_id) AS passage_count,
      (SELECT MAX(position) FROM reading_passages p WHERE p.group_id=q.group_id) AS last_position
    FROM questions q LEFT JOIN question_groups g ON g.id=q.group_id WHERE q.id=ANY(?::integer[])`,[ids],db);
  const byId=new Map(rows.map(row=>[row.question_id,row]));
  return ids.map(question_id=>{
    const row=byId.get(question_id);
    if (!row) fail(422,'Câu hỏi #'+question_id+' không tồn tại');
    const issues=[];
    if (row.status!=='PUBLISHED') issues.push('Chưa xuất bản');
    if (row.option_count!==4 || row.correct_count!==1) issues.push('Cần 4 đáp án và 1 đáp án đúng');
    if (row.part_id===5 && row.group_id) issues.push('Part 5 không gắn đoạn văn');
    if (row.part_id!==5 && (row.group_part!==row.part_id || !row.passage_count || row.last_position!==row.passage_count ||
        (row.part_id===6 && row.passage_count!==1))) issues.push('Đoạn văn thiếu hoặc sai thứ tự/Part');
    return {...row,ready:issues.length===0,issues};
  });
}

export async function assertExamReady(kind,parent,ids,db) {
  const rows=await questionSummaries(ids,db);
  const problems=[];
  if (!rows.length) problems.push('Cần ít nhất một câu hỏi');
  if (rows.some(row=>!row.ready)) problems.push('Có câu chưa xuất bản hoặc chưa đủ nội dung');
  if (kind==='practice' && rows.some(row=>row.part_id!==parent.part_id)) problems.push('Bộ luyện chỉ gồm câu cùng Part');
  if (kind==='mock' && parent.test_format==='FULL') {
    const counts=[5,6,7].map(part=>rows.filter(row=>row.part_id===part).length);
    if (rows.length!==100 || counts.join(',')!=='30,16,54') problems.push('Đề đầy đủ cần 100 câu: Part 5/6/7 = 30/16/54');
    if (parent.time_limit!==4500) problems.push('Đề đầy đủ cần đúng 75 phút (4500 giây)');
  }
  if (problems.length) fail(422,problems.join('. '));
  return rows;
}

async function readingRecord(id,db) {
  const [group]=await query('SELECT * FROM question_groups WHERE id=?',[id],db);
  if (!group) fail(404,'Không tìm thấy bài đọc');
  const passages=await query('SELECT * FROM reading_passages WHERE group_id=? ORDER BY position',[id],db);
  const questions=await query(`SELECT q.*,COALESCE((SELECT jsonb_agg(o ORDER BY label)
    FROM (SELECT label,option_text,is_correct FROM question_options WHERE question_id=q.id) o),'[]'::jsonb) AS options
    FROM questions q WHERE group_id=? ORDER BY id`,[id],db);
  return {...group,status:questions.length && questions.every(q=>q.status==='PUBLISHED')?'PUBLISHED':'DRAFT',passages,questions};
}

async function checkReadingLinks(existing,body,db) {
  const ids=existing.questions.map(q=>q.id);
  if (!ids.length) return;
  const practices=await query(`SELECT s.part_id,s.status FROM practice_set_questions m JOIN practice_sets s ON s.id=m.practice_set_id
    WHERE m.question_id=ANY(?::integer[])`,[ids],db);
  const mocks=await query(`SELECT s.status,s.test_format FROM mock_test_questions m JOIN mock_tests s ON s.id=m.mock_test_id
    WHERE m.question_id=ANY(?::integer[])`,[ids],db);
  if (practices.some(s=>s.part_id!==body.part_id)) fail(409,'Bài đang nằm trong bộ luyện Part khác');
  if (body.part_id!==existing.part_id && mocks.some(s=>s.status==='PUBLISHED' && s.test_format==='FULL')) {
    fail(409,'Chuyển đề đầy đủ liên quan về nháp trước khi đổi Part');
  }
  const removed=ids.filter(id=>!body.questions.some(q=>q.id===id));
  if (body.status==='DRAFT' && [...practices,...mocks].some(s=>s.status==='PUBLISHED')) {
    fail(409,'Chuyển bộ luyện/đề liên quan về nháp trước khi ẩn bài đọc');
  }
  if (removed.length) {
    const [linked]=await query(`SELECT EXISTS(SELECT 1 FROM practice_set_questions m JOIN practice_sets s ON s.id=m.practice_set_id
      WHERE m.question_id=ANY(?::integer[]) AND s.status='PUBLISHED') OR EXISTS(
      SELECT 1 FROM mock_test_questions m JOIN mock_tests s ON s.id=m.mock_test_id
      WHERE m.question_id=ANY(?::integer[]) AND s.status='PUBLISHED') AS used`,[removed,removed],db);
    if (linked.used) fail(409,'Chuyển đề/bộ luyện về nháp trước khi bỏ câu hỏi');
    await query('DELETE FROM practice_set_questions WHERE question_id=ANY(?::integer[])',[removed],db);
    await query('DELETE FROM mock_test_questions WHERE question_id=ANY(?::integer[])',[removed],db);
    await query('DELETE FROM questions WHERE id=ANY(?::integer[])',[removed],db);
  }
}

async function saveReading(req,res) {
  const id=req.params.id?parseId(req.params.id):null;
  const body=readingBody.parse(req.body);
  const saved=await transaction(async db=>{
    await lockContent(db);
    let existing;
    if (id) {
      existing=await readingRecord(id,db);
      assertEditVersion(req,'reading',existing,true);
      if (body.passages.some(p=>p.id && !existing.passages.some(old=>old.id===p.id)) ||
          body.questions.some(q=>q.id && !existing.questions.some(old=>old.id===q.id))) fail(422,'ID không thuộc bài đọc');
      await checkReadingLinks(existing,body,db);
    } else if (body.passages.some(p=>p.id) || body.questions.some(q=>q.id)) fail(422,'Bài mới không nhận ID có sẵn');
    const groupId=id || await insert('INSERT INTO question_groups(part_id,title,instructions,is_practical_reading) VALUES(?,?,?,?)',
      [body.part_id,body.title,body.instructions,body.is_practical_reading],db);
    if (id) await query('UPDATE question_groups SET part_id=?,title=?,instructions=?,is_practical_reading=? WHERE id=?',
      [body.part_id,body.title,body.instructions,body.is_practical_reading,id],db);
    await query('DELETE FROM reading_passages WHERE group_id=?',[groupId],db);
    for (const [index,p] of body.passages.entries()) {
      const columns=['group_id','position','passage_type','title','content','translation_vi','image_url'];
      const values=[groupId,index+1,p.passage_type,p.title,p.content,p.translation_vi,p.image_url];
      if (p.id) {columns.unshift('id');values.unshift(p.id);}
      await query(`INSERT INTO reading_passages(${columns.join(',')}) VALUES(${values.map(()=>'?').join(',')})`,values,db);
    }
    for (const q of body.questions) {
      const values=[body.part_id,groupId,q.question_type,q.question_text,q.explanation,q.difficulty,body.status];
      const questionId=q.id || await insert(`INSERT INTO questions(part_id,group_id,question_type,question_text,explanation,difficulty,status)
        VALUES(?,?,?,?,?,?,?)`,values,db);
      if (q.id) await query(`UPDATE questions SET part_id=?,group_id=?,question_type=?,question_text=?,explanation=?,difficulty=?,status=?,revision=revision+1
        WHERE id=?`,[...values,q.id],db);
      await query('DELETE FROM question_options WHERE question_id=?',[questionId],db);
      for (const option of q.options) await query('INSERT INTO question_options(question_id,label,option_text,is_correct) VALUES(?,?,?,?)',
        [questionId,option.label,option.option_text,option.is_correct],db);
    }
    await audit(db,req.user.id,id?'UPDATE':'CREATE','question_groups',groupId);
    const record=await readingRecord(groupId,db);
    return {...record,edit_version:editVersion('reading',record)};
  });
  res.status(id?200:201).json(saved);
}

async function examRecord(kind,id,db) {
  const c=kindOf(kind);
  const [parent]=await query(`SELECT * FROM ${c.table} WHERE id=?`,[id],db);
  if (!parent) fail(404,'Không tìm thấy bộ luyện/đề');
  const links=await query(`SELECT question_id FROM ${c.link} WHERE ${c.fk}=? ORDER BY position`,[id],db);
  const entries=await questionSummaries(links.map(link=>link.question_id),db);
  return {...parent,question_ids:links.map(link=>link.question_id),entries};
}

async function saveExam(req,res) {
  const kind=req.params.kind,c=kindOf(kind);
  const id=req.params.id?parseId(req.params.id):null;
  const body=examBody.parse(req.body);
  if (kind==='practice' && (!body.part_id || body.test_format)) fail(422,'Bộ luyện cần Part, không nhận loại đề');
  if (kind==='mock' && (body.part_id || !body.test_format)) fail(422,'Đề thi cần loại MINI/FULL, không nhận Part riêng');
  const saved=await transaction(async db=>{
    await lockContent(db);
    if (id) assertEditVersion(req,'exam-'+kind,await examRecord(kind,id,db),true);
    const entries=await questionSummaries(body.question_ids,db);
    if (kind==='practice' && entries.some(q=>q.part_id!==body.part_id)) fail(422,'Bộ luyện chỉ gồm câu cùng Part');
    if (body.status==='PUBLISHED') await assertExamReady(kind,body,body.question_ids,db);
    const columns=['title','description','time_limit','status',kind==='practice'?'part_id':'test_format'];
    const values=columns.map(column=>body[column]);
    const parentId=id || await insert(`INSERT INTO ${c.table}(${columns.join(',')}) VALUES(${columns.map(()=>'?').join(',')})`,values,db);
    if (id) await query(`UPDATE ${c.table} SET ${columns.map(column=>column+'=?').join(',')} WHERE id=?`,[...values,id],db);
    await query(`DELETE FROM ${c.link} WHERE ${c.fk}=?`,[parentId],db);
    if (body.question_ids.length) await query(`INSERT INTO ${c.link}(${c.fk},question_id,position)
      SELECT ?,question_id,ordinality FROM unnest(?::integer[]) WITH ORDINALITY AS v(question_id,ordinality)`,[parentId,body.question_ids],db);
    await audit(db,req.user.id,id?'UPDATE':'CREATE',c.table,parentId);
    const record=await examRecord(kind,parentId,db);
    return {...record,edit_version:editVersion('exam-'+kind,record)};
  });
  res.status(id?200:201).json(saved);
}

export function registerAdminContentRoutes(app) {
  const router=Router();
  router.get('/readings/:id',async(req,res)=>{
    const record=await transaction(async db=>{
      await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY');
      return readingRecord(parseId(req.params.id),db);
    });
    res.json({...record,edit_version:editVersion('reading',record)});
  });
  router.post('/readings',saveReading);
  router.put('/readings/:id',saveReading);
  router.delete('/readings/:id',async(req,res)=>{
    await transaction(async db=>{
      await lockContent(db);
      const record=await readingRecord(parseId(req.params.id),db);
      assertEditVersion(req,'reading',record,true);
      if (record.questions.some(q=>q.status==='PUBLISHED')) fail(409,'Chuyển bài đọc về bản nháp trước khi xóa');
      await checkReadingLinks(record,{...record,questions:[]},db);
      await query('DELETE FROM question_groups WHERE id=?',[record.id],db);
      await audit(db,req.user.id,'DELETE','question_groups',record.id);
    });
    res.sendStatus(204);
  });
  router.get('/exams/:kind/:id',async(req,res)=>{
    const record=await transaction(async db=>{
      await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY');
      return examRecord(req.params.kind,parseId(req.params.id),db);
    });
    res.json({...record,edit_version:editVersion('exam-'+req.params.kind,record)});
  });
  router.post('/exams/:kind',saveExam);
  router.put('/exams/:kind/:id',saveExam);
  app.use('/api/admin/content',router);
}
