#!/usr/bin/env bash
set -euo pipefail
umask 077
export COMPOSE_PROJECT_NAME=nt118
ENV_FILE="${ENV_FILE:-/etc/nt118/server.env}"
if [ ! -r "$ENV_FILE" ]; then printf '%s\n' 'Missing server environment file.' >&2; exit 1; fi
mkdir -p /opt/nt118/backups
cd /opt/nt118
if [ ! -d repo/.git ]; then git clone https://github.com/animusphere4827/NT118.R13.git repo; fi
cd repo
git fetch origin main
git checkout main
git merge --ff-only origin/main
cd server
compose=(docker compose --env-file "$ENV_FILE" -f compose.yaml -f compose.https.yaml)
"${compose[@]}" config --quiet
if "${compose[@]}" ps --status running --services | grep -qx postgres; then
  "${compose[@]}" exec -T postgres pg_dump -U toeic -d toeic > "/opt/nt118/backups/toeic-$(date -u +%Y%m%dT%H%M%SZ).sql"
fi
"${compose[@]}" up -d --build --wait --wait-timeout 180
curl --fail --retry 10 --retry-delay 3 http://127.0.0.1:3000/api/health
