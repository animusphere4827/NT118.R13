import {createHash} from 'node:crypto';
import {z} from 'zod';
import {query} from './db.js';
import {resources} from './resources.js';

const normalized=value=>JSON.parse(JSON.stringify(value));
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value==='object') return Object.fromEntries(Object.keys(value).sort().map(key=>[key,canonical(value[key])]));
  return value;
}
export function editVersion(resource,record) {
  const {edit_version,password_hash,...safe}=record;
  return createHash('sha256').update(resource+JSON.stringify(canonical(normalized(safe)))).digest('hex');
}
export function assertEditVersion(req,resource,record,required=false) {
  const raw=req.get('If-Match');
  const version=raw?.replace(/^"|"$/g,'');
  if(version && !/^[a-f0-9]{64}$/.test(version)) throw Object.assign(new Error('If-Match không hợp lệ'),{status:422});
  if (!version && required) throw Object.assign(new Error('Tải lại bản ghi trước khi lưu'),{status:428});
  if (version && version!==editVersion(resource,record)) {
    throw Object.assign(new Error('Bản ghi đã được người khác sửa. Tải lại trước khi lưu để tránh ghi đè.'),{status:409});
  }
}
export async function lockContent(db) {
  await db.query('SELECT pg_advisory_xact_lock(118,23)');
}
export function resourceMetadata(resource) {
  return {label:resource.label,fields:resource.fields.map(field=>{
    const schema=resource.schema.shape[field.name];
    const json=z.toJSONSchema(schema,{unrepresentable:'any'});
    return {...field,required:!schema.safeParse(undefined).success,
      ...(json.maxLength===undefined?{}:{maxLength:json.maxLength}),
      ...(json.minimum===undefined?{}:{min:json.minimum}),
      ...(json.maximum===undefined?{}:{max:json.maximum}),
      ...(json.default===undefined?{}:{defaultValue:json.default})};
  })};
}

export async function adminList(resource,input) {
  const fields=resources[resource]?.fields || [];
  const shape={page:z.coerce.number().int().min(1).max(100000).default(1),
    limit:z.coerce.number().int().min(1).max(100).default(20),search:z.string().trim().max(200).default('')};
  for (const field of fields) {
    if (field.type==='relation') shape[field.name]=z.coerce.number().int().min(1).max(2147483647).optional();
  }
  if (fields.some(field=>field.name==='status')) shape.status=z.enum(resource==='users'?['ACTIVE','LOCKED','DISABLED']:['DRAFT','PUBLISHED']).optional();
  const {page,limit,search,...filters}=z.object(shape).strict().parse(input);
  const clauses=[];
  const args=[];
  for (const [key,value] of Object.entries(filters)) if (value!==undefined) {clauses.push(`${key}=?`);args.push(value);}
  if (search) {
    const match=/^#([1-9][0-9]*)$/.exec(search);
    if (match) {clauses.push('id=?');args.push(z.coerce.number().int().max(2147483647).parse(match[1]));}
    else {
      const columns=fields.filter(field=>['text','textarea','email'].includes(field.type)).map(field=>field.name);
      if (columns.length) {
        clauses.push('('+columns.map(column=>`${column} ILIKE ?`).join(' OR ')+')');
        args.push(...columns.map(()=>'%'+search.replace(/[\\%_]/g,value=>'\\'+value)+'%'));
      } else clauses.push('FALSE');
    }
  }
  const safe=resource==='users'?'id,email,full_name,avatar_url,firebase_uid,role,status,target_score,last_login_at,created_at':
    resource.endsWith('_answers')?'id,attempt_id,question_id,position,selected_label,is_correct':'*';
  const extra=resource==='question_groups'?`,(SELECT COUNT(*) FROM reading_passages p WHERE p.group_id=question_groups.id) AS passage_count,
    (SELECT COUNT(*) FROM questions q WHERE q.group_id=question_groups.id) AS question_count,
    EXISTS(SELECT 1 FROM questions q WHERE q.group_id=question_groups.id AND q.status='PUBLISHED') AS is_published`:
    ['mock_tests','practice_sets'].includes(resource)?`,(SELECT COUNT(*) FROM ${resource==='mock_tests'?'mock_test_questions':'practice_set_questions'} m
      WHERE m.${resource==='mock_tests'?'mock_test_id':'practice_set_id'}=${resource}.id) AS question_count`:'';
  const [result]=await query(`WITH filtered AS (SELECT ${safe}${extra} FROM ${resource}
      ${clauses.length?'WHERE '+clauses.join(' AND '):''}), page AS (SELECT * FROM filtered ORDER BY id DESC LIMIT ? OFFSET ?)
    SELECT (SELECT COUNT(*) FROM filtered) AS total,
      COALESCE((SELECT jsonb_agg(page ORDER BY id DESC) FROM page),'[]'::jsonb) AS rows`,[...args,limit,(page-1)*limit]);
  return {...result,page,limit};
}
