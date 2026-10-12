# REST API · TOEIC Studio (PostgreSQL, Vocabulary + TOEIC Reading)

Base: `http://localhost:3000/api` (production: domain triển khai + `/api`). JSON request/response. Lỗi có `{ "error": "...", "details": [] }` khi validation. Mã thường dùng: 401 cần đăng nhập, 403 sai quyền/origin, 404 không tồn tại, 409 xung đột hoặc đã nộp, 422 dữ liệu không hợp lệ, 429 quá số yêu cầu.

## Authentication

| Method | Path | Body / kết quả |
|---|---|---|
| POST | `/auth/register` | `{email,password,full_name}`; password 12–128 ký tự; luôn tạo STUDENT |
| POST | `/auth/login` | `{email,password}` → `{token,user}`; JWT hết hạn sau 2 giờ |
| POST | `/auth/firebase` | `{id_token}` (Firebase ID token sau khi đăng nhập Google) → `{token,user,is_new_user}`; 201 khi tạo tài khoản mới, 200 khi đăng nhập lại |
| GET | `/auth/me` | Người dùng hiện tại |
| POST | `/auth/logout` | Xóa cookie web; Android tự xóa token đã lưu |
| GET | `/health` | `{status:"ok",database:"postgresql"}` |

### Đăng ký / đăng nhập Google (Firebase)

1. Android dùng Firebase Authentication + Google Sign-In, sau khi đăng nhập gọi `FirebaseAuth.getInstance().getCurrentUser().getIdToken(true)`.
2. Gửi `POST /auth/firebase` với `{"id_token":"<Firebase ID token>"}`.
3. Server kiểm tra chữ ký RS256 bằng khóa công khai của Google, `aud` = `FIREBASE_PROJECT_ID`, `iss` = `https://securetoken.google.com/<project-id>`, hạn token, nhà cung cấp `google.com` và email đã xác minh. Không cần service account.
4. Lần đầu: tạo tài khoản STUDENT (không có mật khẩu, lấy tên và ảnh Google). Nếu email đã đăng ký bằng mật khẩu thì liên kết với Google, sau đó dùng được cả hai cách. Tài khoản admin không tự liên kết.
5. Nhận `token` (JWT của hệ thống) và dùng như đăng nhập thường.

Lỗi: 503 server chưa đặt `FIREBASE_PROJECT_ID`; 401 token sai/hết hạn, không phải Google, email chưa xác minh hoặc tài khoản bị khóa; 409 email đã gắn tài khoản Google khác hoặc là tài khoản admin.

Android gửi `Authorization: Bearer <token>`. Không gửi `Origin` nếu dùng native HTTP client. Web admin sử dụng cookie, cùng origin và header `X-Requested-With: TOEICAdmin` cho thao tác ghi. API không hỗ trợ web app cross-origin mặc định.

## Admin (role ADMIN)

`GET /admin/meta` trả cấu hình form và tài nguyên chỉ xem.

`GET /admin/dashboard` trả counts, performance, activity 14 ngày, parts, recent audit.

CRUD: `GET/POST /admin/:resource`, `GET/PUT/DELETE /admin/:resource/:id`. PUT gửi toàn bộ các trường ghi, không gửi `id`, `created_at`, `revision` hoặc `password_hash`. Trường không định nghĩa bị từ chối. `GET` danh sách hỗ trợ `page` (mặc định 1), `limit` (1–100, mặc định 20), `search` và trả `{rows,total,page,limit}`.

Các resource ghi: `vocabulary_topics`, `vocabularies`, `grammar_topics`, `grammar_lessons`, `practice_sets`, `question_groups`, `reading_passages`, `questions`, `practice_set_questions`, `mock_tests`, `mock_test_questions`, `users`. Users không xóa, hãy đổi status. Bản ghi được tham chiếu bởi lịch sử không xóa được.

Resource chỉ xem: `toeic_parts`, `practice_attempts`, `practice_answers`, `mock_test_attempts`, `mock_test_answers`, `user_vocabulary_progress`, `user_grammar_progress`, `flashcard_decks`, `flashcards`, `lookup_history`, `notifications`, `audit_logs`.

Chỉ có Part 5, 6, 7. Mọi câu hỏi có đúng 4 đáp án A–D. Part 5 không gắn nhóm. Part 6/7 cần `question_groups` (part_id 6 hoặc 7) và ít nhất một `reading_passages` trước khi xuất bản:

```json
{"group_id": 3, "position": 1, "passage_type": "EMAIL", "title": "Friday meeting",
 "content": "To: All staff ...", "translation_vi": "Gửi toàn thể nhân viên ...", "image_url": ""}
```

`passage_type`: EMAIL, LETTER, MEMO, NOTICE, ADVERTISEMENT, ARTICLE, FORM, SCHEDULE, INVOICE, CHAT, WEB_PAGE, REVIEW, OTHER. Part 6 chỉ 1 đoạn; Part 7 tối đa 3 đoạn (position 1–3). URL rỗng được lưu là `null`.

Gửi thông báo: `POST /admin/notifications/broadcast` với `{type:"SYSTEM|STUDY_REMINDER|NEW_CONTENT",title,body,data,user_ids?}`. Không có `user_ids` thì gửi cho mọi học viên ACTIVE. Trả `{sent}`.

Tạo question:

```json
{
  "part_id": 5,
  "group_id": null,
  "lesson_id": null,
  "question_type": "PREPOSITION",
  "question_text": "Please submit the report _____ Friday.",
  "explanation": "By Friday nghĩa là không muộn hơn thứ Sáu.",
  "difficulty": "EASY",
  "status": "PUBLISHED",
  "options": [
    {"label":"A","option_text":"by","is_correct":true},
    {"label":"B","option_text":"at","is_correct":false},
    {"label":"C","option_text":"in","is_correct":false},
    {"label":"D","option_text":"of","is_correct":false}
  ]
}
```

Tạo practice_set với DRAFT → thêm `practice_set_questions` có `{practice_set_id,question_id,position}` → sửa practice_set thành PUBLISHED. Đề thi làm tương tự với `mock_test_questions` và `mock_test_id`. Position là số nguyên dương, không trùng trong cùng bộ/đề. Câu phải thuộc đúng Part với bộ luyện.

## Learning (ADMIN hoặc STUDENT đang ACTIVE)

| Method | Path | Nội dung |
|---|---|---|
| GET | `/learn/catalog/:resource` | Danh sách đã xuất bản, page/limit; filter topic_id hoặc part_id theo resource |
| GET | `/learn/catalog/:resource/:id` | Chi tiết đã xuất bản |
| PUT | `/learn/vocabulary/:id/progress` | `{status:"NEW|LEARNING|MASTERED",is_favorite:true}` |
| PUT | `/learn/grammar/:id/progress` | `{progress_percent:0..100}` |
| GET | `/learn/progress` | Tiến độ của người đăng nhập |
| GET | `/learn/grammar/:id/exercises?page=1` | Câu đã xuất bản gắn bài học, không lộ key |
| POST | `/learn/grammar/:id/check` | `{question_id,label}` → đúng/sai, đáp án, giải thích |

Catalog resource: `vocabulary_topics`, `vocabularies`, `grammar_topics`, `grammar_lessons`, `toeic_parts`, `practice_sets`, `mock_tests`. Không có endpoint học viên xem toàn bộ ngân hàng câu hỏi hoặc answer key.

`kind` = `practice` hoặc `mock`:

| Method | Path | Nội dung |
|---|---|---|
| POST | `/learn/:kind/:contentId/attempts` | Bắt đầu bộ luyện/đề → `{id}` |
| GET | `/learn/:kind/attempts?page=1` | Lịch sử của chính người dùng |
| GET | `/learn/:kind/attempts/:attemptId` | `{attempt,questions}`; trước submit không có key/explanation |
| POST | `/learn/:kind/attempts/:attemptId/submit` | Chấm toàn bộ bài, không nộp lần hai |

Body submit:

```json
{"answers":[{"question_id":1,"label":"A"},{"question_id":2,"label":"C"}]}
```

Câu không gửi được chấm sai; câu trùng, câu ngoài bài và nhãn ngoài lựa chọn bị từ chối. Sau deadline bài chấm như bỏ trống. Server dùng key trong snapshot lúc bắt đầu. Kết quả mock có thêm `part5_correct`, `part6_correct`, `part7_correct` và tạo thông báo `RESULT`; không có quy đổi điểm ETS. GET attempt sau submit trả đáp án đúng và giải thích để xem lại.

Snapshot câu Part 6/7 có `group_title`, `group_instructions` và `passages: [{position,passage_type,title,content,image_url,translation_vi}]`. `translation_vi` chỉ trả về sau khi nộp. Mobile gom các câu cùng `group_id` để hiển thị chung đoạn văn.

## Dữ liệu cá nhân của học viên

Tất cả cần đăng nhập; chỉ đọc/sửa dữ liệu của chính mình (của người khác trả 404).

### Từ điển và lịch sử tra từ

| Method | Path | Nội dung |
|---|---|---|
| GET | `/learn/dictionary?q=appo` | Tối đa 20 từ đã xuất bản bắt đầu bằng `q` (không phân biệt hoa thường) |
| GET | `/learn/lookups?page=1` | Lịch sử, mới nhất trước; kèm `ipa`, `meaning_vi` nếu khớp từ trong từ điển |
| POST | `/learn/lookups` | `{keyword, source:"DICTIONARY|READING|FLASHCARD"}`; tra lại cùng từ chỉ tăng `lookup_count` |
| DELETE | `/learn/lookups/:id` | Xóa một dòng |
| DELETE | `/learn/lookups` | Xóa toàn bộ lịch sử |

### Bộ thẻ flashcard (ảnh tùy chọn)

| Method | Path | Nội dung |
|---|---|---|
| GET | `/learn/decks` | Bộ thẻ kèm `card_count`, `due_count`, `pinned_count` |
| POST / PUT | `/learn/decks`, `/learn/decks/:id` | `{name, description, cover_image_url}` |
| DELETE | `/learn/decks/:id` | Xóa bộ và toàn bộ thẻ |
| GET | `/learn/decks/:id/cards?due=true&pinned=true&page=1` | Thẻ trong bộ |
| POST | `/learn/decks/:id/cards` | `{front_text, back_text, ipa, example_sentence, note, image_url, is_pinned}` hoặc chỉ `{vocabulary_id}` để lấy nội dung từ từ điển |
| PUT | `/learn/cards/:id` | Sửa thẻ, thêm/bỏ ảnh (`image_url: null`), ghim |
| DELETE | `/learn/cards/:id` | Xóa thẻ |
| POST | `/learn/cards/:id/review` | `{quality:0..5}` → lịch ôn mới theo SM-2 (`interval_days`, `due_at`) |

`image_url` là URL HTTP(S) sau khi app upload ảnh lên storage; bỏ trống nếu không cần ảnh.

### Thông báo

| Method | Path | Nội dung |
|---|---|---|
| GET | `/learn/notifications?unread=true&page=1` | `{rows,total,unread_count,page,limit}` |
| PATCH | `/learn/notifications/:id/read` | Đánh dấu đã đọc |
| POST | `/learn/notifications/read-all` | Đánh dấu tất cả → `{updated}` |
| DELETE | `/learn/notifications/:id` | Ẩn thông báo |

`type`: SYSTEM, STUDY_REMINDER, NEW_CONTENT, RESULT, ACHIEVEMENT. `data` là JSON để app điều hướng, ví dụ `{"screen":"mock_result","attempt_id":12}`.
