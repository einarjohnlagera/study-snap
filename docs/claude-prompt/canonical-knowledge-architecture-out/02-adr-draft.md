# DRAFT ADR — Canonical Knowledge Architecture

**This is a draft. It documents a decision that has NOT been ratified.**

On ratification, move this file to `docs/architecture/ADR-001-canonical-knowledge-architecture.md`, alongside the existing `ARCHITECTURE.md`, `DATA_MODEL.md`, and `BILLING_ADDENDUM.md`. No `docs/adr/` directory exists today; creating a new top-level directory for one record is not worth it — `docs/architecture/ADR-NNN-*.md` keeps architecture records where architecture already lives, and `ADR-001` establishes the numbering convention for later ones.

Delete the "Status: Proposed" line and set a decision date when it moves.

---

## ADR-001 — Notes model canonical knowledge; programs describe applicability

**Status:** Proposed (drafted 2026-08-03, not ratified)
**Deciders:** Owner
**Supersedes:** the implicit single-program assumption introduced by `V39__note_course_program.sql` (added 2026-04-04 — a plain `VARCHAR(120)` column on `notes`, despite the file name)
**Related, not superseded:** Company Redefinition Phase 3b (cross-user question pooling) — adjacent and frequently confused; see Consequences.

### Context

`notes.course_program` is a single free-text `VARCHAR(120)` column that carries five unrelated responsibilities at once:

1. the LLM's authoritative academic domain constraint (`OpenAiLlmStudyPackService:1535-1542`)
2. the private Library filter facet and its facet counts (`NoteLibraryRepositoryImpl:277-279`, `:189-195`)
3. the Public Library filter and free-text search predicate (`PublicLibraryRepositoryImpl:198-200`, `:235`)
4. the Exam Hub mapping key (`ExamGoalConfig` ↔ `frontend/lib/exam-hub-config.ts`)
5. the note card badge

Those five responsibilities want different cardinality. Responsibility 1 requires exactly one value — the instruction it emits ("treat the course/program above as the authoritative academic domain … Do not blend in material from unrelated disciplines") is logically unsatisfiable with more than one. Responsibilities 2–5 want many.

This was invisible while the Official Library covered four programs (ALE/PNLE/LET/CPALE) with little shared foundational content. Expanding into Civil Engineering exposed it: a single Algebra subject is applicable to eleven Philippine engineering programs, and under the current model that means eleven notes, eleven Study Packs, eleven question pools, eleven flashcard sets, eleven memorization decks, eleven public copies, and eleven maintenance obligations for identical knowledge. The architecture, not curator capacity, became the constraint on shipping comprehensive Official Review Sets.

### Decision

Notes model **canonical knowledge**. Programs describe **where that knowledge is applicable**. These are separate axes, each with exactly one owner:

| Axis | Field | Cardinality | Sole responsibility |
|---|---|---|---|
| Subject | `notes.subject` | 1 | what the note is about |
| Content Context | `notes.content_context` | 1 | **how** it is authored — the LLM domain constraint |
| Note Learner Level | `notes.learner_level` | 1 | **how deep** it is authored |
| Applicable Programs | `note_course_program` | N | **where** it appears — discovery only |
| Audience framing | `notes.target_profile_type` | 1 | **who** it is written for — never depth |

Binding rules:

1. **Applicable Programs never reach a prompt.** Generation is driven by Content Context. Programs are a discovery and curriculum-management facet only.
2. **Static content** (note body, summary, key concepts, flashcards, memorization, static question pool) is calibrated by Content Context + Note Learner Level. Never by user learner level.
3. **Quizzes and exams** take Content Context + Note Learner Level as the floor. User Learner Level may adjust scaffolding and wording; it may **never lower the curriculum**.
4. **Review Sets compose freely.** A Review Set may contain any note regardless of its Applicable Programs. A Review Set's own course/program is a curation label — never derived from, never validated against, its notes.
5. **Program Families are an authoring shortcut only.** Selecting a family expands to explicit `note_course_program` rows at save time. Applicability is never inferred from a family at read time.
6. **Reuse a note when the learning objective, depth, and treatment are materially the same. Create a new note only when the learning experience itself genuinely differs.** Multiple canonical Algebra notes (Engineering Algebra, Business Algebra, College Algebra, Algebra Foundations) are correct and expected; collapsing them would be a misreading of this ADR.
7. All resolution stays in `StudyPackGenerationContextResolver`. No service reads these fields directly.

Delivered as four independently-gated steps, in this order: (1) Content Context, (2) `course_programs` catalog + families, (3) `note_course_program` + read paths, (4) Note Learner Level. Steps 1, 2, and 4 are additive and reversible. Step 3 is not.

### Alternatives considered

- **Inverse mapping (program → content contexts), notes stay single-valued.** Far cheaper — dozens of rows, and already the shape `ExamGoalConfig` uses. Rejected: it breaks as soon as applicability is per-note rather than per-context (Engineering Algebra applies to eleven programs, Engineering Statics to nine), forcing a sparse override table plus a second mechanism, and it makes applicability implicit when it should be explicit. Retained as a documented fallback if step 3's cost proves prohibitive.
- **Reuse `notes.tags` for applicability.** Rejected: tags are user-authored and LLM-suggested with no catalog — this returns curation metadata to the free-text soup this ADR exists to escape.
- **Duplicate content per program.** Rejected on compounding maintenance cost, acknowledging that the duplication is currently forward-looking rather than measured.
- **A separate "Academic Level" field distinct from Learner Level.** Rejected: Learner Level with two scopes (user's own vs. the note's) expresses the same thing without a third vocabulary.

### Consequences

**Enabled.** One canonical note serves many programs. Comprehensive Official Review Sets become authorable without duplication — and, because Review Sets already compose notes by explicit reference, this benefit arrives with **step 1 alone**, before the many-to-many exists. Retrofitting LET/PNLE/CPALE/ALE with shared General Education content becomes additive. `ExamGoalConfig`'s hand-synced program-name lists and their documented en-dash fragility retire at step 2.

**Costs and limits.**

- Step 3 is **not reversible** once filters and badges read the join; rollback would require a migration. This knowingly fails clause 2 of the ROADMAP's bootstrap test and is accepted as a multi-release commitment.
- The program vocabulary is currently unvalidated free text that an LLM suggestion can write into (`StudyPackService:746`). Step 2 is vocabulary reconciliation with editorial judgment calls, not a mechanical backfill.
- Rule 2 **reverses** an explicit current instruction (`OpenAiLlmStudyPackService:1540-1541`: "Do not use learner level to calibrate static note or Study Pack content"). Every Study Pack generated before step 4 carries no level signal at all; that gap closes only by regeneration, which is confirmation-gated per `CLAUDE.md`. Pre-existing packs remain level-signal-free, and this is documented rather than hidden.
- `exam_question_pool.learner_level` and `challenge_quiz_question_bank.learner_level` key on **user** level today, and `ExamQuestionPoolService.sameLearnerLevel()` invalidates a pool on user-level mismatch. Step 4 re-keys both to note level and must decide explicitly what happens to existing rows.
- Library and Explore program facet counts will sum above the note total, because a note appears under every applicable program. This is correct and needs a UI affordance, not a fix.
- Filter and search paths move to join/`EXISTS` semantics on a hot paginated path that already required a dedicated performance release (`v0.51.0`).
- **This ADR does not make generated artifacts canonical.** Study Packs, question pools, and question banks remain per-`study_pack_id`, and adopted copies get their own. Canonical *knowledge* is not canonical *generation*. Cross-user pooling is Company Redefinition Phase 3b and stays separately gated.
- Note cards display Content Context as their single badge. Applicable Programs surface only on note detail, as a collapsed disclosure.
- `notes.target_profile_type` survives this ADR unchanged. It is `NOT NULL` and is a **live Public Library audience filter** (`PublicLibraryRepositoryImpl:176-178`, `NoteController:594`/`:636`, `NoteRepository:106`/`:131`), so it sits on the discovery axis alongside Applicable Programs rather than conflicting with Note Learner Level. Whether the precise program facet makes this coarse three-value facet redundant is judged at the **end of step 3**, against real filter usage — not decided here.

**Falsification.** If, six months after step 1 ships, no note is being reused across two or more programs' Review Sets, the duplication problem was hypothetical and step 3 should not be built — Content Context alone was the answer. Tracked as `[CHECKPOINT — due 2027-02-01]` in the ROADMAP Backlog Index.
