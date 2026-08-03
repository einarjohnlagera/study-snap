# Canonical Knowledge Architecture — Critique, Audit & Migration Plan

**Planning document. Nothing here is authorized for implementation.** Written 2026-08-03 against `docs/gpt-context-v0.68.0` (post-`v0.68.0` signoff), from a direct read of the code, not the feature docs.

---

## 0. Verdict

**The architecture is right in direction, and the codebase contains harder evidence for it than the proposal itself offers.** But as written it is three initiatives fused into one, with wildly different cost, risk, and reversibility — and it rests on one factually wrong premise about the current schema.

Three claims that should change how this gets sequenced:

1. **Content Context is the load-bearing piece, and it is cheap.** One additive nullable column, one resolver change, three prompt-builder changes. It is the only piece that is *forced* — the current prompt contains an instruction that is logically unsatisfiable under many-valued programs.
2. **Content Context alone unblocks comprehensive Official Review Sets.** Review Sets already compose notes by explicit reference with no program constraint. A curator can author one canonical "Engineering Foundation / Algebra" note and add it to eleven engineering Review Sets *before any join table exists*. Measured against the stated success metric — "does this reduce the effort to build comprehensive Official Review Sets" — the many-to-many relation contributes very little; it buys discovery, filtering, and SEO reach instead. Both are worth having. They are not the same bet and should not be gated together.
3. **The many-to-many cannot ship in one release, and is not reversible.** It touches 59 backend main-source files and 40 frontend non-test files, rewrites two hot paginated read paths, and once badges and filters read from the join, rollback requires a migration. That directly violates clause 2 of this ROADMAP's own bootstrap test ("fits one release slot and is reversible — no schema/pricing/nav commitment that needs a migration to withdraw").

Recommended shape: **the proposed architecture, unchanged in substance, delivered as four separately-gated steps with Content Context first.**

---

## 1. Critique

### 1.1 What is right — and the code evidence for it

**"Programs do not own knowledge" is correct, and the strongest proof is in the prompt builder.** `OpenAiLlmStudyPackService.java:1535-1542`, in `buildGenerationContextBlock`:

```
Course / Program: {value}
Domain constraint: treat the course/program above as the authoritative academic domain.
All content, terminology, examples, and question framing must belong to that domain.
Do not blend in material from unrelated disciplines.
```

This instruction is **logically unsatisfiable** if the field holds Civil + Mechanical + Electrical + Electronics + Computer + Industrial + Chemical + Mining + Agricultural + Geodetic + Sanitary Engineering. There is no single authoritative domain to name, and "do not blend unrelated disciplines" becomes self-contradictory. The proposal's instinct that Applicable Programs "should NOT simply be concatenated into the prompt" is not a stylistic preference — concatenation would actively break a constraint the system currently depends on. Content Context is the fix, and it is required by the many-to-many, not optional alongside it.

**Separating "where it appears" from "how it is authored" is the correct cut.** Today `notes.course_program` carries five unrelated responsibilities simultaneously: the LLM domain constraint (`:1536`), the private Library filter facet (`NoteLibraryRepositoryImpl:277-279`) and its facet-count query (`:189-195`), the Public Library slug filter and free-text search predicate (`PublicLibraryRepositoryImpl:198-200`, `:235`), the Exam Hub mapping key (`ExamGoalConfig` ↔ `frontend/lib/exam-hub-config.ts`), and the note card badge. Those five want different cardinality. That is the actual bug.

**Review Sets staying free-composition is correct, and is already true.** Collection membership is explicit note references; nothing validates a note's program against its collection's. No work is needed to preserve this — only an explicit written rule so a future change doesn't "helpfully" add validation. See §2.6.

**Not collapsing every Algebra into one note is correct**, and the "reuse when learning objective, depth, and treatment are materially the same" test is the right authoring rule. Worth putting verbatim into `docs/features/notes.md`, because it is the only thing standing between this architecture and a curator quietly recreating the duplication by hand.

**User Learner Level vs. Note Learner Level is a real distinction, and today there is no note-level depth signal at all.** `NoteEntity` has no learner-level field (verified — `NoteEntity.java:26-82`). `StudyPackGenerationContext.learnerLevel` is populated exclusively from `user.getLearnerLevel()` (`StudyPackGenerationContextResolver:30`, `:69`, `:97`). So "the Note's Learner Level wins" is not a precedence change — the losing party doesn't exist yet.

### 1.2 The migration premise is wrong, and it is the biggest hidden cost

The proposal states: *"Every existing note can be migrated automatically using its current Course / Program. There are currently no significant duplicate Official Notes, so this migration should be straightforward."* — and specifies the join as `note_course_program(note_id, course_program_id)`.

**There is no `course_program` table.** There never has been:

- `V38__user_learning_profile.sql`: `users ADD COLUMN course_program VARCHAR(120)`
- `V39__note_course_program.sql` (name notwithstanding): `notes ADD COLUMN course_program VARCHAR(120)` — a plain column, not a join table
- `UpsertNoteRequest.courseProgram` (`:10`) has **no validation annotation** — no `@Pattern`, no enum, no length bound beyond the column's
- `GET /course-programs` returns `List<String>` built from `SELECT DISTINCT`-style aggregation over live rows (`CourseProgramController` → `NoteService.listMineCoursePrograms` / `listPublicCoursePrograms`)
- `StudyPackService:746` calls `note.setCourseProgram(...)` — **an LLM suggestion can write into this field**
- The vocabulary drift is already documented in-code, in two files that must be hand-synced: *"CourseProgram values must match production DB values exactly; 'Medical – Surgical Nursing' uses U+2013 (en-dash), not a hyphen"* (`ExamGoalConfig:52-55`, `exam-hub-config.ts:12-13`)

So the current program vocabulary is **open-ended, user-typed, partly machine-generated, and already known to contain characters that break exact matching.** Building a catalog from it is a data-reconciliation project with editorial judgment calls (is "BS Nursing" the same catalog entry as "Nursing"? is "Senior High – STEM" a program at all, or a Content Context?), and every mis-merge silently drops notes out of a filter.

**This is knowable before committing.** Two queries, runnable today, decide whether step 2 is a half-day or a two-week job:

```sql
-- 1. Note-side vocabulary size and shape
SELECT course_program, count(*) AS notes,
       count(*) FILTER (WHERE visibility = 'PUBLIC') AS public_notes
FROM notes
WHERE course_program IS NOT NULL AND trim(course_program) <> ''
GROUP BY course_program
ORDER BY notes DESC;

-- 2. User-side vocabulary (drives personalization + Exam Hub resolution)
SELECT course_program, count(*) AS users
FROM users
WHERE course_program IS NOT NULL AND trim(course_program) <> ''
GROUP BY course_program
ORDER BY users DESC;
```

Run these before scoping step 2. The plan's cost is genuinely unknown until they do.

### 1.3 This is three initiatives, and their economics are not comparable

| | Content Context | Programs many-to-many | Note Learner Level |
|---|---|---|---|
| Schema | 1 nullable column | catalog + families + join + backfill | 1 nullable column |
| Read paths touched | none | private Library filter + facet counts, Public Library filter + search, Exam Hub resolution, 6 note-card surfaces, analytics | none |
| Reversible? | yes (drop column) | **no** — once filters/badges read the join | yes, but see §2.3 |
| Unblocks canonical authoring? | **yes, on its own** | no (Review Sets already compose freely) | no |
| Release slots | 1 | 3+ | 1 |

Gating all three behind one decision means the cheap, forced, reversible piece waits on the expensive, optional, irreversible one.

---

## 2. What the proposal missed

### 2.1 A fourth note-level axis already exists and nobody mentioned it

`notes.target_profile_type` is **NOT NULL** (`NoteEntity.java:56-58`) and holds `NoteTargetProfileType`: `STUDENT`, `BOARD_TAKER`, `PROFESSIONAL`. It is set from `UpsertNoteRequest.targetProfileType` (`:12`).

Add Content Context + Note Learner Level + Applicable Programs and a note carries **five** metadata axes, two of which look like they overlap: "authored for BOARD_TAKER" and "authored at PROFESSIONAL level" appear to answer nearly the same question.

**Checking what it actually does resolves the conflict, and reframes the ruling.** `target_profile_type` is not write-only — it is read as a **Public Library audience filter**: `PublicLibraryRepositoryImpl:176-178` (`and n.target_profile_type = :targetProfileType`), `NoteController:594` and `:636` (`targetProfileType` request param → `resolvePublicAudienceFilter(audience, targetProfileType)`), `NoteRepository:106` and `:131`. It is projected by both library read paths (`PublicLibraryRepositoryImpl:62`, `NoteLibraryRepositoryImpl:56`) and carried on `BulkGenerationResultEntity:35-36`. It never reaches a prompt.

So it sits on the **discovery axis alongside Applicable Programs, not on the depth axis** — there is no genuine conflict with Note Learner Level. That is the good news. The real question it raises is the opposite one: **Applicable Programs may make it partly redundant**, since it is a coarse three-value audience facet (`STUDENT` / `BOARD_TAKER` / `PROFESSIONAL`) doing a blunter version of what a program list would do precisely.

**Ruling still needed in the same decision, but it is now cheap.** Recommended: keep `target_profile_type` as-is with its documented meaning narrowed to *audience framing for discovery only* — never depth (Note Learner Level's job), never generation. Revisit whether it survives at the *end* of step 3, once program facets are live and it can be judged against real filter usage rather than in the abstract. Do not retire it now: it is `NOT NULL`, it is a live public filter, and removing it is a separate reversible-only-with-a-migration decision that has no reason to ride along with this one.

### 2.2 Note Learner Level reverses an explicit current rule

Immediately below the Domain constraint, `OpenAiLlmStudyPackService.java:1540-1541` emits, for static content only:

```
Content calibration: use the Course / Program above to set depth, vocabulary, terminology,
and examples. Do not use learner level to calibrate static note or Study Pack content.
```

That is a deliberate design decision: **today, static content depth is set by course/program and learner level is deliberately excluded**; learner level enters only for quizzes/exams (`buildLearnerContextBlock` vs. `buildContentContextBlock`, `:1519-1525`). "Note Learner Level determines Study Pack generation, Summary, Key Concepts, Flashcards, Memorization, Static Question Pool" reverses this.

That is fine as a decision, but it has two consequences the proposal doesn't account for:

- **Every existing Official Study Pack was generated with no level signal whatsoever.** Introducing Note Learner Level creates a silent old/new inconsistency across the entire Official Library — notes authored after the change get level-calibrated content, notes before it don't, with nothing on the surface distinguishing them.
- **Closing that gap means regeneration**, which `CLAUDE.md`'s versioning rule gates behind explicit user confirmation and in-place update. For admin-owned Official content that is operationally fine, but it is a curator task with real LLM cost, not a migration step.

Recommended: Note Learner Level takes effect for newly generated content only, and the feature doc states plainly that pre-existing packs are level-signal-free until regenerated. Do not describe the backfill as free.

### 2.3 The two question-pool tables key on *user* learner level and must be re-keyed

- `exam_question_pool.learner_level` (`V58`, `ExamQuestionPoolEntity:41-43`) is written from `target.context().learnerLevel()` (`ExamQuestionPoolService:166`, `:174`) — which the resolver populates from `user.getLearnerLevel()`.
- `challenge_quiz_question_bank.learner_level` (`V96`) likewise, and it is part of the claimable index: `idx_challenge_quiz_question_bank_claimable(user_id, study_pack_id, learner_level, claimed_session_id, generated_at)`.
- `ExamQuestionPoolService.sameLearnerLevel()` (`:368-374`), called at `:101`, **invalidates a pool when the current user's level differs from the level it was generated at.**

If Note Learner Level wins, `:101` must compare against the *note's* level, not the user's — otherwise the invalidation is nonsensical (a College note's pool would be discarded and regenerated the moment a Grade School user touches it). Two decisions are needed and should be stated, not discovered:

1. Which level is written to `learner_level` going forward (note's).
2. What happens to existing rows whose value is a *user's* level. Either treat them as unkeyed (set to `NULL`, accepting one stale-pool generation) or force one invalidation pass. Pick one; silently reinterpreting the column's meaning is the worse option.

**Adjacent, larger, and explicitly out of scope:** pools and banks are keyed by `study_pack_id`, and adopted public-note copies get their own StudyPack row, so N adopters of one Official note produce N pools. "Canonical knowledge" does **not** imply canonical generated artifacts under this proposal. Worth stating in the ADR so nobody later assumes it does.

### 2.4 Exam Hub resolution returns exactly one slug

`getExamSlugForCourseProgram` (`frontend/lib/exam-hub-config.ts:52-62`) returns a single `ExamHubSlug` via `.find(...)`. It drives the public note detail Exam Hub callout banner and the Dashboard `GoalPromptBanner` (`goal-prompt-banner.tsx:34`). `ExamGoalConfig.getCoursePrograms(slug)` is the backend mirror and must stay hand-synced.

With many-valued programs a note can map to several hubs, and `.find()` would silently pick whichever slug is declared first in `EXAM_HUBS` — a config-order-dependent, non-obvious behaviour. A deterministic tie-break rule is needed (recommended: resolve the hub from the *learner's* own program/goal when authenticated, and from Content Context otherwise — never from an arbitrary pick out of the note's program list).

### 2.5 `isQuantitativeContext` would be diluted by a program list

`OpenAiLlmStudyPackService:1568-1590` concatenates `courseProgram + subject + tags + conceptHints + summary` into one lowercase haystack and keyword-matches `QUANTITATIVE_KEYWORDS` to decide whether to generate computational content. Joining eleven program names into that haystack means **one program whose name contains a quantitative keyword flips a non-quantitative note to quantitative.** This must read Content Context, never the program list.

### 2.6 Review Set program is behavioral, and can now diverge from its notes

`NoteCollectionEntity.courseProgram` is not display-only:

- `NoteCollectionService.listPublic(courseProgram)` (`:175-176`) filters **published Review Sets** by program.
- `cascadeCourseProgramToBlankChildren` (`:1231-1242`) cascades a parent's program down the collection hierarchy.
- Collection copy paths carry it forward (`:1305`, `:1346`).

Once notes have many programs, a Civil Engineering Review Set will legitimately contain notes not applicable to Civil Engineering (and, more often, notes applicable to ten programs beyond it). Explicit rule required: **a Review Set's course/program is a curation label. It is never derived from its notes' Applicable Programs, and its notes are never validated against it.** Without that written down, someone will eventually add the "obvious" consistency check and break curated composition.

### 2.7 Personal copies must not inherit curation metadata

`NoteService:264` copies `courseProgram` into a copied note. Applicable Programs are a *curation* property of a canonical Official note — they describe editorial reach, not anything about a learner's personal copy. If they copy, the private Library's program facet fills with eleven-program notes for every adopter, which is noise.

Recommended rule: **copies inherit Content Context and Note Learner Level (both affect generated content, and the copy carries a copied StudyPack) but do not inherit Applicable Programs.** Note that `CLAUDE.md`'s copy rules are already a documented exception minefield ("Public-note copies include the linked StudyPack… owner self-copies exclude generated content") — this needs to be written as a third explicit rule there, not left to inference.

### 2.8 `users.course_program` is free text too

If programs become catalog-backed, the user's own program must resolve to a catalog entry for `getExamSlugForCourseProgram`, the `GoalPromptBanner`, `resolveForBulkGeneration`'s fallback (`StudyPackGenerationContextResolver:71`), and the resolver's `firstNonBlank(note, user)` chain (`:31`) to keep working. Users who typed something off-catalog need a defined fallback — and there is a live signal that this is not a rare edge case: 40.1% of accounts have `profile_type` still NULL (ROADMAP Backlog Index, 2026-07-28), so onboarding-collected fields are frequently incomplete.

Recommended: keep `users.course_program` as free text with an *optional* nullable catalog FK. Do not make the user-side a hard catalog reference; personalization degrading gracefully matters more than referential purity here.

### 2.9 The read paths this rewrites are the ones that already needed a performance release

`PublicLibraryRepositoryImpl:198-200` filters on a normalized program slug, and `:235` includes `lower(coalesce(n.course_program,'')) like :searchPattern` inside the free-text search predicate. `NoteLibraryRepositoryImpl:277-279` filters, and `:189-195` computes facet counts with `GROUP BY n.course_program`.

Under a join table, all four become `EXISTS` subqueries or joins on a hot, paginated, already-performance-sensitive path — `v0.51.0` was a dedicated *Read-Path Performance Pass II* release. Budget for either a denormalized `program_slugs text[]` cache column on `notes` (GIN-indexable, keeps search a single-table scan) or a covering index on the join, and decide which **before** writing the query changes, not after a production regression.

### 2.10 The duplication being solved is currently hypothetical

Honest framing, because the proposal's maintenance argument reads as though duplication already exists: there are **four** Official course programs today (ALE/PNLE/LET/CPALE per `ExamGoalConfig`) across ~697 official public notes (ROADMAP, 2026-07-28), and the proposal itself says there are "no significant duplicate Official Notes." The duplication cost is entirely **forward-looking** — it materializes at Civil Engineering and beyond. That is a legitimate basis for the decision, and it is exactly the kind of bet this ROADMAP's bootstrap test was written to handle, but it should be stated as a forecast rather than a measured problem. §9 turns it into something falsifiable.

---

## 3. Risks

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **Vocabulary reconciliation, not the join table, is the real hazard.** A mis-merged or missed program value silently drops notes out of a filter with no error | High | Run §1.2's two queries first. Curate the catalog by hand; keep the original string on `notes` alongside the FK for one full release so mis-mappings are recoverable |
| R2 | **Step 3 is not withdrawable.** Once badges/filters read the join, rollback needs a migration — conflicts with bootstrap-test clause 2 | High | Accept it explicitly as a multi-release commitment; do not attempt it in one slot. Keep `notes.course_program` populated in parallel through step 3 as the rollback path |
| R3 | **Five-axis authoring form → curator inconsistency.** Worse than duplication because it is invisible | High | Settle §2.1 before adding any field. Admin form must group the axes with one-line "this controls X" labels, not present five peer dropdowns |
| R4 | **Generation quality could get *worse*.** A vaguer Content Context ("Engineering Foundation") is a weaker domain constraint than "Civil Engineering." This is the one place the new architecture can regress output | Medium-High | Content Context must be a curated taxonomy, not free text, with values specific enough to constrain. Before bulk authoring, generate one canonical shared note and diff its Study Pack against its single-program predecessor |
| R5 | **"Canonical knowledge" does not make generated artifacts canonical.** Pools/banks/Study Packs remain per-copy (§2.3) | Medium | State the limit in the ADR. Cross-user pooling is Company Redefinition Phase 3b and stays separately gated |
| R6 | **Program facets create new indexable SEO surface** (sitemap, canonicals, thin-page risk) while the SEO items that would justify it are gated on GSC access nobody has yet (ROADMAP P1, `[EFFORT]`) | Low-Medium | Do not build program landing pages as part of this. Public Library URLs are subject-keyed (`/public/library/[subject]/[slug]`) — leave them that way |
| R7 | **Reprioritization drops dated measurement obligations.** Two are calendar-bound and near | Medium | See §10 — defer building, never measuring |

---

## 4. Alternatives considered

**A1 — Inverse mapping: `program → content_contexts`, keep `notes.course_program` single-valued.** Applicability expressed once per program ("Civil Engineering draws from Engineering Foundation, General Education, Civil Engineering") instead of once per note-program pair. Dozens of rows instead of thousands; already the shape `ExamGoalConfig` uses (hub → many programs); adding Sanitary Engineering becomes one row instead of touching N notes.

**Not recommended, and as of 2026-08-03 not even a fallback.** It breaks the moment applicability is genuinely per-note rather than per-context — one exception forces a sparse per-note override table *plus* a second mechanism to reason about, which is strictly worse than the join alone. It also contradicts the stated constraint that *"the stored applicability should still remain explicit."*

**Retired on evidence, not argument.** This was originally kept as a genuine fallback if step 3's cost proved prohibitive. `06` §7's Query K then showed sharing is ragged *and crosses program families*: `Construction Materials` is shared by Civil Engineering **and Architecture**, and `Engineering Sciences` subjects are shared by differing subsets (Strength of Materials broadly, Hydraulics narrowly). Under that shape this alternative needs the per-note override table on day one, making it `note_course_program` with extra steps. See `08-taxonomy-validation-and-final-recommendation.md` Q6.

**A2 — Reuse `notes.tags` (already `text[]`) for applicability.** Rejected: tags are user-authored *and* LLM-suggested with no catalog, so this collapses curation metadata back into exactly the free-text soup this architecture exists to escape.

**A3 — Duplicate the content; do nothing.** Rejected on the maintenance argument, with §2.10's caveat that the cost is forward-looking.

**A4 — Ship all four axes in one release.** Rejected: bootstrap-test clause 2, and it makes the forced/cheap piece wait on the optional/expensive one.

---

## 5. Recommended architecture

### 5.1 Four axes, one owner each

| Axis | Field | Cardinality | Sole responsibility |
|---|---|---|---|
| Subject | `notes.subject` (exists) | 1 | **what** the note is about |
| Content Context | `notes.content_context` (new) | 1 | **how** it is authored — the LLM domain constraint |
| Note Learner Level | `notes.learner_level` (new) | 1 | **how deep** — educational depth |
| Applicable Programs | `note_course_program` (new) | N | **where** it appears — discovery only |
| Audience framing | `notes.target_profile_type` (exists) | 1 | **who** it is written for — never depth (§2.1) |

Program Families sit on the catalog (`course_programs.family_id`) and are an authoring shortcut that expands to explicit join rows at save time, exactly as proposed. They must never be consulted at read time to infer applicability.

### 5.2 Resolution rules — one utility, not scattered

Extend `StudyPackGenerationContextResolver` (the existing, documented single resolver; `CLAUDE.md` already says "do not bypass this resolver") and widen `StudyPackGenerationContext` to carry `contentContext` and `noteLearnerLevel`:

1. **Static content** (note body, summary, key concepts, flashcards, memorization, static question pool): Content Context + Note Learner Level. **Never** Applicable Programs. **Never** user learner level.
2. **Quizzes and exams** (Quick Review, Challenge, Adaptive, Long Exam, Board Exam, Interview Practice, Teacher preview): Content Context + Note Learner Level set the floor. User Learner Level may adjust scaffolding and wording only, and **may never lower the curriculum** — this is the proposal's rule and it needs to be a code-level invariant with a test, not prose.
3. **Discovery** (filters, facets, badges, Exam Hub, search, recommendations): Applicable Programs only. Never Content Context.
4. **Fallback chains** (each must be one function, not inlined):
   - Content Context: `note.contentContext` → `note.courseProgram` (legacy) → `user.courseProgram`
   - Level: `note.learnerLevel` → `user.learnerLevel` → `LearnerLevel.COLLEGE` (the existing `DEFAULT_LEARNER_LEVEL`, `:77`)

### 5.3 The card badge problem dissolves

The proposal asks how to display many programs without twelve badges. **Show Content Context as the single badge.** It is single-valued, stable, curator-authored, and is the axis that actually describes what the content *is* — "Engineering Foundation" is more informative on a card than either "Civil Engineering" (wrong: it's shared) or "Applicable to 8 programs" (uninformative: says nothing about the content). Surface the program list only on note detail, as a collapsed "Applicable to 8 programs" disclosure. This is a free win from the four-axis split rather than a new affordance to design, and it keeps all six note-card surfaces on the single shared `SharedNoteCard` content cascade that `v0.50.2` consolidated.

### 5.4 Sequencing — four steps, minimum four releases

**Step 1 — `notes.content_context` (one release; additive; reversible). Do this first.**
New nullable column + a curated `content_contexts` value set. Resolver prefers it over `course_program`. `buildGenerationContextBlock` (`:1535-1542`) reads it for the Domain constraint and content-calibration lines. `isQuantitativeContext` (`:1568`) reads it. `buildSubjectSuggestionGuidanceBlock` (`:1560-1564`) reads it. Admin note authoring and Bulk Generate expose it; normal users never see it (their `course_program` keeps working through the fallback chain). **No read-path, filter, badge, or URL change.**

This is the whole near-term curriculum-authoring unblock: a curator can now author one canonical "Engineering Foundation / Algebra" note whose Study Pack, flashcards, and pools are correctly domain-constrained, and add it to every engineering Review Set — because Review Sets already compose freely (§1.1). Ship this and start authoring Civil Engineering; the rest can follow on its own schedule.

**Step 2 — `course_programs` catalog + `program_families` (one release; mostly data; no behavior change).**
Run §1.2's queries first. Hand-curate the catalog and families. Add a nullable `course_program_id` FK to `notes` and `users` *alongside* the existing strings; populate by mapping. Nothing reads the FK yet. `ExamGoalConfig`/`exam-hub-config.ts` migrate from hardcoded name lists to catalog references — which retires the hand-sync comment and the en-dash landmine as a side effect.

**Step 3 — `note_course_program` + read paths (multi-release; NOT reversible).**
Backfill one row per note from its mapped program. Rewrite the four filter/facet/search sites (§2.9) with the perf decision made up front. Badge change per §5.3. Exam Hub tie-break per §2.4. Admin multi-select + family expansion. Copy rule per §2.7. Review Set divergence rule per §2.6. Analytics per §6.14. Keep `notes.course_program` populated in parallel throughout as the rollback path.

**Step 4 — `notes.learner_level` (one release; additive).**
Column + resolver + prompt wiring. Re-key `exam_question_pool` / `challenge_quiz_question_bank` per §2.3, including the decision about existing rows. Document that pre-existing Study Packs are level-signal-free until regenerated (§2.2).

Steps 1, 2, and 4 are independent of each other and of step 3. Step 4 can precede step 3 if Note Learner Level turns out to matter more for Civil Engineering authoring than program-based discovery does.

---

## 6. Migration inventory (the 14 requested items)

1. **Database.** Step 1: `notes.content_context VARCHAR(64)` nullable. Step 2: `course_programs(id, name, slug, family_id, created_at)`, `program_families(id, name, slug)`, nullable `notes.course_program_id` + `users.course_program_id`. Step 3: `note_course_program(note_id, course_program_id, PRIMARY KEY(note_id, course_program_id))` + index on `course_program_id`; optional denormalized `notes.program_slugs text[]` + GIN index (§2.9). Step 4: `notes.learner_level VARCHAR(32)` nullable. Next free version is **V102** — the numeric max is `V101__concept_health_incorrect_streak.sql`, *not* V99; a lexical `ls` sorts `V9__`/`V90__`–`V99__` after `V100__`/`V101__` and reports the wrong answer. Always derive it numerically (`ls … | sed 's/^V\([0-9]*\)__.*/\1/' | sort -n | tail -1`). `v0.47.1` was a migration-collision hotfix — also check concurrent branches before claiming a number.

2. **API.** `GET /course-programs` (`CourseProgramController`) currently returns `List<String>` from live-row aggregation; it becomes catalog-backed and should return objects (`{id, name, slug, family}`) — a **breaking response-shape change** with frontend consumers in at least `private-note-detail-page-client.tsx:663-668`, `library/page.tsx`, `public-library-page-client.tsx`. Prefer a new `GET /course-programs/catalog` and deprecate the old shape rather than mutating it. New: `GET /program-families`. `POST/PUT /notes` accept `contentContext`, `learnerLevel`, `applicableProgramIds[]`. Library/Public Library list endpoints accept repeated or comma-joined program filters.

3. **DTOs.** `UpsertNoteRequest` (+3 fields, and **add validation** — this is the moment to stop accepting arbitrary program strings), `NoteResponse`, `NoteListItemResponse`, `NoteListItemView` (interface — projection-backed, so every implementer and every `NoteLibraryRepositoryImpl` projection alias changes together), `NoteLibraryCandidateProjection`, `NoteCollectionItemResponse`, `NoteCollectionNoteProjection`, `PublicProfileNoteResponse`, `ContinueStudyingResponse`, `NotesLibraryFilterOptionsResponse`, `DataExportResponse`, `BulkGenerateNotesRequest`, `BulkGenerationResultResponse`, `GenerateNoteFromTopicRequest`, `MeResponse`, `UpdateUserProfileRequest`. Roughly 25 DTO/projection types across the 59 backend files.

4. **Search / filter.** `NoteLibraryRepositoryImpl:277-279` (filter), `:189-195` (facet counts — `GROUP BY` becomes a join aggregate), `PublicLibraryRepositoryImpl:198-200` (slug filter), `:235` (search `LIKE`). `NoteLibraryFilterCriteria` and `PublicLibraryFilterCriteria` go from `String courseProgram` to a collection. Perf decision per §2.9 **before** writing these.

5. **Private Library.** Filter facet becomes multi-valued (a note appears under every applicable program — expect facet counts to sum above the note total, which is correct but needs a UI note). `UserLibraryFilterService`, `library/page.tsx`, `library/exam-builder/page.tsx`. Badge → Content Context (§5.3).

6. **Explore / Public Library.** `public-library-page-client.tsx`, `public/library/[subject]/page.tsx`, `[subject]/[slug]/page.tsx`. **No URL migration** — Public Library is subject-keyed (`/public/library/[subject]/[slug]`), programs are query-param facets only. Exam Hub callout tie-break per §2.4. `PublicProfileService` program-count rollups (`public-profile-page-client.tsx:385-387`) become many-valued.

7. **Admin UI.** Note create/edit exposes Content Context (single-select), Note Learner Level (single-select), Applicable Programs (multi-select), Program Family shortcut (expands to explicit rows client-side, submits the expansion). Grouped with per-axis "this controls X" labels, per R3. Normal-user note editor is unchanged except that its free-text program field now maps to the catalog on save.

8. **Bulk Generate.** `NoteBulkGenerationService`, `BulkGenerateNotesRequest`, `BulkGenerationResultEntity` (has its own `courseProgram`), `BulkGenerationResultService:39`, `StudyPackGenerationContextResolver.resolveForBulkGeneration` (`:62-82`). Bulk Generate is where per-program duplication would otherwise be industrialized, so it needs Content Context in **step 1**, not step 3 — a bulk run should produce one canonical set with a Content Context, not eleven program-flavored sets.

9. **Prompts / templates.** `study-pack-v1/developer.txt`, `note-generation-developer.txt`, `companion-developer.txt` (`:33` — `Course / Program: {COURSE_PROGRAM}`). Builder sites: `OpenAiLlmStudyPackService:649` (`{COURSE_PROGRAM}`), `:1519-1549` (`buildGenerationContextBlock`, both variants), `:1551-1566` (`buildSubjectSuggestionGuidanceBlock`), `:1568-1590` (`isQuantitativeContext`), plus the six per-mode builders at `:709`, `:738`, `:783`, `:816`, `:866`, `:893` that inject `{LEARNER_LEVEL}` / `{LEARNER_LEVEL_GUIDANCE}`. Each quiz mode also has its own `{mode}-developer.txt` + `{mode}-system.txt` pair under `prompts/study-pack-v1/` — audit all of them for program/level references. **Rename the placeholder** to `{CONTENT_CONTEXT}` rather than leaving `{COURSE_PROGRAM}` holding a different meaning; a placeholder whose name lies is how anti-drift failures start.

10. **Question pools.** §2.3 in full: re-key `exam_question_pool.learner_level` and `challenge_quiz_question_bank.learner_level` from user level to note level; fix `ExamQuestionPoolService:101`/`:368-374`; decide the existing-rows policy; note that the `idx_challenge_quiz_question_bank_claimable` index includes `learner_level`. `OfficialChallengeQuizTemplateService:179`, `:247` also stamp learner level onto copied template questions. Out of scope but state it: pools remain per-`study_pack_id`, so canonical *notes* do not yield canonical *pools*.

11. **Backfill.** Step 2: map each distinct `notes.course_program` / `users.course_program` string to a catalog row by hand; leave unmappable values with a NULL FK and the string intact rather than guessing. Step 3: one `note_course_program` row per note from its mapped program (notes with no mapping get no row and fall back to Content Context for display). Content Context backfill is a **curator judgment call, not a script** — the whole point is that "Civil Engineering" and "Engineering Foundation" are different values; a mechanical copy of `course_program` into `content_context` would encode the old duplication assumption into the new field. Recommended: backfill `content_context = course_program` mechanically as a *migration-safe default*, then have the curator revise the ~50-value vocabulary by hand before authoring new shared notes.

12. **Rollback.** Steps 1, 2, 4 are additive nullable columns/tables — rollback is dropping them, with no data loss in existing columns. **Step 3 is not reversible once read paths use it**: rollback requires re-deriving a single program per note. Mitigation is to keep `notes.course_program` written and correct in parallel for one full release after step 3 ships, so a revert restores the old filter behaviour without a data migration. Say this in the release's own Known Limitations rather than assuming it.

13. **Documentation.** `docs/features/notes.md` (the four axes + the reuse-vs-new-note authoring test verbatim), `study-pack-generation.md` (the resolution rules in §5.2), `library.md`, `public-library.md`, `bulk-generation.md`, `admin-dashboard.md`, `collections.md` (§2.6's Review Set divergence rule), `public-notes.md` (§2.7's copy rule), `profile-learning-context.md` and `onboarding.md` (user vs. note level), `challenge-quiz.md` + `exam-hub.md` + `quiz.md` (pool re-keying), `docs/architecture/DATA_MODEL.md`, `docs/product/SPEC.md`. `CLAUDE.md`'s "Generation context is resolved in a shared utility" paragraph and its copy-rules paragraph both need updating. **`EXAM_MODES.md` is a locked contract — this proposal does not touch mode structure, and must not.**

14. **Analytics.** Any `AnalyticsEventType` payload carrying `courseProgram` becomes ambiguous under many-valued programs. Recommended: event payloads carry **Content Context** (single-valued, stable, the meaningful grouping for content analysis) and, where discovery attribution matters, the *filter value the user actually clicked* — never the note's full program list. Per `CLAUDE.md`, add to the Java enum before firing anything new. Also: existing dashboards/queries grouping by `course_program` (e.g. the ROADMAP's gate-check SQL under `docs/claude-prompt/company-redefinition-out/`) will silently change meaning — grep that directory before step 3 ships.

---

## 7. Required ROADMAP changes

1. **Backlog Index row** — mandatory in the same commit as this `-out/` directory, per the ROADMAP's own stated invariant. Proposed text is in §11.
2. **New candidate section**, `## Canonical Knowledge Architecture (candidate — 4-step sequence, step 1 authorizable)`, carrying §5.4's sequencing and §9's metric.
3. **Explicit bootstrap-test note**: step 3 fails clause 2 (not reversible without a migration). Record that this is a knowing multi-release commitment rather than an oversight, so a future kickoff doesn't try to compress it.
4. **Deferral list** (§10) with the build-vs-measure distinction stated.
5. **Supersession note**: this does *not* supersede Company Redefinition Phase 3b (cross-user question pooling). The two are adjacent and often confused — §2.3's closing paragraph is the boundary.

## 8. Required GPT_CONTEXT updates

`docs/gpt-contexts/GPT_CONTEXT.md` is version-stamped, so it updates at the *next* kickoff, not now. What must change when it does: the note data-model section (four axes, not one Course/Program), the generation-context section (the §5.2 resolution rules, replacing the current "note-level `courseProgram` preferred, profile fallback" description), the Library/Explore filter descriptions, and an explicit statement that Applicable Programs never reach a prompt. Add a short "what a Note is not" note — a Note is not owned by a program — since that is the single sentence most likely to be re-derived wrongly by a future session.

---

## 9. Making the success metric measurable

The stated metric ("how much easier does it become to publish Civil Engineering?") is not measurable as written — there is no baseline and no unit. Three that are, all computable **before** step 1 ships:

**Baseline A — duplicate-content ratio.** Official public notes sharing a normalized `(title, subject)` across two or more distinct `course_program` values, as a share of all official public notes. Expected answer today: near zero, consistent with the proposal's own statement. That is the point — it is the *baseline*, and it is the number that would climb if the architecture were never built and Civil Engineering shipped anyway.

**Baseline B — notes per published Review Set.** Current Official Review Sets are described as containing "only a handful of Notes" against a target of several hundred. Record the current distribution now; it is the headline before/after.

**Baseline C — curator-hours per published Review Set.** Manually logged, not derivable from the DB. Rough is fine — the comparison is 10× or nothing.

**Post-step-1 leading indicator.** After step 1, count notes whose Content Context is shared by Review Sets belonging to two or more distinct programs. That is the *direct* measure of canonical reuse actually happening, and it is available without step 3 existing. Worth tracking as a progress signal.

**The originally-proposed kill criterion has been retired — it was answered before it was needed.** This section first proposed a `[CHECKPOINT — due 2027-02-01]`: if no note were being reused across two or more programs' Review Sets six months after step 1, the duplication problem would have been hypothetical and step 3 should not be built.

`04-vocabulary-followups.sql`'s Query J answered that question on **2026-08-03**, affirmatively, and Baseline A turned out to be too weak to have detected it. `Strength of Materials` carries "Stress and Strain in Strength of Materials" under Civil Engineering and "Stress, Strain, and Material Strength" under Mechanical Engineering — the same knowledge, two notes, two programs, invisible to exact-title matching. Nine more Civil Engineering SoM notes are queued to need the same twin per additional engineering program.

So step 3 is justified on evidence rather than held as a bet, and **Baseline A's 0.00% must not be cited as evidence of no duplication** — it measures exact-title collisions only. See `05-vocabulary-results.md` for the full Round 2 read, including the finding that four of the eleven apparently-cross-program subjects (Algebra among them) are in fact the same subject at two learner levels, expressed by abusing the program field.

## 10. What to defer — and what must not be deferred

**Deferrable** (building; no dated obligation):
- Retention H1 + H5 (commitment device + pre-decided return action) — currently "next up," `[EVIDENCE]`-gated
- Messaging Architecture remaining surfaces (paywall modal, Exam Hub upsell, landing page, `PLAN_COMPARISON_ROWS`, `FREE.title`) — explicitly incremental, each needing its own kickoff
- Company Redefinition Phase 4 items 5 and 7
- All `[EFFORT]`/`[EVIDENCE]` Backlog Index candidates

**Must not be deferred** — these are dated *measurement* obligations, cheap to run, and the kickoff checklist's steps 8–9 exist specifically to catch them going stale:
- **Diagnostic Read Round 2 — due ~2026-08-06 (three days out).** Re-cut by the 2026-07-28 target-habit segmentation. Deferring this loses the cohort window; it cannot be run late with the same meaning.
- **Knowledge Impact `[CHECKPOINT — due 2026-09-11]`.** Its kill criterion is already written and its instrumentation already shipped.

Reprioritizing should defer *building*, never *measuring*. A reprioritization that quietly drops both dated checkpoints is precisely the silent drift the gate-types discipline was added to prevent.

## 11. Backlog Index row and ROADMAP section

Both have been written into `docs/product/ROADMAP.md` in the same change set as this directory, satisfying the Backlog Index invariant ("no `docs/claude-prompt/*-out/` directory may exist without a row here"):

- **Backlog Index row** — inserted as the first row of the table, above `Retention H1 + H5`, reflecting the reprioritization. Carries the audit findings, the four-step decomposition, and the gates.
- **`## Canonical Knowledge Architecture (candidate — 4-step sequence, step 1 authorizable, added 2026-08-03)`** — placed immediately before `## Company Redefinition Roadmap — Phase Detail`. Carries the sequencing, the success metric, and the deferral list.

ROADMAP is the single source of truth for both; this document is not duplicated there and should not be kept in sync by hand.
