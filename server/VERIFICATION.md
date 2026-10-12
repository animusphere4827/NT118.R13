# Verification

## PostgreSQL + Reading-only schema · 12 October 2026

- Database switched from MySQL (`mysql2`) to PostgreSQL (`pg` 8.23). Schema applied twice on PostgreSQL 16.15 to confirm it is idempotent.
- Fresh database, `migrate` + `seed` without demo content, then `pnpm test`: passed. Same run on a database seeded with `SEED_DEMO=true`: passed; test data cleaned up afterwards.
- Integration test now also covers: 4-option rule, Part 5 without group, Part 6/7 publish requiring a passage, passage locking while questions are published, translation hidden until submission, per-Part mock counts, automatic RESULT notification, admin broadcast, read/read-all/hide notifications, dictionary search, lookup history upsert (case-insensitive) and ownership, flashcard decks with optional images, adding cards from the dictionary, duplicate-word protection, SM-2 review scheduling.
- React admin build passed (Vite 7.3.6). Browser check: login, dashboard with Part 5–7 coverage, reading passage editor, 4-option question editor.
- Google sign-in via Firebase: second test signs tokens with a locally generated RS256 key and checks account creation, repeat login, linking to an existing password account, refusal for admin/another UID/locked account, and rejection of wrong audience, issuer, expired, foreign-key-signed, non-Google and unverified-email tokens. Schema upgrade (ALTERs) verified on a database created before this change. Not yet tested with a real Firebase project.
- Not yet done: Railway redeploy with a PostgreSQL service; GitHub Actions run on the new workflow.

## Earlier MySQL verification · 5 October 2026

## Completed locally

- React production build completed with Vite 7.3.6 (28 modules, ~244 kB JavaScript before gzip).
- MySQL Community Server 8.4.11 running as a separate localhost-only process, port 3307, workspace data directory.
- Schema migration and sample seed completed against real MySQL. Application database user has privileges only on its own database; local root password randomized.
- API integration suite passed with real MySQL: health, unauthenticated requests, admin/student role separation, forced STUDENT registration, CRUD validation, relation matching, vocabulary/grammar progress, grammar exercises, attempt ownership, answer-key redaction, snapshot grading after question changes, invalid answers, duplicate submissions, deadline expiry, mock reading counts, protected deletion, password-hash redaction, pagination validation, dashboard and CSRF/origin checks.
- Browser verification: login succeeded, dashboard displayed live database counts, vocabulary edit saved and returned to list, question editor displayed correct group/options/answer key. Layout inspected in narrow and desktop browser views.
- MySQL sessions and driver use UTC. UI displays admin dates in Asia/Ho_Chi_Minh.
- Production dependency audit: no known vulnerabilities reported at check time.
- App restarted with supplied start-local script and `/api/health` returned success.

## Public deployment (MySQL) · 6 October 2026 (Asia/Saigon)

- URL: https://toeic-api-production-8b44.up.railway.app
- Railway project: `toeic-studio` (`e3b8c084-03a5-4f47-a0cf-3176dd599d3d`). App service: `toeic-api`; database service: `MySQL` with persistent volume.
- Deployment `2b39b111-b076-4068-9b6b-94e52b1728cb` reached SUCCESS. Docker image built using the supplied Dockerfile, production React build completed, migration and seed succeeded on Railway's MySQL 9 template.
- Public HTTPS/MySQL health, production admin login, admin metadata, dashboard, disposable topic CRUD, practice and mock creation/submission/review all passed. Both seeded Reading attempts graded 100%; in-progress answer keys were redacted.
- Separate random production admin password/JWT secret configured through Railway variables. Local credentials were excluded from uploaded source. Database URL uses the private MySQL service reference.
- Production login file is excluded from Docker, deployment upload, and source archive.
- Docker Compose and GitHub Actions workflow remain untested in their respective environments. Hosted backup scheduling has not been configured.

## Sample data

8 vocabulary entries, 3 vocabulary topics, 1 grammar topic/lesson, 7 TOEIC Part definitions, 1 question group, 5 original Reading questions with 20 options, 1 Part 5 practice set, 1 five-question Reading mini mock test. Listening content and full-length TOEIC tests are not seeded.
