Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/product/ROADMAP.md
- docs/features/professional-profile.md
- docs/features/quiz-session.md

---

## TASK

Extend Interview Practice to support up to 2 additional source notes (3 total), generating questions proportionally from each source and merging them into a single session — with no subject constraint, because a real job interview spans multiple technical domains.

## GOAL

Professional users preparing for a full-stack, cross-domain interview should be able to pull from multiple notes in a single session (e.g., "System Design" + "Behavioral" + "Algorithms") rather than being locked to one note. This makes Interview Practice viable for serious interview prep without requiring a single comprehensive note.

## CONTEXT

### Decision log
- **Option A chosen**: no subject constraint — unlike Multi-note Long Exam, Interview Practice does not require sources to share the same subject. A job interview routinely spans unrelated domains; enforcing subject parity would make multi-note less useful, not more.
- **Max 2 additional notes** (3 total sources). Caps prompt size, keeps session cost predictable, keeps the UI simple.
- **Multi-source orchestration is service-level**: `generateInterviewPracticeQuiz` in `OpenAiLlmStudyPackService` takes scalar inputs (title, summary, keyConcepts) and stays unchanged. The service calls it once per source note and merges results with cross-source deduplication — identical in structure to how `LongExamService.generateQuizForSources` works.
- **Quota unchanged**: one interview practice session consumed per start, regardless of source count.
- Single-source start (no `additionalNoteIds`) must continue to work exactly as before. This is a backward-compatible extension.

### Existing code to understand before implementing

Read these files before making changes:

- `backend/.../service/InterviewPracticeService.java` — current single-source start, `buildGeneratingSession`, `markReady`, `toStartResponse`
- `backend/.../dto/InterviewPracticeStartRequest.java` — currently `noteId` + `questionCount` only
- `backend/.../dto/InterviewPracticeStartResponse.java` — currently has no sourceNoteRefs field
- `backend/.../util/QuizSessionStateUtils.java` — `withInterviewPracticeState(quiz, subMode, softTimerSeconds)` builds the JSONB state; `extractInterviewSourceNoteRefs` does not exist yet
- `backend/.../service/LongExamService.java` — study this for the multi-source pattern: `resolveAdditionalStudyPackIds`, `resolveSourceNoteRefs`, `generateQuizForSources`. Interview Practice parallels this but without the subject-match check and without pool sourcing.
- `frontend/app/notes/[id]/interview-practice/page.tsx` — prestart UI; `handleStart` calls `startInterviewPractice`; `listNotes()` from `lib/api.ts` returns all user notes with `studyPackStatus` available for filtering

### Anti-drift rules from AGENTS.md

- Use `item.question()` (not `item.getQuestion()`) when reading `QuizItem` question text in Java — the accessor is an explicit method, not a Lombok getter.
- Analytics events use `AnalyticsEventType` enum — add `INTERVIEW_PRACTICE_STARTED_MULTI` if needed, or add a `sourceCount` metadata field to the existing `INTERVIEW_PRACTICE_STARTED` event (preferred — avoids a new enum value).
- Upgrade CTAs go through `getUpgradeCtas(currentPlan)`. Do not add new hardcoded upgrade copy in the frontend.
- Use `globalThis.localStorage`, `globalThis.setTimeout`, `globalThis.window`, etc. — not bare `window` or `self`. ESLint enforces this.
- Throw named exception subclasses — `InvalidInterviewPracticeRequestException` already exists; use it for all new validation failures with a descriptive message argument.
- Repeated string literals in the same class must be extracted to `private static final` constants.

---

## REQUIRED CHANGES

### Backend

**New DTO: `InterviewSourceNoteRef`**

```java
package com.studysnap.backend.dto;

public record InterviewSourceNoteRef(
        String studyPackId,
        String noteId,
        String noteTitle,
        int questionCount
) {}
```

**`InterviewPracticeStartRequest`** — add optional field:
```java
public record InterviewPracticeStartRequest(
        UUID noteId,
        Integer questionCount,
        List<UUID> additionalNoteIds   // nullable; null or empty = single-source (existing behavior)
) {}
```

**`InterviewPracticeStartResponse`** — add source refs field:
```java
public record InterviewPracticeStartResponse(
        UUID sessionId,
        String status,
        UUID noteId,
        UUID studyPackId,
        int questionCount,
        int currentQuestionIndex,
        int softTimerSeconds,
        QuizItem question,
        List<InterviewSourceNoteRef> sourceNoteRefs   // new — empty list for single-source sessions
) {}
```

**`QuizSessionStateUtils`** — add two methods (state JSONB key: `"interviewSourceNoteRefs"` — use a distinct key to avoid collision with Long Exam's `"sourceNoteRefs"`):

```java
// Store source note refs in interview session state
public Map<String, Object> withInterviewSourceNoteRefs(
        Map<String, Object> sessionState,
        List<InterviewSourceNoteRef> sourceNoteRefs
) { ... }

// Extract source note refs from interview session state; returns empty list if absent
public List<InterviewSourceNoteRef> extractInterviewSourceNoteRefs(
        Map<String, Object> sessionState
) { ... }
```

**`InterviewPracticeService`** — changes in `startSession`:

1. Parse and validate `additionalNoteIds` from request (see ERROR STATES).
2. Resolve `sourceNoteRefs` list — for each source (primary first, then additional), find the owned `StudyPackEntity`, verify it has a linked `StudyPackEntity` with `STUDY_PACK_READY` status, and compute proportional `questionCount`:
   - `baseCount = questionCount / sourceCount`
   - `remainder = questionCount % sourceCount`
   - Primary source gets `baseCount + remainder` questions; each additional source gets `baseCount`
   - Reject if `baseCount < 1` (i.e., `sourceCount > questionCount`)
3. Store `sourceNoteRefs` in the GENERATING session state via `withInterviewSourceNoteRefs`.
4. In the generation block, iterate sources in order. For each source:
   - Call `generationContextResolver.resolveForStudyPack(userId, sourceStudyPack)`
   - Build `disallowedQuestions` = existing study pack quiz questions + all previously generated questions (cross-source dedup)
   - Call `quizGenerationService.generateInterviewPracticeQuiz(title, summary, keyConcepts, disallowed, sourceQuestionCount, context)`
   - Deduplicate against accumulated set with `QuizDeduplicationUtils.uniqueQuestions`
   - Accumulate into merged list
5. Validate that `mergedQuiz.size() == questionCount` — throw `InvalidInterviewPracticeRequestException` if not.
6. `markReady`: extract existing `sourceNoteRefs` from current session state and re-apply them to the new state after calling `withInterviewPracticeState`.
7. `toStartResponse`: call `extractInterviewSourceNoteRefs(session.getSessionState())` and include in the response DTO.
8. Analytics: pass `sourceCount` in the metadata map for `INTERVIEW_PRACTICE_STARTED`.

**Note**: `StudyPackRepository` already has `findByOwnerUserIdAndNoteId`. Use `findByIdAndOwnerUserId` or equivalent to look up additional study packs by ID. Do not use a method that does not exist — check the repository before calling it. All additional-note ownership checks must use locking-safe reads consistent with the existing pattern.

### Frontend

**`lib/api.ts`**

- Update `InterviewPracticeStartResponse` type to add:
  ```ts
  sourceNoteRefs?: Array<{ noteId: string; noteTitle: string | null; studyPackId: string; questionCount: number }>;
  ```
- Update `startInterviewPractice` body type to add:
  ```ts
  body: { noteId: string; questionCount: number; additionalNoteIds?: string[] }
  ```

**`app/notes/[id]/interview-practice/page.tsx`**

- On mount (alongside the existing `getNote` + `getMe` calls), also call `listNotes()` from `lib/api.ts`. Filter the result to `studyPackStatus === "STUDY_PACK_READY"` and `id !== noteId`. Store as `availableNotes` state.
- Add `additionalNoteIds: string[]` state (empty by default).
- In the prestart card, below the "Session length" section and above the info grid, add an "Add more notes (optional)" section:
  - Label: "Add more notes (optional)" with a sublabel "Practice across multiple notes for a cross-domain session."
  - Render `availableNotes` as selectable chips showing note title (truncated at ~40 chars if needed). If there are no available notes, do not render this section.
  - Selecting a chip adds its `id` to `additionalNoteIds` (up to 2 max). If already selected, deselects it. Selecting a 3rd chip replaces the second selected one — or simply disable unselected chips when 2 are already selected (simpler, prefer this).
  - Do not show a loading spinner for this list — if it fails to load, render nothing silently (non-blocking).
- In `handleStart`, pass `additionalNoteIds` to `startInterviewPractice` (omit the field if empty).
- The `QuizGenerationOverlay` message needs no change — "Creating scenario questions from your notes" already works.

---

## ERROR STATES

**Backend — request validation (throw `InvalidInterviewPracticeRequestException` for all):**
- `additionalNoteIds` contains `null` entries → reject
- `additionalNoteIds` contains the primary `noteId` → reject ("Primary note cannot be included as an additional source.")
- `additionalNoteIds` contains duplicate UUIDs → reject ("Duplicate additional notes are not allowed.")
- `additionalNoteIds.size() > 2` → reject ("A maximum of 2 additional notes is allowed.")
- Any additional note does not belong to this user or has no `StudyPackEntity` (not ready) → reject ("One or more selected notes do not have a Study Pack.")
- `baseCount < 1` (more sources than questions) → reject ("Too many source notes for the selected question count. Reduce the number of notes or increase question count.")

**Backend — generation:**
- Per-source generation throws → catch `RuntimeException`, call `markFailed(session)` + save, rethrow (existing pattern — unchanged).
- Merged quiz is short (cross-source deduplication removed too many) → throw `InvalidInterviewPracticeRequestException("Could not generate enough unique interview questions.")` (same message as current).

**Frontend — note list loading:**
- `listNotes()` throws → catch silently, set `availableNotes` to `[]` — the note picker section simply won't render. Do not show an error for this.

**Frontend — session start:**
- Network/transient error → show existing error state ("Could not start Interview Practice."), phase = "error". Existing catch block covers this; no change needed.
- Backend returns 400/validation error → the error message from the API response is already surfaced by `parseApiResponse`. Existing catch block covers this.

---

## TESTING

**Backend:**
- `InterviewPracticeServiceTest` — test `startSession` with:
  - Single-source (null additionalNoteIds) — existing behavior unchanged
  - Two sources — generates from each, merges, sets questionCount proportionally
  - Three sources — same as above
  - More than 2 additional notes → throws `InvalidInterviewPracticeRequestException`
  - Additional note equals primary → throws
  - Additional note not owned by user → throws
  - Additional note not STUDY_PACK_READY → throws
  - `sourceCount > questionCount` → throws
- `QuizSessionStateUtilsTest` — test `withInterviewSourceNoteRefs` + `extractInterviewSourceNoteRefs` round-trip (non-null list, empty list, null state).

**Frontend:**
- The interview practice page has no existing tests. No new tests required for this change.

---

## DOCUMENTATION

- Update `RELEASES.md` under `v0.18.0 ✅ Shipped` with:
  ```
  - **Multi-note Interview Practice** — Professional Pro users can add up to 2 additional notes on the Interview Practice prestart screen; questions are distributed proportionally across sources with cross-source deduplication; no subject constraint (real interviews span domains).
  ```
- Update `docs/features/professional-profile.md` under "Interview Practice Mode → Setup → Source": change "single note" to "up to 3 notes (primary + 2 additional); no subject constraint required."
- Update `docs/features/professional-profile.md` under "Interview Practice Mode → Future direction": remove the "Multi-note Interview Practice (v0.15+)" item since it is now shipped.
- Do not update `AGENTS.md` — no new architectural rules introduced.

---

## CLEANUP

- The "Future direction" entry for multi-note Interview Practice in `docs/features/professional-profile.md` must be removed (shipped now). The "Structured interview templates" and "Open-ended / conversational evaluation" entries remain.
- No dead code to remove in service or DTO layer.

---

## ACCEPTANCE CRITERIA

**Single-source (backward compatibility)**
- [ ] Calling `POST /interview-practice/start` with `noteId` + `questionCount` and no `additionalNoteIds` field produces the same behavior as before this change — session starts, quota is consumed, response includes an empty `sourceNoteRefs` list.

**Multi-source — happy path**
- [ ] Calling `POST /interview-practice/start` with `noteId` + 1 additional note ID + `questionCount: 10` starts a session with 10 questions split proportionally: 5 from the primary note, 5 from the additional note.
- [ ] Calling with `noteId` + 2 additional note IDs + `questionCount: 10` starts a session with 10 questions: 4 from primary (includes remainder), 3 from each additional.
- [ ] Calling with `noteId` + 2 additional note IDs + `questionCount: 5` starts a session: 3 from primary (includes remainder 2), 1 from each additional.
- [ ] `InterviewPracticeStartResponse.sourceNoteRefs` is populated with the correct list (studyPackId, noteId, noteTitle, questionCount) for each source.
- [ ] On page refresh (navigate back to `/notes/:id/interview-practice`), an existing active multi-note session is returned (status IN_PROGRESS or GENERATING) with its `sourceNoteRefs` preserved.
- [ ] Quota is incremented by 1 (not by source count) per multi-note session start.

**Multi-source — validation**
- [ ] More than 2 additional notes → 400 with message about maximum.
- [ ] Primary note in additionalNoteIds → 400.
- [ ] Duplicate IDs in additionalNoteIds → 400.
- [ ] Additional note not owned by user or not STUDY_PACK_READY → 400.
- [ ] `additionalNoteIds.size() + 1 > questionCount` → 400 (e.g., 3 sources, 5 questions is valid; 6 sources, 5 questions is rejected — though 6 sources is already rejected by the max-2-additional rule).

**Frontend**
- [ ] On the prestart screen, if the user has other STUDY_PACK_READY notes, an "Add more notes (optional)" section appears with selectable chips.
- [ ] Selecting 2 chips disables the remaining unselected chips.
- [ ] Deselecting a chip re-enables others.
- [ ] Starting the session with 2 additional notes selected sends `additionalNoteIds` in the request body.
- [ ] If `listNotes()` fails, the note picker section is absent and the prestart screen is otherwise unaffected.
- [ ] If there are no other STUDY_PACK_READY notes, the "Add more notes" section does not render.

**Tests**
- [ ] Backend service tests pass for the cases listed in TESTING.
- [ ] `QuizSessionStateUtilsTest` round-trip test passes.

---

## OUTPUT

Return:
1. All changed files
2. Summary of what changed and why
3. Suggested commit message using the format from AGENTS.md:
   ```
   feat: multi-note Interview Practice (up to 3 sources, no subject constraint)
   - extend InterviewPracticeStartRequest with additionalNoteIds (nullable, max 2)
   - orchestrate per-source generation + cross-source deduplication in service
   - store sourceNoteRefs in interview session JSONB state
   - add note picker to prestart UI (optional, non-blocking load)
   ```
