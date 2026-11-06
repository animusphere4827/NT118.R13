import {z} from 'zod';
import {assertEditVersion,editVersion} from './admin-data.js';
import {query,transaction} from './db.js';
import {parseId} from './resources.js';

const fields = 'id,code,name,description,requirement';
const details = z.object({
  name:z.string().trim().min(1).max(80),
  description:z.string().trim().min(1).max(500)
}).strict();
const pagination = z.object({
  page:z.coerce.number().int().min(1).max(100000).default(1),
  limit:z.coerce.number().int().min(1).max(100).default(20)
}).strict();

function badgeRecord(row) {
  return {...row,edit_version:editVersion('badge_definitions',row)};
}

export function registerAdminBadgeRoutes(app) {
  app.get('/api/admin/badges',async(req,res)=>{
    const rows=await query(`SELECT b.id,b.code,b.name,b.description,b.requirement,
      (SELECT COUNT(*) FROM learner_badges a WHERE a.code=b.code) AS earned_count
      FROM badge_definitions b ORDER BY b.id`);
    res.json({rows:rows.map(({earned_count,...row})=>({...badgeRecord(row),earned_count}))});
  });

  app.put('/api/admin/badges/:id',async(req,res)=>{
    const id=parseId(req.params.id);
    const {name,description}=details.parse(req.body);
    const saved=await transaction(async db=>{
      const [badge]=await query(`SELECT ${fields} FROM badge_definitions WHERE id=? FOR UPDATE`,[id],db);
      if(!badge) throw Object.assign(new Error('Không tìm thấy huy hiệu'),{status:404});
      assertEditVersion(req,'badge_definitions',badge,true);
      if(badge.name===name && badge.description===description) return badge;
      const [updated]=await query(`UPDATE badge_definitions SET name=?,description=? WHERE id=? RETURNING ${fields}`,
        [name,description,id],db);
      await query("INSERT INTO audit_logs(user_id,action,resource,record_id) VALUES(?,'UPDATE','badge_definitions',?)",
        [req.user.id,id],db);
      return updated;
    });
    res.json(badgeRecord(saved));
  });

  app.get('/api/admin/badges/:id/recipients',async(req,res)=>{
    const id=parseId(req.params.id);
    const {page,limit}=pagination.parse(req.query);
    const [result]=await query(`WITH recipients AS (
      SELECT u.id AS user_id,u.full_name,a.unlocked_at,
        COALESCE(u.featured_badge=a.code,FALSE) AS featured
      FROM badge_definitions b JOIN learner_badges a ON a.code=b.code
      JOIN users u ON u.id=a.user_id WHERE b.id=?
    ), visible AS (
      SELECT * FROM recipients ORDER BY unlocked_at DESC,user_id DESC LIMIT ? OFFSET ?
    )
    SELECT EXISTS(SELECT 1 FROM badge_definitions WHERE id=?) AS found,
      (SELECT COUNT(*) FROM recipients) AS total,
      COALESCE((SELECT jsonb_agg(visible ORDER BY unlocked_at DESC,user_id DESC) FROM visible),'[]'::jsonb) AS rows`,
    [id,limit,(page-1)*limit,id]);
    const {found,...recipients}=result;
    if(!found) return res.status(404).json({error:'Không tìm thấy huy hiệu'});
    res.json({...recipients,page,limit});
  });
}
