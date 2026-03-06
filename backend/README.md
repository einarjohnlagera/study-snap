# Study Snap Backend (Spring Boot)

Backend API for Study Snap.

## Responsibilities
- Accept notes text or image
- OCR image input (if provided)
- Normalize notes
- Call LLM to generate review materials
- Return structured JSON

## Run locally
Start Postgres first (from repo root):
```bash
docker compose up -d postgres
```

Set datasource env vars to match `docker-compose.yml`:
```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=study_snap
DB_USER=ss_user
DB_PASSWORD=ss#20260305
```

For local development, you can set these in `backend/.env` and start with:
```powershell
./start-dev.ps1
```
If you still see `${DB_USER}` in runtime errors, clear any `DB_USER`/`DB_PASSWORD` values from your IDE Run Configuration or system environment, because those override `.env`.

Maven:
```bash
./mvnw spring-boot:run
```
Gradle:
```bash
./gradlew bootRun
```
Default: http://localhost:8080
Base API path: http://localhost:8080/api

## Run with Docker
From repo root:
```bash
docker compose up -d --build backend
```

This starts:
- `postgres` on `localhost:5432`
- `backend` on `localhost:8080` (API base path `/api`)

## MVP endpoint
### POST /api/review
Input:
- JSON `{ "notesText": "..." }` OR multipart with `image`

Output:
- title
- summary
- keyConcepts[]
- quiz[] (default 5)

## Rules (MVP)
- Controllers thin; services orchestrate.
- Enforce server-side limits (file size/type, text length).
- Delete images after OCR.
- Avoid logging raw images or full extracted text.
