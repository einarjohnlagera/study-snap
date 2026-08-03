# DRAFT ADR — Canonical Knowledge Architecture

**This is a draft. It documents a decision that has NOT been ratified.**

On ratification, move this file to `docs/architecture/ADR-001-canonical-knowledge-architecture.md`, alongside the existing `ARCHITECTURE.md`, `DATA_MODEL.md`, and `BILLING_ADDENDUM.md`. No `docs/adr/` directory exists today; creating a new top-level directory for one record is not worth it — `docs/architecture/ADR-NNN-*.md` keeps architecture records where architecture already lives, and `ADR-001` establishes the numbering convention for later ones.

Delete the "Status: Proposed" line and set a decision date when it moves.

---

## ADR-001 — Notes model canonical knowledge; programs describe applicability

**Status:** Proposed (drafted 2026-08-03, not ratified)
**Deciders:** Owner
**Open naming decision, must be settled before Release A's migration:** this record calls the axis **Content Context** (`notes.content_context`). `Domain Context` is the recommended alternative — the field's one job in code is to feed the prompt line reading *"treat the {value} above as the authoritative academic **domain**,"* so `domain_context` makes its responsibility self-evident to the next engineer, whereas `content_context` is mildly circular (all note metadata is context about content). `Authoring Context` is the better name for curators specifically; `Academic Context` is weakest, since it excludes the professional and licensure content that is most of this library. Low-stakes either way, and **free to change only until Release A ships.** The learner-facing concern does not bind — a badge displays the value ("Engineering Mathematics"), never the field name.
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

Delivered in two releases (revised 2026-08-03 after the production audit): **Release A** = Content Context + `course_programs` catalog/families + Note Learner Level, all additive and reversible, with pool/bank re-keying as its own PR inside it. **Release B** = `note_course_program` + read paths, multi-release and not reversible.

### Choosing a Content Context value

> **Rule:** Content Context is the coarsest label under which the note's treatment is identical.
>
> **Test (binary):** would a student in a sibling program be served by this exact note, unchanged? Yes → the shared bundle. No → the program name.
>
> **Tiebreaker, only when the test is genuinely unclear:** compare learning objectives, terminology, examples, and depth; the first material difference decides.
>
> **Empirical tie-break of last resort:** because this value is substituted into the generation prompt, generate the note under both candidate values and compare the output. For a small team this is faster and more decisive than adjudicating definitions.

A longer rule enumerating four conjunctive criteria was considered and rejected — it invites more curator disagreement, not less, by creating four axes to argue over instead of one judgment to make.

**Names are derived from real curriculum vocabulary — board exam subject areas and recognized curriculum bundles — never invented.** Two candidate values that were invented rather than borrowed (`Health Sciences Foundation`, `Computing`) failed both the learner-comprehension test and the governance rule below, independently. That correlation is the rule's justification: an invented name usually signals there is no real shared body of knowledge behind it.

### Ratified value set (8, as of Release A)

`Engineering Mathematics` · `Engineering Sciences` · `Civil Engineering` · `Professional Practice & Regulation` · `General Education` · `Professional Education` · `Nursing` · `Accountancy`.

`Professional Practice & Regulation` is justified on **treatment** — legal, procedural, and regulatory rather than scientific — across 68 existing notes. Its *applicability* across engineering boards is a separate and unverified question, like `Engineering Sciences`'.

`Architecture` is an open owner call — its five large subject plans are Architecture-specific, but with `Professional Practice & Regulation` and `Construction Materials` carved out it may be thin enough to start as a fallback.

**Applicability defaults for these values are NOT yet verified against current PRC board syllabi** and must be curator-checked before family-expansion defaults are set. Whether `Engineering Sciences` spans 8 or 11 engineering programs is a curriculum fact, not an architecture decision.

### Content Context governance

> A new Content Context value may be introduced only when **both** hold: (a) there is a sustained body of canonical knowledge — as a concrete floor, **~10 or more notes already authored or firmly planned** — whose treatment cannot be accurately represented by an existing value; and (b) an explicit owner decision is recorded in this ADR's revision log. **When in doubt, reuse an existing Content Context.**
>
> **Failure condition, reviewed at every `/kickoff`:** if the number of Content Context values ever approaches the number of course programs, the taxonomy has failed and has collapsed back into the free-text field it replaced. Baseline at ratification: **8 contexts against 27+ programs.** A ratio trending toward 1:1 is the signal to stop and consolidate, not to keep adding.

### Program-name fallback is a transitional state, not the end state

Thin programs (1–7 notes: Law, Medicine, Criminology, Psychology, Aviation, Business Administration, Physical Therapy, Civil Service, and initially Pharmacy and Information Technology) use their **program name** as the effective authoring context via the resolver's fallback chain. This is pragmatic, not desired.

**It is expressed mechanically rather than only documented, so it cannot be mistaken for the end state:** these values are **not** added to the curated `content_contexts` set, so `content_context IS NULL` *is* the marker of "not yet promoted," and the promotion backlog is a one-line query grouping null-context notes by `course_program`. A curator can see at any time which programs have crossed the ~10-note governance floor. Prose intent decays; a queryable state does not.

### Subject / Content Context collision

Subject and Content Context are different axes, and a value legitimately appearing in both is not an error — a broad survey note about nursing really is `subject = Nursing`. But ~334 notes currently carry a program name as their subject (`Professional Education` 250, `Nursing` 62, `Engineering Mathematics` 10, `Accountancy` 8, `Architecture` 3), which already violates `buildSubjectSuggestionGuidanceBlock`'s existing rule against overly broad subjects and echoing the program name.

**Guard:** when a note's `subject` equals its `content_context`, surface an admin-side warning that the subject is probably too broad — a nudge, never a hard validation error. Content Context values are not renamed to dodge the collision; borrowed board-subject-area names are precisely why the vocabulary reads well to learners.

### Alternatives considered

- **Inverse mapping (program → content contexts), notes stay single-valued.** Far cheaper — dozens of rows, and already the shape `ExamGoalConfig` uses. Rejected: it breaks as soon as applicability is per-note rather than per-context (Engineering Algebra applies to eleven programs, Engineering Statics to nine), forcing a sparse override table plus a second mechanism, and it makes applicability implicit when it should be explicit. **Retired as a fallback 2026-08-03, on evidence rather than argument:** Query K showed sharing is ragged *and crosses program families* — `Construction Materials` is shared by Civil Engineering and Architecture, while `Engineering Sciences` subjects are shared by differing subsets (Strength of Materials broadly, Hydraulics narrowly). Under ragged cross-family sharing this alternative needs the per-note override table immediately, making it `note_course_program` with extra steps. Not a viable fallback at any cost level.
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

**Evidence base (production, 2026-08-03).** This ADR is not taken on forecast. The vocabulary audit (`03`/`04-vocabulary-followups.sql`, results in `05-vocabulary-results.md`) established:

- The program field already conflates **four different kinds of thing** — degree program, learner level (49 notes hold a K-12 grade level), program family (`Engineering`), subject (`Biology`), and activity (`Civil Service`, `Professional / Board Exam Review`, `Self Study / Personal Learning`). This is the empirical case for the four-axis split.
- **Cross-program duplication has already begun**, at n=2 engineering programs: `Strength of Materials` carries "Stress and Strain in Strength of Materials" (Civil Engineering) and "Stress, Strain, and Material Strength" (Mechanical Engineering) — the same knowledge as two notes. Nine further Civil Engineering SoM notes are queued to need a twin per additional engineering program.
- An exact-title duplicate-content ratio of **0.00% across 886 official public notes**, which the above proves is **too weak a measure to trust** — it cannot see semantic duplication. Do not cite it as evidence of no duplication.
- The authoring rule discriminates correctly rather than over-collapsing: Nursing-Pharmacology's 15 notes are genuinely nursing-framed ("Medication Administration Rights in Nursing", "Safe Medication Practices in Nursing") against Pharmacy's generic "Antibiotics: Mechanism of Action and Resistance" — separate notes are right there, and rule 6 says so.

An earlier draft proposed falsifying this ADR via a `[CHECKPOINT — due 2027-02-01]` on whether cross-program reuse ever materialized. **That checkpoint is retired: it was answered affirmatively on 2026-08-03, before it was needed.**
