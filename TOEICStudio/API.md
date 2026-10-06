# REST API — TOEIC Studio

Base: `http://localhost:3000/api` (production: domain triển khai + `/api`). JSON request/response. Lỗi có `{ "error": "...", "details": [] }` khi validation. Mã thường dùng: 401 cần đăng nhập, 403 sai quyền/origin, 404 không tồn tại, 409 xung đột hoặc đã nộp, 422 dữ liệu không hợp lệ, 429 quá số yêu cầu.

## Authentication

| Method | Path | Body / kết quả |
|---|---|---|
| POST | `/auth/register` | `{email,password,full_name}`; password 12–128 ký tự; luôn tạo STUDENT |
| POST | `/auth/login` | `{email,password}` → `{token,user}`; JWT hết hạn sau 2 giờ |
| GET | `/auth/me` | Người dùng hiện tại |
| POST | `/auth/logout` | Xóa cookie web; Android tự xóa token đã lưu |
| GET | `/health` | `{status:"ok",database:"mysql"}` |

Android gửi `Authorization: Bearer <token>`. Không gửi `Origin` nếu dùng native HTTP client. Web admin sử dụng cookie, cùng origin và header `X-Requested-With: TOEICAdmin` cho thao tác ghi. API không hỗ trợ web app cross-origin mặc định.

## Admin (role ADMIN)

`GET /admin/meta` trả cấu hình form và tài nguyên chỉ xem.

`GET /admin/dashboard` trả counts, performance, activity 14 ngày, parts, recent audit.

CRUD: `GET/POST /admin/:resource`, `GET/PUT/DELETE /admin/:resource/:id`. PUT gửi toàn bộ các trường ghi, không gửi `id`, `created_at`, `revision` hoặc `password_hash`. Trường không định nghĩa bị từ chối. `GET` danh sách hỗ trợ `page` (mặc định 1), `limit` (1–100, mặc định 20), `search` và trả `{rows,total,page,limit}`.

Các resource ghi: `vocabulary_topics`, `vocabularies`, `grammar_topics`, `grammar_lessons`, `practice_sets`, `question_groups`, `questions`, `practice_set_questions`, `mock_tests`, `mock_test_questions`, `users`. Users không xóa, hãy đổi status. Bản ghi được tham chiếu bởi lịch sử không xóa được.

Resource chỉ xem: `toeic_parts`, `practice_attempts`, `practice_answers`, `mock_test_attempts`, `mock_test_answers`, `user_vocabulary_progress`, `user_grammar_progress`, `audit_logs`.

Tạo question:

```json
{
  "part_id": 5,
  "group_id": null,
  "lesson_id": null,
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

Câu không gửi được chấm sai; câu trùng, câu ngoài bài và nhãn ngoài lựa chọn bị từ chối. Sau deadline bài chấm như bỏ trống. Server dùng key trong snapshot lúc bắt đầu. Kết quả mock có thêm `listening_correct` và `reading_correct`; không có quy đổi điểm ETS. GET attempt sau submit trả đáp án đúng và giải thích để xem lại.

Ảnh/audio của nhóm đi kèm snapshot câu hỏi dưới `image_url`, `audio_url`, `passage_text`, `group_title`. Mobile có thể gom các câu cùng `group_id` để trình bày chung đoạn văn/hội thoại.
