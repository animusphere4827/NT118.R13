import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import cookieParser from 'cookie-parser';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import {z} from 'zod';
import {query,pool,transaction} from './db.js';
import {resources,readOnly,parseId} from './resources.js';
import {registerLearningRoutes} from './learning.js';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const production=process.env.NODE_ENV==='production';
if(!process.env.JWT_SECRET || process.env.JWT_SECRET.length<32)throw new Error('JWT_SECRET must contain at least 32 characters');
export const app=express();
if(process.env.TRUST_PROXY==='true')app.set('trust proxy',1);
app.disable('x-powered-by');
app.use(helmet());app.use(express.json({limit:'256kb'}));app.use(cookieParser());
app.use('/api',rateLimit({windowMs:60000,limit:300}));
app.use('/api',(req,res,next)=>{const origin=req.get('origin');if(origin && origin!==process.env.APP_ORIGIN)return res.status(403).json({error:'Origin không được phép'});if(!['GET','HEAD','OPTIONS'].includes(req.method)&&req.cookies.token&&!req.get('authorization')&&req.get('x-requested-with')!=='TOEICAdmin')return res.status(403).json({error:'Thiếu xác nhận nguồn yêu cầu'});next();});
export function fail(status,message){const e=new Error(message);e.status=status;throw e;}
const authLimit=rateLimit({windowMs:15*60000,limit:30});
const credentials=z.object({email:z.email().max(254).transform(s=>s.toLowerCase()),password:z.string().min(12).max(128)});
function sign(user){return jwt.sign({sub:String(user.id)},process.env.JWT_SECRET,{expiresIn:'2h',issuer:'toeic-studio',audience:'toeic-app'});}
const publicUser=u=>({id:u.id,email:u.email,full_name:u.full_name,role:u.role,status:u.status,target_score:u.target_score});
app.get('/api/health',async(req,res)=>{await query('SELECT 1');res.json({status:'ok',database:'mysql'});});
app.post('/api/auth/register',authLimit,async(req,res)=>{const b=credentials.extend({full_name:z.string().trim().min(1).max(150)}).parse(req.body);const r=await query('INSERT INTO users(email,password_hash,full_name) VALUES(?,?,?)',[b.email,await bcrypt.hash(b.password,12),b.full_name]);res.status(201).json({id:r.insertId});});
app.post('/api/auth/login',authLimit,async(req,res)=>{const b=credentials.parse(req.body);const [u]=await query('SELECT * FROM users WHERE email=?',[b.email]);const valid=await bcrypt.compare(b.password,u?.password_hash||'$2b$12$FjSfpryrWrPDwJeTMoVKj.1Ii/kqJe63bu1NIGog6wrx.mvmMeXxy');if(!u||!valid||u.status!=='ACTIVE')fail(401,'Email hoặc mật khẩu không đúng');const token=sign(u);res.cookie('token',token,{httpOnly:true,secure:production,sameSite:'strict',maxAge:7200000,path:'/'});res.json({token,user:publicUser(u)});});
app.post('/api/auth/logout',(req,res)=>{res.clearCookie('token',{path:'/',secure:production,sameSite:'strict'});res.sendStatus(204);});
export async function auth(req,res,next){try{const header=req.get('authorization');const token=header?header.replace(/^Bearer /,''):req.cookies.token;const payload=jwt.verify(token,process.env.JWT_SECRET,{algorithms:['HS256'],issuer:'toeic-studio',audience:'toeic-app'});const [u]=await query('SELECT id,email,full_name,role,status,target_score FROM users WHERE id=?',[parseId(payload.sub)]);if(!u||u.status!=='ACTIVE')fail(401,'Phiên đăng nhập không hợp lệ');req.user=u;next();}catch(e){if(e.code)return next(e);res.status(401).json({error:'Vui lòng đăng nhập lại'});}}
function admin(req,res,next){if(req.user.role!=='ADMIN')return res.status(403).json({error:'Chỉ quản trị viên được phép'});next();}
app.get('/api/auth/me',auth,(req,res)=>res.json(req.user));
app.use('/api/admin',auth,admin);
app.get('/api/admin/meta',(req,res)=>res.json({resources:Object.fromEntries(Object.entries(resources).map(([key,r])=>[key,{label:r.label,fields:r.fields}])),readOnly}));
app.get('/api/admin/dashboard',async(req,res)=>{
 const counts={};for(const t of ['users','vocabularies','grammar_lessons','questions','practice_sets','mock_tests']){const [r]=await query(`SELECT COUNT(*) AS n FROM ${t}`);counts[t]=r.n;}
 const [performance]=await query("SELECT COUNT(*) AS completed, COALESCE(ROUND(AVG(accuracy),1),0) AS accuracy FROM (SELECT accuracy FROM practice_attempts WHERE status='SUBMITTED' UNION ALL SELECT accuracy FROM mock_test_attempts WHERE status='SUBMITTED') a");
 const activity=await query("SELECT DATE_FORMAT(day,'%Y-%m-%d') AS day, COUNT(*) AS count FROM (SELECT DATE(started_at) day FROM practice_attempts UNION ALL SELECT DATE(started_at) day FROM mock_test_attempts) a WHERE day>=UTC_DATE()-INTERVAL 13 DAY GROUP BY day ORDER BY day");
 const parts=await query('SELECT p.id,p.name,p.skill,COUNT(q.id) AS questions FROM toeic_parts p LEFT JOIN questions q ON q.part_id=p.id GROUP BY p.id,p.name,p.skill ORDER BY p.id');
 const recent=await query('SELECT a.id,a.action,a.resource,a.record_id,a.created_at,u.full_name FROM audit_logs a JOIN users u ON u.id=a.user_id ORDER BY a.id DESC LIMIT 8');res.json({counts,performance,activity,parts,recent});
});
async function decorateQuestion(q,db=pool){q.options=await query('SELECT label,option_text,is_correct FROM question_options WHERE question_id=? ORDER BY label',[q.id],db);q.options=q.options.map(o=>({...o,is_correct:Boolean(o.is_correct)}));return q;}
const allowed=new Set([...Object.keys(resources),...readOnly]);
app.param('resource',(req,res,next,r)=>{if(!allowed.has(r))return res.status(404).json({error:'Không tìm thấy tài nguyên'});next();});
app.get('/api/admin/:resource',async(req,res)=>{const t=req.params.resource;const page=z.coerce.number().int().min(1).max(100000).default(1).parse(req.query.page);const limit=z.coerce.number().int().min(1).max(100).default(20).parse(req.query.limit);const search=z.string().max(200).default('').parse(req.query.search);const cols=resources[t]?.fields.filter(f=>['text','textarea','email'].includes(f.type)).map(f=>f.name)||[];const where=search&&cols.length?' WHERE '+cols.map(c=>`${c} LIKE ?`).join(' OR '):'';const args=where?cols.map(()=>`%${search}%`):[];const [count]=await query(`SELECT COUNT(*) total FROM ${t}${where}`,args);const safe=t==='users'?'id,email,full_name,role,status,target_score,created_at':(t.endsWith('_answers')?'id,attempt_id,question_id,position,selected_label,is_correct':'*');const rows=await query(`SELECT ${safe} FROM ${t}${where} ORDER BY id DESC LIMIT ${limit} OFFSET ${(page-1)*limit}`,args);res.json({rows,total:count.total,page,limit});});
app.get('/api/admin/:resource/:id',async(req,res)=>{const t=req.params.resource;const [row]=await query(`SELECT * FROM ${t} WHERE id=?`,[parseId(req.params.id)]);if(!row)fail(404,'Không tìm thấy');delete row.password_hash;if(t==='questions')await decorateQuestion(row);res.json(row);});
async function validateRelations(t,b,db){
 if(t==='questions'){
  if(b.options.length!==(b.part_id===2?3:4))fail(422,'Part 2 cần 3 đáp án; các Part khác cần 4 đáp án');
  if(b.group_id){const [g]=await query('SELECT part_id FROM question_groups WHERE id=?',[b.group_id],db);if(!g||g.part_id!==b.part_id)fail(422,'Nhóm câu hỏi phải cùng Part');}
  if(b.status==='PUBLISHED'){
   const [g]=b.group_id?await query('SELECT * FROM question_groups WHERE id=?',[b.group_id],db):[];
   if(b.part_id<=4 && !g?.audio_url)fail(422,'Câu Listening cần nhóm có URL audio');
   if(b.part_id===1 && !g?.image_url)fail(422,'Part 1 cần hình ảnh');
   if([6,7].includes(b.part_id)&&!g?.passage_text)fail(422,'Part 6–7 cần đoạn văn trong nhóm');
  }
 }
 if(t==='practice_set_questions'){
  const [set]=await query('SELECT part_id,status FROM practice_sets WHERE id=? FOR UPDATE',[b.practice_set_id],db);const [q]=await query('SELECT part_id,status FROM questions WHERE id=?',[b.question_id],db);
  if(!set||!q||set.part_id!==q.part_id)fail(422,'Câu hỏi và bộ luyện phải cùng Part');if(set.status==='PUBLISHED'&&q.status!=='PUBLISHED')fail(422,'Bộ luyện đã xuất bản chỉ nhận câu đã xuất bản');
 }
 if(t==='mock_test_questions'){const [test]=await query('SELECT status FROM mock_tests WHERE id=? FOR UPDATE',[b.mock_test_id],db);const [q]=await query('SELECT status FROM questions WHERE id=?',[b.question_id],db);if(!test||!q)fail(422,'Đề hoặc câu hỏi không tồn tại');if(test.status==='PUBLISHED'&&q.status!=='PUBLISHED')fail(422,'Đề đã xuất bản chỉ nhận câu đã xuất bản');}
}
async function publishCheck(t,b,id,db){if(['practice_sets','mock_tests'].includes(t)&&b.status==='PUBLISHED'){if(!id)fail(422,'Lưu bản nháp và thêm câu hỏi trước khi xuất bản');const link=t==='practice_sets'?'practice_set_questions':'mock_test_questions';const fk=t==='practice_sets'?'practice_set_id':'mock_test_id';const rows=await query(`SELECT q.id,q.status,q.part_id FROM ${link} m JOIN questions q ON q.id=m.question_id WHERE m.${fk}=?`,[id],db);if(!rows.length||rows.some(q=>q.status!=='PUBLISHED'))fail(422,'Cần ít nhất một câu hỏi và tất cả câu hỏi phải được xuất bản');if(t==='practice_sets'&&rows.some(q=>q.part_id!==b.part_id))fail(422,'Các câu trong bộ luyện phải cùng Part');}}
async function mutate(req,res){const t=req.params.resource,r=resources[t];if(!r)fail(405,'Dữ liệu này chỉ được xem');const id=req.params.id?parseId(req.params.id):null;const b=r.schema.strict().parse(req.body);const result=await transaction(async db=>{
 let old;if(id){[old]=await query(`SELECT * FROM ${t} WHERE id=? FOR UPDATE`,[id],db);if(!old)fail(404,'Không tìm thấy');}
 if(old&&['practice_set_questions','mock_test_questions'].includes(t)){const parent=t==='practice_set_questions'?'practice_sets':'mock_tests',fk=t==='practice_set_questions'?'practice_set_id':'mock_test_id';const [p]=await query(`SELECT status FROM ${parent} WHERE id=? FOR UPDATE`,[old[fk]],db);if(p.status==='PUBLISHED')fail(409,'Chuyển bộ luyện/đề về bản nháp trước khi sửa danh sách câu');}
 if(t==='users'){
  if(!id&&!b.password)fail(422,'Tài khoản mới cần mật khẩu');
  // Serialize changes to administrators, preventing concurrent removal of the final active admin.
  const admins=await query("SELECT id FROM users WHERE role='ADMIN' AND status='ACTIVE' ORDER BY id FOR UPDATE",[],db);
  if(old?.role==='ADMIN'&&old.status==='ACTIVE'&&(b.role!=='ADMIN'||b.status!=='ACTIVE')&&admins.length<=1)fail(409,'Không thể khóa hoặc hạ quyền quản trị viên cuối cùng');
  if(id===req.user.id&&(b.role!=='ADMIN'||b.status!=='ACTIVE'))fail(409,'Không thể tự khóa hoặc hạ quyền');
  if(b.password)b.password_hash=await bcrypt.hash(b.password,12);delete b.password;
 }
 await validateRelations(t,b,db);await publishCheck(t,b,id,db);
 if(t==='question_groups'&&id){const used=await query('SELECT part_id,status FROM questions WHERE group_id=?',[id],db);if(used.some(q=>q.part_id!==b.part_id))fail(409,'Không thể đổi Part của nhóm đã có câu hỏi');if(used.some(q=>q.status==='PUBLISHED'))fail(409,'Chuyển các câu trong nhóm về bản nháp trước khi sửa nội dung');}
 if(t==='questions'&&id){const linked=await query('SELECT s.part_id,s.status FROM practice_set_questions m JOIN practice_sets s ON s.id=m.practice_set_id WHERE m.question_id=?',[id],db);if(linked.some(s=>s.part_id!==b.part_id))fail(409,'Câu hỏi đang nằm trong bộ luyện Part khác');const mocks=await query("SELECT t.id FROM mock_test_questions m JOIN mock_tests t ON t.id=m.mock_test_id WHERE m.question_id=? AND t.status='PUBLISHED'",[id],db);if(b.status!=='PUBLISHED'&&(linked.some(s=>s.status==='PUBLISHED')||mocks.length))fail(409,'Chuyển bộ luyện/đề liên quan về bản nháp trước');}
 const options=b.options;delete b.options;const columns=Object.keys(b);
 let recordId=id;if(id){await query(`UPDATE ${t} SET ${columns.map(c=>c+'=?').join(',')}${t==='questions'?',revision=revision+1':''} WHERE id=?`,[...Object.values(b),id],db);}else{const result=await query(`INSERT INTO ${t}(${columns.join(',')}) VALUES(${columns.map(()=>'?').join(',')})`,Object.values(b),db);recordId=result.insertId;}
 if(options){await query('DELETE FROM question_options WHERE question_id=?',[recordId],db);for(const o of options)await query('INSERT INTO question_options(question_id,label,option_text,is_correct) VALUES(?,?,?,?)',[recordId,o.label,o.option_text,o.is_correct],db);}
 await query('INSERT INTO audit_logs(user_id,action,resource,record_id) VALUES(?,?,?,?)',[req.user.id,id?'UPDATE':'CREATE',t,recordId],db);return recordId;
 });res.status(id?200:201).json({id:result});}
app.post('/api/admin/:resource',mutate);app.put('/api/admin/:resource/:id',mutate);
app.delete('/api/admin/:resource/:id',async(req,res)=>{const t=req.params.resource,id=parseId(req.params.id);if(!resources[t])fail(405,'Dữ liệu này chỉ được xem');if(t==='users')fail(405,'Khóa tài khoản để bảo toàn lịch sử học tập');await transaction(async db=>{const [old]=await query(`SELECT * FROM ${t} WHERE id=? FOR UPDATE`,[id],db);if(!old)fail(404,'Không tìm thấy');if(['practice_set_questions','mock_test_questions'].includes(t)){const parent=t==='practice_set_questions'?'practice_sets':'mock_tests';const fk=t==='practice_set_questions'?'practice_set_id':'mock_test_id';const [p]=await query(`SELECT status FROM ${parent} WHERE id=? FOR UPDATE`,[old[fk]],db);if(p.status==='PUBLISHED')fail(409,'Chuyển bộ luyện/đề về bản nháp trước khi xóa câu');}await query(`DELETE FROM ${t} WHERE id=?`,[id],db);await query('INSERT INTO audit_logs(user_id,action,resource,record_id) VALUES(?,?,?,?)',[req.user.id,'DELETE',t,id],db);});res.sendStatus(204);});
registerLearningRoutes(app,auth,decorateQuestion);
app.use('/api',(req,res)=>res.status(404).json({error:'API không tồn tại'}));
const dist=fileURLToPath(new URL('../dist/',import.meta.url));app.use(express.static(dist));app.get('/{*path}',(req,res)=>res.sendFile(path.join(dist,'index.html')));
app.use((e,req,res,next)=>{if(e instanceof z.ZodError)return res.status(422).json({error:'Dữ liệu không hợp lệ',details:e.issues.map(i=>`${i.path.join('.')}: ${i.message}`)});if(['ER_DUP_ENTRY','ER_ROW_IS_REFERENCED_2','ER_NO_REFERENCED_ROW_2','ER_CHECK_CONSTRAINT_VIOLATED'].includes(e.code))return res.status(409).json({error:'Dữ liệu trùng, quan hệ không hợp lệ hoặc đang được sử dụng'});if(!e.status)console.error(e.code||e.message);res.status(e.status||500).json({error:e.status?e.message:'Lỗi máy chủ, vui lòng thử lại'});});
