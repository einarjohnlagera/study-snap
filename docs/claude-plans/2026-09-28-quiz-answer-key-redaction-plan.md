# Quiz answer-key redaction — design plan

**Status: Decision-complete for Long Exam, Challenge Quiz, Board Exam, Adaptive Practice, and Interview
Practice. Quick Review is NOT decision-complete — its own short design pass is still owed (see its section
below) before a Codex prompt.** Written 2026-09-28 during v0.163.0 item 1 scoping, revised twice: once after
3 rounds of `advisor()`, once after a cold, no-inherited-context Opus falsification pass whose own findings
then needed a further round of direct re-verification before being trusted (one of its corrections — the
`QuickReviewSessionSummaryResponse` framing — was itself over-corrected on the first attempt; see below).
Most claims in this document have been independently spot-verified against the actual code at least once, per
this repo's rule that a subagent's report is not itself a citation — but not all: the falsification pass's
dedup-filter claims (Long Exam, Challenge Quiz, and Adaptive's questions not overlapping the Study Pack's own
saved quiz — the reasoning that rules out a Quick-Review-style no-op for those three) and Adaptive's
completion-override claim are still subagent-sourced and listed as PR-time checks below, not yet independently
confirmed. One subagent citation was already caught wrong on direct re-verification (the Long Exam page file
— see that section) — treat the remaining unverified ones with the same suspicion, not as settled. Owner
decisions were made in conversation, not re-derived here.

## What this is, and what it explicitly is not

This closes the Backlog Index row "Quiz session wire payload already includes `correctIndex`/`correctIndices`/
`explanation` for unanswered questions, across every shared quiz mode" (found 2026-09-27 during `v0.162.0`'s
H5 diligence) — now expanded, see below, to include a live exploit found in Interview Practice during this
item's own investigation.

**⚠️ This is a practice-integrity fix, not a security boundary, and the release notes must say so plainly.**
`docs/features/quiz.md:22,31` already documents, correctly, that a note/Study Pack owner's own answer key is
"already present in the client payload" via `GET /notes/{id}` (`NoteResponse`) and `GET /study-packs/{id}`
(`StudyPackResponse`) — **both stay unredacted by owner decision, 2026-09-28.** A sufficiently motivated
learner can always read their own note's answer key by visiting the Note/Study Pack page instead of the
intended quiz-session flow. This plan makes the *intended* flow correct and closes the *accidental* exposure
(devtools mid-quiz today shows every answer, unconditionally, with zero effort) — except for Quick Review,
where owner decision 2026-09-28 is to build a genuinely separate data path (see below) specifically because
that mode's leak runs entirely through the very channel this paragraph says stays open.

## Owner decisions, 2026-09-28 (updated after the falsification pass)

1. **Practice sessions redact; content/authoring views do not.** In scope: Quick Review, Long Exam, Board
   Exam, Adaptive Practice, Interview Practice (now confirmed a SEPARATE stack, not an Adaptive sub-mode at
   the code level — see below), Challenge Quiz. Out of scope, unchanged: `NoteResponse`, `StudyPackResponse`,
   `PublicNoteDetailResponse`, DOCX export's `WITH_ANSWERS` mode.
2. **All 6 practice-session surfaces get redaction/locking** (Interview Practice added as its own item after
   the falsification pass found it has a live, already-shipping exploit — see Item 6 below).
3. **Quick Review: build a genuinely separate, quiz-less note fetch, rather than dropping it from scope —
   and this is the LEAST-protected of the 6 surfaces today, not the most, despite being the mode this item
   was originally found from.** Confirmed by direct re-verification: `QuickReviewSessionService.startSession`
   (`:121`) does `session.setSessionState(null)` — **the Quick Review session has never stored a quiz at all,
   ever.** The frontend's entire quiz array — including the answer key `QuizChoiceList` renders — comes from
   `getNote(noteId)` (`quick-review/page.tsx:371,417`), the exact `NoteResponse` endpoint decided to stay
   unredacted. (A separate `getMyStudyPack` call at `:388` exists only as a redirect fallback when the page is
   loaded with a Study Pack id instead of a Note id — confirmed it is not a second content-rendering source.)
   Redacting the (nonexistent) session quiz would be a pure no-op. The only real fix: give the Quick Review
   page its own fetch that returns note metadata plus an answered-state-aware quiz (not the full
   `NoteResponse`), while `GET /notes/{id}` itself — used by Note Detail — stays exactly as it is today.
   **⚠️ This also means the original kickoff claim — "`/progress` returns a type carrying no quiz-item data" —
   was actually TRUE, for an uglier reason than claimed: there is no quiz-item data anywhere in the session to
   carry.** Its case for redacting Quick Review's session responses never held; only its "backend-only pitch"
   history and its implied "this protects the leak" conclusion were false. Do not re-flag the response type
   itself as a defect when scoping the new fetch.
4. **Challenge Quiz / Board Exam's post-completion answer review: widen `ChallengeQuizSessionResponse`** to
   carry the reveal data directly, rather than having the frontend make a second call to `getSessionReview`
   after completing. Keeps `QuizAnswerReview` working off one response call, no extra round trip. (The
   `getSessionReview` route's shared/adopted-pack access guard was flagged as unverified by the falsification
   pass — since this decision avoids relying on it for the main flow, that check is now lower-priority, but
   still worth a quick look before assuming `getSessionReview` is safe to leave as a manual "review this old
   session again" entry point.)

## Channel inventory (backend DTOs carrying `QuizItem`/`sessionState`, and their per-mode disposition)

**⚠️ CORRECTED 2026-09-28 (first pass, before falsification):** "LongExamService serves both Long Exam and
Board Exam" was WRONG. `QuickReviewSessionMode` has exactly 4 values (`QUICK_REVIEW`, `CHALLENGE`, `ADAPTIVE`,
`LONG_EXAM` — no `BOARD_EXAM`). Board Exam is a `mode` STRING discriminator
(`ChallengeQuizService.MODE_BOARD_EXAM = "board_exam"`) served entirely inside `ChallengeQuizService`.

**⚠️ CORRECTED 2026-09-28 (falsification pass):** "Adaptive Practice (incl. Interview Practice sub-mode)" was
WRONG at the code level. Interview Practice is `InterviewPracticeController`/`InterviewPracticeService` — a
fully separate stack with its own DTOs (`InterviewPracticeStartResponse.question`,
`InterviewPracticeAnswerResponse.nextQuestion`, both full `QuizItem`s, keys included) and its own already-live
per-answer round trip (`answerQuestion`, `InterviewPracticeService:168-211`). `QuickReviewAdaptivePracticeService`
actively filters interview sessions OUT (`isInterviewSession`, checked at multiple call sites). `EXAM_MODES.md:41`
documents Interview Practice as `ADAPTIVE + subMode: "INTERVIEW"` at the *product* level, which is where the
original plan's confusion came from — the product framing and the code's actual service boundary don't match.

| Mode | DTOs | Progress/answer-write safety | Disposition |
|---|---|---|---|
| **Quick Review** | `QuickReviewSessionStartResponse`, `QuickReviewSessionResponse` — confirmed to **never carry a quiz at all** (`sessionState` is `null` from `startSession` onward; no `QuizSessionStateUtils.withQuiz` caller exists in this service) | n/a — nothing to redact here; the leak is entirely via `NoteResponse` | **Build a new, separate quiz-less fetch for this page** (owner decision 3 above) — this is NOT a response-redaction task like the other modes |
| **Long Exam** | `LongExamStartResponse`, `LongExamSessionResponse` (Long Exam only, confirmed) | **SAFE** — `saveProgress` (`:402-443`) merges only the single answered field via `QuizSessionStateUtils.withSelectedChoice`/`withSelectedMultiChoice`/`withSelectedIdentificationAnswer`, never trusts a client-supplied full map | **REDACT unconditionally, always.** **⚠️ CORRECTED after this plan's own first-attempt file citation was wrong** — the real page is `frontend/app/notes/[id]/long-exam/page.tsx` (1489 lines), NOT `frontend/app/study-packs/[id]/long-exam/page.tsx` (a 69-line pure redirect stub the first draft grepped by mistake, returning a false "zero matches"). The real page DOES pass `correctIndex`/`correctIndices` into `QuizChoiceList` (`:1300-1301`) with `revealAnswer={false}` (`:1280,1292,1305`) — same pattern as Challenge Quiz: data present in props, never rendered. Disposition unchanged (always redact matches the UI already never showing it), but a Codex prompt must target the real page, and the component must tolerate an `undefined` `correctIndex` post-redaction. No completion review screen exists (`LongExamMasteryReportResponse` has no quiz field) — no reveal-timing decision needed. |
| **Challenge Quiz + Board Exam** | `ChallengeQuizStartResponse`, `GenerateMoreChallengeQuizResponse`, and (per owner decision 4) a widened `ChallengeQuizSessionResponse` | **SAFE** — `mergeSessionState` (`:2064-2089`) is an explicit allowlist; `sanitizeSessionStateForClient` (`:2049-2062`) already strips the quiz key from the `sessionState` copy (so this mode leaks the key ONCE, via the typed `quiz` field, not twice — corrected from the original finding) | **REDACT mid-session** (matches `revealAnswer={false}` at all 4 call sites, `challenge-quiz/page.tsx:2293,2312,2333,2359`); **REVEAL at completion via the widened response** — `QuizAnswerReview` (`challenge-quiz/page.tsx:2651,2880`) currently reads the reveal straight from the same (soon-to-be-redacted) session object and would otherwise silently show no answers post-completion |
| **Adaptive Practice** (excl. Interview) | `QuickReviewAdaptiveQuizResponse` | **No per-answer round trip exists today** — only start/complete/forfeit, confirmed by reading `AdaptivePracticeController.java` in full | **REDACT, reveal per-answer** — needs the round trip built fresh. **⚠️ Completion integrity gap, found by falsification, must be fixed as part of this item, not left standing:** `completeAdaptiveSession` accepts client-submitted `selectedChoices`/`selectedMultiChoices` in the complete request and these **override** stored server-side selections (`QuickReviewAdaptivePracticeService:798-803`); `correctConceptNames`/`correctAnswers` are otherwise only bound-checked, not derived. Any answer-lock built for the per-answer round trip is decorative unless completion scores from the server's own locked selections and ignores the request body's selection maps. |
| **Interview Practice** (Item 6, newly added) | `InterviewPracticeStartResponse`, `InterviewPracticeAnswerResponse` | **UNSAFE, live today — directly re-verified, not taken from a subagent report.** `InterviewPracticeAnswerRequest` carries a client-supplied `questionIndex` (not server-owned/auto-advanced); `answerQuestion` (`:168-211`) validates only that the index is in range, then unconditionally writes via `withInterviewAnswer` with no re-answer guard, generating a fresh LLM critique (which reveals correctness via `verdict`/`rationale`) on every call; `buildReport` (`:454-469`) scores whichever selection is currently stored per index at completion time. Submit-wrong-then-resubmit-correct is real and counts today. | **REDACT + LOCK.** This is the learner's own practice record (an Interview Readiness Report) — same practice-integrity class as the other 5 surfaces, not a cross-user exposure, so treat it as part of this item's normal ship cadence rather than a separate hotfix unless the owner decides otherwise. Reveal per-answer (matches existing critique UX), lock the first answer per question, same pattern as Quick Review/Adaptive below. |
| `QuizSessionReviewResponse` | `NoteController.getQuickReviewSessionReview`/`getChallengeQuizSessionReview` | n/a | **CONFIRMED DELIBERATE REVEAL, CLOSED, both implementations.** `QuickReviewSessionService.getSessionReview:419` and `ChallengeQuizService.getSessionReview:1228` both explicitly throw a "review not available" error when `completedAt == null` — unreachable mid-session. (Note: `QuickReviewSessionService`'s reads `studyPack.getQuiz()`; `ChallengeQuizService`'s reads the session's own stored quiz via `QuizSessionStateUtils.extractQuiz` — the two modes' scoring sources genuinely differ, see below.) |
| `CombinedQuizSection` | teacher share-link internal composition | n/a | **CONFIRMED CLOSED by the falsification pass.** Serialized only via `CombinedQuizResponse` from `CombinedQuizController`, which is `@PreAuthorize` USER/ADMIN and uses `findByIdAndOwnerUserId` — never reaches an unauthenticated recipient. The public `/quiz/share/{token}` path returns `PublicSharedQuizResponse` → `PublicQuizItem`, which structurally cannot carry the answer key. |
| `StudyPackResponse`, `NoteResponse`, `PublicNoteDetailResponse`, `SharedStudyPackResponse`, `PublicShareResponse`, `GeneratedQuizResponse`, `DataExportResponse` | Note/Study Pack authoring, public note, account export | n/a | **OUT OF SCOPE by owner decision** — leave fully unredacted |

**Additional routes returning `QuickReviewAdaptiveQuizResponse`, found by the falsification pass and missing
from the original inventory:** `NoteController:687,700` and `NoteCollectionController:104` (collection-scoped
Adaptive Practice start). Covered by the same service-layer fix as the primary route, but the MockMvc test
suite must name all three, not just the one in `AdaptivePracticeController`.

**`@Deprecated` endpoints on `QuickReviewSessionController`** all delegate to the same underlying service
methods as their non-deprecated equivalents (confirmed) — no separate redaction work, as long as the fix stays
in the service/response-mapping layer.

**⚠️ Scoring source of truth differs by mode — do not generalize from one to the others (an error the first
draft of this plan made and the falsification pass caught):**
- Quick Review's review reads `studyPack.getQuiz()` — irrelevant now since Quick Review is out-of-pattern
  entirely (owner decision 3).
- Long Exam's `saveProgress` reads the quiz from `session.getSessionState()` (the session's OWN copy) — not
  from the Study Pack.
- Challenge Quiz/Board Exam's review also reads from the session's own stored quiz, not the Study Pack.
- Adaptive stores its own freshly-generated quiz via `withQuiz`.
- **The one universal rule that does hold: redaction must be response-mapping only, and must never mutate
  what's actually persisted in `session_state.quiz`** — for Long Exam, Challenge Quiz, Board Exam, and
  Adaptive, that stored copy is the only surviving record of the answer key and scoring depends on it.

## The answer-lock invariant — scoped narrowly, not applied everywhere

**⚠️ CORRECTED after falsification: the first draft recommended adding "the same lock cheaply" to Long Exam's,
Challenge Quiz's, and Board Exam's progress-write merge functions. This is wrong and would have broken a real
feature.** Long Exam's `saveProgress` sends `selectedChoiceIndex` on **every keystroke** of an identification
answer (comment at `LongExamService.java:409-413`, guarding against a different, already-fixed bug in the same
area) — a first-write lock there would freeze the first keystroke of every identification question. More
generally: in a mode with no reveal until completion, revising an answer before submitting is legitimate exam
behavior and must stay unlocked.

**The lock applies to exactly the three surfaces that reveal per-answer: Quick Review's new fetch (in its own
way — see below), Adaptive Practice, and Interview Practice.** It must be implemented in each of those write
paths specifically, not added to the shared `QuizSessionStateUtils.withSelectedChoice` (Long Exam's function —
correcting the plan's earlier wrong name, `recordSelectedChoice`, which doesn't exist) or to Challenge Quiz's
`mergeSessionState`.

**Lock semantics, resolved:** key the lock on "has a reveal already been served for (question, round)", not
"has a value already been written" — a resubmission of the *same* choice is idempotent (safe for a client
retry after a network timeout) and returns the existing reveal; a *different* choice for an
already-answered-and-revealed question in the same round is rejected, stored answer unchanged. Test per
surface: submit, submit-again-same-choice (idempotent), submit-again-different-choice (rejected).

## Reveal timing per mode (revised)

- **Long Exam:** never reveal, at any point, session or completion. No new mechanism needed.
- **Challenge Quiz, Board Exam:** no reveal until completion; reveal then via the widened
  `ChallengeQuizSessionResponse` (owner decision 4).
- **Adaptive Practice, Interview Practice:** reveal per-answer, locked. Adaptive additionally needs completion
  to score from server-stored selections, not the client-submitted ones in the complete request.
- **Quick Review:** N/A in the same sense — its own new fetch design (owner decision 3) needs its own
  reveal-timing decision as part of that build, since it isn't following the existing session-response
  pattern at all.
- **⚠️ Resume rule, corrected — this is NOT uniform across modes.** Only **Adaptive Practice and Interview
  Practice** (the two locked, per-answer-reveal surfaces) should reveal already-answered questions on
  refresh/resume, continuing to redact unanswered ones. **Long Exam, Challenge Quiz, and Board Exam have no
  lock by design (revising an answer before submitting is legitimate exam behavior there) — their resume
  responses must stay FULLY redacted, including for already-answered questions, until completion.** Revealing
  answered items on resume for these three would hand out a trivial cheat: answer everything with a guess,
  refresh, see which ones were right from what's now revealed, go back and fix only the wrong ones.

## `withoutAnswerKey()`'s per-field redaction decision, corrected at the Challenge Quiz PR (applies to every
future mode that reuses this method — Adaptive Practice and Interview Practice both need to check this too)

**`acceptableAnswerGroups` cannot simply be nulled — it is structural, not just content, for ENUMERATION
questions.** `QuizEnumerationInput` (`frontend/components/study-pack/quiz-enumeration-input.tsx:27`) derives
the NUMBER of answer input boxes it renders directly from `acceptableAnswerGroups.length`. Nulling it
outright (the original design) would render zero input boxes for every Enumeration question — silently
unanswerable, no error surfaced anywhere. **Fixed in the Challenge Quiz PR:** when `acceptableAnswerGroups` is
non-null, the redacted copy keeps the same OUTER length but replaces every inner list with an empty list —
preserving the slot count while hiding the accepted answers themselves. Every other field
(`correctIndex`/`correctIndices`/`explanation`/`workingSolution`/`acceptableAnswers`) is still nulled outright;
none of them are structural in this way for any format checked so far. **Long Exam never emits ENUMERATION**
(confirmed against `long-exam-developer.txt`), so this correction doesn't affect the already-shipped PR #1460
— but **check whether Adaptive Practice or Interview Practice emit ENUMERATION before reusing this method
unmodified**, and re-derive whether any OTHER field is structural (not just answer-bearing) for a format
those two modes serve that Challenge Quiz and Long Exam don't.

## PR-time checks flagged by the falsification pass, not yet resolved (resolve when each PR is scoped)

- **Challenge Quiz/Board Exam:** `QuizAnswerReview` renders at `challenge-quiz/page.tsx:2651` and `:2880` —
  confirm both render paths are reached only from the just-completed session (which will carry the widened
  response's reveal data), not from resuming an old session or loading history, before assuming the widened
  `ChallengeQuizSessionResponse` covers every place this component mounts. Also confirm whether Board Exam's
  results screen renders `QuizAnswerReview` at all, or something else. The client's own `computeScore`
  (`:1104`) will read 0 post-redaction (it was scoring off the now-redacted in-session `quiz` field) — confirm
  the results screen displays the server-computed score from the response instead, not the client's local
  computation.
- **Interview Practice:** every `answerQuestion` call runs `quizGenerationService.generateInterviewCritique`,
  an actual LLM call (`LLM_MODEL_CRITIQUE`). The lock's idempotent same-choice path must return the previously
  stored critique (`withInterviewFeedback`'s existing value), not regenerate one — otherwise "idempotent"
  still costs a fresh LLM call on every retry. **Check before scoping this PR: is the Interview Practice quota
  charged per session or per answer?** If per-session, the CURRENT missing re-answer guard is not just an
  answer-key leak — it's an unmetered LLM-call loop (repeatedly resubmit the same question, repeatedly trigger
  a paid LLM call, at no additional quota cost). That would make this a quota/money-semantics finding, not only
  a practice-integrity one, and belongs in the pre-signoff falsification brief explicitly if confirmed.
- **Adaptive Practice:** its per-answer reveals must be merged into the frontend's local scoring state, or the
  client's own score tally (`adaptive-practice/page.tsx:484-487,739-745`) will show 0 even though the server
  scored correctly. **Naming the ConceptHealth implication explicitly, per advisor guidance:** once completion
  scores from server-stored, locked selections instead of the client-submitted ones, that changes what
  `ConceptHealth` gets written from for this mode — the pre-signoff falsification brief must name this as a
  behavior change to verify, not just a security fix.

## Quick Review's new fetch — sized separately, not yet designed in detail

This is real, separate frontend+backend work, not a variant of the other modes' fix. Rough shape: a new
endpoint (or a new response type on the existing Quick Review start/resume calls) that returns the note's
metadata plus a quiz array with `correctIndex`/`correctIndices`/`explanation` present only for questions the
session has already recorded an answer for — meaning Quick Review's session actually needs to start storing
something now (today it stores `null`), which changes its data model, however small. `GET /notes/{id}` itself
must NOT change shape. **This needs its own short design pass before a Codex prompt, not a prompt written
straight from this paragraph** — flag that explicitly when this PR is scoped.

**Constraints carried forward from the falsification pass that the design pass must account for, not
rediscover:**
- `QuickReviewSessionService.updateSessionProgress` (`:150-179`) still does `session.setSessionState(request.sessionState())`
  — an unconditional overwrite of whatever the client sends. Once Quick Review's session starts storing
  anything (which this fetch design requires), this overwrite lets the client write `selectedChoices` directly
  and bypass any answer lock added on top of it. This must move to an allowlist/per-field merge (the same
  pattern Challenge Quiz's `mergeSessionState` and Long Exam's `withSelectedChoice` already use) as part of
  this design, not left as-is.
- `quiz.md:16`'s mastery predicate, `verifiedCorrectAnswers`, reads exactly this client-written state at
  completion — so the overwrite above isn't just a lock-bypass risk, it's already the actual source Quick
  Review's mastery gate trusts today. Changing the write contract touches that gate; re-verify the predicate
  still holds once the write path changes.
- The frontend's actual `/progress` payload (`quick-review/page.tsx:646-653`) sends `selectedChoices`,
  `selectedMultiChoices`, `currentQuestionIndex`, `currentRound`, `retryCount`, **and also**
  `retryQuestionIndexes`, `activeQuestionIndexes`, `roundSelections`, `roundMultiSelections` — resume reads all
  of these back. Any future allowlist must keep every one of them, not just the first five (an earlier draft
  of this plan proposed a 5-key allowlist that would have silently broken resuming mid-`Retry Incorrect
  Questions`).

## Deploy ordering (per CLAUDE.md's breaking-change rule)

Removing `correctIndex`/`correctIndices`/`explanation` from unanswered items is a breaking response-shape
change in both directions for every mode that changes its response shape (Long Exam, Challenge Quiz/Board
Exam, Adaptive, Interview, and whatever Quick Review's new fetch turns out to be) — old frontend against new
backend reads `undefined`; new frontend against old backend expects fields or a round trip that doesn't exist
yet. Both sides of each mode's PR must ship together; cached old frontend bundles hitting a new backend
mid-session is a real risk, per the `v0.136.0` lesson.

## Test strategy

**⚠️ CORRECTED after falsification:** the original rationale ("the `sessionState` map re-embeds the full
`QuizItem` list regardless of typed fields") is not actually true anywhere today — Challenge Quiz already
strips it, Long Exam and Adaptive's DTOs have no `sessionState` field, and Quick Review's never contained one.
**Keep the same MockMvc-body-assertion test strategy anyway, as a regression guard**, but state the real
reason: a typed-DTO-only test can't see a field re-added to a raw map by a future change, and a service-level
test with mocked repositories proves nothing about what Jackson actually serializes. Every mode's test must use
`MockMvc`, assert on the raw serialized JSON body, and name every route (including the two extra Adaptive
routes on `NoteController`/`NoteCollectionController` found above). A frontend `lib/api-*.test.ts` file per
touched endpoint pins the new response shape.

## Shape of the work

Recommended PR sequence, each backend+frontend together:
1. **Long Exam** — simplest, unconditional redaction, no reveal-timing decision, no lock.
2. **Challenge Quiz + Board Exam together** (same service file), coordinated with items 2-4 of this release
   (same service/bank code, same falsification pass found both) — includes widening
   `ChallengeQuizSessionResponse` for the completion reveal.
3. **Interview Practice** — small, self-contained surface with a confirmed-live exploit; no shared-code
   reason to sequence it earlier than build order suggests, and every PR in this item ships together at the
   same release merge regardless of internal ordering, so this isn't a separate-hotfix decision unless the
   owner wants it to be.
4. **Adaptive Practice** — new round trip, the lock, and the completion-scoring fix (stop trusting
   client-submitted selections).
5. **Quick Review** — last, and explicitly needs its own short design pass first (see above) before a Codex
   prompt is written, since it isn't a variant of the other four; different shape of work entirely.

Call `advisor()` before writing each PR's Codex prompt, not just once for the whole item.

## Docs to update once shipped, not before

`docs/features/quiz.md:22,31` currently states the self-study leak is accepted and not a security boundary —
once this ships, correct it to describe the new redact/reveal-on-answer behavior for practice sessions, while
explicitly preserving that Note/Study Pack page access and the public note page remain unredacted by design.
Do not edit `quiz.md` until behavior actually ships.
