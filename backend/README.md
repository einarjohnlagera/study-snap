# Study Snap Backend (Spring Boot)

Backend API for Study Snap.

## Responsibilities
- Accept text/image input
- OCR image input (if provided)
- Normalize text
- Call LLM
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

### POST /api/solve
Input:
- JSON `{ "text": "..." }` OR multipart form with `image`

Output (success):
- `restatedQuestion`
- `steps[]`
- `finalAnswer`

## Rules (MVP)
- Controllers thin; logic in services.
- Enforce server-side limits (file size/type, text length).
- Do not store images permanently; delete after OCR.
- Avoid logging raw images or full extracted text.
- Log request id + latency.
