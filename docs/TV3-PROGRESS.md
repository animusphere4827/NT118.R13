Cập nhật tích hợp Android: Reading, quiz, luyện Part 5, thi thử, lịch sử, ngữ pháp và thống kê đã nối API thật. Chi tiết hiện tại xem [ANDROID_STUDY_FLOWS.txt](ANDROID_STUDY_FLOWS.txt). Các ghi chú chờ API bên dưới là trạng thái trước bản tích hợp này.

# Tiến độ TV3 (nội dung, Trang chủ/Lộ trình, Reading, Quiz, Thi thử, Phân tích)

Cập nhật: 28/10/2026 · Nhánh: `feat/tv3-week1-content-home` (chưa commit)

## Trạng thái task

| Task | Hạn | Trạng thái | Ghi chú |
|---|---|---|---|
| L1.1 Từ vựng + ngữ pháp | 28/10 | Xong phần nội dung; **chưa import DB** | 312 từ / 12 chủ đề, 13 bài ngữ pháp. Validator đạt 0 lỗi |
| L1.2 2 đề Reading + bộ luyện | 30/10 | Xong phần nội dung; **chưa import DB** | 2 × 100 câu (30/16/54), 3 bộ luyện Part 5/6/7, 232 câu Reading tổng |
| L1.3 Trang chủ + Lộ trình | 01/11 | Xong trên dữ liệu **mock**; chờ B2.1 | Đã chạy trên emulator (AVD Medium_Phone) |
| L2.1 Reading, L2.2 Quiz, L2.3 Thi thử | Tuần 2 | Chưa bắt đầu | Màn hình hiện là layout tĩnh của Figma |
| L3.1 Kết quả/Lịch sử, L3.2 Phân tích | Tuần 3 | Chưa bắt đầu | |
| T2.1/T3.x Báo cáo | Tuần 2–3 | Chưa bắt đầu | |

## File đã thay đổi

Mới:
- `server/content/` — `vocabulary.json`, `grammar.json`, `reading/test-1.json`, `reading/test-2.json`, `reading/practice.json`, `README.md`
- `server/scripts/validate-content.js`, `server/scripts/import-content.js`, `server/tests/content.test.js`
- Android `learning/`: `PathLesson`, `PathUnit`, `PathUnlockPolicy`, `LearningPathRepository`, `MockLearningPathRepository`, `UnavailableLearningPathRepository`, `LearningPathViewModel`, `LessonRouter`
- Android `ui/learning/`: `HomeFragment`, `LearnPathFragment`, `StateViews`, `LessonNavigator`
- Layout: `fragment_tv3_home.xml`, `fragment_tv3_learn_path.xml`, `item_tv3_entry.xml`, `item_tv3_path_unit.xml`, `item_tv3_path_lesson.xml`, `layout_tv3_state.xml`; chuỗi `values/strings_tv3.xml`
- Test: `app/src/test/.../learning/PathUnlockPolicyTest.java`

Sửa file dùng chung (tối thiểu):
- `android/app/build.gradle.kts`: bật `buildConfig`, thêm cờ `TV3_MOCK_LEARNING_PATH` (debug = true, release = false).
- `android/app/src/main/res/navigation/nav_graph.xml`: destination `home` và `learn_path` trỏ sang `HomeFragment`/`LearnPathFragment` (giữ nguyên id nên các nút `nav:home`, `nav:learn_path` và thanh điều hướng dưới không đổi). Layout Figma gốc `fragment_home.xml`, `fragment_learn_path.xml` vẫn giữ làm tham chiếu.

Không đụng: `android/gradle/libs.versions.toml` và `gradle-wrapper.properties` đang có thay đổi chưa commit từ trước (AGP 9.4.1, Gradle 9.6.0) — giữ nguyên, không phải của TV3.

## Kiểm tra đã chạy (28/10)

| Lệnh | Kết quả |
|---|---|
| `node scripts/validate-content.js` (trong `server`) | 312 từ / 12 chủ đề, 13 bài ngữ pháp, 2 đề, 232 câu; 0 lỗi, 0 cảnh báo |
| `node --test tests/content.test.js` | 8/8 pass (nội dung thật đạt; 7 ca lỗi cố ý đều bị bắt) |
| `./gradlew --offline assembleDebug compileReleaseJavaWithJavac testDebugUnitTest` | BUILD SUCCESSFUL; `PathUnlockPolicyTest` 8/8 pass |
| `assembleRelease` | Không chạy được offline: thiếu `lint-gradle` trong cache (giới hạn môi trường, không phải lỗi code) |
| Chạy thủ công trên emulator | Trang chủ hiển thị nhãn dữ liệu mẫu, bài đang học, tiến độ 3/14; Lộ trình hiển thị đã xong/đang học/khóa; chạm bài khóa hiện thông báo và không chuyển màn; chạm 2 lần bài đang học chỉ mở 1 màn; Back quay đúng; xoay ngang giữ dữ liệu |
| `scripts/import-content.js` | **Chưa chạy** với PostgreSQL (chỉ `node --check`) |

Máy TV3 không có Node riêng: các lệnh Node ở trên chạy bằng runtime Node 24 của VS Code (`ELECTRON_RUN_AS_NODE=1 Code.exe ...`). JDK dùng bản đi kèm Android Studio (JDK 25).

## Mock / API thật

| Phần | Nguồn hiện tại | Ghi chú |
|---|---|---|
| Lộ trình + tiến độ (Trang chủ, Lộ trình) | `MockLearningPathRepository` khi `BuildConfig.TV3_MOCK_LEARNING_PATH = true` (chỉ debug) | Có nhãn "DỮ LIỆU MẪU" trên màn hình. Tắt cờ thì hiện lỗi "Chưa có API lộ trình", **không** tự quay về mock |
| Quy tắc mở khóa | `PathUnlockPolicy` (tạm) | Học tuần tự: bài chưa xong đầu tiên = đang học, sau đó khóa; bài đã xong giữ "đã xong" |
| Điều hướng bài | `LessonRouter` | Từ vựng → `flashcard_front` (TV2), Part 5/ngữ pháp → `quiz_part5`, Part 6/7 → `reading_list`, Thi thử → `exam_list`; truyền `lessonId` cho tuần 2 |

## Chờ TV1 / TV2

TV1:
1. Chạy `scripts/import-content.js` trên DB local/test, xác nhận số liệu (chưa ai chạy thật).
2. **B2.1 API lộ trình** chưa có. Đề xuất (chờ TV1 xác nhận): `GET /api/learn/path` → `{units:[{id,title,description,lessons:[{id,title,subtitle,type:"VOCABULARY|GRAMMAR|PRACTICE_PART5|READING|MOCK_TEST",content_id,minutes,status:"DONE|IN_PROGRESS|LOCKED"}]}],completed,total}`. Nếu server trả `status`, app bỏ `PathUnlockPolicy` và dùng trực tiếp. Lỗi theo chuẩn hiện có (`401`, `404`, `{error}`).
3. Thi thử (cho L2.3): hiện server chấm bài nộp sau `deadline_at` như bỏ trống toàn bộ. Nếu mất mạng đúng lúc hết giờ, người học mất hết đáp án. Đề xuất: thời gian ân hạn ngắn (ví dụ 60 giây) hoặc endpoint lưu nháp đáp án.
4. Quiz 3 dạng (L2.2: điền khuyết, nối từ, sắp xếp câu) chưa có bảng/endpoint; schema hiện chỉ có câu 4 lựa chọn. Cần chốt JSON với TV1 trước 03/11.

TV2 (main đã có M1.1–M3.3: Retrofit, lưu token mã hóa, đăng nhập, từ điển, flashcard, thông báo; đã thử gộp nhánh này với main ngày 28/10: không xung đột, `assembleDebug` + unit test đạt):
1. Tuần 2 TV3 dùng lại HTTP client/`LearnApi` và cơ chế token của TV2, không tự làm riêng.
2. Trang chủ chưa hiển thị tên người dùng; lấy từ profile của TV2 khi nối API.
3. Lộ trình mở `flashcard_front` cho bài từ vựng; cần TV2 xác nhận màn học từ theo chủ đề sẽ nhận `lessonId`/`topic_id` thế nào.

## Bước tiếp theo (cho phiên sau)

1. Khi TV1 import xong: kiểm tra `GET /api/learn/catalog/mock_tests` có 2 đề "Đề luyện Reading tự biên soạn số 1/2", `practice_sets` có 3 bộ.
2. L2.1 Reading (02/11): thay destination `reading_list`, `reading_article` bằng Fragment thật; lấy nội dung qua `practice` attempts (snapshot có `passages`); chạm từ → `GET /api/learn/dictionary?q=` + `POST /api/learn/lookups {source:"READING"}`; thêm flashcard qua `POST /api/learn/decks/:id/cards {vocabulary_id}` của TV2.
3. L2.3 Thi thử: timer theo `deadline_at` của attempt (không đếm lùi biến), lưu đáp án cục bộ theo `attempt_id` để khôi phục khi xoay/nền, khóa nút nộp khi đang gửi, giữ bài và cho thử lại khi lỗi mạng.
4. Nếu cần chạy `assembleRelease`, chạy online một lần để Gradle tải `lint-gradle`.
