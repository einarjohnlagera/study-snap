# Subject Review — Cross-Note Learning Consolidation
## Stage 2 Tightened Product + Architecture Plan

**Status: Stage 2 product + architecture plan only. Nothing implemented, no code edited, no migration, no
commit, no production write. DO NOT IMPLEMENT.**

**Date written: 2026-09-13. Repo state: branch `releases/v0.144.0` (Released — its one item, admin
exam-pool invalidation, merged as PR #1385 at `e61ee2ce`, signed off at `dfd332e1`). No release is
currently open.**

**Builds on:**
- `docs/claude-plans/cross-note-review-consolidation-stage1.md` (Stage 1 audit, 2026-09-13, untracked) —
  the completed audit this plan tightens. Its DEFER verdict is **not re-litigated here**; it is the
  approved input.
- `docs/claude-plans/artifact-first-learning-availability-stage2.md` (sibling Stage 2, 2026-09-13,
  untracked) — its **§C capability model is consumed as a finalized contract**, not re-derived. Field
  names used below (`hasQuizQuestions`, `hasKeyConcepts`, `hasTeacherQuiz`, `studyPackDone`, and the
  `StudyPackArtifactFacts` utility that derives them) are that plan's, cited to it.

**Production reads: ZERO new reads in this pass, deliberately.** Stage 1's production facts were read
**2026-09-13 — the same calendar day as this plan**, so they have not decayed and re-running them would
add nothing while costing a full audit. They are carried forward *as dated snapshots* and are flagged as
decaying for any future reader (§1, and the closing note). The brief's §5 instruction — *"do not repeat
dated production numbers as current facts"* — is honoured by labelling every one, not by re-reading them
hours later.

**What this pass DID re-verify against code, because it is load-bearing for a NEW decision here** (five
reads, each named at the point of use): the Ask Companion grounding and availability rule (§4, §8 option
E); the `CompanionContent` / `CompanionSection` shape (§8 option E); `LlmStudyPackService`'s full method
list (§7, §8); the current Subject Plan detail render tree and its action-slot count (§13); and the
commit that first put a plan-scoped practice CTA on a leaf Subject Plan (§17, §18).

---

## 1. Revalidated Stage 1 findings

### 1.1 Findings that still hold, carried forward unchanged

The brief's §5 list, each confirmed as the current working assumption. Where a row says "per Stage 1 §X"
it was **not** re-derived in this pass — that is the brief's own instruction, not an omission.

| Stage 1 finding | Status | Anchor |
|---|---|---|
| Study Pack `summary` is one unstructured prose blob (`nullable = false`) | **Holds** | Per Stage 1 §1.4, `StudyPackEntity.java:45-63` |
| `keyConcepts` are bare `List<String>`, 2–80 chars, no per-concept body anywhere | **Holds** | Per Stage 1 §1.4, `schema.json:27-36` |
| 58.9% of key concepts have no explanatory text anywhere in the product | **Holds** *(Production, 2026-09-13 — same day, not re-read)* | Per Stage 1 §1.4 |
| Deterministic multi-note aggregation therefore yields weak consolidation value | **Holds**, and §7 below hardens it into a forced conclusion | Per Stage 1 §1.4, §5 |
| No canonical cross-pack concept identity exists | **Holds** | Per Stage 1 §2, `DashboardService.java:471-473`; `ROADMAP.md:508` sizing read (0.4%) |
| No non-assessment cross-note material surface exists | **Holds** | Per Stage 1 §1.3, §2 |
| Cross-note *assessment* already exists four ways | **Holds** | Per Stage 1 §1.3 |
| Review-only surfaces must not write `ConceptHealth` | **Holds** | Per Stage 1 §12; `MemorizationCardEntity.java:18` |
| Exactly 5 assessment modes; Subject Review must not become a sixth | **Holds** | `EXAM_MODES.md:25`; per Stage 1 §9 |
| No learner PDF writer exists anywhere in the repo | **Holds** | Per Stage 1 §1.6 |
| No persisted Review Pack entity exists | **Holds** | Per Stage 1 §10 |
| Subject Plan is structurally the cleanest candidate scope | **Holds**, confirmed in §5 below | Per Stage 1 §7 |

### 1.2 One Stage 1 open item is now CLOSED by this pass

Stage 1 §0 flagged, honestly, one thing it did not finish tracing:

> *"the Review Set detail page's leaf-vs-Goal render branching — specifically whether `AskCompanionPanel`
> can render on a child Subject Plan where `AskCompanionService.assertCompanionAvailable` (`:174-179`)
> would reject it. Flagged in §2 as **[NOT FULLY TRACED]**."*

**It is now traced, and the answer is no — structurally, not just by convention.** Three reads, this pass:

1. `AskCompanionService.assertCompanionAvailable` throws `CompanionNotAvailableException` when
   `collection.getParentCollectionId() != null` **or** the companion has no renderable content — read
   directly this pass.
2. A child collection **cannot hold companion content at all**: `NoteCollectionService.java:884` executes
   `child.setCompanion(null)` (and `setCompanionStructureSnapshot(null)`) whenever a collection is nested
   under a parent; the same nulling appears again at `:1143`. `setCompanion` itself (`:920-936`) is
   `assertAdmin`-gated and runs `validateCompanionTarget(collection)` before writing.
3. The frontend panel is therefore unreachable on a leaf: `collection-detail-page-client.tsx:4072` gates
   `AskCompanionPanel` on `hasRenderableCompanionContent(collection.companion)`, which is `false` by
   construction for any child collection.

**Why this matters to Stage 2 rather than being tidy-up:** it is simultaneously the strongest
*distinctness* fact in §4 and the discovery of a genuine *architecture option* in §8 (option E). Both are
new to this pass.

### 1.3 One Stage 1 number is now materially re-framed (not falsified)

Stage 1 §1.1's headline — **0 of 729 sessions have ever been collection-anchored** — is not challenged.
But this pass found the exposure window is far shorter than the capability's age, which changes what the
zero can be read to mean.

`git log -S` on the explanatory comment in `collection-detail-page-client.tsx` returns commit
**`d1dacb3d`, 2026-09-03, "feat: announce whole-plan assessment, and reach leaf Subject Plans (item 2)"**,
first contained in tag **`v0.109.0`**. Its own in-code comment says it plainly:

> *"⚠️ `planPracticeAction` was passed to the GOAL view only when it shipped in `v0.107.0`, so a leaf
> Subject Plan — a plan with no children — had no plan-scoped practice CTA at all. This is the SAME
> action, on the view it was omitted from; it is not a second entry point."*

**So for most of its life the capability had no entry point on the very surface a learner reaches when
they open one subject.** It has been reachable from a leaf Subject Plan for **ten days** as of this
writing. That is consistent with — and independent corroboration of — the reason `ROADMAP.md:520` was
lifted to `[EFFORT]` and re-dated to 2026-10-13 *from the announcement rather than the ship date*.

**Consequence, and it is load-bearing for §18:** the 0-of-729 is a valid statement about a
collection-anchored session count and an invalid basis for any claim about plan-level *demand*. This
reinforces the brief's §6 correction rather than contradicting Stage 1.

### 1.4 Stale claims flagged

- **No Stage 1 claim was found false in this pass.** Every claim re-checked (five code reads, §0 above)
  held exactly.
- **Every production number in Stage 1 is a 2026-09-13 snapshot and decays.** Per `CLAUDE.md`'s
  snapshot-not-fact rule, the five that would change a decision if they moved are: the 0-of-729
  plan-scoped sessions; the 9 monthly actives / 36 completed sessions in 30 days; the 41.1% concept-match
  rate; the 4-versus-452 Subject Plan eligibility split; and the 3-user Memorization adoption. **Re-read
  each before it reaches a kickoff, prompt or release note** — not before it reaches this plan, which is
  same-day.
- **Two repo-doc defects Stage 1 reported remain open and are explicitly NOT this plan's to fix:**
  `CLAUDE.md`'s Feature-enum line (says four values, the enum has five — `ASK_COMPANION` at
  `Feature.java:16-18`), and the dead "PDF Exports" meter (`settings/page.tsx:1094-1100`). The second is
  separated by the brief's §28 and is handled in §15 below.

---

## 2. Tightened product problem

> **A learner who has worked through several notes in one subject holds them as separate,
> unconnected units. Nothing in NoteLib ever tells them how those topics relate — which one is the
> foundation for which, which two are easily confused, which share a mechanism — so the subject never
> becomes more than the sum of its notes.**

Three things that statement deliberately is **not**:

- It is **not** *"I cannot see my notes in one place"* — the Subject Plan detail page already lists them,
  with per-note readiness, due-concept counts and last-session timestamps (per Stage 1 §1.3,
  `NoteCollectionItemResponse:19-21`). Multi-note **navigation** is solved.
- It is **not** *"I cannot test myself across notes"* — four cross-note assessment paths exist (per
  Stage 1 §1.3). Multi-note **assessment** is solved.
- It is **not** *"I want a study document"* — that is the export identity the brief's §28 and Stage 1's
  §18 both reject by name.

The gap is **comprehension across topics**, and it is the only one of the three that is genuinely empty.

---

## 3. Product hierarchy

```
Note                 Learn one topic
   │                 (Study Pack: summary, key concepts, 5 quiz items — one note's worth)
   ▼
Subject Plan         Connect the topics        ◀── SUBJECT REVIEW LIVES HERE, AND NOWHERE ELSE
   │                 (today: lists them, counts them, and assesses across them — never relates them)
   ▼
Long Exam            Test the subject
   │
   ▼
Review Set / Goal    Navigate the curriculum
   │
   ▼
Board Exam           Test the curriculum
```

Where the other surfaces sit relative to it:

| Layer | Role | Subject Review's relationship to it |
|---|---|---|
| **Progress / Readiness** | Measures mastery from assessment evidence | Subject Review **reads nothing from it and writes nothing to it** (§11). It may sit *near* readiness on the page (§13) but is not derived from it. |
| **Companion / Coach** | Curated guidance (Companion) and next-step routing (Coach) | Companion is the **closest existing thing** and is top-level-only and admin-authored (§1.2, §4). Coach integration is **deferred** (§16). |
| **Assessment modes (5, locked)** | Produce mastery evidence | Subject Review **hands off to** them (§14) and **never becomes** one (§11). |

**What Subject Review is NOT — the negative contract, stated once and binding:**

- **Not a sixth quiz mode**, and not a sub-mode of one (`EXAM_MODES.md:25`).
- **Not another document library** — no persisted artifact a learner accumulates (§9).
- **Not another Study Pack** — it is not generated per note and does not extend `StudyPackEntity`.
- **Not a PDF generator** (§15).
- **Not another Flashcards surface** and **not another Memorization surface** — no card deck, no
  self-grade, no scheduling (§6, §20).
- **Not a progress or streak surface** — viewing it credits nothing (§11).

---

## 4. Distinctness analysis

**This is a fresh analysis, not Stage 1's.** Stage 1 §4 compared the *thin deterministic* concept
(concatenated summaries + concept list + reused flashcards) and correctly found it distinct from nine
surfaces "by one axis only." The tightened concept — *relationships across notes, not concatenation of
note artifacts* — must be re-tested, because the axis has changed.

The test applied to each row: **could this surface, as it exists today, answer "how do these topics fit
together?" for a learner's own Subject Plan?**

| Existing surface | What it does | Can it answer the cross-note "how do these fit together" question? | What Subject Review uniquely contributes |
|---|---|---|---|
| **Study Pack** | One note → summary, ~9 bare concepts, exactly 5 quiz items (per Stage 1 §1.4) | **No — structurally.** Its generation contract is single-note: every `LlmStudyPackService` method takes one `(title, summary, keyConcepts)` triple, verified in full this pass. A Study Pack has never seen a second note. | The only artifact whose **input is more than one note**. |
| **Flashcards** | Frontend-only, `keyConcepts` × fuzzy-matched `explanation`, one note, no persistence, no events (per Stage 1 §1.5) | **No.** It is term→definition recall. It has no representation for a relationship *between* two terms, let alone two notes. | Relationships are the content, not the terms. |
| **Memorization** | Same matched concepts minus unmatched; SM-2; one note; firewalled from readiness | **No**, same reason, plus it is explicitly a *retention schedule*, not an explanation. | Comprehension, not retention. |
| **Quick Review** | 5 static questions, one note, writes `ConceptHealth` | **No.** Assessment, single-note. | **⚠️ And under the tightened framing the old overlap is GONE.** Stage 1 rated this "severe" because the thin v1's Recall section reused the same `quiz` array. §6 removes Recall entirely, which removes the overlap at its source. |
| **Challenge Quiz** | Progressive 5→20, multi-note from a verified plan, writes `ConceptHealth` | **No.** Multi-note *questions*, never multi-note *material*. | Material, not questions. |
| **Adaptive Practice** | Weak-concept remediation, plan-scoped since `v0.107.0`, collection-anchored | **No — and the brief's §6 correction is right.** It asks *"what should I repair?"* from `ConceptHealth` evidence. It cannot say anything about a topic the learner has never been assessed on, which is most of them. | Answers a question that needs **no prior assessment evidence at all** — the one cross-note capability that works on day one of a subject. |
| **Long Exam** | 6/8/10 multi-note sources from a plan, real provenance | **No.** Assessment. | Precedes assessment rather than being one (§14). |
| **Board Exam** | Whole-Review-Set assessment, strata-sampled | **No.** Assessment, and broader scope than a Subject Plan. | Same. |
| **Companion / Ask Companion** | Static admin-authored guidance + 6-turn LLM chat | **⚠️ This is the only genuine contender, and it fails on THREE independent structural grounds — all verified this pass.** (a) **Wrong scope**: `assertCompanionAvailable` rejects any collection with `parentCollectionId != null`, and `NoteCollectionService.java:884` nulls a child's companion on nesting — so it is **unreachable on a Subject Plan by construction**, not by policy. (b) **Wrong source**: `llmService.answer(collection.getCompanion(), turns, question)` grounds the chat on the **admin-authored Companion content**, never on the learner's notes — so it cannot relate *these* notes. (c) **Wrong authorship**: it is `assertAdmin`-gated, so it exists only where a curator wrote it. | The only surface that relates **the learner's own notes**, at **subject scope**, with **no curator required**. |
| **Progress / Readiness** | Aggregates mastery numbers across a plan | **No.** It aggregates *numbers*, never *content* — the exact gap Stage 1 §2 named. | Content, not counts. |
| **Review Set detail page** | Lists notes with per-item readiness and due counts | **No**, same reason. | Same. |

### 4.1 Verdict — unique value survives, and it is now sharper than Stage 1's

**Stage 1's distinctness was weak ("one axis away from four existing surfaces"). The tightened concept's
is not, and the reason is that the tightening removed the overlapping half.** Two changes did it:

1. **Removing Recall (§6, brief §15) deletes the Flashcards / Memorization / Quick Review overlap
   outright.** Stage 1 rated all three "High" risk *because of the Recall section*. With no Recall
   section there is no shared material and no shared mechanic.
2. **Adding relationships as the content creates a capability no existing surface has any representation
   for.** There is no field, no table, no DTO and no prompt in this repo that can express *"Topic A is the
   foundation for Topic B"* — verified: `StudyPackEntity` has no such column, `ConceptHealthEntity` is
   keyed `(user_id, study_pack_id, concept)` with free-text concepts and cannot relate two packs
   (`DashboardService.java:471-473`), and `CompanionContent` (the one authored shape that comes close)
   holds `overview / studyStrategy / commonMistakes / resources / faq / mentorTips` — prose fields, on
   top-level collections only, written by an admin.

**Recommendation: do NOT abandon the concept.** The unique contribution is defensible in one sentence:

> **Subject Review is the only surface that would relate a learner's own notes to each other, at subject
> scope, without requiring a curator to have authored the relationship or an assessment to have measured
> it.**

**⚠️ But note precisely what this does and does not establish.** It establishes *distinctness*, which is
necessary. It does not establish *demand*, which is absent (per Stage 1 §1.1, §1.2), and it does not
establish that generation can actually produce the content (§19's abandon trigger, which is the real
open question). Distinctness is why the concept is preserved; the missing two are why it stays deferred.

---

## 5. Final scope

> **CONFIRMED: Subject Plan only. A single child `NoteCollectionEntity` (`parentCollectionId != null`),
> resolved through the existing `PlanSourcedExamVerifier`.**

**Why, under the tightened framing specifically** (Stage 1's §7 reasons still hold and are not repeated —
see per Stage 1 §7 for the selection-picker, breadth and Coach arguments):

1. **Semantic coherence is the input to synthesis, not a nicety.** A synthesis prompt asked *"how do
   these fit together?"* over an arbitrary note selection has no reason to believe they fit together at
   all, and will confabulate a relationship to satisfy the instruction. A Subject Plan is a container the
   learner or curator has already asserted is one subject. **The scope choice is doing real work in the
   generated case that it was not doing in Stage 1's deterministic case.**
2. **It is the only scope with an authorized, shared, already-drifted-once resolver**
   (`PlanSourcedExamVerifier.java:18-29` — its javadoc exists because two copies of the rule drifted).
3. **A Goal / Review Set is the wrong *conceptual* level, not merely too large.** Board Exam already
   resolves a child up to its parent Goal and samples the whole set
   (`ChallengeQuizService.java:1656-1663`), and that breadth is correct *for an exam*. "How do these
   topics fit together" asked across an entire board curriculum has no useful answer — the honest answer
   is the curriculum's own structure, which is what the Review Set already displays.
4. **Free selection is rejected twice over**: the median learner has one distinct note ever studied (per
   Stage 1 §1.2), so the picker has nothing to pick from; and §14's assessment handoff has **no truthful
   CTA** for an arbitrary selection (per Stage 1 §13).

**Rejected, explicitly:** arbitrary selected Notes; whole Review Set / Goal; Coach-selected scope;
free-form multi-note picker. All four are listed in §20 as deferred architecture.

---

## 6. Minimum differentiated experience

The tightened candidate structure is adopted with **one name change and one hard cap**. Recall is **not**
included (brief §15).

### A. Big Picture

**Purpose:** answer *"what are the major ideas in this subject, and how do they fit together?"* in one
short pass.

**Shape:** 3–5 short paragraphs, or a lead paragraph plus 3–4 named major ideas. **Explicitly not** one
summary per note and not a concatenation — the test is that its length must be **independent of the
source note count**. A 5-note plan and a 20-note plan produce a Big Picture of the same size; only its
content differs. This is the single property that prevents the mobile-overload failure (§ brief 23) by
construction rather than by trimming.

### B. Connections — *the primary differentiated value*

**Shape:** a small set (**cap 5–7**) of discrete, typed relationship items. Each item names **two or more
source topics** and states the relationship in one or two sentences.

Relationship types worth supporting, each drawn from the brief's §14B list and each expressible against
the available material:

| Type | Example shape |
|---|---|
| **Foundation / dependency** | *"X gives you the method Y depends on — learn X first."* |
| **Contrast** | *"X and Y look similar but apply under opposite conditions."* |
| **Commonly confused** | *"Learners mix up X and Y; the difference is Z."* |
| **Shared mechanism** | *"X and Y are the same principle applied to different systems."* |
| **Complementary parts of one problem** | *"X solves the loading case; Y solves the failure case."* |

**Hard grounding rule:** every Connection must name its source notes by **durable `noteId`** (§10) and
must be derivable from those notes' actual content. **Do not infer relationships from note titles alone**
(brief §14B) — a title-only inference is exactly the confabulation §5 point 1 warns about, and it is the
failure mode §19's prototype test is designed to catch.

### C. Must Remember

**Shape:** a **prioritized, deliberately short** list — **cap 8–10 items** — of the ideas that matter most
across the whole subject, each one or two lines.

**The cap is the feature.** A mean Subject Plan carries ~104 key-concept strings (11.4 notes × 9.12 per
pack, per Stage 1 §1.4). Presenting 104 is the naive aggregation the brief's §23 prohibits; presenting 10
is a judgment about importance. **That judgment is precisely what requires generation** (§7), and §22 of
the brief is right that it must not come from note counts.

**Renamed from "Key Concepts" deliberately** — "Key Concepts" is already a `StudyPackEntity` field name
and a visible per-note UI label; reusing it here would tell a learner they are looking at the same thing
concatenated, which is the identity this plan rejects.

### D. Source Topics

**Shape:** every source note, by title, linking back to the note by **durable `noteId`** (§10). Flat list,
no readiness numbers, no due counts — those live on the Subject Plan page one level up and duplicating
them here re-creates the "aggregate numbers" surface that already exists.

**Purpose:** provenance the learner can act on. It answers *"which of my notes did this come from, and
where do I go to read more?"*

### E. Assessment handoff

Not a section of content — one or two CTAs at the end. Specified in §14.

### 6.1 What is deliberately absent, and why

| Absent | Why |
|---|---|
| **Recall / self-check** | Brief §15. It duplicates Flashcards and Memorization, and reusing `quiz` items without scoring is Quick Review minus the score (per Stage 1 §4). Its removal is what makes §4's distinctness hold. **If later research argues for a lightweight self-check, it must be justified separately** — it is not core. |
| **Per-note summaries** | §6A. The whole point is that Big Picture's length is note-count-independent. |
| **The full key-concept list** | §6C. 104 strings is raw material, not a review. |
| **Progress / readiness numbers** | §6D, §11. Already exist one level up. |
| **Any completion state** | §11. Scrolling to the end is not learning. |
| **Export** | §15. |

---

## 7. Generation decision

> # **REQUIRED — with one scope-conditional exception (§8.3).**
>
> **REQUIRED** for learner-authored plans, which are the common case. **OPTIONAL** for curated / Official
> Review Sets, where an authored Companion-style block can do the same job — §8.3 derives that split and
> §35 records it.

Not "defer the decision." The brief asks for an honest evaluation and the evaluation comes out forced, by
three independent facts that meet at the same point:

1. **The brief's §13 rejects deterministic concatenation as production v1** — that is an approved product
   decision, not a finding to re-derive.
2. **Stage 1 §1.4 establishes that the material required for the differentiated sections does not exist
   in the data model.** There is no relationship field, no contrast field, no misconception field, no
   per-concept explanation field, and 58.9% of key concepts have no explanatory text anywhere in the
   product. **Deterministic assembly cannot produce content that is not stored.**
3. **§4's distinctness rests entirely on relationships.** Remove generation and the surviving surface is
   the thin one Stage 1 already found weak *and* whose Recall section has now been removed — i.e. a note
   list. That is not a product.

**Which sections require generation:**

| Section | Generated? | Reason |
|---|---|---|
| **A. Big Picture** | **YES** | A note-count-independent synthesis cannot be assembled from N independent summaries. |
| **B. Connections** | **YES** | No relationship data exists anywhere in the repo (§4). This is the section that *is* the feature. |
| **C. Must Remember** | **YES** | Prioritizing 104 concepts down to 10 is a judgment, and no trustworthy weighting signal exists (§ brief 22, and per Stage 1 §2: concepts cannot even be deduplicated across packs). |
| **D. Source Topics** | **NO — never** | Titles, note ids and links are deterministic and already loaded. Generating them would be the "regenerate what is already available" anti-pattern the brief's §18 prohibits by name. |
| **E. Assessment handoff** | **NO — never** | Existing CTAs against existing endpoints. |

**The important corollary, and it makes the DEFER stronger rather than weaker:** Stage 1 deferred on the
grounds that the cheap version was not wanted. **This plan defers on the grounds that the version worth
building is not cheap.** Those are different verdicts with different exit conditions — the second is
exited by evidence that synthesis *works* and is *wanted* (§18), not by someone finding a cheaper way to
concatenate.

**⚠️ And this is a new prompt family, not a parameter on the existing one.** Verified this pass: every
one of `LlmStudyPackService`'s eleven methods takes a single `(studyPackTitle, studyPackSummary,
keyConcepts)` triple plus a `StudyPackGenerationContext` — there is no multi-source signature anywhere
on the interface. The Study Pack schema is a hard single-note contract (`schema.json` pins `quiz` at
exactly 5 and concepts at 2–80 chars, per Stage 1 §1.4). **Neither can be extended to multi-source; a
cross-note synthesis contract is a new prompt directory and a new response schema.**

---

## 8. Generation architecture options

**Evaluated from first principles, as the brief's §19 requires — not from what `LlmStudyPackService`
currently offers.** The first thing first principles asks is *how big is the input actually*, and Stage 1
gathered the numbers without multiplying them.

### 8.1 The arithmetic that settles most of this

*(All inputs are Stage 1 production facts, 2026-09-13, carried forward and labelled — not re-read.)*

| Input | Value | Source |
|---|---|---|
| Summary length, mean / max | 1,641 / 2,878 chars | Per Stage 1 §1.4 |
| Key concepts per pack, mean | 9.12 (range 5–10) | Per Stage 1 §1.4 |
| Non-admin Subject Plan size, mean / max | **11.4** / **77** pack-bearing notes | Per Stage 1 §7, §18 |

**Mean Subject Plan (11.4 notes):**
- summaries ≈ 11.4 × 1,641 ≈ **18,700 chars**
- concepts ≈ 11.4 × 9.12 ≈ 104 strings × ~30 chars ≈ **3,100 chars**
- titles and note ids ≈ negligible
- **total ≈ 22,000 chars ≈ ~5,500 tokens.**

**Worst case in production (77 notes):** ≈ 126,000 chars of summary + ~700 concepts ≈ **~37,000 tokens.**

**Both fit one call comfortably**, on either configured model (`LLM_MODEL_FREE` = `gpt-4.1-mini`,
`LLM_MODEL_PREMIUM` = `gpt-4.1`, per `CLAUDE.md`).

> **⚠️ The binding constraint is NOT input context. It is output size, cost per review, and the source
> cap policy.** Stage 1 inferred that cross-note synthesis "could require N calls" from the observation
> that current methods are single-source. **That inference is wrong at this scale** — it describes the
> current *API*, not the problem. Correcting it is the main thing this section does.

### 8.2 The options, compared

| | **A. One multi-source synthesis call** | **B. Staged map/reduce** | **C. Deterministic pre-compression → one call** | **D. Per-source extraction → final synthesis** | **E. Extend authored Companion to child collections** |
|---|---|---|---|---|---|
| **Shape** | One prompt: all source summaries + concepts + note ids in, one structured review out | Per-note summarize → merge → synthesize | Truncate/first-sentence each summary app-side, then one call | N calls extract per-note "candidate relations", 1 call merges | No LLM. Admin authors an `overview` / `commonMistakes` block per Subject Plan, as today at Goal level |
| **Grounding** | **Best.** The model sees every source verbatim and can only relate what it read | Weakens at each stage — the reducer sees summaries of summaries and can relate two things neither source said | Good, but pre-compression **discards exactly the detail relationships live in** (a first sentence rarely states a mechanism) | Good per-note, but the merger must relate extracts it cannot verify | **Perfect** — a human wrote it |
| **Provenance** | **Clean.** Source `noteId`s go in with each block and come back on each Connection (the `sourceStudyPackId` / `sourceNoteRefs` pattern already shipped, per Stage 1 §3) | Hard — provenance must survive two hops; the reducer is where it gets lost | Same as A | Hard, same as B | Trivial |
| **Input size** | ~5.5k tokens mean, ~37k worst case — **not a constraint** (§8.1) | Solves a problem that does not exist at this scale | Same | Same | N/A |
| **Cost / latency** | **One call.** Highest per-call cost, lowest total | N+1 calls | One call + trivial CPU | N+1 calls | **Zero** |
| **Regeneration behavior** | Re-resolve the plan, re-run. No invalidation problem while ephemeral (§9) | Same, ×(N+1) | Same | Same, ×(N+1) | Manual — goes stale silently, exactly like the existing `companionMayBeOutdated` flag |
| **Mobile output size** | Controlled by the **response schema**, which is the right place | Same | Same | Same | Controlled by the author |
| **Scales to the 77-note outlier** | Needs a source cap (§8.3) | Also needs one — N+1 calls at N=77 is worse, not better | Needs one | Worst here | N/A |

### 8.3 Recommendation

> **Option A — one multi-source synthesis call — is the recommended future architecture, with a source
> cap. Option E is a genuine, cheaper complement for curated content and should not be dismissed.**

**Why A:** it is the only option where the model sees every source verbatim, which is the only condition
under which a claimed relationship is *grounded* rather than *plausible*. Grounding is the feature's
central correctness property (brief §14B, §20), and B/C/D all trade it away to solve a context-size
problem §8.1 shows does not exist. Cost follows: one call beats N+1 on both spend and latency.

**The source cap, which is the real design decision A creates.** Three in-repo precedents exist and the
choice between them is a product judgment for the BUILD decision, not this plan:
- `ExamSourceLimitResolver.java:15-17` — derived (`questionCount / 3`)
- Long Exam — **tiered by learner level** (6 / 8 / 10, `StudySnapProperties.java:157-159`)
- `CombinedQuizService.java:42` — flat (`MAX_SOURCE_NOTES = 20`)

And one existing sampler is the right primitive if a cap bites: **`LongExamPlanSourceSampler`
(`:17-19, 22-87`) — bucket round-robin over Section / Subject Plan label, explicitly never size-weighted**,
which is exactly the property brief §22 demands (note count must not become curriculum weight). **Do not
write a second sampler.**

**Why E deserves to survive as a complement rather than a rejected alternative — this is new to Stage 2.**
The verified facts in §1.2 and §4 show NoteLib *already decided* where "how do these ideas fit together"
lives: `CompanionContent` carries `overview`, `commonMistakes`, `studyStrategy`, `faq` and `mentorTips`,
admin-authored, on top-level collections only. **`overview` and `commonMistakes` are structurally the
authored twins of Big Picture and part of Connections.** Extending that model down one level — an authored
per-Subject-Plan block — is *cheaper than any generation architecture* and needs no new prompt at all.

**So the honest answer is a split, by who authored the plan:**

| Plan type | Architecture | Why |
|---|---|---|
| **Curated / Official Review Sets** (where `docs/curriculum/` shows real authoring investment, a strategist pipeline and a curator) | **Option E is viable and cheaper.** Generation is **OPTIONAL** here. | A curator already writes program-level content; extending `validateCompanionTarget` to children is a far smaller change than a synthesis pipeline, and the output is human-verified. |
| **Learner-authored plans** (the common case — per Stage 1 §20 Q2, 5,583 of 7,608 notes carry a non-null `copied_from_note_id`, so most plans are assembled from public copies with no curator anywhere near them) | **Option A. Generation is REQUIRED.** | There is no author. Nobody will ever write the Big Picture for one learner's Structural Theory plan. |

**This split is the §7 answer stated precisely**, and it also nominates the cheapest possible first
experiment: **if the curated half is authored first (option E), it produces real examples of what a good
Subject Review looks like — which is exactly the input §19's prototype test needs to judge whether
generation can match it.**

**Not implemented. Not scoped. No prompt written.**

---

## 9. Persistence decision

> # **EPHEMERAL** — no persisted entity, by default and for any first version.

**Confirmed against the brief's §17 list:** no `ReviewPackEntity`, no `ReviewSessionEntity`, and
**explicitly not `QuickReviewSessionEntity`**. Stage 1 §10 states the reason precisely and it is carried
forward: that table *can* represent a collection-anchored session (`:41-42, :123-140`), which makes
reusing it the path of least resistance, and `EXAM_MODES.md:153` routes that choice into a five-mode
contract review rather than blocking it — **a gate, not a wall, and therefore skippable, which is what
makes it a risk worth naming rather than assuming away.**

### 9.1 Ephemeral + generated needs a second clause, and this is where the brief stops short

Ephemeral and generated are in tension: a generated review that is discarded means either an LLM call per
page view, or a cache — and a cache is the persistence this section just rejected. **The brief does not
resolve this; the plan must.** The resolution is the repo's own async-generation pattern:

> **EPHEMERAL means "no durable artifact," NOT "recomputed on every scroll."**
>
> Subject Review is **explicitly user-initiated** — the learner presses a button, sees a loading state,
> and gets a review. The result lives for that request/response and the page render that follows it.
> Leaving and returning generates again, and the learner knows that because they pressed the button.

This is the same contract the product already uses for every other generation, and the same dispatch
infrastructure applies (`StudyPackGenerationTaskDispatcher`, `dispatchAfterCommit`,
`llmParallelTaskExecutor` — per Stage 1 §3). **No auto-generation, ever** — that would violate the
versioning rule's spirit *and* spend money on a page load.

### 9.2 What persistence would have to earn, and the blocker that stands in its way

Persistence un-defers only when a requirement from the brief's §17 list is real and named — saved
history, explicit reopen/resume, sharing, export consistency, expensive-generation caching, learner
annotations, or versioned artifacts. **Cost-driven caching is the most likely of these to become real**,
and it is the one to watch: if a learner regenerates the same unchanged plan repeatedly, a cache keyed on
(plan, source-pack-version-set) becomes an economic argument rather than a product one.

**⚠️ But the moment it persists, a pre-existing blocker applies, and it must be reported as such
(brief §20):** deleting a note deletes its pack with **no check for use as a secondary source**
(`NoteService.deleteById:495-503`), and multi-note source ids inside session JSONB carry **no FK**
(`V4__quick_review_sessions.sql:4` constrains only the primary pack) — per Stage 1 §5, §11.

> **The orphan gap does NOT block ephemeral synthesis** (nothing is stored, so nothing can be orphaned) —
> the brief's §20 is explicit and this plan agrees. **It DOES block persisted generated review, and must
> be closed before any persisted variant is scoped.**

---

## 10. Source / provenance contract

| Element | Contract |
|---|---|
| **Source Note identity** | **Durable `noteId` (UUID). Never the title.** Titles are mutable, duplicated across copies, and — per Stage 1 §7.1 — most notes in production are copies of public notes, so titles collide by construction. Every Connection item and every Source Topics row carries `noteId`. |
| **Study Pack identity** | Carried alongside `noteId` for the generation input (`studyPackId`), matching the shipped `QuizItem.sourceStudyPackId` pattern (per Stage 1 §3). **Not** exposed as the learner-facing link target — the learner navigates to a Note, not a pack. |
| **Eligibility** | **Consumed from the sibling Stage 2 plan's finalized capability contract** — see below, §10.1. |
| **Regeneration behavior** | **Ephemeral: no invalidation needed.** Each review resolves the plan and its members' *current* Study Packs at request time, so a regenerated source is simply read in its new form on the next review. This is the single largest architectural benefit of §9's EPHEMERAL verdict — `v0.143.0`/`v0.144.0` had to ship exam-pool invalidation for exactly the class of bug this avoids by construction. **Persisted: would require durable source provenance, regeneration invalidation, stale-artifact semantics and copy/share semantics** — see §9.2. |
| **Deleted-source behavior** | **Ephemeral: self-healing.** A deleted note is simply not a plan member on the next resolve, and nothing dangles. **Persisted: this is the orphan gap** (§9.2) and is a blocker. |
| **Authorization** | Every resolve goes through `PlanSourcedExamVerifier`, which re-checks ownership on every call (`:44-79`, javadoc at `:18-29` explains it exists because a duplicated copy of this rule drifted). **Do not write a second resolver.** |

### 10.1 Eligibility contract — consumed, not re-derived

Per the brief's §10 and §32, this plan **consumes the artifact-first contract finalized in
`docs/claude-plans/artifact-first-learning-availability-stage2.md` §C** and does not invent a competing
formulation. That plan's §C defines four primitive facts, all derived at response-build time and never
persisted, via a new `StudyPackArtifactFacts` utility:

| §C fact | Subject Review's requirement |
|---|---|
| `hasKeyConcepts` | **Required** for any source contributing to Must Remember. |
| `studyPackDone` | **Required** for any source contributing to Big Picture or Connections — synthesis reads the `summary`, and a pack that is not `DONE` is not a completed artifact. This mirrors the predicate Long Exam and Board Exam already use (`StudyPackStatus.DONE`). |
| `hasQuizQuestions` | **Not required.** Subject Review consumes no quiz items (§6.1 removes Recall). Named here so it is explicit rather than merely absent. |
| `hasTeacherQuiz` | **Not required.** Teacher-quiz availability is unrelated to consolidation. |

**Three properties inherited from that contract, binding here:**

1. **`NoteStatus` is never consulted.** Not `GENERATED`, not `DRAFT`, not the derived `studyPackStatus`
   lifecycle string. Per that plan's §B: *"never use Note generation lifecycle as a proxy for learning
   artifact availability."*
2. **A Note whose latest regeneration FAILED but whose prior Study Pack is intact remains eligible** —
   the brief's §10 requires exactly this, and it is the headline case the sibling plan exists to fix.
3. **Entitlement is resolved separately**, through `FeatureGateService`, which knows nothing about
   artifacts (that plan's §C item 8). §17 of this document keeps pricing out of scope; the *separation*
   is architectural regardless of whether any gate is ever applied.

**Dependency, stated plainly:** Subject Review must not be built before that contract ships, or it will
re-create the lifecycle-gating defect on a brand-new surface. The sibling plan is itself unbuilt as of
this writing.

---

## 11. Mastery contract

> **This is a hard rule (brief §16), and it is stated as four NOs because each has a distinct mechanism
> that could violate it.**

| Write | Verdict | Why, and the specific mechanism being forbidden |
|---|---|---|
| **`ConceptHealth`** | **NO** | Displaying material is not retrieval evidence. There are exactly five writers today (`QuickReviewSessionService:304,308`; `ChallengeQuizService:1018,1037`; `QuickReviewAdaptivePracticeService:824,850,853`; `LongExamService:584,592`; `InterviewPracticeService:307,315` — per Stage 1 §12) and Subject Review does not become a sixth. **A concept the learner *read about* must never look identical in the data to a concept they *answered a question about*** — that would silently corrupt readiness, Adaptive Practice targeting and Board Exam sampling, all of which read `ConceptHealth`. |
| **Readiness** | **NO** | Follows from the above — readiness is computed *from* `ConceptHealth`, so refusing the write is the whole guard. No separate readiness write path is added. |
| **Activity / streak** | **NO** | `ActivityType` has 7 values and `MEANINGFUL_STUDY_ACTIVITIES` feeds the study streak via `ActivityTrackingEventListener.updateStudyStreak:82-113` (per Stage 1 §12). **Adding a review-completion activity type would inflate streaks for a surface where the learner did nothing measurable.** Analytics events (§17) are a different system and do not touch this. |
| **New exam mode or sub-mode** | **NO** | `EXAM_MODES.md:25` locks five modes. Subject Review is not a mode, has no session row, no discriminator, no timer and no score. |
| **`memorization_cards`** | **NO** | Would silently alter a *different* feature's SM-2 schedule (per Stage 1 §12). Named separately because it is the non-obvious one. |
| **`quick_review_sessions`** | **NO** | §9. It is reachable and it is the wrong tool. |

**Why, in one sentence:** *viewing, expanding, scrolling or finishing a Subject Review is not evidence of
learning mastery, and the product already encodes that principle for a review-only surface* —
`MemorizationCardEntity.java:18` states the firewall in the entity's own class comment, and
`EXAM_MODES.md:143`/`:151` lock it in the canonical doc. **Assessment remains solely responsible for
mastery evidence.**

---

## 12. "Studied" semantics

### 12.1 The claim, stated plainly

> **NoteLib's current telemetry cannot reliably prove that a learner studied a Note.**

A learner may study a Note by reading it, reading the summary, reviewing key concepts, using Flashcards,
using Memorization, or taking an assessment. **Only the last two are observable**, and one of those two
barely: per Stage 1 §1.5, **Flashcards has no persistence and no analytics event at all** — the only
flashcard-shaped constant in the 135-value `AnalyticsEventType` enum is `PUBLIC_NOTE_FLASHCARDS_CLICKED`
(`:78`), a public *landing-page* CTA. **In-app flashcard usage is not measurable by any means today.**
Note reading and summary reading are likewise unobserved.

**Therefore Stage 1's §7.1 proxy — "notes with completed quiz sessions" — must NOT be carried forward as
product truth.** It was a legitimate *sizing* device in an audit. It is not a definition of studied, and
using it as one would under-count every learner who reads rather than tests.

### 12.2 Eligibility is defined WITHOUT reference to engagement history

Two separate questions, and conflating them is the error this section exists to prevent:

| Question | Answer source | Used for |
|---|---|---|
| **"Can this Note contribute source material?"** | The artifact-first contract (§10.1): `studyPackDone` + `hasKeyConcepts`. **No session history is consulted.** | Eligibility. |
| **"Has this learner studied this Note?"** | **Not answerable today.** | **Nothing.** Not eligibility, not copy, not ordering, not emphasis. |

**Consequence, accepted deliberately (brief §11):** a Subject Plan may contain Notes the learner has never
opened, and they will contribute. **That is correct**, because Subject Review's job is to explain how the
subject's topics fit together — which is *more* useful, not less, for a topic the learner has not reached
yet. The alternative (gating on study history) produces the four-Subject-Plan addressable population
Stage 1 §7.1 measured, and answers a question the telemetry cannot support.

### 12.3 Safe learner-facing wording

| ✅ Safe — makes no claim about history | ❌ Forbidden until a trustworthy study-engagement signal exists |
|---|---|
| *"Review this subject"* | *"Review what you've studied"* |
| *"Bring these topics together"* | *"Everything you've learned"* |
| *"See how these topics fit together"* | *"What you've studied this week"* |
| *"Review this subject before you test yourself"* | *"Review all studied topics"* |
| *"Based on the N notes in this plan"* | *"Based on the N notes you've completed"* |

**Note what this rules out, since Stage 1 proposed it:** Stage 1's own recommended mental model was
*"Bring the topics **you've been studying** in this Subject Plan back together before you test yourself."*
**That phrasing is now forbidden** by this section — it is exactly the unsupported history claim. The
replacement is in §35.

**Un-defer condition for the forbidden column:** a durable, comprehensive study-engagement signal — one
that observes note reading, summary reading and Flashcards, not only assessment. **None exists and none
is proposed here.**

---

## 13. Subject Plan UI entry point

### 13.1 Current action density — re-verified this pass, and it produces a number rather than an adjective

Stage 1 §18 rated Review Set detail *"High — the page is already loaded."* This pass read the current leaf
Subject Plan render tree (`frontend/app/collections/[id]/collection-detail-page-client.tsx`, 4,383 lines)
and found something more specific and more decisive.

**The leaf Subject Plan page renders, in order:** `BackLink` → `PlanHeroCard` (with `CollectionActionsMenu`
overflow: edit / primary toggle / companion / delete / publish) → conditional `ReviewSetSourceUpdateCard`
→ conditional `ReviewSetPublicationCard` → **`TodaysFocusCard`** → conditional `GuidanceTip` → conditional
skipped/error notices → `ReadinessSummary` (compact, with its own footer link to `/progress`) →
`CompanionDisplayCard` → conditional `AskCompanionPanel` (**unreachable on a leaf — §1.2**) → the notes
list card, each row carrying up to three buttons.

> **⚠️ The decisive finding: `TodaysFocusCard` already carries FOUR action slots plus a tip slot** —
> `action` (Continue: `primaryStudyAction`), `terminalAction` (Long Exam / Board Exam / exam-builder:
> `terminalSecondaryAction`), `dueConceptReviewHref`, and `planPracticeAction` (Practice whole plan), plus
> `mentorTip`. Read directly this pass at the leaf render branch.

**A fifth action in that card IS the overload.** That is not a judgment about crowding — it is the slot
count.

### 13.2 Options compared

| Option (brief §24) | Verdict |
|---|---|
| **A. Primary / secondary Subject Plan action** | **Reject.** This means a fifth slot in `TodaysFocusCard`, or competing with "Continue learning" for primary. Continue must stay primary — it is the higher-frequency job and the one that produces assessment evidence. |
| **B. Action overflow** (`CollectionActionsMenu`) | **Reject.** That menu is *management* (edit / delete / publish / companion), not *learning*. A learning action there is a category error and would be undiscoverable. |
| **C. Progress / readiness area** | **Viable, and the strongest of the five.** `ReadinessSummary` is already its own card with its own footer slot (`ReadinessCardFooter`, currently a link to `/progress?collectionId=`). It is adjacent in meaning — *here is where you stand in this subject* sits naturally beside *here is how this subject fits together* — and it needs **no new slot in the already-four-slot card**. |
| **D. Contextual card once enough material exists** | **Viable, and the right *gating* mechanism regardless of where the entry point lands.** With a threshold (≥3 eligible source notes, say), it prevents a 1-note plan from offering a cross-note review with nothing to cross. |
| **E. Another minimal placement** | Not needed. |

### 13.3 Recommendation

> **C + D: a slot in or beside the readiness card, revealed only once the plan has enough eligible source
> material.** Never a fifth button in `TodaysFocusCard`.

**Mobile-first properties this placement must hold:**
- The entry point is **one** control, not a row.
- It appears **below the fold on mobile by design** — this is a considered action, not the primary path,
  and competing for above-the-fold space with "Continue learning" would be wrong even if space existed.
- It must not push `TodaysFocusCard` or the notes list further down; a card that only sometimes renders
  (D's threshold) is preferable to one that always renders empty.
- The review itself is a **separate route**, not an expanding accordion on this page — §6's content does
  not belong inside an already-11-block page.

### 13.4 ⚠️ A naming dependency, flagged not resolved

**"Subject Review" collides with a label that is not always "Subject."** `frontend/lib/collection-labels.ts`
keys child-collection labels by `ProfileType` via `subjectSingular`: **"Subject Plan"** for `STUDENT` and
`BOARD_EXAM`, but **"Unit"** for `TEACHER`. A button reading *"Subject Review"* on a page whose own
vocabulary says "Unit" reads wrong.

Three ways out, none chosen here (the brief's §2 says not to lock final copy): make the label
profile-keyed like every other collection label; pick a label that is vocabulary-neutral; or accept the
collision for non-teacher profiles and label it differently for `TEACHER`. **"Subject Review" remains the
preferred internal and default learner-facing candidate; this is a copy dependency for the BUILD
decision, not a reason to rename now.**

---

## 14. Assessment handoff

**Subject Review is not assessment (§11). After it, the truthful next actions are the ones that already
exist over the same source set** — nothing new is built, and no recall engine is embedded (brief §14E).

| Offered after a Subject Plan review | Truthful? | Evidence |
|---|---|---|
| **"Take the Long Exam"** | **YES — the cleanest handoff.** It is plan-addressed over the same member set (`LongExamService.java:170-175`), matching the hierarchy exactly: *connect the topics → test the subject*. | Per Stage 1 §13 |
| **"Practice weak areas across this plan"** (plan-scoped Adaptive Practice) | **YES, conditionally.** It accepts the same `sourceCollectionId` (`QuickReviewAdaptivePracticeService.java:944`). **But it is remediation** — it needs prior `ConceptHealth` evidence to have anything to target, and a learner who just finished *consolidating* may have none. Offer it only when the plan actually has weak/due concepts, which the page already knows (`dueConceptCount`, per Stage 1 §3). | Per Stage 1 §13; brief §6 |
| **"Take the Board Exam"** | **Only with an explicit caveat, and preferably not at all here.** Board Exam resolves a child Subject Plan **up to its parent Goal** and samples the whole Review Set (`ChallengeQuizService.java:1656-1663`) — **its source set is strictly broader than the review's.** Offering it unqualified after a single-subject review tells the learner they are being tested on what they just reviewed, which is false. | Per Stage 1 §13 |
| **A multi-note Quick Review inside Subject Review** | **NO — prohibited.** Brief §14E. It would be a new assessment engine, and reusing `quiz` items without scoring is Quick Review minus the score. | §6.1, §11 |
| **Anything for an arbitrary note selection** | **N/A — and there is no truthful option.** Multi-note Challenge Quiz requires a *verified plan* membership predicate, not an arbitrary selection (`EXAM_MODES.md:78`). One more reason §5 rejects free selection. | Per Stage 1 §13 |

**Recommended minimum:** one primary handoff (**Long Exam**), plus **Practice weak areas** shown only when
the plan has due or weak concepts. **No new mode, no new sub-mode, no new discriminator, no new engine.**

---

## 15. Export

> **CONFIRMED DEFERRED.** Subject Review does not build PDF or DOCX, and must not become *"Generate
> reviewer PDF."*

Three reasons, in order of weight:

1. **Identity risk, which is the real one.** Per Stage 1 §18, "export a reviewer" would be the most
   tangible thing the feature could do and would become its identity — the multi-note document generator
   the brief prohibits by name.
2. **Nothing exists to reuse.** Per Stage 1 §1.6: no PDF writer anywhere in the repo (`pdfbox` is a
   dependency but reads uploaded PDFs for OCR only); no `jsPDF` / `html2pdf` / `html2canvas` /
   `window.print` / `@media print` in `frontend/`. The DOCX writer is quiz-shaped and `ADMIN`/`TEACHER`-only
   (`GeneratedQuizService.requireTeacherExportUser:597-604`).
3. **Export pressures persistence.** Export consistency is on the brief's §17 list of things that could
   earn persistence — building it would drag §9's EPHEMERAL verdict along with it.

**If it is ever built:** reuse the PDF quota that already exists (`UserUsageEntity.pdfExportsCount:72`,
`ExportUsageProtectionService.assertPdfQuotaAvailable/recordPdfUsage:24-35`, limits at
`StudySnapProperties.java:273-280`). **Do not invent a second export quota.** A deterministic re-resolve
means export needs no persistence.

### 15.1 The dead PDF meter is a separate product issue — backlogged independently

**Independent of Subject Review, and explicitly not solved by it (brief §28):** `frontend/app/settings/page.tsx:1094-1100`
renders an `ExportUsageMetric` labelled **"PDF Exports"** with real per-plan limits (Free 2/mo, Plus 15/mo,
Pro unlimited — `frontend/lib/pricing-config.ts:15,24`), backed by a fully-wired backend meter that has
**zero callers** anywhere in `backend/src/main/java` (per Stage 1 §1.6, verified there by exhaustive grep).

> **Every user currently sees a quota meter for a capability no code path can consume.** It needs its own
> owner decision — build the exporter, hide the meter, or label it as coming — and it is **not Subject
> Review's to make or to justify.** Carried into §20's deferred list solely so it is not lost.

---

## 16. Coach / Companion

> **CONFIRMED DEFERRED.** Subject Review is not added to Today's Focus, Continue Studying, Coach, or
> Companion recommendations.

**Reasons:**

1. **Dashboard recommendation space is scarce** (brief §29) — `ContinueStudying` is a 5-tier waterfall and
   `TodayFocus` a 4-tier waterfall, per Stage 1 §4, and every dashboard load executes them. **A feature
   must earn a tier through demonstrated value, and this one has no usage at all.**
2. **It would fire for almost nobody.** A rule like *"you've studied 7 topics in this subject — review
   them together"* would need a studied-note count per subject, which per §12 **is not trustworthy**, and
   which per Stage 1 §15 would fire for **4 non-admin accounts at a 3-note threshold and 0 at 7**
   *(Production, 2026-09-13)*.
3. **Companion integration specifically is structurally blocked at this scope** — §1.2: a child collection
   cannot hold Companion content at all, and Ask Companion rejects children outright. Reaching a Subject
   Plan through Companion means §8's option E, which is a *content-authoring* decision, not a
   recommendation-engine one.

**What would justify future integration — and note one thing it does NOT require.** Per the brief's §29,
*a future recommendation need not wait for canonical concept identity* if it is based on Subject Plan
activity rather than cross-pack concept matching. So the condition is:

> Subject Review has demonstrated value on its own (§18's build trigger has fired and the feature is in
> use), **and** a plan-activity-based rule can be written without consulting `ConceptHealth` across packs.

**Cross-pack concept identity remains foreclosed** for anything that *does* need it —
`DashboardService.java:471-473` states it in a code comment, and `ROADMAP.md:508` records the
owner-executed sizing read that returned **0.4%** (6 user-concept pairs across 5 users, against 10,361
distinct authored concepts) with an explicit re-read trigger at ~5%. **Do not design either now.**

---

## 17. Analytics / validation

**No analytics are designed or added now** (brief §30) — the feature is deferred, and an event taxonomy
written 41 releases before a feature ships is a Backlog row that goes stale (per `CLAUDE.md`'s own
stale-row doctrine). What follows is the **minimum future set**, recorded so a BUILD decision does not
have to re-derive it.

### 17.1 The minimum future measurement

**Four questions, in priority order. Three events answer all four** — the fourth is derivable from
existing events with no new type, using `countDistinctUsersWithEventAfterEvent`
(`AnalyticsEventRepository.java:229-261`, generic JPQL, no new repository code).

| Question | Event | Why it is the right question |
|---|---|---|
| **Did a learner open Subject Review at all?** | `SUBJECT_REVIEW_STARTED` (with `collectionId`, eligible source count) | The first and cheapest falsifier. Zero here ends the discussion. |
| **Did they engage past the first screen?** | `SUBJECT_REVIEW_ENGAGED` — fired on a **deliberate interaction** (expanding a Connection, opening a source) | **⚠️ Explicitly NOT "scrolled to bottom"** (brief §30). Scroll depth is not pedagogical completion and must not be treated as such. |
| **Did they go back to a source Note?** | `SUBJECT_REVIEW_SOURCE_OPENED` (with `noteId`) | The strongest single signal that the synthesis *taught* something — the learner found a topic worth returning to. |
| **Did they proceed to assessment afterward?** | **No new event.** Derive from `SUBJECT_REVIEW_STARTED` → `LONG_EXAM_STARTED` / `ADAPTIVE_PRACTICE_STARTED` | The handoff in §14 either works or it does not. |
| **Did they use it repeatedly?** | Derived from repeat `SUBJECT_REVIEW_STARTED` per user | Repeat use is the value signal; a single try is curiosity. |

**Two implementation notes for whenever this ships**, both learned the hard way in this repo:
- **Add to both files by hand.** `AnalyticsEventType.java` (135 constants) and the frontend string-literal
  union at `frontend/lib/api.ts:599-729` are not generated from one another and **have already drifted**
  (`BOARD_EXAM_COMPLETED` exists backend-side and is missing frontend-side) — per Stage 1 §16.
- **Verify the event is EMITTING, not merely present in the enum.** `ROADMAP.md`'s checkpoint rows make
  this an explicit standard (*"instrumentation verified EMITTING at signoff, not merely enum-resident"*),
  and a checkpoint whose metric was never built is the "decorative checkpoint" that doc names.

### 17.2 ⚠️ Adaptive Practice usage must NOT be the validation proxy

**Three independent reasons, and the brief's §6 and §25 each supply one:**

1. **Different job** (brief §6): Adaptive Practice asks *what should I repair?*; Subject Review asks *how
   does this fit together?* Remediation demand and comprehension demand are not the same signal.
2. **Different job again, for the Phase-D proxy** (brief §25): engaging with *"12 concepts due"* does not
   prove demand for *"synthesize my Structural Engineering topics."* Stage 1 §6 proposed exactly this as a
   demand test; **that proposal is superseded** — see §18.3.
3. **The instrument itself is compromised, newly found in this pass** (§1.3): the leaf Subject Plan had
   **no plan-scoped practice CTA at all** until `d1dacb3d` / `v0.109.0` on 2026-09-03. The zero is measured
   over ten days of leaf-view exposure, not the capability's lifetime.

---

## 18. Build trigger

**Written in the shape `ROADMAP.md`'s checkpoint rows use** — a stated read, a kill criterion, and a
denominator clause — because a trigger without a denominator clause fires on noise at this scale.

### 18.1 The gate that comes FIRST, and costs nothing

> **GATE 0 — the manual synthesis prototype. No engineering. Run this before any other trigger is
> evaluated.**

**What:** take **three real Subject Plans** spanning the product's actual range (one curated Review Set
subject, one learner-assembled plan of copied public notes, one small 3–4 note plan). For each, paste the
source notes' **titles + summaries + key concepts** — the exact material §8's option A would send — into a
chat model and ask for the §6 structure: Big Picture, Connections, Must Remember.

**What it tests, and why it is the highest-value experiment available:** **whether the source material can
support synthesis at all.** Per Stage 1 §1.4, 58.9% of key concepts are bare noun phrases with no
definition anywhere, and summaries are unstructured prose. **It is entirely possible that a model given
this material cannot produce a grounded Connection — only plausible-sounding ones.** That is the single
fact that decides the feature, and **it is knowable today for zero engineering cost.**

**Judged by the owner against three criteria:**
1. Are the Connections **true** — verifiable against the source notes, not merely plausible?
2. Are they **non-obvious** — do they say something the note titles alone do not?
3. Is Must Remember a genuine **prioritization**, or a reshuffled concept list?

**Kill criterion:** if the output on the learner-assembled plan fails (1) or (2) — relationships that are
confabulated or restate the titles — **generation cannot deliver the differentiated value, and §19's
abandon trigger fires.** A pass on the curated plan but a fail on the learner-assembled one is **not** a
pass; it points at §8's option E for curated content and abandonment for the general case.

**⚠️ Run GATE 0 before spending anything on the demand triggers below.** The demand triggers ask *does
anyone want this*; GATE 0 asks *can it exist*. Asking them in the wrong order risks measuring demand for
something that cannot be built.

**✅ Owner note, 2026-09-13: GATE 0 will be run from the Release Implementor session, not scoped or
executed here.** It needs no engineering and no kickoff — three real Subject Plans, pasted into a chat
model, judged against the three criteria below. Recorded so a future kickoff scan does not re-propose it
as unstarted work.

### 18.2 The demand triggers — BUILD requires GATE 0 plus at least TWO of these

Today's values are stated beside each so the gap is visible and the trigger is falsifiable rather than
aspirational. **All "today" values are *(Production, 2026-09-13)* per Stage 1 and decay.**

| # | Trigger | Read | Today | Threshold |
|---|---|---|---|---|
| **T1** | **Multi-note study behaviour exists at all** | Distinct notes studied per account in any 7-day window | **Median 1 ever; 2 non-admin accounts have EVER reached ≥5 in 7 days; 15 have reached ≥3** (per Stage 1 §1.2) | **≥ 10 non-admin accounts reach ≥5 distinct notes in a 7-day window, in the same 30-day period.** This is the closest observable proxy for the learner the feature is for. |
| **T2** | **A base big enough to observe anything** | Distinct users with a completed session in 30 days | **9** (per Stage 1 §1.2) | **≥ 50 monthly actives.** Below this, no funnel in §17 can distinguish a signal from one enthusiastic user. |
| **T3** | **Plan-level surfaces are used at all** | `ADAPTIVE_PRACTICE_STARTED` with `sourceScope IN ('plan','review-set')`, per `ROADMAP.md:520` | **0 of 729 sessions ever collection-anchored**, over ten days of leaf-view exposure (§1.3) | **Any nonzero count on a readable denominator.** ⚠️ **Weak evidence by design** — per §17.2 this is *caution about plan-level surfaces*, never a test of Subject Review. It can support a BUILD; **it can never alone block one.** |
| **T4** | **Learners ask for it** | Feedback / support requests naming subject-level consolidation | Unknown — not measured | **≥ 3 unprompted requests** for seeing how a subject's topics relate. **The strongest single signal on this list**, because it is the only one that is not a proxy. |
| **T5** | **Review Set adoption grows** | Subject Plans with ≥3 artifact-eligible notes, and their owners | **452 of 453 plans qualify on artifacts; 88 owners** (per Stage 1 §7.1) | Already effectively met on artifacts. **⚠️ Do not count this as one of the two** — it measures material, not demand, and counting it would let the trigger fire on a condition that is already true. |

**Formal BUILD condition:**

> **GATE 0 passes on a learner-assembled plan, AND at least two of {T1, T2, T3, T4} are met, of which at
> least one is T1 or T4.**

**⚠️ Denominator clause, binding — the same one `ROADMAP.md`'s rows carry:** at fewer than 50 monthly
actives, a null result on T1, T3 or T4 is **"not yet measurable," not a verdict.** It is a re-date, not an
abandonment. **T2 is therefore both a trigger and the readability precondition for the others.**

### 18.3 One thing that is explicitly NOT a build trigger

**Stage 1 §6/§19's "Phase D" — surfacing the existing `dueConceptCount` / `dueConcepts` /
`lastSessionCompletedAt` aggregation as a demand test — is superseded by the brief's §25 and is not part
of this trigger set.** Engagement with due-concept counts measures interest in *remediation status*, not
demand for *synthesis*.

**But the other half of the brief's §25 stands:** if the Subject Plan page genuinely needs a readability
or progress improvement, **it may still be worth doing on its own merits** — classified as an
**independent Subject Plan UX improvement**, never as Subject Review validation, and never as a
precondition for it. §13.1's render-tree read is directly useful to whoever scopes that.

---

## 19. Abandon trigger

**Any ONE of these fires and the concept is dropped, not re-dated.** The first is the cheapest and the
most likely.

| # | Trigger | How it is observed | Why it is decisive |
|---|---|---|---|
| **A1** | **Synthesis quality is unreliable on real material** | **GATE 0** (§18.1) on a learner-assembled plan: Connections are confabulated, restate the titles, or cannot be verified against the sources | **The single most likely outcome and the cheapest to test.** 58.9% of key concepts are bare noun phrases and summaries are unstructured prose — **it is genuinely unknown whether there is enough signal to relate.** If there is not, the feature cannot exist in any architecture, and no amount of demand changes that. |
| **A2** | **The prototype adds nothing beyond the existing Study Packs** | GATE 0 output, judged against simply reading the notes' own summaries | If the owner reads both and cannot say what the synthesis added, the differentiated value claimed in §4 is not real. Distinctness in a table is not value in a product. |
| **A3** | **Learners consistently go Study Pack → assessment and never want an intermediate step** | After BUILD: `SUBJECT_REVIEW_STARTED` (§17) near zero on a readable denominator (≥50 monthly actives, ≥90 days, feature discoverable), while note-scoped and plan-scoped assessment starts continue | This is the *"consolidation is not a step learners take"* hypothesis, and it is a legitimate product answer. **Requires a readable denominator** — the whole point of §18.2's T2. |
| **A4** | **Cost or latency materially exceeds the learning value** | After BUILD: per-review generation cost against observed repeat usage; or generation latency long enough that learners abandon before it renders | A review that costs a Study Pack's worth of tokens and is opened once per learner is not economic. **Watch this first if §9.2's caching argument starts being made** — the cache is a symptom, not a fix. |
| **A5** | **Engagement stays negligible after meaningful exposure** | After BUILD: `SUBJECT_REVIEW_ENGAGED` / `SUBJECT_REVIEW_STARTED` stays near zero across ≥90 days with the entry point discoverable | **⚠️ This trigger must NOT fire on the mistake §1.3 documents.** `v0.107.0`'s plan-scoped capability read zero partly because its entry point was **missing from the leaf view for most of its life**. Before A5 fires, **verify the entry point was actually present and reachable for the whole window** — otherwise it measures the product's own silence, exactly as `ROADMAP.md:520`'s lift-to-`[EFFORT]` reasoning warns. |

**⚠️ The trap this section is written to avoid:** an abandon trigger that is merely *"the build trigger did
not fire"* is not a trigger, it is a restatement — it would leave the concept deferred forever with no
mechanism to close it. **A1 and A2 are real, independent, and cheap**, and they are the ones to run.

---

## 20. Deferred architecture

Everything below is **explicitly out of scope**, both for this plan and for any first version. Listed so a
future session cannot quietly re-introduce one as "obviously part of it."

| Deferred | Status / condition |
|---|---|
| **Arbitrary selected Notes as scope** | Rejected, §5. Re-opens only if free selection gains a truthful assessment handoff — none exists (`EXAM_MODES.md:78`). |
| **Whole Review Set / Goal review** | Rejected, §5. Wrong conceptual level, not merely too large. |
| **`ReviewPackEntity` / `ReviewSessionEntity`** | Rejected, §9. Must be *earned* by a named requirement from the brief's §17 list. |
| **Reusing `QuickReviewSessionEntity`** | Rejected, §9. Technically reachable, which is why it is named. Would owe an `EXAM_MODES.md` amendment and a five-mode contract review (`:153`). |
| **Saved review history / resume** | Deferred with persistence, §9. |
| **PDF / DOCX export** | Deferred, §15. No writer exists. |
| **⚠️ The dead "PDF Exports" settings meter** | **Separate live issue, needs its own owner decision.** §15.1. **Not Subject Review's to solve.** |
| **Coach / Today's Focus / Companion integration** | Deferred, §16. Condition stated there. |
| **Extending authored Companion to child collections (§8 option E)** | **Not rejected — a real, cheaper complement for curated content.** Deferred as its own decision, with its own scope (`validateCompanionTarget`, `NoteCollectionService.java:884`). |
| **Spaced scheduling / review intervals** | Rejected. SM-2 already exists in `memorization_cards` and is firewalled from readiness; **any future work extends the approved one, never adds a second** (per Stage 1 §1.5, §15). |
| **Cross-pack `ConceptHealth` merging** | Rejected, §11 and brief §21. `DashboardService.java:471-473`. |
| **Canonical cross-pack concept identity** | Foreclosed, §16. `ROADMAP.md:508`, re-read trigger at ~5% (currently 0.4%). **⚠️ A generated explanatory relationship is NOT a mastery identity** — brief §21's hard boundary: *"these ideas are related"* must never become *"these are the same tracked concept."* |
| **Learner annotations** | Deferred with persistence, §9. |
| **Social sharing** | Deferred with persistence, §9. Also drags copy/share semantics in. |
| **A new assessment mode or sub-mode** | Rejected permanently, §11. `EXAM_MODES.md:25`. |
| **Pricing / quota / plan gating** | Out of scope, §17 of the brief. **If option A's per-review cost becomes material it is a future pricing consideration only** — no assumption that Subject Review is Premium, Pro-only or metered. Product value first. |
| **Recall subsystem / self-check** | **Not core**, §6.1. Requires separate justification, not inclusion by default. |
| **Note-count-derived weighting** | Rejected, brief §22. Importance in Must Remember is **synthesis-oriented, not curriculum-weighted** — no trustworthy weighting signal exists, and `LongExamPlanSourceSampler` is already explicitly never size-weighted (§8.3). |
| **Analytics taxonomy beyond §17's three events** | Deferred, §17. Nothing is added while the feature is deferred. |

---

## SUBJECT REVIEW — STAGE 2 DECISION

**Product verdict:**
**DEFER** — preserve the product concept, reject the current v1. **⚠️ And note the verdict changed its
grounds:** Stage 1 deferred because the cheap version was too thin to be wanted. This plan defers because
**the version worth building requires generation (§7), and it has not been shown that generation can
produce grounded cross-note relationships from this product's actual material (§18.1 GATE 0).**

**Learner-facing candidate:**
Subject Review *(with a copy dependency: `collection-labels.ts` labels a child collection "Unit" for
`TEACHER` profiles — §13.4, flagged not resolved)*

**Internal concept:**
Cross-Note Learning Consolidation

**Primary learner job:**
*"Show me how the topics in this subject fit together, before I test myself on them."*

**Unique value:**
The only surface that would relate **a learner's own notes** to each other, at **subject scope**, without
requiring a **curator** to have authored the relationship or an **assessment** to have measured it. All
nine compared surfaces fail on at least one of those three (§4); the closest contender, Ask Companion,
fails all three — it is admin-authored, grounded on Companion content rather than notes, and structurally
unreachable on a Subject Plan (`NoteCollectionService.java:884`, `assertCompanionAvailable`).

**First scope:**
**Subject Plan** — a single child `NoteCollectionEntity`, resolved through the existing
`PlanSourcedExamVerifier`. Not selected Notes, not Review Set, not Coach-resolved.

**Production-worthy minimum:**
**Big Picture** (note-count-independent synthesis) + **Connections** (5–7 grounded, typed relationship
items — the differentiated value) + **Must Remember** (8–10 prioritized ideas, deliberately smaller than
the ~104 source concepts) + **Source Topics** (deterministic, by durable `noteId`) + **assessment
handoff**.

**Deterministic concatenation sufficient:**
**NO**

**Cross-note synthesis:**
**REQUIRED** for Big Picture, Connections and Must Remember. **NEVER** for Source Topics or the handoff.
*Split by plan type (§8.3): **OPTIONAL** for curated/Official Review Sets, where an authored Companion-style
block (option E) can do the same job more cheaply and with human verification; **REQUIRED** for
learner-authored plans, where no author exists.*

**Persisted entity:**
**NO** by default

**Initial persistence model:**
**EPHEMERAL** — and with the clause the brief leaves open (§9.1): ephemeral means **no durable artifact**,
**not** recomputed on every scroll. Subject Review is **explicitly user-initiated**, with a loading state,
request-scoped. **No auto-generation, ever.**

**ConceptHealth writes:**
**NO**

**Readiness writes:**
**NO**

**New exam mode:**
**NO**

**Recall section:**
**NOT CORE** — removed entirely, and its removal is what makes the §4 distinctness hold.

**Export:**
**DEFERRED** *(and the dead "PDF Exports" settings meter is a separate live issue with its own owner
decision — §15.1)*

**Coach:**
**DEFERRED**

**"Studied" used as eligibility:**
**NO** — current telemetry cannot prove a Note was studied (note reading, summary reading and in-app
Flashcards are all unobserved), so eligibility is artifact-based only and the learner-facing copy makes no
history claim (§12).

**Artifact-first eligibility:**
**YES** — consuming `docs/claude-plans/artifact-first-learning-availability-stage2.md` §C: **`studyPackDone`**
(Big Picture, Connections) and **`hasKeyConcepts`** (Must Remember), derived via `StudyPackArtifactFacts`,
never from `NoteStatus`. `hasQuizQuestions` and `hasTeacherQuiz` are **not** required. A Note whose latest
regeneration failed with an intact prior pack **stays eligible**.

**Assessment handoff:**
**Long Exam** as the primary handoff (plan-addressed over the same member set — *connect the topics → test
the subject*), plus **plan-scoped Adaptive Practice** shown only when the plan actually has due or weak
concepts. **Board Exam only with an explicit caveat** that it samples the whole parent Goal, or not at
all. No new engine, no embedded recall.

**Main build trigger:**
**GATE 0 — the zero-engineering manual synthesis prototype on three real Subject Plans (§18.1) — passes on
a learner-assembled plan, AND at least two of {T1 multi-note study behaviour, T2 ≥50 monthly actives, T3
any nonzero plan-scoped session, T4 ≥3 unprompted learner requests}, of which at least one is T1 or T4.**
Binding denominator clause: below 50 monthly actives, a null is a re-date, not a verdict.

**Main abandon trigger:**
**A1 — GATE 0 fails on a learner-assembled plan**: the model cannot produce Connections that are both true
against the sources and non-obvious beyond the titles. With 58.9% of key concepts carrying no definition
anywhere in the product, this is the most likely outcome, it is the cheapest thing on either list to test,
and if it fires the feature cannot exist in any architecture.

**Biggest product risk:**
**Building the fourth low-adoption review-only surface.** Flashcards (unmeasurable — no persistence, no
events), Memorization (3 users / 7 cards / 10 weeks) and plan-scoped Adaptive Practice (0 of 729 sessions
ever collection-anchored) are three consecutive bets in exactly this category. **§4's distinctness argument
is a reason the fourth would be *different*; it is not evidence that it would be *wanted*.**

**Biggest architecture risk:**
**Confabulated relationships presented as grounded.** A Connection asserting *"X is the foundation for Y"*
that no source note supports is worse than no Subject Review at all — it is a confident false statement
about a learner's own material, on a surface whose entire value proposition is trustworthiness. The
mitigations are structural (option A's verbatim-source grounding, §8.3; the ban on inferring from titles,
§6B; durable `noteId` provenance on every item, §10) and the test is GATE 0 (§18.1). **The adjacent risk is
that such a relationship then leaks into mastery semantics — brief §21's hard boundary: *"these ideas are
related"* must never become *"these are the same tracked concept."***

---

> **A Study Pack helps the learner understand one Note. Subject Review should only exist if it helps the
> learner understand how multiple Notes fit together — and that is a claim about generated synthesis
> quality on this product's real material, which has never been tested and can be tested for free.**

**DO NOT IMPLEMENT.**

---

## Housekeeping

Per `CLAUDE.md` kickoff step 8, **this file (`docs/claude-plans/subject-review-cross-note-consolidation-stage2.md`)
requires a row in `ROADMAP.md`'s Backlog Index at the next release kickoff.** **The row is not added here** —
this is the flag, not the action, matching the convention of every sibling planning document in this
directory.

**Backlog Index status of the relevant siblings, checked this pass (not a re-audit of every file):**

- **`cross-note-review-consolidation-stage1.md`** — **this file's own Stage 1 predecessor. Still has NO
  Backlog Index row.** `grep -ni "cross-note\|consolidation-stage1\|subject review" docs/product/ROADMAP.md`
  returns **zero matches**, and it has not been kicked off as a release. Still untracked in `git status`.
  **This confirms, unchanged, what the sibling Artifact-First Stage 2 plan's own Housekeeping section
  recorded earlier today.** When the next kickoff adds a row, the two files are **one line of work** —
  prefer a single row pointing at Stage 1 (the audit) and this file (the tightened architecture) over two
  independent rows, which is the two-rows-one-obligation failure `ROADMAP.md:509` records paying for.
- **`note-visibility-learning-status-stage1.md`** — **has a row**, at `ROADMAP.md:434`, added in the
  `v0.144.0` kickoff docs commit `786f46b9`. It still reads *"Candidate for a future release, not yet
  scoped into one."* Its own Stage 2 (`artifact-first-learning-availability-stage2.md`) notes that this
  row should be **updated to point at that Stage 2 file**, not duplicated.
- **`artifact-first-learning-availability-stage2.md`** — **no row yet**; flagged in its own Housekeeping.
  **⚠️ It is a live dependency of this plan** (§10.1) — Subject Review must not be built before its
  contract ships.
- Two further untracked files remain unindexed and were observed in `git status` only, not examined:
  `docs/claude-plans/2026-09-12-bulk-regeneration-404-terminal-state-fix-plan.md` and
  `docs/claude-findings/2026-09-12-bulk-regeneration-modal-wedged-stale-batch-id.md`. A third,
  `docs/claude-plans/domain-context-biomedical-business-calibration-stage1.md`, was confirmed unindexed by
  the sibling Stage 2 plan earlier today.

**This file is intentionally left untracked and uncommitted**, matching every sibling document in
`docs/claude-plans/`.

**⚠️ Every production number in this document is inherited from Stage 1's 2026-09-13 reads and decays.**
Per `CLAUDE.md`'s snapshot-not-fact rule, re-read before any of them reaches a plan, prompt, kickoff or
release note — in particular the 0-of-729 plan-scoped sessions, the 9 monthly actives, the 58.9%
undefined-concept rate, the 11.4-note mean Subject Plan that §8's arithmetic rests on, and the
4-versus-452 eligibility split. **A Stage 1 sibling fact already decayed within a single day** (Stage 1
§1.7), which is the standing proof that this warning is not ceremonial.
