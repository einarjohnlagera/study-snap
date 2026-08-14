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
- Question count selector: teachers choose `10`, `20`, or `30` before generation.
- Free Teacher stays fixed at `10`; Plus and Pro Teacher unlock `20` and `30`, with the paywall explaining that Plus unlocks longer teacher quizzes.
- Helper copy: `Generate a quiz from this note with answers and explanations for review and export.`
- Selector helper copy: `Choose how many questions to generate. Higher counts cover more material.`
- Generation consumes `1` quiz credit
- Quiz generation is based on the note itself, not on student quiz sessions
- Notes may store an optional authored learner level. Teacher/Admin Note Editor, Bulk Generate, and Note Detail's inline metadata panel all expose Domain Context and `Authored Depth` (the control label for the `notes.learner_level` axis) as optional authoring metadata. The inline panel is what makes both axes correctable after a Study Pack exists; the correction shapes the next generation only.
- Teacher Generate Quiz includes a required per-invocation `Target Level` picker so one note can produce quizzes for different classes without copying or rewriting the note.
- The picker pre-fills from the most recent Target Level used to generate a quiz on that note, falling back to the teacher's profile learner level when the note has no prior target.
- The selected `Target Level` is an explicit authoring act for that quiz generation. It replaces the curriculum-level slot even when the note has its own authored level, and it does not generate reader-scaffolding guidance. It is not stored on the note; generated quiz history retains the target only so the next Generate Quiz modal can prefill the latest value.
- **A stored `targetLearnerLevel` of NULL means "the teacher never chose one," and that state is load-bearing.** Only an explicit Target Level is persisted — the resolved curriculum level is never written in its place. `findByNoteIdAndTargetLearnerLevelIsNotNullOrderByGeneratedAtDesc` exists precisely to encode it, and the picker's fallback in the rule above depends on it. Writing a resolved level here floors it to `COLLEGE`, which makes the modal pre-fill a level nobody selected and locks every later generation to it.

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
- The shared teacher DOCX export modal includes a `Versions` selector for `1`, `2`, or `3` printable versions:
  - Free Teachers stay on `1`; selecting `2` or `3` opens the Plus paywall for multiple anti-cheating versions
  - Plus and Pro Teachers may export `2` or `3` versions from Quiz Preview and combined Exam Builder export
  - multi-version exports keep the same stored `generatedQuiz` content, deterministically shuffle question order and choice order per `Version A`, `Version B`, and `Version C`, and keep each shuffled answer key aligned to its rendered choices
  - Exam Builder preserves its selected sections while shuffling questions inside each section for each version
- Teacher DOCX export details can add a local header without changing stored `generatedQuiz` data:
  - `schoolName` comes from Teacher Profile `Teaching Info` and is the default first header line when set
  - per-export `Class or section` text is optional and stays local to the open export modal
  - `Include today's date in the header` defaults on for each export and can be toggled off
  - the same export-details controls apply to Quiz Preview DOCX export and combined Exam Builder DOCX export
- `Quiz + Answers` appends:
  - `Answer Key`
  - `Explanations`
- In a multi-version `Quiz + Answers` export, the answer key and explanations follow the matching version before the next version begins on a page break.
- DOCX export is shown only for `Teacher` and `Admin`
- Teachers can also build one combined exam from `Library`:
  - enter teacher-only `Select` mode
  - choose multiple quiz-ready notes
  - organize notes into editable sections inside `Exam Builder`
  - add forgotten quiz-ready notes from inside `Exam Builder` and place them directly into a target section
  - reorder sections and move notes across sections using the drag handles
  - choose `Start Blank` for one `Untitled section`, or choose a structured preset
  - rebalance the pooled questions with either `Even Balance` (spreads questions equally across all sections) or `Smart Balance` (balances question counts and spreads topic diversity across sections, using each section's learning intent as a guide) without generating new questions
  - review the footer breakdown by section before export
  - keep note-level `Move up` / `Move down` controls as the accessibility fallback
  - export one combined DOCX with optional `Answer Key` and `Explanations`
  - combined DOCX export preserves the section titles, section order, and question-level balanced grouping chosen in the builder
- Teachers can also open Exam Builder from a Lesson Plan collection:
  - the terminal CTA passes `collectionId` so Exam Builder reloads the owned collection on direct load or refresh
  - each distinct trimmed item label becomes an initial section containing that label's quiz-ready notes in collection order
  - unlabeled quiz-ready notes are placed in one trailing default section; labels containing only non-quiz-ready notes are skipped
  - collection notes without a generated quiz are not dropped silently: an amber `N of M notes excluded — no quiz generated yet` notice lists those note titles so the teacher knows exactly which notes still need a quiz
  - the seeded sections remain fully editable and continue through the existing combined DOCX and shareable-quiz workflow
  - this is frontend structure reuse only; it does not generate questions, call an LLM, or widen Teacher/Admin export access

## Plan Accessibility

`docs/product/PLANS.md` is the source of truth for the Teacher Profile DOCX Export Override.

- Teacher Flow DOCX exports are profile-aware: Free Teachers get `10` DOCX exports per month, Plus Teachers get unlimited DOCX exports, and Pro remains unlimited.
- The override is DOCX-only because DOCX export is the terminal Teacher Flow action and uses stored `generatedQuiz` data without LLM cost.
- PDF export limits stay on the standard plan quota for every profile.
- Settings -> Plan & Billing must show resolved DOCX and PDF export limits separately so teachers can see the override without client-side quota recomputation.

## Upgrade Copy

- Teacher quiz-generation and export-limit upgrade CTAs use teacher-specific labels from `getUpgradeCtas`.
- Teacher export-limit copy frames the upgrade around DOCX quiz export headroom for class use.
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
