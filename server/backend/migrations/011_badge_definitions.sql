CREATE TABLE badge_definitions (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500) NOT NULL,
  requirement VARCHAR(100) NOT NULL,
  icon VARCHAR(30) NOT NULL,
  metric VARCHAR(40) NOT NULL CHECK (metric IN (
    'vocabulary_completed','readings_completed','mocks_completed','quizzes_completed','longest_streak'
  )),
  target INTEGER NOT NULL CHECK (target > 0)
);

INSERT INTO badge_definitions(code,name,description,requirement,icon,metric,target) VALUES
  ('FIRST_VOCABULARY','Bước đầu vững chắc','Hoàn thành bài từ vựng bằng kiểm tra nhớ từ.','1 bài từ vựng','book_open','vocabulary_completed',1),
  ('FIVE_READINGS','Người đọc chăm chỉ','Hoàn thành năm bài đọc thực tế.','5 bài đọc','mail','readings_completed',5),
  ('FIRST_MOCK','Thử sức Reading','Nộp bài thi thử và xem kết quả.','1 bài thi thử','target','mocks_completed',1),
  ('TEN_QUIZZES','Luyện tập bền bỉ','Hoàn thành mười lượt quiz.','10 lượt quiz','award','quizzes_completed',10),
  ('STREAK_5','Giữ nhịp học','Có hoạt động học trong năm ngày liên tiếp.','5 ngày liên tiếp','flame','longest_streak',5);

CREATE INDEX idx_learner_badges_by_code ON learner_badges(code,unlocked_at DESC,user_id DESC);
