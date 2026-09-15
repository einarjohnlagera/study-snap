# Cross-Note Review & Learning Consolidation — Stage 1 Product + Repository Audit

**Status: Stage 1 audit only. Nothing implemented, no migration, no behavior change, no commit.**
**Date: 2026-09-13. Repo state: branch `fix/v0.144.0-admin-exam-pool-invalidation`, off `releases/v0.144.0`; last release `v0.143.0` (Released).**
**Production reads: READ-ONLY (`SELECT` only), per `CLAUDE.md`'s production rule. Every production fact is labeled `(Production, 2026-09-13)` and separated from repository facts.**

**Sibling audit consumed as settled input:** `docs/claude-plans/note-visibility-learning-status-stage1.md` (2026-09-13). Its §9 eligibility contract is adopted rather than re-derived; three of its load-bearing citations were spot-verified (§7.1 below).

---

## 0. Method, and what this audit did NOT reach

Every claim about current behavior was verified by opening the file and reading the cited lines. Three parallel tracing subagents were launched and **all three completed** (unlike the sibling audit's run). Their findings were spot-checked against the code directly wherever a conclusion rests on them.

**Three claims this audit nearly published and then falsified against the code — recorded because the corrections are the evidence that the discipline ran:**

1. **"Board Exam has never been used in production" — FALSE, and it would have been an infer-from-a-name error.** `QuickReviewSessionMode` has exactly four values — `QUICK_REVIEW, CHALLENGE, ADAPTIVE, LONG_EXAM` (`backend/src/main/java/com/studysnap/backend/entity/QuickReviewSessionMode.java:3-8`). Board Exam rides `CHALLENGE` with `session_state.mode = "board_exam"` (`QuizSessionHistoryService.java:179-180, 188-193`), exactly as `EXAM_MODES.md:33` says. Re-derived from `session_state`: **8 completed Board Exam sessions by 1 user** *(Production, 2026-09-13)*. Interview Practice likewise rides `ADAPTIVE` + `subMode: "INTERVIEW"` — 3 completed, 2 forfeited, 1 user.
2. **The key-concept match rate was first computed with a looser normalizer than production uses.** `normalizeConceptKey` is `trim().toLowerCase()` only — it does **not** strip punctuation (`frontend/lib/concepts.ts:1-3`), and `isFuzzyConceptMatch` additionally requires the shorter string to be ≥ 4 chars (`frontend/lib/flashcards.ts:14-24`). Re-run with exact semantics: the number was unchanged at 41.1% (1,132 vs 1,134 of 2,756). The number stands; it stands *because it was re-derived*, not because the first pass was trusted.
3. **`source_collection_id` was initially read as "the collection-scoped session column" from its name.** Verified before use: `setSourceCollectionId` has **exactly one caller in the entire backend** — `QuickReviewAdaptivePracticeService.java:944`. That single fact is what makes §1.1's headline measurable at all.
4. **A subagent trace attributed the Board Exam Review-Set resolver to the wrong file, and the line numbers do not exist in the file it named.** It cited `LongExamService.java:1634-1728`; **that file is 1,423 lines long.** The method is `ChallengeQuizService.resolveBoardExamReviewSetSourceNoteRefs:1634`, and the cited line offsets are correct *within that file* — the parent-walk comment at `:1650-1655`, resolution at `:1656-1659`, strata at `:1660-1663`, Study-Pack dedupe at `:1675-1692` — all read directly and confirmed. Every other citation this audit took from that trace and relied on was then re-verified line by line (`LongExamService.java:78, 123, 170-176, 990-991, 1121-1128, 1162-1163`, and `:584/592` for the ConceptHealth writes); all are correct as given. **Recorded rather than silently fixed, because "the agent said so" is not evidence and a wrong file with plausible line numbers is the failure mode that survives review.**

**Not fully traced, and marked as such rather than reported clean:** the Review Set detail page's leaf-vs-Goal render branching (`collection-detail-page-client.tsx:3592, 3961`) — specifically whether `AskCompanionPanel` can render on a child Subject Plan where `AskCompanionService.assertCompanionAvailable` (`:174-179`) would reject it. Flagged in §2 as **[NOT FULLY TRACED]**. No conclusion here depends on it.

---

## 1. Executive judgment — the brief's premises, tested against code and production

### 1.1 The decisive fact: NoteLib already shipped cross-note learning over a Subject Plan, and in production it has been used exactly zero times.

`v0.107.0` shipped plan-scoped Adaptive Practice — the capability `EXAM_MODES.md:89-93` describes as *"Adaptive Practice may be scoped to a whole **Subject Plan or Review Set**, not just one note, via a collection-addressed endpoint."* It is real, it works, and it is anchored by a dedicated column: `QuickReviewSessionEntity.sourceCollectionId` (`:41-42`), written by one and only one call site (`QuickReviewAdaptivePracticeService.java:944`), with the anchor exclusivity enforced both in `@PrePersist` (`QuickReviewSessionEntity.java:123-140`) and by the DB constraint `chk_quick_review_sessions_anchor`.

*(Production, 2026-09-13.)* **Of 729 quiz sessions ever created, `source_collection_id` is NULL on all 729.** Not one learner has ever run a **plan-scoped (collection-anchored)** session.

**⚠️ Read that number with its exact scope, because it is narrower than "no cross-note session has ever run."** `sourceCollectionId` is the *anchor* mechanism and has one writer, so the zero measures **plan-scoped Adaptive Practice only**. The other cross-note capabilities in §1.3 are pack-anchored and record their sources in `session_state.sourceNoteRefs` instead — they do not touch this column, and two of them *have* been used: **8 completed Board Exam sessions (1 user) and 2 completed Long Exam sessions (1 user)** *(Production, 2026-09-13)*. The claim is "the collection-anchored surface has never been used," not "nothing cross-note has ever run."

This is not an analogy to Cross-Note Review. It is **the same product hypothesis — "a learner wants to work across the notes in one Subject Plan rather than one note at a time" — already built, already shipped, and null so far.**

And the repo already knows to ask: `ROADMAP.md:515` carries `[CHECKPOINT — due 2026-10-13] v0.107.0 — does anyone practise across a PLAN, or is remediation a note-level habit?`, re-armed at the `v0.109.0` signoff and deliberately dated from the *announcement* (2026-09-03) rather than the ship date, because a read before learners were told would have measured discoverability rather than demand.

**The honest reading of that checkpoint's current state, which matters more than the zero:** *(Production, 2026-09-13)* since 2026-09-03 there have been **37 sessions from 5 distinct users, of which 2 were Adaptive Practice, of which 0 were plan-scoped.** The denominator is two. **The gating instrument exists and is dated, but at the current rate it will not be readable on 2026-10-13 either.** §20 Q1 asks the owner to decide what happens when a checkpoint's denominator fails to arrive — that is a real decision the repo cannot make.

### 1.2 "A learner may study 5 Notes today, 20 Notes this week" is false in this product's production data by roughly an order of magnitude.

The brief's motivating scenario is its load-bearing premise. *(Production, 2026-09-13, from completed sessions:)*

| Measure | Value |
|---|---|
| Accounts that have ever completed any session | 138 |
| **Median distinct notes a learner has ever studied** | **1** |
| Accounts with ≥ 3 distinct notes ever studied | 16 |
| Accounts with ≥ 5 distinct notes ever studied | **3** (one is the `ADMIN` curator) |
| Accounts with ≥ 7 distinct notes ever studied | **2** (one is `ADMIN`) |
| Best-ever 7-day window, non-admin | 13 distinct notes (one account) |
| **Non-admin accounts that have EVER studied ≥ 5 distinct notes in any 7-day window** | **2** |
| Non-admin accounts that have ever studied ≥ 3 distinct notes in any 7-day window | 15 |

The brief's "5 Notes today, 20 Notes this week" learner exists in production as **one account**, and the population for whom a weekly consolidation ritual would have any material to consolidate is **2 non-admin accounts**, or 15 if the bar is three notes a week.

Scale context, because it governs every measurement recommendation in §16: *(Production, 2026-09-13)* **9 distinct users and 36 completed sessions in the last 30 days**; 120 users and 339 completed sessions in the last 90. 399 accounts total, 22 created in the last 30 days.

### 1.3 "Review is still heavily Note-level or assessment-oriented" — half true, and the half that is false is the half that matters.

**Cross-note is not missing from NoteLib. It has been built three separate times, and a fourth aggregation already runs on the Review Set detail page.**

| Existing cross-note capability | Evidence | Shape |
|---|---|---|
| Plan-scoped Adaptive Practice | `QuickReviewAdaptivePracticeService.java:936-944` (nulls `studyPackId`/`noteId`, sets `sourceCollectionId`) | Collection-**anchored** session |
| Multi-note Long Exam (6/8/10 sources from a plan) | `LongExamService.java:175, 990-991`; cap from `ExamSourceLimitResolver.java:15-17` (`questionCount / 3`) | Pack-anchored + `sourceNoteRefs` manifest |
| Board Exam across a whole Review Set | **`ChallengeQuizService.resolveBoardExamReviewSetSourceNoteRefs:1634`** — resolves a child Subject Plan up to its parent Goal (`:1656-1659`), strata-samples across every Subject Plan, dedupes by Study Pack rather than note id (`:1675-1692`). *(File attribution corrected — see §0 correction 4.)* | Same |
| Teacher Combined Quiz — manual multi-note assembly, **no LLM** | `CombinedQuizService.assemble:56-118`; `MAX_SOURCE_NOTES = 20` (`:42`), `MAX_TOTAL_QUESTIONS = 100` (`:43`) | Immutable snapshot |
| **Cross-note readiness + due concepts per Subject Plan** | `NoteCollectionService.toPlanReadinessResponse:3250-3278`, `summarizeReadinessConcepts:3280-3287`, `loadDueConceptsByStudyPackId:3408-3433` | Already on the wire |

The genuine gap the brief identifies is narrower and real: **every existing cross-note capability is an assessment.** There is no non-assessment cross-note surface. That is a true observation — but §1.1 shows the one non-exam-shaped cross-note capability that did ship has zero uptake, which is evidence about the *scope*, not only about the *mode*.

### 1.4 "Study Packs already contain structured learning material" is materially overstated, and this is the single biggest correction to the brief.

The brief proposes assembling Model A deterministically from "Summary, Key Concepts, existing quiz/practice material." Here is what a Study Pack actually holds (`StudyPackEntity.java:45-63`):

- `title` — String.
- `summary` — **one unstructured prose blob**. `nullable = false` (`:48-49`). *(Production, 2026-09-13: mean 1,641 chars, median 1,721, max 2,878, across 7,591 packs.)*
- `keyConcepts` — **`List<String>`. Bare strings. No definition, no explanation, no per-concept body** (`:57-59`). The generation schema caps them at 2–80 chars each (`backend/src/main/resources/prompts/study-pack-v1/schema.json:27-36`). *(Production: mean 9.12 per pack, range 5–10, zero packs empty.)*
- `quiz` — `List<QuizItem>`, and the schema pins it at **exactly 5** (`schema.json:38-40`, `minItems:5, maxItems:5`). *(Production: min 5, max 5, mean 5.00 across all 7,591 packs — the schema holds perfectly.)*

There is **no flashcard column, no memorization column, no per-concept explanation field** anywhere on `StudyPackEntity`.

**The consequence is decisive for Model A.** The only per-concept explanatory text in the product is `QuizItem.explanation`, reachable from a key concept only through the frontend's fuzzy match (`frontend/lib/flashcards.ts:29-51`). With 5 quiz items against ~9 concepts, most concepts cannot have one.

> ***(Production, 2026-09-13, 300-pack sample, 2,756 concepts, computed with the exact production matcher — `trim().toLowerCase()`, equality or ≥4-char bidirectional substring):* only **41.1%** of key concepts have any explanatory text anywhere in their Study Pack. **58.9% are bare noun phrases with no definition in the product at all.***

So a deterministic "Key Concepts" section across 7 notes is, concretely, **a list of ~64 bare noun phrases of which ~38 have no definition**, under ~11,500 characters of concatenated prose summary. That is not a consolidation experience. It is the raw material *before* one.

### 1.5 The brief forbids inventing spaced repetition. It is already built, already approved in a locked canonical doc, and already in production.

The brief says: *"Do not add: nextReviewAt, SM-2, FSRS, review intervals… unless you discover an already-approved architecture in current canonical docs."* **That exception fires.**

`EXAM_MODES.md:151` locks Memorization as *"a simplified SM-2 (Anki-family) — 4-button self-grade (Again/Hard/Good/Easy)… stored in a **new, separate `memorization_cards` entity** — never a `ConceptHealth` column, never joined or read by `ProgressReportService` or any readiness/`Overall Readiness` calculation (firewalled by design, not just convention)."* The code matches: `MemorizationCardEntity.java:17-62` carries `intervalDays`, `easeFactor`, `repetitions`, `dueAt`, `lastReviewedAt`, `lastGrade`, keyed `(userId, studyPackId, concept)`, with the class comment at `:18` stating the firewall in the entity itself.

**This is also the closest existing precedent for the surface class Cross-Note Review belongs to — review-only, non-engine, no mastery — and its adoption is the most relevant prior in this audit.** *(Production, 2026-09-13:* `memorization_cards` holds **7 rows across 3 users**, first 2026-07-05, last review 2026-09-08 — roughly ten weeks at 3 users.*)* Flashcards has no persistence and no analytics event at all: the only flashcard-shaped event in the 135-value enum is `PUBLIC_NOTE_FLASHCARDS_CLICKED` (`AnalyticsEventType.java:78`), a public landing-page CTA, so in-app flashcard usage is **not measurable today by any means**.

### 1.6 Export: there is no PDF exporter in this product, and the plan UI is already advertising one.

The brief treats "Export Reviewer (PDF, DOCX)" as reusing existing infrastructure. Verified:

- **No PDF writer exists anywhere.** `pdfbox` is a dependency (`backend/pom.xml:76-84`) but is used only to *read* uploaded PDFs for OCR (`NoteTextExtractionService.java:11-13, 117, 126, 143`). No `jsPDF`, `html2pdf`, `html2canvas`, `window.print`, or `@media print` anywhere in `frontend/` either.
- **DOCX exists but is not a learner feature.** `QuizDocxExportService` (Apache POI, `:9-16`) exports *quizzes*, single-note (`:74-113`) and multi-note combined (`:115-152`), and `GeneratedQuizService.requireTeacherExportUser:597-604` throws for any account that is not `ADMIN` or `TEACHER`. *(Production, 2026-09-13: one TEACHER account exists, and `combined_quizzes` holds **0 rows**.)*
- **⚠️ A live finding, independent of this feature: the Settings page advertises a PDF export allowance for a capability that does not exist.** `frontend/app/settings/page.tsx:1094-1100` renders an `ExportUsageMetric` labeled **"PDF Exports"** with real per-plan limits (Free 2/mo, Plus 15/mo, Pro unlimited — `frontend/lib/pricing-config.ts:15,24`; backend `StudySnapProperties.java:273-280`). The backend meter is fully wired — `ExportUsageProtectionService.assertPdfQuotaAvailable:24-27` and `recordPdfUsage:33-35` — and **has zero callers anywhere in `backend/src/main/java`** (verified by exhaustive grep; the only other reference is `MePlanService.java:59`, which reads the count for display). Every user sees a meter for a feature with no producer. **Reported as found, not proposed as a build item.**

### 1.7 Corrections to repo docs found while auditing

- **`CLAUDE.md`'s Feature-gating section is stale.** It states the `Feature` enum values are *"`ADAPTIVE_QUIZ`, `LONG_EXAM_SESSION`, `INTERVIEW_PRACTICE`, `WEAK_CONCEPT_DETECTION`"* — four values. The enum has **five**: `ASK_COMPANION` is present at `backend/src/main/java/com/studysnap/backend/entity/Feature.java:16-18`. Not fixed here (this audit changes no file but its own); flagged for the next doc pass.
- **A sibling-audit production fact has already decayed in one day, exactly as `CLAUDE.md` warns.** That audit recorded *"zero notes are currently `GENERATING`, so this class has no live instance"* (2026-09-13). *(Production, 2026-09-13, later the same day:* **1 note is `GENERATING` and it has an intact `DONE` Study Pack** — a live instance of its own D2 third variant.*)* Cited here as a dated snapshot, not as a correction to that audit's reasoning, which is unaffected.

---

## 2. Current learning architecture, and where consolidation is actually missing

```
Note (NoteEntity)  ──generate──▶  Study Pack  ── summary (prose) + keyConcepts (List<String>) + quiz (exactly 5 QuizItems)
   │                                   │
   │                                   ├──▶ Flashcards        frontend-only, buildFlashcardDeck(lib/flashcards.ts:29-51), no persistence, no events
   │                                   ├──▶ Memorization      memorization_cards, SM-2, firewalled from readiness (MemorizationCardEntity.java:18)
   │                                   └──▶ Quiz Session Engine — one table, quick_review_sessions
   │                                             QUICK_REVIEW / CHALLENGE (+board_exam) / ADAPTIVE (+INTERVIEW) / LONG_EXAM
   │                                                   │
   │                                                   └──▶ ConceptHealth  (userId, studyPackId, concept) — five writers only
   │
   └── NoteCollectionItemEntity ──▶ NoteCollectionEntity (self-referential: parentCollectionId + siblingPosition)
              Goal / Review Set  ──parent of──▶  Subject Plan  ──contains──▶ notes
                       │
                       ├──▶ PlanReadinessResponse: totalConcepts / masteredConcepts / dueConcepts / notPracticedConcepts, per subject
                       ├──▶ NoteCollectionItemResponse: lastSessionCompletedAt, dueConceptCount, dueConcepts[3]
                       ├──▶ Companion (static, admin-authored) + Ask Companion (LLM chat, 6 turns, top-level only)
                       └──▶ Coach / Today's Focus (DashboardService) — every rule single-pack
```

**Structural fact that governs every scope choice below: "Review Set", "Study Plan", "Lesson Plan", "Goal", "Subject Plan" and "Unit" are not distinct entities.** They are `ProfileType`-keyed display labels over one self-referential table (`frontend/lib/collection-labels.ts:45-89`; `NoteCollectionEntity.java:88-92`). A top-level row (`parentCollectionId == null`) is a Goal / Review Set; its children are Subject Plans. `ChallengeQuizService.java:1650-1663` states the rule explicitly in a comment and resolves a child claim up to its parent for Board Exam — *"Board Exam's identity is the WHOLE Review Set, so a child claim resolves to its parent"* (read directly, `:1650-1655`).

**Where consolidation is genuinely missing:** there is no surface that presents *material* (as opposed to *questions*) from several notes at once. Every cross-note capability in §1.3 produces a quiz. The Review Set detail page aggregates *numbers* across notes (readiness, due counts) but never *content*.

**And the repo has already ruled out the hardest part of doing it well, in a code comment, with the reason named.** `DashboardService.java:471-473`:

```java
// Within-pack only. `concept` is free text keyed per Study Pack with no canonical identity,
// so the same idea in two packs cannot be related — counts must never be summed across packs.
// Cross-pack recommendation needs concept identity first and is deliberately out of scope.
```

`ROADMAP.md:503` records the sizing read that tested whether to lift that constraint (owner-executed 2026-09-04): **6 user-concept pairs across 5 users — 0.4% — have real assessed evidence for one concept in two packs**, against **10,361 distinct authored concepts**, which killed the curated-catalog analogy `ADR-001` rested on (41 rows). The row's own verdict is *"the problem is real and merely EARLY."*

**This is the ceiling on Cross-Note Review's "Key Concepts" section.** Concepts cannot be deduplicated, related, or merged across notes today. Any cross-note concept list must remain *grouped by source note* — which is exactly what the brief's §"Review interaction design" already proposes, and it is right for a reason it does not state.

---

## 3. Existing reusable infrastructure

| Need | What already exists | Evidence |
|---|---|---|
| **Source selection (plan-scoped, authorized)** | `PlanSourcedExamVerifier` — the one shared primitive turning a caller-supplied `sourceCollectionId` into a verified member-note set, re-checking ownership every call. Its javadoc says it exists *because two independent copies of this rule drifted* | `PlanSourcedExamVerifier.java:18-29, 44-79` |
| **Source selection (UI)** | `resolveCollectionScopedSourceNotes` picks candidate notes from a collection, excluding the primary | `frontend/lib/collection-exam.ts:38-54` |
| **Source-count caps** | `resolveMaxSourceNotes(questionCount) = questionCount / 3`; Long Exam 6/8/10 by learner level; manual additional sources capped at 3 | `ExamSourceLimitResolver.java:15-17`; `StudySnapProperties.java:157-159`; `LongExamService.java:123` |
| **Balanced sampling across sources** | `LongExamPlanSourceSampler.sample` — bucket round-robin over Section or Subject Plan label, explicitly never size-weighted | `LongExamPlanSourceSampler.java:17-19, 22-87` |
| **Key-concept aggregation across notes** | `ProgressReportService.buildSubjectProgressEntriesByGroup` — batched multi-scope grouping with one concept-health fetch for the whole batch | `ProgressReportService.java:117-185, 150-151` |
| **Cross-note due concepts, per Subject Plan, already on the wire** | `loadDueConceptsByStudyPackId` + `toItemResponse`; ships `dueConceptCount` + top-3 `dueConcepts` and `lastSessionCompletedAt` per item | `NoteCollectionService.java:3408-3433, 3448-3481`; `NoteCollectionItemResponse.java:19-21` |
| **Plan-level readiness totals** | `toPlanReadinessResponse` / `summarizeReadinessConcepts` | `NoteCollectionService.java:3250-3287` |
| **Recall mechanic with no mastery write** | Memorization SM-2 self-grade; `recordGradeForOwnedStudyPack` saves one row and nothing else — no `ConceptHealthService`, no `ActivityTrackingService`, no `AnalyticsService` import in the class | `MemorizationCardService.java:54-70, 103-142` |
| **Deterministic multi-note assembly precedent (no LLM)** | `CombinedQuizService.assemble` — up to 20 source notes, immutable snapshot, per-item `withSourceStudyPackId`, note title as section header | `CombinedQuizService.java:36, 42-43, 56-118, 158-180` |
| **Per-item provenance** | `QuizItem.sourceStudyPackId` (`:31`, `withSourceStudyPackId` `:400-402`), stamped on every multi-source generation path | `LongExamService.java:1162-1163`; `ChallengeQuizService.java:1840-1849` |
| **Source manifest per session** | `session_state.sourceNoteRefs`: `{studyPackId, noteId, noteTitle, questionCount}` | `LongExamSourceNoteRef.java:3-8`; `LongExamService.java:1121-1128, 78` |
| **Collection-anchored session (no single note)** | `sourceCollectionId` + exclusive-or anchor validation in `@PrePersist` and a DB check constraint | `QuickReviewSessionEntity.java:41-42, 113-117, 123-140` |
| **Plan-scoped progress page** | `/progress?collectionId=` is live and wired from the Review Set footer link | `frontend/app/progress/page.tsx:8,12`; `progress-report-client.tsx:440, 750-753`; `collection-detail-page-client.tsx:1198` |
| **Analytics funnel** | `countDistinctUsersByEventType` and `countDistinctUsersWithEventAfterEvent` — generic JPQL, no new repository code needed | `AnalyticsEventRepository.java:194-199, 229-261`; used `AdminFunnelService.java:359-365` |
| **Async generation dispatch** | `StudyPackGenerationTaskDispatcher` (shared by 4 services); `llmParallelTaskExecutor` for per-source fan-out; `dispatchAfterCommit` pattern | `AppConfig.java:101-110, 145-153`; `StudyPackService.java:766-778` |
| **Quota pattern** | One `UserUsageEntity` column + one `resolveMonthlyXLimit(PlanType)` + one `incrementX` + one thin protection service | `UserUsageEntity.java:38-75`; `ExportUsageProtectionService.java` |

**What does NOT exist and would have to be built:** a PDF writer (§1.6); a single LLM call that takes multiple notes (every `LlmStudyPackService` method takes one title/summary/keyConcepts triple — multi-source generation is N calls merged app-side, `ChallengeQuizService.java:1806-1838`); any cross-pack concept identity (§2); any non-assessment cross-note surface.

---

## 4. Product overlap analysis

| Existing | What it does | Genuinely distinct from Cross-Note Review? |
|---|---|---|
| **Quick Review** | 5 static Study Pack questions, one note, **writes ConceptHealth** (`QuickReviewSessionService.java:304, 308-313`) | Distinct in scope (one note) — but **the overlap risk is severe**: a multi-note "recall" section built from the same `quiz` array, with no mastery write, is Quick Review with the scoring removed. This is the brief's own "Review Session = Quick Review" trap, and the material is literally identical. |
| **Flashcards** | Frontend-only, `keyConcepts` × fuzzy-matched `explanation`, one note, no persistence, no events (`flashcards.ts:29-51`) | **Barely.** Cross-Note Review's "Key Concepts + Recall" over N notes is Flashcards over N notes. The delta is the source scope and nothing else. |
| **Memorization** | Same matched concepts, minus unmatched; SM-2 schedule; one note; firewalled from readiness | **Barely**, and it is the direct precedent — 3 users in 10 weeks. |
| **Challenge Quiz** | Progressive 5→20, multi-note capable from a verified plan (Free 3 / Plus 6 sources), writes ConceptHealth | Distinct — assessment. Its multi-note capability is the closest *assessment* analogue to the proposed source picker. |
| **Adaptive Practice** | Weak-concept remediation, **already plan-scoped since `v0.107.0`**, collection-anchored | **This is the overlap that decides the verdict.** It already answers "work across the notes in this Subject Plan." Zero production uses. |
| **Long Exam / Board Exam** | Multi-note and whole-Review-Set assessment respectively, with real provenance | Distinct — assessment, and the contract forbids a sixth mode. |
| **Companion** | Static admin-authored guidance on top-level collections; Ask Companion is a 6-turn LLM chat **grounded on the Companion content, not on note text** (`AskCompanionService.java:147, 174-179`) | Distinct — curated, timeless, top-level only, and not derived from the learner's own notes. |
| **Coach / Today's Focus** | 5-tier `ContinueStudying` waterfall + 4-tier `TodayFocus` waterfall, **every rule single-pack by explicit design** (`DashboardService.java:471-473`) | Distinct — routing, not review. The dashboard hero does aggregate across a Goal's children (`dashboard-primary-collection-hero.tsx:17-51`), but to pick *one* next step. |
| **Review Set detail** | Already aggregates readiness + due concepts across the plan's notes | **This is the nearest thing that exists**, and it aggregates numbers rather than content. |

**Verdict on distinctness:** Cross-Note Review is *conceptually* distinct from all nine — no existing surface presents study *material* from several notes. But it is distinct from each of them by **one axis only** (scope, for Flashcards/Memorization/Quick Review; content-vs-questions, for the exam modes). A feature whose novelty is one axis away from four existing surfaces, three of which have near-zero adoption, is a weak case on its own merits and must be carried by demand evidence. That evidence is absent (§1.1, §1.2).

---

## 5. Model comparison

| | **A — ephemeral deterministic** | **B — persisted generated Review Pack** | **C — hybrid** | **D — extend what exists** |
|---|---|---|---|---|
| **Learning value** | **Low, and §1.4 is why.** The assembled artifact is N prose summaries + ~9N bare concept strings (58.9% undefined) + 5N existing questions. Concatenation is not consolidation. | Potentially real — comparison/relationship/misconception content is the one thing genuinely absent from the data model | Real, concentrated in the synthesized part; the deterministic part is A and carries A's weakness | Modest but immediate; surfaces aggregation already computed |
| **Complexity** | Low — one read endpoint, one page | High — new entity, generation lifecycle, staleness, regeneration, copy semantics, privacy, export | High — B's complexity plus A's | **Lowest** — additive DTO/UI work |
| **Cost** | Zero LLM | Real, and **it is N calls, not one** — no multi-note LLM method exists (`LlmStudyPackService`); an 11-note plan is 11 calls or a bespoke new prompt | Same as B for the synthesized part | Zero |
| **Provenance** | Trivial — nothing is generated | **Achievable**: `QuizItem.sourceStudyPackId` + `sourceNoteRefs` is the shipped pattern. **But the orphan gap is real** — `NoteService.deleteById:495-503` deletes a note and its pack with no check for use as a secondary source, and the JSONB source ids carry no FK (`V4__quick_review_sessions.sql:4` constrains the *primary* pack only) | Same as B | N/A |
| **Freshness** | Always current | Stale the moment any source regenerates — and `v0.143.0` had to ship exam-pool invalidation for exactly this class | Stale in the synthesized part only | Always current |
| **UX / mobile** | **Poor.** ~11,500 chars of prose for 7 notes; 452 of 453 non-admin Subject Plans would exceed that under a pack-based rule (mean 11.4 notes ⇒ ~18,700 chars) | Bounded by the generator | Bounded | Fits the existing card |
| **Persistence** | None | New entity beside Note and Study Pack — the thing the brief says to default against | New entity | None |
| **Export** | Would need a PDF writer built from scratch (§1.6) | Same | Same | N/A |
| **Notes-first fit** | Good | **Weakest** — creates the "parallel review document library" the brief prohibits | Medium | **Best** |

**A is cheap and thin.** **B is the only model with real incremental learning value, and it is the most expensive, the most provenance-fragile, and the one the brief and the architecture both default against.** **C inherits A's thin half.** **D is real but is not this feature.**

Rejected outright: **B as a v1**. Not because generation is never justified, but because there is no evidence anyone wants the cheap version, and B's entire justification is "the cheap version is not valuable enough" — which is an argument for *not building either* until someone asks.

---

## 6. Recommended product model

**DEFER the feature. Build nothing called Cross-Note Review in the next release.**

This is a verdict, not a hedge, because the deciding instrument already exists and is dated: `ROADMAP.md:515`'s `[CHECKPOINT — due 2026-10-13]` on `v0.107.0`. NoteLib has already shipped the cross-note-over-a-Subject-Plan hypothesis and is already scheduled to read whether anyone uses it.

**If and when it is built, the model is D → C, in that order, and never B alone:**

> **Learner-facing mental model, one sentence:** *"Bring the topics you've been studying in this Subject Plan back together before you test yourself."*

- **First (D):** surface the cross-note aggregation the Review Set detail already computes — `dueConceptCount`, `dueConcepts`, `lastSessionCompletedAt` per item, plus plan readiness totals — as a coherent "what you've covered / what's due" view rather than scattered per-row metadata. **Say plainly: this is not Cross-Note Review.** It is making an existing computation legible, and it is the cheapest way to learn whether learners engage with plan-level material at all.
- **Only then (C):** a deterministic review over a Subject Plan with one optional synthesized "Compare & Connect" section — and only if D shows engagement *and* the `v0.107.0` checkpoint reads nonzero.

---

## 7. Recommended v1 source scope (if built)

**Subject Plan (a child `NoteCollectionEntity`), and nothing else.** Not selected Notes, not the whole Review Set, not Coach-resolved.

Why, with numbers:

- **Selected Notes is the wrong v1 because the selection has nothing to select from.** Median distinct notes studied per learner is 1 (§1.2). A free-selection picker whose realistic candidate set is one item is not a feature.
- **Whole Review Set is too broad.** *(Production, 2026-09-13:* non-admin Subject Plans average 11.4 pack-bearing notes, max 77; a Goal spans several.*)* At 1,641 chars of summary per note, a Review Set-scoped review is tens of thousands of characters.
- **Subject Plan is the only scope with a shared, already-authorized resolver** (`PlanSourcedExamVerifier`), an existing sampler (`LongExamPlanSourceSampler`), and an existing cross-note aggregation on the same page.
- **Coach-resolved is foreclosed today** by `DashboardService.java:471-473` and the 0.4% sizing read (§2).

**Do not build separate implementations per scope.** `PlanSourcedExamVerifier.java:18-29` exists precisely because that rule was duplicated once and drifted; any Cross-Note Review source resolution goes through it.

### 7.1 The scope number the owner should see before approving any of this

*(Production, 2026-09-13.)* The v1 population depends entirely on which eligibility definition is chosen, and the two differ by two orders of magnitude:

| Eligibility rule | Non-admin Subject Plans qualifying with ≥3 notes | Owners |
|---|---|---|
| **"Notes with reviewable Study Pack material"** (artifact presence) | **452 of 453** | 88 |
| **"Notes the learner has studied"** (completed session) | **4 of 453** | **4** |

Under the honest "studied" definition the feature has a four-user addressable population. Under the artifact definition it has 88 — but then it is no longer *consolidation of what you learned*; it is a reader over material the learner has mostly never opened, which is precisely the "multi-note PDF generator" identity the brief prohibits.

**That contrast is the §7 answer, and it is the single most important number in this audit after §1.1.**

---

## 8. Eligibility contract

**Adopted verbatim from `docs/claude-plans/note-visibility-learning-status-stage1.md` §9, not re-derived:**

> A Cross-Note Review session may include an owned Note **if and only if the artifacts that session consumes exist on that Note's current Study Pack — evaluated from the artifact, never from `NoteStatus`.** A Note whose last generation attempt failed but whose Study Pack is intact is eligible. A Note with no Study Pack is ineligible, and the surface says which artifact is missing and offers generation.

Consumed as `hasKeyConcepts` for concept review and `hasQuizQuestions` for question-based recall. Entitlement resolved separately via `FeatureGateService`.

**Spot-verification of that audit's three most load-bearing citations — all three hold exactly:**

1. `NoteStudyPackStatusResolver.resolve(NoteStatus, boolean)` — read in full. `GENERATED → STUDY_PACK_READY` (`:21-23`), `GENERATING → GENERATING` (`:24-26`), `FAILED → FAILED` (`:27-29`) all return without consulting `hasStudyPack`; only the `DRAFT` fall-through checks it (`:30-33`). **Confirmed.**
2. `NoteCollectionItemResponse` — read in full (24 lines). It carries `studyPackStatus`, `studyPackId`, `generatedQuizId` but **no `quizCount` and no `keyConceptCount`**. **Confirmed.** *(Addition this audit makes: it does carry `lastSessionCompletedAt`, `dueConceptCount` and `dueConcepts` at `:19-21` — a "studied" signal and a due-concept signal the sibling audit did not note, and directly reusable here.)*
3. `NoteListItemResponse` — read in full. `quizCount` at `:20`, `keyConceptCount` at `:21`. **Confirmed.**

**Dependency on that audit's open Q1 ("should a failed regeneration keep serving its previous Study Pack?"), sized as the brief asks:**

*(Production, 2026-09-13.)* **0 notes are currently `FAILED`; 7 notes have ever recorded a generation failure; 1 note is currently `GENERATING` with an intact `DONE` pack.** Of 7,591 notes with a `DONE` pack, **a "no" on Q1 removes 1 note today — about 0.01% of the eligible pool.**

**Therefore: Q1 is a correctness dependency for Cross-Note Review, not a scope dependency.** It determines whether the eligibility rule is *honest*; it does not meaningfully change how much material a v1 would have. It should be answered before this feature consumes the contract, but it is not a blocker on scope sizing, and nothing in §6's verdict turns on it.

---

## 9. Review experience (if built)

Smallest coherent structure, with the **Compare & Connect section omitted from v1** because §10 concludes no LLM call is justified:

| Section | Content | Interactive? |
|---|---|---|
| **Overview** | One line per source note: title + the *first sentence* of its summary — **never the full 1,641-char blob**, per §1.4 and the mobile constraint | Read-only; tap to expand one at a time (progressive disclosure) |
| **Key Concepts** | **Grouped by source note, never merged** — cross-pack concept identity does not exist (§2, `DashboardService.java:471-473`, 0.4% sizing read). Concepts with no matched explanation (58.9%) must render honestly as a term without a definition, the way `flashcards-page-client.tsx:171-182` already does | Read-only |
| **Recall** | Reuse `buildMatchedFlashcards` (`lib/flashcards.ts:53-55`) across the selected notes — matched concepts only, exactly as Memorization already restricts itself | Flip / self-review only |
| **Sources** | Link back to each source Note by **durable `noteId`**, never by title | Navigation |

**What must stay read-only, and the precise standing of the rule — stated carefully, because it is easy to overstate.** `EXAM_MODES.md:153` reads: *"Constraint: neither surface may be routed through `QuickReviewSessionEntity` or any other engine discriminator. If a future requirement needs scoring, a timer, or persistence beyond spaced-repetition scheduling, it no longer fits this classification and must go through the 5-mode contract review instead."*

**"Neither surface" is Flashcards and Memorization — the two rows of that table. It is not, as written, a blanket prohibition on any future review-only surface.** The generalizable half is the second sentence, and it says **escalate to contract review**, not *forbidden*. So the honest statement is: a Cross-Note Review that wanted a session row would owe an `EXAM_MODES.md` amendment and a 5-mode contract review **first** — it is a gate, not a wall. The recommendation below does not depend on which it is, because a read-only surface needs no session row at all.

**Known / Still learning marking: NO for v1.** Not because it implies mastery — Memorization proves a self-grade can be firewalled (`MemorizationCardEntity.java:18`) — but because persisting it requires either a new table or a write into `memorization_cards`, and the second would silently change the SM-2 schedule of a *different* feature. Neither is justified before demand exists.

**Completion: no pedagogical meaning here, and it should not pretend to have one.** Record it as an analytics event only (§16). No activity type, no streak contribution — `ActivityType` has exactly 7 values (`ActivityType.java:6-13`) and `MEANINGFUL_STUDY_ACTIVITIES` (`:15-22`) feeds the study streak via `ActivityTrackingEventListener.updateStudyStreak:82-113`. Adding a review-completion activity type would inflate streaks for a non-assessment surface.

**Resume: NO.** See §10.

---

## 10. Persistence decision

> **Does v1 require a new persisted Review Session / Review Pack entity? — NO.**

And: **it should not reuse `QuickReviewSessionEntity` either**, even though that table can technically represent a collection-anchored session (`:41-42`, `:123-140`). Doing so would give a review-only surface an engine discriminator — the brief's "accidental sixth quiz mode" failure mode — and under `EXAM_MODES.md:153`'s escalation clause it would owe a 5-mode contract review and a doc amendment before it could ship (see §9 for the precise standing of that rule; it is a gate, not an outright prohibition). The session table is available, reachable, and the wrong tool.

**How the experience operates without persistence:** one authenticated read endpoint takes a `collectionId`, resolves members through `PlanSourcedExamVerifier`, batch-loads their Study Packs, and returns titles, summary first-sentences, concepts grouped by note, matched flashcards, and note ids. Nothing is written. Every render reflects current Study Packs, so the staleness class that forced `v0.143.0`'s exam-pool invalidation cannot arise.

**Resume/history implications, stated rather than waved away:** with no persistence there is no resume and no history row. That is acceptable *because* the review is read-only navigation over live data — reopening the surface reconstructs it identically, and the only lost state is scroll position and which cards were flipped, which `localStorage` can hold per-viewer if it ever matters. A history entry would additionally be misleading: Recent Sessions is built from `quick_review_sessions` and a review with no score in that list reads as an assessment the learner did badly at.

---

## 11. Generation decision

> **Does v1 need an LLM call? — NO.**

Existing artifacts are sufficient *for the deterministic experience described in §9* — title, summary, `keyConcepts`, `quiz`, and the existing fuzzy matcher are all already loaded by the note-detail read path, and `CombinedQuizService` (`:56-118`) is the in-repo precedent for deterministic multi-note assembly with no LLM at all.

**But the honest statement, which §5 requires and which must not be softened:** the deterministic experience is *thin* (§1.4). The only genuinely new learning value in the brief's proposal — comparison, relationship, misconception synthesis — is precisely the part that requires generation. **So the recommendation is not "deterministic is enough." It is "deterministic is cheap enough to test demand with, and generation is not justified before demand is demonstrated."**

Recorded for a later slice, should it ever be scoped: synthesis would need N single-source LLM calls merged app-side or a new multi-source prompt (no `LlmStudyPackService` method accepts multiple notes); it would reuse `llmParallelTaskExecutor` (`AppConfig.java:145-153`) and `dispatchAfterCommit` (`StudyPackService.java:766-778`); provenance would follow the shipped `sourceStudyPackId` + `sourceNoteRefs` pattern; and **it would have to solve the orphan gap first** — `NoteService.deleteById:495-503` deletes a note and its pack without checking whether it is referenced as a secondary source in existing multi-note session JSONB, which carries no FK. **Per the brief's own rule, no generated-synthesis architecture should be recommended while provenance cannot be made trustworthy — and today it cannot.**

---

## 12. Progress / mastery contract

**May write:** analytics events only — and specifically **only the three enumerated in §16** (`CROSS_NOTE_REVIEW_STARTED`, `_COMPLETED`, `_SOURCE_OPENED`). This clause and §16's list are one decision written twice; if either changes, change both. Splitting a permission from its enumeration across two sections is the per-PR drift pattern `CLAUDE.md` warns about.

**Must not write:** `ConceptHealth` (any of the five writers — `QuickReviewSessionService.java:304,308`; `ChallengeQuizService.java:1018,1037`; `QuickReviewAdaptivePracticeService.java:824,850,853`; `LongExamService.java:584,592`; `InterviewPracticeService.java:307,315`); `UserActivityEventEntity` / any `ActivityType`, because that feeds the study streak (`ActivityTrackingEventListener.java:82-113`); `memorization_cards`, because that would alter another feature's SM-2 schedule; `quick_review_sessions`, per §10.

**No new mastery signal of any kind.** Displaying material is not evidence of retrieval, and self-grading in a non-assessment surface is not evidence of mastery — the repo already encodes this in `MemorizationCardEntity.java:18` and `EXAM_MODES.md:143`.

---

## 13. Assessment handoff

| Source scope | Truthful CTA | Why |
|---|---|---|
| **Subject Plan** | **"Practice weak areas across this plan"** (plan-scoped Adaptive Practice) and **"Take the Long Exam"** | Both already accept a `sourceCollectionId` over the same member set (`QuickReviewAdaptivePracticeService.java:944`; `LongExamService.java:170-175`). Same sources in, same sources out. |
| **Subject Plan, Pro + Board Taker** | "Take the Board Exam" | Truthful only with a caveat: Board Exam resolves a child Subject Plan **up to its parent Goal** and samples the whole Review Set (`ChallengeQuizService.java:1656-1663`, read directly). Its source set is *broader* than the review's. If offered, the label must say so. |
| **Whole Review Set** | Board Exam only | Long Exam and Adaptive Practice are plan-addressed, not Goal-addressed. |
| **Selected Notes (not recommended for v1)** | **None that is truthful.** Multi-note Challenge Quiz requires a *verified plan* membership predicate, not an arbitrary selection (`EXAM_MODES.md:78`) | **Say so rather than invent one** — this is the brief's own instruction, and it is one more reason §7 rejects free selection. |

No new exam mode, no new sub-mode, no new discriminator.

---

## 14. Export Reviewer

**DEFER, and correct the brief's premise: there is nothing to reuse.** §1.6 — no PDF writer exists in this repo; the DOCX writer is quiz-shaped and `ADMIN`/`TEACHER`-only (`GeneratedQuizService.java:597-604`).

If export is ever built:
- **Reuse the PDF quota that already exists** — `pdfExportsCount` (`UserUsageEntity.java:72`), `assertPdfQuotaAvailable` / `recordPdfUsage` (`ExportUsageProtectionService.java:24-35`), limits at `StudySnapProperties.java:273-280`. **Do not invent a second export quota**; this one is fully wired, already advertised to users, and currently has zero callers.
- A deterministic review can be exported without persistence — the export request re-resolves the same `collectionId`.
- Source attribution must appear, by `noteId`, never by title.
- **Private source content is not a new concern** at v1: the review is owner-scoped and `PlanSourcedExamVerifier:73` re-checks ownership on every call.

**Independently of this feature: the "PDF Exports" meter at `frontend/app/settings/page.tsx:1094-1100` advertises an allowance no code path can consume.** Reported as found. It deserves its own decision — build the exporter, hide the meter, or label it as coming — but that decision is not Cross-Note Review's to make.

---

## 15. Coach integration

**DEFER — and this is the least ambiguous recommendation in the audit, because the code has already decided it.**

`DashboardService.java:471-473` states that cross-pack recommendation is deliberately out of scope until canonical concept identity exists, and `ROADMAP.md:503` records the owner-executed sizing read that tested exactly that and returned *not yet* (0.4%, 6 user-concept pairs across 5 users; 10,361 distinct authored concepts, which killed the curated-catalog approach `ADR-001` used for 41 programs).

A Coach rule saying *"You've studied 7 topics in Structural Theory — review them together"* would need to count studied notes within a subject scope. That much is deterministically available (`lastSessionCompletedAt` per collection item, `NoteCollectionItemResponse:19`). But *(Production, 2026-09-13)* it would fire for **4 non-admin accounts at a 3-note threshold, and 0 at 7**. A recommendation rule that can fire for four users is not worth a tier in a waterfall that every dashboard load executes.

No spaced repetition, no adaptive scheduling, no LLM scheduling. (Note that SM-2 scheduling already exists in `memorization_cards` — §1.5 — so any future work here must extend the approved one rather than add a second.)

---

## 16. Analytics

Minimum funnel to falsify the hypothesis — **3 event types, no new repository code**, using `countDistinctUsersByEventType` and `countDistinctUsersWithEventAfterEvent` (`AnalyticsEventRepository.java:194-199, 229-261`):

1. `CROSS_NOTE_REVIEW_STARTED` — with `collectionId` and source count.
2. `CROSS_NOTE_REVIEW_COMPLETED` — reached the Sources section.
3. `CROSS_NOTE_REVIEW_SOURCE_OPENED` — jumped back to a source Note.

Assessment follow-through is derivable from existing events via `countDistinctUsersWithEventAfterEvent(CROSS_NOTE_REVIEW_COMPLETED, CHALLENGE_QUIZ_STARTED | LONG_EXAM_STARTED)` — no new event needed.

⚠️ **Add to both files by hand.** `AnalyticsEventType.java` (135 constants) and the frontend string-literal union at `frontend/lib/api.ts:599-729` are not generated from one another and have already drifted — `BOARD_EXAM_COMPLETED` exists backend (`:54`) and is missing from the frontend union.

⚠️ **And state the measurement problem honestly rather than shipping a funnel that cannot answer anything.** *(Production, 2026-09-13: 9 distinct users and 36 completed sessions in the last 30 days.)* A three-step funnel over a population of 9 monthly actives cannot reach significance in any usable window. **This is an argument for §6's defer, not for a bigger event taxonomy.**

---

## 17. Implementation shape (architecture only — nothing here is scoped)

Were the D-then-C path taken:

**Phase D (surfacing, not a feature).** Frontend-only. Reorganize the Review Set / Subject Plan detail page to present `dueConceptCount` / `dueConcepts` / `lastSessionCompletedAt` (already in `NoteCollectionItemResponse:19-21`) and the plan readiness totals as a coherent block. **No migration. No new endpoint. No backend change.** Small.

**Phase C (only on evidence).** One `GET` endpoint taking `collectionId`, resolving through `PlanSourcedExamVerifier`, batch-loading packs, returning a read-only payload. One new frontend route. **No migration, no new entity, no new quota, no LLM.** Medium — and it owes one `MockMvc` test with `.contentType(MediaType.APPLICATION_JSON)` per `CLAUDE.md`'s real-request rule, plus a `lib/api-*.test.ts` pinning request shape; a component test that mocks `lib/api` proves nothing about the seam.

**Routing per `CLAUDE.md`:** Phase D is Claude Code inline. Phase C is a new backend endpoint and is **Codex**.

---

## 18. Risks / failure modes

| Risk | Live likelihood here | Evidence |
|---|---|---|
| **Becoming another PDF generator** | **High if export is in v1.** With §1.4's thin material, "export a reviewer" is the most tangible thing the feature could do, and it would become the identity | §1.4, §1.6 |
| **Duplicating Quick Review** | **High.** A multi-note recall section built from the same `quiz` array with scoring removed *is* Quick Review minus the score | §4 |
| **Duplicating Flashcards / Memorization** | **High.** The recall mechanic is literally `buildMatchedFlashcards` over N notes | `lib/flashcards.ts:53-55` |
| **Accidental sixth quiz mode** | **Medium, and structurally invited** — `QuickReviewSessionEntity` can already represent a collection-anchored session, so reusing it is the path of least resistance; `EXAM_MODES.md:153` routes that choice into a 5-mode contract review rather than blocking it outright, so the guard is procedural and skippable | §9, §10 |
| **Stale generated review artifacts** | N/A under the recommendation; **high under Model B** — `v0.143.0` shipped exam-pool invalidation for this class | §5 |
| **Lost source provenance** | **Real and pre-existing.** Deleting a note orphans its references inside other sessions' JSONB; no FK | `NoteService.java:495-503`; `V4__quick_review_sessions.sql:4` |
| **Mastery inflation** | **Low if §12 holds**, and the firewall precedent exists | `MemorizationCardEntity.java:18` |
| **Review Set action overload** | **High — the page is already loaded.** 2 header buttons + up to 4 overflow items + 1 primary CTA + up to 3 quick actions + a progress link + a conditional guidance CTA + a Companion chat panel + 3 buttons per note row | `collection-detail-page-client.tsx:1210-1341, 1093-1186, 1190-1205, 2901-2907` |
| **Unnecessary LLM cost** | Avoided by §11; **would be N calls per review, not one** | `LlmStudyPackService` |
| **Huge Review Set scope** | **High if scope is the Goal.** Non-admin Subject Plans mean 11.4 pack-bearing notes, max 77 | *(Production, 2026-09-13)* |
| **Mobile information overload** | **High and quantified.** 7 notes ≈ 11,500 chars of summary + ~64 concepts; a mean Subject Plan ≈ 18,700 chars | §1.4 |
| **⚠️ Building the fourth low-adoption review surface** | **The dominant risk, and it is not in the brief's list.** Flashcards (unmeasurable, no events), Memorization (3 users / 7 cards / 10 weeks) and plan-scoped Adaptive Practice (0 of 729 sessions) are three consecutive bets in this exact category | §1.1, §1.5 |

---

## 19. Smallest coherent release sequence

**Phase 0 — read the checkpoint that already exists. No code.** `ROADMAP.md:515`, due 2026-10-13. Report the plan-scoped Adaptive Practice count honestly, *including its denominator*. **Do not re-date it silently if the denominator is short** — bring it back to the owner as §20 Q1.

**Phase 1 — the Model D surfacing pass, if the owner wants motion now.** Frontend-only, no migration, and **labeled internally as what it is: making existing aggregation legible, not shipping Cross-Note Review.** Its own measurement is engagement with plan-level material.

**Phase 2 — deterministic Subject Plan review (Model C minus synthesis).** Only if Phase 0 reads nonzero *or* Phase 1 shows engagement.

**Phase 3 — synthesis, export, Coach.** Each individually gated, each requiring the provenance orphan gap closed first (§11).

**Challenge to the brief's proposed sequence:** the brief's Phase 1 is "deterministic core." This audit inserts a Phase 0 that costs nothing and can cancel the whole sequence, and downgrades the brief's Phase 1 to a surfacing pass, because §1.4 shows the deterministic core is thinner than the brief assumes and §1.1 shows the demand signal is already under measurement.

---

## 20. Genuine owner decisions

Only questions the repository and production cannot answer.

**Q1 — What happens when a checkpoint's denominator never arrives?** `ROADMAP.md:515` is due 2026-10-13 and, at 2 Adaptive Practice sessions in the 10 days since its announcement anchor, will almost certainly be unreadable then. The options are: re-date again (a third time), convert it to a threshold gate (*"read when monthly actives exceed N"*), or **accept the null as an answer** on the grounds that a capability with zero uses across its entire lifetime is informative even at a small denominator. **This is the decision that governs Cross-Note Review's fate**, and it is a judgment about evidence standards, not a fact.

**Q2 — Is the target learner the one production shows, or the one the product is being built for?** Production says median 1 distinct note ever studied and 9 monthly actives; separately, 5,583 of 7,608 notes carry a non-null `copied_from_note_id`, i.e. they were copied from a public note rather than authored. The brief describes a learner studying 20 notes a week. If the owner's answer is *"we are building for the learner we intend to have, not the one we measure,"* that is legitimate — but it must be said out loud, because every scope number in §7.1 changes meaning under it.

**Q3 — "Studied" or "has material"?** §7.1: 4 Subject Plans versus 452. The first makes an honest consolidation feature with almost no audience; the second makes a reviewer over unread material, which the brief prohibits by name. **There is no third option in the current data**, and the repo cannot choose.

**Q4 — Should the dead PDF export meter be built, hidden, or labeled?** §1.6, §14. Independent of this feature; needs an owner call because it is currently a visible promise.

**Q5 — Does the owner accept a fourth review-only surface after three low-adoption ones?** Flashcards, Memorization (3 users), plan-scoped Adaptive Practice (0 uses). This is a portfolio judgment, not an engineering one.

Deliberately **not** asked, because the repo answered them: whether a sixth mode is possible (no — `EXAM_MODES.md:25`); whether a new entity is needed (no — §10); whether an LLM call is needed for the deterministic core (no — §11); whether cross-pack concept merging is available (no — `DashboardService.java:471-473` plus the 0.4% read); whether PDF export infrastructure exists (no — §1.6).

---

## Decision block

**Verdict:** **DEFER** — gated on the checkpoint that already exists (`ROADMAP.md:515`, `v0.107.0`, due 2026-10-13), not on a new one.

*One-sentence why:* NoteLib already shipped the cross-note-over-a-Subject-Plan hypothesis in `v0.107.0` and **zero of 729 production sessions have ever been plan-scoped (collection-anchored)**, while the material a deterministic review would assemble is far thinner than the brief assumes (58.9% of key concepts have no definition anywhere) and the learner the brief describes — five notes a day, twenty a week — exists in production as **two non-admin accounts**.

**Recommended learner-facing concept:** *"Bring the topics you've been studying in this Subject Plan back together before you test yourself."* — **Review Session**, ephemeral, not a Review Pack. Not to be built yet.

**Recommended v1 scope:** Subject Plan (a child `NoteCollectionEntity`), resolved through the existing `PlanSourcedExamVerifier`. Not selected Notes, not Review Set, not Coach-resolved.

**New persisted entity required?** **NO** — and it should not reuse `QuickReviewSessionEntity` either; under `EXAM_MODES.md:153` that choice would owe a 5-mode contract review and a doc amendment first (a gate, not a wall — see §9).

**New LLM generation required?** **NO** for the deterministic core. Synthesis is the only part with genuine incremental value and is **not recommended** until demand exists and the provenance orphan gap is closed.

**Writes ConceptHealth?** **NO.** Nor `ActivityType`, nor `memorization_cards`, nor `quick_review_sessions`.

**New exam mode?** **NO.**

**Export in v1?** **NO / DEFER** — and the brief's premise is wrong: no PDF writer exists in this repo, and the DOCX one is quiz-shaped and `ADMIN`/`TEACHER`-only.

**Coach integration in v1?** **NO** — foreclosed by `DashboardService.java:471-473` and the 0.4% concept-identity sizing read; would fire for 4 accounts.

**Why this belongs in NoteLib (when it does):** it is the only gap in the stated loop that is genuinely empty — every cross-note capability shipped so far produces questions, and nothing presents *material* across notes — and the notes-first architecture already supplies the source resolver, the sampler, the provenance pattern and the cross-note aggregation, so it costs no new entity and no new quota.

**What would falsify the hypothesis (i.e. would flip this to BUILD):** the `v0.107.0` checkpoint reading a nonzero **collection-anchored** session count on a denominator worth reading; or, after a Phase 1 surfacing pass, learners engaging with plan-level material. **What would confirm the defer:** that checkpoint reading zero again, or Memorization still sitting near 3 users at its next read.

---

## Housekeeping

Per `CLAUDE.md` kickoff step 8, **this file requires a row in `ROADMAP.md`'s Backlog Index at the next release kickoff** — every `docs/claude-plans/` file must be indexed, and that scan is the only enforced checkpoint against a planning document going unindexed across release cycles. **The row is not added here**; this is the flag, not the action.

**Its sibling audit needs the same row and does not have one yet:** `docs/claude-plans/note-visibility-learning-status-stage1.md` (2026-09-13) is **still untracked**, carries **five unresolved owner decisions** (its Q1–Q5), and its Q1 is a live dependency of this document's §8. It is not release-artifact exempt. Two further untracked files in the same directories were observed in `git status` and may also need checking against the Index at the next scan: `docs/claude-plans/2026-09-12-bulk-regeneration-404-terminal-state-fix-plan.md` and `docs/claude-findings/2026-09-12-bulk-regeneration-modal-wedged-stale-batch-id.md`.

This file is intentionally left **untracked and uncommitted**, matching the sibling audit's convention for Stage 1 planning documents.

**Production facts in this document were read on 2026-09-13 and decay.** Per `CLAUDE.md`'s snapshot-not-fact rule, re-read before any of them reaches a plan, prompt, kickoff or release note — in particular the 0-of-729 plan-scoped sessions, the 9 monthly actives, the 41.1% concept-match rate, the 4-versus-452 Subject Plan split, and the 3-user Memorization adoption. One fact in the sibling audit **already decayed within a single day** (§1.7).
