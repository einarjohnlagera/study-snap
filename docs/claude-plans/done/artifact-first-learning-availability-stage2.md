# Artifact-First Learning Availability — Stage 2 Implementation Plan

**Status: Stage 2 plan only. Nothing implemented, no migration, no code edited, no commit, no production
write.**
**Date written: 2026-09-13. Repo state: branch `releases/v0.144.0` (Released — its one item, admin
exam-pool invalidation, merged as PR #1385 at `e61ee2ce`). No release is currently open.**
**Builds on: `docs/claude-plans/note-visibility-learning-status-stage1.md` (Stage 1 audit, same day,
untracked, already carries a Backlog Index row — see Housekeeping). Every Stage 1 claim used below was
re-opened and re-read against current code in this pass, not copied forward.**
**Production reads: one, READ-ONLY `SELECT`, dated and labeled below, run because it changed a concrete
DTO-design decision (§C).**

---

## A. Revalidated repository truth

Every important Stage 1 finding was re-verified in this pass by opening the current file. All of it
holds. Line-number drift is minor everywhere (0 to +2 lines — ordinary incidental churn since Stage 1
was written a few hours earlier the same day) except where called out. Three things Stage 1 either
undercounted or didn't reach are flagged as **NEW** below; nothing Stage 1 claimed turned out to be
false.

| Area | Verdict | Evidence (file:line, this pass) |
|---|---|---|
| `NoteStatus` lifecycle (`DRAFT/GENERATING/FAILED/GENERATED`) | Holds | `entity/NoteStatus.java:4-7` |
| `StudyPackStatus` (`DONE/NEEDS_CONFIRMATION/FAILED`) | Holds, **+NEW**: in production, `NEEDS_CONFIRMATION` and `FAILED` are dead values — see below | `entity/StudyPackStatus.java` |
| `NoteStudyPackStatusResolver` (3 of 5 branches lifecycle-first) | Holds, exact | `service/NoteStudyPackStatusResolver.java:20-35` |
| SQL re-expression of the same rule | Holds, exact | `repository/NoteLibraryRepositoryImpl.java:105-113`, `:447-465` |
| Regeneration sets `GENERATING`, old pack untouched | Holds, exact | `StudyPackService.java:224` (`sourceNote.setStatus(NoteStatus.GENERATING)`) |
| Regen failure sets `FAILED`, old pack untouched, no early-return for it | Holds, exact | `StudyPackService.java:1314-1324` (`markNoteGenerationFailed`, guard at `:1315-1317` only skips `GENERATED`) |
| Recovery sweeper reaches the same code path | Holds, exact | `StudyPackService.java:1303-1305` → same `:1314-1324`; `GenerationRecoveryRowWriter.java:157-167` |
| Stranded `GENERATING` (null `generation_enqueued_at`) never swept | Holds, exact | `GenerationRecoveryService.java:103-118`, log message quoted verbatim |
| `NoteService.mapToResponse` is already artifact-first for `quickReviewAvailable`/`challengeQuizAvailable` | Holds, exact, and this is **the single most load-bearing fact in this plan** (see §C, §G) | `NoteService.java:1500,1537-1539` |
| `adaptivePracticeAvailable` conflates artifact + entitlement in `NoteResponse`, is entitlement-only in `MePlanResponse`/`BillingUsageSummaryResponse` (D4) | Holds, exact, **+NEW**: a third, independent, correct runtime gate exists in the actual session-start service and none of the three DTOs reflect it | `NoteService.java:1539`; `MePlanResponse.java:144-150`; `BillingUsageSummaryResponse.java:19`; `QuickReviewAdaptivePracticeService.java:158-213` |
| `NoteCollectionItemResponse` DTO gap (no artifact signal at all) | Holds, exact, and root-caused precisely: its `studyPackStatus` field is built from `NoteStudyPackStatusResolver.resolve(note.status(), studyPack != null)`, i.e. it is the **lifecycle** string, not the pack's own `StudyPackStatus` | `NoteCollectionService.java:3465-3481`, specifically `:3474` |
| Quick Review has no backend emptiness guard (D1) | Holds, exact | `QuickReviewSessionService.java:87-127`, no check before `:112` |
| Long Exam / Board Exam frontend-backend disagreement (D3) | Holds, but Stage 1 undercounted — **see NEW below** | `LongExamService.java:932-935,1080-1090`; `LongExamPlanSourceSampler.java:31-38`; `ChallengeQuizService.java:1672-1706` |
| Recent Sessions already artifact-first by accident | Holds, exact | `QuickReviewSessionRepository.java:216-229,248-267` — no `notes.status` join anywhere |
| Copy-path 4-row table (§2.3 of Stage 1) | Holds, exact | `NoteService.java:343-462`, retroactive heal at `:359-372` |
| Study Pack deletion demotes note to `DRAFT` with no downstream invalidation (O2) | Holds, exact, now named: `StudyPackService.deleteMine`, `:520-533` | `StudyPackService.java:520-533` |
| Public note page renders a regen-failed note as Draft (§4 row 17) | Holds, exact | `app/public/library/[subject]/[slug]/page.tsx:96,118,139,142` |
| Public copy CTA offers Generate when it would actually copy the pack | Holds, exact | `components/notes/public-seo-copy-cta.tsx:21,38,41` |
| `v0.143.0`/`v0.144.0` exam-pool invalidation on regeneration is already fixed | Holds, re-confirmed directly in `StudyPackService.java:905-928` (unconditional `refreshPool` calls at `:909-912`, run on every save including first generation, which is a documented no-op) | `StudyPackService.java:905-928` |

### NEW — three things Stage 1 either undercounted, didn't reach, or that change a design decision

1. **The Long Exam / Board Exam disagreement is not two-way, it is at least four-way, and it is worse
   for Board Exam than for Long Exam.** Stage 1 described "frontend excludes, backend includes." Full
   trace, this pass:
   - **Frontend gate** (four call sites, not one, and they are not even consistent with each other —
     see item 2 below): all read the Note-lifecycle string `studyPackStatus`.
   - **Backend primary lookup**, `LongExamService.findOwnedStudyPackForGenerationOrThrow`,
     `:932-935`: no `StudyPackStatus` filter **at all** — owner+id only. This is the opposite failure
     direction from the frontend: it is too permissive, not too strict.
   - **Backend eligible pool**, `LongExamService.java:1080-1090`: `StudyPackStatus.DONE`, exact.
   - **`LongExamPlanSourceSampler.java:31-38`**: looks up the primary *inside* the already-`DONE`-filtered
     pool; if the primary's own pack isn't `DONE`, the primary silently isn't there and
     `LongExamPrimarySourceNotEligibleException` fires — a **discovered-late** failure mode.
   - **Board Exam's equivalent** (`ChallengeQuizService.java:1672-1674`, identical `DONE` predicate) does
     **not** rely on this late discovery: it explicitly pre-checks pool membership at `:1704-1706` and
     throws a **different, fourth** exception shape, `InvalidBoardExamSourceException.sourceUnavailable()`.

     So today there are two genuinely different eligibility CONTRACTS across two axes: (a) frontend
     lifecycle-string vs. backend `StudyPackStatus.DONE` (the axis Stage 1 named), and (b) Long Exam's
     late-discovered-via-sampler failure vs. Board Exam's pre-checked failure (an axis Stage 1 did not
     name). **The DONE-based eligibility RULE is identical for both modes and is not in dispute** — only
     the frontend's representation of it, and the two backends' error-reporting shape, disagree. §G
     resolves axis (a). Axis (b) is deliberately left alone — see §L.

2. **The four frontend "start this mode" entry gates are byte-identical to each other, which is a gift
   for the fix.** `long-exam/page.tsx:349` (via `.studyPackId`/`.studyPackStatus`),
   `challenge-quiz/page.tsx:839`, `adaptive-practice/page.tsx:376`, `quick-review/page.tsx:371` all run the
   literal pattern:
   ```ts
   if (detail.studyPackStatus !== "STUDY_PACK_READY") {
     setError("Generate a Study Pack first.");
     return;
   }
   ```
   Three of these four modes (Quick Review, Challenge/Board, Long Exam) already have a correct,
   artifact-derived boolean sitting unused on the exact same `NoteResponse` object this code already
   holds (`quickReviewAvailable`, `challengeQuizAvailable` — both are already `hasGeneratedQuiz`,
   `NoteService.java:1537-1538`, confirmed artifact-only, no lifecycle and no entitlement). **This is not
   a design problem, it is an unwired connection.** §E specifies the four swaps precisely.

3. **Every `study_packs` row in production is `DONE`, and no code path has ever written anything else.**
   Grepped every `setStatus(StudyPackStatus...)`/`.setStatus(StudyPackStatus.DONE)` site in
   `backend/src/main/java` (10 call sites, listed in §C) — **all ten write `DONE`, zero write `FAILED` or
   `NEEDS_CONFIRMATION`.** Confirmed against production, read-only, today:

   ```sql
   select status, count(*) from study_packs group by status;
   -- DONE: 7599.  (no other rows)
   ```
   *(Production read, 2026-09-13, via Render MCP against `notelib-db-prod`, `dpg-d6tvb8fkijhs73fda4m0-a`.
   Read-only `SELECT`, zero writes. This is the one production read this plan needed — it settles §C's
   `studyPackDone` field design, below.)*

   **This does not mean the plan should collapse `studyPackDone` into "row exists."** `StudyPackStatus.FAILED`
   and `NEEDS_CONFIRMATION` are live enum values a future code path could legitimately start writing
   (the brief is explicit: "a mere Study Pack row is not automatically sufficient... retain existing
   validity requirements"), and the backend genuinely still gates Long/Board Exam and Adaptive Practice
   on `StudyPackStatus.DONE`, not on row presence. The correct read of this finding is narrower and still
   useful: **today, `hasQuizQuestions` and `studyPackDone` are empirically coextensive for every existing
   pack, but this is a fact about current data, not an invariant enforced anywhere in code** — so the new
   field must still be sourced from the real `status` column (§C, §D), not hard-coded as `studyPackId !=
   null`.

---

## B. Final invariant

> **A Note's learning-material availability is derived from the artifacts a learning action actually
> consumes — the Study Pack's `quiz`, its `keyConcepts`, a `generated_quizzes` row, and, for
> session-assembly eligibility, the Study Pack's own `StudyPackStatus` — never from the Note's
> generation lifecycle (`NoteStatus`, or the `studyPackStatus` string derived from it). An artifact that
> exists right now stays usable while a regeneration is in progress and after a regeneration fails.
> Lifecycle is reported alongside capability, for messaging, never substituted for it.**

Shorter form, for `AGENTS.md` (see §24): **never use Note generation lifecycle as a proxy for learning
artifact availability.**

---

## C. Capability model

Four primitive facts, all **derived, never persisted**, matching the brief's naming preference
(fact-named, not mode-named):

| Fact | Definition | Already exists on the wire today? |
|---|---|---|
| `hasQuizQuestions` | `studyPack != null && studyPack.quiz != null && !studyPack.quiz.isEmpty()` | Yes, as `NoteResponse.quickReviewAvailable`/`challengeQuizAvailable` (both already equal this, confirmed `NoteService.java:1537-1538`) and as `NoteListItemResponse.quizCount > 0` (`NoteService.java:1578`). **No** on `NoteCollectionItemResponse` — gap. |
| `hasKeyConcepts` | `studyPack != null && studyPack.keyConcepts != null && !studyPack.keyConcepts.isEmpty()` | As `NoteListItemResponse.keyConceptCount > 0` and as the raw `NoteResponse.keyConcepts` array (frontend already does `.length > 0`, e.g. `private-note-detail-page-client.tsx:3033`). **No** on `NoteCollectionItemResponse` — gap. |
| `hasTeacherQuiz` | `generatedQuizId != null` | Yes, everywhere it's needed (`NoteCollectionItemResponse.generatedQuizId`, `collection-exam.ts:11-13`). No change needed — §20 explicitly defers "Quiz Ready" naming. |
| `studyPackDone` | `studyPack != null && studyPack.status == StudyPackStatus.DONE` | **No, nowhere.** Every DTO that carries a pack today only carries the *lifecycle*-derived `studyPackStatus` string, never the pack's own `status` column. This is the field Long/Board Exam eligibility actually needs (§G) and it does not exist on any response type. |

**Where each is derived — decision for §7's eight questions:**

1. **Backend source of truth**: a new tiny stateless utility, `StudyPackArtifactFacts`
   (`backend/src/main/java/com/studysnap/backend/util/StudyPackArtifactFacts.java`), three static
   methods overloaded for `List<QuizItem>`/`List<String>`/`StudyPackStatus` inputs so it works against
   both `StudyPackEntity` (has getters for all three) and the lighter `StudyPackProgressView` projection
   (§D item 2) without forcing either to depend on the other's shape:
   ```java
   public static boolean hasQuizQuestions(List<QuizItem> quiz) { return quiz != null && !quiz.isEmpty(); }
   public static boolean hasKeyConcepts(List<String> keyConcepts) { return keyConcepts != null && !keyConcepts.isEmpty(); }
   public static boolean isDone(StudyPackStatus status) { return status == StudyPackStatus.DONE; }
   ```
2. **New collaborator, or generalize an existing one?** New, small, static, stateless utility — nothing
   existing fits without forcing a dependency in the wrong direction. `NoteStudyPackStatusResolver` stays
   untouched (§8: it is the lifecycle projection, not the capability source, and must not become both).
   `ProgressReportService.hasKeyConcepts` (`:660-661`) and `DashboardService.isQuizReadyForChallenge`
   (`:799-804`, which already independently checks `DONE + quiz non-empty` together — a sixth scattered
   instance found in this pass) are **not** migrated onto the new utility in this release. Migrating
   every one of the ~15 scattered call sites the verification pass found (`ProgressReportService`,
   `DashboardService` ×3, `StudyPackQuizMasteryService`, `QuickReviewSessionService`, `RetentionService`,
   and ~10 more) is a mechanical sweep with zero user-visible effect and real regression surface for no
   benefit — out of scope, consistent with "do not let this cascade" (§L).
3. **Which DTOs need primitive facts**: only `NoteCollectionItemResponse` needs new *named booleans*
   (`hasQuizQuestions`, `hasKeyConcepts`, `studyPackDone`) — it is the one DTO with no artifact signal at
   all. `NoteResponse` and `NoteListItemResponse` already carry either the raw arrays or counts (see next
   row); adding redundant booleans there would be exactly the "same fact, mode-named twice" anti-pattern
   the brief warns against. Both need exactly **one** new field: `studyPackDone` (neither exposes the
   pack's own `StudyPackStatus` today, and both feed a session-assembly eligibility check in §D/§E).
4. **Which facts are already counts**: `NoteListItemResponse.quizCount`/`keyConceptCount` (Integer,
   already correct, already artifact-derived, already free — `NoteService.java:1578-1579`). Frontend
   consumers of `NoteListItemResponse` should prefer these over `studyPackStatus` wherever a capability
   question is being asked (§E, `resolveCollectionScopedSourceNotes`).
5. **Frontend predicates that become shared helpers**: `collection-exam.ts` stays the one shared module
   (already the right shape, already has the explanatory comments per Stage 1 §1.5) — its two exported
   predicates are updated in place (§E) rather than replaced.
6. **Feature-specific eligibility that must stay separate**: Adaptive Practice's weak-concept/prior-session
   requirement (`QuickReviewAdaptivePracticeService.java:189-213`) is untouched — it is a real runtime
   requirement beyond artifact presence, not lifecycle leakage. Long Exam's timer/anchor/quota rules are
   untouched. Board Exam's dedupe-by-pack-id (`ChallengeQuizService.java:1675-1693`) is untouched.
7. **Avoiding capability caching**: nothing is persisted; every fact is computed at response-build time
   from data already loaded for that response (§D confirms zero new queries except one additive
   projection column, §D item 2). Stage 1's own O3 (`NoteService.java:359-372`, a note's capability set
   can increase hours later via an unrelated re-copy) is the standing proof this must stay derived.
8. **Preventing capability from re-absorbing entitlement**: `FeatureGateService` stays the only place
   entitlement is decided (confirmed in this pass — it is purely plan-tier, knows nothing about `quiz`/
   `keyConcepts`/`NoteStatus`, `FeatureGateService.java:20-57`). None of the four new/changed fields in
   this plan reference `PlanType`, `Feature`, or `featureGateService` anywhere in their computation. §H
   states the split explicitly per surface.

---

## D. Exact backend changes

### D1. New utility class

**File (new)**: `backend/src/main/java/com/studysnap/backend/util/StudyPackArtifactFacts.java`

```java
package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.StudyPackStatus;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * The three primitive artifact facts a learning action can require, derived from a Study Pack's own
 * fields -- never from {@code NoteStatus} or {@link com.studysnap.backend.service.NoteStudyPackStatusResolver}.
 * See docs/claude-plans/artifact-first-learning-availability-stage2.md.
 */
@UtilityClass
public class StudyPackArtifactFacts {
    public boolean hasQuizQuestions(List<QuizItem> quiz) {
        return quiz != null && !quiz.isEmpty();
    }

    public boolean hasKeyConcepts(List<String> keyConcepts) {
        return keyConcepts != null && !keyConcepts.isEmpty();
    }

    public boolean isDone(StudyPackStatus status) {
        return status == StudyPackStatus.DONE;
    }
}
```
**Why**: single owner for the three facts this release's DTOs need; matches the `@UtilityClass` pattern
already used by `NoteStudyPackStatusResolver`. **Tests**: `StudyPackArtifactFactsTest` — null list, empty
list, non-empty list for each of the three methods (table test, ~9 cases).

### D2. `StudyPackProgressView` / `StudyPackProgressProjection` — add `quiz`

**Current** (`backend/src/main/java/com/studysnap/backend/model/StudyPackProgressView.java`):
```java
public interface StudyPackProgressView {
    UUID getId();
    UUID getNoteId();
    UUID getOwnerUserId();
    String getSubject();
    List<String> getKeyConcepts();
    StudyPackStatus getStatus();
}
```
**Change**: add `List<QuizItem> getQuiz();`.

**Current** (`backend/src/main/java/com/studysnap/backend/repository/StudyPackRepository.java:185-197`,
both JPQL projections used by `NoteCollectionService`):
```java
@Query("""
        select s.id as id, s.noteId as noteId, s.ownerUserId as ownerUserId, s.subject as subject,
               s.keyConcepts as keyConcepts, s.status as status
        from StudyPackEntity s where s.ownerUserId = :ownerUserId
        """)
List<StudyPackProgressProjection> findProgressViewsByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);
```
and the sibling `findProgressViewsByNoteIdIn` above it (`:185` declares it; same select-list shape).
**Change**: add `s.quiz as quiz` to both `select` lists. `StudyPackProgressProjection` is a Spring Data
interface-backed projection over the same entity (`StudyPackEntity implements StudyPackProgressView`
directly, confirmed `entity/StudyPackEntity.java:26`), so no separate projection class needs editing —
only the interface and the two `@Query` select lists.

**Why**: this is the *only* query-shape change in the entire plan. `keyConcepts` and `status` are
already selected (so `hasKeyConcepts`/`studyPackDone` are free); `quiz` is not, and `NoteCollectionService`
needs it for `hasQuizQuestions`. Cost: one more `jsonb` column on an already-per-note query, same order
of magnitude as the `keyConcepts` column already being pulled — no new query, no new round trip.
**Compatibility**: additive interface method + additive select column. Nothing currently implements
`StudyPackProgressView` other than `StudyPackEntity` (which already has `getQuiz()`), so no other
implementer breaks.

### D3. `NoteCollectionItemResponse` — the DTO gap (§9)

**Current** (`backend/src/main/java/com/studysnap/backend/dto/NoteCollectionItemResponse.java`):
```java
public record NoteCollectionItemResponse(
        UUID noteId, String label, int position, String title, String subject, String courseProgram,
        String domainContext, String learnerLevel, String studyPackStatus, String studyPackId,
        String generatedQuizId, OffsetDateTime lastSessionCompletedAt, int dueConceptCount,
        List<String> dueConcepts, OffsetDateTime updatedAt
) {}
```
**Change**: append three fields (records are positional, so new fields go last to keep this additive for
any consumer still relying on ordinal deserialization, though the API is JSON so this is belt-and-braces):
```java
        ...
        OffsetDateTime updatedAt,
        boolean hasQuizQuestions,
        boolean hasKeyConcepts,
        Boolean studyPackDone
) {}
```
`studyPackDone` is boxed `Boolean` (nullable) so `null` unambiguously means "no pack," matching the
existing nullable `studyPackId`/`generatedQuizId` convention in this same record — `hasQuizQuestions`/
`hasKeyConcepts` are primitive `boolean` because "no pack" and "pack with no quiz" are both correctly
`false` for those two facts (no third state needed, unlike done-ness).

**Construction site** (`backend/src/main/java/com/studysnap/backend/service/NoteCollectionService.java:3450-3482`,
`toItemResponse`) — **before**:
```java
return new NoteCollectionItemResponse(
        item.getNoteId(), item.getLabel(), item.getPosition(), note.title(), note.subject(),
        note.courseProgram(), note.domainContext() == null ? null : note.domainContext().name(),
        note.learnerLevel() == null ? null : note.learnerLevel().name(),
        NoteStudyPackStatusResolver.resolve(note.status(), studyPack != null),
        studyPackId, generatedQuizId == null ? null : generatedQuizId.toString(),
        lastSessionCompletedAt, dueConcepts.size(),
        dueConcepts.stream().limit(DUE_CONCEPT_DISPLAY_LIMIT).toList(), note.updatedAt()
);
```
**after** (only the trailing three arguments are new):
```java
return new NoteCollectionItemResponse(
        item.getNoteId(), item.getLabel(), item.getPosition(), note.title(), note.subject(),
        note.courseProgram(), note.domainContext() == null ? null : note.domainContext().name(),
        note.learnerLevel() == null ? null : note.learnerLevel().name(),
        NoteStudyPackStatusResolver.resolve(note.status(), studyPack != null),
        studyPackId, generatedQuizId == null ? null : generatedQuizId.toString(),
        lastSessionCompletedAt, dueConcepts.size(),
        dueConcepts.stream().limit(DUE_CONCEPT_DISPLAY_LIMIT).toList(), note.updatedAt(),
        studyPack != null && StudyPackArtifactFacts.hasQuizQuestions(studyPack.getQuiz()),
        studyPack != null && StudyPackArtifactFacts.hasKeyConcepts(studyPack.getKeyConcepts()),
        studyPack == null ? null : StudyPackArtifactFacts.isDone(studyPack.getStatus())
);
```
`NoteStudyPackStatusResolver.resolve(...)` call is **left exactly as-is** — `studyPackStatus` keeps its
current lifecycle meaning on this DTO (§8). Only caller of the record's canonical constructor in
`src/main`, confirmed by grep (`grep -rn "new NoteCollectionItemResponse(" backend/src/main/java` → one
hit) — **that grep does not cover `src/test`**; adding three fields to a Java `record`'s canonical
constructor is a source-incompatible change for any test file that constructs one directly (as opposed to
via `toItemResponse`), so Codex should expect and fix compile errors in
`NoteCollectionServiceTest`/`NoteCollectionControllerTest`-shaped test fixtures, not just the production
call site.
**Tests**: extend whatever test covers `toItemResponse`/`getCollectionDetail` with the two critical
regression rows from §K1 (`GENERATING`+old pack, `FAILED`+old pack must both report `hasQuizQuestions=true`
when the old pack has quiz).

### D4. `NoteResponse` / `NoteListItemResponse` — add `studyPackDone` only

**`NoteResponse.java`**: append `Boolean studyPackDone` as the new last field (after `studyPackTitle`).
Both canonical-shaped constructors (the two compact ones at `:50-111` and `:113-173`) delegate to the
canonical one, so only the canonical record header and the two delegating calls need the extra `null`/
value argument — same low-risk pattern already used for `studyPackTitle` itself (added `v0.120.0`, per
its own doc comment) and `copiedFromNoteId`/`copiedFromUserId` etc. earlier in the same file.

**Construction site**, `NoteService.mapToResponse` (`:1491-1546`) — the `studyPack` parameter is already
in scope; add one line near the existing `hasGeneratedQuiz` derivation (`:1500`):
```java
boolean studyPackDone = studyPack != null && StudyPackArtifactFacts.isDone(studyPack.getStatus());
```
and pass it as the new trailing constructor argument. **Do not touch** `:1537-1539`
(`quickReviewAvailable`/`challengeQuizAvailable`/`adaptivePracticeAvailable`) — those stay computed
exactly as today; see §H for why `adaptivePracticeAvailable`'s conflation is fixed on the *frontend*
side, additively, rather than by changing this field's meaning.

**`NoteListItemResponse.java`**: same addition, append `Boolean studyPackDone` as the new last field.
Construction site `NoteService.mapToListItemResponse` (`:1549-1594`) already has `studyPack` (the full
`StudyPackEntity`, not the lighter projection) in scope — same one-line addition, sourced from
`studyPack.getStatus()`.

**Why not add `hasQuizQuestions`/`hasKeyConcepts` booleans here too**: both responses already expose the
raw facts a client needs (`NoteResponse.quiz`/`.keyConcepts` full arrays; `NoteListItemResponse.quizCount`/
`.keyConceptCount` integers) — adding booleans that are a one-line derivation of data already on the
wire is the redundant-boolean pattern §7 explicitly warns against. `studyPackDone` is different: no
existing field on either DTO exposes the pack's own `StudyPackStatus` in any form, derived or raw.

### D5. Quick Review backend guard (D1, §10)

**File**: `backend/src/main/java/com/studysnap/backend/service/QuickReviewSessionService.java`,
`startSession` (`:87-127`).

**Before** (`:87-101`):
```java
public QuickReviewSessionStartResponse startSession(String studyPackIdRaw, UUID userId) {
    UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
    StudyPackEntity studyPack = findAccessibleStudyPack(studyPackId, userId, true);

    QuickReviewSessionEntity existing = quickReviewSessionRepository
            .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                    userId, studyPackId, QuickReviewSessionMode.QUICK_REVIEW, QuickReviewSessionStatus.IN_PROGRESS)
            .orElse(null);
    if (existing != null) {
        return toStartResponse(existing);
    }
```
**After** — the guard goes **after** the existing-in-progress-session lookup and return, not before it.
This placement is deliberate and load-bearing, not incidental: `startSession` is also the *resume* path
(`existing != null` returns `toStartResponse(existing)` unchanged), and a guard placed before that lookup
would block resuming an already-in-progress session, not just block creating a new empty one — the exact
opposite of what D1's "no existing session row is touched" claim requires. Only the creation branch below
the existing-session check needs the guard:
```java
public QuickReviewSessionStartResponse startSession(String studyPackIdRaw, UUID userId) {
    UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
    StudyPackEntity studyPack = findAccessibleStudyPack(studyPackId, userId, true);

    QuickReviewSessionEntity existing = quickReviewSessionRepository
            .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                    userId, studyPackId, QuickReviewSessionMode.QUICK_REVIEW, QuickReviewSessionStatus.IN_PROGRESS)
            .orElse(null);
    if (existing != null) {
        return toStartResponse(existing);
    }
    if (!StudyPackArtifactFacts.hasQuizQuestions(studyPack.getQuiz())) {
        throw new QuickReviewNotAvailableException();
    }

    QuickReviewSessionEntity session = new QuickReviewSessionEntity();   // unchanged from here
```
**New exception** (`backend/src/main/java/com/studysnap/backend/exception/QuickReviewNotAvailableException.java`),
mirroring `ChallengeQuizNotAvailableException.java` exactly:
```java
package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuickReviewNotAvailableException extends AppException {
    public QuickReviewNotAvailableException() {
        super(
                "QUICK_REVIEW_NOT_AVAILABLE",
                "Quick Review is not available for this Study Pack yet.",
                HttpStatus.BAD_REQUEST
        );
    }
}
```
**Why after, not before**: an `IN_PROGRESS` session for an empty-quiz pack should not be created going
forward, but if one already exists in production (created before this guard shipped) it must still be
resumable/completable rather than orphaned — placing the guard after the existing-session lookup means it
only ever fires on the creation branch. Challenge Quiz's own guard (`ChallengeQuizService.java:1897-1903`)
is structurally different — it lives inside `toStartResponse`, a response-builder that validates whatever
session state it is handed, not an explicit "does an in-progress session already exist" branch like Quick
Review's — but the outcome it protects is the same: never turn an empty-quiz state into a hard block on a
session that is otherwise resumable. No existing session row is touched, deleted, or migrated.
**Compatibility**: response body of a successful start is unchanged; a request that previously silently
created a 0-question session now gets `400 QUICK_REVIEW_NOT_AVAILABLE` instead — this is the entire
point of D1, a behavior change on a path with zero known live instances: *(Stage 1's production read,
dated 2026-09-13, same day as this plan — not re-read here since the exact count is not decision-changing,
only confirmatory; if it were load-bearing for a go/no-go call it would need a fresh `SELECT`.)*
**Tests**: `QuickReviewSessionServiceTest` — new case, empty-quiz pack → `QuickReviewNotAvailableException`,
asserting no `QuickReviewSessionEntity` was persisted (mutation-test the guard per `AGENTS.md`'s
recurring-fixture-blind-spot warning — name which assertion the empty-quiz mutant would fail).
Controller-level: extend `QuickReviewControllerTest` (or create it if none exists targeting `/quick-review/start`)
with one real `MockMvc` request (`.contentType(MediaType.APPLICATION_JSON)`, real body) asserting `400`
+ the error code, per `CLAUDE.md`'s real-request rule.

### D6. `NativeQueryPostgresIntegrationTest` — no change required

No new `@Query(nativeQuery = true)` is introduced by this plan (D2's projection change is JPQL, not
native SQL; D3-D5 add no queries). `EXPECTED_NATIVE_QUERIES` (currently 44,
`NativeQueryPostgresIntegrationTest.java:164`) does not need bumping. Called out explicitly because this
is exactly the kind of thing a diff audit should verify rather than assume.

### D7. Long Exam / Board Exam — no predicate change

Confirmed in §A: the backend eligible-pool predicate (`StudyPackStatus.DONE`, identical in both
`LongExamService.java:1080-1090` and `ChallengeQuizService.java:1672-1674`) is **already correct** —
artifact-derived, `NoteStatus`-blind. Nothing in `LongExamService`, `LongExamPlanSourceSampler`, or the
Board Exam pool-assembly path in `ChallengeQuizService` changes in this plan. The fix is entirely on the
frontend representation of this already-correct rule (§E) plus the new wire field that lets the frontend
see it (`studyPackDone`, D3/D4). See §G for why touching the backend here would violate the brief's "do
not reconcile in the wrong direction" and "preserve the current distinction if legitimate" constraints —
there is no defect to fix on the backend side of this specific question.

---

## E. Exact frontend changes

### E1. The four quiz-mode entry gates (§A item NEW-2) — the highest-leverage change in this plan

All four are the same three-line swap. **Current predicate** (identical in all four files):
```ts
if (detail.studyPackStatus !== "STUDY_PACK_READY") {
  setError("Generate a Study Pack first.");
  return;
}
```

| File:line | New predicate | Source fact |
|---|---|---|
| `frontend/app/study-packs/[id]/quick-review/page.tsx:371` | `if (!detail.quickReviewAvailable) { ... }` | Already artifact-only (`hasGeneratedQuiz`) |
| `frontend/app/study-packs/[id]/challenge-quiz/page.tsx:839` | `if (!detail.challengeQuizAvailable) { ... }` | Same fact, already artifact-only |
| `frontend/app/notes/[id]/long-exam/page.tsx:349` | `if (!detail.studyPackId || !detail.studyPackDone) { ... }` | New `studyPackDone` field (D4); keep the existing `studyPackId` null-check since `studyPackDone` is `null` (not `false`) when there is no pack, and the two conditions read more clearly kept apart |
| `frontend/app/study-packs/[id]/adaptive-practice/page.tsx:376,580` | See E2 — this one is not a plain swap because of D4 | — |

Error copy stays `"Generate a Study Pack first."` at these four call sites — this is the *entry gate*,
reached before the user has any context about whether a pack previously existed; the richer
"regenerating / last update failed, previous material still available" messaging belongs on Note Detail
and the collection-launch surfaces (E4, E5), which the learner reaches *before* clicking into one of
these four pages. Changing this string is out of scope.

**Why this is low risk**: three of the four right-hand sides already exist on the wire today with the
correct semantics (`quickReviewAvailable`, `challengeQuizAvailable` — confirmed §A). Only the Long Exam
line depends on the new `studyPackDone` field landing first (§J deploy ordering).

### E2. Adaptive Practice entry gate — split artifact from entitlement (D4, §12)

**Current** (`frontend/app/study-packs/[id]/adaptive-practice/page.tsx:376` and the mirrored check at
`:580`):
```ts
if (!detail.adaptivePracticeAvailable) {
  setNote(detail);
  setError("Generate a Study Pack first.");
  setAdaptiveQuiz(null);
  return;
}
```
This is wrong in **both** directions today: a `FREE`-plan learner with a perfectly good Study Pack sees
"Generate a Study Pack first" (the real reason is plan, not artifact); a `PRO`-plan learner whose note is
`FAILED`-with-an-intact-pack also sees the same generic message even though nothing needs generating.

**After** — the page already fetches `usageSummary` (confirmed at `:268`,
`const currentPlan = usageSummary?.plan ?? getAuthUser()?.planType ?? "FREE";`, and `usageSummary` is the
existing `BillingUsageSummaryResponse`, which already carries the correct **entitlement-only**
`adaptivePracticeAvailable` boolean, `BillingUsageSummaryResponse.java:19`). Split the single check into
two:
```ts
if (!detail.quiz.length) {
  setNote(detail);
  setError("Generate a Study Pack first.");
  setAdaptiveQuiz(null);
  return;
}
if (usageSummary && !usageSummary.adaptivePracticeAvailable) {
  setNote(detail);
  setError("Adaptive Practice is not included on your current plan.");
  setAdaptiveQuiz(null);
  return;
}
```
`detail.quiz.length` reuses the raw array already on `NoteResponse` (§D4 — no new field needed for the
artifact half). The entitlement branch is guarded on `usageSummary` truthiness because that fetch can
still be in flight when this runs (matches the page's existing optional-chaining pattern at `:268`) — if
it hasn't resolved yet, the artifact check alone gates, and the backend's own
`featureGateService.checkFeatureAccess` call inside `QuickReviewAdaptivePracticeService.generateAdaptiveQuiz`
(`:162`) remains the authoritative enforcement regardless (§H).

**Do not remove or rename `NoteResponse.adaptivePracticeAvailable`** — a wire consumer may still read it
as "artifact AND entitlement," which stays literally true after this change (nothing about its backend
computation changes, §D4). This frontend page simply stops being the consumer that relies on it, in
favor of two more precise signals it already has in hand.

### E3. `frontend/lib/collection-exam.ts` — the shared module (§7 item 5)

**Before**:
```ts
export function canIncludeCollectionItemInPremiumExam(item: Pick<NoteCollectionItem, "studyPackStatus">): boolean {
  return item.studyPackStatus === "STUDY_PACK_READY";
}
```
**After**:
```ts
export function canIncludeCollectionItemInPremiumExam(item: Pick<NoteCollectionItem, "studyPackDone">): boolean {
  return item.studyPackDone === true;
}
```
`NoteCollectionItem`'s TypeScript type (generated/hand-maintained in `frontend/lib/api.ts` alongside the
other response shapes) gains `studyPackDone: boolean | null` to mirror D3's backend change — additive.

**`resolveCollectionScopedSourceNotes`** — **before** (`:38-54`):
```ts
export function resolveCollectionScopedSourceNotes(
  collection: NoteCollectionDetail,
  notes: NoteListItemResponse[],
  primaryNoteId: string,
  options: { requireStudyPackId: boolean },
): NoteListItemResponse[] {
  const eligibleNoteIds = new Set(getCollectionPremiumExamReadyNoteIds(collection.items));
  const noteById = new Map(notes.map((note) => [note.id, note]));

  return sortCollectionItemsByPosition(collection.items)
    .filter((item) => eligibleNoteIds.has(item.noteId))
    .filter((item) => item.noteId !== primaryNoteId)
    .map((item) => noteById.get(item.noteId))
    .filter((note): note is NoteListItemResponse => Boolean(note))
    .filter((note) => note.studyPackStatus === "STUDY_PACK_READY")
    .filter((note) => !options.requireStudyPackId || Boolean(note.studyPackId));
}
```
**After** — only line `:52` changes:
```ts
    .filter((note) => note.studyPackDone === true)
    .filter((note) => !options.requireStudyPackId || Boolean(note.studyPackId));
```
sourced from `NoteListItemResponse.studyPackDone` (§D4). This closes the exact gap the verification pass
found: `collection.items` (a `NoteCollectionItem[]`, now carrying real `studyPackDone`) and `notes` (a
separately-fetched `NoteListItemResponse[]`, also now carrying real `studyPackDone`) can no longer
silently disagree the way `studyPackStatus` values from two different fetches theoretically could,
because both now read the pack's own status rather than two independently-recomputed lifecycle strings.

**`requireStudyPackId` stays exactly as-is** — verified this pass (§ frontend agent's item 5) that its
three call sites (`interview-practice/page.tsx:142` → `false`, `long-exam/page.tsx:376` → `true`,
`challenge-quiz/page.tsx:632` → `true`) are a deliberate per-caller strictness knob, not a second
disagreeing predicate as Stage 1's phrasing of D5 suggested. Not touched — changing it is not needed to
fix the artifact-vs-lifecycle problem and risks the exact per-mode requirement the brief says to leave
alone ("if Long and Board legitimately have different eligibility contracts, preserve that distinction").

`canIncludeCollectionItemInExam` (`:11-13`, `Boolean(item.generatedQuizId)`) — **unchanged**, §20 defers
"Quiz Ready" naming and this predicate is already artifact-first.

### E4. `FlashcardsGuard` / `MemorizationGuard` — real action slot (D7, §13)

**Current** (`frontend/app/notes/[id]/flashcards/flashcards-page-client.tsx:18-27`, and the equivalent
`MemorizationGuard` at `frontend/app/notes/[id]/memorization/memorization-page-client.tsx:46-55`):
```ts
function FlashcardsGuard({ title, message }: Readonly<{ title: string; message: string }>) {
  return (
    <Card className="space-y-4 p-5 sm:p-6">
      <div className="space-y-2">
        <h1 ...>{title}</h1>
        <p ...>{message}</p>
      </div>
    </Card>
  );
}
```
Called today with copy that names an action ("Retry generation when you are ready" for `FAILED`,
"Generate a Study Pack…" for `DRAFT`, both confirmed exact at `:130-135`/`:137-142`) the component cannot
perform.

**Change**: add an optional action slot, reusing the exact retry mechanism Note Detail already uses
(`createStudyPackFromNote(note.id)`, confirmed at `private-note-detail-page-client.tsx:1433` inside
`handleGenerate`) — **do not duplicate generation business logic**, call the same `lib/api.ts` function
directly:
```ts
function FlashcardsGuard({
  title,
  message,
  actionLabel,
  onAction,
  actionPending,
}: Readonly<{
  title: string;
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  actionPending?: boolean;
}>) {
  return (
    <Card className="space-y-4 p-5 sm:p-6">
      <div className="space-y-2">
        <h1 ...>{title}</h1>
        <p ...>{message}</p>
      </div>
      {actionLabel && onAction ? (
        <ResponsiveActionButton type="button" onClick={onAction} disabled={actionPending} showTextOnMobile>
          {actionPending ? "Working..." : actionLabel}
        </ResponsiveActionButton>
      ) : null}
    </Card>
  );
}
```
Both call sites (`FAILED` and `DRAFT` branches, four total across the two pages) gain a small local
`retrying` state and an `onAction` that calls `createStudyPackFromNote(note.id)` then refetches the note
— the same shape as `handleGenerate` in Note Detail, but scoped to these two pages since they are
separate route components and cannot share React state with Note Detail across a navigation. This is
copy-paste of an existing ~15-line pattern, not new business logic — the actual generation call, quota
enforcement, and email-verification gate all stay entirely server-side and entirely in the one existing
endpoint. `ResponsiveActionButton`'s `showTextOnMobile` (already used at
`private-note-detail-page-client.tsx:2950-2959`) satisfies the "mobile must work without hover"
requirement (§13).

**Regeneration-in-progress / regeneration-failed with old `keyConcepts`**: per §B/§F, Flashcards and
Memorization must **stay open** in these two states (their only requirement is `hasKeyConcepts`, which is
already true). Confirm the pages' top-level gate (`hasGeneratedStudyPack = studyPackStatus ===
"STUDY_PACK_READY"`, `flashcards-page-client.tsx:68`) is replaced with a check against
`note.keyConcepts.length > 0` directly (the raw array is already on `NoteResponse`, no new field needed) —
**this is the actual fix for D2 on these two pages**, and it is a smaller change than it looks: the guard
copy strings for `FAILED`/`GENERATING` only need to render when `keyConcepts.length === 0`, not whenever
`studyPackStatus !== "STUDY_PACK_READY"`.

### E5. Note Detail (§14)

**Current gates** (`private-note-detail-page-client.tsx:986-991`):
```ts
const isStudyPackReady = studyPackStatus === "STUDY_PACK_READY";
const isGeneratingStudyPack = studyPackStatus === "GENERATING";
const hasGenerationFailed = studyPackStatus === "FAILED";
const canGenerateStudyPack = studyPackStatus === "DRAFT" || hasGenerationFailed;
const isDraft = !isStudyPackReady;
```
These five booleans currently drive: the quiz-action row (`:2947-2971`), the flashcard/memorization entry
point (`:3033`), and the failure card (`:2990-2997`, fixed copy, confirmed no dynamic reason text needed
— see below). All five are lifecycle facts and **stay exactly as they are** — they are correct and
necessary for *messaging* (§B: "lifecycle is reported alongside capability"). What changes is what they
gate:

**Before** (`:2947-2971`, collapsed):
```ts
{isGeneratingStudyPack ? <Disabled "Generating..." />
  : canGenerateStudyPack ? <Retry/Generate button />
  : <Quick Review / Challenge / Adaptive / Interview buttons />}
```
**After** — branch on capability first, lifecycle for messaging only:
```ts
{note.quiz.length > 0 || note.keyConcepts.length > 0 ? (
  <>
    {isGeneratingStudyPack || hasGenerationFailed ? (
      <UpdatingOrFailedBanner
        status={isGeneratingStudyPack ? "GENERATING" : "FAILED"}
        onRetry={hasGenerationFailed ? () => void handleGenerate() : undefined}
        retrying={generating}
      />
    ) : null}
    <QuizActionRow note={note} /* existing buttons, now rendered whenever artifacts exist */ />
  </>
) : isGeneratingStudyPack ? (
  <StudyPackGeneratingCard ... />        /* unchanged, first-generation-in-progress */
) : (
  <Retry/Generate button />              /* unchanged, DRAFT or FAILED-with-no-pack */
)}
```
`UpdatingOrFailedBanner` is a small new component (not a reuse of `StudyPackFailureCard`, which currently
renders unconditionally whenever `hasGenerationFailed` is true, §A: its copy — "We couldn't generate the
Study Pack this time... Your note is saved" — is already correct for a first-generation failure but
**wrong** for a regen-failure with intact material, since it never mentions the old material is still
there). Two copy variants, gated on `note.studyPackId != null`:
- No prior pack (first generation failed): keep `StudyPackFailureCard`'s existing copy verbatim.
- Prior pack exists (regen failed): *"The last update to this Study Pack didn't finish. Your previous
  Study Pack is still available below."* — non-blocking, dismissible-style banner rather than a full card
  replacing the action row, with the same `onRetry`/`retrying` wiring `StudyPackFailureCard` already has.
- `GENERATING` + prior pack: *"Updating this Study Pack…"* — no retry action (nothing has failed), no
  block.

**Flashcard/Memorization entry point** (`:3033`) — **before**:
```ts
{!isTeacherMode && !isGeneratingStudyPack && !hasGenerationFailed && !isDraft && note.keyConcepts.length > 0 ? (
```
**after** — drop the three lifecycle clauses, keep only what the surface actually requires:
```ts
{!isTeacherMode && note.keyConcepts.length > 0 ? (
```
This is the Note Detail half of D2/D7 — the entry link becomes available whenever key concepts exist,
regardless of `GENERATING`/`FAILED`, matching E4's change on the destination pages themselves.

**Stranded-`GENERATING` escape hatch** (§16, `:2948`): the disabled "Generating..." button only ever
renders in the branch above where **no artifact exists yet** (first generation) — see §I for the actual
escape-hatch design, which is a Note Detail-level addition (a manual "Check again" / "Start over" action
after a bound), not a change to this render branch's structure.

### E6. Public note page and public copy CTA (§15)

**Correction from the first draft of this plan, caught by `advisor()` before finalizing**: `note` (the
single-note object at `:139`) and `n` (the list items at `:96,118`) are **not the same DTO**. The single
note is built from `NoteService.getPublicBySeoPath` → `mapToPublicDetail`
(`NoteService.java:1163,1608-...`), which returns `PublicNoteDetailResponse` — a distinct record with no
`studyPackId` field at all (`dto/PublicNoteDetailResponse.java:6-24`: `studyPackStatus`, `summary`,
`keyConcepts`, `quiz` — no id). The related-notes lists (`allSubjectNotes`, `moreByCourseProgram`, from
`getServerPublicNotesBySubjectSlug`/`getServerPublicNotesByCourseProgram`) **are** `NoteListItemResponse`-
shaped, via `PublicNoteListResponse.items` (`dto/PublicNoteListResponse.java:9`) — so those two *are*
covered by §D4's `NoteListItemResponse.studyPackDone` addition, unchanged from the first draft.

**`PublicNoteDetailResponse` needs its own new field**, since it has neither `studyPackId` nor any other
pack-presence signal today: append `Boolean studyPackDone` (same pattern as the other three DTOs, §D3/§D4).
Construction site `NoteService.mapToPublicDetail` (`:1608-...`) already has the `StudyPackEntity` in
scope (it already reads `studyPack.getKeyConcepts()`/`.getQuiz()` for the existing `keyConcepts`/`quiz`
fields) — same one-line addition as D4: `studyPack != null && StudyPackArtifactFacts.isDone(studyPack.getStatus())`.

**`app/public/library/[subject]/[slug]/page.tsx:139`** — **before**: `const isDraft = note.studyPackStatus
!== "STUDY_PACK_READY";` — **after**: `const isDraft = !note.studyPackDone;`

**`:96,118`** (related-note filters, operating on `NoteListItemResponse`-shaped `n`) — **before**:
`n.studyPackStatus === "STUDY_PACK_READY"` — **after**: `n.studyPackDone === true`.

`hasVisibleStudyPackPreviews` (`:142`) keeps its existing `!isDraft && ... keyConcepts.length > 0 &&
note.quiz.length > 0` clauses unchanged — already artifact-first once `isDraft` itself is corrected, no
second change needed there.

This correction also resolves what would otherwise have been a real inconsistency with this plan's own
§N falsification point (a): the first draft's `!note.studyPackId` for the single-note check was a
presence-only predicate sitting next to a `studyPackDone`-based predicate everywhere else in this plan —
precisely the drift §N asks a reviewer to check for. Using `studyPackDone` uniformly on all four DTOs
(`NoteResponse`, `NoteListItemResponse`, `NoteCollectionItemResponse`, `PublicNoteDetailResponse`) removes
the inconsistency rather than requiring a reviewer to catch it later.

**`public-seo-copy-cta.tsx:41`** — **before**: `skipGenerate: studyPackStatus === STUDY_PACK_READY_STATUS`
— **after**: `skipGenerate: Boolean(studyPackId)` — matches `NoteService.copyNote`'s actual behavior
exactly (§A copy-path table: presence of a source pack, not `NoteStatus`, is what determines whether
`copySourceStudyPack` runs, confirmed `NoteService.java:404` guard). This directly closes the defect
named in the brief's §15: "The copy CTA must not tell the recipient generation is required when the
existing copy flow will actually copy a valid Study Pack."

### E7. `NoteStateBadge` and its two undocumented siblings (§8's "do not proliferate")

Not changed by this plan — §17/§18 explicitly say not to add capability badges, and the three
lifecycle-badge implementations found in this pass (`note-state-badge.tsx:5-29`,
`dashboard/study-pack-grid.tsx:35-59`, `private-note-detail-page-client.tsx:198-222`) all remain correct
for their purpose (lifecycle messaging) after this plan ships, since none of them are being asked to
answer a capability question. **Flagged as a real, pre-existing triplication worth a future
"consolidate the three lifecycle-badge implementations" backlog row (§L)** — out of scope here because
it is pure duplication cleanup with no behavioral defect, and the brief's scope discipline (§20, "do not
let this hitchhike") applies.

### E8. `collection-detail-page-client.tsx` — two consumer fixes named by the verification pass

Two call sites the frontend verification pass surfaced that are direct, user-visible instances of D2 and
were not individually named in Stage 1's matrix:
- **`getNextPlanAction` (`:1376`)**: picks the first item whose `studyPackStatus !== "STUDY_PACK_READY"`
  and tells the learner to "Generate Study Pack," even when that item is `FAILED`-with-a-prior-pack.
  **Fix**: skip items where `studyPackDone === true` — the plan's "next action" should never point at a
  note that already has usable material.
- **`getNoteExecutionStatus` (`:1353-1369`)**: labels any non-`STUDY_PACK_READY` item "Needs Study Pack."
  **Fix**: when `studyPackDone === true`, this is a lifecycle-message case (§B), not a capability case —
  render the existing lifecycle label (`Failed` / `Generating`) without the "Needs Study Pack" action
  framing, since the pack does not, in fact, need generating.

`getContinuePlanAction` (`:260`) is left as-is: it already offers "Generate Study Pack" specifically when
there is genuinely no `studyPackId`, which is correct per §B and was not misfiring in the trace.

---

## F. State matrix

`Y` = fully usable. `Y*` = usable, with a non-blocking lifecycle note. `N` = unavailable, with a named
missing artifact and a real action. `—` = not applicable / unchanged from today.

| State | Note Detail | Flashcards | Memorization | Quick Review | Challenge/Board | Adaptive Practice | Review Set exam launch | Long/Board Exam | Public Note | Public copy |
|---|---|---|---|---|---|---|---|---|---|---|
| Draft, no pack | Visible, Generate CTA | N, Generate CTA (E4) | N, Generate CTA | N | N | N | Excluded (`studyPackDone=null`) | Excluded | Draft render (unchanged — no pack, correct) | Offers Generate (correct — no pack to copy) |
| Generating, no pack | Progress card (unchanged) | N, "being generated" | N | N | N | N | Excluded | Excluded | Draft render | Offers Generate |
| **Generating, old pack** | **Y\*** "Updating…" banner, actions live (E5) | **Y** (E4) | **Y** | **Y** | **Y** | **Y** (artifact half) | **Included** (`studyPackDone=true`) | **Included** | **Full render** (E6) | **Copies pack** (E6) |
| Failed, no pack | N, Retry (unchanged) | N, Retry (E4) | N, Retry | N | N | N | Excluded | Excluded | Draft render | Offers Generate |
| **Failed, old pack** | **Y\*** banner + Retry, actions live (E5) | **Y** (E4) | **Y** | **Y** | **Y** | **Y** (artifact half) | **Included** | **Included** | **Full render** (E6) | **Copies pack** (E6) |
| Generated, full pack | Y (unchanged) | Y | Y | Y | Y | Y (+ entitlement, §H) | Included | Included | Full render | Copies pack |
| Generated, pack with no quiz | Y detail; quiz actions N per-action | Y (if keyConcepts) | Y (if keyConcepts) | N (D5 backend guard) | N (existing D1-symmetric guard) | N | Excluded from premium pool (`hasQuizQuestions=false` even if `studyPackDone=true` — §L notes this is latent, 0 instances in production) | Backend `DONE`-gated only, unaffected by empty quiz today — **flagged, not fixed, see §L** | — | — |
| Generated, pack with no keyConcepts | Y detail; flashcards/memo N | N | N | Y | Y | Y | Included (unaffected) | Included | — | — |

Bold rows are the two rows that change behavior. Every other row is unchanged from today's actual
behavior (not necessarily unchanged from what the *code currently claims* — e.g. "Draft, no pack /
Review Set exam launch: Excluded" is unchanged in outcome, but the field driving it changes from
`studyPackStatus` to `studyPackDone`).

**The "pack with no quiz, Long/Board Exam" cell is a genuine gap this plan does not close** — flagged
explicitly rather than silently left: the eligible-pool predicate is `StudyPackStatus.DONE`, and nothing
stops a future `DONE`-with-empty-quiz pack from being sampled into a Long/Board Exam. Not fixed here
because (a) it is latent by two independent, differently-dated pieces of evidence — this plan's own
production read (§A NEW-3, 2026-09-13) confirms every existing pack row is `DONE` and that no code path
has ever written any other `StudyPackStatus`, and Stage 1's separate same-day production read found zero
packs with an empty `quiz` — combined with the fact that `markNoteGenerated` writes `status`, `quiz`, and
`keyConcepts` together in one save (no code path produces `DONE` and empty-`quiz` independently), and (b)
fixing it means changing `LongExamService`'s eligible-pool query, which §11's "do not touch
source-note-scoped question-pool architecture unless strictly required" and "do not change sampling
semantics" both caution against for a condition with no known live or historical instance. Recorded as a
Backlog candidate (§L), not silently dropped.

---

## G. Long/Board canonical eligibility

> **A Note is eligible as a Long Exam or Board Exam source — whether as the primary anchor or as a pool
> member — if and only if it has a Study Pack whose own `StudyPackStatus` is `DONE`. `NoteStatus`
> (`DRAFT`/`GENERATING`/`FAILED`/`GENERATED`) plays no role in this predicate, directly or via the
> `studyPackStatus` lifecycle string. A Note carrying `FAILED` or `GENERATING` because of a stale
> regeneration attempt remains eligible if its existing pack is `DONE`.**

This is **already the backend rule**, verified identically in both `LongExamService.java:1080-1090` and
`ChallengeQuizService.java:1672-1674` (§A). This plan does not change it, does not narrow it, does not
widen it. What changes:
1. The **frontend's representation** of the rule moves from the lifecycle string (`studyPackStatus ===
   "STUDY_PACK_READY"`, which returns `false` for a `FAILED`-with-`DONE`-pack note) to the actual fact
   (`studyPackDone === true`, newly exposed) — §E1, §E3.
2. **A gap in the opposite direction is left alone, by design**: the backend *primary lookup*
   (`findOwnedStudyPackForGenerationOrThrow`, `:932-935`) does not itself filter on `StudyPackStatus`,
   relying instead on the downstream sampler to reject a non-`DONE` primary. This means the primary
   lookup, taken alone, is *more* permissive than the rule above — but the end-to-end outcome (whether an
   exam actually starts) is unaffected, because `LongExamPlanSourceSampler:31-38` still enforces `DONE`
   before any content is used. Tightening the primary lookup itself would be a pure error-experience
   improvement (turn a late sampler-thrown exception into an early, clearer one) with no behavioral
   effect — explicitly **out of scope** for this release (§L), because the brief instructs against
   touching this architecture "unless strictly required," and it is not required for the artifact-first
   invariant to hold.
3. **Board Exam's separate, earlier pre-check and separate exception shape**
   (`InvalidBoardExamSourceException.sourceUnavailable()`) is left exactly as it is — it already enforces
   the identical `DONE` rule, just with a different (arguably better) failure shape than Long Exam's.
   Unifying the two exception shapes is cosmetic and out of scope (§L).

**Frontend/backend agreement mechanism**: both now read the same fact by name (`studyPackDone`) from the
same source (the pack's `StudyPackStatus` column), computed by the same utility
(`StudyPackArtifactFacts.isDone`) on the backend and mirrored 1:1 as a boolean on the wire. There is no
shared code between frontend and backend (impossible across the language boundary) — agreement is
structural: both sides now name and source the identical fact, rather than the frontend re-deriving a
*different* fact (lifecycle) that happens to usually coincide with it.

---

## H. Entitlement separation

| Surface | Artifact capability (this plan) | Entitlement (unchanged, `FeatureGateService`) | Runtime eligibility (unchanged) |
|---|---|---|---|
| Quick Review | `hasQuizQuestions` (new backend guard, D5) | None — free on every plan | — |
| Challenge/Board Exam | `hasQuizQuestions` (existing guard, unchanged) | Monthly quota, `ChallengeQuizService` (unchanged) | — |
| Long Exam | `studyPackDone` (new field, D4; frontend E1) | `FeatureGateService.checkFeatureAccess(..., LONG_EXAM_SESSION)`, `LongExamNotAvailableException` (unchanged, `FeatureGateService.java:31`) | Timer/quota reservation (unchanged) |
| Board Exam | `studyPackDone` (same) | Same gate class, Pro-only (unchanged) | Multi-note dedupe (unchanged) |
| Adaptive Practice | `note.quiz.length > 0` (frontend, E2) | `usageSummary.adaptivePracticeAvailable` (frontend, E2) / `featureGateService.checkFeatureAccess(..., ADAPTIVE_QUIZ)` (backend, `QuickReviewAdaptivePracticeService.java:162`, unchanged, authoritative) | Prior completed session + weak concepts (`:189-213`, unchanged) |
| Flashcards / Memorization | `hasKeyConcepts` (E4) | None — free on every plan | — |

**Backend entitlement enforcement is not weakened anywhere in this plan.** Every entitlement check listed
above is a line this plan does not touch. The frontend changes in §E only ever make the *artifact*
half of a combined check more accurate; the entitlement half is either left as its own separate check
(Adaptive Practice, E2) or was never combined with artifact logic to begin with (Long/Board Exam's
`FeatureGateService` calls live entirely server-side and are invisible to the frontend predicates this
plan touches).

---

## I. Recovery behavior

| Case | Before | After |
|---|---|---|
| First generation in progress, no pack | Progress card, light polling (unchanged) | Unchanged |
| Regeneration in progress, old pack | Old pack hidden behind `GENERATING` gates everywhere (D2) | Old pack fully usable; Note Detail shows a non-blocking "Updating…" note (E5); destination pages (E4) unaffected by lifecycle at all |
| First generation failed, no pack | Retry offered on Note Detail only (D7 on Flashcards/Memorization) | Retry offered on Note Detail (unchanged) **and** on Flashcards/Memorization directly (E4) |
| Regeneration failed, old pack | Old pack hidden everywhere (D2, the 7/7 production case) | Old pack fully usable everywhere in this plan's scope; Retry still offered, non-blocking (E5) |
| Stranded `GENERATING`, no `generation_enqueued_at` (never swept, §16) | Disabled "Generating…" forever, no escape hatch | **Smallest safe fix, in scope**: see below |

**Stranded-generation escape hatch — the smallest safe fix, per §16's explicit instruction to keep the
backend sweeper out of scope and solve the user-facing dead end first.**

Backend: **no sweeper change.** `GenerationRecoveryService`'s decision to leave null-`generation_enqueued_at`
rows untouched is not altered — that decision protects against sweeping a row mid-write-before-the-stamp-lands
(a real race, not a bug) and fixing it correctly is the "broader recovery architecture" §16 says to keep
out. Instead: **add one new, narrowly-scoped manual recovery endpoint**, `POST
/notes/{id}/recover-stranded-generation`, on the existing `NoteController` (`@RequestMapping("/notes")`,
`backend/src/main/java/com/studysnap/backend/controller/NoteController.java:98`), callable by the note's
owner, that:
1. Loads the note (owner-scoped lookup, same pattern as every other owner-authenticated method on this
   controller), requires `status == GENERATING`.
2. Requires the note to have been in that state longer than the *same* `noteBoundMinutes` property the
   sweeper already uses (`StudySnapProperties.java:511`, currently 120) — read-only reuse of an existing
   config value, no new property.
3. Calls the **existing** `studyPackService.markNoteGenerationFailed(note)` (the exact single-arg
   overload the sweeper itself calls, `StudyPackService.java:1303-1305`) — zero new business logic, just
   a manually-triggered instance of the same recovery the sweeper performs for the rows it *can* reach.
4. Is idempotent by construction: if the note is not `GENERATING` when called (already recovered, or
   never stranded), or if it has not yet exceeded the bound, it throws a new named exception —
   `backend/src/main/java/com/studysnap/backend/exception/GenerationRecoveryNotEligibleException.java`,
   following the `ChallengeQuizNotAvailableException`/`QuickReviewNotAvailableException` (D5) pattern
   exactly: code `GENERATION_RECOVERY_NOT_ELIGIBLE`, `HttpStatus.CONFLICT` (409, matching
   `LongExamPrimarySourceNotEligibleException`'s use of the same status for "state doesn't support this
   action yet" rather than `BAD_REQUEST`), message `"This note isn't eligible for manual recovery right
   now."` — rather than double-writing.

Frontend: Note Detail's disabled "Generating…" render branch (`:2947-2948`, unchanged structurally per
§E5) gains a client-side timer — once `generationEnqueuedAt` is null-or-absent and creation is older than
the bound, show a **secondary, non-primary** "This is taking longer than expected — Check for a stuck
generation" link that calls the new endpoint, then refetches the note. This is deliberately a rare,
low-prominence affordance (the brief's own evidence: zero live instances at audit time, §A) — not a
redesign of the polling UX, not a new persistent banner, not surfaced anywhere but this one already-disabled
button's vicinity.

**Why a new endpoint rather than just re-wiring the existing generate/retry call — checked per `advisor()`
review, since reusing `createStudyPackFromNote` would drop a controller method, an exception class, and
a test.** `advisor()` asked whether `StudyPackService.startAsyncGenerationFromNote` (the method
`createStudyPackFromNote` ultimately calls) already tolerates being invoked on a note that is still
`GENERATING`. Read both `generationContextResolver.assertGenerationReady`
(`StudyPackGenerationContextResolver.java:33-39`) and `startAsyncGenerationFromNote`'s body
(`StudyPackService.java:178-227`) directly: **`assertGenerationReady` checks only multi-program Domain
Context ambiguity — it has nothing to do with generation-in-progress state** — and no other guard in
`startAsyncGenerationFromNote` inspects `sourceNote.getStatus()` before re-enqueuing. So the existing
generate path is not itself blocked by a stranded `GENERATING` note; it would happily start a *second*
generation, re-charging the Study Pack quota meter (`assertMonthlyStudyPackQuotaAvailable`, `:213`) on
top of whatever was charged for the original (still possibly-alive) stranded attempt. Reusing that path
as the recovery action would risk exactly the double-charge `advisor()` flagged.

**This is why the recovery endpoint calls `markNoteGenerationFailed`, never the generate path.**
`markNoteGenerationFailed` (`StudyPackService.java:1314-1325`, confirmed again in this pass) touches only
`note.status`/`generationFailureCode`/`generationFailureReason`/`generationFailedAt`/`updatedAt` — it has
no quota interaction of any kind. The endpoint therefore cannot double-charge, by construction, because
it never calls anything that charges. After it runs, the note is `FAILED` (not `GENERATING`), which makes
Note Detail's *existing* `canGenerateStudyPack`/Retry flow reachable again (today, `GENERATING` renders
only a disabled button with no click handler at all, `:2947-2948` — this is the actual reason a dedicated
endpoint is needed: nothing today can flip a stranded note out of `GENERATING` from the client at all).
The user still has to click Retry afterward, through the existing, already-quota-checked generate flow.

**The one remaining race `advisor()` asked to be checked — a genuinely-still-alive stranded task
completing after the recovery endpoint has already marked the note `FAILED`** — is already handled by
existing code, not new code: `StudyPackService.java:938-944`'s interlock (`if (saved == null)`, reached
when the async completion transaction finds the note is no longer `GENERATING`) already treats "someone
else resolved this note first" as the expected, quiet case — its own comment names a recovery sweep as
the exact scenario it exists for. The recovery endpoint produces precisely that state (note no longer
`GENERATING`) through the same write the sweeper already performs, so the async completion path's
existing interlock covers it with no new code. The narrower residual risk — a user clicks Retry while the
original stranded task is *still* alive and both eventually save — is a pre-existing risk of retrying
against a task that turns out not to be dead, identical today whether or not this endpoint exists; this
plan does not change it and the endpoint does not make it more likely (it only removes the UI dead end
that currently makes reaching Retry impossible on a stranded note in the first place).

**Why an endpoint and not just widening the sweeper's own query**: the brief explicitly separates "keep
the backend sweeper change out of scope" from "solve the user-facing escape hatch first, with a clearly
documented follow-up" — this endpoint *is* that escape hatch, deliberately shaped as the smallest
possible addition (one guarded call to an already-existing method) rather than a sweeper redesign.
**Follow-up, documented, not built here**: a proper fix would have the enqueue path itself write
`generation_enqueued_at` transactionally before any window where a crash could leave it null — that is
the "broader recovery architecture" §16 defers, and it belongs in `docs/product/ROADMAP.md`'s Backlog
Index as its own row (§L), not in this release.

---

## J. API/DTO changes

All changes below are **additive**. No field is removed, renamed, or made required.

| DTO | New field | Type | Nullable | Backward compatible? |
|---|---|---|---|---|
| `NoteCollectionItemResponse` | `hasQuizQuestions` | `boolean` | No (always a real fact) | Yes — new trailing field |
| `NoteCollectionItemResponse` | `hasKeyConcepts` | `boolean` | No | Yes |
| `NoteCollectionItemResponse` | `studyPackDone` | `Boolean` | Yes (`null` = no pack) | Yes |
| `NoteResponse` | `studyPackDone` | `Boolean` | Yes | Yes |
| `NoteListItemResponse` | `studyPackDone` | `Boolean` | Yes | Yes |
| `PublicNoteDetailResponse` | `studyPackDone` | `Boolean` | Yes | Yes (added after `advisor()` review found this DTO, not `NoteListItemResponse`, backs the single public-note read — §E6) |

**Deployment ordering — explicit, per `CLAUDE.md`'s rule that a release depending on a new field owes a
statement.** This release's frontend changes (§E1, §E3) **do** make `studyPackDone` load-bearing — the
Long Exam entry gate and the Review Set premium-exam predicate stop working correctly if the frontend
ships before the backend. Required order:

1. **Backend deploys first**, carrying D1-D7. This is safe standalone: the three new/changed DTOs are
   purely additive, every existing frontend consumer ignores unknown JSON fields, and no existing backend
   behavior changes except D5 (Quick Review's new 400 on an empty-quiz start — confirmed zero production
   instances, so this is not expected to affect any live request) and D7 (explicitly a no-op, nothing
   changes).
2. **Frontend deploys second**, carrying E1-E8, now reading the fields the backend already serves.
3. **Rollback safety**: if the frontend must roll back after the backend has deployed, it reverts to
   reading `studyPackStatus` again, which the backend continues to serve unchanged (§8, never removed) —
   a safe old-frontend/new-backend combination. The unsafe combination (new frontend against old backend,
   reading `studyPackDone` that doesn't exist yet) is the one this ordering statement exists to prevent —
   `studyPackDone` would deserialize as `undefined`/`null` on the frontend if the backend is old, which
   the Long Exam and Review Set predicates in §E1/§E3 would read as "no pack" for *every* note, making
   Long/Board Exam appear to have zero eligible sources. **Do not deploy frontend before backend for this
   release.**

No coordinated single deploy is required (per `CLAUDE.md`'s preference for "additive backend → deploy →
frontend consumes" over a forced simultaneous release) — this is the two-step, not one-step, form of that
pattern precisely because §E1/§E3 make the new field load-bearing, which the fully-additive case (most of
this plan) does not require.

---

## K. Tests

1. **`StudyPackArtifactFactsTest`** (new) — table test, null/empty/non-empty × three methods.
2. **Backend capability boundary matrix** (new test class, e.g. `NoteResponseCapabilityTest` or added to
   existing `NoteServiceTest`) — cross `{DRAFT, GENERATING, FAILED, GENERATED}` × `{no pack, pack+empty
   quiz, pack+empty keyConcepts, full pack}`, asserting `NoteResponse.studyPackDone`,
   `quickReviewAvailable`, `challengeQuizAvailable`, and the raw `quiz`/`keyConcepts` arrays for each of
   the 16 cells. **The two rows that must exist and must be new** (they are the entire point of this
   plan): `GENERATING + full prior pack` → capability fields all reflect the prior pack, not `false`;
   `FAILED + full prior pack` → same. Both were previously untested per the verification pass (no
   existing test targets this cross).
3. **`NoteCollectionServiceTest`** — extend `toItemResponse` coverage with the same two critical rows,
   asserting `hasQuizQuestions`/`hasKeyConcepts`/`studyPackDone` on `NoteCollectionItemResponse`.
4. **`QuickReviewSessionServiceTest`** — new case per D5: empty-quiz pack → `QuickReviewNotAvailableException`,
   no session persisted (assert via repository interaction, not just the thrown type — mutation-verify
   per `AGENTS.md`'s repeated warning about fixtures that pass for the wrong reason).
5. **New `MockMvc` real-request test** for `/quick-review/start` (or extend an existing controller test if
   one already covers this endpoint) — `.contentType(MediaType.APPLICATION_JSON)`, real body, asserting
   `400` + `QUICK_REVIEW_NOT_AVAILABLE`, per `CLAUDE.md`'s explicit rule that a changed endpoint owes one
   real-HTTP-shaped test, following the exact pattern in `NoteControllerTest.java:246-273`.
6. **`LongExamServiceTest`** — no new backend behavior to pin (§D7, §G item 2/3 explicitly out of scope),
   but confirm the existing `startSession_eligiblePoolCountsOnlyReadyStudyPacks` (`:1577-1608`) still
   passes unmodified — it is the existing regression guard for the rule this plan relies on but does not
   change.
7. **Frontend/backend symmetry**, per mode (§22 of the brief): for Quick Review, Challenge Quiz, Adaptive
   Practice, Long Exam, Board Exam — one test per mode asserting the frontend's new entry-gate predicate
   (E1/E2) evaluates to the same allow/deny outcome as the backend's actual guard, for the two critical
   states (`GENERATING`+pack, `FAILED`+pack). This is the closest this plan gets to a true end-to-end
   check without spinning up both stacks; implement as parallel unit tests asserting against the same
   fixture-shaped data on each side, named so a future drift is traceable to which side moved.
8. **`collection-exam.ts` frontend unit tests** — extend for `canIncludeCollectionItemInPremiumExam` and
   `resolveCollectionScopedSourceNotes` against `studyPackDone` instead of `studyPackStatus`, retaining
   the existing `requireStudyPackId` per-caller cases.
9. **Regeneration regression** (backend, matching §22's exact required scenario) — a fixture-driven test
   (no real LLM call needed): valid pack → `StudyPackService` regeneration path sets `GENERATING` →
   assert `NoteResponse`/`NoteCollectionItemResponse` for that note still report full capability. Repeat
   for the failure path (`markNoteGenerationFailed`) → assert capability still full **and** `studyPackDone`
   is still `true`, `studyPackStatus` (lifecycle) is `"FAILED"`, Retry is signaled as available.
10. **First-generation regression** — Draft/no-pack note begins generation → assert capability fields
    stay `false`/`null` throughout `GENERATING`, confirming this plan's fix to the regeneration case does
    not accidentally expose non-existent artifacts during first generation (the brief's explicit
    anti-regression instruction, §22).
11. **Entitlement separation** — parametrize `NoteResponse.studyPackDone`/`quickReviewAvailable`/
    `challengeQuizAvailable` computation tests across `PlanType.FREE/PLUS/PRO`, asserting no variance
    (they must not change with plan). Separately assert `QuickReviewAdaptivePracticeService.generateAdaptiveQuiz`
    still throws on `FREE` regardless of artifact state (unchanged, but worth a regression pin given E2
    touches the surrounding frontend logic).
12. **New `MockMvc` real-request test for the recovery endpoint (D-`§I`)** — this is a brand-new endpoint
    and, per `CLAUDE.md`'s rule (quoted twice already in this plan, including the `v0.119.0` precedent of
    a feature that could not make one successful request while 2,182 other tests passed), it owes its own
    real-HTTP-shaped test, not coverage folded into an existing suite by inference. Add
    `NoteControllerTest` cases (following the exact `.contentType(MediaType.APPLICATION_JSON)` pattern at
    `NoteControllerTest.java:246-273`) for `POST /notes/{id}/recover-stranded-generation`: (a) success —
    note `GENERATING` past the bound → `200`, note now `FAILED`, `markNoteGenerationFailed` invoked
    exactly once (`ArgumentCaptor`/`verify`, not just response-shape assertion); (b) `409
    GENERATION_RECOVERY_NOT_ELIGIBLE` when the note is not `GENERATING`; (c) `409` when it is
    `GENERATING` but has not yet exceeded `noteBoundMinutes`.
13. **Public copy regression** (§22's exact required scenario) — a source note with `NoteStatus.FAILED`
    and an intact pack: assert `copyNote`/`copySourceStudyPack` still produces a `GENERATED` copy with a
    full deep-copied pack (this path is untouched by this plan, §D — the test is new only because §A's
    copy-path table had no existing test pinning this specific FAILED-source case, and E6's frontend fix
    depends on the backend behavior it describes actually holding).

---

## L. Deferred findings

Not implemented in this release, listed here per the brief's requirement, not silently dropped:

1. **Long Exam's late-discovered-primary vs. Board Exam's pre-checked primary** (§A NEW-1, §G item 2) —
   error-UX inconsistency, not an eligibility defect. Candidate: unify on Board Exam's pre-check shape.
2. **`StudyPackStatus.DONE`-but-empty-quiz sampling into Long/Board Exam** (§F table note) — latent,
   zero production instances, zero code path that has ever produced one. Candidate: add an
   `AND jsonb_array_length(quiz) > 0` (or equivalent) clause to the two eligible-pool queries, in a
   release that is willing to touch `LongExamService`/`ChallengeQuizService` sampling code directly.
3. **Three independent lifecycle-badge implementations** (§E7) — `note-state-badge.tsx`,
   `study-pack-grid.tsx`, and `private-note-detail-page-client.tsx`'s own `stateChip`/`stateLabel` all
   duplicate the same 4-state → label/color mapping. Pure consolidation, no behavioral defect.
4. **~15 scattered inline `hasKeyConcepts`/`hasQuizQuestions`-shaped checks not migrated onto
   `StudyPackArtifactFacts`** (§C item 2) — `ProgressReportService`, `DashboardService` (×3),
   `StudyPackQuizMasteryService`, `QuickReviewSessionService`, `RetentionService`, and others. Mechanical,
   zero user-visible effect, real regression surface for a broad sweep with no benefit in this release.
5. **Dashboard's Priority-5 fallback recommends Quick Review without checking quiz length** (§20's O4,
   `DashboardService.java:135-152`, contrasted with the correct `isQuizReadyForChallenge` check at
   `:799-804` used two priorities earlier in the same method) — the brief explicitly allows folding this
   in "if trivial," and it is: the fix is calling `isQuizReadyForChallenge`-equivalent logic (or the new
   `StudyPackArtifactFacts.hasQuizQuestions`) before returning this fallback. **Left out of this release's
   core scope anyway**, because it is a recommendation-quality nicety on a latent-only condition (zero
   empty-quiz packs exist), not a defect this plan's invariant requires — including it would grow the
   release past the "3-4 items" sizing the brief's own tiering rule ties to a single-`advisor()`
   verification tier (§M). Flagged as a trivial one-line follow-up, not built here.
6. **The stranded-generation sweeper's null-`generation_enqueued_at` exclusion** (§I) — the escape-hatch
   endpoint in this plan is deliberately narrow; the real fix (write the timestamp transactionally before
   any crash window) is broader recovery-architecture work, explicitly deferred by §16 itself.
7. **"Quiz Ready" naming** (§20/§3 of the brief) — deferred, not touched.
8. **Recent Sessions `LONG_EXAM`/`ADAPTIVE_PRACTICE` clickable no-op** (Stage 1's aside, §20) — unrelated
   bug, not re-scoped here.
9. **Study Pack deletion's downstream invalidation gap** (O2, `StudyPackService.deleteMine:520-533`) —
   §20 explicitly says not to expand scope here unless required for the artifact resolver's own
   correctness; it is not (the resolver correctly reports "no pack" the instant `deleteMine` runs, since
   nothing in this plan changes what "no pack" means).
10. **Challenge Quiz per-user question bank staleness** and **`deactivateShareLinksForNote` default-scope
    gap** — both explicitly out of scope per `v0.143.0`/`v0.144.0` precedent and the brief's own
    instruction not to re-open them.
11. **Library "Needs attention" filter** (Q4, D6 fully) — explicitly declined for this release (§20's
    brief, Q4 resolved NO). The one minimum consistency fix this plan *does* make is narrower: see §17
    below.

---

## §17/§18 addendum — Library and Review Set, the one permitted consistency change

**Library** (§17): the `STUDY_PACK_READY` readiness filter predicate
(`NoteLibraryRepositoryImpl.java:105-113`) currently reads:
```sql
(
    n.status = 'GENERATED'
    or ((n.status is null or n.status = 'DRAFT') and exists (select 1 from study_packs sp where sp.note_id = n.id))
)
```
A `FAILED`-with-intact-pack note matches **neither** branch — it is invisible to the "Ready" filter even
though it has a usable pack, the exact lie §17 permits correcting. **Minimum fix**: replace the whole
predicate with the artifact-first form:
```sql
exists (select 1 from study_packs sp where sp.note_id = n.id and sp.status = 'DONE')
```
This is a strictly narrower dependency (no `NoteStatus` reference at all) and is provably equivalent to
the current predicate for every state *except* the one case being fixed (confirmed: `GENERATED` always
has a pack, §A; `DRAFT`-with-pack is the resolver's own fallback case and always has whatever pack
exists). **What this does not do, per Q4**: it does not add a new filter value, does not surface
`FAILED`-no-pack or `GENERATING` notes anywhere new — D6's broader gap (those two states are reachable
only under `ALL`) stays exactly as open as it was. This is filter-predicate correctness, not new IA.

**Second call site, found and checked per `advisor()` review — `NoteLibraryRepositoryImpl.java:317-325`**
(`findMostRecentlyUpdatedStudyPackReadyNoteId`) also references `STUDY_PACK_READY_PREDICATE` and is
rewritten by the same constant edit. Traced its only caller: `DashboardService.java:287`, inside the
private, owner-scoped "most recently updated ready pack" Dashboard lookup — **not** public/Featured
discovery. Public Featured eligibility (the `studyPackStatus = STUDY_PACK_READY` requirement named in
`AGENTS.md`'s Public Library Discovery section) is computed entirely separately, in
`PublicLibraryRepositoryImpl.java`, which does not reference `NoteLibraryRepositoryImpl`'s constant at
all — confirmed by grep (`Featured`/`isFeaturedEligible` only appear in `PublicLibraryRepositoryImpl.java`).
So this second call site's behavior change is in-scope, desirable (the same artifact-first invariant
applied to one more private Dashboard consumer), and carries no public-surface risk. Both call sites of
the rewritten constant are named here so neither is silently missed during implementation.

**Review Set** (§18): no membership restriction is added (Q5 locked YES, re-confirmed: 0 of 6,872 items
are drafts today, purely behavioral not enforced — unchanged by this plan). The only Review Set change in
this plan is the DTO/predicate fix in §D3/§E3/§E8 — eligibility for a specific *action* (premium exam
launch), never membership.

---

## M. Release slicing

**One release.** The brief's own criterion — split by deployable invariant, not by file count or
frontend/backend — points at a single invariant here: *artifact presence gates learning actions,
lifecycle never does*, expressed through one new backend fact (`studyPackDone`) and its consumers. There
is no natural second invariant to split off — D1-D8 from Stage 1 are eight symptoms of the same one root
cause, and §D/§E show every fix is either (a) wiring an *already-correct* existing fact to an
already-identified consumer (the four entry gates, the collection-exam predicates, the public-note gates
— the majority of this plan's LOC), or (b) one small additive field plus its three call sites. Splitting
these across two releases would mean shipping "the fact exists on the wire" in one release and "anything
actually reads it" in a second — pure sequencing overhead with no independent value delivered by the
first half, unlike the deploy-ordering split in §J (which is a within-release deploy step, not a
release-boundary split).

**Item count for the verification-tier gate (`CLAUDE.md`)**: this plan is larger than the brief's own
"3-4 item" sizing note in Stage 1's bottom line, because it also resolves D3's Long/Board Exam
frontend-representation gap and D7's Flashcards/Memorization action slot in the same pass — but every
item traces to the single invariant above, so it stays "one coherent change" rather than "many small
unrelated changes," which is the distinction `CLAUDE.md`'s sizing guidance actually cares about. §N
below states the resulting verification tier explicitly.

---

## N. Codex routing

**Per `CLAUDE.md`'s task-routing table: Codex is required.** This is unambiguous against every listed
criterion — it is a new-endpoint-plus-migration-shaped backend change (D5's new endpoint in §I, D1-D4's
DTO changes) **and** a multi-system change (frontend + backend together, §E depends on §D) **and** it
touches more than 5 files and more than ~100 LOC (§D alone touches 7 backend files; §E touches at least
10 frontend files). None of the "Claude Code implements directly" exceptions apply — this is not a
frontend-only ≤50 LOC addition, not an isolated 1-3-file bug fix with a single root cause (it is one root
cause with ~15 consumer sites), and it requires reading and applying `AGENTS.md`'s anti-drift rules
across many files (the tiebreaker explicitly names this condition as the Codex trigger).

**Verification tier**: per `CLAUDE.md`'s gate — this release does not move a permission/authorization
boundary (entitlement enforcement is untouched everywhere, §H), and it is not a first-of-kind cross-user
read. It **does** touch production-data-adjacent semantics (what artifacts a learner can act on) and adds
a new mutation-shaped endpoint (the stranded-generation recovery endpoint, §I) alongside a
`@Transactional` regeneration-adjacent read path. This matches the brief's own precedent from `v0.143.0`
item 2 (which the same repo tiered as **one scoped cold agent, falsification-framed**) closely enough to
recommend the same tier here, rather than the full three-agent test (this release does not meet any of
the three full-pressure-test triggers — no permission substrate, no first-of-kind cross-user read, no
money/quota semantics change) or the bare single-`advisor()` default (the D5 new endpoint and the
deploy-ordering dependency in §J are exactly the kind of thing a single summary call is weakest at
catching). **Recommend: one scoped cold agent, falsification-framed**, handed this plan's §D/§E/§J
sections and asked to disprove: (a) that `studyPackDone` is actually derived from `StudyPackStatus` and
not accidentally from presence-only in the final diff; (b) that the four entry-gate swaps in E1 use the
field each mode actually requires rather than being copy-pasted identically; (c) that the deploy-ordering
statement in §J is actually true of the shipped diff (i.e., no frontend file reads `studyPackDone`
without a fallback that degrades gracefully against an old backend, given `v0.136.0`'s precedent of a
release that got exactly this kind of ordering wrong).

**What happens next, not now**: after this plan is approved by the owner, write a Long-mode Codex prompt
using `docs/skills/codex-prompt-generator.md`. No existing file in `docs/codex-prompts/` covers this work
(confirmed this pass — the directory holds 12 files, none named for "artifact-first" or "note-visibility").
After Codex delivers, run `/audit-diff` before commit (required for every Long-mode prompt per
`AGENTS.md`), specifically checking the three falsification points above plus the standard error-state/
transaction/idempotency/load-on-refresh checklist. **Not done in this session per the brief's explicit
instruction** — no prompt is written here.

---

## Housekeeping

Per `CLAUDE.md` kickoff step 8, this file (`docs/claude-plans/artifact-first-learning-availability-stage2.md`)
requires a Backlog Index row in `ROADMAP.md` at the next release kickoff. **Not added here** — this is the
flag, not the action, matching the Stage 1 predecessor's own housekeeping note.

**Status of the three untracked sibling planning files found on disk, checked this pass so the next
kickoff scan does not have to re-discover it from scratch:**
- **`note-visibility-learning-status-stage1.md`** (this plan's own predecessor) — **already has a Backlog
  Index row**, added in the `v0.144.0` kickoff's docs commit (`786f46b9`, "docs: index two untracked
  planning artifacts found on disk"), which the current `ROADMAP.md` still shows as "Candidate for a
  future release, not yet scoped into one." **This Stage 2 plan is the scoping that row was waiting on**
  — the next kickoff should update that row to point at this file and record it as scoped rather than
  merely audited, not merely add a second, separate row for this file.
- **`cross-note-review-consolidation-stage1.md`** — checked `ROADMAP.md` and `RELEASES.md`: **no Backlog
  Index row exists for it, and it has not been kicked off as a release.** Untracked, uncommitted, not yet
  indexed.
- **`domain-context-biomedical-business-calibration-stage1.md`** — same check, same result: **no Backlog
  Index row, not kicked off.** Untracked, uncommitted, not yet indexed.

Neither of those two siblings was re-audited in this pass (out of scope for this task, which is Stage 2
for the note-visibility line only) — this note exists only to save the next kickoff's step-8 scan the
work of rediscovering that all three files are untracked and that two of the three still have zero
Backlog Index presence at all.

This file itself is left **untracked and uncommitted**, per the same convention its predecessor and
siblings follow.

---

## Final decision block

**ARTIFACT-FIRST LEARNING AVAILABILITY — IMPLEMENTATION PLAN**

**Architecture:** Derive learning-action availability from the Study Pack's own artifacts
(`quiz`/`keyConcepts`/`status`) at response-build time via one small stateless utility, and stop using
`NoteStatus`/the `studyPackStatus` lifecycle string as a capability input anywhere it currently is one.

**New persisted state:** NO

**Database migration:** NO

**Existing Study Pack usable during regeneration:** YES

**Existing Study Pack usable after failed regeneration:** YES

**Draft Notes allowed in Review Sets:** YES (unchanged, no new restriction)

**Library Needs Attention filter:** NO

**Quiz Ready rename:** DEFERRED

**Cross-Note Review:** OUT OF SCOPE (contract stated in Stage 1 §9, unchanged, not built)

**ConceptHealth changes:** NO

**Pricing/quota changes:** NO

**Backend entitlement enforcement preserved:** YES — every `FeatureGateService` call site in scope is
untouched; §H traces each surface's entitlement check explicitly.

**Long/Board eligibility:** A Note is exam-source-eligible if and only if it has a Study Pack whose own
`StudyPackStatus` is `DONE` — already the backend rule on both modes; this plan exposes it as
`studyPackDone` on the wire and repoints the frontend at it instead of the lifecycle string.

**Quick Review backend guard:** `startSession` throws `QuickReviewNotAvailableException` (400) when the
target Study Pack's `quiz` is empty, mirroring Challenge Quiz's existing `ChallengeQuizNotAvailableException`.

**Stranded generation:** smallest safe recovery — a new owner-callable `POST
/notes/{id}/recover-stranded-generation` endpoint that reuses the sweeper's own existing
`markNoteGenerationFailed(note)` method once the note has exceeded the same `noteBoundMinutes` bound,
surfaced as a low-prominence manual escape hatch on Note Detail's stuck-"Generating" button; the sweeper
itself is not redesigned (explicitly deferred, §I/§L).

**Recommended release count:** 1

**Codex required:** YES, per `CLAUDE.md`'s routing table (multi-system, >5 files, >100 LOC, requires
cross-file `AGENTS.md` anti-drift application).

**Highest-risk regression:** the deploy-ordering dependency in §J — if the frontend ships before the
backend, `studyPackDone` reads as absent everywhere, making the Long Exam entry gate and the Review Set
premium-exam predicate reject every note (fails closed, not open — a availability regression, not a
security one, but a real one, and exactly the class of mistake `v0.136.0` made in the opposite direction).

**Most important regression test:** the `FAILED + full prior pack` row of the backend capability boundary
matrix (§K2) — it is the one case with 7/7 production precedent (Stage 1) and the one this entire plan
exists to fix; every other row is either unchanged behavior or a latent (zero-instance) edge case.

**Deferred backlog items:** Long Exam's late-discovered-primary vs. Board Exam's pre-checked primary
(error-UX only); `DONE`-but-empty-quiz sampling into Long/Board Exam (latent); three duplicate
lifecycle-badge implementations; ~15 scattered inline artifact-presence checks not migrated to the new
utility; Dashboard's Priority-5 recommendation not checking quiz length; the sweeper's
null-`generation_enqueued_at` exclusion (real fix is transactional-write-before-crash-window, broader
than this release); "Quiz Ready" naming; Recent Sessions' `LONG_EXAM`/`ADAPTIVE_PRACTICE` no-op click;
Study Pack deletion's downstream invalidation gap; Challenge Quiz question-bank staleness and
`deactivateShareLinksForNote`'s default-scope gap (both pre-existing, separately tracked, explicitly not
re-opened here).

> Implementation should make generation lifecycle informative, never destructive to learning material
> that still exists.

**DO NOT IMPLEMENT YET.**
