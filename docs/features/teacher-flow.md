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
- `Ready to Export`
- `Recently Generated Quizzes`
- `Recent Notes`
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
- Notes carry an optional `Learner Level` field so teachers can calibrate each note to the student audience for that class.
- On new notes, the learner level is prefilled from the teacher's profile but should be adjusted when the note is for a different grade level.
- Teacher helper text: `Prefilled from your profile. Adjust to match your students' grade level.`
- Study Pack generation and pre-generated Long Exam / Board Exam pools use the note's learner level when set, falling back to the teacher profile level only when the note has no override.

### View

- After generation, Note Detail shows:
  - primary: `View Quiz`
  - secondary: `Regenerate`
- Helper copy: `Already generated a quiz? Regenerate to create a new version (costs 1 credit).`
- `View Quiz` opens a dedicated Quiz Preview page
- Quiz Preview is read-only and uses the note title as the page heading under the `Quiz Preview` eyebrow.
- Quiz Preview shows:
  - `Generated Quiz - Ready for export`
  - question count
  - question text
  - choices
  - correct answer clearly highlighted
  - explanation visible

### Export

- Export belongs only inside Quiz Preview
- Place Export in the top-right of the Quiz Preview header
- Export is the only primary action in the Quiz Preview header
- Regenerate lives in the Quiz Preview overflow menu and still opens the confirmation flow
- Export uses stored `generatedQuiz` data only and must not call LLMs
- Export format for Teacher Flow is `DOCX`
- Export options:
  - `Quiz Only`
  - `Quiz + Answers`
- `Quiz + Answers` appends:
  - `Answer Key`
  - `Explanations`
- DOCX export is shown only for `Teacher` and `Admin`
- Teachers can also build one combined exam from `Library`:
  - enter teacher-only `Select` mode
  - choose multiple quiz-ready notes
  - organize notes into editable sections inside `Exam Builder`
  - reorder sections and move notes across sections using the drag handles
  - choose `Start Blank` for one `Untitled section`, or choose a structured preset
  - rebalance the pooled questions with either `Even Balance` (spreads questions equally across all sections) or `Smart Balance` (balances question counts and spreads topic diversity across sections, using each section's learning intent as a guide) without generating new questions
  - review the footer breakdown by section before export
  - keep note-level `Move up` / `Move down` controls as the accessibility fallback
  - export one combined DOCX with optional `Answer Key` and `Explanations`
  - combined DOCX export preserves the section titles, section order, and question-level balanced grouping chosen in the builder

## Upgrade Copy

- Teacher quiz-generation and export-limit upgrade CTAs use teacher-specific labels from `getUpgradeCtas`.
- Teacher export-limit copy frames the upgrade around unlimited DOCX quiz exports for class use.
- Student, Board Exam, and Professional paywall copy remains profile-specific to those flows.

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
