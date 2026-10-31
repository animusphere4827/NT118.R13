# REST API · TOEIC Studio (PostgreSQL, Vocabulary + TOEIC Reading)

Base: `http://localhost:3000/api` (production: domain triển khai + `/api`). JSON request/response. Lỗi có `{ "error": "...", "details": [] }` khi validation. Mã thường dùng: 401 cần đăng nhập, 403 sai quyền/origin, 404 không tồn tại, 409 xung đột hoặc đã nộp, 422 dữ liệu không hợp lệ, 429 quá số yêu cầu.

## Authentication

| Method | Path | Body / kết quả |
|---|---|---|
| POST | `/auth/register` | `{email,password,full_name}`; password 12–128 ký tự; luôn tạo STUDENT |
| POST | `/auth/login` | `{email,password}` → `{token,user}`; JWT hết hạn sau 15 phút; kèm refresh_token tối đa 30 ngày |
| POST | `/auth/firebase` | `{id_token}` (Firebase ID token sau khi đăng nhập Google) → `{token,user,is_new_user}`; 201 khi tạo tài khoản mới, 200 khi đăng nhập lại |
| GET | `/auth/me` | Người dùng hiện tại |
| POST | `/auth/logout` | Thu hồi phiên trên server và xóa cookie; Android xóa cả access và refresh token |
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
| POST | `/learn/:kind/attempts/:attemptId/submit` | Chấm toàn bộ bài; retry trả kết quả đã chốt |

Body submit:

```json
{"answers":[{"question_id":1,"label":"A"},{"question_id":2,"label":"C"}]}
```

Câu không gửi được chấm sai; câu trùng, câu ngoài bài và nhãn ngoài lựa chọn bị từ chối. Sau deadline bài chấm nháp đã lưu thành công trước hạn; bỏ qua đáp án mới trong body muộn. Server dùng key trong snapshot lúc bắt đầu. Kết quả mock có thêm `part5_correct`, `part6_correct`, `part7_correct` và tạo thông báo `RESULT`; không có quy đổi điểm ETS. GET attempt sau submit trả đáp án đúng và giải thích để xem lại.

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

B1.2 phiên đăng nhập
Access JWT có hạn 15 phút; refresh token có hạn tối đa 30 ngày. Login và Firebase trả các trường token, access_token, refresh_token, token_type, expires_in, refresh_expires_in và user. Trường token vẫn dùng được với Android hiện có.

POST /auth/refresh nhận {"refresh_token":"..."} hoặc cookie refresh_token cho web admin. Thành công trả cặp token mới. Client thay refresh token đã lưu, chỉ gửi một yêu cầu refresh tại một thời điểm. Dùng lại token cũ sẽ thu hồi phiên.

POST /auth/logout nhận Bearer access token hoặc {"refresh_token":"..."}. Logout thu hồi phiên trên server, không chỉ xóa cookie. Token của phiên đó không thể gọi API tiếp. Các phiên thiết bị khác được giữ lại.

Web admin dùng cookie HttpOnly và header X-Requested-With: TOEICAdmin cho refresh/logout. Android dùng JSON và Bearer header. Google endpoint là /auth/firebase, nhận Firebase ID token sau Google sign-in; Google OAuth ID token không được chấp nhận thay thế. Xem B1_AUTH.txt.

B1.3: /auth/password/forgot, /auth/password/verify, /auth/password/reset; PUT /auth/me; POST /auth/me/avatar (multipart avatar). Chi tiết request/response trong openapi.yaml và B1_3_OPERATIONS.txt. Admin đã mở CRUD flashcard_decks, flashcards, notifications. Dashboard nhận days từ 7 tới 90.


B2.1 Course và Practical Reading

GET /learn/path trả chương/bài theo thứ tự DB, content_id, content_url, trạng thái DONE/IN_PROGRESS/LOCKED và tiến độ của phiên đăng nhập. GET /learn/path/lessons/:id trả chi tiết điều hướng; bài khóa trả 409.

GET /learn/readings hỗ trợ page, limit, part_id (6/7), type và search. GET /learn/readings/:id trả toàn bộ đoạn của nhóm theo position; thêm ?translation=true để lấy bản dịch. Chỉ đọc nội dung từ bộ luyện PUBLISHED, không mở nhóm chỉ thuộc đề thi. Không trả đáp án/lời giải.

PUT /learn/readings/:id/progress nhận {"completed":true}, chạy lại giữ mốc hoàn thành đầu tiên. POST /learn/readings/:id/lookups nhận {"keyword":"invoice"}, kiểm tra từ xuất hiện trong bài rồi tra từ điển đã xuất bản và ghi lịch sử READING. Từ chưa có nghĩa trả rows=[], không tạo nghĩa giả.

Chạy pnpm content:import và pnpm course:seed sau migrate/seed để nhập nội dung TV3 và tạo lộ trình. Quy tắc mở khóa và hoàn thành từ vựng đã được cập nhật trong COURSE_LOGIC.txt và openapi.yaml; không dùng cờ MASTERED để tự hoàn thành bài.


B2.2 Quiz / Reading attempts

GET /learn/quizzes và /learn/quizzes/:id trả metadata bộ quiz. POST /learn/quizzes/:id/attempts tạo snapshot; GET /learn/quiz/attempts/:id đọc bài. POST /learn/quiz/attempts/:id/answers chấm câu FILL_BLANK, MATCH hoặc ORDER và trả feedback lần đầu; POST /learn/quiz/attempts/:id/submit chốt toàn bài. GET /learn/quiz/attempts xem lịch sử riêng.

PATCH /learn/:kind/attempts/:id/answers (kind practice/mock) nhận answer_version và batch answers với question_id, label tùy chọn, marked_for_review tùy chọn. Phiên bản cũ trả 409. GET bài trả deadline_at/server_time và nháp để tiếp tục làm bài. POST submit nhận answers tùy chọn và answer_version tùy chọn; nộp lại trả nguyên kết quả 200.

GET bài sau deadline tự chấm dữ liệu đã lưu; maintenance cũng xử lý bài hết giờ theo lô. Lịch sử hỗ trợ page,limit,status,content_id. Chi tiết cấu trúc cả ba dạng quiz, deadline, autosave và retry ở B2_2_QUIZ_EXAMS.txt; kết quả kiểm thử ở B2_2_TEST_RESULTS.txt.


B2.3 Web admin và quản lý nội dung

GET chi tiết /admin/:resource/:id trả edit_version. Web gửi If-Match khi sửa/xóa; phiên bản cũ trả 409. Meta thêm required/maxLength/min/max/defaultValue. Danh sách hỗ trợ filter status và các trường quan hệ của resource, tìm #ID hoặc nội dung; %, _ là ký tự thường.

POST /admin/content/readings và GET/PUT/DELETE /admin/content/readings/:id quản lý nguyên nhóm đoạn văn và câu hỏi trong transaction. is_practical_reading=true đưa nhóm có câu PUBLISHED vào thư viện đọc độc lập ngay; các nhóm chỉ dùng trong đề không tự bật cờ. Nhóm thuộc bộ luyện công khai vẫn hiển thị theo bộ luyện.

POST /admin/content/exams/:kind và GET/PUT /admin/content/exams/:kind/:id nhận metadata và question_ids theo thứ tự. kind=practice|mock. Đề mock có test_format MINI hoặc FULL; FULL khi xuất bản cần 100 câu theo 30/16/54 trong 4500 giây. PUT/DELETE workspace cần If-Match, thiếu trả 428. Lịch sử/snapshot được bảo toàn. Hướng dẫn: B2_3_ADMIN.txt.
