# Note Visibility by Learning / Study Pack Status — Stage 1 Audit

**Status: Stage 1 audit only. Nothing implemented, no migration, no behavior change, no commit.**
**Date: 2026-09-13. Repo state: branch `releases/v0.144.0`, last release `v0.143.0` (Released).**
**Production reads: READ-ONLY (`SELECT` only), per `CLAUDE.md`'s production rule. All production
facts are labeled and dated separately from repository facts.**

---

## 0. How this audit was performed, and one limitation to declare

Every claim below about current behavior was verified by opening the file and reading the lines
cited. Where a doc and the code disagree, the code is reported and the doc is named as stale.

**Limitation, declared up front:** four parallel research subagents were launched to trace the 18
surfaces in the brief and **all four died on an API session rate limit before returning anything.**
The tracing was then done directly, with two targeted follow-up subagents launched afterward for the
highest-value remaining gaps (Dashboard/Coach and My Progress/Recent Sessions), both of which
completed and are folded in below. The consequence is *coverage*, not accuracy: the surfaces below
carry real `file:line` evidence, but two items in the brief's list were reached only shallowly and
are marked **[NOT FULLY TRACED]** rather than reported as clean. They are: data export and the Ask
Companion panel. No conclusion in this document depends on them.

**⚠️ Correction made after this document's first draft:** Recent Sessions was initially left marked
`[NOT FULLY TRACED]` even though a follow-up subagent had already traced it completely — the final
synthesis pass did not fold that trace back in. §4 row 14 and §5's note below now reflect the actual
traced behavior, verified against `NoteService.java:314-333` directly. This is exactly the class of
defect this audit itself is about — a downstream consumer left stale after an upstream state changed
— so it is corrected here rather than left standing.

---

## 1. Executive judgment — the brief's premises, tested

### 1.1 The state names in the brief are the API names, not the persisted ones. Both exist. The translation between them is lossy, and that seam is the whole subject of this audit.

The brief lists `DRAFT / GENERATING / FAILED / STUDY_PACK_READY` and warns not to trust
documentation. The documentation it is quoting (`AGENTS.md:172-176`, `DATA_MODEL.md:63`) is
faithfully reproduced — but it describes **the API string**, not the column.

- **Persisted enum** (`backend/src/main/java/com/studysnap/backend/entity/NoteStatus.java:3-8`):
  `DRAFT, GENERATING, FAILED, GENERATED`. There is no `STUDY_PACK_READY` in the database.
- **Production confirms this at the schema level.** Constraint `ck_notes_status` on `notes` is
  `CHECK (status IN ('DRAFT','GENERATING','FAILED','GENERATED'))` — exactly four values, and the
  ready one is spelled `GENERATED`. *(Production read, 2026-09-13.)*
- **API string** is produced by `NoteStudyPackStatusResolver`
  (`backend/.../service/NoteStudyPackStatusResolver.java:10-35`), which maps `GENERATED →
  "STUDY_PACK_READY"`.

So the brief is not wrong about the vocabulary the frontend sees. **What no doc records is that the
translation is not a rename — it changes meaning**, and the way it changes meaning is the root of
the most serious defect in this audit (§1.2). `NoteStudyPackStatusResolver:20-35`:

```java
if (resolvedStatus == NoteStatus.GENERATED) { return STUDY_PACK_READY; }   // :22-24  ignores hasStudyPack
if (resolvedStatus == NoteStatus.GENERATING) { return GENERATING; }        // :25-27  ignores hasStudyPack
if (resolvedStatus == NoteStatus.FAILED)     { return FAILED; }            // :28-30  ignores hasStudyPack
if (!hasStudyPack) { return DRAFT; }                                       // :31-33
return STUDY_PACK_READY;                                                   // :34     DRAFT + pack ⇒ READY
```

Three of the five branches return a **lifecycle** answer to what every consumer treats as a
**capability** question, and only the `DRAFT` branch consults whether the artifact actually exists.

### 1.2 The headline finding: a note that HAS a complete Study Pack is reported as having none, and this is not hypothetical — it is the only generation failure that has ever happened in this product.

Regeneration is in-place (`CLAUDE.md` versioning rule) and sets the note to `GENERATING` **while the
previous Study Pack row stays intact**: `StudyPackService.java:224` (`sourceNote.setStatus(NoteStatus.GENERATING)`),
with the pack only replaced later at `:1002` / `:1277`. If that regeneration fails,
`markNoteGenerationFailed` (`StudyPackService.java:1314-1324`) sets the note to `FAILED` — its
early-return guard at `:1315-1317` only skips notes already `GENERATED`, and a regenerating note is
`GENERATING`, so the guard does not fire.

The note is now `FAILED`. **Its Study Pack is untouched, complete, and valid.** But
`NoteStudyPackStatusResolver:28-30` returns `"FAILED"` without looking at the pack, and every
frontend surface keys off that string:

| Surface | Line | Effect on a FAILED note that still has a complete pack |
|---|---|---|
| Note Detail — all quiz actions | `private-note-detail-page-client.tsx:2949-2971` | Quick Review / Challenge / Adaptive buttons replaced by "Retry Generation" |
| Note Detail — Flashcards + Memorization entry | `:3033` (`!hasGenerationFailed && !isDraft && keyConcepts.length > 0`) | Entry points removed |
| Flashcards page | `flashcards-page-client.tsx:130-135` | "Flashcards are not available yet… Generation did not complete" |
| Memorization page | `memorization-page-client.tsx:239` | Same |
| Interview Practice | `interview-practice/page.tsx:158` (`studyPackStatus !== "STUDY_PACK_READY"`) | Blocked |
| Review Set premium exam pool | `collection-exam.ts:19-20` | Note silently dropped from Long/Board Exam sources |
| Public note page | `public/library/[subject]/[slug]/page.tsx:139` (`isDraft = status !== "STUDY_PACK_READY"`) | Public page renders the note as a **Draft**; `:96,:118` drop it from related-note lists |
| Public copy CTA | `public-seo-copy-cta.tsx:41` (`skipGenerate: status === READY`) | Tells the copier they must generate — though `NoteService:402-406` will in fact copy the pack and mark the copy `GENERATED` |

Meanwhile **the backend disagrees with all of it.** Long Exam's eligible pool is
`StudyPackStatus.DONE` on the *pack* (`LongExamService.java:1084-1090`) and never reads `NoteStatus`
— so the same note is backend-eligible and frontend-ineligible, simultaneously, on a paid path.

**There is a second producer of this defect, and it fires on a timer rather than needing an LLM
error.** The `v0.86.0` recovery sweeper flips stranded generations to `FAILED` with the same
blindness. `GenerationRecoveryRowWriter.recoverNote:158-167` calls
`studyPackService.markNoteGenerationFailed(note)` — the single-arg overload at
`StudyPackService.java:1303-1305`, delegating to the same `:1314-1324` method, which **never
consults pack presence.** So a regeneration that merely *strands* (a JVM kill mid-generation, the
case `v0.86.0` exists for) becomes `FAILED`-with-an-intact-pack after **`noteBoundMinutes`, a
configured property whose default is 120** (`StudySnapProperties.java:511`), plus up to one sweep
cadence of 10 minutes (`GenerationRecoveryJob.java:18`, `cron 0 */10 * * * *`). *The bound is the
configured property, not the cadence — `CLAUDE.md` records that exact mistake being made once.*

**And the class the sweep refuses to touch is worse.** `GENERATING` rows with a null
`generation_enqueued_at` are counted, logged *"leaving them untouched"*
(`GenerationRecoveryService.java:104-110`), and **strand indefinitely.** Such a note keeps an intact
Study Pack, reports `GENERATING` forever, has every learning action hidden, and — unlike `FAILED` —
is offered no retry at all: Note Detail renders a disabled "Generating..." button
(`private-note-detail-page-client.tsx:2948`). No terminal state, no recovery affordance, no sweeper.
*(Production, 2026-09-13: zero notes are currently `GENERATING`, so this class has no live instance.)*

**Production evidence, 2026-09-13 (read-only):** seven notes in the entire database have ever
recorded a generation failure (`generation_failed_at IS NOT NULL`). **For all seven, the Study Pack
was created weeks-to-months before the failure** (`pack_created_on` ranges 2026-06-01 → 2026-08-27;
`failed_on` 2026-09-07 → 2026-09-10) — i.e. **7 of 7 production failures were regeneration failures
on notes that already had an intact Study Pack.** All seven are `ADMIN`-owned, **all seven are
`PUBLIC`, and all seven are members of Review Sets.** All have since been recovered to `GENERATED`.

This reframes the defect class. The brief asks "if generation FAILED, should the note disappear from
learning context or remain visible with a recovery action?" — the repo's honest answer is that
**`FAILED` has never once meant "there is no learning material here."** It has only ever meant "the
most recent attempt to *replace* existing learning material did not finish."

Severity note, stated honestly: because all seven were curator-owned, no private learner lost access
to *their own* library. The exposure was via public pages and Review Set membership. It is a real
live defect, not a latent one, but it is not an outage.

### 1.3 "Visibility" is the wrong word, and the repo already supplies better ones. Do not coin a new term.

`NoteVisibility` is a real enum with a real meaning (`entity/NoteVisibility.java:3-6`: `PRIVATE`,
`PUBLIC`). Reusing "visibility" for this concept would collide with it in code review, in grep, and
in the schema.

The repo already has three established terms, each correct in its own layer, and they should simply
be used consistently rather than replaced:

- **Readiness** — already the user-facing library filter vocabulary (`library/page.tsx:83`
  `LibraryReadinessFilter`; backend `NoteLibraryReadiness`, `NoteController:116`
  `READINESS_REQUEST_PARAM`). Correct for *filtering and sorting a list of notes*.
- **Availability** — already the API's capability vocabulary (`NoteResponse.java:33-35`
  `quickReviewAvailable`, `challengeQuizAvailable`, `adaptivePracticeAvailable`). Correct for *can
  this note do this action*.
- **Eligibility** — already the backend's session-assembly vocabulary (`EligiblePlanSource`,
  `LongExamInsufficientEligibleSourcesException`, `BoardExamInsufficientEligibleSourcesException`).
  Correct for *may this note be sampled into a generated session*.

**Recommendation: adopt the existing three-word split rather than inventing a fourth.** Readiness =
list filtering. Availability = per-note action affordance. Eligibility = session assembly. They are
genuinely different questions and the codebase already distinguishes them; it just does not do so
*consistently*.

### 1.4 The brief's Principle 1 ("Draft notes remain first-class") is already satisfied. Do not spend the release re-proving it.

Verified, not assumed:

- **Library shows drafts by default** and offers an explicit Draft filter
  (`library/page.tsx:83`, `:132`); the default is `ALL` and applies no readiness predicate
  (`NoteLibraryRepositoryImpl.java:449-451`).
- **The Review Set / Study Plan builder does not filter drafts.** The picker predicate
  `filterPickerNotes` (`study-plan-builder-page-client.tsx:207-219`) filters on *already-present* and
  *text query* only — no status term. The fetch is `listNotes(limit, search)`
  (`lib/api.ts:5114-5128`), which sends only `limit` and `search`. `NoteCollectionService` applies no
  `NoteStatus` gate on add.
- **Note Detail is fully functional on a draft** and offers Generate / Retry
  (`private-note-detail-page-client.tsx:2949-2959`, `:2990-2997`).

**Production, 2026-09-13:** 18 `DRAFT` notes exist across 14 of 292 note-owning accounts; 17 are
older than 7 days; oldest 2026-04-22. **Zero draft notes are in any Review Set** (0 of 6,872
`note_collection_items`). Since the builder demonstrably does *not* filter them, that zero is
**behavioural, not enforced** — learners simply do not add drafts to Review Sets today.

The practical consequence for scoping: **the Review Set half of the brief's problem is prospective.**
It is worth designing a contract for, and not worth building a mechanism for yet.

### 1.5 The brief's suspicion that a capability abstraction may already exist is correct — and it is better than the brief assumes.

`NoteService.mapToResponse` (`:1491-1546`) already derives capability **from artifacts, not from
lifecycle**:

```java
boolean hasGeneratedQuiz = !quiz.isEmpty();                                        // :1500
...
hasGeneratedQuiz,                                                                  // :1537 quickReviewAvailable
hasGeneratedQuiz,                                                                  // :1538 challengeQuizAvailable
hasGeneratedQuiz && featureGateService.hasFeatureAccess(planType, ADAPTIVE_QUIZ),  // :1539 adaptivePracticeAvailable
```

This is Model B, already shipped, on one endpoint. Three things are wrong with it, and all three are
fixable without a new persisted field:

1. **It mixes artifact capability with entitlement in a single boolean.** `:1539` ANDs "this pack has
   questions" with "this plan includes Adaptive Practice." The brief explicitly asks these be kept
   apart, and the repo proves why: `adaptivePracticeAvailable` **also exists in
   `MePlanResponse.java:145` and `BillingUsageSummaryResponse.java:19`, where it means entitlement
   only**. One name, two meanings, three DTOs.
2. **It is inline in a private method, so nothing else can reuse it** — which is precisely why every
   other surface reinvented the rule (§3).
3. **The local variable name is actively misleading.** `hasGeneratedQuiz` (`:1500`) is derived from
   `studyPack.getQuiz()`, while `generatedQuiz` (`:1498`) is a `GeneratedQuizEntity` — a *different
   artifact*, the teacher-authored quiz. Two near-identical names for two unrelated things, eleven
   lines apart.

`frontend/lib/collection-exam.ts` is the second, smaller instance of the same good idea: two named,
commented capability predicates (`canIncludeCollectionItemInExam:11-13`,
`canIncludeCollectionItemInPremiumExam:18-20`). It also contains its own internal disagreement — see
D5.

---

## 2. Current-state truth

### 2.1 Lifecycle and source-of-truth fields

| Concern | Field | Source |
|---|---|---|
| Note lifecycle | `notes.status` : `DRAFT / GENERATING / FAILED / GENERATED` | `NoteStatus.java:3-8`; DB `ck_notes_status` |
| Note privacy | `notes.visibility` : `PRIVATE / PUBLIC` | `NoteVisibility.java:3-6` |
| Pack lifecycle | `study_packs.status` : `DONE / NEEDS_CONFIRMATION / FAILED` | `StudyPackStatus.java:3-7` |
| Artifact — concepts | `study_packs.key_concepts` jsonb, `nullable = false` | `StudyPackEntity.java:57-59` |
| Artifact — quiz | `study_packs.quiz` jsonb, `nullable = false` | `StudyPackEntity.java:61-63` |
| Artifact — teacher quiz | `generated_quizzes` row keyed by `note_id` | `NoteService.java:1498` |
| Last-failure record | `generation_failure_code / _reason / generation_failed_at` — **deliberately not cleared on a later success** | `NoteEntity.java:95-118` |
| API projection | `studyPackStatus` string | `NoteStudyPackStatusResolver.java:10-35` |

**`NoteStatus` and `StudyPackStatus` are independent and are never reconciled.** Nothing in the
codebase asserts that a `GENERATED` note has a `DONE` pack, or that a `FAILED` note does not.

### 2.2 `NoteStatus` transition map

*Method, stated so the claim is checkable: every `setStatus(NoteStatus.` site in
`backend/src/main`, plus a check that no bulk update writes the column — the only `@Modifying`
update on `NoteEntity` is `reassignOwnerByOwnerUserIdAndVisibility`
(`NoteRepository.java:103-115`), which touches `ownerUserId` and `updatedAt` only. The recovery
sweeper writes status through `StudyPackService:1303-1305 → :1314-1324`, so it is covered by the
`FAILED` row below rather than being a separate site.*

| → | Site | Meaning |
|---|---|---|
| `DRAFT` | `NoteService.java:192` | Note created |
| `DRAFT` | `NoteService.java:385` | Copy initialised (all copies start here) |
| `DRAFT` | `StudyPackService.java:528` | **Study Pack deleted ⇒ note demoted to DRAFT** |
| `GENERATING` | `StudyPackService.java:224` | Generate/regenerate from an existing note |
| `GENERATING` | `StudyPackService.java:326` | Generate from topic |
| `GENERATED` | `StudyPackService.java:1002`, `:1277` | Generation succeeded |
| `GENERATED` | `NoteService.java:405` | Non-owner copy of a note that has a pack |
| `GENERATED` | `NoteService.java:366` | **Re-copy heals an existing pack-less copy** |
| `GENERATED` | `ShareService.java:126` | Share-derived acquisition |
| `FAILED` | `StudyPackService.java:1319` | Generation attempt failed (skipped if already `GENERATED`, `:1315-1317`). **Also reached by the recovery sweeper** via `GenerationRecoveryRowWriter:158-167` → `StudyPackService:1303-1305`, after `noteBoundMinutes` = 120 (`StudySnapProperties:511`) + a ≤10-min cadence (`GenerationRecoveryJob:18`) |

Two consequences worth stating plainly:

- **There is no path that yields `GENERATED` with no pack.** Deleting a pack demotes the note
  (`:528`). Confirmed in production: 0 of 7,589 `GENERATED` notes lack a pack *(2026-09-13)*. So the
  `GENERATED`-ignores-`hasStudyPack` branch (`resolver:22-24`) is **latent, not live** — correctly
  flagged, but not a current bug.
- **`StudyPackService.java:528` silently demotes a Review Set member to `DRAFT`** with no
  invalidation of anything downstream. Deleting a pack is the one way to remove a capability from a
  note that is already inside a learning journey.

### 2.3 Public-note copy — proven from code, as the brief requires

`NoteService.copyNote` (`:339-431`) and `copySourceStudyPack` (`:433-462`):

| Case | Resulting `NoteStatus` | Study Pack | Evidence |
|---|---|---|---|
| Owner self-copy | `DRAFT` | **None** — `copySourceStudyPack` is inside `if (!isOwner)` | `:385`, `:420-421` |
| Non-owner copy, source **has** pack | **`GENERATED`** | **New row**, deep copy | `:402-406`, `:421`, `:438-461` |
| Non-owner copy, source has **no** pack | `DRAFT` | None | `:385`, `:404` guard |
| Re-copy of an existing copy that lacks a pack | **`DRAFT` → `GENERATED`** | New row added retroactively | `:359-372` |

Specifics the brief asked for: the copy is a **new `study_packs` row, not a shared reference**
(`:438-441` assigns a fresh id and the copier as owner); `keyConcepts` and `quiz` are deep-copied
(`:446-451`); the copied pack is stamped `StudyPackStatus.DONE` unconditionally (`:457`);
`includeStudyPack` defaults to `true` (`:339-341`) and the 3-arg overload has no other production
caller.

**This matches `AGENTS.md:188-189` exactly.** That documented exception is accurate — a rare case in
this audit, and worth recording as such.

**So the brief's warning is correct and the numbers are stark.** *(Production, 2026-09-13:)* **5,577
of 7,589 `GENERATED` notes (73%) are copies**, and 6 of 18 `DRAFT` notes are copies. Neither
"copied ⇒ draft" nor "owned ⇒ generated" holds.

The one unremarked consequence: `:359-372` means **a note's capability set can increase without any
generation**, hours or months after creation, triggered by an unrelated user action. Any design that
caches capability will be wrong here.

---

## 3. The core structural finding: ten implementations of one question

There is no shared definition of "can this Note support this learning action." There are at least
**ten independent implementations across four layers**, and they do not agree.

| # | Implementation | Layer | Predicate | Evidence |
|---|---|---|---|---|
| 1 | `NoteStudyPackStatusResolver.resolve` | Java | `NoteStatus` first, pack presence only for `DRAFT` | `:20-35` |
| 2 | `STUDY_PACK_READY_PREDICATE` | **SQL** | The same rule, re-expressed by hand | `NoteLibraryRepositoryImpl.java:105-113` |
| 3 | `mapToResponse` availability triple | Java | `!quiz.isEmpty()` (+ plan gate on one) | `NoteService.java:1500, 1537-1539` |
| 4 | `resolveEligiblePlanSourcePool` | Java | `StudyPackStatus.DONE` on the pack | `LongExamService.java:1084-1090` — the same predicate, verified, also gates Recent Sessions review (`NoteService.java:314-333`, `getOwnedStudyPackIdOrThrow`): a third independent call site for the identical rule |
| 5 | Board Exam pool | Java | Same `findByOwnerUserIdAndNoteIdInAndStatus(..., DONE)` lookup — read, not inferred | `ChallengeQuizService.java:1672-1674`, consumed `:1682-1693` |
| 6 | `groupQualifyingPacksBySubject` | Java | `hasKeyConcepts(studyPack)` | `ProgressReportService.java:265` |
| 7 | Challenge Quiz start | Java | `quiz.isEmpty()` ⇒ `ChallengeQuizNotAvailableException` | `ChallengeQuizService.java:1898-1901` |
| 8 | Library `QUIZ_READY` filter | **SQL** | `exists (generated_quizzes)` — a different artifact | `NoteLibraryRepositoryImpl.java:457-464` |
| 9 | `collection-exam.ts` | TS | `generatedQuizId` / `studyPackStatus === READY` / optional `studyPackId` | `:11-13, 18-20, 44-53` |
| 10 | Note Detail flashcard entry | TS | `note.keyConcepts.length > 0` | `private-note-detail-page-client.tsx:3033` |

Four distinct notions of "ready" are in play — **`NoteStatus`** (#1, #2), **artifact contents**
(#3, #6, #7, #10), **`StudyPackStatus`** (#4, #5), and **a separate `generated_quizzes` row** (#8,
#9). Nothing keeps them in sync and no test compares them.

---

## 4. Surface matrix

`Ready` = `GENERATED` + pack. `Regen-fail` = `FAILED` with an intact pack (the live case, §1.2).

| # | Surface | Draft | Generating | Failed (no pack) | **Regen-fail (pack intact)** | Ready | Required artifact | Enforced |
|---|---|---|---|---|---|---|---|---|
| 1 | Library list | Visible, badge "Draft" | Visible, "Generating" | Visible, "Generation Failed" | Visible, **"Generation Failed"** | Visible | — | Backend (`NoteLibraryRepositoryImpl:447-465`) |
| 1a | Library readiness filter | `DRAFT` matches | **No filter matches** | **No filter matches** | **No filter matches** | `STUDY_PACK_READY` | — | Backend `:452-464` |
| 2 | Note Detail | Full; Generate CTA | Polling card `:2983-2988` | Failure card + Retry `:2990-2997` | **Failure card + Retry; pack hidden** | Full | — | Frontend |
| 3 | Review Set detail | Listed | Listed | Listed | Listed | Listed | — | None |
| 3a | Review Set premium-exam launch | Excluded | Excluded | Excluded | **Excluded despite valid pack** | Included | `studyPackStatus === READY` | Frontend `collection-exam.ts:19` |
| 4 | Review Set builder picker | **Selectable** | **Selectable** | **Selectable** | Selectable | Selectable | none | **Neither** (`:207-219`) |
| 5 | Dashboard / Today's Focus | Never surfaced | Never surfaced | Never surfaced | Surfaced (pack-anchored) | Surfaced | Pack row | Backend `DashboardService:105-209` |
| 6 | Flashcards | "No key concepts yet" | "being generated" | "not available yet" | **"not available yet"** | Deck | `keyConcepts` | Frontend `:67-149` |
| 7 | Memorization | Same shape | Same | Same | **Same** | Cards | `keyConcepts` | Frontend `:146-260` |
| 8 | Quick Review | Entry hidden | Hidden | Hidden | **Hidden** | Runs | `quiz` non-empty | **Frontend only** — see D1 |
| 9 | Challenge Quiz | Hidden | Hidden | Hidden | **Hidden** | Runs | `quiz` non-empty | Both (`:1898-1901`) |
| 10 | Adaptive Practice | Hidden | Hidden | Hidden | **Hidden** | Needs weak concepts `:1105` | quiz + weak concepts + plan | Both |
| 11 | Long Exam | Not eligible | Not eligible | Not eligible | **Frontend excludes; backend INCLUDES** | Eligible | pack `DONE` | Both, **disagreeing** |
| 12 | Board Exam | Not eligible | Not eligible | Not eligible | **Same disagreement** | Eligible | pack `DONE` | Both, disagreeing |
| 13 | Progress | Excluded | Excluded | Excluded | **Included** (pack-keyed) | Included | `hasKeyConcepts` | Backend `:265` |
| 14 | Recent Sessions | n/a | n/a | n/a | **Listed AND clickable** | Listed | session rows | `getOwnedStudyPackIdOrThrow` uses row 4's `DONE` predicate |
| 15 | Data export | — | — | — | — | — | — | **[NOT FULLY TRACED]** |
| 16 | Teacher Exam Builder | Not "quiz-ready" | " | " | " | Only with `generated_quizzes` | `generatedQuizId` | Frontend `collection-exam.ts:11-13` |
| 17 | Public note page | Renders as Draft | Draft | Draft | **Renders as Draft** `:139` | Full | `status === READY` | Frontend |
| 17a | Public copy CTA | Offers Generate | " | " | **Offers Generate, wrongly** | Skips Generate | `status === READY` | Frontend `:41` |
| 18 | Cross-Note Review | Does not exist | — | — | — | — | — | §8 |

---

## 5. Defects vs. opportunities

**The live/latent standard used below, stated explicitly so the labels are comparable:** a defect is
**live** if its triggering condition has actually occurred in production at least once, and
**latent** if the code path is reachable but the condition has never arisen. By that standard D2 is
live (7 of 7 historical generation failures were exactly its trigger) and D1 is latent (zero Study
Packs have ever had an empty quiz). Neither has an instance *at this moment* — current instance
count is not the criterion, because D2's instances were remediated rather than prevented.

### Genuine defects

**D1 — Quick Review has no backend guard; the gate is frontend-only.**
`QuickReviewSessionService.startSession` (`:87-127`) creates an `IN_PROGRESS` session with
`totalQuestions = studyPack.getQuiz() == null ? 0 : size()` (`:112`), records a
`STARTED_QUICK_REVIEW` activity (`:121`) and a `QUICK_REVIEW_STARTED` analytics event (`:122`) — with
**no empty-quiz check**. Challenge Quiz throws `ChallengeQuizNotAvailableException` for exactly this
input (`ChallengeQuizService:1898-1901`). The frontend gate exists
(`NoteResponse.quickReviewAvailable`, `NoteService:1537`) and is the only thing preventing a
0-question session that would pollute Recent Sessions and activity. *Latent — production shows 0
packs with an empty quiz (2026-09-13). Asymmetry is real regardless.*

**D2 — `FAILED` / `GENERATING` hide an intact Study Pack across every learning surface.** §1.2. The
single highest-value finding; **7 of 7 production failures were this case**. It has **two
independent producers**: a thrown generation error (`StudyPackService:1314-1324`) and the recovery
sweeper on a timer (`GenerationRecoveryRowWriter:158-167`), neither of which consults pack presence.
A third variant — `GENERATING` rows with a null `generation_enqueued_at`, which the sweeper
explicitly declines to touch (`GenerationRecoveryService:104-110`) — strands with no terminal state
and no retry affordance at all. **Any fix must be made in the derivation, not in the failure paths**,
or it will cover one producer and miss the others.

**Recent Sessions — traced (see the §0 correction), and it is the one surface already behaving per
the doctrine this audit recommends, though by accident rather than design.** Neither
`QuickReviewSessionRepository` query behind the session list joins to `notes.status` at all — a
session row persists and is listed regardless of what happens to the note afterward
(`QuickReviewSessionRepository.java:216-225, 248-261`; `QuickReviewSessionEntity` stores `noteId`
as a plain UUID column with no `@ManyToOne`, so there is no schema-level join to reach). **Clicking
into a past session on a regen-failed note still works** — `getOwnedStudyPackIdOrThrow` (verified
above) checks only pack presence and `StudyPackStatus.DONE`, and a regen-fail leaves the old pack at
exactly that status. This is §6's recommended doctrine already in effect, one layer removed from
where it should be: the surface is right for the wrong reason — nobody engineered "stay usable
through a regen failure," it is a side effect of the review gate never having looked at `NoteStatus`
in the first place. **Worth citing as a working precedent when the eventual fix is written**, and a
useful check on the fix itself: whatever change makes D2's other surfaces behave this way must not
accidentally make Recent Sessions *more* restrictive than it is today.

*Aside, explicitly out of scope: the same trace found that a `LONG_EXAM`/`ADAPTIVE_PRACTICE` row in
the Recent Sessions list renders as a clickable button that no-ops on click
(`isNoteSessionReviewMode`, `frontend/lib/note-session-review.ts:11,26-28`, which only recognizes
`QUICK_REVIEW`/`CHALLENGE`/`BOARD_EXAM`). This is a session-mode dispatch bug, unrelated to note
status or capability — it does not belong in this audit's scope and is noted only so it is not lost.*

**D3 — Frontend and backend disagree about Long/Board Exam eligibility, and the repo knows it.**
`LongExamPrimarySourceNotEligibleException`'s own Javadoc states: *"Reachable because the start-time
primary lookup does not filter on `StudyPackStatus`, while the eligible pool does."*
`LongExamPlanSourceSampler.java:31-38` repeats it. The mismatch was papered over with a named
exception instead of one predicate. On the regen-fail note the disagreement inverts: the **backend**
would accept it (pack is `DONE`) and the **frontend** removes it (`collection-exam.ts:19`).

**D4 — `adaptivePracticeAvailable` means two different things in three DTOs.**
`NoteResponse:35` = artifact ∧ entitlement (`NoteService:1539`); `MePlanResponse:145` and
`BillingUsageSummaryResponse:19` = entitlement only. Directly contradicts the brief's required
separation of "does the artifact exist" from "is this user entitled."

**D5 — `collection-exam.ts` contradicts itself about the same capability.**
`canIncludeCollectionItemInPremiumExam` (`:18-20`) requires only `studyPackStatus === READY`, while
`resolveCollectionScopedSourceNotes` (`:37-53`) re-filters the *same* capability and adds a
conditional `requireStudyPackId` (`:52`). Two answers to one question, in one 54-line file.

**D6 — Failed and Generating notes are unreachable by any Library filter.** `DRAFT` requires
`status IN (null,'DRAFT') AND NOT EXISTS a pack` (`NoteLibraryRepositoryImpl:452-455`);
`STUDY_PACK_READY` requires `GENERATED` or draft-with-pack (`:105-113`). `FAILED` and `GENERATING`
match neither — findable only under `ALL`. The learner has no way to ask "what needs my attention?"

**D7 — Recovery is described but not offered on the destination surfaces.** `FlashcardsGuard`
(`flashcards-page-client.tsx:18-27`) accepts only `title` and `message` — no action slot — yet its
`FAILED` copy reads *"Retry generation when you are ready"* (`:133`) and its draft copy reads
*"Generate a Study Pack…"* (`:140`). Both name an action the page cannot perform. Memorization
mirrors it (`:239`, `:246`). Note Detail does this correctly (`:2990-2997`), so this is
inconsistency, not absence. Squarely the brief's "avoid dead-end disabled UI."

**D8 — The `STUDY_PACK_READY` rule is hand-duplicated in SQL.**
`NoteLibraryRepositoryImpl:105-113` re-expresses `NoteStudyPackStatusResolver:20-35` in SQL. They
agree today; nothing keeps them agreeing and no test compares them.

### Product opportunities (not defects)

- **O1 — "Quiz Ready" is a near-empty category.** `canIncludeCollectionItemInExam` requires a
  `generated_quizzes` row (`collection-exam.ts:11-13`), as does the Library `QUIZ_READY` filter
  (`:457-464`). *Production, 2026-09-13:* **8 of 7,589** `GENERATED` notes have one, and **4 of
  6,872** Review Set items — all 8 belong to the `ADMIN` account. There is exactly **one** `TEACHER`
  profile, owning 1 note with 0 generated quizzes. The vocabulary is confusing (a learner reads
  "quiz-ready" as "has a Study Pack quiz"), but it is shown only to teaching-mode profiles, the exam
  builder and admins (`profile-mode.ts:45-59`). **Naming issue, near-zero live impact — do not
  prioritise it.**
- **O2 — Deleting a Study Pack silently demotes a Review Set member** (`StudyPackService:528`) with
  no downstream invalidation.
- **O3 — Re-copy can grant capability retroactively** (`NoteService:359-372`) — an argument against
  ever caching capability.
- **O4 — Today's Focus can recommend Quick Review on a pack it has not checked.**
  `DashboardService:135-152` falls back to the most recently created pack with
  `resumeType = QUICK_REVIEW` and no quiz-length check. Same latent class as D1.

### Explicitly NOT a defect — verified before writing

**Exam question pool staleness after regeneration is FIXED.** `v0.143.0` item 2 shipped
unconditional pool invalidation on regeneration (`RELEASES.md`, `v0.143.0` §Planned Scope, PR #1382,
`77b6c226`). Reporting it as open would have repeated exactly the failure class `CLAUDE.md` is
loudest about. Two adjacent items remain genuinely open and are *not* re-litigated here: the
`deactivateShareLinksForNote` default-scope gate gap (already an open Backlog row), and the Challenge
Quiz per-user question bank, which `v0.143.0` explicitly refused to scope without tracing where it is
populated. **This audit does not scope either.**

---

## 6. Recommended doctrine

The brief's candidate form is close. Two amendments, both forced by evidence rather than taste: it
says nothing about *stale-but-valid* material (§1.2, the only failure that has actually occurred),
and it conflates having an artifact with being entitled to use it (D4).

> **A Note is always visible wherever ownership, organization or authoring matters — Library, search,
> Note Detail, Review Set structure, the builder — regardless of lifecycle state.**
>
> **A learning action offers itself when the artifact it consumes exists *right now*, not when the
> Note's lifecycle says the last generation attempt succeeded.** Existing material stays usable while
> a regeneration runs and after one fails; lifecycle state is reported alongside it, never in place
> of it.
>
> **Where an artifact is genuinely absent, the surface says which artifact is missing and offers the
> action that would create it.** It does not merely name that action.
>
> **Entitlement is a separate question, answered separately.** "This Note has questions" and "your
> plan includes this mode" are never combined into one boolean.

Two consequences worth stating because they close the brief's open questions:

- `FAILED` and `GENERATING` are **facts about the last generation attempt**, not about the Note's
  learning material. A Note carrying both an intact pack and a `FAILED` stamp is *usable and
  retryable*, and should present as both.
- **A Note is never excluded from a surface for lacking an artifact that surface does not consume.**
  Flashcards needs `keyConcepts`. Quick Review needs `quiz`. Neither needs `NoteStatus`.

---

## 7. Recommended architecture — **Model B, implemented through the abstraction that already exists**

Not a fourth model. Model B is already partially shipped; the work is to finish and unify it.

**Rejected: Model A (status-filtered visibility)** — it is the mechanism that produced D2, D6 and
the public-page misrender. It is the bug, not the fix.

**Rejected: Model C as a standalone** — surface-specific minimums are *correct* (Flashcards really
does need something different from Long Exam), but as an architecture it is what the repo already
has, and §3 is what it looks like after ten independent implementations.

**Recommended: B, with C's per-surface rules expressed as named members of one derivation.**

Three layers, kept strictly apart:

1. **Artifact capability — derived, never persisted.** One backend resolver, the extraction of
   `NoteService:1500, 1537-1539`, answering per note+pack: is `quiz` non-empty, is `keyConcepts`
   non-empty, does a `generated_quizzes` row exist, is the pack `StudyPackStatus.DONE`. **It reads
   artifacts. It does not read `NoteStatus`.** That single rule fixes D2, D3 and the public-page
   misrender at their shared root.
2. **Lifecycle — reported alongside, never instead of.** `studyPackStatus` keeps its current
   meaning and its current name (nothing breaks), but stops being the input to capability decisions.
   Surfaces use it for *messaging* — "regenerating", "last attempt failed, retry" — while capability
   drives what is *offered*.
3. **Entitlement — separate, and already correct.** `FeatureGateService` stays the single source of
   truth. Remove the plan gate from `NoteService:1539`; let the frontend compose
   `adaptivePracticeAvailable ∧ planIncludesAdaptive`, as `MePlanResponse:145` already supplies.

**Why not a persisted field.** Every input is already stored and already cheap to read.
`NoteListItemResponse` **already carries `quizCount` and `keyConceptCount`** (`:20-21`) plus
`studyPackId`, `studyPackStatus`, `generatedQuizId`, `generatedQuizQuestionCount` — the list payload
can already answer every capability question truthfully today. And `NoteService:359-372` proves
capability can change without generation, so a persisted flag would need invalidation hooks on paths
that have nothing to do with generation. **The repo does not prove derivation insufficient; it proves
derivation is already working where it is used.**

**Why not a giant boolean DTO.** The brief is right to be wary, and the repo shows the concrete cost:
`adaptivePracticeAvailable` already went wrong by absorbing entitlement (D4). Ship **only the
capabilities a surface actually consumes**, named for the artifact rather than the mode where the
artifact is shared — `hasQuizQuestions`, `hasKeyConcepts`, `hasTeacherQuiz` are three facts;
`canUseFlashcards` and `canUseMemorization` are the same fact twice.

**The payload asymmetry that must be closed.** Three DTOs answer the same question three ways:
`NoteResponse` gives derived booleans but no `keyConceptCount`; `NoteListItemResponse:20-21` gives
raw counts but no booleans; **`NoteCollectionItemResponse:7-23` gives neither** — only
`studyPackStatus`, `studyPackId`, `generatedQuizId`. That third gap is precisely why the Review Set
surface had to gate on the lifecycle string (`collection-exam.ts:19`) and therefore why it inherits
D2. **Adding the two counts to `NoteCollectionItemResponse` is additive, breaks nothing, and is the
single highest-leverage change in this audit.**

---

## 8. State-by-state UX recommendation

Governing rule: **branch on the artifact first, then use lifecycle for the message.** Today every
surface branches on lifecycle first, which is what hides working material.

| State | Learning surfaces should | Why |
|---|---|---|
| **Draft** | Show normally in Library / builder / Note Detail / search. Learning actions **visible but unavailable**, each naming its missing artifact, each offering **Generate Study Pack** as a real control. | Principle 1 holds; D7 is the gap — the copy already exists, the button does not. |
| **Generating** | **If a pack already exists (regeneration): keep every capability live**, add a non-blocking "Updating this Study Pack" note. If no pack exists (first generation): progress state + light polling, as today. Note Detail's disabled "Generating..." button (`:2948`) is the only affordance a strand-with-null-stamp note will ever show, so it needs an escape hatch. | Fixes half of D2, including the indefinite-strand class the sweeper declines to touch. First-generation behaviour is already correct. |
| **Failed, no pack** | Visible everywhere. Learning actions unavailable, reason shown, **Retry** offered on the destination surface, not only on Note Detail. Make it reachable by a Library filter. | D6, D7. |
| **Failed, pack intact** ⟵ *the only failure that has occurred* | **Every capability stays live.** Show a dismissible "last update didn't finish — retry" banner. Do **not** render as Draft on public pages; do **not** drop from Review Set exam pools. | D2. 7/7 of production's failures. |
| **Ready** | As today. | Correct. |
| **Ready, artifact empty** | Per-action: Flashcards unavailable if `keyConcepts` empty; Quick Review unavailable if `quiz` empty — and **enforced backend-side too** (D1). | Latent (production: 0 such packs), but it is the asymmetry that makes D1 a defect. |

**Mobile.** `ResponsiveActionButton` already carries `showTextOnMobile`
(`private-note-detail-page-client.tsx:2950-2959`) — an unavailable action needs its reason reachable
without hover. Prefer one inline line per surface over per-item badges.

**Badges.** Do not proliferate. `NoteStateBadge` (`note-state-badge.tsx:5-29`) already renders all
four lifecycle states and belongs in the Library list. It should **not** spread onto learning
surfaces: those need "what you can do and why not," which is a different sentence.

---

## 9. Cross-Note Review contract

Not designing the feature. The minimal rule it should consume, and the two constraints it inherits:

> **A Cross-Note Review session may include an owned Note if and only if the artifacts that session
> consumes exist on that Note's current Study Pack — evaluated from the artifact, never from
> `NoteStatus`. A Note whose last generation attempt failed but whose Study Pack is intact is
> eligible. A Note with no Study Pack is ineligible, and the surface says which artifact is missing
> and offers generation.**

Consumed as: `hasKeyConcepts` for concept review, `hasQuizQuestions` for question-based review — the
same members every other surface uses, with the session type naming its own minimum (Model C's
correct part, expressed through Model B's shared derivation). Entitlement and quota are resolved
separately via `FeatureGateService`.

Two inherited constraints the future feature must not trip over:

- **`ADR-001:461-470` is binding.** Existing `exam_question_pool` and
  `challenge_quiz_question_bank` rows stay **source-note-scoped** and must not enter cross-note
  pooling until a PR explicitly re-keys and audits compatibility. A Cross-Note Review that pools
  *questions* across notes needs its own ADR decision; one that assembles *sessions* from
  per-note-scoped material does not.
- **`ConceptHealth` is per-user-per-Study-Pack** (`docs/features/quiz.md:260`: *"never mix concept
  health across notes or Study Packs"*). Cross-Note Review must not become a back door to
  cross-pack mastery. Brief's Principle 4, and the repo already states it.

---

## 10. Migration / backend / frontend implications (architecture level only)

**Migration: none required, and none recommended.** No new column, no new table, no backfill. Every
input already exists. This is the strongest single argument for the recommended model.

**Backend.** Extract the derivation now inline at `NoteService:1491-1546` into one named collaborator
reading `(NoteEntity, StudyPackEntity, GeneratedQuizEntity?)`. Have `NoteStudyPackStatusResolver`
keep its current output (so `studyPackStatus` stays wire-compatible) while ceasing to be anyone's
capability input. Add the two artifact counts to `NoteCollectionItemResponse`. Remove the plan gate
from `:1539` and let the frontend compose it. Add the missing Quick Review guard (D1). Reconcile the
`LongExamService:1084-1090` pool with the primary lookup (D3) — with the caveat that reconciling in
the wrong direction could *narrow* Long Exam eligibility, which is a product decision, not a cleanup
(Q2 below).

**Frontend.** One shared module — generalise `lib/collection-exam.ts` rather than starting a new one;
it is already the right shape and already carries the explanatory comments. Resolve its internal
contradiction (D5). Give the guard components an action slot (D7). Switch
`public/library/[subject]/[slug]/page.tsx:139` and `public-seo-copy-cta.tsx:41` from the lifecycle
string to artifact presence.

**Deploy ordering.** Per `CLAUDE.md`'s rule: adding fields to `NoteCollectionItemResponse` and
dropping the plan gate from `NoteResponse` are **additive and backwards-compatible**, so no
coordinated deploy is needed — *provided the frontend does not start requiring the new fields in the
same release*. If any slice makes a new field load-bearing, that slice owes an explicit
deploy-ordering statement. No existing API form is removed, renamed or made required by this
recommendation.

---

## 11. Tests that would eventually be required

1. **Boundary matrix, backend** — the cross product of `{DRAFT, GENERATING, FAILED, GENERATED}` ×
   `{no pack, pack with empty quiz, pack with empty keyConcepts, full pack}`, asserting each derived
   capability. **The `FAILED` + full-pack row is the one that must exist**; it is 7/7 of production
   history and currently untested.
2. **Frontend/backend symmetry** — for each mode, a test asserting the backend rejects what the
   frontend hides. D1 exists precisely because this test does not.
3. **Resolver ≡ SQL** — one test pinning `NoteStudyPackStatusResolver:20-35` against
   `NoteLibraryRepositoryImpl:105-113` on the same fixtures (D8). Per `CLAUDE.md`'s guard rule, this
   is the kind of finding worth converting into something that cannot silently drift.
4. **Entitlement separation** — assert artifact capability is independent of `PlanType` (D4).
5. **Copy invariants** — the four rows of §2.3, including the `:359-372` retroactive heal.
6. **Real-request tests, per `CLAUDE.md`'s explicit rule** — any new or changed endpoint owes one
   `MockMvc` test with `.contentType(MediaType.APPLICATION_JSON)` and a body, not a direct handler
   call; client-side, a `lib/api-*.test.ts` pinning request shape, not a component test that mocks
   `lib/api`.
7. **Anti-no-op check, per `CLAUDE.md`** — every behaviour change in the eventual slice must move a
   test that executes it. `v0.116.0` and `v0.117.0` both shipped silent no-ops that passed the full
   suite.

---

## 12. Genuine owner decisions

Only questions the repository cannot answer.

**Q1 — Should a Note whose regeneration failed keep serving its previous Study Pack?**
*Recommendation: yes.* It is the entire §1.2 fix, and 7/7 of production's failures. But it is a
product call about whether a learner may study material the owner tried to replace. **If the answer
is no, most of this audit's headline finding becomes intentional behaviour and the scope shrinks to
messaging** — which is why this is Q1 and not a footnote. Note that answering "no" still leaves the
indefinite-`GENERATING` strand (D2's third variant) as a defect on its own terms, since that state
offers no retry and no terminal resolution regardless of the policy chosen here.

**Q2 — Should Long/Board Exam eligibility follow `StudyPackStatus.DONE` (backend, today) or artifact
presence (frontend, today)?** They disagree (D3) and the repo documents the disagreement without
resolving it. Choosing `DONE` narrows eligibility slightly; choosing artifact presence widens it.
Either is defensible; the current state is not.

**Q3 — Does "Quiz Ready" stay as a learner-visible label?** *(Production: 8 of 7,589 notes qualify;
one TEACHER account exists.)* Keep, rename, or scope it to admin surfaces only — a naming and
audience call, not an engineering one.

**Q4 — Should the Library gain a "Needs attention" filter for Failed/Generating (D6)?** A product
call about whether to surface generation failure as a first-class library concern. Production says
this is rare (0 currently failed) but 100%-concentrated in curator workflows.

**Q5 — Should a Draft Note be addable to a Review Set as a deliberate "planned topic" affordance, or
is today's permissiveness incidental?** The builder allows it (§1.4) and nobody has ever done it (0
of 6,872 items). Worth confirming as intentional before Cross-Note Review consumes the contract.

Deliberately **not** asked, because the repo answered them: whether Draft notes appear in the Library
(yes), whether public copies include the Study Pack (yes — §2.3), whether a `GENERATED` note can lack
a pack (no — §2.2), and whether exam pools are invalidated on regeneration (yes, `v0.143.0`).

---

## 13. Bottom line

**Recommended model: B — visible knowledge with derived action eligibility — implemented by
generalising the derivation the repo already has** (`NoteService:1491-1546`, `lib/collection-exam.ts`),
with Model C's per-surface minimums expressed as named members of that shared derivation rather than
as ten independent predicates.

**Why it fits NoteLib.** It is the only model that fixes the defect that has actually occurred: a
lifecycle-first rule hid a complete Study Pack on 7 of 7 production failures. It requires no
migration and no new persisted state, because every input is already stored and already on the wire.
It is the direction the codebase is already moving — the two best-behaved surfaces (Note Detail's
`keyConcepts.length > 0`, Progress's `hasKeyConcepts`) are already artifact-derived, and the two
worst (public note page, Review Set exam launch) are lifecycle-derived. And it preserves the
notes-first hierarchy: a Draft Note stays a first-class object that simply has fewer artifacts yet.

**Smallest coherent future slice** — one release, three or four items, which `CLAUDE.md`'s
verification-tier rule says keeps the gate at a single `advisor()` call:

1. Make the derivation artifact-first so an intact Study Pack stays usable through `GENERATING` and
   after a failed regeneration (D2) — gated on **Q1**.
2. Add `quizCount` and `keyConceptCount` to `NoteCollectionItemResponse` (additive) so the Review Set
   surface can stop gating on the lifecycle string.
3. Add the missing Quick Review backend guard (D1) — smallest item, clearest asymmetry.
4. Give the flashcards/memorization guards a real recovery action (D7) — the copy already promises
   it.

Items 1 and 2 are one coherent change; 3 and 4 are independent and individually small.

**Explicitly out of scope**, and to be restated in any prompt that follows: no change to
`PRIVATE`/`PUBLIC`; no new persisted status field; no hiding of Draft notes from Library, search,
Note Detail or the builder; no sixth exam mode; no change to `ConceptHealth`, mastery or readiness
semantics; no change to quotas or pricing; no automatic generation or regeneration; no spaced
repetition; no Library redesign; **no Cross-Note Review design**; and **no re-opening of the
`v0.143.0` exam-pool work or the two adjacent invalidation seams** (`deactivateShareLinksForNote`,
Challenge Quiz question bank) — both have their own Backlog rows and neither is traced here.

---

## 14. Housekeeping

Per `CLAUDE.md` kickoff step 8, **this file requires a row in `ROADMAP.md`'s Backlog Index at the
next release kickoff** — every `docs/claude-plans/` file must be indexed, and that scan is the only
enforced checkpoint against a planning document going unindexed across release cycles. **The row is
not added here**; this is the flag, not the action.

The file is intentionally left **untracked and uncommitted**, per this repo's convention for Stage 1
planning documents.

**Production facts in this document were read on 2026-09-13 and decay.** Per `CLAUDE.md`'s
snapshot-not-fact rule, re-read them before any of them reaches a plan, prompt, kickoff or release
note — in particular the 18 drafts, the 7 historical failures, the 8 generated quizzes, and the
zero draft notes in Review Sets.
