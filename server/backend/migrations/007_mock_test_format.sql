ALTER TABLE mock_tests ADD COLUMN test_format VARCHAR(4) NOT NULL DEFAULT 'MINI'
  CHECK (test_format IN ('MINI','FULL'));

UPDATE mock_tests t SET test_format='FULL'
WHERE time_limit=4500 AND EXISTS (
  SELECT 1 FROM mock_test_questions m JOIN questions q ON q.id=m.question_id
  WHERE m.mock_test_id=t.id GROUP BY m.mock_test_id
  HAVING COUNT(*)=100 AND COUNT(*) FILTER (WHERE q.part_id=5)=30
    AND COUNT(*) FILTER (WHERE q.part_id=6)=16 AND COUNT(*) FILTER (WHERE q.part_id=7)=54
);
