# Nội dung học (TV3 · L1.1, L1.2)

Nội dung **tự biên soạn** cho app Vocabulary + TOEIC Reading. Không sao chép đề thương mại; đề thi ở đây là đề luyện, không phải đề chính thức của ETS.

| File | Nội dung |
|---|---|
| `vocabulary.json` | 312 từ, 12 chủ đề TOEIC. Mỗi từ: `[word, part_of_speech, ipa, meaning_vi, example_sentence]` |
| `grammar.json` | 5 chủ đề, 13 bài ngữ pháp phục vụ Part 5/6 |
| `reading/test-1.json`, `reading/test-2.json` | 2 đề Reading đầy đủ, mỗi đề 100 câu: Part 5 = 30, Part 6 = 16 (4 đoạn), Part 7 = 54 (29 câu đoạn đơn, 10 câu đoạn kép, 15 câu đoạn ba), 75 phút |
| `reading/practice.json` | 3 bộ luyện theo Part: Part 5 (15 câu, có gắn bài ngữ pháp), Part 6 (2 đoạn, 8 câu), Part 7 (3 nhóm, 9 câu) |

Quy ước:

- Câu đề thi đánh số 101–200 như đề thật. Chỗ trống Part 6 trong đoạn văn ghi `(131) _____` và câu hỏi tương ứng là `Choose the best option for blank (131).`
- Câu chèn câu Part 7 dùng vị trí `[1]`–`[4]` trong đoạn văn.
- `type` của câu và `passage.type` dùng đúng giá trị CHECK trong `backend/schema.sql`.
- Đáp án đúng được phân bổ đều A/B/C/D (validator báo lỗi nếu một chữ cái dưới 15% hoặc trên 35% trong một đề).
- `key`/`no` là mã ổn định để đối chiếu, không phải id database.

## Kiểm tra (không cần database)

Chạy trong thư mục `server`:

```sh
node scripts/validate-content.js
node --test tests/content.test.js
```

Validator kiểm tra: số từ/chủ đề tối thiểu, trường bắt buộc, IPA, từ trùng, số câu theo đề và Part, cấu trúc Part 7 (đơn/kép/ba), 4 phương án khác nhau, đáp án A–D, giải thích, chỗ trống Part 6 khớp đoạn văn, tham chiếu bài ngữ pháp, nội dung giữ chỗ (lorem ipsum, TODO), phân bổ vị trí đáp án.

## Import vào PostgreSQL

Sau `pnpm migrate` và `pnpm seed` (cần có `toeic_parts` 5–7):

```sh
node --env-file=.env scripts/import-content.js
```

- Validator chạy trước; có lỗi thì không ghi gì.
- Mỗi đề và bộ luyện được import trong một transaction: tạo ở DRAFT, thêm câu theo thứ tự, rồi chuyển sang PUBLISHED.
- Chạy lại an toàn: chủ đề trùng tên dùng lại, từ đã có bỏ qua, đề/bộ luyện trùng tiêu đề bỏ qua.
- Chỉ dùng câu lệnh `INSERT/UPDATE` trên các bảng có sẵn, không đổi schema.

> **Trạng thái:** script import **chưa được chạy với PostgreSQL thật** (máy TV3 chưa có PostgreSQL, Docker Desktop lỗi khi khởi động). Mới kiểm tra cú pháp. Cần TV1 chạy thử trên database local/test rồi đối chiếu số liệu trong web admin (dashboard: độ phủ Part 5–7).
