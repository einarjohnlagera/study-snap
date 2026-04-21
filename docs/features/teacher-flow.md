# teacher-flow.md - NoteLib Feature Context

## Goal

Teacher Flow is the quiz-authoring path for teachers.

It is intentionally separate from Student and Board Taker quiz-taking behavior.

Core lifecycle:

- `Generate`
- `View`
- `Export`

Teacher Dashboard should feed that lifecycle, not replace the shared note product.

- `Create Teaching Material`
- `Recent Notes`
- `Recently Generated Quizzes`
- `Ready to Export`
- `Teacher Help / Tips`

## Ownership Model

Teacher quiz data uses `generatedQuiz`.

- note-owned
- read-only in preview
- regenerated in place for v1
- no quiz session is created
- no score, attempts, performance history, or weak-concept tracking

Student quiz data uses `quizSession`.

- answer storage
- score
- attempts
- performance metrics
- session review history

Do not mix these models.

## Generate -> View -> Export

### Generate

- Entry point: Teacher mode Note Detail
- Primary CTA before generation: `Generate Quiz`
- Helper copy: `Generate a quiz from this note with answers and explanations for review and export.`
- Generation consumes `1` quiz credit
- Quiz generation is based on the note itself, not on student quiz sessions

### View

- After generation, Note Detail shows:
  - primary: `View Quiz`
  - secondary: `Regenerate`
- Helper copy: `Already generated a quiz? Regenerate to create a new version (costs 1 credit).`
- `View Quiz` opens a dedicated Quiz Preview page
- Quiz Preview is read-only and shows:
  - question text
  - choices
  - correct answer clearly highlighted
  - explanation visible

### Export

- Export belongs only inside Quiz Preview
- Place Export in the top-right of the Quiz Preview header
- Export uses stored `generatedQuiz` data only and must not call LLMs
- Export format for Teacher Flow is `DOCX`
- Export options:
  - `Quiz Only (Student Version)`
  - `Quiz + Answers (Teacher Version)`
- `Quiz + Answers` appends:
  - `Answer Key`
  - `Explanations`
- DOCX export is shown only for `Teacher` and `Admin`
- Teachers can also build one combined exam from `Library`:
  - enter teacher-only `Select` mode
  - choose multiple quiz-ready notes
  - reorder notes inside `Exam Builder` using the drag handle or the fallback move buttons
  - export one combined DOCX with optional `Answer Key` and `Explanations`

## UI Rules

- Teacher mode Note Detail must not show:
  - `Start Quick Review`
  - student preview/take-quiz session controls
  - `Performance Overview`
  - `Recent Sessions`
  - weak-concept UI
  - Board Exam references
- Do not place `Export` on Note Detail
- Regeneration requires confirmation:
  - title: `Regenerate quiz?`
  - body: `This will create a new set of questions and costs 1 credit.`

## Implementation Rule

- Do not reuse student quiz session logic for teacher preview
- Do not route teachers into Challenge Quiz setup or session screens for preview
- Teacher flow uses `generatedQuiz` only
