# Proposed `ADR-001` amendment — remove Target Audience as a note metadata axis

**Status: DIRECTION REVISED 2026-08-15 after a second opinion. Retirement is agreed in principle; immediate removal is NOT.** Originally raised by the owner 2026-08-15 after a product-UX discussion. **Read the REVISED DIRECTION section at the end first — the audit findings above stand, but their sequencing is superseded.** This document exists so the proposal, the audit behind it, the production evidence, and the counter-argument survive the conversation that produced them. **Nothing here is decided, and no code has been changed.**

**What it would amend:** `ADR-001-canonical-knowledge-architecture.md` (Accepted 2026-08-03) currently defines **five** note metadata axes — Subject (*what*), Domain Context (*how it is authored*), Note Learner Level / Authored Depth (*how deep*), Applicable Programs (*where it appears*), and **Target Audience (*who* — discovery only, never depth)**. This proposal reduces that to four.

**In code the field is `targetProfileType`**, backed by `NoteTargetProfileType { STUDENT, BOARD_TAKER, PROFESSIONAL }` on `notes.target_profile_type` and `bulk_generation_result`. "Target Audience" is the product-facing name.

---

## The owner's proposal

Target Audience originally served two purposes: categorise notes by audience, and prevent learners from seeing notes intended for another audience. Since then the model gained Subject, Course / Program(s), Domain Context, and Authored Depth — each owning one responsibility. The claim is that Target Audience no longer owns anything unique and should be removed rather than carried as a redundant axis.

The owner explicitly asked for the conclusion to be challenged before implementation.

## Audit findings — verified in code 2026-08-15, do not re-derive

### 1. Runtime surface

**20 backend files, 12 frontend files, 4 migrations.** Entity (`NoteEntity`), enum (`NoteTargetProfileType`), 4 DTOs (`NoteResponse`, `NoteListItemResponse`, `UpsertNoteRequest`, `BulkGenerateNotesRequest`), `BulkGenerationResultEntity` + service, `NoteService`, `NoteBulkGenerationService`, `NoteController`, both library repositories, `PublicLibraryFilterCriteria`, `NoteListItemView`. Migrations `V44`, `V45`, `V64`, `V73`. Frontend: `note-target-profile.ts`, `public-library-url.ts`, note editor, note detail, bulk generation, public library, onboarding, `api.ts`.

### 2. AI generation — ABSENT, explicitly

**Target Audience appears in no prompt file under `backend/src/main/resources/prompts/`, and in neither `StudyPackGenerationContextResolver` nor `StudyPackGenerationContext`.** It does not participate in Study Pack, quiz, explanation, or adaptive generation. The owner's claim is correct and this was verified by direct search, not inferred.

### 3. Discovery — STILL LIVE, and the owner's claim here was wrong

The proposal states Target Audience "is no longer responsible for discoverability." **That is false as written.** It is an active filter:

- `PublicLibraryRepositoryImpl:197-199` — `and n.target_profile_type = :targetProfileType`
- `public-library-page-client.tsx:1842` — user-facing filter chips
- `public-library-url.ts:8` — a shareable **`?audience=`** URL parameter

Removing it is a **user-visible change to a public, linkable surface**, not a silent cleanup.

In the **private** Library it is selected as a column (`NoteLibraryRepositoryImpl:64`) but carries **no WHERE clause** — projected, never filtered.

### 4. Existing features — the original access-control purpose was never implemented

The audience filter defaults to `NOTE_TARGET_PROFILE_ALL` (`public-library-page-client.tsx:552`) and **nothing anywhere restricts note visibility by audience.** Purpose 2 of the field's original design does not exist in the codebase. No permissions, analytics, reporting, progress, notification, or recommendation dependency was found.

It remains a **curator authoring field** (bulk generation, note editor) and is **displayed** on private note detail. Onboarding writes it via `mapProfileTypeToNoteTargetProfile(profileType)`.

### 5. Database

One column on `notes`, one on `bulk_generation_result`, plus the enum. Dropping is irreversible. Backfilling is not required. See "Historical ambiguity" below.

### 6. Documentation

`ADR-001` (names it as one of five axes), `CLAUDE.md`, `AGENTS.md`, `docs/product/SPEC.md`, `docs/features/notes.md`, `docs/features/public-library.md`, and the GPT context modules. **Removal amends an Accepted ADR and therefore needs explicit ratification, not a refactor.**

---

## The argument that was tried and FAILED — recorded so it is not retried

**Hypothesis:** Authored Depth subsumes Target Audience, because the enums map cleanly — `BOARD_TAKER → BOARD_EXAM_REVIEW`, `PROFESSIONAL → PROFESSIONAL`, and `STUDENT` expands into `GRADE_SCHOOL / JUNIOR_HIGH / SENIOR_HIGH / COLLEGE`. Depth is a strict refinement, so every audience distinction is recoverable.

**Why it fails:** **5,538 of 5,587 notes (99.1%) carry a Target Audience and have NO Authored Depth.** `notes.learner_level` is optional and most notes inherit depth through the resolution chain instead of carrying their own. Removing Audience on this argument would delete the *populated* axis and leave the theoretically-superior one *empty*.

The subsumption is real in the model and absent in the data. **Do not revive this as the justification.**

## The evidence that actually settles it — Course / Program predicts audience

Production, public notes with a Target Audience (945 total: **823 `BOARD_TAKER`, 122 `STUDENT`, 0 `PROFESSIONAL`**):

| Program group | BOARD_TAKER | STUDENT |
|---|---|---|
| Licensure programs (Civil Eng 254, Accountancy 153, Education 146, Nursing 131, Architecture 90, + 10 further engineering fields, Pharmacy, Civil Service) | ~1,073 | **~7** |
| Academic levels used as programs (Junior High 24, High School 9, Senior High strands 10, Grade School 3) | 1 | 43 |
| **Information Technology** | **9** | **63** |

**Outside Information Technology, audience is ~99.3% predictable from Course / Program.** Every licensure program is uniformly `BOARD_TAKER`; every academic-level value is uniformly `STUDENT`. **`PROFESSIONAL` is unused across all 945 public notes.**

Information Technology is the sole genuine mix — and IT has no Philippine licensure board, so those 9 `BOARD_TAKER` notes are more likely mis-tagging than a distinction worth preserving an axis for. **Worth a curator glance before removal**, purely to confirm they were not a deliberate call.

**This supports the owner's original claim.** Course / Program has taken over the discovery responsibility in practice.

## Counter-argument — what is actually lost

1. **A working cross-program filter.** A learner filtering to Student narrows 945 public notes to 122. That is a real, populated facet, not a vestigial chip. After removal, the equivalent intent must be expressed through Course / Program, which cannot say "student-level content across several programs." **The axis that should serve this is Authored Depth, which is unpopulated on 99.1% of notes** — so the capability is genuinely lost until Depth adoption improves. The ADR amendment should say so rather than claim nothing is lost.
2. **Shareable `?audience=` URLs break.** Any external or indexed link carrying that parameter degrades to an unfiltered view. Small footprint, but it is a public contract.
3. **Historical ambiguity.** After the column is dropped, the 5,538 notes with an audience and no depth lose their only explicit audience signal. It remains *inferable* from program at ~99.3% accuracy, but it is no longer *recorded*.

## Phased plan — SUPERSEDED 2026-08-15, retained as history

**⚠️ This four-phase plan is no longer the direction. See REVISED DIRECTION at the end of this document.** It is kept because the phase boundaries remain sound; what changed is that migration must precede removal.

Ordered so each phase is independently revertible and the irreversible step is last.

1. **Amend `ADR-001`.** Five axes to four, recording the evidence: absent from generation, access control never implemented, program predicts audience 99.3%, and the explicit acknowledgement that the cross-program "student-level" filter is lost until Depth is populated. **This is the ratification step and lands before any code.**
2. **Remove the discovery surface.** Filter chips, `?audience=` parsing/building, `PublicLibraryFilterCriteria` field, the WHERE clause. The only user-visible phase.
3. **Remove authoring and display.** Bulk generation dropdown, note editor field, note detail display, DTO fields, onboarding write.
4. **Drop the columns and the enum.** Last, separately, so phases 2–3 can be reverted without data loss.

**Cheaper middle option, if ratification stalls:** stop offering the field in authoring while keeping the column and the filter. New notes stop accumulating a field nobody believes in, nothing is deleted, and the decision stays reversible while Depth adoption catches up.

## Sequencing constraint

Phase 2 touches Public Library discovery. **`[CHECKPOINT — due 2026-09-13]` measures Explore-driven engagement**, and Explore embeds public-library surfaces. Confirm whether that read reaches the filter chips before scoping Phase 2; if it does, Phases 1, 3 and 4 can still proceed while Phase 2 waits.

## Adjacent finding — level-as-program, worth its own row

`Junior High`, `High School`, `Senior High – STEM/HUMSS/ABM`, and `Grade School` are being used as **Course / Program** values on ~47 public notes. Those are academic *levels* — Authored Depth's responsibility leaking into the program axis. `v0.79.0` found the same confusion in learner profiles (`High School` was 7 of the off-catalog learners).

This matters to *this* proposal specifically: **"program predicts audience" holds partly because program is already doubling as level.** That is an argument for the amendment, not against it — but the ADR should name it rather than let a future reader mistake correlation for clean design.

## Open questions for ratification

1. Is the loss of the cross-program "student-level" filter acceptable until Authored Depth is populated — and is populating Depth a commitment or an aspiration?
2. Were the 9 Information Technology `BOARD_TAKER` notes deliberate?
3. Drop the columns (phase 4) or retain them as inert historical data?
4. Does the 2026-09-13 Explore checkpoint reach the public-library filter chips?

---

# REVISED DIRECTION — 2026-08-15, after second opinion and pressure test

## The decision to ratify

> **Target Audience has no long-term architectural responsibility and should be retired — but only after the useful information it currently carries has been migrated into the correct axis, and its live discovery contract has been replaced.**

**This supersedes the "remove it in four phases" plan above.** That plan is retained as history because its audit findings stand; only its sequencing is wrong.

**Two corrections to the earlier reasoning, both accepted:**

1. **"Course / Program predicts audience ~99.3%" is not a removal argument.** It is correlation produced by a board-heavy catalog, not semantic equivalence. Civil Engineering, Nursing, Accountancy and IT can all legitimately carry both college-level and board-review material. If the content mix shifts toward general-student or professional material, the correlation breaks and the axis would have been carrying information after all. **Do not cite it as justification for an irreversible step.**
2. **The IT split (9 `BOARD_TAKER` / 63 `STUDENT`) must be audited, not dismissed.** The earlier proposal called it likely mis-tagging because IT has no Philippine licensure board. That was an assumption. It is equally readable as the one program honest enough to carry two depths — in which case the uniform programs are the anomaly.

## ⚠️ PRESSURE TEST — the revised sequence has a blocking prerequisite

**Step 1 as proposed ("populate Authored Depth from Target Audience") is the exact operation `v0.75.0` rejected, at roughly 5,500× the scale.**

`notes.learner_level` is not inert metadata. It is the **curriculum floor** feeding `StudyPackGenerationContextResolver.effectiveCurriculumLevel`, which keys **both** `exam_question_pool.learner_level` and `challenge_quiz_question_bank.learner_level`. Backfilling depth onto a note therefore changes that note's effective curriculum level.

**And `V96:12` defines `uq_challenge_quiz_question_bank_user_pack_key` as `unique (user_id, study_pack_id, question_key)` — `learner_level` is stored but EXCLUDED from the key.** So changing a note's level does not replace its bank rows; it leaves them in place, tagged at the stale level, unreachable through `sameLearnerLevel` gating. They are orphaned.

This is already a known defect (Backlog Index → *Challenge bank orphans on a learner-level correction*). `v0.75.0` rejected align-on-add specifically because it *"mutates already-generated notes, making authoring corrections routine, which is exactly the condition… that makes that bug unbounded,"* and forbade the depth pre-fill on the inline metadata editor for the same reason.

**Scale makes it worse, not better: ~5,540 of the 5,587 notes carrying a Target Audience already have a Study Pack.** A backfill is not an edge case here — it is the whole population.

### Therefore the sequence needs a Step 0

**Step 0 — fix the Challenge-bank orphan defect before any depth backfill.** Either include `learner_level` in the uniqueness key, or delete/regenerate bank rows on a level change. Without it, Step 1 orphans bank rows across the entire corpus in a single migration. **This is a prerequisite, not a nice-to-have** — the migration is precisely the operation that converts a bounded known defect into an unbounded one.

### Second pressure-test finding — Step 2 cannot complete for the population it exists to protect

The safe mappings cover `BOARD_TAKER → BOARD_EXAM_REVIEW` and `PROFESSIONAL → PROFESSIONAL`. `STUDENT` is deliberately excluded, because it spans four real depths.

But **`STUDENT` is exactly the cross-program job the replacement must preserve** — 122 of 945 public notes, the "student-level material across programs" filter. After a safe-mappings-only backfill, a depth-based filter would serve board reviewers and **return nothing for students**. The replacement would be *worse than the thing it replaces*, for the only segment that needed it.

So Step 2 blocks on curator classification of the `STUDENT` rows, which is unbounded manual work. **That should be sized before the sequence is committed to**, not discovered midway.

### Third — Step 3 is a design task, not a redirect

`?audience=BOARD_TAKER` maps cleanly to `BOARD_EXAM_REVIEW`. `?audience=STUDENT` maps to a **set** of four depth values, so preserving it requires a user-facing *grouping* of depths ("school levels") that does not exist today. That is new UI vocabulary, not URL rewriting.

## Revised sequence

| Step | Work | Blocking condition |
|---|---|---|
| **0** | Fix the Challenge-bank orphan defect (`learner_level` in the uniqueness key, or invalidate-on-change) | **Prerequisite for Step 1** |
| **1a** | Audit the 9 IT `BOARD_TAKER` notes — genuine or mis-tagged? | Informs whether one program can carry two depths |
| **1b** | Backfill depth for the safe mappings only (`BOARD_TAKER`, `PROFESSIONAL`); leave `STUDENT` null for curator review | Step 0 |
| **1c** | Classify `STUDENT` rows using Review Set depth or level-specific metadata; size this before committing | Manual, unbounded |
| **2** | Replace Public Library Audience filter with a depth-based filter, including a grouping for school levels | 1b + 1c |
| **3** | Map `?audience=` URLs onto the depth semantics; never silently degrade to unfiltered | 2 |
| **4** | Remove Target Audience writes: curator field, onboarding mapping, DTOs, services, repositories, UI | 2 + 3 live |
| **5** | Drop the column and enum | Nothing reads or writes it |

**Explicitly ruled out (agreed):** Target Audience must **not** become a runtime depth fallback. It is migration evidence only. Adding it to the resolution chain would create a fifth fallback layer in the one resolver `ADR-001` deliberately keeps narrow.

## Queries owed before Step 1

### (a) RAN 2026-08-16 — mis-tagged, and it changes the migration

All nine: **seven database fundamentals** (ER modelling, SQL joins, ACID, stored procedures, triggers, views, security) authored **2026-07-03**, then **two JavaScript async topics** (event loop, callbacks) on **2026-07-04**. Ordinary IT coursework, and there is no Philippine IT licensure board. Two batches on consecutive days is the signature of a curator session with the audience dropdown left on one value. **`learner_level` and `domain_context` are NULL on all nine**, so nothing independently supports `BOARD_TAKER`.

**Verdict: mis-tagged.** The "one program can legitimately carry two depths" hypothesis is not supported.

**But the migration does not stay unchanged.** Under the safe mapping, `BOARD_TAKER → BOARD_EXAM_REVIEW` would stamp these nine at **board-exam depth** — a wrong curriculum floor on nine public notes, so every future regeneration would author *"Database Views"* and *"Callbacks in Web Development"* at licensure-review depth. **`BOARD_TAKER` is therefore not self-certifying:** it is trustworthy where a licensure board exists and unreliable where one does not.

**Information Technology is excluded from the `BOARD_TAKER` mapping.** Those nine keep NULL depth and remain curator-classifiable, exactly like the `STUDENT` rows.

**No catalog field models licensure status, and the obvious candidate does not work.** `course_programs.exam_goal_slug` marks only the four programs with Exam Hubs (`let`, `ale`, `pnle`, `cpale`). **Civil Engineering — 254 `BOARD_TAKER` notes, the largest population — has none**, so excluding on that column would drop the bulk of the legitimate migration. The exclusion is therefore named explicitly rather than derived, and a general rule would need a new catalog attribute.

**Residual risk, bounded:** other non-licensure programs could carry the same mis-tagging. The distribution shows Computer Science, Software Engineering, Biology, Psychology and Business Administration each holding a single `STUDENT` note and no `BOARD_TAKER`, so the visible blast radius is IT alone.

```sql
-- (a) The IT audit — ANSWERED above. Kept for reproducibility.
SELECT n.id, left(n.title, 70) AS title, n.learner_level, n.domain_context, n.created_at::date
FROM notes n
LEFT JOIN note_course_program ncp ON ncp.note_id = n.id
LEFT JOIN course_programs cp ON cp.id = ncp.course_program_id
WHERE n.visibility = 'PUBLIC' AND n.target_profile_type = 'BOARD_TAKER'
  AND coalesce(cp.name, n.course_program) = 'Information Technology'
ORDER BY n.created_at;

-- (b) Who owns the notes a backfill would mutate? Writing depth onto a LEARNER's
-- note contradicts v0.75.0's "never applies to learners or to an existing note".
SELECT u.role,
       count(*)                                                    AS notes,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM study_packs sp WHERE sp.note_id = n.id)) AS with_study_pack
FROM notes n JOIN users u ON u.id = n.owner_user_id
WHERE n.target_profile_type IS NOT NULL AND n.learner_level IS NULL
GROUP BY 1 ORDER BY notes DESC;
```

**(b) RAN 2026-08-15, and it did change the plan — for the better.**

| Owner role | Notes with audience, no depth | …with a Study Pack |
|---|---|---|
| `USER` (learners) | **4,645** | 4,628 |
| `ADMIN` (curators) | **905** | 905 |

**83.7% of the notes a naive backfill would touch are learner-owned.** Writing `BOARD_EXAM_REVIEW` onto them would assert an authoring decision their authors never made, onto a field that acts as a **curriculum floor** for their future regenerations — exactly what `ADR-001` constraint 2 and `v0.75.0` exist to prevent — and would strand Challenge-bank rows tied to real practice history at scale.

**But the discovery surface does not need them.** The Public Library filter reads **public** notes only: 945 carry an audience, and `ADMIN` owns 905. Those are the same population — curator-authored Official content. **The 4,645 learner notes are private and never reach the surface the replacement must preserve.**

### Consequences — this is now the migration shape

- **Backfill curator-owned notes only (~905).** A curator asserting depth on curator content is legitimate authoring, not an inferred write on someone else's work.
- **Do not backfill learner notes at all.** Not because it is hard — because it buys nothing. They feed no filter.
- **Step 0's blast radius drops from ~5,550 notes to ~905**, and predominantly the curator's own bank rows: a learner who copies a public note gets their own note and Study Pack, so their bank is keyed to *their* `study_pack_id` and is unaffected by a change to the source. **Step 0 remains a prerequisite** — 905 is tractable, not negligible.
- **The unbounded curator-classification worry largely dissolves.** The `STUDENT` rows needing human judgement sit inside those 905, and public `STUDENT` notes total 122, concentrated in Information Technology and the academic-level values.

### The question this leaves for the ADR amendment

**Should learner-owned notes keep a Target Audience during the transition?** Onboarding currently writes it from profile type. If writes stop but no backfill occurs, ~4,645 learner notes carry a value nothing reads. Harmless, but the amendment must answer it explicitly — inert historical data or cleared — rather than leaving it to whatever the migration happens to do.
