import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import cookieParser from 'cookie-parser';
import {hashPassword} from './passwords.js';
import {registerAccountRoutes} from './account.js';
import {registerMediaRoutes} from './media.js';
import {registerDashboardRoutes} from './dashboard.js';
import {z} from 'zod';
import {query,insert,pool,transaction} from './db.js';
import {resources,readOnly,parseId} from './resources.js';
import {registerLearningRoutes} from './learning.js';
import {registerPersonalRoutes} from './personal.js';
import {registerAuthRoutes} from './auth.js';
import {auth} from './sessions.js';
import path from 'node:path';
import {fileURLToPath} from 'node:url';


export const app=express();
if(process.env.TRUST_PROXY==='true')app.set('trust proxy',1);
app.disable('x-powered-by');
app.use(helmet());app.use(express.json({limit:'256kb'}));app.use(cookieParser());
app.use('/api/auth',(req,res,next)=>{res.set('Cache-Control','no-store');next();});
app.use('/api',rateLimit({windowMs:60000,limit:300}));
app.use('/api',(req,res,next)=>{const origin=req.get('origin');if(origin && origin!==process.env.APP_ORIGIN)return res.status(403).json({error:'Origin không được phép'});if(!['GET','HEAD','OPTIONS'].includes(req.method)&&(req.cookies.token||req.cookies.refresh_token)&&!req.get('authorization')&&req.get('x-requested-with')!=='TOEICAdmin')return res.status(403).json({error:'Thiếu xác nhận nguồn yêu cầu'});next();});
export function fail(status,message){const e=new Error(message);e.status=status;throw e;}
app.get(['/health', '/api/health'], async (req, res) => {
  await query('SELECT 1');
  res.json({status: 'ok', database: 'postgresql'});
});
registerAuthRoutes(app);
registerAccountRoutes(app);
registerMediaRoutes(app);
function admin(req,res,next){if(req.user.role!=='ADMIN')return res.status(403).json({error:'Chỉ quản trị viên được phép'});next();}
app.get('/api/auth/me',auth,(req,res)=>res.json(req.user));
app.use('/api/admin',auth,admin);
app.get('/api/admin/meta',(req,res)=>res.json({resources:Object.fromEntries(Object.entries(resources).map(([key,r])=>[key,{label:r.label,fields:r.fields}])),readOnly}));
registerDashboardRoutes(app);
app.post('/api/admin/notifications/broadcast',async(req,res)=>{const b=z.object({type:z.enum(['SYSTEM','STUDY_REMINDER','NEW_CONTENT']).default('SYSTEM'),title:z.string().trim().min(1).max(200),body:z.string().max(5000).default(''),data:z.record(z.string(),z.unknown()).default({}),user_ids:z.array(z.number().int().positive()).max(1000).optional()}).strict().parse(req.body);const count=await transaction(async db=>{const rows=await query(`INSERT INTO notifications(user_id,type,title,body,data,created_by) SELECT id,?::varchar,?::varchar,?::text,?::jsonb,?::int FROM users WHERE status='ACTIVE'${b.user_ids?' AND id=ANY(?::int[])':" AND role='STUDENT'"} RETURNING id`,[b.type,b.title,b.body,JSON.stringify(b.data),req.user.id,...(b.user_ids?[b.user_ids]:[])],db);await query('INSERT INTO audit_logs(user_id,action,resource,record_id) VALUES(?,?,?,?)',[req.user.id,'BROADCAST','notifications',rows[0]?.id||0],db);return rows.length;});res.status(201).json({sent:count});});
async function decorateQuestion(q,db=pool){q.options=await query('SELECT label,option_text,is_correct FROM question_options WHERE question_id=? ORDER BY label',[q.id],db);q.options=q.options.map(o=>({...o,is_correct:Boolean(o.is_correct)}));if(q.group_id)q.passages=await query('SELECT position,passage_type,title,content,translation_vi,image_url FROM reading_passages WHERE group_id=? ORDER BY position',[q.group_id],db);return q;}
const allowed=new Set([...Object.keys(resources),...readOnly]);
app.param('resource',(req,res,next,r)=>{if(!allowed.has(r))return res.status(404).json({error:'Không tìm thấy tài nguyên'});next();});
app.get('/api/admin/:resource',async(req,res)=>{const t=req.params.resource;const page=z.coerce.number().int().min(1).max(100000).default(1).parse(req.query.page);const limit=z.coerce.number().int().min(1).max(100).default(20).parse(req.query.limit);const search=z.string().max(200).default('').parse(req.query.search);const cols=resources[t]?.fields.filter(f=>['text','textarea','email'].includes(f.type)).map(f=>f.name)||[];const where=search&&cols.length?' WHERE '+cols.map(c=>`${c} ILIKE ?`).join(' OR '):'';const args=where?cols.map(()=>`%${search}%`):[];const [count]=await query(`SELECT COUNT(*) total FROM ${t}${where}`,args);const safe=t==='users'?'id,email,full_name,avatar_url,firebase_uid,role,status,target_score,last_login_at,created_at':(t.endsWith('_answers')?'id,attempt_id,question_id,position,selected_label,is_correct':'*');const rows=await query(`SELECT ${safe} FROM ${t}${where} ORDER BY id DESC LIMIT ${limit} OFFSET ${(page-1)*limit}`,args);res.json({rows,total:count.total,page,limit});});
app.get('/api/admin/:resource/:id',async(req,res)=>{const t=req.params.resource;const [row]=await query(`SELECT * FROM ${t} WHERE id=?`,[parseId(req.params.id)]);if(!row)fail(404,'Không tìm thấy');delete row.password_hash;if(t==='questions')await decorateQuestion(row);res.json(row);});
async function validateRelations(t,b,db){
 if(t==='questions'){
  if(b.part_id===5&&b.group_id)fail(422,'Part 5 là câu đơn, không gắn nhóm đoạn văn');
  if(b.group_id){const [g]=await query('SELECT part_id FROM question_groups WHERE id=?',[b.group_id],db);if(!g||g.part_id!==b.part_id)fail(422,'Nhóm câu hỏi phải cùng Part');}
  if(b.status==='PUBLISHED'&&[6,7].includes(b.part_id)){
   if(!b.group_id)fail(422,'Part 6–7 cần gắn nhóm câu hỏi');
   const [p]=await query('SELECT COUNT(*) AS n FROM reading_passages WHERE group_id=?',[b.group_id],db);if(!p.n)fail(422,'Part 6–7 cần ít nhất một đoạn văn trong nhóm');
  }
 }
 if(t==='reading_passages'){const [g]=await query('SELECT part_id FROM question_groups WHERE id=?',[b.group_id],db);if(!g)fail(422,'Nhóm câu hỏi không tồn tại');if(g.part_id===6&&b.position>1)fail(422,'Part 6 chỉ có một đoạn văn');}
 if(t==='practice_set_questions'){
  const [set]=await query('SELECT part_id,status FROM practice_sets WHERE id=? FOR UPDATE',[b.practice_set_id],db);const [q]=await query('SELECT part_id,status FROM questions WHERE id=?',[b.question_id],db);
  if(!set||!q||set.part_id!==q.part_id)fail(422,'Câu hỏi và bộ luyện phải cùng Part');if(set.status==='PUBLISHED'&&q.status!=='PUBLISHED')fail(422,'Bộ luyện đã xuất bản chỉ nhận câu đã xuất bản');
 }
 if(t==='mock_test_questions'){const [test]=await query('SELECT status FROM mock_tests WHERE id=? FOR UPDATE',[b.mock_test_id],db);const [q]=await query('SELECT status FROM questions WHERE id=?',[b.question_id],db);if(!test||!q)fail(422,'Đề hoặc câu hỏi không tồn tại');if(test.status==='PUBLISHED'&&q.status!=='PUBLISHED')fail(422,'Đề đã xuất bản chỉ nhận câu đã xuất bản');}
}
async function publishCheck(t,b,id,db){if(['practice_sets','mock_tests'].includes(t)&&b.status==='PUBLISHED'){if(!id)fail(422,'Lưu bản nháp và thêm câu hỏi trước khi xuất bản');const link=t==='practice_sets'?'practice_set_questions':'mock_test_questions';const fk=t==='practice_sets'?'practice_set_id':'mock_test_id';const rows=await query(`SELECT q.id,q.status,q.part_id FROM ${link} m JOIN questions q ON q.id=m.question_id WHERE m.${fk}=?`,[id],db);if(!rows.length||rows.some(q=>q.status!=='PUBLISHED'))fail(422,'Cần ít nhất một câu hỏi và tất cả câu hỏi phải được xuất bản');if(t==='practice_sets'&&rows.some(q=>q.part_id!==b.part_id))fail(422,'Các câu trong bộ luyện phải cùng Part');}}
async function mutate(req,res){const t=req.params.resource,r=resources[t];if(!r)fail(405,'Dữ liệu này chỉ được xem');const id=req.params.id?parseId(req.params.id):null;const b=r.schema.strict().parse(req.body);const result=await transaction(async db=>{
 if(t==='users')await query('SELECT pg_advisory_xact_lock(118,2)',[],db);
 let old;if(id){[old]=await query(`SELECT * FROM ${t} WHERE id=? FOR UPDATE`,[id],db);if(!old)fail(404,'Không tìm thấy');}
 if(old&&['practice_set_questions','mock_test_questions'].includes(t)){const parent=t==='practice_set_questions'?'practice_sets':'mock_tests',fk=t==='practice_set_questions'?'practice_set_id':'mock_test_id';const [p]=await query(`SELECT status FROM ${parent} WHERE id=? FOR UPDATE`,[old[fk]],db);if(p.status==='PUBLISHED')fail(409,'Chuyển bộ luyện/đề về bản nháp trước khi sửa danh sách câu');}
 if(t==='users'){
  if(!id&&!b.password)fail(422,'Tài khoản mới cần mật khẩu');
  const admins=await query("SELECT id FROM users WHERE role='ADMIN' AND status='ACTIVE' ORDER BY id FOR UPDATE",[],db);
  if(old?.role==='ADMIN'&&old.status==='ACTIVE'&&(b.role!=='ADMIN'||b.status!=='ACTIVE')&&admins.length<=1)fail(409,'Không thể khóa hoặc hạ quyền quản trị viên cuối cùng');
  if(id===req.user.id&&(b.role!=='ADMIN'||b.status!=='ACTIVE'))fail(409,'Không thể tự khóa hoặc hạ quyền');
  if(b.password)b.password_hash=await hashPassword(b.password);delete b.password;
 }
 await validateRelations(t,b,db);await publishCheck(t,b,id,db);
 const lockedGroup=async gid=>(await query("SELECT id FROM questions WHERE group_id=? AND status='PUBLISHED' LIMIT 1",[gid],db)).length>0;
 if(t==='reading_passages'&&(await lockedGroup(b.group_id)||(old&&old.group_id!==b.group_id&&await lockedGroup(old.group_id))))fail(409,'Chuyển các câu trong nhóm về bản nháp trước khi sửa đoạn văn');
 if(t==='question_groups'&&id){const used=await query('SELECT part_id,status FROM questions WHERE group_id=?',[id],db);if(used.some(q=>q.part_id!==b.part_id))fail(409,'Không thể đổi Part của nhóm đã có câu hỏi');if(used.some(q=>q.status==='PUBLISHED'))fail(409,'Chuyển các câu trong nhóm về bản nháp trước khi sửa nội dung');}
 if(t==='questions'&&id){const linked=await query('SELECT s.part_id,s.status FROM practice_set_questions m JOIN practice_sets s ON s.id=m.practice_set_id WHERE m.question_id=?',[id],db);if(linked.some(s=>s.part_id!==b.part_id))fail(409,'Câu hỏi đang nằm trong bộ luyện Part khác');const mocks=await query("SELECT t.id FROM mock_test_questions m JOIN mock_tests t ON t.id=m.mock_test_id WHERE m.question_id=? AND t.status='PUBLISHED'",[id],db);if(b.status!=='PUBLISHED'&&(linked.some(s=>s.status==='PUBLISHED')||mocks.length))fail(409,'Chuyển bộ luyện/đề liên quan về bản nháp trước');}
 if(t==='users'&&id&&(b.password_hash||b.status!=='ACTIVE'||b.role!==old.role))await query('UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=?',[id],db);
 if(t==='notifications')b.created_by=req.user.id;
 const options=b.options;delete b.options;const columns=Object.keys(b);
 let recordId=id;if(id){await query(`UPDATE ${t} SET ${columns.map(c=>c+'=?').join(',')}${t==='questions'?',revision=revision+1':''} WHERE id=?`,[...Object.values(b),id],db);}else{recordId=await insert(`INSERT INTO ${t}(${columns.join(',')}) VALUES(${columns.map(()=>'?').join(',')})`,Object.values(b),db);}
 if(options){await query('DELETE FROM question_options WHERE question_id=?',[recordId],db);for(const o of options)await query('INSERT INTO question_options(question_id,label,option_text,is_correct) VALUES(?,?,?,?)',[recordId,o.label,o.option_text,o.is_correct],db);}
 await query('INSERT INTO audit_logs(user_id,action,resource,record_id) VALUES(?,?,?,?)',[req.user.id,id?'UPDATE':'CREATE',t,recordId],db);return recordId;
 });res.status(id?200:201).json({id:result});}
app.post('/api/admin/:resource',mutate);app.put('/api/admin/:resource/:id',mutate);
app.delete('/api/admin/:resource/:id',async(req,res)=>{const t=req.params.resource,id=parseId(req.params.id);if(!resources[t])fail(405,'Dữ liệu này chỉ được xem');if(t==='users')fail(405,'Khóa tài khoản để bảo toàn lịch sử học tập');await transaction(async db=>{const [old]=await query(`SELECT * FROM ${t} WHERE id=? FOR UPDATE`,[id],db);if(!old)fail(404,'Không tìm thấy');if(['practice_set_questions','mock_test_questions'].includes(t)){const parent=t==='practice_set_questions'?'practice_sets':'mock_tests';const fk=t==='practice_set_questions'?'practice_set_id':'mock_test_id';const [p]=await query(`SELECT status FROM ${parent} WHERE id=? FOR UPDATE`,[old[fk]],db);if(p.status==='PUBLISHED')fail(409,'Chuyển bộ luyện/đề về bản nháp trước khi xóa câu');}if(t==='reading_passages'&&(await query("SELECT id FROM questions WHERE group_id=? AND status='PUBLISHED' LIMIT 1",[old.group_id],db)).length)fail(409,'Chuyển các câu trong nhóm về bản nháp trước khi xóa đoạn văn');await query(`DELETE FROM ${t} WHERE id=?`,[id],db);await query('INSERT INTO audit_logs(user_id,action,resource,record_id) VALUES(?,?,?,?)',[req.user.id,'DELETE',t,id],db);});res.sendStatus(204);});
registerLearningRoutes(app,auth,decorateQuestion);
registerPersonalRoutes(app);
app.use('/api',(req,res)=>res.status(404).json({error:'API không tồn tại'}));
const dist=fileURLToPath(new URL('../dist/',import.meta.url));app.use(express.static(dist));app.get('/{*path}',(req,res)=>res.sendFile(path.join(dist,'index.html')));
app.use((e,req,res,next)=>{if(res.headersSent)return next(e);if(e.type==='entity.parse.failed')return res.status(400).json({error:'JSON không hợp lệ'});if(e.type==='entity.too.large'||e.code==='LIMIT_FILE_SIZE')return res.status(413).json({error:'Dữ liệu gửi lên quá lớn'});if(e.name==='MulterError')return res.status(422).json({error:'Tệp tải lên không hợp lệ'});if(e instanceof z.ZodError)return res.status(422).json({error:'Dữ liệu không hợp lệ',details:e.issues.map(i=>`${i.path.join('.')}: ${i.message}`)});
 if(['23505','23503','23514'].includes(e.code))return res.status(409).json({error:'Dữ liệu trùng, quan hệ không hợp lệ hoặc đang được sử dụng'});if(!e.status)console.error(e.code||e.message);res.status(e.status||500).json({error:e.status?e.message:'Lỗi máy chủ, vui lòng thử lại'});});
