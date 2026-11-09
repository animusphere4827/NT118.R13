-- Huy hiệu "Giữ nhịp học": nâng điều kiện từ 5 lên 7 ngày học liên tiếp và đổi mã STREAK_5 -> STREAK_7.
-- Người đã nhận huy hiệu theo điều kiện cũ vẫn giữ huy hiệu (đổi sang mã mới).
-- users.featured_badge có khóa ngoại tới learner_badges(user_id,code) không có ON UPDATE CASCADE,
-- nên tạm bỏ gắn huy hiệu nổi bật rồi gắn lại sau khi đổi mã.

CREATE TEMP TABLE streak_featured AS
  SELECT id FROM users WHERE featured_badge='STREAK_5';

UPDATE users SET featured_badge=NULL WHERE featured_badge='STREAK_5';

UPDATE learner_badges SET code='STREAK_7' WHERE code='STREAK_5';

UPDATE badge_definitions
SET code='STREAK_7',
    description='Có hoạt động học trong bảy ngày liên tiếp.',
    requirement='7 ngày liên tiếp',
    target=7
WHERE code='STREAK_5';

UPDATE users SET featured_badge='STREAK_7' WHERE id IN (SELECT id FROM streak_featured);

DROP TABLE streak_featured;
