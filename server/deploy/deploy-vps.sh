#!/usr/bin/env bash
# Deploy server lên VPS. Chạy tay (sudo bash .../deploy-vps.sh) hoặc do GitHub Actions gọi qua SSH.
# Toàn bộ nằm trong hàm main: bash đọc xong cả hàm trước khi chạy, nên git pull sửa chính file này
# giữa chừng không làm hỏng lần chạy. Sau khi pull, script tự chạy lại bản mới đúng một lần.
set -euo pipefail

main() {
  umask 077
  export COMPOSE_PROJECT_NAME=nt118
  ENV_FILE="${ENV_FILE:-/etc/nt118/server.env}"
  if [ ! -r "$ENV_FILE" ]; then printf '%s\n' 'Missing server environment file.' >&2; exit 1; fi
  mkdir -p /opt/nt118/backups

  # Không cho hai lần deploy chạy cùng lúc (tay và tự động)
  exec 9>/opt/nt118/deploy.lock
  if ! flock -w 600 9; then printf '%s\n' 'Another deploy is still running.' >&2; exit 1; fi

  cd /opt/nt118
  if [ ! -d repo/.git ]; then git clone https://github.com/animusphere4827/NT118.R13.git repo; fi
  cd repo
  git fetch origin main
  git checkout main
  git merge --ff-only origin/main
  printf 'Deploy commit %s\n' "$(git rev-parse --short HEAD)"

  # Chạy lại bằng phiên bản script vừa kéo về để các bước mới có hiệu lực ngay lần này
  if [ "${NT118_DEPLOY_REEXEC:-0}" != 1 ]; then
    exec 9>&-
    NT118_DEPLOY_REEXEC=1 exec bash /opt/nt118/repo/server/deploy/deploy-vps.sh
  fi

  cd server
  compose=(docker compose --env-file "$ENV_FILE" -f compose.yaml -f compose.https.yaml)
  "${compose[@]}" config --quiet
  if "${compose[@]}" ps --status running --services | grep -qx postgres; then
    "${compose[@]}" exec -T postgres pg_dump -U toeic -d toeic > "/opt/nt118/backups/toeic-$(date -u +%Y%m%dT%H%M%SZ).sql"
  fi
  "${compose[@]}" up -d --build --wait --wait-timeout 180
  curl --fail --retry 10 --retry-delay 3 http://127.0.0.1:3000/api/health

  # Nội dung học. import-content ghi lại trạng thái/mô tả chủ đề trùng tên, nên chỉ chạy khi database
  # chưa có chủ đề từ vựng nào, để không ghi đè chỉnh sửa của admin trên web.
  # seed-course và import-quizzes chỉ thêm phần còn thiếu, chạy lại mỗi lần deploy đều an toàn.
  topics=$("${compose[@]}" exec -T postgres psql -U toeic -d toeic -tAc 'SELECT COUNT(*) FROM vocabulary_topics')
  if [ "${topics//[[:space:]]/}" = "0" ]; then
    "${compose[@]}" exec -T app node scripts/import-content.js
  else
    printf 'Bỏ qua import-content: database đã có %s chủ đề từ vựng.\n' "${topics//[[:space:]]/}"
  fi
  "${compose[@]}" exec -T app node scripts/seed-course.js
  "${compose[@]}" exec -T app node scripts/import-quizzes.js

  # Giữ 14 bản sao lưu gần nhất
  ls -1t /opt/nt118/backups/toeic-*.sql 2>/dev/null | tail -n +15 | xargs -r rm -f
  printf '%s\n' 'Deploy finished.'
}

main "$@"
