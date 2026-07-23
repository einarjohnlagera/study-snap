# Reusable Assets, Cross-User Question Pools & the Reviewer-Object Decision

> Planning document. No code changed. Session R3 of the company-redefinition series — extends
> `fable-out/01-foundation-architecture.md` and `fable-out/02-matching-coverage-flywheel.md`'s
> reuse-first pipeline and knowledge-matching design; does not redesign either. Builds on
> `company-redefinition-out/01-strategic-redefinition.md`'s "learning OS" framing.

## Decisions carried forward

**Cross-user pool model (Official Study Packs only):** `ExamQuestionPoolEntity`/`ExamQuestionPoolService`
stay exactly as shipped (schema, sample-without-replacement logic, PENDING/GENERATING/READY/FAILED
ladder) for the authoring/content side. A new `resolvePoolKey(studyPackId)` step, added inside
`ExamQuestionPoolService` only (zero call-site changes at `LongExamService`/`StudyPackService`/etc.),
resolves the pool row to key off the Official source's `studyPackId` whenever the caller's own note is
either itself Official (`visibility=PUBLIC` + `isOfficialAuthor`, reusing `PublicProfileService`'s
existing check) or a one-hop copy of one (`copiedFromNoteId` + `copiedFromPublic=true`, reusing existing
`NoteEntity` lineage fields — copying already blocks copying-a-copy, so lineage is always exactly one
hop). Falls through to today's per-owner keying (own `studyPackId`) for private notes and copies of
non-Official public notes — unchanged. Resolution is derived on read, never persisted, self-healing
exactly like coverage/readiness (fable-out/01 §1.4): an unpublished/superseded Official source silently
drops adopters back to their own private pool on the next read, no invalidation event needed.
**Two things must change on the shared branch only:** (1) served-question tracking moves from the pool
row (`servedQuestionKeys`) to a new small per-user child table (`exam_question_pool_progress`, keyed
`pool_id`+`user_id`) — sharing one pool across adopters while depleting `servedQuestionKeys` globally
would let one learner's sample-without-replacement history bleed into a stranger's; (2) the
`learnerLevel`-triggered auto-refresh is dropped for Official pools (they generate courseProgram-only,
never learnerLevel-forked, per the already-locked "shared content is never leveled per-user" rule) —
otherwise every adopter with a different learner level would thrash the shared pool. Exhaustion or
source-content drift on an Official pool raises an admin-only signal (Companion-staleness shape)
instead of auto-regenerating; expansion is an Internal Curator action, and generated batches land
pending-review before READY (reuses the review-queue shape already specified in fable-out/01; the
review UI itself belongs to fable-out/03, not redesigned here). Private per-owner pools: fully
unchanged, including auto-regenerate-on-exhaustion.

**Bounded object model:** of the proposal's 8 fields, 5 need zero new work (Note, Summary, Key
Concepts, Explanations, Related Notes — already shipped or already served by existing discovery
queries / fable-out/01's fulfillment mapping); Flashcards stays fully derived, no new field (its ~56%
coverage ceiling is a documented non-goal, not reopened here); Difficulty is cut from this bounded model
(a generation-time knob already exists via `DIFFICULTY_SELECTION`/mixed-difficulty prompts — no new
persisted field is justified); Curated Question Pool is the one genuinely new build, and it is the
resolver + child table above, not a new entity.

**Reviewer decision: (b), label-only — no new entity.** `getCollectionLabels("BOARD_EXAM")` already
returns `Review Set`; "Reviewer" ships as a relabel of that same NoteCollection machinery (fable-out/06's
territory, not redesigned here), never a new exportable object. One-line justification: a new Reviewer
entity's main reason to exist would be being a better exportable/printable unit, and "1 export, ever"
is direct evidence nobody wants that unit — building one now optimizes for a behavior the data says
isn't happening, while the in-app value (Companion, readiness, adaptive practice) has zero export
dependency.

**Where AI still adds value:** curator-side pool expansion (factory, gated); private-note quiz/Study
Pack generation for non-Official content (unaffected, still per-owner on demand); PRO Adaptive Practice
weak-concept follow-ups (explicitly out of scope for pooling — per-learner and reactive by design, the
opposite of shared static content); curator gap-fill note generation (fable-out/01 step 5). AI stays
the intelligence layer behind curation, never the thing a learner is asked to trust directly.

---

## 0. What this session reuses, verbatim, and where

Per hard constraint 5, nothing below redesigns knowledge matching or the reuse search order. Named
reuse:

| From | Reused as |
|---|---|
| fable-out/01 §1.4 ("coverage... derived, never persisted per learner... no stored aggregate means no stale aggregate") | The exact doctrine applied to pool-key resolution in §1 below — resolving the Official source at read time instead of persisting a pointer |
| fable-out/01 §2.4 / §3.4 (Companion `companionStructureSnapshot`/`companionMayBeOutdated` staleness signal shape) | The shape reused for the new "pool may be outdated / pool running low" admin-only signal (§1.4) |
| fable-out/01 §1.2 (`curator_generation_requests` review ladder: `REQUESTED → DRAFT_GENERATED → IN_REVIEW → PUBLISHED/REJECTED`) | The shape a pool-expansion batch's review gate follows (§1.3) — no new review UI proposed, that UI is fable-out/03's |
| fable-out/01 §2.2 Step 5 ("Generate only what is missing... calibrated by courseProgram only, never learnerLevel — these are shared notes") | The anti-drift rule that resolves the learner-level thrash problem for shared pools (§1.2) |
| fable-out/01 §3.1 ("the reuse ladder: Study Pack → plan of packs → Goal of plans → assembled curriculum coverage") | Extended one more rung in §1.5: shared curated question pools |
| fable-out/02 §3.3 (fingerprint-drift staleness: note `updatedAt` + `keyConcepts` hash, `mayBeOutdated` vs. `MATCH_BASIS_REMOVED`) | The pattern reused for pool source-content drift detection (§1.4) |
| `docs/features/public-notes.md` (copy spine, `copiedFromNoteId`/`copiedFromPublic`, "adoptable study plans reuse the public-note copy spine... zero AI call") | The lineage fields the resolver reads; confirms copying is always exactly one hop from a currently-PUBLIC note |
| `docs/features/study-pack-generation.md` ("Static Study Pack output... uses the resolved courseProgram... It does not use learnerLevel") | Confirms the anti-drift rule is already product-wide, not invented for this session |
| `docs/features/quiz-session.md` (shared `QuickReviewSessionEntity` across all modes, `QuizSessionStateUtils` as sole JSON owner) | Confirms pool sampling output still flows into the same unchanged session engine — nothing here touches session state |
| `backend/.../ExamQuestionPoolEntity.java` / `ExamQuestionPoolService.java` (read directly, not just cited) | The exact shipped shape extended in §1 — fields, method signatures, and status constants below are the real ones, not invented |
| `backend/.../NoteService.java#copyNote` / `copySourceStudyPack` (read directly) | Confirms every copy mints a brand-new `StudyPackEntity` UUID — this is *why* per-owner pool keying breaks for Official content, and it's the concrete mechanism the resolver works around |
| `backend/.../PublicProfileService.java#isOfficialAuthor` (read directly) | The exact, already-shipped "is this note Official" check (`isNoteLibOfficialAccount(user) || user.getRole() == ADMIN`) — reused as-is, not reinvented |

---

## 1. Cross-user question pool design (Official Study Packs only)

### 1.1 The problem, precisely

`ExamQuestionPoolEntity` is keyed by `(study_pack_id, mode)`. `NoteService.copySourceStudyPack()` — the
same method the public-note copy spine and adoptable-plan spine both call — mints a **brand-new**
`StudyPackEntity` with a new random UUID for every single copy, even though its `summary`/`keyConcepts`/
`quiz` content is byte-identical to the source. That is the entire mechanism behind the hard constraint's
"currently PER-OWNER" description: it is not a per-owner *quota* rule, it is a structural fact that every
adopter of the same Official Review Set ends up with their own distinct `study_pack_id`, so naively
flipping `examPoolPrewarmEnabled=true` today would mean every adopter of a popular Official Study Pack
triggers their **own independent LLM pool-generation call** for content that a curator already
authored and published once. That defeats "curate once, reuse everywhere" before it starts, and is
exactly why the flag ships disabled.

### 1.2 The resolver: one indirection, zero call-site changes

Add a private resolution step inside `ExamQuestionPoolService`, applied at the top of every existing
public method (`initiatePool`, `sampleQuestions`, `markServed`, `refreshPool`) before it touches
`examQuestionPoolRepository`:

```
resolvePoolKey(UUID localStudyPackId) -> PoolKey(UUID keyStudyPackId, boolean isOfficialShared)

  pack = studyPackRepository.findById(localStudyPackId)
  note = noteRepository.findById(pack.noteId)

  if note.visibility == PUBLIC and isOfficialAuthor(note.owner):
      return PoolKey(pack.id, isOfficialShared=true)          // this pack IS the canonical source

  if note.copiedFromNoteId != null and note.copiedFromPublic == true:
      origin = noteRepository.findById(note.copiedFromNoteId)
      if origin != null and origin.visibility == PUBLIC and isOfficialAuthor(origin.owner):
          originPack = studyPackRepository.findByNoteId(origin.id)
          if originPack present:
              return PoolKey(originPack.id, isOfficialShared=true)   // adopter -> shared Official pool

  return PoolKey(pack.id, isOfficialShared=false)              // unchanged, today's per-owner behavior
```

`isOfficialAuthor` is not reinvented — it is the exact check already shipped in
`PublicProfileService.isOfficialAuthor` (`isNoteLibOfficialAccount(user) || user.getRole() == ADMIN`),
lifted to a shared location both services can call (a small `OfficialContentUtils` or moving the check
onto `UserEntity`/a shared service — a wiring detail, not a design decision). `copiedFromNoteId` /
`copiedFromPublic` are the existing `NoteEntity` columns the public-note copy spine already writes; no
new column is needed to know lineage.

**Why this is always one hop, never a chain.** `NoteService.copyNote()` throws `NoteNotFoundException`
when a non-owner tries to copy a note that is not currently `PUBLIC`
(`if (!isOwner && resolveVisibility(source) != NoteVisibility.PUBLIC) throw ...`). A learner's private
adopted copy is therefore never itself copyable — every `copiedFromNoteId` in the system points
directly at a note that was `PUBLIC` at copy time. The resolver's single lookup is exhaustive; no
recursive walk is ever required.

**Call sites are untouched.** `LongExamService`, `StudyPackService`, `ChallengeQuizService`, and any
Board Exam caller keep passing their own local `studyPackId` exactly as today (`initiatePool(saved,
ownerUserId)`, `sampleQuestions(studyPackId, mode, count, learnerLevel)`, etc.). The redirection to the
shared pool row happens entirely inside `ExamQuestionPoolService`, which is the same "assembly layer
over entities that already exist" discipline fable-out/01 §1.1 uses for the whole Smart Review Planning
design — this session applies it to the pool the same way.

### 1.3 What must actually change on the shared branch (and why)

Two things break if the shared pool row is used naively as-is, and both need a small, bounded fix.
Everything else on the shared branch is identical to today's per-owner code path.

**(a) Served-question tracking must move off the pool row.**
`servedQuestionKeys` on `ExamQuestionPoolEntity` currently works as an accidental *per-user* tracker,
because until now every pool row genuinely belongs to exactly one owner. The moment many adopters
resolve to the same pool row, sharing that field means one learner's history of "questions I've already
seen" would silently apply to every other learner drawing from the same Official pool — the pool would
functionally drain for *everyone* the first time *anyone* works through it, and `markServed`'s existing
"if available count drops below exam size, `refreshPool()`" logic would fire a full regeneration
triggered by aggregate cross-learner usage rather than one person's real exhaustion. This is a
correctness bug, not a tuning question, so it needs a genuinely new (but small) table:

```
exam_question_pool_progress
  id              UUID (pk)
  pool_id         UUID (fk -> exam_question_pool.id)
  user_id         UUID (fk -> users.id)
  served_question_keys  jsonb   -- same normalized-question-key shape already used on the pool row
  updated_at      timestamptz
  UNIQUE (pool_id, user_id)
```

For an Official-shared pool: `sampleQuestions`/`markServed` read/write this child row (keyed to the
*calling* user, resolved separately from the pool key) instead of the pool's own `servedQuestionKeys`.
For a private per-owner pool, behavior is byte-for-byte unchanged — the owner and the sole consumer are
the same person, so the existing pool-level field continues to work exactly as shipped (no migration of
existing private pool rows is needed; this table only starts getting rows once cross-user resolution is
live). This is the one genuinely new schema object this whole design introduces — everything else is
either read-only resolution logic or a status-ladder addition on the existing entity.

**(b) `learnerLevel`-triggered refresh must be dropped for Official pools.**
`sampleQuestions()` today calls `sameLearnerLevel(pool.getLearnerLevel(), currentUserLearnerLevel)` and,
on mismatch, calls `refreshPool()` — regenerating the whole pool for the new level. That is correct when
there is exactly one owner with one learner level. Once many adopters with different learner levels
share one pool row, this would thrash: the first Beginner learner's request regenerates the pool for
"Beginner," the next Advanced learner's request immediately regenerates it again, forever, and (per
§1.3(c) below) each of those regenerations would also need to clear a mandatory-review gate — an
unworkable loop. The fix is not new machinery, it is applying a rule this codebase has already locked
for exactly this situation: `study-pack-generation.md` states static Study Pack content "uses the
resolved courseProgram... It does not use learnerLevel," and fable-out/01 §2.2 Step 5 states generated
shared content is "calibrated by courseProgram only, never learnerLevel... the Learner Level vs.
Course/Program anti-drift rule explicitly forbids leveling shared content per-user." Official pools are
shared content by definition, so they generate once at a fixed mixed-difficulty calibration (the
existing `DIFFICULTY_MIXED` constant `ExamQuestionPoolService` already uses for private pools) and never
fork or refresh on learner-level mismatch. `learnerLevel` stays populated on private per-owner pools
exactly as shipped.

**(c) Generation must land behind a review gate before it is servable — for the shared branch only.**
`generatePoolAsync()` today calls the LLM and flips the pool straight to `STATUS_READY` with no human
step, which is correct for a private pool (it is one person's own practice questions, the same standard
as their own Study Pack quiz). For an Official pool served to every adopter, that same autonomy would
mean freshly generated exam questions reach potentially thousands of learners the moment generation
finishes, with no curator ever having seen them — a direct violation of hard constraint 2/3
("curation, never generation... applies to question pools exactly as it applies to notes and plans";
"pool generation/expansion is an Internal Curator (admin) action with mandatory review"). The fix reuses
the review-ladder *shape* fable-out/01 already specified for `curator_generation_requests`
(`REQUESTED → DRAFT_GENERATED → IN_REVIEW → PUBLISHED/REJECTED`) rather than inventing a new one: an
Official pool's freshly generated batch lands in a new `PENDING_REVIEW` status (sitting alongside the
existing `PENDING`/`GENERATING`/`READY`/`FAILED` constants) instead of `READY`. `sampleQuestions()`
needs no new branch for this — its existing `if (!STATUS_READY.equals(pool.getGenerationStatus()))
return Optional.empty();` guard already refuses to serve anything that isn't `READY`, so an unreviewed
batch is invisible to learners for free. A curator reviews the batch (reusing whatever quiz-preview
surface the Internal Curator workspace already has for content review — that screen is fable-out/03's
territory and is not redesigned here) and an explicit publish action flips `PENDING_REVIEW → READY`.
Private per-owner pools keep going straight to `READY`, unchanged.

### 1.4 Invalidation when the source note/pack changes

Two distinct drift cases, both reusing an existing staleness pattern rather than inventing one:

- **The Official source is unpublished or superseded** (an admin flips it back to `PRIVATE`, or
  replaces it with an improved Make-a-Copy version and retires the old one — the existing
  Make-a-Copy-then-publish flow, unchanged). The resolver's `note.visibility == PUBLIC` check simply
  fails on the next read. Existing adopters whose local `copiedFromNoteId` still points at the retired
  note fall straight through to the per-owner branch of `resolvePoolKey` — they keep practicing off
  their own local (now-standalone) copy, at worst triggering one single, private, per-owner
  regeneration for that one learner if they exhaust it, which is not a curation concern (same standard
  as any private pool today). New adopters who copy the *replacement* Official note resolve into that
  note's own fresh pool. No persisted invalidation flag is needed — this is the same "derived, never
  persisted... self-heals" doctrine fable-out/01 §1.4 already uses for coverage and fable-out/02 §3.3
  already uses for adopted-copy snapshots ("adopted copies never invalidate... improvement flows
  forward to future adopters only").
- **The Official source's Study Pack content is regenerated in place** (same note, same `study_pack_id`,
  content updated via whatever in-place regeneration path the product exposes to owners). Because the
  pool's `questions` were authored against a specific `keyConcepts` snapshot, this needs the same
  fingerprint-drift signal fable-out/02 §3.3 already designed for fulfillments: snapshot the source
  pack's `updatedAt` + a hash of its `keyConcepts` onto the pool row at generation time; on the next
  curator-facing read, compare against the pack's current fingerprint. Pure content-only drift (a typo
  fix) raises a soft, admin-only `poolMayBeOutdated` flag — the exact `companionMayBeOutdated` shape.
  A vanished `keyConcept` the pool's questions were tagged against raises the stronger signal (mirroring
  `MATCH_BASIS_REMOVED`) and pushes the pool into the curator's expansion/refresh queue. Never
  auto-regenerates, never auto-republishes — a human decides, exactly per the locked versioning rule.

### 1.5 Zero marginal cost, extended one more rung

fable-out/01 §3.1 names the reuse ladder: "Study Pack (shipped) → plan of packs (shipped) → Goal of
plans (shipped) → assembled curriculum coverage (that initiative)." This design adds the next rung
directly on top: **shared curated question pools**. An adopter's Long Exam or Board Exam attempt on an
Official Review Set item costs the adopter nothing beyond the DB read/write already implied by
`sampleQuestions()` — no LLM call, no quota decrement, the same "adoption is DB copies through the
existing spine, zero marginal LLM" cost model already locked for Study Packs, now covering the
higher-stakes exam modes too. This is also the concrete reason `examPoolPrewarmEnabled` stays worth
turning on for Official content specifically once this ships (prewarm generates once per Official
Study Pack, amortized across every future adopter) while remaining not worth it for private notes
(most private notes never reach Board/Long Exam mode at all — prewarming them stays wasted work,
exactly why lazy per-request `scheduleLazyInitiation` remains the private-pool path, unchanged).

---

## 2. The bounded reusable-learning-object model

The original proposal's maximal model: **Note / Summary / Key Concepts / Flashcards / Curated Question
Pool / Explanations / Difficulty / Related Notes.** Checked one at a time against what is actually
shipped, using only existing entities/fields:

| Field | Status | Where it already lives |
|---|---|---|
| **Note** | Shipped, unchanged | `NoteEntity` — the atomic unit every other layer is built from |
| **Summary** | Shipped, unchanged | `StudyPackEntity.summary` — includes the enhanced markdown format (comparison table, Common Misconceptions) per `study-pack-generation.md` |
| **Key Concepts** | Shipped, unchanged | `StudyPackEntity.keyConcepts` — already doing double duty as the flashcard-front source *and* the matcher's primary signal (fable-out/02 §1.2: "Key Concepts... Primary matcher") |
| **Flashcards** | Shipped, **fully derived, no stored field** | Computed at read time from `keyConcepts` + `quiz[].explanation` via fuzzy match (`docs/features/flashcards.md`). Its ~56% coverage ceiling is a documented, deliberate non-goal ("closing the gap would require adding a real per-concept definition field... deliberate non-goal") — this session does not reopen that for Official content. A curator-facing "flashcard coverage %" quality signal on an Official pack is a plausible small polish item, not a schema change |
| **Curated Question Pool** | **The one genuinely new build** | Section 1 above — the resolver + `exam_question_pool_progress` child table + the `PENDING_REVIEW` status. Everything else about `ExamQuestionPoolEntity`/`Service` is reused verbatim |
| **Explanations** | Shipped, unchanged | `QuizItem.explanation` — already "required for each quiz item" per `study-pack-generation.md`'s quiz quality principles |
| **Difficulty** | **Cut from this bounded model** | A generation-time *input* already exists (`DIFFICULTY_MIXED`/targeted difficulty passed into quiz generation calls; `DIFFICULTY_SELECTION` is an existing `Feature` enum gate) but there is no persisted per-question or per-pack difficulty *field* today, and none is proposed here. Adding one would be exactly the kind of maximal-model scope creep this session is explicitly asked to avoid; the existing generation-time knob is enough for Official pool authoring (mixed difficulty, same as private pools) |
| **Related Notes** | Shipped, **fully derived, no stored field** | Two existing mechanisms already cover it with zero new work: (a) `public-notes.md`'s "More in {Subject}" / "More {Course/Program} notes" modules, a live query over `GET /notes/public?subject=X`; (b) once Smart Review Planning ships, `curriculum_objective_fulfillments` (fable-out/01 §1.2) already groups notes by shared `subjectLabel`/curriculum objective — a stronger, curriculum-aware "related" signal that falls out of the entity model for free |

**Tally:** 5 of 8 fields need zero new work at all (Note, Summary, Key Concepts, Explanations, Related
Notes). One (Flashcards) is intentionally left derived, preserving an already-documented non-goal
rather than reopening it under Official-content pressure. One (Difficulty) is deliberately cut — a
maximal-model field with no shipped persisted counterpart and no case made here for adding one. One
(Curated Question Pool) is the actual net-new build, and it is scoped to Section 1's resolver + one
small child table, not a new content entity. Whatever curator workflow reviews the pool's
`PENDING_REVIEW` batches plugs into the same review surface the Internal Curator session
(fable-out/03) already owns — this session does not add a second review screen.

---

## 3. The Reviewer-object decision

### 3.1 Case for (a): a new first-class Reviewer entity

A `ReviewerEntity` composing multiple notes (potentially across subjects, or a curator-picked subset of
an Official Review Set) into a single exportable/studyable unit would let NoteLib produce something
closer to what "reviewer" culturally means in Philippine board-exam prep — a printable, offline-capable
booklet a candidate can carry, distinct from an in-app navigable plan. Arguments in its favor:

- It would be a genuinely different *consumption mode* than the existing Review Set (in-app, quiz-first,
  read online) — a document artifact versus an application surface.
- It would map cleanly onto how competitors and the existing PH-market vernacular use the word
  "reviewer," which may matter for SEO/marketing copy independent of the in-app experience.
- It could, in principle, be assembled once by a curator and exported many times — a genuine
  curate-once artifact, structurally consistent with this session's "curate once, reuse everywhere"
  theme.

### 3.2 Case against (a)

- It requires real new surface area no other part of this design touches: a new entity, an assembly
  service, and — critically — an export/print rendering pipeline (PDF or print-CSS), none of which
  exist today beyond the barely-used generic export path.
- It creates a second "official container" concept sitting next to the Official Review Set that
  fable-out/01 §1.3 already anticipated a badge-tier identity for. Two competing "this is the official,
  curated thing" concepts (Review Set vs. Reviewer) is exactly the kind of duplicated territory this
  redefinition is trying to collapse, not multiply.
- Most importantly, its central justification — a better exportable/printable unit — runs directly into
  the grounding fact this session was explicitly told not to quietly reintroduce: PDF export of study
  content has been used **once, ever, across 260 users.** Building a new entity whose primary
  differentiator from a Review Set is being a better export target is optimizing for a behavior the
  product's own usage data says essentially does not happen.
- It would also need its own content-ops workflow (who assembles a Reviewer, from which notes, reviewed
  by whom) beyond what the Internal Curator session already owns — exactly what hard constraint 2 in
  this session's prompt says not to invent.

### 3.3 Case for (b): "Reviewer" as a `getCollectionLabels` display label only

`getCollectionLabels("BOARD_EXAM")` already returns `{ singular: "Review Set", plural: "Review Sets",
navLabel: "Review Sets", newCtaLabel: "New Review Set" }` (confirmed directly in
`frontend/lib/collection-labels.ts` / its test file). Under option (b), "Reviewer" is simply a relabel
of that same value for the board-exam profile — no new entity, no new endpoint, no new schema, and
every piece of machinery a Review Set already has (the Goal→Subject hierarchy, the adopt-as-snapshot
spine, Companion guidance, curriculum coverage once Smart Review Planning ships, and — per Section 1 —
shared question pools) is already exactly the thing the word "reviewer" is trying to name: a curated,
ordered, practice-backed study sequence. This is a same-shaped change to the one fable-out/06 already
scoped ("terminology rename map") — the actual copy decision (does "Reviewer" fully replace "Review Set"
for board-exam users, or ship as an alternate synonym in specific surfaces) is deferred to that session
or a future terminology pass, not decided here.

### 3.4 Case against (b)

- A Review Set, however labeled, is still fundamentally an in-app collection. Some board-exam users'
  mental model of "a reviewer" may specifically mean a downloadable, offline-usable document — a
  relabel gives the right *name* without necessarily giving the full *cultural affordance* some users
  associate with the word. If that gap turns out to matter, label-only under-delivers relative to
  what a user hearing "reviewer" might expect.
- It is a smaller, safer bet, but a smaller bet forecloses less upside too — if genuine "let me download
  this and study offline" demand ever materializes (which is precisely what "1 export, ever" currently
  argues against), a relabel alone does not capture it.

### 3.5 Recommendation

**Ship (b): "Reviewer" as a display-label-only relabel of the existing Review Set, no new entity.**
Justification, stated once so it isn't diluted: the strongest reason to build a first-class Reviewer
entity is to make NoteLib produce a better exportable/printable study document, and the product's own
usage data — one export, ever, across the entire user base — is direct evidence that exportability is
not the thing driving value for the users NoteLib already has. The in-app value this product actually
demonstrates (Companion guidance, ConceptHealth-derived readiness, adaptive practice, and now — per
Section 1 — zero-marginal-cost shared exam pools) has no export dependency at all. Spending new-entity
effort to serve an export use case the data says nobody uses would be solving a problem this product
does not currently have, dressed up in the right vocabulary. This is not a permanently foreclosed
door: if a genuine, evidenced "I want to take this offline" signal shows up later (which the current
data contradicts), reconsidering a real Reviewer object is a legitimate future decision — it is simply
not one this session's evidence supports making now.

---

## 4. Where AI still adds value in this reuse-first world

Consistent with `company-redefinition-out/01`'s identity framing — AI as "the intelligence layer behind
all of it, never the product itself," restated here per this session's brief as "AI as content factory
+ tutor, not the product" — the runtime/authoring moments that still call an LLM after this design
ships:

- **Curator-side pool expansion (factory, gated).** Section 1.3(c)'s `PENDING_REVIEW` batches are
  generated by the exact same `generatePoolAsync()` LLM call already shipped, invoked by an Internal
  Curator action (initial fill or "expand this pool") rather than by learner traffic. AI drafts; a
  human publishes. This is the pool-specific instance of the same mandatory-review contract fable-out/01
  already locked for curriculum-gap generation.
- **Private-note quiz/Study Pack generation for non-Official content (unaffected).** Every private
  user's own Note → Study Pack → Quick Review/Challenge Quiz/Adaptive Practice/Board Exam generation
  flow (`study-pack-generation.md`'s async generation flow) is completely untouched by anything in this
  document. AI still generates on demand for the vast majority of content that has not (yet, or ever)
  been curated into anything shared — this design changes nothing about that path.
- **PRO Adaptive Practice weak-concept selection (explicitly out of scope for pooling).** Adaptive
  follow-up generation ("generated from weak concepts in the latest completed Quick Review session...
  does not modify the original baseline Study Pack quiz," per `study-pack-generation.md`) stays
  per-learner, per-session, and reactive to *that specific learner's* misses. It is the clearest example
  of why this whole cross-user pool design does not (and should not) try to swallow every quiz-adjacent
  AI surface: adaptive output is diagnostic and personal by construction, the structural opposite of the
  static, shareable content Section 1 is built to reuse. `WEAK_CONCEPT_DETECTION`/`ADAPTIVE_QUIZ`
  feature-gated behavior is unchanged.
- **Curator gap-fill note generation (fable-out/01 §2.2 Step 5).** The reuse-first pipeline's last
  resort — generating a draft note (and its Study Pack, and eventually its exam pool) only for
  objectives nothing else can fulfill — remains the same Internal Curator, mandatory-review, PREMIUM-tier,
  courseProgram-only-calibrated generation fable-out/01 already specified. This design's pool-expansion
  gate (Section 1.3(c)) is the same shape one layer further down the reuse ladder (Section 1.5).

In every one of these, a learner still never receives raw model output as the deliverable — they
receive curated Official pools, their own reviewed Study Pack, or a per-learner adaptive practice set
that is itself bounded, quota'd, and framed as practice rather than as "official" content. AI remains
the labor-saving mechanism behind curation and personalization; it is never itself the thing a learner
is asked to trust directly.
