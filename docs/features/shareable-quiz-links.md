# shareable-quiz-links.md - NoteLib Feature Context

## Goal

Shareable Quiz Links let any onboarded user give a generated quiz to someone through a public `/quiz/{token}` URL. The recipient can take the quiz without creating an account, see server-validated results at the end, and then sign up to keep studying.

## Sharing Flow

An onboarded user generates a quiz from a note, reviews it on the quiz preview page, then uses the `Share Quiz` section to create a public link for the person they are helping.

Rules:

- link generation is available to any onboarded user; it is not gated by profile type
- a link targets exactly one immutable quiz snapshot: either a single-note `GeneratedQuizEntity` or a
  `combined_quizzes` row. `quiz_share_links.generated_quiz_id` and `combined_quiz_id` are an exclusive
  arc, enforced by PostgreSQL, so token lookup and the share-link meter remain singular
- each link uses a 16-character URL-safe token stored in `quiz_share_links`
- the quiz owner can toggle a link on or off from the preview page
- inactive or missing links return `404` from the public quiz endpoint

## Anonymous Recipient Play

Recipients open `/quiz/{token}` without authentication.

Rules:

- the initial public quiz response includes `question`, `choices`, `concept`, and `questionFormat`
- **⚠️ the initial response must not include `correctIndex`, `correctIndices` or `explanation`** — `PublicQuizItem` is the only thing enforcing this, and `questionFormat` is present so the recipient can be given the right *control*, never the answer
- no `QuickReviewSessionEntity` or score/session rows are created
- answers stay client-side until the recipient submits the quiz
- `/api/quiz/share/{token}/results` accepts the answers and returns the score, correct answers, and explanations for review
- the results screen prompts signup instead of persisting anonymous performance

This keeps public play lightweight and avoids creating student-owned history before authentication.

### The recipient page is a focused assessment for signed-in recipients too

`/quiz/{token}` is `permitAll`, so a recipient may or may not have an account — but until `v0.121.0` a
**signed-in** recipient was given the whole authenticated app shell, mobile bottom tab bar included, while
answering. The same link therefore produced two different experiences depending on whether the viewer
happened to be logged in, and offered navigation escape hatches in the middle of a scored assessment.

`AppShell.shouldUseAuthenticatedShell` now excludes any path under `/quiz/`, so anonymous and authenticated
recipients get the identical focused page.

- **⚠️ The exclusion is `/quiz/` ONLY — the recipient route.** The authenticated in-app quiz surfaces live
  under `/notes/…` and `/study-packs/…` and must keep the shell; a test pins both sides, because a
  predicate matching both would pass a guard that only asserted the `/quiz/` case.
- the concept is rendered as a labelled *Topic* chip rather than a bare string under the question stem,
  where it read as a second sentence of the question itself
- the results call-to-action wraps rather than overflowing on a narrow screen, via `buttonVariants`'
  opt-in `wrap`

### ⚠️ `cn` is a plain join, not tailwind-merge

`frontend/lib/utils.ts`'s `cn` is `inputs.filter(Boolean).join(" ")`. It does **not** resolve conflicting
Tailwind utilities, so passing `whitespace-normal` through a `className` leaves **both** it and
`buttonVariants`' base `whitespace-nowrap` in the class list and lets stylesheet order decide the winner.
That is why wrapping is an **option on `buttonVariants`** (`wrap`) that suppresses the conflicting class at
source, rather than an override at the call site. Its guard asserts `whitespace-nowrap` is **absent**, not
merely that `whitespace-normal` is present — the latter passes under the bug. **⚠️ Do not "simplify" this
into a `className` override, and do not remove `whitespace-nowrap` from the shared base, which every other
button in the app depends on.**

## Combined Quiz Snapshots

A combined quiz assembles selected questions from already-generated per-note quizzes into ordered sections.
Each section copies the source note title and its selected `QuizItem`s into `combined_quizzes.sections`; it
never retains a reference to a source note. The snapshot is immutable: there is no update, re-assembly, or
question-edit operation. Creating another assembly makes a new row and therefore needs a separate link.

This is what lets an active combined link survive source-note deletion and source-quiz regeneration. The
public endpoint still receives a flat question list in section order, so `/quiz/{token}` treats either arc
identically. Per-item `sourceStudyPackId` is retained through `QuizItem.withSourceStudyPackId`, but is inert
for anonymous shared play because that path creates no quiz session or Concept Health signal; copied section
titles remain owner-visible snapshot context. The public payload is flat, so recipients see the combined
quiz's title rather than source-note section headers.

## Combined Quiz Owner Flow

Any onboarded, email-verified user starts in **Library → Create → Combined quiz**. The Library picker uses
the existing multi-select and sends the selection to `/library/combined-quiz?notes=…`; it is not a
`/notes/[id]` route and is not gated on profile type or a Learning Connection.

The build page names the combined quiz, reports the contributing notes and running question total, and sends
all question indexes from every selected note with a generated quiz. Notes without a generated quiz are
excluded with an explicit warning, never silently. The owner must first generate a Study Pack and quiz for
each source note that needs one; assembling itself has no generation cost. Requests are blocked before
submission above 20 source notes or 100 questions, while the API remains the final guard if a source quiz
changes between selection and assembly.

After assembly, `/library/combined-quiz/{combinedQuizId}` is the share surface. It loads the immutable
snapshot and uses `GET /combined-quiz-share/{combinedQuizId}` on every visit to discover an existing link,
including one the owner turned off. It only POSTs when the owner explicitly creates a link, so refresh does
not consume a second share link. The owner can copy the link and toggle it on or off; the page has no edit,
regenerate, re-assemble, or export action because another assembly is a distinct immutable quiz and needs
its own link.

Owners can later return through **Library → Combined quizzes** at `/library/combined-quizzes`. This bounded
newest-first list exposes only snapshot metadata (title, when it was made, stored section/question counts,
and whether sharing is on, off, or not created), then links to the existing detail page. The list never
duplicates share controls: revoking or copying remains on `/library/combined-quiz/{combinedQuizId}`.

### Question formats a recipient can be given

`teacher-quiz-developer.txt` can emit four formats, and the shared path handles them differently:

| Format | Graded | Rendered |
|---|---|---|
| `MCQ` | single `correctIndex` | one-shot choice, committed on click |
| `TRUE_FALSE` | single `correctIndex` | same as MCQ, two choices |
| `MULTI_SELECT` | **exact set** against `correctIndices` | checkboxes with *Select all that apply*; editable until Continue |
| `MATCHING` | single `correctIndex` per item | ⚠️ as N unrelated MCQs — see Known limitations |

### ⚠️ MULTI_SELECT grading must route through `QuizSessionReviewUtils.isAnswerCorrect`

**Fixed in `v0.110.0` after shipping broken.** `getSharedQuizResults` used to compare `answer == correctIndex`.
`QuizItem.correctIndex()` **falls back to `correctIndices.getFirst()`** for MULTI_SELECT, so on a question
whose correct answers were `[0, 2]` a recipient selecting both scored **zero**, while one selecting only
choice 0 scored full marks. The prompt instructs 1–2 MULTI_SELECT questions per quiz, so this was live in
effectively every shared quiz, and all ten of the service's tests passed over it.

The shared path now grades through the **same** `QuizSessionReviewUtils.isAnswerCorrect` every in-app mode
uses — exact-set semantics, order-insensitive. **Do not reintroduce a bespoke comparison here**, and do not
grade MULTI_SELECT by overlap or by first index; `QuizShareLinkServiceTest` pins both partial-credit cases.

**The wire format is positional and both halves are required to stay aligned:**

- `answers` carries one entry per question and is **null at a MULTI_SELECT position** — that question has no
  single index
- `multiAnswers` is index-aligned and carries the selections; it is **optional**, so a recipient still holding
  the pre-fix browser bundle keeps submitting successfully (they simply cannot score a MULTI_SELECT question,
  which they could never answer fully anyway)
- both lists are rejected with `InvalidSharedQuizAnswersException` at the wrong length, because a shorter
  `multiAnswers` would grade one question against another's selections rather than failing
- `SharedQuizResultItem.correctIndices` is populated **only** for MULTI_SELECT and empty for every other
  format, so the review screen follows one rule — *prefer `correctIndices` when non-empty, else
  `correctIndex`* — and never branches on the question format itself

## Quotas

Shareable quiz links are plan-gated per billing month:

- Free: `3` links / month
- Plus: `10` links / month
- Pro: unlimited

Runtime tracking uses `user_usage.quiz_share_links_created`. The quiz preview page shows neutral upgrade guidance when the quota is exhausted and resolves its actions through `getUpgradeCtas(currentPlan)`.

### ⚠️ Generating the quiz is metered separately from sharing it

**Two different meters apply, and exhausting either one blocks a different step.** The limits above cover creating the *link*. Producing the quiz that a link points at is a real LLM call and is metered on its own:

- **It draws down the user-facing “Quiz generations” allowance.** `GeneratedQuizService.assertQuizCreditAvailable` reads `user_usage.challenge_quiz_generations` against `resolveMonthlyChallengeQuizLimit` — **Free `20` / Plus `100` / Pro `200`** per month — and `incrementChallengeQuizGeneration` increments that same counter on success. **⚠️ Making a quiz for someone therefore consumes the same monthly allowance as the user's own Challenge Quiz generations.** Exhausting it raises `MonthlyQuizCreditLimitReachedException`.
- **It consumes an AI rate-limit slot** under its own scope, `"generated-quiz"` (`GeneratedQuizService:53`), which is separate from the share-link quota and from other AI scopes.

The shared meter is disclosed before generation as **“Quiz generations”**, described as “Quiz sessions we generate for you, plus quizzes you make for someone. Board Exam sessions also count against their own allowance.” The same pre-generation view shows share links remaining. If links are exhausted, generation remains available for export or later sharing; `QuizShareLimitService` still enforces the share-link cap only when a link is created. No separate generation counter exists, and no quota behaviour changed.

**Question count is a separate axis again, and it is gated on PROFILE, not plan.** Only a `TEACHER` may
choose a count; a `TEACHER` on `FREE` is refused any non-default value with
`QuestionCountNotAllowedForPlanException`.

**⚠️ A non-`TEACHER` who sends a non-default count is REJECTED with `QuestionCountNotSelectableException`
— it must never be silently clamped to `10`.** `resolveQuestionCount` used to `return` the default for
every non-teacher, so a Plus/Pro supporter who picked 30 in the Generate Quiz modal received 10 with no
error and no explanation. Silently discarding a caller's choice is the pattern `v0.103.0` recorded as how a
cap gets bypassed. The rejection is raised before `assertQuizCreditAvailable` and the LLM call, so nothing
is charged; a caller sending nothing, or `10`, is unaffected.

**⚠️ The two exceptions are NOT interchangeable.** `QuestionCountNotAllowedForPlanException` carries
`UPGRADE_TO_PLUS` and `PAYMENT_REQUIRED` — correct for a Teacher on Free, who can actually buy the
capability. `QuestionCountNotSelectableException` is `FORBIDDEN` with **no upgrade action**, because
question count is gated on the Teacher profile and **no plan grants it**. Reusing the plan exception would
sell Plus for something Plus does not give.

**⚠️ The modal therefore shows the count selector to `TEACHER` only** — the same gate Target Level beside
it has always carried. Before that gate, the lock badge and its *"Plus unlocks 20 and 30 questions"* copy
keyed on **plan with no profile check**, so a supporter on Free clicking 20 was shown an upgrade prompt for
a capability Plus would not have given them either. **⚠️ Do not "fix" this by honouring the count for
non-teachers — that raises the cap, which is an untaken pricing decision.**

### The shared quiz is generated, never reused

The quiz behind a share link is a **fresh LLM generation** stored in `GeneratedQuizEntity`. It is not the Study Pack quiz that Quick Review administers (`note.quiz`), not the Challenge Quiz bank, and not an exam question pool.

That separation is deliberate and load-bearing in both directions:

- the shared quiz carries its own **Target Level** and question count, so it can be aimed at the recipient's depth rather than the owner's;
- regeneration passes the previous shared quiz's questions as `disallowedQuestions`, so giving the same person a second quiz yields new questions;
- **⚠️ reusing `note.quiz` would hand the recipient the exact questions the owner is assessed on** — the answer-key exposure `v0.74.0` locked the Quiz tab to prevent. Do not "optimise" this path by reading from the Study Pack quiz.

### How a supporter finds the combined path

The Generate Quiz modal carries a one-time tip pointing at the Library's **Combined quiz** Create-menu item.
**⚠️ That copy names a control by its exact label**, so renaming the menu item falsifies the tip — a pinned
test in `private-note-detail-page-client.test.tsx` guards the pairing. The tip is **not** profile-gated,
because the combined path is not either.

### ⚠️ Regenerating a shared quiz turns its live link OFF

The quiz behind a share link is **mutable**: `GeneratedQuizService.generate` reuses the existing row and
overwrites its questions, so the id never changes and `quiz_share_links.generated_quiz_id` keeps pointing at
it. Before `v0.110.2` that meant a regeneration silently swapped the questions under anyone mid-quiz — a
changed count 400'd them on submit, and an **unchanged** count graded them against questions they never saw.

- Regeneration now deactivates **every live link** for that quiz. **⚠️ Not just the newest:** only `token` is
  unique, `createShareLink` mints a new row over an inactive one, and `findActiveLink` accepts any active
  token, so several live links can point at one quiz.
- **⚠️ It deactivates, never deletes.** Deleting would force a new link and spend share-link quota. The owner
  can turn it back on — which then serves the new questions under the same token, as their informed choice.
- The owner is warned beforehand only when a live link exists, and the panel **re-reads** the link afterwards
  rather than assuming: the effect keys on the quiz id, which a regeneration does not change.

## Known limitations

- **A MATCHING block loses its grouping.** `teacher-quiz-developer.txt` may emit one block of 2–4
  consecutive questions sharing a `questionGroup` and the same four choices. The shared quiz page renders
  each as a standalone question with no *Match each item to one option* header, unlike the in-app
  `QuizMatchingGroup`. **Grading is unaffected** — a MATCHING item carries a single `correctIndex` and is
  graded like an MCQ. This is presentation only, and is the sharing gap `assessment-architecture-audit.md`
  §15 records.
- **IDENTIFICATION and ENUMERATION cannot be graded on the shared path.** Neither format is reachable
  today — `teacher-quiz-developer.txt` does not emit them — but if one ever appeared it would score zero,
  because the recipient has no text input to answer it with.

## Deferred

The following are intentionally out of scope for v0.16.0:

- response tracking
- recipient response summaries
- anonymous or authenticated score persistence for shared-link plays
- recipient identity collection
- quiz-session history entries for shared-link plays
