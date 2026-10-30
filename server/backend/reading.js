import {Router} from 'express';
import {z} from 'zod';
import {query,transaction} from './db.js';
import {parseId,passageTypes} from './resources.js';

const listQuery = z.object({
  page:z.coerce.number().int().min(1).max(100000).default(1),
  limit:z.coerce.number().int().min(1).max(100).default(20),
  part_id:z.coerce.number().int().min(6).max(7).optional(),
  type:z.enum(passageTypes).optional(),
  search:z.string().trim().max(150).default('')
}).strict();
const detailQuery = z.object({translation:z.enum(['true','false']).default('false')}).strict();
const lookupBody = z.object({keyword:z.string().trim().min(1).max(150)}).strict();
const normalize = value => value.normalize('NFKC').toLowerCase().replaceAll('’',"'");
const tokens = value => normalize(value).match(/[\p{L}\p{N}]+(?:['-][\p{L}\p{N}]+)*/gu) || [];
const fail = (status,message) => { throw Object.assign(new Error(message),{status}); };

async function loadReading(id,userId,db) {
  const [reading] = await query(`SELECT r.*,p.completed_at FROM published_readings r
    LEFT JOIN user_reading_progress p ON p.group_id=r.id AND p.user_id=? WHERE r.id=?`,[userId,id],db);
  if (!reading) fail(404,'Không tìm thấy bài đọc đã xuất bản');
  return reading;
}

export function registerReadingRoutes(app) {
  const router = Router();
  router.get('/readings',async (req,res) => {
    const {page,limit,part_id,type,search} = listQuery.parse(req.query);
    const where = [];
    const params = [req.user.id];
    if (part_id) { where.push('r.part_id=?'); params.push(part_id); }
    if (type) { where.push('?=ANY(r.passage_types)'); params.push(type); }
    if (search) {
      where.push(`(r.title ILIKE ? OR EXISTS (
        SELECT 1 FROM reading_passages p WHERE p.group_id=r.id AND p.title ILIKE ?))`);
      const pattern = '%' + search.replace(/[\\%_]/g,value => '\\' + value) + '%';
      params.push(pattern,pattern);
    }
    const filter = where.length ? 'WHERE ' + where.join(' AND ') : '';
    const [result] = await query(`WITH filtered AS (
        SELECT r.*,p.completed_at FROM published_readings r
        LEFT JOIN user_reading_progress p ON p.group_id=r.id AND p.user_id=? ${filter}
      ), page AS (SELECT * FROM filtered ORDER BY id LIMIT ? OFFSET ?)
      SELECT (SELECT COUNT(*) FROM filtered) AS total,
        COALESCE((SELECT jsonb_agg(page ORDER BY id) FROM page),'[]'::jsonb) AS rows`,
    [...params,limit,(page-1)*limit]);
    res.set('Cache-Control','no-store').json({...result,page,limit});
  });

  router.get('/readings/:id',async (req,res) => {
    const id = parseId(req.params.id);
    const {translation} = detailQuery.parse(req.query);
    const result = await transaction(async db => {
      await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY');
      const reading = await loadReading(id,req.user.id,db);
      const passages = await query(`SELECT id,position,passage_type,title,content,image_url
        ${translation==='true' ? ',translation_vi' : ''}
        FROM reading_passages WHERE group_id=? ORDER BY position`,[id],db);
      return {...reading,passages};
    });
    res.set('Cache-Control','no-store').json(result);
  });

  router.put('/readings/:id/progress',async (req,res) => {
    const id = parseId(req.params.id);
    z.object({completed:z.literal(true)}).strict().parse(req.body);
    const result = await transaction(async db => {
      await loadReading(id,req.user.id,db);
      const [row] = await query(`INSERT INTO user_reading_progress(user_id,group_id) VALUES(?,?)
        ON CONFLICT (user_id,group_id) DO UPDATE SET completed_at=user_reading_progress.completed_at
        RETURNING group_id,completed_at`,[req.user.id,id],db);
      return row;
    });
    res.set('Cache-Control','no-store').json(result);
  });

  router.post('/readings/:id/lookups',async (req,res) => {
    const id = parseId(req.params.id);
    const {keyword} = lookupBody.parse(req.body);
    const normalized = normalize(keyword);
    if (tokens(normalized).length !== 1 || tokens(normalized)[0] !== normalized) {
      fail(422,'Chọn một từ trong bài đọc');
    }
    const result = await transaction(async db => {
      await loadReading(id,req.user.id,db);
      const passages = await query('SELECT content FROM reading_passages WHERE group_id=? ORDER BY position',[id],db);
      if (!passages.some(passage => tokens(passage.content).includes(normalized))) {
        fail(422,'Từ không có trong bài đọc');
      }
      const rows = await query(`SELECT v.id,v.word,v.ipa,v.part_of_speech,v.meaning_vi,
          v.definition_en,v.example_sentence,v.audio_url,v.image_url,t.name AS topic
        FROM vocabularies v JOIN vocabulary_topics t ON t.id=v.topic_id
        WHERE lower(v.word)=? AND v.status='PUBLISHED' AND t.status='PUBLISHED'
        ORDER BY v.id LIMIT 20`,[normalized],db);
      const [history] = await query(`INSERT INTO lookup_history(user_id,keyword,vocabulary_id,source)
        VALUES(?,?,?,'READING') ON CONFLICT (user_id,(lower(keyword))) DO UPDATE
        SET lookup_count=lookup_history.lookup_count+1,last_looked_up_at=now(),
          source='READING',vocabulary_id=EXCLUDED.vocabulary_id
        RETURNING id,vocabulary_id,lookup_count`,[req.user.id,normalized,rows[0]?.id ?? null],db);
      return {keyword:normalized,rows,lookup:history};
    });
    res.status(201).set('Cache-Control','no-store').json(result);
  });
  app.use('/api/learn',router);
}
