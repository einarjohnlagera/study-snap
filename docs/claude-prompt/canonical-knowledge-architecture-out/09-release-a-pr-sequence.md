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

In `OpenAiLlmStudyPackService`: `buildGenerationContextBlock` (`:1529-1549`) emits the Domain constraint and content-calibration lines from Domain Context; `buildSubjectSuggestionGuidanceBlock` (`:1553-1564`); `isQuantitativeContext` (`:1568-1590`) reads Domain Context and **not** a program value.

> **Correction (2026-08-03, while writing the PR 2 prompt).** An earlier version of this line said the `{COURSE_PROGRAM}` placeholder at `:649` should be renamed to `{DOMAIN_CONTEXT}` across `developer.txt`, `note-generation-developer.txt`, and `companion-developer.txt`. **That was wrong on both counts and would have violated ADR-001.**
>
> There is exactly **one** `{COURSE_PROGRAM}` placeholder in the codebase — `companion-developer.txt:33` — and it sits in `buildCompanionDeveloperPrompt(CompanionGenerationContext …)`, so its value is a **Review Set's** `course_program`, not a note's. ADR-001 rule 4 makes that a **curation label**; renaming it would reclassify it as an authoring domain. **The Companion path is out of scope for PR 2 entirely.**
>
> `developer.txt` and `note-generation-developer.txt` contain no placeholder at all — note and Study Pack context reaches the model as Java-assembled text from `buildLearnerContextBlock` / `buildContentContextBlock`. What those two templates *do* need is a **prose** fix: both currently instruct *"Treat Course / Program as the shared academic level **and** domain signal (for example, Junior High, Senior High - STEM, Nursing, or Civil Engineering)"* — one field read as two axes, with examples mixing levels and programs. That sentence is the textual twin of the schema defect this ADR fixes, and splitting it is PR 2's real template work.

Audit the other per-mode `{mode}-developer.txt` / `{mode}-system.txt` pairs for wording that contradicts the split — `interview-practice`, `adaptive-practice`, `teacher-quiz`, `challenge-quiz`, `long-exam`, `board-exam` developers plus `long-exam-system.txt` all reference course/program or learner level. Minimum wording edits only; no restructuring.

Static content takes Domain Context + Note Learner Level; quizzes take them as the floor with user level adjusting scaffolding only, never lowering the curriculum — ADR-001 rule 3, which needs a test asserting the invariant rather than only prose.

> **⚠️ STATUS 2026-08-03: PR 2 is merged, but the R4 verification below was NOT performed and is still owed.** It is an owner action through the UI, not something the PR could self-check. Full step-by-step, and why it should happen before PRs 4–7 rather than after, is in `RELEASES.md` v0.69.0 under **"Verification owed."** Do not read the paragraph below as a completed step just because the PR merged.

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

**`High School` — DECIDED 2026-08-03 (ADR-001 "Legacy-data policy"). No blanket mapping.** The legacy label is strictly less precise than the `JUNIOR_HIGH`/`SENIOR_HIGH` distinction replacing it, so it is resolved **per note, from actual curriculum and content** — never inferred from the old label, and **no `HIGH_SCHOOL` enum value** may be added to preserve the ambiguity.

This changes PR 4's shape materially: **it is not a pure SQL `UPDATE … WHERE course_program = 'High School'`.** It needs a human review pass over those 11 notes producing an explicit note-ID → level mapping, which the migration then applies. Sequence it as:

1. A read-only query listing the 11 notes (id, title, subject, content excerpt) for admin review.
2. The owner/curator classifies each as `JUNIOR_HIGH` or `SENIOR_HIGH`, or marks it unclassifiable.
3. The migration applies that explicit mapping — classified notes get their level and have `course_program` cleared; **unclassifiable notes keep `learner_level` NULL and retain `course_program`**, so they are never left with no classification at all, and remain flagged for admin review.

**Do not flip `visibility`.** All 11 are live official public notes; ADR-001 interprets the ratifying instruction's "unpublished" conservatively as *leave unclassified* and explicitly does not authorize withdrawing published content.

The other 38 notes (`Grade School`, `Junior High`, and the three Senior High strands) are unambiguous and migrate mechanically.

All 49 are in zero collections, so blast radius is nil.

### PR 5 — `course_programs` catalog and `program_families`

Tables plus nullable FK on `notes` and `users`, populated by mapping; **nothing reads the FK.** Seed from the 32 audited values, applying the ~6 semantic decisions in `08` (`Bsed`→Education; Computer Science / IT / Software Engineering all survive as programs; `Engineering` is a family, not a program; `Biology` is a subject; `Civil Service`, `Professional / Board Exam Review`, and `Self Study / Personal Learning` are goals and do not become programs). Unmappable values keep a NULL FK and their original string rather than being guessed.

`ExamGoalConfig` and `frontend/lib/exam-hub-config.ts` move from hardcoded name lists to catalog references, retiring their hand-sync comment and the documented en-dash fragility. `users.course_program` stays free text with an optional FK — do not make the user side a hard reference (`01` §2.8; 40% of accounts have incomplete onboarding fields).

### PR 6 — Question pool and bank re-keying

`ExamQuestionPoolService.sameLearnerLevel()` (`:368-374`, called at `:101`) compares the **note's** level; `exam_question_pool.learner_level` and `challenge_quiz_question_bank.learner_level` are written from the note's level (`:166`, `:174`, `:235`); `OfficialChallengeQuizTemplateService` (`:179`, `:247`) stamps the same. Note `idx_challenge_quiz_question_bank_claimable` includes `learner_level`.

**Existing rows — DECIDED 2026-08-03 (ADR-001 "Legacy-data policy" rule 2). Neither of the two options originally offered was chosen; the ratified policy is *preserve and narrow-scope*:**

> Preserve existing assets, but do not expand their semantic reach until their compatibility has been deliberately reclassified.

- Existing questions **stay reusable for their original source Note** — no behavior is removed, nothing is nulled out wholesale, nothing is invalidated en masse.
- Domain Context is backfilled onto those rows **from the source Note, only where the mapping is deterministic.**
- Legacy `course_program` is **not** evidence of cross-program reusability. A question generated when a note belonged to one program carries no warrant for the ten programs it may later be applicable to.
- Legacy rows remain **source-note-scoped** and must not enter cross-note or cross-program sharing until a later PR explicitly re-keys and audits compatibility.
- Rows whose source Note has no confidently resolved Domain Context stay usable **only through their existing narrow path**, or are excluded from shared retrieval. **Never deleted.**
- **No destructive regeneration, no bulk retirement.**

**Scope consequence for this PR:** the policy requires pool and bank rows to carry a Domain Context, which neither table has today. Two mechanisms are viable and this is a PR-6 implementation choice, not a reopened decision:

- **Denormalize** `domain_context` onto both tables, matching how `learner_level` is already denormalized (and already part of `idx_challenge_quiz_question_bank_claimable`). Consistent with the existing pattern; admits staleness if a note's context later changes.
- **Resolve by join** to the source note at read time. Always correct; adds a join to a claim path that is latency-sensitive.

The existing-pattern precedent favours denormalizing, but the staleness question needs an answer either way — if a note's Domain Context is later corrected, a denormalized pool row silently keeps the old one, which is exactly the "semantic reach widened without deliberate reclassification" failure the policy exists to prevent. Whichever is chosen, state the staleness behavior explicitly.

**Do not read the policy as disabling `v0.60.0`'s Official template sharing.** `OfficialChallengeQuizTemplateService` shares cross-*user* but same-source-*note* (an adopter's copies trace back via `copiedFromNoteId`), so it is already source-note-scoped and compliant. What the policy blocks is *new* cross-program pooling.

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

## Decisions — all closed 2026-08-03

1. **`High School` (11 notes)** — **no blanket mapping.** Per-note classification from actual content; unclassifiable notes stay unclassified and keep `course_program`; no `HIGH_SCHOOL` enum value. Recorded in ADR-001 "Legacy-data policy" rule 1. See PR 4 above for the three-step shape this forces.
2. **Existing pool/bank rows** — **preserve and narrow-scope**, not NULL-them and not force-invalidate. Recorded in ADR-001 rule 2. See PR 6 above, including the one remaining *implementation* choice (denormalize vs. join) and its staleness question.
3. **`DomainContext` as a Java enum** — confirmed, and already implemented in PR 1.

**Do not reopen 1 or 2 in a later PR.** Both are recorded in ADR-001 precisely so a future session scoping PR 4 or PR 6 inherits them rather than re-deriving them.

One item remains genuinely open and is *not* a decision — a verification task: **the applicability groupings in `06`/`08` are unverified against current PRC board syllabi.** That only affects Release B's family-expansion defaults, so it does not block any PR in this release.
