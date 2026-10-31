CREATE TABLE learner_badges (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code VARCHAR(40) NOT NULL,
  unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,code)
);
ALTER TABLE users ADD COLUMN featured_badge VARCHAR(40);
ALTER TABLE users ADD CONSTRAINT users_featured_badge_fk FOREIGN KEY(id,featured_badge)
  REFERENCES learner_badges(user_id,code);

CREATE TABLE grammar_answer_progress (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  question_id INTEGER NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
  lesson_id INTEGER NOT NULL REFERENCES grammar_lessons(id) ON DELETE CASCADE,
  is_correct BOOLEAN NOT NULL,
  checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,question_id)
);
CREATE INDEX idx_grammar_answer_lesson ON grammar_answer_progress(user_id,lesson_id);

CREATE TABLE flashcard_review_days (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  study_day DATE NOT NULL,
  last_reviewed_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(user_id,study_day)
);
INSERT INTO flashcard_review_days(user_id,study_day,last_reviewed_at)
SELECT d.user_id,(c.last_reviewed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,MAX(c.last_reviewed_at)
FROM flashcards c JOIN flashcard_decks d ON d.id=c.deck_id WHERE c.last_reviewed_at IS NOT NULL GROUP BY 1,2;

CREATE FUNCTION record_flashcard_study_day() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO flashcard_review_days(user_id,study_day,last_reviewed_at)
  SELECT user_id,(NEW.last_reviewed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,NEW.last_reviewed_at
  FROM flashcard_decks WHERE id=NEW.deck_id
  ON CONFLICT(user_id,study_day) DO UPDATE SET last_reviewed_at=GREATEST(flashcard_review_days.last_reviewed_at,EXCLUDED.last_reviewed_at);
  RETURN NEW;
END;
$$;
CREATE TRIGGER flashcard_study_day AFTER UPDATE OF last_reviewed_at ON flashcards
FOR EACH ROW WHEN (NEW.last_reviewed_at IS NOT NULL AND NEW.last_reviewed_at IS DISTINCT FROM OLD.last_reviewed_at)
EXECUTE FUNCTION record_flashcard_study_day();
