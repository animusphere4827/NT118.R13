import {Router} from 'express';
import {query,pool} from './db.js';
import {parseId} from './resources.js';

const pathSql = `
  WITH learner AS (SELECT ?::integer AS id), vocabulary AS (
    SELECT v.topic_id,COUNT(*) AS total
    FROM vocabularies v
    WHERE v.status='PUBLISHED' GROUP BY v.topic_id
  ), practice_done AS (
    SELECT DISTINCT practice_set_id FROM practice_attempts
    WHERE user_id=(SELECT id FROM learner) AND status='SUBMITTED' AND accuracy>=80
  ), mock_done AS (
    SELECT DISTINCT mock_test_id FROM mock_test_attempts
    WHERE user_id=(SELECT id FROM learner) AND status='SUBMITTED'
      AND EXISTS (SELECT 1 FROM mock_test_answers a WHERE a.attempt_id=mock_test_attempts.id AND a.selected_label IS NOT NULL)
  )
  SELECT l.id,l.title,l.subtitle,l.type,l.minutes,
    COALESCE(l.vocabulary_topic_id,l.grammar_lesson_id,l.practice_set_id,l.reading_group_id,l.mock_test_id) AS content_id,
    u.id AS unit_id,u.title AS unit_title,u.description AS unit_description,u.unlock_group,
    vp.completed_at AS vocabulary_completed_at,COALESCE(vp.passed_count,0) AS passed_words,
    COALESCE(jsonb_array_length(vp.words),v.total) AS total_words,
    CASE l.type
      WHEN 'VOCABULARY' THEN vp.completed_at IS NOT NULL
      WHEN 'GRAMMAR' THEN COALESCE(gp.progress_percent=100,FALSE)
      WHEN 'PRACTICE_PART5' THEN pd.practice_set_id IS NOT NULL
      WHEN 'READING' THEN rp.completed_at IS NOT NULL
      WHEN 'MOCK_TEST' THEN md.mock_test_id IS NOT NULL
    END AS completed
  FROM course_lessons l JOIN course_units u ON u.id=l.unit_id
  LEFT JOIN vocabulary_topics vt ON vt.id=l.vocabulary_topic_id AND vt.status='PUBLISHED'
  LEFT JOIN vocabulary v ON v.topic_id=vt.id
  LEFT JOIN course_vocabulary_progress vp ON vp.lesson_id=l.id AND vp.user_id=(SELECT id FROM learner)
  LEFT JOIN grammar_lessons gl ON gl.id=l.grammar_lesson_id AND gl.status='PUBLISHED'
  LEFT JOIN grammar_topics gt ON gt.id=gl.topic_id AND gt.status='PUBLISHED'
  LEFT JOIN user_grammar_progress gp ON gp.lesson_id=gl.id AND gp.user_id=(SELECT id FROM learner)
  LEFT JOIN practice_sets ps ON ps.id=l.practice_set_id AND ps.status='PUBLISHED' AND ps.part_id=5
  LEFT JOIN practice_done pd ON pd.practice_set_id=ps.id
  LEFT JOIN published_readings r ON r.id=l.reading_group_id
  LEFT JOIN user_reading_progress rp ON rp.group_id=r.id AND rp.user_id=(SELECT id FROM learner)
  LEFT JOIN mock_tests mt ON mt.id=l.mock_test_id AND mt.status='PUBLISHED'
  LEFT JOIN mock_done md ON md.mock_test_id=mt.id
  WHERE u.status='PUBLISHED' AND l.status='PUBLISHED' AND CASE l.type
    WHEN 'VOCABULARY' THEN v.total>0
    WHEN 'GRAMMAR' THEN gt.id IS NOT NULL
    WHEN 'READING' THEN r.id IS NOT NULL
    WHEN 'PRACTICE_PART5' THEN ps.id IS NOT NULL
      AND EXISTS (SELECT 1 FROM practice_set_questions x WHERE x.practice_set_id=ps.id)
      AND NOT EXISTS (SELECT 1 FROM practice_set_questions x JOIN questions q ON q.id=x.question_id
        WHERE x.practice_set_id=ps.id AND (q.status<>'PUBLISHED' OR q.part_id<>5))
    WHEN 'MOCK_TEST' THEN mt.id IS NOT NULL
      AND EXISTS (SELECT 1 FROM mock_test_questions x WHERE x.mock_test_id=mt.id)
      AND NOT EXISTS (SELECT 1 FROM mock_test_questions x JOIN questions q ON q.id=x.question_id
        WHERE x.mock_test_id=mt.id AND q.status<>'PUBLISHED')
  END
  ORDER BY u.position,l.position,l.id`;

function contentUrl(lesson) {
  const id = lesson.content_id;
  switch (lesson.type) {
    case 'VOCABULARY': return '/api/learn/path/lessons/' + lesson.id + '/vocabulary';
    case 'GRAMMAR': return '/api/learn/catalog/grammar_lessons/' + id;
    case 'PRACTICE_PART5': return '/api/learn/catalog/practice_sets/' + id;
    case 'READING': return '/api/learn/readings/' + id;
    case 'MOCK_TEST': return '/api/learn/catalog/mock_tests/' + id;
  }
}

export async function loadPath(userId,db=pool) {
  const rows = await query(pathSql,[userId],db);
  const units = new Map();
  const activeGroups = new Set();
  const groupPositions = new Map();
  let current = null;
  let currentPosition = Infinity;
  let completed = 0;
  for (const row of rows) {
    if (!units.has(row.unit_id)) {
      units.set(row.unit_id,{
        id:row.unit_id,title:row.unit_title,description:row.unit_description,
        unlock_group:row.unlock_group,lessons:[]
      });
    }
    const lesson = {
      id:row.id,title:row.title,subtitle:row.subtitle,type:row.type,
      content_id:row.content_id,minutes:row.minutes,completed:row.completed,
      status:row.completed ? 'DONE' : !activeGroups.has(row.unlock_group) ? 'IN_PROGRESS' : 'LOCKED'
    };
    if (row.type==='VOCABULARY') lesson.start_url='/api/learn/path/lessons/'+row.id+'/vocabulary/start';
    if (row.type==='VOCABULARY') lesson.progress={
      passed_words:row.passed_words,total_words:row.total_words,completed_at:row.vocabulary_completed_at,
      completion_rule:'STUDY_AND_CHECK',batch_size:10,required_percent:80
    };
    if (row.type==='PRACTICE_PART5') lesson.completion_rule='ACCURACY_80';
    if (row.type==='MOCK_TEST') lesson.completion_rule='SUBMITTED_WITH_ANSWERS';
    const position=groupPositions.get(row.unlock_group)??0;
    groupPositions.set(row.unlock_group,position+1);
    lesson.content_url = contentUrl(lesson);
    if (row.completed) completed++;
    else if (lesson.status==='IN_PROGRESS') {
      activeGroups.add(row.unlock_group);
      if (position<currentPosition) {current=lesson.id;currentPosition=position;}
    }
    units.get(row.unit_id).lessons.push(lesson);
  }
  return {units:[...units.values()],completed,total:rows.length,current_lesson_id:current};
}

export function registerCourseRoutes(app) {
  const router = Router();
  router.get('/path',async (req,res) => {
    res.set('Cache-Control','no-store').json(await loadPath(req.user.id));
  });
  router.get('/path/lessons/:id',async (req,res) => {
    const id = parseId(req.params.id);
    const path = await loadPath(req.user.id);
    const lesson = path.units.flatMap(unit => unit.lessons).find(item => item.id === id);
    res.set('Cache-Control','no-store');
    if (!lesson) return res.status(404).json({error:'Không tìm thấy bài học'});
    if (lesson.status === 'LOCKED') return res.status(409).json({error:'Bài học chưa mở khóa'});
    res.json(lesson);
  });
  app.use('/api/learn',router);
}
