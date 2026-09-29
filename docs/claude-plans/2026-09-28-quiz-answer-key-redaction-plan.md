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
| **Adaptive Practice** (excl. Interview) | `QuickReviewAdaptiveQuizResponse` | **No per-answer round trip exists today** — only start/complete/forfeit. Every response funnels through ONE real builder, `toAdaptiveResponse` (`:1045-1066`) — the other 7 construction sites all return an empty `quiz`. | **REDACT, reveal per-answer — design finalized, largest PR of the 6.** **⚠️ The completion integrity gap is worse than "can be overridden": it is the ONLY source today.** `safeCorrectAnswers` comes directly from the client's `correctAnswers` param with no alternative; `submittedSelectedChoices`/`submittedSelectedMultiChoices` fall back to the stored session state only when the client sends null, and since nothing writes selections into `session_state` today, that fallback has always resolved empty in practice — meaning stored score AND `ConceptHealth` writes have effectively always been client-reported, for every session, not just adversarial ones. **This PR changes both from client-reported to server-derived, for every completion** — a production-data-semantics change, named for the pre-signoff falsification brief, not folded silently into "closed a leak." The `correctConceptNames` fallback also changes meaning: narrow it to apply only when the stored quiz itself is empty, not whenever stored selections are empty (post-fix, empty selections mean "answered nothing," not "we can't tell"). **Resume must reveal already-answered items, which is a NEW requirement this PR introduces, not a regression risk** — nothing persists mid-session progress today (`applyAdaptiveSession` unconditionally resets position/selections on every load), so there's nothing to preserve, but once `/answer` starts writing real state, redacting everything on resume would strand a learner on an already-answered, now-locked, unrevealed question. **Race closed properly here, unlike Interview Practice** — the reveal is a stored-quiz lookup with no LLM call, so a `findByIdAndUserIdAndSessionModeForUpdate` row lock across it is cheap; no known-limitation needed. **MATCHING groups (2-4 consecutive shared-choice items) lock per question INDEX, same granularity as every other mode** — the frontend's own "whole group answered" check is a presentation choice, not a backend requirement; no group-level endpoint variant. Confirmed: Adaptive generates only MCQ/MULTI_SELECT/MATCHING (no IDENTIFICATION/ENUMERATION), so no Challenge-Quiz-style structural redaction concern applies. Confirmed: both generation paths (single-pack and collection-scoped) genuinely dedupe against source packs' own saved quiz — redaction is real, not a no-op. |
| **Interview Practice** (Item 6, newly added) | `InterviewPracticeStartResponse`, `InterviewPracticeAnswerResponse` | **UNSAFE, live today — directly re-verified, not taken from a subagent report.** `InterviewPracticeAnswerRequest` carries a client-supplied `questionIndex` (not server-owned/auto-advanced); `answerQuestion` (`:168-211`) validates only that the index is in range, then unconditionally writes via `withInterviewAnswer` with no re-answer guard, generating a fresh LLM critique (which reveals correctness via `verdict`/`rationale`) on every call; `buildReport` (`:454-469`) scores whichever selection is currently stored per index at completion time. Submit-wrong-then-resubmit-correct is real and counts today. | **REDACT + LOCK — design finalized, scoped into its own PR.** This is the learner's own practice record (an Interview Readiness Report) — same practice-integrity class as the other 5 surfaces, not a cross-user exposure. **Lock is keyed on whether a critique was actually SERVED, not on whether a selection was recorded** — `answerQuestion` writes the selection before calling the LLM, inside one class-level `@Transactional` method, so a failed critique call rolls both writes back together today, but keying on the critique makes the lock correct independent of that transaction shape. Same choice resubmitted is idempotent (returns the stored critique, no LLM call); a different choice after a reveal is rejected (409). **Quota/cost finding, not just a leak:** `assertQuotaAvailable` runs once per session at start, never per answer — the missing guard was also an unmetered, un-rate-limited LLM call on every resubmission. **Concurrency race explicitly NOT fixed, documented as a known limitation:** `findInterviewSessionOrThrow` takes no row lock, so two genuinely concurrent requests for the same index could both pass the check before either writes — closing it needs a lock held across an LLM call, which this repo's pool-exhaustion history argues against doing casually, and exploiting it needs a learner to deliberately script concurrent requests against their own record (same risk class as the already-parked Quick Review `releaseClaims` precedent). |
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

## Quick Review's new fetch — design finalized 2026-09-28, corrected across TWO rounds of `advisor()`
findings before any Codex prompt was written, plus one owner scope decision

**⚠️ First pass of this design was wrong in a way that would have shipped a no-op: it kept `getNote` in the
page for "metadata" while claiming the leak was closed.** `NoteResponse.quiz` carries the full key
regardless of which other fields the page reads off it — the leak is the RESPONSE BODY existing on the
wire, not which fields the app happens to render. **Second pass, still before implementation, found four
more:** the post-completion `getNote` re-fetch is legitimate and must stay; redaction/reveal must be scoped
to the CUMULATIVE answer map, not the per-attempt lock bucket, or a mid-retry resume corrupts the score;
shared-recipient Quick Review has NO real backend session today (`NoteController`'s routes are
owner-gated), so "close it the same way" was not actually available without a scope decision; and `title`
must come from the Note entity, not the Study Pack's own (separately-editable) title. **Owner decision,
2026-09-28: extend real sessions to shared recipients** (see below) rather than leaving that surface
unfixed or building a second, inconsistent mechanism.

1. **[Blocking, corrected] The page must never call `GET /notes/{id}` (or the shared-recipient equivalent)
   BEFORE completion — not "stop reading `.quiz`," stop calling it. This is a genuine restructuring of the
   page's load sequence, not a data-source swap within the existing sequence.** `QuickReviewSessionStartResponse`
   (shared builder in `QuickReviewSessionService`, used by `POST .../quick-review/start` and
   `GET .../quick-review/in-progress`) gets widened with everything the page actually reads off `note`
   pre-completion — confirmed by grep, exactly: `id`, `title`, `keyConcepts`, `quizMastered`,
   `quizMasteredAt`, `quickReviewAvailable`-equivalent, `quizCount`, plus the new `quiz: List<QuizItem>`
   field (redacted per CUMULATIVE answer state — see point 5's correction below — sourced from
   `studyPack.getQuiz()`; no new per-session quiz storage needed), plus a new **`isOwner: boolean`**
   (server-computed: caller's userId equals `studyPack.getOwnerUserId()`) — see below for why this field is
   required, not optional polish.
   - **[Blocking] `loadNote`'s branching structure itself must be removed, not just its `getSharedStudyPack`
     leg — a direct consequence of the day-later-scenario production check, not a hypothetical.** Today
     `loadNote`'s ELSE branch (taken whenever `isSharedSource` is false — i.e., ANY entry point without an
     explicit `?source=shared` query param, which is every Dashboard Continue Studying card link
     (`continue-spotlight.tsx:202`, `/notes/{noteId}/quick-review`, no param) and every due-concepts-digest
     link (`RetentionService.java:437`, `?source=due-concepts-digest`, a DIFFERENT param value)) calls
     `getNote(noteId)` directly — and `NoteService.getById` is `findByIdAndOwnerUserId` with **no shared
     fallback** (confirmed by direct comparison against `getOwnedStudyPackIdOrThrow`, which does have one).
     **This means a recipient arriving from the Dashboard card or the digest TODAY already 404s before the
     session-start effect ever fires** — a genuinely pre-existing dead end, just not one caused by this PR
     (there is currently no other way for a recipient to reach this page productively either, since the
     ONLY working entry point is the exact page carrying `?source=shared`). Confirmed this has never
     actually been exercised: a read-only production query
     (`SELECT count(*) FROM quick_review_sessions q JOIN study_packs sp ON sp.id = q.study_pack_id WHERE
     q.session_mode = 'QUICK_REVIEW' AND q.user_id <> sp.owner_user_id`) returns **zero** — no recipient
     Quick Review session has ever existed. The service-layer capability is real and tested; nothing
     downstream of it (this query, the Dashboard's in-progress lookup, ConceptHealth readers, unlock
     analytics) has ever seen a live instance. **Fix: gate the session-start effect on `noteId` (the route
     param) directly, not on a prior successful `note` fetch — `startQuickReviewSession(noteId)` becomes
     the FIRST call the page makes, for every entry point, with no preceding `getNote`/`getSharedNote`/
     `getSharedStudyPack` call of any kind.** The `isSharedSource` query-param branch in `loadNote` is
     deleted in its entirety, not narrowed — it cannot be trusted to identify a recipient anyway, since
     the Dashboard/digest links prove a real recipient can arrive with no such param.
   - **[Blocking] `noteDetailHref` (`:528-530`) currently branches on the same unreliable `isSharedSource`
     param to choose `/shared/notes/{id}` vs `/notes/{id}` — this must switch to the new server-computed
     `isOwner` field, or a recipient arriving via the Dashboard/digest gets a link that 404s** (`/notes/{id}`
     is owner-only, confirmed above) **even after the load-sequence fix**, because the query param still
     won't be present. `isOwner ? \`/notes/${noteId}\` : \`/shared/notes/${noteId}\`` is the corrected
     derivation, sourced from the widened session response, not the URL.
   - **Two remaining `note.id` consumers, checked and resolved:** `generateQuickReviewStudyTip(note.id, …)`
     needs only the note id, already available as the route's own `noteId` param — no `note` object
     required. `completeProductOnboarding` reads no note field at all (a global onboarding-completion call)
     — unaffected by this restructuring.
   - **`title` must be the NOTE's title, not the Study Pack's.** Confirmed at
     `NoteService.mapToResponse` (`:1504-1546`): `NoteResponse.title` = `entity.getTitle()` (the
     `NoteEntity`), while a SEPARATE `studyPackTitle` field = `studyPack.getTitle()` — the two have
     legitimately diverged since v0.120.0 (the pack's own title is a dismissible rename suggestion, not the
     displayed title). `quick-review/page.tsx:1116` renders the NOTE's title. `QuickReviewSessionService`
     currently loads only `StudyPackEntity`, not the `NoteEntity` — building the widened response requires
     also loading the `NoteEntity` (via `studyPack.getNoteId()`) and sourcing `title` from it, `keyConcepts`
     from the study pack (unchanged). **Mastery sourcing is corrected further below (the caller's own
     userId, not the owner's) — do not use the owner's id here, that draft was superseded.**
   - **The "Generate a Study Pack first" state** (`detail.quickReviewAvailable` today) maps instead from
     `startSession` throwing `QuickReviewNotAvailableException` when
     `!StudyPackArtifactFacts.hasQuizQuestions(studyPack.getQuiz())` — the check already exists
     server-side; the page just needs to catch that specific error and render the same message, not
     pre-flight it from a note fetch.
   - **Shared-recipient path — the CORRECT, FINAL finding, settled empirically after four straight rounds
     of wrong static-reading conclusions (precedented-via-Challenge-Quiz, then first-of-kind, then
     needs-a-new-resolver — all wrong). Two scratch unit tests were written, run, and deleted (not
     committed) to stop re-deriving this from static reads:**
     - **Test 1:** `QuickReviewSessionService.startSession(studyPackId, recipientUserId)`, called with the
       recipient NOT the owner but holding a live share — **succeeds**, returns a real `IN_PROGRESS`
       session with a real `sessionId`.
     - **Test 2:** `NoteService.getOwnedStudyPackIdOrThrow(noteId, recipientUserId)` — the exact method
       `NoteController.startQuickReview`/`getInProgressQuickReview` (`:486-506`) call — **also succeeds**
       for the same non-owner-with-a-live-share case. Reading its source confirms why: it does NOT throw
       on a failed owner lookup — it falls back to `hasAuthorizedShare(noteId, ownerUserId)` (backed by
       `noteShareRepository.existsLiveAuthorizedShare`) and, if a live share exists, resolves the study
       pack anyway. The name is misleading; the method is not actually owner-only.
     - **Conclusion: `NoteController.startQuickReview`/`getInProgressQuickReview` — the EXACT routes this
       page already calls today via `startQuickReviewSession(note.id)` — already work end-to-end for a
       shared recipient, right now, with ZERO backend changes.** No new resolver, no route change, no
       `@Deprecated`-route wiring, no studyPackId-based frontend call. Every earlier draft's backend-change
       proposal for this sub-point is WITHDRAWN.
     - **So what is the actual bug?** Purely that the frontend's `isSharedSource` branch (`:330-369`)
       ALSO builds a redundant, hand-stitched `NoteResponse`-shaped object via `getSharedNote` +
       `getSharedStudyPack` — and `getSharedStudyPack`'s `.quiz` field is the full, unredacted answer key,
       rendered from this manual object while a REAL, already-working session (created by the SAME
       `startQuickReviewSession(note.id)` call this page already fires unconditionally, per the effect at
       `:693-701`) exists in parallel and goes unused for content. **Fix: delete the entire manual
       `getSharedNote`/`getSharedStudyPack` stitching block (`:330-369`) outright.** Once `quiz` is
       sourced from the widened session response (point 1) instead of `note.quiz`, `isSharedSource` no
       longer needs to change WHICH backend call this page makes — owner and recipient use the identical
       code path. `isSharedSource` may still be needed for cosmetic UI branching (e.g. `noteDetailHref`
       pointing at `/shared/notes/{id}` vs `/notes/{id}`), but not for data loading.
     - **This also resolves the Dashboard Continue Studying card and due-concepts-digest concern raised
       against an earlier draft, with no extra work.** `continue-spotlight.tsx:202` builds
       `/notes/{noteId}/quick-review` with no ownership branching and no `source=shared` param; the digest
       link (`RetentionService.java:437`, `/notes/{noteId}/quick-review?source=due-concepts-digest`) is the
       same shape. Both already route to the SAME note-anchored page that now works uniformly for owner and
       recipient — there is no separate "recipient" entry point to wire up, and no dead end, because the
       one route both entry points already use is the one that already works.
     - **Mastery fields on the widened response must be resolved with the CALLER's own userId, not the
       owner's.** `StudyPackQuizMasteryService.resolve/tryResolve(userId, studyPack)` takes `userId` as a
       genuine per-caller parameter (`findQuizMasteredAt(userId, studyPackId, ...)`, confirmed) — it is NOT
       an owner-only concept. `NoteResponse`'s mapper happens to pass `entity.getOwnerUserId()` only because
       Note Detail is itself owner-only, so that parameter is always "the current user" in that one context
       — generalizing it to "always the owner" for Quick Review's widened response (which now serves BOTH
       callers through the SAME route) would show every shared recipient the OWNER's mastery state, a
       genuine cross-user read this fix must not introduce. **Use the caller's own userId, unconditionally,
       for both owner and shared callers** — this gives each learner independent mastery tracking on shared
       material.
     - **Use `tryResolve`, not `resolve`, building the widened response** — `completeSession` already does
       this specifically so a failed mastery lookup can't break completion; `startSession`/`getInProgressSession`
       need the same defensiveness so a failed lookup can't break START, defaulting `quizMastered`/
       `quizMasteredAt` to `false`/`null` on `Optional.empty()`.
     - **Known limitations, not fixed here, and genuinely pre-existing (not introduced by this PR, since
       the recipient's session already exists today independent of this fix):**
       - `getPostSessionNextStep` (`StudyPackController:144-152` → `PostSessionNextStepService.getNextStep`)
         uses `studyPackRepository.findByIdAndOwnerUserId` — owner-only, confirmed — so a shared
         recipient's completion 404s on this call, already wrapped in
         `.catch(() => setNextStepResponse(null))`, so it fails silently and the "Recommended next step"
         card simply doesn't render for a recipient.
       - **The post-completion `getNote(noteId)` call kept at `:785` for the mastery-unlock banner is
         ALSO owner-only** — `NoteController.getById` → `NoteService.getById` uses
         `noteRepository.findByIdAndOwnerUserId` with NO shared fallback (unlike `getOwnedStudyPackIdOrThrow`,
         confirmed by direct comparison of the two methods), so this 404s for a recipient too, already
         wrapped in `.catch(() => setQuizMasteredAfterCompletion(false))` — the "You earned access..."
         unlock banner never shows for a recipient, silently.
       - Recent Sessions and Performance Summary are similarly owner-only via the note-anchored routes.
       - Document all of these in `RELEASES.md`'s Known Limitations.
     - **Verification-tier consequence of this correction:** since no backend authorization code changes
       for the shared path at all (the access logic is unchanged; only the frontend stops bypassing it),
       the CLAUDE.md "moves an authorization boundary" trigger arguably does NOT fire for this specific
       sub-point on its own. The owner's decision to run the full three-agent pressure test regardless
       (made before this final correction, for session-continuity assurance — see the verification-tier
       note below) still stands and should not be walked back on the strength of this correction alone;
       the redaction/lock/reveal mechanism itself (point 1-5, applying identically to both callers) is
       still new, real, and worth the full test.
   - **`quick-review/page.tsx:785`'s separate `getNote(noteId)` call is legitimate and must stay** — it
     runs strictly AFTER `completeQuickReviewSession` resolves, specifically to detect whether THIS session
     flipped mastery (comparing `quizMasteredAt` against `sessionStartedAt`). The full key is no longer
     sensitive once the session has completed, so this call is out of scope for redaction and the test below
     must not assert it away.
   - **Required test:** `expect(getNote).not.toHaveBeenCalled()` asserted BEFORE `completeQuickReviewSession`
     resolves, not for the whole test lifecycle (the post-completion call at `:785` is expected and
     correct) — a passing suite that doesn't distinguish pre- from post-completion would pass trivially
     without closing anything, the same `v0.116.0`/`v0.117.0` failure shape this repo's own rules warn
     about. **`getSharedNote` and `getSharedStudyPack` are deleted from this page's module entirely**
     (per the shared-recipient finding above), so the shared-path test asserts neither mock exists to be
     called, and additionally exercises the SAME start → answer → complete flow for a shared caller as for
     an owner, through the identical code path — there is no separate shared branch left to test in
     isolation.
   - **[Blocking] Start is now load-bearing and needs a failure-mode spec, with a test per case.** Today a
     failed `startQuickReviewSession` call degrades gracefully to sessionless practice off `note.quiz`
     (still present as a fallback). Once `note.quiz` is gone, a failed start leaves the page with NO quiz
     and no title at all — the degrade path this page relied on disappears. Map each failure explicitly:
     transient error/5xx → a retry state, not a blank page; `QuickReviewNotAvailableException` → the
     existing "Generate a Study Pack first" message; a 403/404 (deleted pack, revoked share) → a
     not-found state. Test each.
   - **`getInProgressSession` (`:134-148`) needs two small, easy-to-miss fixes alongside the widening.**
     It currently discards `findAccessibleStudyPack(studyPackId, userId, false)`'s return value entirely
     (called only for its access-check side effect) — capture it, since `toStartResponse` needs the
     `StudyPackEntity` (and the `NoteEntity` loaded from it) to populate the new metadata fields when an
     in-progress session IS found. **The "no session" empty-literal at `:147`**
     (`new QuickReviewSessionStartResponse(null, null, 0, null, 0, null)`) **must widen alongside the DTO** —
     every new field needs an explicit default in this literal, or it silently compiles with nulls that
     don't match the "nothing in progress" semantics the other fields already express.
2. **New endpoint, mirroring Adaptive Practice's `/answer` in contract, but matching Quick Review's OWN
   existing route shape — no `/sessions/` segment.** `QuickReviewSessionController`'s existing routes are
   `POST /quick-review/{sessionId}/progress`, `/complete`, `/forfeit`, `/confidence` — add
   `POST /quick-review/{sessionId}/answer` alongside them, same controller, same
   `{"/quick-review-sessions", "/quick-review"}` prefix mapping. Request: `{ questionIndex, selectedChoiceIndex |
   selectedMultiChoiceIndices }` — no client-supplied round; the endpoint locks against the per-attempt
   bucket keyed by the SESSION's own stored `retryCount` (point 3), not a client-supplied value. **This
   does not make `currentRound`/`retryCount` fully server-owned** — `/progress` still writes them from the
   client request today (point 10's hardening note), so the bucket key itself is still influenced by what
   the client has told `/progress` — but a client cannot retroactively rewrite which bucket an ALREADY
   ANSWERED index's lock lives in, which is the actual property this endpoint needs. Response:
   `{ questionIndex, question: <unredacted QuizItem> }`, same shape as `AdaptivePracticeAnswerResponse`.
   Loads via a row lock (`findByIdAndUserIdAndSessionModeForUpdate` — cheap here, same as Adaptive, since
   this is a stored-quiz lookup with no LLM call). Reads `studyPack.getQuiz()` for the question content (not
   a session-stored copy — see point 1).
   - **[Blocking] `/answer` must call `recheckMaterialAccess`, the same re-authorization every other Quick
     Review write already does** (`updateSessionProgress`, `completeSession`, `forfeitSession`,
     `saveConfidenceLevel` all call it) — this is what cuts off a share recipient whose access is revoked
     mid-session. `/answer` is the endpoint that hands out the answer key, so skipping this check would be
     an authorization regression this PR itself introduces, not a pre-existing gap. Read the quiz through
     that same access path; treat `Optional.empty()` (pack deleted/inaccessible) as a hard failure for this
     endpoint specifically — unlike `completeSession`'s tolerant handling of a missing pack (which is safe
     because completion only needs the pack for an optional concept breakdown), `/answer`'s entire job is
     revealing the answer key, so "can't verify access" must never fall through to "reveal anyway." This
     adds `/answer` to the pre-signoff falsification brief as an authorization-boundary change, per
     CLAUDE.md's escalation trigger.
3. **[Blocking, corrected] Lock storage must be bucketed by attempt, and the two writes `/answer` makes are
   different in kind — one locked, one never locked.** Verified directly against the frontend (not assumed):
   `retryCount` only ever becomes `0` or `1` in the current product (grep confirms no code path increments
   it past `1` — `handleNext`'s `phase === "retry"` branch goes straight to `"complete"` regardless of
   outcome, there is exactly one retry round, never a second). So keying the lock by `(questionIndex,
   currentRound)` and by `(questionIndex, retryCount)` are equivalent TODAY — but the storage must still be
   genuinely bucketed per attempt (e.g. nested under a `retryCount`-keyed map inside `sessionState`, not a
   flat `roundSelections` map the round transition merely intends to have reset), because **`/progress` no
   longer writes `roundSelections`/`roundMultiSelections` at all (point 4 below)** — the client's own
   `setRoundSelections({})` at the start of a retry round is a LOCAL reset only; nothing tells the server to
   clear the prior round's bucket, and nothing needs to if each attempt gets its own empty bucket by
   construction. **Document this `retryCount ≤ 1` invariant explicitly in the code** (a comment, not just
   this doc) so a future multi-retry feature doesn't silently violate the bucketing this PR's tests assume.
   - **The second write is unconditional and never lock-checked:** on every `/answer` call, regardless of
     round, the endpoint must ALSO overwrite `selectedChoices[index]`/`selectedMultiChoices[index]` (the
     CUMULATIVE, latest-attempt-wins maps) with the newly submitted value — confirmed this is exactly what
     `handleSelectChoice` already does client-side today (unconditionally spreads a new value into
     `selectedChoices` regardless of `phase`), and it is what
     `computeConceptBreakdownForStoredSelections(studyPack.getQuiz(), session.getSessionState())` reads at
     completion, which is what `quiz.md`'s mastery predicate and `MASTERY_PATH_AFTER_RETRY` both depend on.
     If a retry's correct answer only updated the round-bucketed lock map and not this cumulative map,
     mastery-after-retry would never unlock — the lock check and the scoring write must read from two
     different maps, not one. **Required test:** INITIAL wrong, then RETRY correct for the same index →
     completion reports `verifiedPerfect` with `MASTERY_PATH_AFTER_RETRY`, not a stale miss from the
     INITIAL attempt.
4. **`updateSessionProgress` stops being a whole-map overwrite.** Confirmed exactly which of
   `QuickReviewSessionProgressRequest.sessionState()`'s 6 raw keys (`selectedChoices`, `selectedMultiChoices`,
   `retryQuestionIndexes`, `activeQuestionIndexes`, `roundSelections`, `roundMultiSelections` — re-confirmed
   against `quick-review/page.tsx`'s `SessionStatePayload`/`persistProgress`, correcting this doc's earlier
   "8 keys" miscount, which double-counted the 3 fields that are actually top-level typed request fields,
   not inside `sessionState` at all) split which way: **`retryQuestionIndexes` and `activeQuestionIndexes`
   stay free-form, merged/overwritten from the client as today** — navigation/presentation bookkeeping
   (which indices to show next), not an answer key, and the client legitimately owns them. **`selectedChoices`,
   `selectedMultiChoices`, `roundSelections`, `roundMultiSelections` move to being owned exclusively by the
   new `/answer` endpoint** — `updateSessionProgress` merges in only the two navigation keys and preserves
   whatever `/answer` has already written for the other four, discarding any value the client sends for
   them (accepted for request-shape compatibility, same "legacy field, no longer trusted" pattern as
   Adaptive Practice's completion request). Without this split, the frontend's existing fire-and-forget
   `/progress` call (which today echoes ALL 6 keys on every answer, per `persistProgress`) would silently
   overwrite whatever the locked `/answer` endpoint just wrote, on the very next navigation tick — turning
   the lock into a no-op the same day it ships.
5. **[Blocking, corrected] Reveal/redaction is scoped to the CUMULATIVE answer map, not the per-attempt lock
   bucket — this is the opposite of what the first pass said, and getting it backwards corrupts scoring, not
   just UX.** RETRY only re-asks questions that were WRONG in INITIAL (`retryQuestionIndexes =
   incorrectIndexes`, confirmed at `:970-979`) — a question answered CORRECTLY in INITIAL never appears in
   RETRY's `activeQuestionIndexes` at all, so it has no entry in a RETRY-scoped bucket. Redacting by
   per-attempt bucket would therefore re-redact every INITIAL-correct item on a mid-RETRY resume, even though
   its answer was already revealed earlier in the SAME session and protects nothing by hiding it again. Worse
   than a display bug: `isQuizSelectionCorrect` would evaluate those items against a redacted (null)
   `correctIndex`, corrupting both the displayed score and the `correctAnswers` sent to `/complete` — which
   this item explicitly leaves client-computed (point 9) specifically because it was assumed accurate, not
   because it was expected to be broken by this redaction fix. **Rule: redact `quiz[i]` iff `i` is absent
   from the CUMULATIVE `selectedChoices`/`selectedMultiChoices` maps (any round, ever) — the per-attempt
   bucket from point 3 governs ONLY the lock (whether a given `/answer` write is accepted), never what gets
   redacted.** Client-side visual gating (`roundSelections`-driven `hasAnsweredCurrent`) is unchanged — a
   learner still can't see feedback on the CURRENT question before selecting, because the frontend doesn't
   render the revealed fields until it has a local selection to compare against, independent of what the
   server chose to redact. **Required test:** start INITIAL, answer one question wrong, transition to RETRY,
   answer it correctly, then resume mid-RETRY (before finishing) — the INITIAL-correct items must come back
   revealed, not redacted, and the eventual `correctAnswers` sent to `/complete` must include them.
   **Fix the resume effect's stale-closure bug this redesign would otherwise introduce:** the existing resume effect (`quick-review/page.tsx:693-750`)
   restores selections via `toSelectedChoiceIndexRecord(state.selectedChoices, quiz)`, reading the OUTER
   `quiz` variable — once `quiz` is deleted as a `note`-derived memo (point 1) and becomes page-local state
   populated asynchronously, this effect must read `started.quiz` (the just-fetched response's own field)
   directly, not the outer `quiz` binding, or resume restores against `[]` and silently drops every
   selection. The count derivation at `:381` similarly needs `metadata.quizCount`, not `.quiz.map(...)`.
6. **MATCHING keeps its current whole-group reveal, not Adaptive Practice's per-item reveal.** Verified
   directly: `hasAnsweredCurrent` for a matching group already requires EVERY item in the group answered
   before `revealAnswer` goes true (`activeMatchingGroup.items.every(...)`, `quick-review/page.tsx:434-435`),
   and `QuizMatchingGroup` here is passed one group-level boolean, not a per-item `revealedAnswers` map (the
   Adaptive Practice PR's addition). **Do not lock on the first item's click.** Accumulate matching selections
   locally as today; fire one `/answer` call per item in the group only once the WHOLE group is locally
   selected (parallel or sequential calls against the same single-question-index endpoint contract — no new
   batch shape needed), then reveal the group from those responses together. This preserves today's UX
   exactly and keeps the endpoint's per-index contract uniform with every other surface.
7. **Deploy-skew consequence, verified by reading `computeConceptBreakdownForStoredSelections` directly —
   NOT the same shape as Adaptive Practice's skew finding, and must not be described as if it were.** An old
   frontend against the new backend never calls `/answer`, and the new `/progress` allowlist (point 4) now
   silently drops the four answer-bearing keys the old frontend still sends. Unlike Adaptive Practice (where
   an empty-selections completion actively recorded a miss on every concept), Quick Review's
   `computeConceptBreakdownForStoredSelections` returns `List.of()` when both selection maps are empty, which
   short-circuits BOTH `recordCorrectAnswers` and `recordIncorrectAnswers` — no ConceptHealth write happens
   at all, and `session.verifiedCorrectAnswers` is simply never set, so `verifiedPerfect` (which requires it
   non-null) is always false. **The consequence is silent non-unlock, not an active miss:** every learner
   completing a session during the skew window keeps their displayed score (still client-computed, honestly,
   by the old frontend's own unredacted-at-build-time logic) but never gets `QUICK_REVIEW_MASTERED` /
   `STUDY_PACK_QUIZ_UNLOCKED` fired, and no ConceptHealth signal is recorded either way. **Quick Review is
   the most-used mode of the six** — this skew window's reach is the largest of any PR in this item; name
   this precisely in `RELEASES.md`, not as a copy-paste of Adaptive's wording.
   - **No separate skew direction for the shared-recipient path — it is fully subsumed by the owned path's
     skew above, because both callers now use the identical route and response type.** Corrected from an
     earlier draft, which wrongly described shared-recipient session start as a NEW capability the skew
     window would newly expose — it is not: recipient sessions already succeed today, before this PR, so
     there is nothing newly reachable during a skew window. An old frontend against the new backend keeps
     calling `getSharedStudyPack` (unchanged, unremoved) exactly as it does today — the leak stays open for
     shared users on stale bundles, no differently from before this PR shipped. A new frontend against an
     old backend has no `quiz` field on the start response for EITHER caller type, handled by the same
     failure-mode spec as the owned path (point 1).
8. **[Blocking, corrected — this must be FIXED, not deferred, because this PR is what turns the drift into
   active harm.** An earlier draft called this "pre-existing, not fixed here." That undersells it: the
   index drift itself predates this PR, but before this PR there was no lock and no reveal, so a stale
   index just quietly scored against changed content — annoying, not a hard failure. **This PR adds a
   lock**, so the SAME drift now returns a 409 to a learner answering in good faith (new harm this PR
   introduces), and cumulative reveal (point 5) now exposes the REGENERATED quiz's answer key at every
   index the learner answered before the regeneration, for questions they have never seen (a redaction
   failure this PR introduces). This is the owner's named "day-later" scenario and must be fixed, not
   documented around.
   - **The regeneration clock already exists and is already being loaded for this PR's other purposes**:
     `NoteEntity.generationEnqueuedAt`, the same field `StudyPackQuizMasteryService`'s query uses (see its
     Javadoc) to invalidate mastery earned before a regeneration. Point 1 already requires loading the
     `NoteEntity` for `title` — reuse it here.
   - **In `startSession`:** if an existing IN_PROGRESS session's `createdAt` is before the note's
     `generationEnqueuedAt` (null-tolerant the same way the mastery query's `coalesce` is — a null
     `generationEnqueuedAt` never counts as "regenerated since"), forfeit it (existing `forfeitSession`
     write pattern) and fall through to creating a fresh session, exactly as if no existing session were
     found.
   - **In `getInProgressSession`:** the same staleness check, but since this method is
     `@Transactional(readOnly = true)`, treat a stale session as absent — return the same empty literal
     used for "no session," rather than writing a forfeit itself. The actual forfeit-and-restart write
     happens in `startSession`, which the page calls to actually resume.
   - **In `/answer`:** run the same staleness check against the session it loads; if stale, return a
     distinct error (a new exception, not the answer-lock's 409) that the page maps to "restart," not to
     "you got this wrong."
   - **Required test:** start a session, answer index 0, bump the note's `generationEnqueuedAt` past the
     session's `createdAt` (simulating a regeneration), then resume. Expect a brand-new session (different
     `sessionId`), `quiz[0]` redacted (not leaking the old answer against the new content), and no 409 from
     `/answer` on re-answering index 0.
9. **Explicitly OUT of scope, flagged so it isn't silently assumed:** `completeSession` still trusts the
   client's `request.correctAnswers()` for the PRIMARY stored score (a pre-existing, separate-from-this-leak
   issue — Quick Review already computes a server-verified `verifiedCorrectAnswers` field today, but only
   for the mastery/unlock gate, not as the displayed score). Once every reveal is server-verified via
   `/answer`, the client's own tally becomes accurate in the honest case, but a malicious client can still
   misreport `correctAnswers` at `/complete` independent of anything this item changes. Flipping the PRIMARY
   score to server-derived here is more invasive than closing the redaction leak requires and was not part
   of the owner's scoping decision — raise it as a separate future candidate, do not fold it into this PR.
10. **Non-blocking hardening: `/progress` should reject a round/attempt regression — but verify against
    every real call site first, since the frontend's own error-swallowing makes a wrong guard invisible.**
    `updateSessionProgress` (`:172-174`) still writes `currentRound`/`retryCount` straight from the client
    request (navigation fields, not answer-bearing, per point 4) — this doesn't reopen the leak, but a cheap
    guard (reject `currentRound: INITIAL` while stored state is already `RETRY`; reject a `retryCount`
    decrease) is worth adding. **Before adding it, enumerate every `persistProgress` call site's
    `(currentRound, retryCount)` pair** (`handleSelectChoice`/`handleSelectMatchingChoice`: current
    round/retryCount, unchanged; `handleNext`'s mid-round advance: same; the INITIAL→retry-transition call:
    `currentRound: "INITIAL"`, `retryCount: 1` — retryCount ANNOUNCING the upcoming retry one call before
    the round itself flips; `handleStartRetryRound`: `currentRound: "RETRY"`, `retryCount` unchanged from
    the transition call) and confirm the guard's comparison is against the SESSION's stored round/retryCount
    at write time, not the request's own fields compared to themselves — a guard that's wrong here fails
    silently, since `persistProgress`'s `.catch(() => {})` swallows the rejection and the learner just loses
    navigation persistence with no visible symptom until they refresh mid-session.
11. **Second, separate leak found in the legacy redirect path — name it even if not fixed here.** The legacy
    `/study-packs/{id}/quick-review` URL fallback (`quick-review/page.tsx:387-403`) calls
    `getMyStudyPack(noteId)` — a full-key `StudyPackResponse` — purely to read `.noteId` and redirect to the
    canonical `/notes/{noteId}/quick-review` URL; the quiz content is never rendered from it. This is a real,
    separate full-key response on the wire, lower severity (URL-pattern edge case, immediate redirect, no
    render) but real by the same standard as point 1. If a lightweight id-only lookup already exists, use
    it; if not, name this explicitly as an unfixed residual leak path in `RELEASES.md` rather than letting it
    pass unmentioned — per CLAUDE.md's "never silently drop a finding."

**Verification-tier: FULL three-agent pressure test at signoff — owner decision, 2026-09-28.** This
question went through FOUR wrong framings before landing on a verified answer, recorded here as the
final, empirically-settled state, not because the churn matters going forward but because each wrong
framing changed what was being asked of the owner:
1. "Reusing existing shared-access support" (too casual) →
2. "First-of-kind, needs a scope decision" (too alarmed — checked directly and found every OTHER mode's
   session-start route genuinely IS owner-only, so this looked plausible) →
3. "Needs a new resolver method + frontend route switch" (still wrong — assumed Quick Review's OWN
   note-anchored route was ALSO owner-only, by analogy with the other modes, without directly checking) →
4. **Verified empirically, via two scratch unit tests written, run, and deleted:** Quick Review's
   note-anchored routes (`NoteController.startQuickReview`/`getInProgressQuickReview`) already succeed
   end-to-end for a non-owner recipient with a live share, TODAY, with no code changes. `getOwnedStudyPackIdOrThrow`
   is misleadingly named — it already falls back to shared-access resolution. **No backend authorization
   code changes for the shared path at all.** The bug is narrower than every prior framing: the frontend's
   `isSharedSource` branch redundantly ALSO builds a leaky manual object in parallel with an
   already-working real session, and the fix is deleting that redundant branch, not adding capability.
- **Correction on top of #4, after running the read-only production check #4 implied but had not yet run:
  the trigger DOES fire, just not for the reason any earlier draft gave.** `SELECT count(*) FROM
  quick_review_sessions q JOIN study_packs sp ON sp.id = q.study_pack_id WHERE q.session_mode =
  'QUICK_REVIEW' AND q.user_id <> sp.owner_user_id` returns **zero** — no recipient Quick Review session
  has ever existed in production. The SERVICE layer is precedented and tested, but **every downstream
  reader of a Quick Review session row is, in practice, first-of-kind for a recipient row**: the Dashboard's
  in-progress/Continue-Studying query, ConceptHealth readers (including the due-concepts digest, which
  will now link a recipient to an owner's note for the first time), and the `STUDY_PACK_QUIZ_UNLOCKED`/
  `QUICK_REVIEW_MASTERED` counts have never executed against a live recipient row and their behavior is
  therefore unverified in practice, not merely unverified in theory. **The full pressure test's brief must
  name these three reader classes explicitly**, not just re-review the redaction/lock/reveal mechanism.
  Combined with the owner's own explicit call (made independently, for session-continuity coverage), the
  full three-agent test is warranted on both grounds, not just the one the owner named.
- **Add explicit test coverage for hard/soft refresh, logout, and day-later Dashboard-continue resume**,
  since a history search found no documented prior incident or revert matching this concern precisely (the
  closest adjacent history, `v0.108.0` — "Session Identity" — fixed the Continue Studying card offering
  the *wrong mode's* session, not a Quick-Review-specific refresh/logout defect) — this is a fresh
  requirement to build coverage for, not a regression to re-verify against a known prior fix. Because the
  shared and owned paths are now one code path, these tests do not need a separate shared variant beyond
  the ordinary MockMvc coverage of a non-owner caller with a live share succeeding identically to an owner.

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
5. **Quick Review** — last. Needed its own short design pass (see above), which ran to five corrected
   rounds before landing on a verified design. **Ships as ONE PR, not split into owned/shared-recipient
   halves** — an earlier draft proposed a split, which `advisor()` rejected: the owned-path restructuring
   already requires deleting `getNote` from pre-completion load and gating session-start on `noteId` alone,
   and once that exists it already serves a share recipient correctly, since the backend routes it calls
   already succeed for both callers. Splitting would ship an incoherent interim page. Final prompt:
   `docs/codex-prompts/v0.163.0-quick-review-answer-key-redaction.md` (gitignored, not committed).

Call `advisor()` before writing each PR's Codex prompt, not just once for the whole item.

## Docs to update once shipped, not before

`docs/features/quiz.md:22,31` currently states the self-study leak is accepted and not a security boundary —
once this ships, correct it to describe the new redact/reveal-on-answer behavior for practice sessions, while
explicitly preserving that Note/Study Pack page access and the public note page remain unredacted by design.
Do not edit `quiz.md` until behavior actually ships.
