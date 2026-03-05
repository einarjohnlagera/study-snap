# Study Snap Backend (Spring Boot)

Backend API for Study Snap.

## Responsibilities
- Accept notes text or image
- OCR image input (if provided)
- Normalize notes
- Call LLM to generate review materials
- Return structured JSON

## Run locally
Maven:
```bash
./mvnw spring-boot:run
```
Gradle:
```bash
./gradlew bootRun
```
Default: http://localhost:8080

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
