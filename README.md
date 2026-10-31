# NT118.R13 · Nhóm 8

App học tiếng Anh tập trung **Từ vựng** và **TOEIC Reading** (Part 5, 6, 7).

## Cấu trúc

```text
android/   App Android (Android Studio, Gradle)
server/    Backend Node.js (Express 5) + database PostgreSQL + web admin React
docs/      Tài liệu API: API.md, openapi.yaml
.github/   GitHub Actions (build và test server)
```

## Server

- Database: [schema PostgreSQL](server/backend/schema.sql), migration và seed.
- Backend: JWT, đăng nhập Google qua Firebase, API học viên/admin, luyện tập và thi thử Reading, flashcard có ảnh, lịch sử tra từ, thông báo, tiến độ và thống kê.
- Web admin (React): CRUD từ vựng/ngữ pháp/đoạn văn/câu hỏi/đề thi và dashboard.
- [Quy tắc lộ trình và hoàn thành bài từ vựng](docs/COURSE_LOGIC.txt)
- [Hướng dẫn chạy và deploy](server/README.md) · [Tài liệu API](docs/API.md) · [OpenAPI](docs/openapi.yaml)

Lệnh `pnpm` chạy trong thư mục `server`. Khi deploy từ repository này (Railway...), chọn root directory `/server`.

## Android

Mở thư mục `android/` bằng Android Studio. App gọi API của server (Bearer JWT) và đăng nhập Google bằng Firebase Authentication; file `google-services.json` đặt trong `android/app/`.

Thông tin đăng nhập, file `.env`, `local.properties` và database runtime không đưa lên GitHub.
