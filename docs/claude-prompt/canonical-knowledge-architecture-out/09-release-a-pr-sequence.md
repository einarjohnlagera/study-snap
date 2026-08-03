# Release A (`v0.69.0`) — PR sequence

Scoped 2026-08-03, after kickoff. Governs how `RELEASES.md` v0.69.0's Planned Scope is cut into PRs off `releases/v0.69.0`.

**Seven PRs, in two phases.** Phase 1 (three PRs) is the minimum that unblocks authoring canonical Algebra notes — that milestone is called out explicitly below, because it is the reason this release exists and it does not require the whole release to land.

---

## Two structural decisions this sequence rests on

### 1. The generation-context change is ONE unit, so `domain_context` and `learner_level` ship together

The obvious split — "one PR per new field" — is wrong here. Both fields flow through the *same* three places: `StudyPackGenerationContextResolver`, the `StudyPackGenerationContext` record, and `OpenAiLlmStudyPackService.buildGenerationContextBlock` (`:1529-1549`). Splitting by field means the second PR rewrites the first PR's code in all three files, with a guaranteed merge conflict and two rounds of review on the same logic.

Split by **layer** instead: schema/DTO surface, then behavior, then UI. Each layer is independently reviewable, and no file is rewritten by a later PR in the sequence.

### 2. `DomainContext` should be a Java enum, not a database table

Recommended, and it is a genuine design point rather than a convenience:

- **It mechanically enforces the governance rule.** ADR-001 states *"adding notes is authoring; adding a Domain Context is architecture."* An enum makes adding a value require a code change, review, and deploy — the governance rule stops being a doc anyone can quietly ignore and becomes a property of the system. A lookup table would let a curator INSERT a new value mid-authoring-session, which is precisely what the rule forbids.
- **It matches this codebase's convention exactly** — `NoteStatus`, `NoteVisibility`, `NoteTargetProfileType`, `ProfileType`, and `LearnerLevel` are all enums persisted as `VARCHAR` via `@Enumerated(EnumType.STRING)`.
- **Validation comes free**, closing `01` §6 item 3's note that this is the moment to stop accepting arbitrary strings.
- `NULL` remains meaningful and unaffected: it is the promotion marker for thin programs falling back to their program name (ADR-001).

Note the asymmetry with `course_programs`, which **is** a table — correctly, because programs are curator data that grows routinely, while Domain Contexts are architecture that should not.

---

## Phase 1 — unblock canonical authoring

### PR 1 — Schema, enum, and the DTO surface (no behavior change)

**Migration V102:** `notes.domain_context VARCHAR(64)` nullable, `notes.learner_level VARCHAR(32)` nullable.

New `DomainContext` enum with the eight ratified values (`ENGINEERING_MATHEMATICS`, `ENGINEERING_SCIENCES`, `CIVIL_ENGINEERING`, `PROFESSIONAL_PRACTICE_AND_REGULATION`, `GENERAL_EDUCATION`, `PROFESSIONAL_EDUCATION`, `NURSING`, `ACCOUNTANCY`) plus a display-label mapping in the style of `toLearnerLevelLabel` (`:1436-1447`).

`NoteEntity` fields; `UpsertNoteRequest` (+ validation); the read-side DTO and projection surface — `NoteResponse`, `NoteListItemResponse`, `NoteListItemView`, `NoteLibraryCandidateProjection`, `NoteCollectionItemResponse`, `NoteCollectionNoteProjection`, `PublicProfileNoteResponse`, `ContinueStudyingResponse`, `DataExportResponse`, and the aliases in `NoteLibraryRepositoryImpl` / `PublicLibraryRepositoryImpl` select lists. `NoteService` create/update persists both; the copy path (`:264`) carries both forward, per ADR-001 (copies inherit authoring axes, and would inherit no Applicable Programs — which do not exist yet).

**Deliberately not in this PR:** nothing reads either field for generation, no UI renders or sets them, no filter or facet changes.

**Why first:** roughly 25 DTO/projection types is a large mechanical diff. Isolating it keeps PR 2 — the only PR that can change generated output — small enough to review properly.

**Verification:** existing suites green; both columns null on every existing row; a create-then-read round-trip persists both; no response shape breaks for existing consumers.

### PR 2 — Resolver and prompt wiring (the load-bearing change)

`StudyPackGenerationContext` gains `domainContext` and `noteLearnerLevel`. `StudyPackGenerationContextResolver` implements ADR-001's fallback chains — `note.domainContext` → `note.courseProgram` → `user.courseProgram`, and `note.learnerLevel` → `user.learnerLevel` → `COLLEGE` (the existing `DEFAULT_LEARNER_LEVEL`, `:77`) — across all three entry points (`resolve`, `resolveForStudyPack`, `resolveForBulkGeneration`).

In `OpenAiLlmStudyPackService`: `buildGenerationContextBlock` (`:1529-1549`) emits the Domain constraint and content-calibration lines from Domain Context; `buildSubjectSuggestionGuidanceBlock` (`:1553-1564`); `isQuantitativeContext` (`:1568-1590`) reads Domain Context and **not** a program value; the `{COURSE_PROGRAM}` placeholder (`:649`) becomes `{DOMAIN_CONTEXT}` in `developer.txt`, `note-generation-developer.txt`, and `companion-developer.txt` (`:33`). Audit every per-mode `{mode}-developer.txt` / `{mode}-system.txt` pair for program or level references.

Static content takes Domain Context + Note Learner Level; quizzes take them as the floor with user level adjusting scaffolding only, never lowering the curriculum — ADR-001 rule 3, which needs a test asserting the invariant rather than only prose.

**This is the one PR that can silently degrade output quality (`01` risk R4), and tests cannot catch it.** Verification must include generating a real Study Pack for an existing Civil Engineering note under the new path and diffing it against its current output — plus one note whose Domain Context is deliberately broader (`Engineering Mathematics`) than the `Civil Engineering` it replaces, since a vaguer constraint is the specific regression risk. Heaviest `/audit-diff` of the release.

### PR 3 — Admin authoring UI and Bulk Generate

Two selects on note create/edit, visible to Teacher and Admin only, reusing the established role-conditional pattern already in `note-editor-form.tsx` for `targetProfileType` (`:373-386`). Bulk Generate exposes both (`BulkGenerateNotesRequest`, `NoteBulkGenerationService`, `BulkGenerationResultEntity`) — this matters more than it looks, because Bulk Generate is where per-program duplication would otherwise be industrialised.

Normal users see no new field; their notes resolve through the fallback chain.

> ### ✅ Milestone: canonical Algebra authoring is unblocked here
>
> After PR 3, the Engineering Mathematics subject plan can be populated: author each Algebra topic note once with `domain_context = ENGINEERING_MATHEMATICS`, and add it to every engineering Review Set's Engineering Mathematics plan. Review Sets already compose by explicit reference, so **no join table is required for this** — Applicable Programs (Release B) adds discovery and filtering, not the ability to reuse. **Phase 2 can proceed in parallel with curriculum authoring.**

---

## Phase 2 — correctness and cleanup

### PR 4 — Backfill the level-in-program notes

**The audit's "49 notes" splits into two groups with different treatments — this is a refinement of the earlier spec, found while checking the `LearnerLevel` enum:**

| Current `course_program` | Notes | `learner_level` | `course_program` after | `domain_context` |
|---|---|---|---|---|
| `Grade School` | 3 | `GRADE_SCHOOL` | **cleared** | `GENERAL_EDUCATION` |
| `Junior High` | 24 | `JUNIOR_HIGH` | **cleared** | `GENERAL_EDUCATION` |
| `High School` | 11 | see decision below | **cleared** | `GENERAL_EDUCATION` |
| `Senior High – STEM` | 4 | `SENIOR_HIGH` | **kept** | `GENERAL_EDUCATION` |
| `Senior High – ABM` | 4 | `SENIOR_HIGH` | **kept** | `GENERAL_EDUCATION` |
| `Senior High – HUMSS` | 3 | `SENIOR_HIGH` | **kept** | `GENERAL_EDUCATION` |

**38 notes are pure levels** and lose their `course_program` entirely. **11 are not** — STEM, ABM, and HUMSS are genuine Senior High *strands*, so they keep their program identity and *gain* a level. Nothing is lost, which the original flat "move 49 notes" framing would have discarded.

**One open decision:** `High School` (11 notes) is ambiguous — the enum offers `JUNIOR_HIGH` and `SENIOR_HIGH` with no generic value. Either inspect those 11 notes' content or default to `SENIOR_HIGH`. Needs an answer before the prompt is written; it is a one-line difference in the migration and a real content question.

All 49 are in zero collections, so blast radius is nil.

### PR 5 — `course_programs` catalog and `program_families`

Tables plus nullable FK on `notes` and `users`, populated by mapping; **nothing reads the FK.** Seed from the 32 audited values, applying the ~6 semantic decisions in `08` (`Bsed`→Education; Computer Science / IT / Software Engineering all survive as programs; `Engineering` is a family, not a program; `Biology` is a subject; `Civil Service`, `Professional / Board Exam Review`, and `Self Study / Personal Learning` are goals and do not become programs). Unmappable values keep a NULL FK and their original string rather than being guessed.

`ExamGoalConfig` and `frontend/lib/exam-hub-config.ts` move from hardcoded name lists to catalog references, retiring their hand-sync comment and the documented en-dash fragility. `users.course_program` stays free text with an optional FK — do not make the user side a hard reference (`01` §2.8; 40% of accounts have incomplete onboarding fields).

### PR 6 — Question pool and bank re-keying

`ExamQuestionPoolService.sameLearnerLevel()` (`:368-374`, called at `:101`) compares the **note's** level; `exam_question_pool.learner_level` and `challenge_quiz_question_bank.learner_level` are written from the note's level (`:166`, `:174`, `:235`); `OfficialChallengeQuizTemplateService` (`:179`, `:247`) stamps the same. Note `idx_challenge_quiz_question_bank_claimable` includes `learner_level`.

**Open decision, must be explicit in the prompt:** existing rows hold a *user's* level. Either set them NULL (accepting one regeneration per pool) or force a single invalidation pass. Silently reinterpreting the column's meaning is the one unacceptable option.

### PR 7 — Subject-equals-context nudge

Admin-side warning when a note's `subject` equals its `domain_context`, surfacing that the subject is probably too broad — a nudge, never a validation error. Targets the ~334 notes already violating `buildSubjectSuggestionGuidanceBlock`'s own rule.

**Frontend-only and well under ~50 LOC, so per `CLAUDE.md`'s task-routing table this is Claude Code inline, not a Codex prompt.**

---

## Sequencing notes

**Dependencies:** PR 2 needs PR 1. PR 3 needs PR 1 (DTOs) and is only meaningful after PR 2. PR 4 needs PR 1. PR 6 needs PR 1 and PR 2. **PR 5 is fully independent** and can land any time — including first, if the catalog seed work happens to be ready. PR 7 needs PR 3.

**Every PR that changes behavior updates its feature docs in the same PR** — `docs/features/notes.md` (the four axes and the reuse-vs-new-note rule verbatim), `study-pack-generation.md` (ADR-001's resolution rules), `bulk-generation.md`, `admin-dashboard.md`, plus `challenge-quiz.md` / `exam-hub.md` / `quiz.md` for PR 6. `CLAUDE.md`'s "Generation context is resolved in a shared utility" paragraph and `docs/architecture/DATA_MODEL.md` both need updating in PR 2. Per `CLAUDE.md`, updating `RELEASES.md` alone is not enough.

**Migration numbers:** V102 (PR 1), then V103/V104 as PRs 4 and 5 land — assign at write time, not now, and derive the max numerically (`ls … | sed 's/^V\([0-9]*\)__.*/\1/' | sort -n | tail -1`); a lexical `ls` reports V99 when the real max is V101.

**`/audit-diff` after every Codex delivery**, heaviest on PR 2.

**Pre-signoff pressure test:** this release meets the full-pressure-test bar in `CLAUDE.md` on two counts — one concept touching 3+ surfaces, and more than one PR touching the same shared method (`buildGenerationContextBlock` in PR 2, the resolver in PRs 2 and 6). Budget for it.

## Open decisions before the first Codex prompt

1. **`High School` (11 notes) → `JUNIOR_HIGH` or `SENIOR_HIGH`?** (PR 4)
2. **Existing pool/bank rows: NULL them, or force one invalidation pass?** (PR 6)
3. **Confirm `DomainContext` as a Java enum** rather than a table (this document's recommendation).

None blocks PR 1.
