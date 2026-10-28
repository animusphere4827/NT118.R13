import {z} from 'zod';
import {query} from './db.js';

export function registerDashboardRoutes(app) {
  app.get('/api/admin/dashboard',async(req,res)=>{
    const days = z.coerce.number().int().min(7).max(90).default(30).parse(req.query.days);
    const [countsRows,performanceRows,activity,parts,recent,daily,partPerformance,userStates,difficulties,securityRows] = await Promise.all([
      query(`SELECT (SELECT COUNT(*) FROM users) AS users,(SELECT COUNT(*) FROM vocabularies) AS vocabularies,
        (SELECT COUNT(*) FROM grammar_lessons) AS grammar_lessons,(SELECT COUNT(*) FROM questions) AS questions,
        (SELECT COUNT(*) FROM reading_passages) AS reading_passages,(SELECT COUNT(*) FROM practice_sets) AS practice_sets,
        (SELECT COUNT(*) FROM mock_tests) AS mock_tests,(SELECT COUNT(*) FROM flashcards) AS flashcards`),
      query(`SELECT COUNT(*) AS completed,COALESCE(ROUND(AVG(accuracy),1),0) AS accuracy
        FROM (SELECT accuracy FROM practice_attempts WHERE status='SUBMITTED'
        UNION ALL SELECT accuracy FROM mock_test_attempts WHERE status='SUBMITTED') a`),
      query(`SELECT to_char(day,'YYYY-MM-DD') AS day,COUNT(*) AS count
        FROM (SELECT (started_at AT TIME ZONE 'UTC')::date AS day FROM practice_attempts
        UNION ALL SELECT (started_at AT TIME ZONE 'UTC')::date FROM mock_test_attempts) a
        WHERE day>=(now() AT TIME ZONE 'UTC')::date-13 GROUP BY day ORDER BY day`),
      query(`SELECT p.id,p.name,p.test_question_count,COUNT(q.id) AS questions FROM toeic_parts p
        LEFT JOIN questions q ON q.part_id=p.id GROUP BY p.id ORDER BY p.id`),
      query(`SELECT a.id,a.action,a.resource,a.record_id,a.created_at,u.full_name FROM audit_logs a
        JOIN users u ON u.id=a.user_id ORDER BY a.id DESC LIMIT 8`),
      query(`WITH dates AS (
          SELECT generate_series((now() AT TIME ZONE 'UTC')::date-(?::int-1),(now() AT TIME ZONE 'UTC')::date,'1 day')::date AS day
        ), attempts AS (
          SELECT started_at,submitted_at,status,accuracy FROM practice_attempts
          UNION ALL SELECT started_at,submitted_at,status,accuracy FROM mock_test_attempts
        ), starts AS (
          SELECT (started_at AT TIME ZONE 'UTC')::date AS day,COUNT(*) AS started FROM attempts GROUP BY 1
        ), submissions AS (
          SELECT (submitted_at AT TIME ZONE 'UTC')::date AS day,COUNT(*) AS completed,ROUND(AVG(accuracy),2) AS accuracy
          FROM attempts WHERE status='SUBMITTED' GROUP BY 1
        ), registrations AS (
          SELECT (created_at AT TIME ZONE 'UTC')::date AS day,COUNT(*) AS users FROM users GROUP BY 1
        )
        SELECT to_char(d.day,'YYYY-MM-DD') AS day,COALESCE(s.started,0) AS started,
          COALESCE(a.completed,0) AS completed,a.accuracy,COALESCE(r.users,0) AS users
        FROM dates d LEFT JOIN starts s USING(day) LEFT JOIN submissions a USING(day)
        LEFT JOIN registrations r USING(day) ORDER BY d.day`,[days]),
      query(`WITH answers AS (
          SELECT a.snapshot,a.is_correct FROM practice_answers a JOIN practice_attempts t ON t.id=a.attempt_id WHERE t.status='SUBMITTED'
          UNION ALL SELECT a.snapshot,a.is_correct FROM mock_test_answers a JOIN mock_test_attempts t ON t.id=a.attempt_id WHERE t.status='SUBMITTED'
        )
        SELECT p.id AS part_id,COUNT(a.snapshot) AS total,
          COUNT(*) FILTER(WHERE a.is_correct) AS correct,
          COALESCE(ROUND(100.0*COUNT(*) FILTER(WHERE a.is_correct)/NULLIF(COUNT(a.snapshot),0),2),0) AS accuracy
        FROM toeic_parts p LEFT JOIN answers a ON (a.snapshot->>'part_id')::int=p.id GROUP BY p.id ORDER BY p.id`),
      query('SELECT role,status,COUNT(*) AS count FROM users GROUP BY role,status ORDER BY role,status'),
      query('SELECT part_id,difficulty,status,COUNT(*) AS count FROM questions GROUP BY part_id,difficulty,status ORDER BY part_id,difficulty,status'),
      query(`SELECT COUNT(*) FILTER(WHERE revoked_at IS NULL AND expires_at>now()) AS active_sessions,
        COUNT(*) FILTER(WHERE expires_at<=now()) AS expired_sessions,
        COUNT(*) FILTER(WHERE revoked_at IS NOT NULL) AS revoked_sessions FROM auth_sessions`)
    ]);
    res.set('Cache-Control','no-store').json({
      counts:countsRows[0],performance:performanceRows[0],activity,parts,recent,
      days,daily,part_performance:partPerformance,user_states:userStates,difficulties,security:securityRows[0]
    });
  });
}
