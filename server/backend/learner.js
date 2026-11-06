import {Router} from 'express';
import {z} from 'zod';
import {query,transaction} from './db.js';
import {parseId} from './resources.js';

const fail=(status,message)=>{throw Object.assign(new Error(message),{status});};
const pageQuery=z.object({page:z.coerce.number().int().min(1).max(100000).default(1),limit:z.coerce.number().int().min(1).max(50).default(20)}).strict();
const hasReadingAnswer = table => `EXISTS (
  SELECT 1 FROM ${table} a WHERE a.attempt_id=t.id AND a.selected_label IS NOT NULL
)`;
const readingAnswers = `WITH all_answers AS (
  SELECT 'practice' AS kind,t.id AS attempt_id,t.content_title,t.submitted_at,a.question_id,a.position,
    a.selected_label,a.is_correct,a.snapshot FROM practice_answers a JOIN practice_attempts t ON t.id=a.attempt_id
    WHERE t.user_id=? AND t.status='SUBMITTED' AND t.result IS NOT NULL AND ${hasReadingAnswer('practice_answers')}
  UNION ALL
  SELECT 'mock',t.id,t.content_title,t.submitted_at,a.question_id,a.position,a.selected_label,a.is_correct,a.snapshot
    FROM mock_test_answers a JOIN mock_test_attempts t ON t.id=a.attempt_id
    WHERE t.user_id=? AND t.status='SUBMITTED' AND t.result IS NOT NULL AND ${hasReadingAnswer('mock_test_answers')}
)`;
const events=`WITH learner AS (SELECT ?::integer AS id), events AS (
  SELECT completed_at AS at,'vocabulary' AS kind FROM course_vocabulary_progress WHERE user_id=(SELECT id FROM learner) AND completed_at IS NOT NULL
  UNION ALL SELECT completed_at,'reading' FROM user_reading_progress WHERE user_id=(SELECT id FROM learner)
  UNION ALL SELECT updated_at,'grammar' FROM user_grammar_progress WHERE user_id=(SELECT id FROM learner) AND progress_percent=100
  UNION ALL SELECT submitted_at,'practice' FROM practice_attempts t WHERE user_id=(SELECT id FROM learner) AND status='SUBMITTED' AND result IS NOT NULL
    AND ${hasReadingAnswer('practice_answers')}
  UNION ALL SELECT submitted_at,'mock' FROM mock_test_attempts t WHERE user_id=(SELECT id FROM learner) AND status='SUBMITTED' AND result IS NOT NULL
    AND ${hasReadingAnswer('mock_test_answers')}
  UNION ALL SELECT submitted_at,'quiz' FROM quiz_attempts t WHERE user_id=(SELECT id FROM learner) AND status='SUBMITTED' AND result IS NOT NULL
    AND EXISTS(SELECT 1 FROM quiz_answers a WHERE a.attempt_id=t.id AND a.answer IS NOT NULL)
  UNION ALL SELECT last_reviewed_at,'flashcard' FROM flashcard_review_days WHERE user_id=(SELECT id FROM learner)
)`;

export function streaks(days,today) {
  const unique=[...new Set(days)].sort();
  const number=day=>Math.floor(Date.parse(day+'T00:00:00Z')/86400000);
  let longest=0,run=0,previous=null;
  for(const day of unique) {
    const value=number(day);
    run=previous!==null && value===previous+1?run+1:1;
    longest=Math.max(longest,run);previous=value;
  }
  let current=0,cursor=number(today);
  const seen=new Set(unique.map(number));
  if(!seen.has(cursor)) cursor--;
  while(seen.has(cursor)) {current++;cursor--;}
  return {current_streak:current,longest_streak:longest};
}

export async function learnerStats(userId,db) {
  const [{activity,...counts}]=await query(`${events}, daily AS (
    SELECT (at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date::text AS day,COUNT(*)::integer AS count
    FROM events WHERE at IS NOT NULL GROUP BY 1
  )
  SELECT COALESCE((SELECT jsonb_agg(daily ORDER BY day) FROM daily),'[]'::jsonb) AS activity,
    COUNT(*) FILTER(WHERE kind='vocabulary') AS vocabulary_completed,
    COUNT(*) FILTER(WHERE kind='reading') AS readings_completed,COUNT(*) FILTER(WHERE kind='grammar') AS grammar_completed,
    COUNT(*) FILTER(WHERE kind='practice') AS practice_completed,COUNT(*) FILTER(WHERE kind='mock') AS mocks_completed,
    COUNT(*) FILTER(WHERE kind='quiz') AS quizzes_completed,
    (SELECT (clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date::text) AS today FROM events`,[userId],db);
  const parts=await query(`${readingAnswers}
    SELECT (snapshot->>'part_id')::integer AS part_id,snapshot->>'question_type' AS question_type,
      COUNT(*) AS total,COUNT(*) FILTER(WHERE is_correct) AS correct FROM all_answers
    GROUP BY 1,2 ORDER BY 1,2`,[userId,userId],db);
  const total=parts.reduce((n,row)=>n+row.total,0),correct=parts.reduce((n,row)=>n+row.correct,0);
  const by_part=[5,6,7].map(part_id=>{
    const rows=parts.filter(row=>row.part_id===part_id),total=rows.reduce((n,row)=>n+row.total,0),correct=rows.reduce((n,row)=>n+row.correct,0);
    return {part_id,total,correct,accuracy:total?Math.round(correct/total*10000)/100:null};
  });
  const weak_types=parts.filter(row=>row.total>=3 && row.correct/row.total<0.8)
    .map(row=>({...row,accuracy:Math.round(row.correct/row.total*10000)/100})).sort((a,b)=>a.accuracy-b.accuracy).slice(0,5);
  return {...counts,study_days:activity.length,...streaks(activity.map(row=>row.day),counts.today),activity:activity.slice(-30),
    by_part,weak_types,total_questions:total,correct_count:correct,accuracy:total?Math.round(correct/total*10000)/100:null,
    score_note:'Tỷ lệ đúng Reading luyện tập, không phải điểm TOEIC quy đổi.',
    recommendation:weak_types.length?'Ôn lại câu sai và luyện thêm dạng câu có tỷ lệ đúng thấp.':'Tiếp tục bài đang mở trong lộ trình; ôn từ đã học trước khi làm bài mới.'};
}

async function readingPrediction(userId) {
  const [sample] = await query(`WITH latest_per_test AS (
    SELECT DISTINCT ON (t.mock_test_id) t.id,t.correct_count,t.submitted_at
    FROM mock_test_attempts t
    WHERE t.user_id=? AND t.status='SUBMITTED' AND t.submitted_at IS NOT NULL
      AND t.total_questions=100 AND t.deadline_at-t.started_at=interval '75 minutes'
      AND t.correct_count BETWEEN 0 AND 100
      AND t.result @> '{"by_part":[{"part_id":5,"total_questions":30},{"part_id":6,"total_questions":16},{"part_id":7,"total_questions":54}]}'::jsonb
      AND ${hasReadingAnswer('mock_test_answers')}
    ORDER BY t.mock_test_id,t.submitted_at DESC,t.id DESC
  ), recent AS (
    SELECT correct_count FROM latest_per_test ORDER BY submitted_at DESC,id DESC LIMIT 5
  )
  SELECT COUNT(*)::integer AS sample_count,ROUND(AVG(correct_count))::integer AS estimated_correct FROM recent`, [userId]);
  return {...sample, questions_per_test: 100};
}

export function registerLearnerRoutes(app,auth) {
  const router=Router();
  router.use((req,res,next)=>{res.set('Cache-Control','no-store');next();});
  router.get('/learn/analytics',async(req,res)=>{
    const [stats, reading_prediction] = await Promise.all([
      learnerStats(req.user.id),
      readingPrediction(req.user.id)
    ]);
    res.json({...stats, reading_prediction});
  });
  router.get('/learn/badges',async(req,res)=>{
    const data=await transaction(async db=>{
      const badges=await query('SELECT code,name,description,requirement,icon,metric,target FROM badge_definitions ORDER BY id',[],db);
      const stats=await learnerStats(req.user.id,db);
      const eligible=badges.filter(b=>stats[b.metric]>=b.target).map(b=>b.code);
      if(eligible.length) {
        const fresh=await query(`INSERT INTO learner_badges(user_id,code) SELECT ?,unnest(?::text[])
          ON CONFLICT DO NOTHING RETURNING code`,[req.user.id,eligible],db);
        for(const row of fresh) {
          const badge=badges.find(b=>b.code===row.code);
          await query(`INSERT INTO notifications(user_id,type,title,body,data)
            VALUES(?,'ACHIEVEMENT',?,?,?::jsonb)`,[req.user.id,'Bạn đã nhận huy hiệu '+badge.name,badge.description,
            JSON.stringify({screen:'badges',badge_code:badge.code})],db);
        }
      }
      const earned=await query('SELECT code,unlocked_at FROM learner_badges WHERE user_id=?',[req.user.id],db);
      const [user]=await query('SELECT featured_badge FROM users WHERE id=?',[req.user.id],db);
      const rows=badges.map(({metric,...badge})=>{
        const unlocked=earned.find(row=>row.code===badge.code);
        return {...badge,progress:Math.min(stats[metric],badge.target),unlocked:Boolean(unlocked),unlocked_at:unlocked?.unlocked_at??null};
      }).sort((a,b)=>Number(b.unlocked)-Number(a.unlocked));
      return {rows,total:rows.length,unlocked_count:earned.length,featured_code:user.featured_badge};
    });
    res.json(data);
  });
  router.put('/auth/me/featured-badge',auth,async(req,res)=>{
    const {badge_code}=z.object({badge_code:z.string().max(40).nullable()}).strict().parse(req.body);
    if(badge_code!==null) {
      if(!(await query('SELECT 1 FROM badge_definitions WHERE code=?',[badge_code])).length) fail(404,'Huy hiệu không tồn tại');
      if(!(await query('SELECT code FROM learner_badges WHERE user_id=? AND code=?',[req.user.id,badge_code])).length) fail(409,'Huy hiệu chưa mở khóa');
    }
    await query('UPDATE users SET featured_badge=? WHERE id=?',[badge_code,req.user.id]);
    res.sendStatus(204);
  });
  router.get('/learn/mistakes',async(req,res)=>{
    const {page,limit}=pageQuery.parse(req.query);
    const [result]=await query(`${readingAnswers}, latest AS (
      SELECT DISTINCT ON(question_id) * FROM all_answers
      ORDER BY question_id,submitted_at DESC NULLS LAST,attempt_id DESC,kind DESC
    ),
    wrong AS (SELECT kind,attempt_id,content_title,submitted_at,question_id,position,selected_label,
      snapshot->>'question_text' AS question_text,snapshot->>'explanation' AS explanation,
      (snapshot->>'part_id')::integer AS part_id FROM latest WHERE NOT COALESCE(is_correct,FALSE)),
    page AS (SELECT * FROM wrong ORDER BY submitted_at DESC,question_id DESC LIMIT ? OFFSET ?)
    SELECT (SELECT COUNT(*) FROM wrong) AS total,COALESCE((SELECT jsonb_agg(page ORDER BY submitted_at DESC,question_id DESC) FROM page),'[]'::jsonb) AS rows`,
    [req.user.id,req.user.id,limit,(page-1)*limit]);
    res.json({...result,page,limit});
  });
  app.use('/api',router);
}
