# TOEIC Studio — Database, Node.js API & React Admin

Ứng dụng quản trị học TOEIC: Express 5 + Node.js 24, MySQL 8.4, React 19 và Vite 7. Backend phục vụ cả REST API và bản build React trên cùng domain.

## Hiện trạng

- Đã chạy local với MySQL 8.4 thật tại `http://localhost:3000`.
- Đã build React, kiểm tra API tích hợp với MySQL, đăng nhập và lưu chỉnh sửa trên trình duyệt.
- Đã deploy Docker trên Railway: https://toeic-api-production-8b44.up.railway.app. MySQL Railway có volume riêng; private database URL được tham chiếu qua biến dịch vụ.
- Đã kiểm tra HTTPS, đăng nhập production, CRUD, dashboard, luyện tập, thi thử và xem lại đáp án trên server public. Thông tin đăng nhập riêng nằm trong `PRODUCTION-LOGIN.txt`, không có trong gói source.
- Đây là bản chức năng ban đầu. Seed có 8 từ, 1 bài ngữ pháp, 5 câu Reading tự biên soạn, 1 bộ Part 5 và 1 mini test. Chưa có kho audio hoặc đề đầy đủ 200 câu.

## Chức năng

- JWT 2 giờ; admin web sử dụng HttpOnly cookie; Android sử dụng Bearer token.
- Đăng ký học viên, đăng nhập, khóa/vô hiệu hóa người dùng, quản lý mục tiêu TOEIC.
- CRUD chủ đề/từ vựng, chủ đề/bài ngữ pháp, nhóm câu hỏi có đoạn văn/audio/ảnh, câu hỏi và đáp án, bộ luyện, đề thi, danh sách câu theo thứ tự.
- Bài tập ngữ pháp dùng chung ngân hàng câu hỏi qua `lesson_id`.
- Part 1–7 dùng một hệ thống câu hỏi. Part 2 có 3 lựa chọn; các Part khác có 4. Chính xác một đáp án đúng.
- Chỉ xuất bản Listening khi có audio; Part 1 cần ảnh; Part 6–7 cần đoạn văn.
- Bộ luyện/đề phải có câu đã xuất bản; bộ luyện và câu phải cùng Part.
- Bắt đầu/nộp/xem lại luyện tập và thi thử; lưu snapshot câu hỏi và đáp án khi bắt đầu; chấm trên server trong transaction và khóa hàng để chống nộp trùng.
- Không gửi đáp án đúng hoặc giải thích trước khi nộp. Bài làm chỉ chủ sở hữu được đọc.
- Deadline được server kiểm tra. Bài nộp sau deadline chấm như bỏ trống; hiện chưa có autosave từng câu.
- Dashboard: số người dùng/nội dung/bài làm, độ chính xác trung bình, nhịp học 14 ngày UTC, độ phủ Part và nhật ký admin. Không dùng số liệu giả.
- Tiến độ từ vựng/ngữ pháp; lịch sử bài làm/đáp án chỉ xem trong admin.
- Validation, parameterized SQL, foreign keys, Helmet, rate limits, kiểm tra origin/CSRF, bảo vệ quản trị viên cuối cùng.

## Cấu trúc

```text
backend/       server, REST API, schemas, migration, seed
frontend/      React admin, giao diện tiếng Việt
tests/         kiểm tra luồng API với MySQL thật
public/        favicon
deploy/        cấu hình reverse proxy HTTPS mẫu
Dockerfile     bản build Node.js production
compose.yaml   app + MySQL có volume lưu dữ liệu
railway.json   cấu hình build và health check
```

## Chạy trên máy khác

1. Cài Node.js 24, pnpm 11.19.0 và MySQL 8.4. Tạo database `toeic` dùng `utf8mb4`; tạo tài khoản database riêng.
2. Copy `.env.example` thành `.env`, thay database URL, JWT secret ngẫu nhiên tối thiểu 32 ký tự, email và mật khẩu admin tối thiểu 12 ký tự.
3. Chạy:

```sh
pnpm install --frozen-lockfile
pnpm migrate
pnpm seed
pnpm build
pnpm dev
```

Muốn nội dung mẫu: thêm `SEED_DEMO=true` vào `.env` trước khi seed. Seed mặc định chỉ tạo admin và 7 Part, không tự thêm mẫu trong production. Seed không đổi mật khẩu admin đã tồn tại.

React phát triển riêng: `pnpm exec vite --host 127.0.0.1`; backend vẫn chạy port 3000. Khi chạy React dev server riêng, đổi `APP_ORIGIN` sang origin của Vite. Web admin production và API dùng chung origin; chưa mở CORS cho website khác.

Kiểm tra: `pnpm test`. Test tạo rồi dọn dữ liệu trong database cấu hình bởi `.env`; chỉ chạy với database local/test riêng, tránh production.

Thông tin đăng nhập local đã tạo nằm ở `LOCAL-LOGIN.txt`; `.env` và file này không nằm trong source archive và Docker image. MySQL local của phiên hiện tại nằm trong thư mục `work/` bên ngoài source, không cài Windows service. Các script `start-local.ps1` / `stop-local.ps1` chỉ dành cho máy hiện tại.

## Triển khai Railway

Đã triển khai bằng CLI trong workspace Dương TK's Projects, project `toeic-studio`, app `toeic-api`, database `MySQL`. Railway template hiện cung cấp MySQL 9; schema đã migrate và chạy thành công trên bản này. Server public có seed Reading demo và hai bài làm kiểm tra triển khai. Quy trình bên dưới để triển khai lại; cần tài khoản có quyền tạo dịch vụ và đủ quota. Không cần GitHub nếu triển khai từ source bằng CLI.

1. Tạo project, thêm MySQL và một app service từ source này (Dockerfile).
2. App service đặt `DATABASE_URL` bằng reference `${{MySQL.MYSQL_URL}}` của dịch vụ MySQL; không dùng public database URL cho app khi có private networking.
3. Đặt `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME`, `NODE_ENV=production`, `TRUST_PROXY=true`, `SEED_DEMO=false`.
4. Generate domain cho app; đặt `APP_ORIGIN=https://<domain>` đúng origin không có dấu `/` cuối. Railway cung cấp `PORT`; app tự đọc.
5. Deploy. Container áp dụng schema, tạo admin nếu chưa có, rồi mở server. Health check `/api/health` chỉ thành công khi kết nối MySQL thành công.
6. Kiểm tra HTTPS, đăng nhập, CRUD và luồng học viên trên URL public. Bật backup/volume cho database theo gói tài khoản. Sau lần bootstrap có thể gỡ `ADMIN_PASSWORD` khỏi dịch vụ nếu đổi start command để bỏ seed.

Tài liệu chính thức: [MySQL trên Railway](https://docs.railway.com/databases/mysql), [biến tham chiếu](https://docs.railway.com/variables).

## Triển khai VPS / EC2

Trên Ubuntu có Docker Compose và domain trỏ về máy:

1. Copy source và tạo `.env` trên server với `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`, `DEPLOY_DATABASE_URL=mysql://toeic:<URL-encoded-password>@mysql:3306/toeic`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `APP_ORIGIN=https://admin.example.com`.
2. Chạy `docker compose up -d --build`. MySQL chỉ ở mạng nội bộ Compose; app chỉ bind cổng máy chủ `127.0.0.1:3000`.
3. Cài Caddy từ nguồn chính thức, thay domain trong `deploy/Caddyfile`, cấu hình reverse proxy đến `127.0.0.1:3000`; mở 80/443 ở firewall/security group. Không mở port MySQL.
4. Kiểm tra HTTPS và `/api/health`, rồi kiểm tra đăng nhập public.

Volume `mysql-data` chứa dữ liệu. `docker compose down` giữ volume; không dùng `down -v` nếu cần giữ dữ liệu. Backup trước khi cập nhật schema. Schema v1 idempotent; các thay đổi schema tương lai cần migration tăng version, không chỉ sửa file SQL.

## Giới hạn có chủ ý

- Kết quả là số câu đúng và phần trăm, không gán điểm TOEIC chuẩn ETS hoặc quy đổi giả.
- Đề thi có thể là mini test; hiện không ép cấu trúc đề TOEIC đầy đủ/section listening 45 phút và reading 75 phút.
- Audio/ảnh nhập qua HTTPS URL; chưa có upload object storage.
- Chưa có reset password qua email, refresh token, autosave từng câu hoặc kiểm tra bản quyền kho đề.
- Android application nằm ngoài phạm vi; có API để Android tích hợp, xem `API.md` và `openapi.yaml`.
- CI/CD thực tế cần repository/tài khoản của bạn; source có workflow GitHub Actions để build và test MySQL khi push.
