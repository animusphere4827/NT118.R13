# Bảng hướng dẫn trường API (NT118 TOEIC Studio)

Đi kèm Postman collection **NT118 TOEIC Studio API** và environment **NT118 VPS** (workspace "AnimuspherE's Workspace"). Lấy từ code `server/backend` ngày 08/11/2026.

## Quy ước chung

| Mục | Giá trị |
|---|---|
| Base URL | `https://toeic4us.duckdns.org` (VPS 45.32.123.221, Caddy cấp HTTPS). App Android dùng `https://toeic4us.duckdns.org/api/` |
| Tài khoản admin | `ADMIN_EMAIL`/`ADMIN_PASSWORD` trong `/etc/nt118/server.env` trên VPS; không ghi mật khẩu vào repo |
| Xác thực | Header `Authorization: Bearer <access_token>`. Access token sống 15 phút, refresh token 30 ngày |
| Kiểu body | JSON, kiểm tra **strict**: gửi thừa trường bị 422. Ngoại lệ: `register` và `login` bỏ qua trường thừa |
| Phân trang | `page` (mặc định 1, tối đa 100000), `limit` (mặc định 20, 1-100). Kết quả dạng `{rows, total, page, limit}` |
| ID | Số nguyên dương, tối đa 2147483647. Riêng bài kiểm tra từ vựng dùng UUID |
| URL ảnh (`*_url`) | URL `http`/`https` tối đa 2048 ký tự; `''` hoặc `null` được hiểu là không có ảnh |
| Lỗi | `{error, details?, error_code?}`. 400 sai yêu cầu, 401 chưa đăng nhập/sai token, 403 không đủ quyền, 404 không thấy, 405 bảng chỉ đọc, 409 xung đột trạng thái, 413 quá lớn, 422 sai dữ liệu, 428 thiếu If-Match, 429 quá nhiều lần |
| Giới hạn tần suất | 300 request/phút cho `/api`; đăng ký/đăng nhập/Google 30 lần/15 phút; refresh 60 lần/15 phút; quên mật khẩu 20 lần/15 phút; tải avatar 20 lần/15 phút |
| Admin | Mọi `/api/admin/*` cần user role `ADMIN` (không phải thì 403) |

Ký hiệu cột "Bắt buộc": **Có** = phải gửi; **Không** = có mặc định hoặc tùy chọn.

---

## 0. Hệ thống

| API | Ghi chú |
|---|---|
| `GET /api/health` | Không cần token. Trả `{status:"ok", database:"postgresql"}` |

## 1. Xác thực và hồ sơ

### POST /api/auth/register (không cần token)
| Trường | Kiểu | Bắt buộc | Ràng buộc / ý nghĩa |
|---|---|---|---|
| email | string | Có | Email hợp lệ, <=254 ký tự, tự chuyển chữ thường |
| password | string | Có | 12-128 ký tự |
| full_name | string | Có | 1-150 ký tự |

Trả `201 {id}`.

### POST /api/auth/login (không cần token)
| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| email | string | Có | Như trên |
| password | string | Có | 12-128 ký tự |

Trả `{token, access_token, refresh_token, token_type:"Bearer", expires_in:900, refresh_expires_in, user}`. `user` gồm `id, email, full_name, avatar_url, role (ADMIN/STUDENT), status (ACTIVE/LOCKED/DISABLED), target_score`.

### POST /api/auth/refresh và POST /api/auth/logout
| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| refresh_token | string | Refresh: Có (hoặc cookie). Logout: Không | Đúng 64 ký tự `[A-Za-z0-9_-]` |

Refresh trả cặp token mới, token cũ bị thu hồi. Logout trả 204.

### POST /api/auth/firebase (không cần token)
| Trường | Kiểu | Bắt buộc | Ràng buộc / ý nghĩa |
|---|---|---|---|
| id_token | string | Có | Firebase ID token của tài khoản Google, 20-8192 ký tự |
| existing_password | string | Không | Mật khẩu tài khoản cũ cùng email, 12-128 ký tự. Thiếu thì 409 `error_code: GOOGLE_LINK_PASSWORD_REQUIRED`, sai thì 401 `GOOGLE_LINK_PASSWORD_INVALID` |

Tài khoản mới trả 201 kèm `is_new_user:true`. Admin không được đăng nhập Google (409). Server chưa cấu hình Firebase trả 503.

### Quên mật khẩu (không cần token)
| API | Trường | Kiểu | Ràng buộc |
|---|---|---|---|
| `POST /api/auth/password/forgot` | email | string | Email hợp lệ. Luôn trả `202 {expires_in:300, resend_after:60}` |
| `POST /api/auth/password/verify` | email | string | Như trên |
| | otp | string | Đúng 6 chữ số. Sai 5 lần trả 429. Đúng trả `{reset_token, expires_in:300}` |
| `POST /api/auth/password/reset` | reset_token | string | 64 ký tự hex từ bước verify |
| | password | string | 12-128 ký tự. Thành công trả 204 và thu hồi mọi phiên |

### GET /api/auth/me
Trả `user` như ở login.

### PUT /api/auth/me
| Trường | Kiểu | Bắt buộc | Ràng buộc / ý nghĩa |
|---|---|---|---|
| full_name | string | Có | 1-150 ký tự |
| target_score | integer | Có | Mục tiêu Reading 5-495 |
| avatar_url | string/null | Không | Không gửi thì giữ ảnh cũ; `null`/`''` thì xóa ảnh. URL `/api/media/avatars/...` phải là ảnh của chính tài khoản |

### POST /api/auth/me/avatar (multipart/form-data)
| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| avatar | file | Có | JPEG/PNG/WebP một khung hình, tối đa 16 triệu pixel. Server cắt về 512x512 WebP |

Trả `{avatar_url, user}`. Ảnh xem công khai qua `GET /api/media/avatars/<uuid>.webp`.

### PUT /api/auth/me/featured-badge
| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| badge_code | string/null | Có | Mã huy hiệu <=40 ký tự đã mở khóa; `null` để bỏ chọn. Không tồn tại 404, chưa mở khóa 409 |

## 2. Từ điển và lịch sử tra cứu

| API | Tham số | Kiểu | Bắt buộc | Ràng buộc / ý nghĩa |
|---|---|---|---|---|
| `GET /api/learn/dictionary` | q (query) | string | Có | 1-150 ký tự, tìm theo tiền tố, tối đa 20 kết quả |
| `GET /api/learn/lookups` | page (query) | integer | Không | 20 dòng/trang |
| `POST /api/learn/lookups` | keyword | string | Có | 1-150 ký tự. Tra lại cùng từ thì tăng `lookup_count` |
| | source | enum | Không | `DICTIONARY` (mặc định), `READING`, `FLASHCARD` |
| `DELETE /api/learn/lookups/:id` | id (path) | integer | Có | Xóa 1 mục |
| `DELETE /api/learn/lookups` | | | | Xóa toàn bộ lịch sử |

Kết quả từ điển: `id, word, ipa, part_of_speech, meaning_vi, image_url, topic`. Lịch sử: `id, keyword, source, lookup_count, first_looked_up_at, last_looked_up_at, vocabulary_id, ipa, meaning_vi`.

## 3. Flashcard

### Bộ thẻ: POST /api/learn/decks, PUT /api/learn/decks/:id
| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| name | string | Có | 1-150 ký tự |
| description | string | Không | <=2000, mặc định `''` |
| cover_image_url | string/null | Không | URL ảnh |

`GET /api/learn/decks` trả thêm `card_count, due_count, pinned_count`. `DELETE /api/learn/decks/:id` xóa cả thẻ bên trong.

### Thẻ: POST /api/learn/decks/:id/cards
| Trường | Kiểu | Bắt buộc | Ràng buộc / ý nghĩa |
|---|---|---|---|
| vocabulary_id | integer/null | Không* | Lấy nội dung từ từ vựng có sẵn |
| front_text | string | Không* | 1-500. *Bắt buộc khi không có vocabulary_id |
| back_text | string | Không* | 1-5000. *Bắt buộc khi không có vocabulary_id |
| ipa | string | Không | <=150 |
| example_sentence | string | Không | <=2000 |
| note | string | Không | <=2000 |
| image_url | string/null | Không | URL ảnh |
| is_pinned | boolean | Không | Mặc định false |

`PUT /api/learn/cards/:id`: cùng các trường trừ `vocabulary_id`; `front_text`, `back_text` bắt buộc.

### GET /api/learn/decks/:id/cards
| Query | Kiểu | Ý nghĩa |
|---|---|---|
| page | integer | 20 thẻ/trang |
| due | `true`/`false` | Chỉ thẻ đến hạn ôn |
| pinned | `true`/`false` | Chỉ thẻ ghim |

### POST /api/learn/cards/:id/review
| Trường | Kiểu | Bắt buộc | Ràng buộc / ý nghĩa |
|---|---|---|---|
| quality | integer | Có | 0-5 theo SM-2; dưới 3 là quên (đặt lại `repetitions`) |

Trả `{id, ease_factor, interval_days, repetitions, due_at, last_reviewed_at}`.

## 4. Thông báo

| API | Tham số | Ý nghĩa |
|---|---|---|
| `GET /api/learn/notifications` | page, unread=`true`/`false` | Trả `{total, unread_count, rows, page, limit:20}` |
| `PATCH /api/learn/notifications/:id/read` | | Không body, 204 |
| `POST /api/learn/notifications/read-all` | | Trả `{updated}` |
| `DELETE /api/learn/notifications/:id` | | 204 |

Trường thông báo: `id, type (SYSTEM, STUDY_REMINDER, NEW_CONTENT, RESULT, ACHIEVEMENT), title, body, data (object, ví dụ {screen, attempt_id}), is_read, read_at, created_at`.

## 5. Huy hiệu, thống kê, câu sai

| API | Tham số | Kết quả |
|---|---|---|
| `GET /api/learn/badges` | | `{rows:[{code, name, description, requirement, icon, target, progress, unlocked, unlocked_at}], total, unlocked_count, featured_code}`. Gọi API này cũng trao huy hiệu mới đủ điều kiện |
| `GET /api/learn/analytics` | | Chỉ số học tập kèm `reading_prediction` |
| `GET /api/learn/mistakes` | page, limit | Câu sai ở lần làm gần nhất: `kind, attempt_id, content_title, submitted_at, question_id, position, selected_label, question_text, explanation, part_id` |
| `GET /api/learn/progress` | | `{vocabulary:[...], grammar:[...]}` |

## 6. Danh mục nội dung

| API | Tham số | Kiểu | Ràng buộc / ý nghĩa |
|---|---|---|---|
| `GET /api/learn/catalog/:resource` | resource (path) | enum | `vocabulary_topics, vocabularies, grammar_topics, grammar_lessons, toeic_parts, practice_sets, mock_tests`. Chỉ trả nội dung `PUBLISHED` |
| | page, limit | integer | Phân trang |
| | topic_id | integer | Lọc `vocabularies`, `grammar_lessons` |
| | part_id | integer | Lọc `practice_sets` |
| `GET /api/learn/catalog/:resource/:id` | | | Một bản ghi |
| `PUT /api/learn/vocabulary/:id/progress` | status | enum | Bắt buộc: `NEW, LEARNING, MASTERED` |
| | is_favorite | boolean | Bắt buộc |
| `GET /api/learn/grammar/:id/exercises` | page | integer | 20 câu/trang, đã ẩn đáp án đúng |

## 7. Lộ trình và học từ theo bài

| API | Body / tham số | Ràng buộc / ý nghĩa |
|---|---|---|
| `GET /api/learn/path` | | `{units:[{id, title, description, lessons:[{id, title, subtitle, type, content_id, minutes, status}]}], completed, total, current_lesson_id}`. `status`: DONE, IN_PROGRESS, LOCKED |
| `GET /api/learn/path/lessons/:id` | | Bài khóa trả 409 |
| `POST /api/learn/path/lessons/:id/vocabulary/start` | `{}` | Chỉ bài từ vựng đã mở. Học theo lượt 10 từ |
| `GET /api/learn/path/lessons/:id/vocabulary` | | Trạng thái học |
| `GET /api/learn/path/lessons/:id/vocabulary/review` | page | Từ đã qua kiểm tra, 10 từ/trang |
| `PUT /api/learn/path/lessons/:id/vocabulary/study` | vocabulary_ids: integer[] | Bắt buộc, 1-10 id không trùng, thuộc lượt hiện tại |
| `POST /api/learn/path/lessons/:id/vocabulary/checks` | `{}` | Tạo bài kiểm tra lượt (cần học hết lượt trước) |
| `GET /api/learn/vocabulary-checks/:id` | id: UUID | Xem bài kiểm tra |
| `POST /api/learn/vocabulary-checks/:id/submit` | answers: array | Bắt buộc, 1-10 phần tử, phải đủ đúng danh sách từ của bài |
| | answers[].vocabulary_id | integer |
| | answers[].answer | string <=300: từ tiếng Anh ứng với nghĩa tiếng Việt |

Kết quả nộp: `check_id, lesson_id, total, correct_count, required_correct, passed (đúng >=80%), lesson_completed, passed_words, total_words, review[{vocabulary_id, prompt, answer, correct_answer, is_correct}]`.

## 8. Ngữ pháp

| API | Trường | Kiểu | Ràng buộc |
|---|---|---|---|
| `GET /api/learn/grammar/:id/workspace` | | | Trả `{lesson, progress:{total, answered, correct, passed, required_percent:80}, questions}` |
| `POST /api/learn/grammar/:id/check` | question_id | integer | Bắt buộc, thuộc bài |
| | label | enum | Bắt buộc: A, B, C, D. Trả `{is_correct, correct_label, explanation, progress}` |
| `PUT /api/learn/grammar/:id/progress` | progress_percent | integer | Bắt buộc 0-100, chỉ tăng. Đặt 100 khi chưa đạt 80% trả 409 |

## 9. Reading

| API | Tham số | Kiểu | Ràng buộc / ý nghĩa |
|---|---|---|---|
| `GET /api/learn/readings` | page, limit | integer | Phân trang |
| | part_id | integer | 6 hoặc 7 |
| | type | enum | EMAIL, LETTER, MEMO, NOTICE, ADVERTISEMENT, ARTICLE, FORM, SCHEDULE, INVOICE, CHAT, WEB_PAGE, REVIEW, OTHER |
| | search | string | <=150, tìm theo tiêu đề bài hoặc đoạn |
| `GET /api/learn/readings/:id` | translation | `true`/`false` | `true` thì kèm `translation_vi` |
| `PUT /api/learn/readings/:id/progress` | completed | `true` | Bắt buộc, chỉ nhận true. Trả `{group_id, completed_at}` |
| `POST /api/learn/readings/:id/lookups` | keyword | string | Bắt buộc, đúng 1 từ có trong bài. Trả `{keyword, rows, lookup}` |

## 10. Quiz (điền từ, nối cặp, sắp xếp)

| API | Tham số | Ràng buộc / ý nghĩa |
|---|---|---|
| `GET /api/learn/quizzes` | page, limit, topic_id, lesson_id, type (`FILL_BLANK, MATCH, ORDER`) | Bộ quiz đã xuất bản |
| `GET /api/learn/quizzes/:id` | | `id, title, description, time_limit, vocabulary_topic_id, grammar_lesson_id, question_count, types` |
| `POST /api/learn/quizzes/:id/attempts` | không body | Trả `201 {id, started_at, deadline_at, server_time}` |
| `GET /api/learn/quiz/attempts` | page, limit, content_id, status (`IN_PROGRESS, SUBMITTED`) | Lịch sử |
| `GET /api/learn/quiz/attempts/:id` | | `{attempt, server_time, questions}`. Hết giờ thì server tự chốt |
| `POST /api/learn/quiz/attempts/:id/answers` | question_id, answer | Chấm từng câu, mỗi câu chỉ chấm 1 lần |
| `POST /api/learn/quiz/attempts/:id/submit` | answers: [{question_id, answer}] tối đa 50 | Nộp bài; nộp lại trả kết quả cũ |

Dạng `answer` theo loại câu:

| Loại | Dạng answer | Ràng buộc |
|---|---|---|
| FILL_BLANK | `"has developed"` | Chuỗi 1-500, không phân biệt hoa thường |
| MATCH | `{"pairs":[{"left_id":"l1","right_id":"r2"}, ...]}` | 2-20 cặp, dùng đủ và không trùng id của `payload.left`/`payload.right` |
| ORDER | `{"order":["t3","t1","t2"]}` | 2-20 id, đủ và không trùng id của `payload.tokens` |

Phản hồi chấm câu: `{question_id, answer, is_correct, correct_answer, explanation}`.

## 11-12. Luyện tập Part 5-7 và Thi thử

`:kind` là `practice` (bộ luyện, id từ catalog `practice_sets`) hoặc `mock` (đề thi, id từ catalog `mock_tests`).

| API | Trường | Kiểu | Ràng buộc / ý nghĩa |
|---|---|---|---|
| `POST /api/learn/:kind/:id/attempts` | không body | | Trả `201 {id, started_at, deadline_at, answer_version, server_time}`. Đề FULL phải đủ 100 câu (30/16/54) và 75 phút |
| `GET /api/learn/:kind/attempts` | page, limit, content_id, status | | Lịch sử |
| `GET /api/learn/:kind/attempts/:id` | | | `{attempt, server_time, questions}`; sau khi nộp có đáp án đúng, giải thích, bản dịch |
| `PATCH /api/learn/:kind/attempts/:id/answers` | answer_version | integer | Bắt buộc, phải bằng phiên bản hiện tại (lệch trả 409) |
| | answers | array | Bắt buộc, 1-200 phần tử, không trùng question_id |
| | answers[].question_id | integer | Thuộc bài làm |
| | answers[].label | A-D/null | Không bắt buộc; `null` là bỏ chọn |
| | answers[].marked_for_review | boolean | Không bắt buộc; cần ít nhất label hoặc cờ này |
| `POST /api/learn/:kind/attempts/:id/submit` | answer_version | integer | Không bắt buộc; có thì phải khớp |
| | answers | [{question_id, label}] | Tối đa 200, mặc định [] |

Kết quả nộp: `attempt_id, kind, correct_count, total_questions, accuracy (%), by_part[{part_id, total_questions, correct_count, accuracy}], expired, score_note`. Đề thi thêm `part5_correct, part6_correct, part7_correct` và tạo thông báo RESULT.

## 13. Admin

### Tổng quan
| API | Tham số | Ràng buộc / ý nghĩa |
|---|---|---|
| `GET /api/admin/meta` | | Danh sách bảng, nhãn, trường và các bảng chỉ đọc |
| `GET /api/admin/dashboard` | days | 7-90, mặc định 30, ngày theo giờ Việt Nam |
| `POST /api/admin/notifications/broadcast` | type | `SYSTEM` (mặc định), `STUDY_REMINDER`, `NEW_CONTENT` |
| | title | Bắt buộc 1-200 |
| | body | <=5000 |
| | data | object tùy ý |
| | user_ids | Tùy chọn, <=1000 id. Không gửi thì gửi mọi STUDENT đang hoạt động. Trả `201 {sent}` |
| `GET /api/admin/badges` | | Kèm `edit_version, earned_count` |
| `PUT /api/admin/badges/:id` | Header If-Match | Bắt buộc (thiếu 428, lệch 409) |
| | name | 1-80 |
| | description | 1-500 |
| `GET /api/admin/badges/:id/recipients` | page, limit | `user_id, full_name, unlocked_at, featured` |

### CRUD chung: /api/admin/:resource
- `GET /api/admin/:resource`: `page, limit, search (<=200), status` và lọc theo trường quan hệ (ví dụ `topic_id=1`).
- `GET /api/admin/:resource/:id`: trả bản ghi kèm `edit_version`.
- `POST` tạo (trả 201 `{id}`), `PUT /:id` sửa, `DELETE /:id` xóa. PUT/DELETE nên gửi `If-Match: <edit_version>` để chống ghi đè.
- Bảng chỉ đọc (POST/PUT/DELETE trả 405): `toeic_parts, practice_attempts, practice_answers, mock_test_attempts, mock_test_answers, user_vocabulary_progress, course_vocabulary_progress, user_grammar_progress, lookup_history, audit_logs`. `users` không xóa được, chỉ khóa.

Trường từng bảng ghi được (`status` nội dung: DRAFT mặc định / PUBLISHED; `difficulty`: EASY, MEDIUM mặc định, HARD):

| Bảng | Trường (bắt buộc in đậm) |
|---|---|
| vocabulary_topics | **name** (<=150), description, image_url, status |
| vocabularies | **topic_id**, **word** (<=150), ipa (<=150), part_of_speech (<=40), **meaning_vi**, definition_en, example_sentence, example_vi, synonyms, antonyms, audio_url, image_url, status |
| grammar_topics | **name** (<=150), description, status |
| grammar_lessons | **topic_id**, **title** (<=200), **content**, difficulty, status |
| practice_sets | **part_id** (5-7), **title**, description, time_limit (60-14400 giây, mặc định 600), status |
| question_groups | **part_id** (6-7), **title**, instructions, is_practical_reading (boolean, đưa vào thư viện đọc) |
| reading_passages | **group_id**, position (1-3), passage_type (enum như Reading), title, **content**, translation_vi, image_url |
| questions | **part_id** (5-7), group_id (Part 6-7), lesson_id, question_type, **question_text**, **explanation**, difficulty, status, **options** (đúng 4 phần tử `{label A-D, option_text, is_correct}`, đúng 1 đáp án đúng) |
| mock_tests | **title**, description, test_format (MINI mặc định / FULL), time_limit (mặc định 4500), status |
| practice_set_questions | **practice_set_id**, **question_id**, **position** |
| mock_test_questions | **mock_test_id**, **question_id**, **position** |
| users | **email**, **full_name**, avatar_url, **role** (ADMIN/STUDENT), **status** (ACTIVE/LOCKED/DISABLED), **target_score** (5-495), password (12-128, bắt buộc khi tạo) |
| flashcard_decks | **user_id**, **name**, description, cover_image_url |
| flashcards | **deck_id**, vocabulary_id, **front_text**, **back_text**, ipa, example_sentence, note, image_url, is_pinned |
| notifications | **user_id**, type (SYSTEM, STUDY_REMINDER, NEW_CONTENT, RESULT, ACHIEVEMENT), **title**, body |

`question_type`: WORD_FORM, VERB_TENSE, PREPOSITION, CONJUNCTION, PRONOUN, VOCABULARY, COLLOCATION, SENTENCE_INSERTION, MAIN_IDEA, DETAIL, INFERENCE, VOCAB_IN_CONTEXT, NOT_TRUE, PURPOSE, OTHER.

### Soạn bài đọc: /api/admin/content/readings
`GET /:id` (kèm `edit_version`), `POST /`, `PUT /:id`, `DELETE /:id`. PUT và DELETE bắt buộc `If-Match`.

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| part_id | integer | Có | 6 hoặc 7 |
| title | string | Có | 1-200 |
| instructions | string | Không | <=60000 |
| status | enum | Có | DRAFT / PUBLISHED (PUBLISHED cần ít nhất 1 câu) |
| is_practical_reading | boolean | Không | Mặc định false |
| passages | array | Có | 1-3 đoạn (Part 6 chỉ 1). Mỗi đoạn: id (để giữ đoạn cũ), passage_type, title, **content**, translation_vi, image_url |
| questions | array | Có | Tối đa 50. Mỗi câu: id, question_type, **question_text**, **explanation**, difficulty, **options** (4 đáp án, 1 đúng) |

Chỉ xóa được bài khi mọi câu đang là DRAFT.

### Soạn bộ luyện / đề thi: /api/admin/content/exams/:kind
`:kind` = `practice` hoặc `mock`. `GET /:kind/:id`, `POST /:kind`, `PUT /:kind/:id` (bắt buộc `If-Match`).

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| title | string | Có | 1-200 |
| description | string | Không | <=60000 |
| time_limit | integer | Có | 60-14400 giây |
| status | enum | Có | DRAFT / PUBLISHED |
| part_id | integer | practice: Có; mock: không gửi | 5-7, mọi câu phải cùng Part |
| test_format | enum | mock: Có; practice: không gửi | MINI / FULL (FULL: 100 câu 30/16/54, 4500 giây) |
| question_ids | integer[] | Có | Tối đa 200, không trùng |
