ALTER TABLE question_groups ADD COLUMN is_practical_reading BOOLEAN NOT NULL DEFAULT FALSE;

CREATE OR REPLACE VIEW published_readings AS
SELECT g.id,g.part_id,g.title,g.instructions,
  COUNT(p.id)::integer AS passage_count,
  array_agg(DISTINCT p.passage_type ORDER BY p.passage_type) AS passage_types
FROM question_groups g
JOIN reading_passages p ON p.group_id=g.id
WHERE (g.is_practical_reading AND EXISTS (
  SELECT 1 FROM questions q WHERE q.group_id=g.id AND q.part_id=g.part_id AND q.status='PUBLISHED'
)) OR EXISTS (
  SELECT 1 FROM questions q
  JOIN practice_set_questions link ON link.question_id=q.id
  JOIN practice_sets s ON s.id=link.practice_set_id
  WHERE q.group_id=g.id AND q.status='PUBLISHED' AND s.status='PUBLISHED'
    AND q.part_id=g.part_id AND s.part_id=g.part_id
)
GROUP BY g.id;
