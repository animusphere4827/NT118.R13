-- Bài đọc tiếng Anh lấy từ RSS của báo lớn (chỉ tiêu đề + đoạn tóm tắt mà RSS công khai, kèm link bài gốc).
CREATE TABLE news_articles (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  source VARCHAR(60) NOT NULL,
  category VARCHAR(20) NOT NULL CHECK (category IN ('BUSINESS','TECHNOLOGY','WORLD','SCIENCE','ENVIRONMENT','TRAVEL')),
  guid VARCHAR(1000) NOT NULL UNIQUE,
  title VARCHAR(400) NOT NULL,
  summary TEXT NOT NULL,
  url VARCHAR(1000) NOT NULL,
  image_url VARCHAR(1000),
  image_credit VARCHAR(300),
  word_count INTEGER NOT NULL CHECK (word_count >= 0),
  published_at TIMESTAMPTZ NOT NULL,
  fetched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_news_articles_recent ON news_articles(published_at DESC, id DESC);
CREATE INDEX idx_news_articles_category ON news_articles(category, published_at DESC, id DESC);
