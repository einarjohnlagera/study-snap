# ADR-001 — Notes model canonical knowledge; programs describe applicability

**Status:** **Accepted** — ratified by the owner 2026-08-03. Drafted, audited against production, and revised the same day; amended 2026-08-16 to ratify the four-axis model and gated Target Audience retirement.
**Deciders:** Owner
**First ADR in this repo.** `docs/adr/` was deliberately not created; `docs/architecture/ADR-NNN-*.md` keeps architecture records alongside the existing `ARCHITECTURE.md`, `DATA_MODEL.md`, and `BILLING_ADDENDUM.md`, and establishes the numbering convention for later records.
**Naming — decided 2026-08-03.** The axis is **Domain Context** (`notes.domain_context`). Chosen over `Content Context` (the original working name), `Authoring Context`, and `Academic Context`. Two reasons: the field's one job in code is to feed the prompt line reading *"treat the {value} above as the authoritative academic **domain**,"* so the name states its responsibility; and `content_context` would have collided semantically with the **pre-existing** `OpenAiLlmStudyPackService.buildContentContextBlock` (`:1523`), which already means something different — the static-content variant of the generation context block, as opposed to `buildLearnerContextBlock`. A field and an unrelated method sharing a name in the same class is exactly the kind of ambiguity that produces anti-drift errors later. `Academic Context` was weakest: it excludes the professional and licensure content that is most of this library.
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

Notes model **canonical knowledge**. Programs describe **where that knowledge is applicable**. The durable note metadata model has **four axes**, each with exactly one owner:

| Axis | Field | Cardinality | Sole responsibility |
|---|---|---|---|
| Subject | `notes.subject` | 1 | what the note is about |
| Domain Context | `notes.domain_context` | 1 | **how** it is authored — the LLM domain constraint |
| Note Learner Level | `notes.learner_level` | 1 | **how deep** it is authored |
| Course / Program(s) | `note_course_program` (curated) / `notes.course_program` (personal) | N curated, 1 personal | **where** it appears — discovery only |

The **Course / Program(s)** row covers two stores, because NoteLib has two authoring modes: curated notes carry catalog programs in `note_course_program` (one or many), and personal notes carry one free-text value in `notes.course_program`. Both are current; neither is legacy. See "Two authoring modes — and therefore two program fields" below.

Binding rules:

1. **Course / Program(s) never reach a prompt as a list.** Generation is driven by Domain Context, which is **required** once a note has more than one program. A single-program note may still fall back to that one value; several programs may not be sent as a domain constraint. Programs are a discovery and curriculum-management facet only.
2. **Static content** (note body, summary, key concepts, flashcards, memorization, static question pool) is calibrated by Domain Context + Note Learner Level. Never by user learner level.
3. **Quizzes and exams** take Domain Context + Note Learner Level as the floor. User Learner Level may adjust scaffolding and wording; it may **never lower the curriculum**.
4. **Review Sets compose freely.** A Review Set may contain any note regardless of its Applicable Programs. A Review Set's own course/program is a curation label — never derived from, never validated against, its notes.
5. **Program Families are an authoring shortcut only.** Selecting a family expands to explicit `note_course_program` rows at save time. Applicability is never inferred from a family at read time. **Ratified in full 2026-08-05 — see "Program Families are a productivity feature" below. The three constraints there are binding.**
6. **Reuse a note when the learning objective, depth, and treatment are materially the same. Create a new note only when the learning experience itself genuinely differs.** Multiple canonical Algebra notes (Engineering Algebra, Business Algebra, College Algebra, Algebra Foundations) are correct and expected; collapsing them would be a misreading of this ADR.
7. All resolution stays in `StudyPackGenerationContextResolver`. No service reads these fields directly.

Delivered in two releases (revised 2026-08-03 after the production audit): **Release A** = Domain Context + `course_programs` catalog/families + Note Learner Level, all additive and reversible, with pool/bank re-keying as its own PR inside it. **Release B** = `note_course_program` + read paths, multi-release and not reversible.

### Target Audience retirement — ratified 2026-08-16

> **Target Audience has no long-term architectural responsibility and should be retired — but only after the useful information it currently carries has been migrated into Authored Depth, and its live discovery contract has been replaced.**

This amendment established the durable note metadata model as **four axes**. `notes.target_profile_type` still exists and is still written for retained evidence, but `v0.83.0` removed its product responsibility; it is no longer a row in the Decision table.

**Implementation status — `v0.83.0`, 2026-08-17.** Target Audience is removed from request/response DTOs, authoring and display UI, private-library projections, and Public Library filtering. Legacy `?audience=` links and stale JSON fields are ignored. Public Library discovery now exposes an Authored Depth equality filter through `?level=` and offers chips only for distinct non-null depths present on public notes. The physical `notes.target_profile_type` column, its `NOT NULL` and CHECK constraints, its index, `bulk_generation_result.target_profile_type`, and `NoteTargetProfileType` remain unchanged pending `[CHECKPOINT — due 2026-09-16]`. `NoteService.create` and bulk normalization continue writing the owner-profile-derived value so retained constraints hold; updates and copies preserve stored evidence. No migration ships in `v0.83.0`, and the value remains absent from `StudyPackGenerationContextResolver`, `StudyPackGenerationContext`, and prompt resources.

**The two grounds for retirement are narrow and binding:**

1. **It reaches no prompt.** Target Audience is absent from every file under `backend/src/main/resources/prompts/`, and from both `StudyPackGenerationContextResolver` and `StudyPackGenerationContext`. It does not participate in Study Pack, quiz, explanation, or adaptive generation.
2. **Its original access-control purpose was never implemented.** The Public Library audience filter defaults to `NOTE_TARGET_PROFILE_ALL`, and nothing restricts note visibility by audience.

**The observed ~99.3% correlation between Course / Program and Target Audience is not grounds for removal.** It is a supporting observation about the current board-heavy public catalog: licensure-program notes are overwhelmingly `BOARD_TAKER`, academic-level values are overwhelmingly `STUDENT`, and `PROFESSIONAL` is unused across the 945 public notes measured. It is not semantic equivalence. Civil Engineering, Nursing, Accountancy, Information Technology, and other programs can legitimately carry several authored depths; the correlation will break as general-student or professional content grows. It therefore cannot justify an irreversible column drop.

**Retirement is not lossless; the replacement is partially populated.** Before `v0.83.0`, Target Audience was a live Public Library `WHERE` clause, a set of user-facing chips, and a shareable `?audience=` URL parameter. `v0.83.0` rebuilt that filtering mechanism as a single-column Authored Depth equality filter, not as an assertion that nothing was lost. The 2026-08-17 production read found 120 curator-owned public notes carrying the former `STUDENT` audience: 26 `JUNIOR_HIGH`, 11 `SENIOR_HIGH`, 3 `GRADE_SCHOOL`, and **80 with NULL Authored Depth**. Those 80 notes remain outside every depth-filtered result until curators classify them; the new facet restores the mechanism but does not provide full historical coverage.

**Migration is curator-scoped.** The migration sizing found **5,550 affected notes: 4,645 learner-owned and 905 curator-owned**. The learner-owned notes must never be backfilled: writing depth there would assert an authoring decision the author never made onto a curriculum floor, violating constraint 2 of this ADR and the `v0.75.0` rule. They do not serve the Public Library contract being replaced. The 905 curator-owned notes are the legitimate migration population and are essentially the 945 public notes the audience filter reads. Learner-owned Target Audience values remain untouched during the transition and disappear only with the final schema retirement, after nothing reads or writes them.

Safe curator mappings may migrate `BOARD_TAKER` to `BOARD_EXAM_REVIEW` and `PROFESSIONAL` to `PROFESSIONAL`. **`STUDENT` spans four authored depths — `GRADE_SCHOOL`, `JUNIOR_HIGH`, `SENIOR_HIGH`, and `COLLEGE` — and must not be guessed.** Those curator notes require classification from authoritative curation context or direct human review before the discovery replacement can cover them.

**Target Audience must never become a runtime depth fallback.** It is one-time migration evidence only. Adding it to `StudyPackGenerationContextResolver` would create a fifth fallback layer in the one resolver this ADR deliberately keeps narrow and would turn transitional data into a permanent responsibility by accident.

#### Retirement sequencing and gates

The phases are ordered so the populated discovery contract is not removed ahead of its replacement:

1. **Prerequisite — complete.** `v0.81.0` widened the Challenge-bank uniqueness key so a curator depth correction does not turn preserved old-level rows into a collision that fails a session.
2. **Migrate curator depth.** Audit mixed Information Technology material, apply only safe curator mappings, and classify curator-owned `STUDENT` notes without guessing. Learner-owned notes remain untouched.
3. **Replace Public Library discovery.** Introduce the depth-based filter semantics, including the school-level grouping needed to replace `STUDENT`, only after the curator data can support them. This phase changes discovery inside `[CHECKPOINT — due 2026-09-13]`'s Explore-engagement measurement window and **must wait for that checkpoint**.
4. **Preserve the link contract.** Map shareable `?audience=` URLs to the replacement depth semantics; never silently degrade them to an unfiltered view.
5. **Remove writes and runtime surfaces.** Retire curator authoring, onboarding mapping, displays, DTO/service/repository fields, and other reads or writes only after the discovery and URL replacements are live.
6. **Drop storage last.** Drop the Target Audience columns and enum only when nothing reads or writes them.

Phases 3–5 above were superseded by the owner-ratified 2026-08-16 execution direction and shipped at the product-surface level in `v0.83.0`; phase 6 remains gated on `[CHECKPOINT — due 2026-09-16]`. The historical sequence is retained to explain the original safety argument, not as a live blocker for the already-shipped surface removal.

### Program Families are a productivity feature, not a curriculum feature

**Ratified by the owner 2026-08-05, closing the gate that stood on Release B's third slice.** Rule 5 said families are an authoring shortcut; this section states what that forbids, because "shortcut" alone did not stop the design drifting toward a curriculum engine.

**A Program Family's only job is to reduce repetitive authoring work.** It is not a curriculum model, not a taxonomy, and not a second source of applicability truth. Three binding constraints:

1. **Expansion is unconditional.** Selecting a family fills in the same rows every time. It must not depend on the note's Subject, Domain Context, learner level, or anything else about the note.
2. **A family expands to all of its members.** No curated per-family subsets, no preset table beyond `course_programs.program_family_id`. If a family should expand to a different set, that is a change to *membership*, not to expansion logic.
3. **Expansion never happens at read time.** It is a save-time pre-fill producing explicit rows. No filter, facet, badge, or search predicate may resolve a family into programs.

**Families are deliberately allowed to over-select.** The author sees the filled-in rows immediately and trims what does not apply, and **the Note's explicit `note_course_program` rows are always the source of truth.** A slightly-too-broad default that a human corrects is a healthier long-term trade-off than a subject→program mapping maintained forever — that mapping would re-couple the two axes this ADR exists to separate: *Subject* is what the knowledge is, *Domain Context* is how it is authored, and *Applicable Programs* is where it appears. Families must not become a fourth knowledge model.

> **The tripwire:** if we ever find ourselves maintaining curriculum rules inside Program Families, the feature has exceeded its responsibility. Remove the rules, not the constraint.

Rejected on the record, so neither returns as a "small improvement": **subject-conditioned expansion** (an earlier taxonomy doc proposed `Engineering Mathematics` reaching all engineering programs while `Engineering Sciences` reached only some) and **curated subset expansion**.

### Applicable Programs mean valid applicability, not curriculum coverage

**Ratified by the owner 2026-08-05.** The catalog answers *"who can legitimately study this note?"* — **not** *"which programs do we fully support?"*

This decouples catalog growth from curriculum completeness. A program may be seeded before any comprehensive Review Set exists for it, so canonical notes can be marked applicable to it immediately; that is what makes one Engineering Mathematics note serve many engineering programs without duplication, which is the purpose this ADR was written for. **Review Sets are what communicate curriculum completeness** — Applicable Programs never promise it.

**The catalog still follows curriculum; what changed is what "follows" means.** It does **not** mean waiting for a complete Official Review Set. It means a program earns a catalog entry once there are **legitimate canonical notes applicable to it** — if Engineering Mathematics notes are genuinely applicable to Mechanical Engineering, that alone justifies the entry, with no Mechanical Engineering Review Set required.

**Do not pre-seed a program vocabulary.** Seeding every PRC engineering program at once is premature expansion and is explicitly rejected. **Catalog growth is incremental and demand-driven by authoring:** a curator judging that a canonical note is applicable to a program is the trigger to add that program. This keeps the taxonomy grounded in real content.

This refines rather than reverses the catalog's *follow-not-lead* posture (`v0.70.0`, where `Computer Science` and `Software Engineering` were excluded pending real curriculum). Those rulings stand. The standard for seeding is now "canonical notes are applicable to this program," which is weaker than "we have a curriculum for it" but strictly stronger than "a learner might plausibly exist."

### Course / Program(s) is the only program concept — there is no "primary"

**Ratified by the owner 2026-08-05, superseding the two-field model Release B shipped with.**

**A note does not belong to one program. It applies to one or many.** The data model, the API, and every human-facing surface express exactly that: a single many-valued axis labelled **Course / Program(s)**. There is no primary program, no separate "Applicable Programs" section, and no synchronisation between two fields.

**The reasoning that settles it.** Each axis in this ADR owns exactly one responsibility — Subject owns *what*, Domain Context owns *how it is authored*, Note Learner Level owns *how deeply*, Course / Program(s) owns *where it is discovered*. A "primary" program owns **none** of them. It was the note's only program when the schema was single-valued, and after Release B it is merely whichever program happened to be first. The question is therefore not "should we replace primary?" but **"should a concept with no remaining architectural responsibility continue to exist?"** — and the answer is no. Retaining it would reintroduce precisely the overlap this ADR was written to remove.

#### Domain Context is REQUIRED when a note has more than one program

**A many-valued program list must never become the LLM's domain signal.** The generation prompt states: *"treat the domain above as the authoritative academic domain. All content, terminology, examples, and question framing must belong to that domain. Do not blend in material from unrelated disciplines."* That instruction is **logically unsatisfiable given a list** — it names several disciplines while forbidding blending across disciplines. This is the founding observation of this ADR, not a new concern: it is why Domain Context exists as a separate single-valued axis at all.

So:

1. **Domain Context, when set** — always the authoring signal, unchanged.
2. **A single program, when Domain Context is unset** — today's single-value fallback, unchanged.
3. **Several programs with no Domain Context** — **rejected at save**, server-side. Not a UI-disabled button.

Sending the full program list to the model is **explicitly rejected.** The hypothesis that a list encourages authoring toward shared knowledge rather than over-specialising is untested and runs opposite to this ADR's premise. **R4 does not cover it** — R4 validated a *broader single* Domain Context, which is a different question. Revisiting this requires an R4-style generate-and-diff read first.

The rule is enforced by **program count at save time**, which means adding a second program to an existing note is the moment Domain Context becomes required. That is intended: it forces the authoring decision exactly when it starts to matter. The error must teach rather than name a mechanism — *"A note shared across several programs needs a Domain Context, so the AI knows which academic domain to write in."*

**Introduced at zero cost, and only at this moment.** `V107` produces exactly one join row per note and has not yet reached production, so **no multi-program note exists anywhere**. The requirement lands with no pre-existing violations and no backfill. That cheapness expires the moment curators begin authoring.

#### Two authoring modes — and therefore two program fields

**Ratified by the owner 2026-08-05.** This section **supersedes an earlier framing in this same ADR** that called `notes.course_program` a *"frozen compatibility column"* with a *"closed tap"* and an exit condition. That framing was wrong, and it is corrected here rather than quietly edited: it assumed every author would be restricted to the catalog, which is not the product we want.

NoteLib has **two authoring modes**, and this is a product distinction rather than a permission workaround:

| Mode | Who | Programs | Vocabulary | Stored in |
|---|---|---|---|---|
| **Personal note-taking** | learners | exactly one | **free text** | `notes.course_program` |
| **Curation** | Teacher / Admin | one or many | **catalog only** | `note_course_program` |

**Selecting several applicable programs is a curation act, not everyday note-taking.** A learner's note serves that learner; canonical material serving many curricula is authored deliberately, by someone doing that job. Restricting multi-program authoring therefore follows from what the two activities *are* — it is not a UI workaround for a permission problem, and it should never be documented as one.

**Free text is retained for personal notes on purpose.** NoteLib behaves like a notebook, not a closed LMS. Someone studying a niche subject, or a program the catalog does not yet cover, must still be able to describe their own material naturally. The catalog is the controlled vocabulary for **curated content**, not a restriction on personal learning.

**The two splits follow one line.** Cardinality and vocabulary are both gated on Teacher/Admin — curators get catalog-only and many, learners get free text and one. Splitting them on different conditions would make the model harder to explain than either split alone.

**So `notes.course_program` is not legacy and is not frozen.** It is **the personal-notes program field**, permanently, and it is still written by learner authoring. `note_course_program` is the curated-content axis. Both are current; neither is awaiting removal.

**This is what Slice 2's read semantics were actually describing.** Join-first with a legacy-string fallback was scoped as legacy handling, but it maps exactly onto the mode boundary: curated notes resolve through the join, personal notes through the string. The fallback is the personal-notes path, not a compatibility shim.

**Consequences to hold onto:**

- **The column has no exit condition and needs none.** Retiring it would delete personal-note discovery. `19-slice-2-facet-equivalence-impact.sql` query A still usefully sizes how much *existing* content sits outside the catalog, but it is no longer gating a removal.
- **The generation fallback for legacy multi-program notes.** The Domain Context requirement cannot apply retroactively, so a pre-existing multi-program note with no Domain Context resolves its domain through its string. That path must survive any future change to this column.
- **Catalog-excluded historical values** (bare levels, bare subjects, the `Engineering` family, the owner-ruled `Computer Science` / `Software Engineering`) keep working through the same personal-notes path, which is why nothing needs migrating.

### Representation authority: what may author an Applicable Program row

**Ratified 2026-08-06**, closing pressure-test finding B2 (Decision A). The rule is about **provenance**, not ownership:

> **A learner's personal free-text Course / Program must not be mechanically materialized into a catalog Applicable Program row.**

**This is deliberately narrower than "learner-owned notes cannot carry join rows", and that broader phrasing is wrong.** It would contradict copy inheritance and the standing ruling that ownership must not change the semantics of the metadata model. Learner-owned notes may carry join rows whenever those rows have legitimate curated provenance.

The canonical shapes:

| Shape | `notes.course_program` | `note_course_program` |
|---|---|---|
| **Learner-authored personal note** | the program | **no mechanically derived rows** |
| **Curator-authored note** | null | one or more authored rows |
| **Curated note copied by a learner** | as copied | inherited rows, preserved as authored metadata |

**Read semantics are identical for all three** and do not consult ownership: *joined programs first when joined programs exist; otherwise the personal program string.* Option 7 therefore does not make the same metadata mean different things depending on who owns it — it prevents one representation from being populated by a source with no authority to author it. A learner's string is that learner's own representation, permanently; materializing it into the curated axis invents metadata nobody authored, and the learner cannot then reach it to correct it.

**A learner-authored note using the personal-string fallback is a canonical, fully supported shape — not a degraded one.** Join rows exist to express curated multi-program applicability. They are not a superior representation of every single-program note, and discovery parity is not a reason to manufacture them.

**Consequences:**

- **`V107`'s unfiltered backfill is corrected by an additive follow-up migration** rather than by editing it, because editing a migration that has already run elsewhere breaks its checksum. Curator rows remain; future inherited rows remain. Shipped as `V108__remove_derived_learner_note_programs.sql`.
- **No learner-facing Applicable Programs UI**, no `source` provenance column, and no learner-save synchronization. Each was considered and rejected: the first leaks the curator publishing model into personal authoring, the second is schema weight for a population this rule keeps empty, and the third cannot distinguish a mechanically derived row from an inherited curator-authored row when both match the learner's old string — so it can silently delete valid inherited applicability.
- **Zero affected users strengthens the case for prevention rather than weakening it.** It is not a reason to deploy a deterministic divergence mechanism and monitor the resulting harm.
- **Any future backfill or bulk process is bound by this rule**, not just `V107`. No runtime path currently derives join rows from the personal string — all writes are curator-authored or copy-inherited — and that must stay true.

### "No learner-facing Applicable Programs UI" governs authoring, not provenance display

**Ratified 2026-08-10**, scoped into `v0.71.1`, clarifying the first clause of the Consequences bullet above. It is a clarification of what that rule always meant, not a reversal — but it was genuinely ambiguous, and the ambiguity was blocking a fix.

> **A learner may be shown the Applicable Programs their note carries, read-only, with their provenance. A learner may never author, add, remove, or edit them.**

**Why the question arose.** The two rulings above are each correct and jointly produce a shape neither anticipated. The canonical-shapes table defines a curated note copied by a learner as carrying **both** representations — `notes.course_program` "as copied" plus inherited join rows — while *Read semantics are identical for all three* makes the join rows win whenever they exist. On that shape the string is therefore **unreadable**, and the note-authoring surfaces were still presenting it to the learner as a required field. The learner was compelled to fill a field that nothing reads, on a note filed under programs they were shown but could not explain.

It is worth stating plainly that this was an *interaction* defect, not a mistake in either ruling. Copy inheritance is right. Ownership-blind read semantics are right. Their conjunction was not examined.

**The shadowing predicate — use this, not "the note has join rows."**

```
shadowed = (joinRowCount >= 1) && (joinRowCount == 1 || domainContext != null)
```

This is the exact condition under which `notes.course_program` cannot be read on any path. **The string is shadowed only when BOTH readers ignore it — that conjunction is the whole predicate**, and it is what the first version of this paragraph got wrong:

- **Discovery** ignores the string whenever *any* join row exists — every library and public read is `EXISTS(join rows) OR (NOT EXISTS(join rows) AND legacy string matches)`. Verified across all five readers: both library filters, both legacy-value lookups, and the facet count. So discovery ignores it iff `joinRowCount >= 1`.
- **Generation** reads the string only through `effectiveAuthoringDomain`, which returns the Domain Context label when one is set, and otherwise calls `resolveCourseProgram` — which returns the joined catalog name at **exactly one** row and falls through to the string at 0 or 2+. So generation ignores it iff `domainContext != null || joinRowCount == 1`.

So a copy of a curated note is shadowed on both paths: at 2+ programs the inherited Domain Context wins (curated multi-program notes are *required* to carry one, and `copyNote` inherits it); at exactly one program the joined catalog name wins.

**Corrected 2026-08-11 by the `v0.71.1` pressure test. The original form was `(joinRowCount == 1) || (domainContext != null)`** — it dropped the discovery term entirely, and so returned `true` at **zero** join rows with a Domain Context, which is precisely where the string is *maximally* readable: with no join rows it is the only value discovery can match on. The consequence was a learner whose own Course / Program field was hidden, un-required, and skipped by `update`, while still deciding which shelf their note appeared on — permanently uneditable through the product. It was reachable by copying a curated note whose program the catalog deliberately excludes. **The error came from deriving the predicate from the two resolvers while treating their conditions as alternatives rather than as a conjunction**, then propagating it into four documents and a test that asserted it as correct.

**Do not simplify the predicate to "has join rows"** either. That would rest on the invariant *2+ rows ⟹ Domain Context non-null* holding across every write path, present and future. The predicate above is correct whether or not that invariant holds, and a fix that depends on an invariant nobody enforces is a fix waiting to rot.

**Consequences:**

- **Permitted:** read-only display of a note's Applicable Programs to its learner owner, naming their provenance (inherited from the note that was copied). Withholding this was the worse option — it leaves the learner unable to explain why their own note is filed where it is.
- **Still forbidden, unchanged:** any learner-facing control that adds, removes, or edits Applicable Programs; any path that derives a join row from a learner's personal string; the `source` provenance column; learner-save synchronization.
- **The personal Course / Program field must not be required on a shadowed note.** Requiring a value nothing can read is the defect this ruling exists to close. `NoteService.resolveRequestedCourseProgram` already falls back to the owner's profile program and throws only when both are null — that residual throw is the same defect on the AI-suggestion surface, and closes with the same predicate.
- **This ruling binds every surface that renders a learner's Applicable Programs, not just the one that prompted it.** **Corrected 2026-08-11 — the original wording asserted a mechanism that does not exist.** It read: *"Note Detail is the only such surface today because the private list projection returns an empty array; the private library card already passes `applicablePrograms` through, so correcting that projection surfaces programs on learner cards automatically."* That is wrong on two counts. **Library cards have rendered joined programs since Release B slice 2** (`de441fbc`), because the Library page reads `listLibraryPage`, whose native query has always carried the aggregate join. **M2 concerns `listMine` (`GET /notes`)**, a different repository method that no card consumes. So correcting M2 surfaces nothing on cards, and the sequencing rule built on this claim was a correct instinct resting on a wrong mechanism. What actually holds: cards already show the neutral summary with no provenance, which is exactly what this ruling requires of them — the state was live before the ruling was written, not a future consequence gated on M2.
- **Provenance *display* lives on Note Detail, not on cards** — a deliberate split, not an oversight. Note Detail already carries the note-level *"Copied from … in Public Library."* line, so the read-only program block can lean on an existing, true statement. A card carries no copy indicator at all, so provenance there would be the only such signal and would have to assert *how* the rows arrived — which **cannot be stated safely while the admin-curation path (item 1) is undecided**, since an admin may author rows on a learner's non-copy note. Cards therefore show the same neutral program summary every other card shows. The learner who asks *"why is my note filed here?"* gets the answer one click away, on the surface that already answers the related question.
- **Known gap, tied to item 1's outcome — NARROWED 2026-08-11, see *Curation authority* below.** It read: on an admin-curated **non-copy** learner note, the learner sees read-only programs with no explanation of their origin. Item 1 removed the mechanism that produced that shape, so it is no longer reachable across users. What remains is narrower: a curator acting on a note they own that is itself a copy can overwrite inherited rows through the full-replace endpoint while `copiedFromNoteId` stays set. The Note Detail sentence is conditioned on `copiedFromNoteId` rather than `sourceNoteId` precisely so it never asserts a copy that did not happen; it can still credit a copy for rows a curator authored on their own copied note.

**Deliberately left open — the overlapping-representation class itself.** Two options would close it outright rather than patching its symptom: **not inheriting join rows on copy** (a learner's copy becomes a personal-string note), and **letting a learner's own value clear the inherited rows on their copy**. Both amend ratified text — the canonical-shapes table and the learner-save-synchronization rejection respectively — so neither is a patch-release decision, and both are deferred with a Backlog Index row rather than folded in. **The sharpest evidence for eventually taking one of them:** a learner can flip their copy to `PUBLIC`, and public discovery is join-first, so inherited curator rows can drive *public* shelves for a note the curator never published. Default-private is not stays-private.

### Curation authority: an Applicable Program row may only be authored onto a note its author owns

**Ratified 2026-08-11**, closing `v0.71.1` group 1 item 1. This is the ownership half of *Representation authority* above: that rule says a learner's personal string must not be **mechanically derived** into a catalog row; this one says a catalog row must not be **authored onto someone else's note**.

> **An Applicable Program row may only be written by an author acting on a note they own.** `ADMIN` role grants catalog-curation authority; it does not grant it over other users' notes.

**The defect this closes.** `NoteApplicableProgramsService.findAuthorizedNote` returned early for any `ADMIN`, so an admin could set catalog programs on any note in the system, and a learner update never clears them. That reproduces the exact `V108` class — rows a learner cannot reach on a note that is theirs — by ordinary product action rather than by migration. **`V108` deleted the instances; it could not close the mechanism**, which is why this needed a decision rather than a patch.

**This also retires a `v0.70.0`-era known limitation rather than leaving it standing.** `V108`'s deletion predicate was *learner-owned AND not-a-copy*, and it accepted as a documented cost that it would remove rows **an admin had legitimately authored** on such a note. Under this rule those rows can no longer be authored at all, so that limitation is moot going forward rather than live. `RELEASES.md` still carries the original wording under `v0.71.0`; read it as historical.

**Why ownership rather than visibility.** The rejected alternative was to permit curation of any *public* note, on the reasoning that Applicable Programs is a discovery surface and a private note is not discoverable. It fails on an ordinary sequence: a learner's note is public, an admin curates it, the learner flips it back to private — `canManageVisibility` is `isEmailVerified || isPublic`, so that is a normal user action. The note now carries admin-authored rows the learner cannot clear, reached through two individually-permitted steps. **Gating the write moment does not constrain the resulting state.** Closing that would require distinguishing admin-authored rows from inherited ones so they could be cleared on the transition — the `source` provenance column this ADR rejected. Ownership needs no such distinction, because it prevents the state instead of detecting it.

**Production sizing, run 2026-08-11 before deciding** (`docs/claude-plans/item1-admin-authored-applicable-programs-sizing.sql`). Recorded here because a query cited as a mechanism whose result is never written down is how this project has previously lost the reasoning behind a decision:

| Query | Result | What it settled |
|---|---|---|
| **A** — rows only reachable by an admin write (learner-owned, non-copy, carrying join rows) | **0 rows, 0 notes, 0 owners** | The class is entirely unrealised. Prevention is free; **no cleanup migration is needed** |
| **B** — learner-owned private notes carrying join rows | **none** | The flip hole has produced no instances yet either |
| **C** — notes carrying join rows that an admin does not own | **none** | **This restriction costs nothing.** Every curated note in production is admin-owned |
| **D** — what the admin curator page lists | 885 admin-owned · 4 other-public · 4480 other-private · **5369 total** | See the page consequence below |

**Zero affected users is the argument for prevention, not against it** — the same reasoning that decided B2, and the reason this ships as a constraint rather than as a monitored risk.

**Consequences:**

- **`findAuthorizedNote`'s early return for `ADMIN` is removed.** Authority becomes ownership-based for every role. `TEACHER` behaviour is already ownership-scoped and does not change. **Read** access is untouched — this rule is about authoring rows, not seeing them.
- **The Official Library is unaffected**, which is what makes the restriction cheap: official notes are `ADMIN`-owned (`isOfficialAuthor` is `role == ADMIN`), so the main curation workflow is an admin acting on its own notes. Teachers curate their own via the existing owner check.
- **What is genuinely given up:** an admin can no longer classify a *learner's* public note into programs. Production holds **4** non-admin public notes and none carry rows, so the present cost is nil — and the Trust → Habit → Community ordering puts community content last deliberately. **If community authoring later makes this a real need, it returns as a scoped decision with the `source` column on the table**, not as a quiet relaxation of this rule.
- **The Admin Dashboard curator page loses its write function on notes the admin does not own — this is a UI change, not only a list filter.** The page writes through `replaceNoteApplicablePrograms` → the same restricted `PUT`, and `findAuthorizedNote` has exactly one caller, so there is no second path that survives. `getAdminPage` uses `findAll()` with no ownership or visibility filter, so today the page offers an edit action on all **5369** notes, of which **4480 are other users' private notes — 83% of everything it renders** — and every one of those actions would begin returning `NoteNotFoundException` on a row the page just showed. Filter to the **885** writable notes rather than leaving a mostly-dead edit affordance; that also turns the page from a list of other people's private material into a curator surface. **The separate `AdminNoteApplicableProgramsController` is GET-only and needs no change** — this rule closes the only write door, not one of several.
- **This narrows but does not close the item 2 provenance gap.** With admin-authored rows prevented, a non-curator owner's join rows can only arrive by copy inheritance — which `copiedFromNoteId` and `sourceNoteId` already distinguish. The residue: `replace` is a full replace, so a curator acting on a note they own that *is itself a copy* can overwrite inherited rows while `copiedFromNoteId` stays set, and the provenance sentence would then credit the copy for rows the curator authored. Narrow, unreachable across users under this rule, and **exactly the argument the `source` column makes for itself** — so it is recorded, not inferred away.

### Programs and Review Sets answer different questions

**Ratified 2026-08-05.** The two surfaces have distinct responsibilities and must not be collapsed:

| Surface | Answers |
|---|---|
| **Program** (Applicable Programs) | *"What notes are applicable to me?"* — a **discovery** surface |
| **Review Set** | *"What is my complete learning journey?"* — the **completeness** signal |

**Consequence:** a seeded program with no applicable notes is invisible to learners by construction — every learner-facing program list (facets, filter dropdowns, search) derives from *notes*, not from the catalog, so coverage is **emergent rather than declared** and the catalog is author-facing. The residual risk is a **thin** shelf, not an empty one: a program carrying a handful of shared foundational notes can read as a curriculum without being one.

**The agreed direction — a design direction, not a shipped mechanism.** Coverage is communicated **at the Program level**, when a learner browses a Program that has no dedicated Official Review Set yet — conceptually *"This Program currently contains shared foundational notes. A dedicated Official Review Set is still being developed."* Explicitly rejected: per-note coverage indicators, and any new coverage metadata system. Tracked in `docs/product/ROADMAP.md`; **not** part of Release B slice 3.

**Consequence to hold onto:** a seeded program with no applicable notes is invisible to learners by construction — every learner-facing program list (facets, filter dropdowns, search) derives from notes, not from the catalog, so coverage is *emergent rather than declared*. The catalog is author-facing. **The open risk is a thin shelf, not an empty one:** once a handful of canonical notes are marked applicable to a program, it looks like a curriculum without being one, and exposing programs to learners requires communicating coverage rather than implying completeness.

### Choosing a Domain Context value

> **Rule:** Domain Context is the coarsest label under which the note's treatment is identical.
>
> **Test (binary):** would a student in a sibling program be served by this exact note, unchanged? Yes → the shared bundle. No → the program name.
>
> **Tiebreaker, only when the test is genuinely unclear:** compare learning objectives, terminology, examples, and depth; the first material difference decides.
>
> **Empirical tie-break of last resort:** because this value is substituted into the generation prompt, generate the note under both candidate values and compare the output. For a small team this is faster and more decisive than adjudicating definitions.

A longer rule enumerating four conjunctive criteria was considered and rejected — it invites more curator disagreement, not less, by creating four axes to argue over instead of one judgment to make.

**`General Engineering` is explicitly rejected (owner, 2026-08-29).** It would be a vague catch-all that instructs the model toward no particular treatment, which is strictly worse than the honest signal `domain_context IS NULL` already carries: *not yet promoted*. **An honest NULL beats a catch-all**, because NULL is a backlog marker a query can find and a catch-all is a decision that looks made.

**Names are derived from real curriculum vocabulary — board exam subject areas and recognized curriculum bundles — never invented.** Two candidate values that were invented rather than borrowed (`Health Sciences Foundation`, `Computing`) failed both the learner-comprehension test and the governance rule below, independently. That correlation is the rule's justification: an invented name usually signals there is no real shared body of knowledge behind it.

**Worked examples — the water cases (owner ruling, 2026-08-29).** Recorded in the same form as the
Architecture example below, because they are the rule applied rather than a new rule.

- *Water Treatment* — would a Chemical Engineering student be served by this exact note, unchanged?
  **Yes** → the shared bundle → `Engineering Sciences`.
- *Water Supply Engineering* — would a Sanitary/Environmental student be served by the same note as a
  Civil student, unchanged? **Yes** → the shared bundle → `Engineering Sciences`.

**⚠️ The selection rule was NOT amended, and a proposal to invert it was declined.** A direction
document proposed selecting *"the narrowest existing authoring tradition that materially improves
generation."* That is the opposite instruction to the coarsest-label rule above, and its accompanying
six conjunctive criteria are an enlarged form of the four-criteria shape this ADR already rejected for
inviting more curator disagreement, not less. **The owner ruled on 2026-08-29 that this ADR remains
authoritative**, and that the direction's actual intent — stopping Course/Program identity from
mechanically determining Domain Context — is served by the rule as written plus these consequences:

- **multi-program applicability alone does not imply `Engineering Sciences`**;
- **membership of a Civil Engineering Review Set does not imply `Civil Engineering`**;
- **the existence of a narrower or program-shaped value is not a reason to select it**;
- **Applicable Programs remains responsible for curriculum and discovery applicability**;
- **specialization must be justified by a real difference in authoring treatment** — a more specific
  context is warranted only when that specificity materially changes the correct terminology, framing,
  examples, conventions or scope of the generated material.

**⚠️ Water Treatment is preserved as a calibration case, with its prior stated.** If representative
generation under `Engineering Sciences` proves accurate but consistently mis-framed or too generic in
terminology, examples, conventions or scope, that is evidence the taxonomy may be insufficient. **It
does not justify preemptive expansion.** ⚠️ **Expect it to pass:** R4 below already established that a
broader Domain Context does not degrade authored content, so a passing result confirms an existing
finding rather than proving something new, and must not be recorded as if it did. Use R4's existing
runbook (`docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`) —
**do not write a second evaluation rubric.**

### Ratified value set (8, as of Release A)

`Engineering Mathematics` · `Engineering Sciences` · `Civil Engineering` · `Professional Practice & Regulation` · `General Education` · `Professional Education` · `Nursing` · `Accountancy`.

`Professional Practice & Regulation` is justified on **treatment** — legal, procedural, and regulatory rather than scientific — across 68 existing notes. Its *applicability* across engineering boards is a separate and unverified question, like `Engineering Sciences`'.

**`Architecture` is deliberately NOT a Domain Context** (owner decision, 2026-08-03). Despite carrying 837 notes across five large subject plans, it uses the **program-name fallback** until there is enough *shared* canonical knowledge to justify its own context. This is the governance rule applied at ratification rather than retrofitted: a Domain Context earns its existence by materially reducing duplication, and Architecture's subject plans are Architecture-specific — a context for them would reduce nothing. Note volume is explicitly **not** a qualifying criterion; shared treatment is.

This is the clearest worked example of the rule, and it is worth preserving as one: a program can be among the largest in the library and still not warrant a Domain Context.

**⚠️ The Architecture rebuild gathers evidence AGAINST this floor; it does not reopen the 2026-08-03 decision (clarified 2026-08-29).** Observing whether an `Architecture` Domain Context becomes warranted during that rebuild is legitimate and expected — the governance bar above is exactly what such evidence would be measured against. **What is not open is the decision itself**, and no volume of Architecture notes reopens it, because **note volume is explicitly not a qualifying criterion; shared treatment is.** **Do not create `Architecture` from program identity alone.**

**Applicability defaults for these values are NOT yet verified against current PRC board syllabi** and must be curator-checked before family-expansion defaults are set. Whether `Engineering Sciences` spans 8 or 11 engineering programs is a curriculum fact, not an architecture decision.

### R4 verification — RESOLVED 2026-08-04. The 8-value set is not amended.

**Risk R4 (`01`): a Domain Context is often broader than the `course_program` it replaces, and a vaguer domain constraint could make generated content drift generic.** No automated test can detect this — the prompt-building tests assert which values reach the model, never whether the output is good. The check was owed as a post-deploy checkpoint from `v0.69.0`, carried through `v0.70.0`, and run against production on 2026-08-04 once `v0.70.0` deployed. Runbook and scoring rubric: `docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`.

**Result: passed on all three steps. Bulk authoring is unblocked.**

- **Control** — `Design and Function of Irrigation Canals in Hydraulic Structures` set to `Civil Engineering` + `Board Exam Review` and regenerated. Output near-identical to its prior pack, slightly richer. The domain label barely moved and neither did the output, so the wiring introduces no regression of its own.
- **The actual test** — `Fundamentals and Design Principles of Pressure Vessels` (subject `Strength of Materials`, program `Civil Engineering`) set to the deliberately broader `Engineering Sciences`, learner level left NULL, and regenerated. **Zero of five drift checks fired.** `hoop (circumferential)` and `longitudinal` stress, `Lame's equations`, the `ASME Boiler and Pressure Vessel Code`, `carbon steel and stainless steel`, the thin/thick-wall classification, and the civil-engineering application framing all survived the broader label. Verified afterwards in production that only `domain_context` changed and `learner_level` remained NULL, so this is a clean single-variable result.
- **Level precedence** — a quiz generated from the control note (authored `BOARD_EXAM_REVIEW`) by a reader whose profile level is `COLLEGE` produced board-level questions, scored against `docs/features/challenge-quiz.md`'s own level rubric: plausible-distractor `NOT`/`INCORRECT` framings, and a four-step problem (trapezoid area → wetted perimeter from side slopes → hydraulic radius → Manning's) rather than the single plug-in a College-level item would use. **Rule 3 held**: the reader's lower level did not lower the curriculum.

**What this does and does not settle.** It validates the **8-value Domain Context set** — a broader value does not degrade authored content, so the taxonomy does not need narrowing. It says nothing about the **applicability groupings** in `06`/`08`, which the paragraph above still flags as unverified against current PRC board syllabi. Those remain a curator question and are exactly what Release B's family-expansion defaults depend on. Do not read "R4 passed" as "applicability is settled."

**One incidental finding, logged separately rather than here.** The regenerated Pressure Vessels summary dropped a comparison table and a Common Misconceptions block that its prior version had. This is **not** drift: the surviving prose stayed fully domain-specific, and a level-signal explanation is falsified by the note's own history — the original pack also had `learner_level` NULL and *did* carry the table. Recorded as one-sample structural variance on optional summary blocks, cause unestablished, with its own ROADMAP Backlog Index row on the user-facing grounds that a regeneration can silently drop content a learner valued.

**Usage observation, 2026-08-29 — recorded as UNRESOLVED EVIDENCE, not as a finding.** Curator
classification stands at **32.6% (370 of 1,135 public notes)** with **five** of the eight values in use.
The three unused values — `Professional Education`, `Nursing`, `Accountancy` — are the program-shaped
ones, and **1,135 notes remain on the program-name fallback**, including Accountancy 154 and Nursing 132.

**Two explanations fit equally well and this ADR deliberately does not choose between them:**

1. **Authoring order.** Curation has been Civil-Engineering-first, so the engineering values were reached
   first and the program-shaped ones simply have not come up yet. Under this reading the zero is a
   schedule artifact and predicts nothing.
2. **The values are shaped wrongly.** A value named after a program may be the thing curators avoid,
   precisely because the rule above tells them to ask about *treatment* rather than program identity.
   Under this reading the zero is a signal about the vocabulary.

**⚠️ Do not resolve this by reasoning — it is what the taxonomy calibration checkpoint reads.** Recording
one explanation as settled would make that read unfalsifiable before it runs. **⚠️ Do not cite 12.7% or
"four of eight values used"** — those are the 2026-08-17 figures and are superseded by the above.

### Domain Context governance

> **Domain Contexts are expected to remain relatively stable.** Introducing a new one is an **architectural decision, not routine curriculum authoring** — it changes how the LLM is instructed to author an entire class of content and permanently widens the closed vocabulary curators assign. Learners do not see this authoring vocabulary. Treat it with the weight of a schema change: it needs an owner decision and a recorded rationale, not a curator's judgment call mid-authoring-session. **Adding notes is authoring. Adding a Domain Context is architecture.** This distinction is the primary defence against taxonomy explosion, and it is the reason the field is a curated closed set rather than free text.
>
> A new Domain Context value may be introduced only when **both** hold: (a) there is a sustained body of canonical knowledge — as a concrete floor, **~10 or more notes already authored or firmly planned** — whose treatment cannot be accurately represented by an existing value; and (b) an explicit owner decision is recorded in this ADR's revision log. **When in doubt, reuse an existing Domain Context.**
>
> **⚠️ A new Course / Program does NOT imply a new Domain Context (owner, 2026-08-29).** This ADR governs promotion by *shared treatment*, and never stated the converse — which matters now that the catalog is expanding. Programs are added as curriculum and discovery facts; a Domain Context is added only on the evidence bar above. **The catalog growing is not evidence for the taxonomy growing.**
>
> **Failure condition, reviewed at every `/kickoff`:** if the number of Domain Context values ever approaches the number of course programs, the taxonomy has failed and has collapsed back into the free-text field it replaced. Baseline at ratification: **8 contexts against 27+ programs.** **⚠️ Corrected 2026-08-31: that figure is the PRE-CATALOG FREE-TEXT SPREAD** (32 distinct values audited at ratification), **not catalog rows.** `V106` seeds a **21-row** catalog, so the ratio this condition actually watches is **8:21**. The correction matters because the whole condition is a ratio and it was being read against the wrong denominator. A ratio trending toward 1:1 is the signal to stop and consolidate, not to keep adding.

### Program-name fallback is a transitional state, not the end state

Thin programs (1–7 notes: Law, Medicine, Criminology, Psychology, Aviation, Business Administration, Physical Therapy, Civil Service, and initially Pharmacy and Information Technology) use their **program name** as the effective authoring context via the resolver's fallback chain. This is pragmatic, not desired.

**⚠️ Carve-out, stated because it is ENFORCED and a curator otherwise meets an unexplained save error: `domain_context IS NULL` is the backlog marker for SINGLE-PROGRAM notes only.** A note with two or more Applicable Programs **must** carry a Domain Context — `NoteApplicableProgramsService` rejects the combination server-side, on the reasoning ratified above: *"treat the domain above as the authoritative academic domain"* is logically unsatisfiable when handed a list. **⚠️ Second-order effect, recorded because it is load-bearing rather than incidental: as shared engineering material gains Applicable Programs, Domain Context becomes mandatory more often — the multi-program rule is itself the forcing function that generates classification evidence.** No instrumentation is needed for it.

**It is expressed mechanically rather than only documented, so it cannot be mistaken for the end state:** these values are **not** added to the curated `domain_contexts` set, so `domain_context IS NULL` *is* the marker of "not yet promoted," and the promotion backlog is a one-line query grouping null-context notes by `course_program`. A curator can see at any time which programs have crossed the ~10-note governance floor. Prose intent decays; a queryable state does not.

### Subject / Domain Context collision

Subject and Domain Context are different axes, and a value legitimately appearing in both is not an error — a broad survey note about nursing really is `subject = Nursing`. But ~334 notes currently carry a program name as their subject (`Professional Education` 250, `Nursing` 62, `Engineering Mathematics` 10, `Accountancy` 8, `Architecture` 3), which already violates `buildSubjectSuggestionGuidanceBlock`'s existing rule against overly broad subjects and echoing the program name.

**Guard:** when a note's `subject` equals its `domain_context`, surface an admin-side warning that the subject is probably too broad — a nudge, never a hard validation error. Domain Context values are not renamed to dodge the collision; borrowed board-subject-area names are precisely why the vocabulary reads well to learners.

### Legacy-data policy: ambiguity is not migrated forward

Ratified by the owner 2026-08-03, binding on every migration under this ADR. Later PRs must not reopen either rule.

**1. Ambiguous legacy values are resolved per-record by content, never by blanket mapping.**

The concrete case is `notes.course_program = 'High School'` (11 notes, all official and public, none in any collection). `LearnerLevel` already distinguishes `JUNIOR_HIGH` from `SENIOR_HIGH`, so the legacy label is strictly less precise than the taxonomy replacing it. Rules:

- Classify each such note as `JUNIOR_HIGH` or `SENIOR_HIGH` from **its actual curriculum and content**, not from the old label.
- Where a note cannot be classified confidently, **leave it unclassified for admin review** rather than assign a level that may be wrong.
- **Do not introduce a `HIGH_SCHOOL` enum value** to preserve the ambiguity. Adding one would migrate the imprecision permanently into the new taxonomy, which is the opposite of this ADR's purpose.

Derived operational rule: an unclassified note keeps `learner_level` NULL **and retains its existing `course_program`**, so it is never left with no classification at all — the fallback chain still resolves it. The general "clear `course_program` once the level moves out of it" step applies only to notes that were confidently classified.

**Corollary, added 2026-08-03 while scoping PR 4 — an unclassified note also keeps `domain_context` NULL.** Setting a Domain Context on a note whose level could not be decided would defeat the rule above rather than complement it. `StudyPackGenerationContextResolver` (`:122-140`) resolves `effectiveAuthoringDomain` as `domainContext` → `courseProgram`, and `effectiveCurriculumLevel` as `noteLearnerLevel` → user level → `COLLEGE` — the level chain **never reads `courseProgram`**. So on an unclassified `High School` note, `'High School'` reaches `buildGenerationContextBlock` (`:1561-1566`) today as the `Domain:` line — the wrong axis, but a real grade-level signal. Backfilling `domain_context = GENERAL_EDUCATION` evicts it, because Domain Context wins that fallback, and nothing replaces it: static content takes its level from `noteLearnerLevel` **directly, with no reader fallback** (`:1554-1556`, deliberate — a Grade School reader must not lower a College note), so a NULL level emits no `Curriculum level:` line at all; quizzes and exams do fall back and land on the reader's level, defaulting to `COLLEGE`. The note therefore moves from a wrong-axis level signal to **no level signal for static content and college-level curriculum for quizzes**. Retaining `course_program` only preserves a classification if nothing overrides it. This is also the consistent reading of the promotion marker — `domain_context IS NULL` means "not yet classified," which is precisely this note's state.

One consequence to expect rather than act on: because these rows keep `domain_context IS NULL` alongside `course_program = 'High School'`, they will appear in the promotion-backlog query described above under "Program-name fallback." **`High School` is not a thin program awaiting promotion** — those rows are admin-review flags for a classification that was declined, and the correct resolution is to classify the note, never to mint a Domain Context for the label.

**Second corollary, added 2026-08-03 — clearing `course_program` is deferred out of the backfill entirely, for classified and unclassified notes alike.** The rule above ("clear `course_program` once the level moves out of it") is retained as intent but is **not** executed by PR 4. Three reasons, the first of which stands alone:

1. **It achieves nothing for generation.** `effectiveAuthoringDomain` resolves `domainContext` first and only falls back to `courseProgram`, so once a note has a Domain Context its legacy label can never reach a prompt again. The generation defect these 49 notes represent is fixed by *setting* the two new axes, not by *clearing* the old one. Clearing is cosmetic.
2. **It is not reversible**, which contradicts this ADR's own promise that Release A is "additive and reversible." For a note reclassified out of `High School` the original label is unrecoverable — a `JUNIOR_HIGH` note cannot be distinguished afterward from one that was always `Junior High`.
3. **It would activate a live frontend defect.** `note-editor-page-client.tsx:269` populates `profileCourseProgram` unconditionally, while the `isEditMode` guard at `:270` gates only the draft prefill; `:592` then resolves `draft.courseProgram || profileCourseProgram` and writes the result into the payload at `:620`. On a note with a cleared `course_program`, an admin sees an empty Course/Program field while their **own profile program** is silently submitted on save — reintroducing exactly the free-text program contamination this ADR exists to remove, on canonical official notes. The defect is pre-existing and already affects null-program notes; deferring the clear simply declines to multiply its blast radius by 38.

What counts as a *program* value — and therefore whether `Grade School` and `Junior High` survive at all — is properly PR 5's decision, which must already rule on `Civil Service`, `Biology`, `Professional / Board Exam Review`, and `Self Study / Personal Learning`. Adding two more exclusions there is zero marginal work; doing it here is an irreversible write with a known active hazard.

**Left open, deliberately, at the time this corollary was written — CLOSED 2026-08-03, before R4 ran:** the same eviction applies in weaker form to the 11 Senior High strand notes, which would keep `course_program` and gain `domain_context = GENERAL_EDUCATION` — so `Senior High – STEM` would stop reaching the prompt and STEM, ABM, and HUMSS would collapse to one domain constraint. The level is preserved there, so it is not this regression; it is risk R4 (a broader domain label replacing a narrower one) on live notes.

**Resolution:** production review found Physics notes under both ABM and STEM whose strand-specific framing would have collapsed, and the curator confirmed keeping the strand in the authoring-domain fallback. **V105 therefore sets only `learner_level = SENIOR_HIGH` and leaves `domain_context` NULL**, so the strand keeps reaching the prompt. This was settled on production evidence rather than the generate-under-both-values tie-break, and it is why the wider R4 pass (see "R4 verification" above, resolved 2026-08-04) used a Strength of Materials note instead of a strand note. `v0.70.0` subsequently seeded all three strands into the `course_programs` catalog, consistent with treating them as curriculum tracks rather than bare levels.

> The word "unpublished" appeared in the ratifying instruction alongside "unclassified." This ADR interprets it conservatively as *leave unclassified*, and does **not** authorize flipping `visibility` from `PUBLIC` to `PRIVATE` — all 11 notes are live official public content, and silently withdrawing published material is precisely the kind of destructive side effect rule 2 below forbids. If actively unpublishing them is intended, that needs its own explicit decision.

**2. Existing generated assets are preserved, but their semantic reach does not widen automatically.**

> **Principle: preserve existing assets, but do not expand their semantic reach until their compatibility has been deliberately reclassified.**

Applies to `exam_question_pool` and `challenge_quiz_question_bank` rows generated before this ADR:

- Existing questions **continue to be reusable for their original source Note.** No behavior is taken away.
- Domain Context is backfilled onto those rows **from the source Note, only where the mapping is deterministic.**
- **Legacy `course_program` metadata is not evidence that a question is reusable across every newly-applicable program.** A question generated when a note belonged to one program carries no warrant for the ten programs the note may later be applicable to.
- Until a PR explicitly re-keys and audits compatibility, legacy rows stay **source-note-scoped** and must not enter broader cross-note or cross-program sharing.
- Rows whose source Note has no confidently resolved Domain Context remain usable **only through their existing narrow path**, or are excluded from shared retrieval. They are **not deleted**.
- **No destructive regeneration and no bulk retirement of existing questions,** ever, under this ADR.

**Clarification, so this is not read too broadly:** the cross-*user* Official template sharing shipped in `v0.60.0` (`OfficialChallengeQuizTemplateService`) is already source-note-scoped — an adopter's copied questions trace back to one source note via `copiedFromNoteId`. This policy does **not** restrict or disable it. What it restricts is *new* cross-program pooling that would treat many-program applicability as a licence to share questions across notes.

### Authoring populates by inference, not manual classification (direction, added 2026-08-04)

**Status: agreed direction, UNGATED as of 2026-08-13, in implementation as `v0.75.0` — see "Sequencing" at the end of this section for why both original gates turned out to be already satisfied.** The four axes above are unchanged. What changes is how their values get set. **Scope of `v0.75.0` is depth only; Domain Context is deliberately excluded** under constraints 1 and 2 below.

**The reframing that produced this.** The question "should a Note have a Learner Level?" was the wrong one. The right one is **"what is the canonical source of educational depth?"** — and the answer is *the content itself*. Grade School Algebra, Senior High Algebra, College Engineering Algebra and Board Exam Review Algebra are **different knowledge artifacts**, not one artifact with four quiz settings: the explanation, examples, terminology, Study Pack, flashcards and quizzes all differ. Depth is therefore a property of the note, which is what `notes.learner_level` already encodes. This makes explicit what rule 2 above only implied.

Two rejected placements, both recorded so they are not re-proposed:

- **Depth on the reader alone.** Removing the note axis makes depth fall back to whoever is reading, so a College-profile learner studying a Grade School Algebra note receives a college-level quiz. That failure is the exact scenario the note axis prevents.
- **Depth on the program (`course_programs.learner_level_id`).** One program spans several authored depths — Civil Engineering runs Year 1 through Board Review; Nursing runs College and Board Review. Normalising the relationship into an FK asserts one program has one depth, which is false, and re-creates the conflation this ADR exists to remove. It relocates the coupling rather than solving it.

**But the authoring surface currently exposes implementation concepts rather than educational ones.** Asking an admin to reason about three orthogonal metadata axes is technically correct and cognitively wrong. A teacher thinks *"I'm writing Engineering Algebra for first-year engineering students."* Everything else should be inferred where it can be, and confirmed rather than composed.

**Direction:** keep the four-axis knowledge model; evolve authoring toward **inferred metadata with explicit human override**, instead of manual classification everywhere.

#### Constraints on any inference implementation

These are binding on the design, not suggestions. They exist because the naive version of this proposal breaks two mechanisms this ADR already depends on.

**1. Only a curation container may infer depth. Subject and Domain Context may not.**

The proposed order was Review Set → Subject → Domain Context → defaults → override. The middle two are invalid *as depth sources*:

- **Subject cannot imply depth** — `Algebra` is the worked counter-example from this section's own opening. A subject that spans four depths cannot select one.
- **Domain Context must not imply depth.** It is the *how it is authored* axis; depth is *how deep*. Inferring one from the other re-couples exactly what the four-axis split separated, and `Engineering Mathematics` spans Year 1 through Board Review just as its program does.

A **Review Set / subject plan** is a legitimate source because it is a curation container with a declared purpose — a CE Board Review set is board-depth by construction. The author's own profile level is a legitimate weak fallback. So the supported chain is **Review Set → author profile → explicit override**, and it is deliberately shorter than proposed.

**2. Inference is a UI pre-fill. It must never be a server-side default write.**

`domain_context IS NULL` is load-bearing: this ADR uses it as *the* marker of "not yet promoted," and the promotion backlog is a one-line query over null-context notes. A server-side default would populate that column for every note and **silently destroy the mechanism** — the backlog query would return nothing and the governance floor would become unobservable.

Pre-filling a form control is safe: the value is only persisted because a human saved it, so a stored value continues to mean "a person decided this." A confidently-wrong stored value is worse than NULL, because NULL is visible and a wrong value is not.

**3. Depth granularity is capped at the existing `LearnerLevel` values — Year 1–4 is explicitly out of scope.**

The Civil Engineering example above lists Year 1 through Year 4. `LearnerLevel` has a single `COLLEGE` value and will keep it. Year-level granularity would multiply the value set for a distinction the generation prompts do not currently act on, and the same governance instinct that keeps Domain Context to eight values applies here. If per-year depth ever becomes real, it is a separate decision with its own evidence, not a quiet enum expansion.

**4. Renaming the user-facing label is in scope; `Intended Audience` was not available during the authoring transition.**

"Learner Level" is implementation vocabulary and may be relabelled for admins — `Educational Level` and `Authored Depth` are both viable. During the transition, `Intended Audience` was unavailable because `notes.target_profile_type` occupied that label in the editor as *"Who is this note for?"* `v0.83.0` removed that editor field; any future depth-label change is still a separate copy decision, and the column stays `learner_level`.

#### Sequencing — BOTH GATES CLEARED (amended 2026-08-13, `v0.75.0` kickoff)

**Status: cleared. This section previously blocked the work on two prerequisites; neither still holds, and both had been satisfied for some time before anyone checked.**

- **R4 — RESOLVED 2026-08-04.** The generate-and-diff verification ran against production once `v0.70.0` deployed and passed on all three steps; zero of five drift checks fired. See *R4 verification* in this ADR. It surfaced no unexpected authoring or generation behaviour, so it informs this design by confirming it rather than by amending it.
- **Editability — SHIPPED IN `v0.70.0`; narrowed in `v0.83.0`.** On a `STUDY_PACK_READY` note, Edit stays on Note Detail, and Teacher/Admin authors may edit Domain Context and Note Learner Level through `private-note-detail-page-client.tsx`'s `canEditAuthoringMetadata` path. Target Audience was removed from that panel in `v0.83.0`. Correcting either durable authoring axis shapes *future* generation only and never touches the existing Study Pack.

**Why this correction is recorded rather than quietly deleted.** A stale gate in an ADR is more expensive than a stale line in a feature doc, because an ADR outranks a feature doc where they disagree — so anyone checking whether inference work was authorized would read this section, find a prerequisite stated as unmet, and correctly defer. The gate was satisfied in `v0.70.0` and the text describing it was never revisited. **The general lesson, which applies to every gate in this ADR: a prerequisite written as a blocker must be re-read against current code before it is trusted, not carried forward on its own authority.**

**What remains binding in this section: constraints 1–4 above, all four unchanged.** Clearing the sequencing gate authorizes the work; it does not relax a single constraint on how the work is done. In particular, **constraint 1 names an inference chain for depth only** — no source is authorized for Domain Context anywhere in this section, and constraint 2 explains why a pre-fill there would be actively destructive.

### Alternatives considered

- **Inverse mapping (program → domain contexts), notes stay single-valued.** Far cheaper — dozens of rows, and already the shape `ExamGoalConfig` uses. Rejected: it breaks as soon as applicability is per-note rather than per-context (Engineering Algebra applies to eleven programs, Engineering Statics to nine), forcing a sparse override table plus a second mechanism, and it makes applicability implicit when it should be explicit. **Retired as a fallback 2026-08-03, on evidence rather than argument:** Query K showed sharing is ragged *and crosses program families* — `Construction Materials` is shared by Civil Engineering and Architecture, while `Engineering Sciences` subjects are shared by differing subsets (Strength of Materials broadly, Hydraulics narrowly). Under ragged cross-family sharing this alternative needs the per-note override table immediately, making it `note_course_program` with extra steps. Not a viable fallback at any cost level.
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
- Note cards do not display Domain Context; the value remains curator-facing authoring metadata and an LLM input. Applicable Programs surface on note cards as a count and on note detail as a collapsed disclosure.
- `notes.target_profile_type` **survives only as retained migration evidence**. It remains `NOT NULL`, constrained, indexed, readable by SQL, and written on create by `NoteService.resolveTargetProfileType`; it is absent from product requests, responses, authoring, display, discovery, and generation context. Storage retirement waits for `[CHECKPOINT — due 2026-09-16]`.

**Evidence base (production, 2026-08-03).** This ADR is not taken on forecast. The vocabulary audit (`03`/`04-vocabulary-followups.sql`, results in `05-vocabulary-results.md`) established:

- The program field already conflates **four different kinds of thing** — degree program, learner level (49 notes hold a K-12 grade level), program family (`Engineering`), subject (`Biology`), and activity (`Civil Service`, `Professional / Board Exam Review`, `Self Study / Personal Learning`). This is the empirical case for the four-axis split.
- **Cross-program duplication has already begun**, at n=2 engineering programs: `Strength of Materials` carries "Stress and Strain in Strength of Materials" (Civil Engineering) and "Stress, Strain, and Material Strength" (Mechanical Engineering) — the same knowledge as two notes. Nine further Civil Engineering SoM notes are queued to need a twin per additional engineering program.
- An exact-title duplicate-content ratio of **0.00% across 886 official public notes**, which the above proves is **too weak a measure to trust** — it cannot see semantic duplication. Do not cite it as evidence of no duplication.
- The authoring rule discriminates correctly rather than over-collapsing: Nursing-Pharmacology's 15 notes are genuinely nursing-framed ("Medication Administration Rights in Nursing", "Safe Medication Practices in Nursing") against Pharmacy's generic "Antibiotics: Mechanism of Action and Resistance" — separate notes are right there, and rule 6 says so.

An earlier draft proposed falsifying this ADR via a `[CHECKPOINT — due 2027-02-01]` on whether cross-program reuse ever materialized. **That checkpoint is retired: it was answered affirmatively on 2026-08-03, before it was needed.**
