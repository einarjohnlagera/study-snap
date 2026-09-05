# Shared Quiz — Recipient Experience Audit & Plan

**Status:** PLAN ONLY — nothing implemented. Written 2026-09-05.
**Origin:** two owner screenshots from real use on mobile, taken while signed in.
**Surface:** `/quiz/[token]` — the "Quiz for someone" recipient flow (`frontend/app/quiz/[token]/page.tsx`, 340 lines).

**⚠️ Two of the five items are not what the report describes, and the audit found a sixth the report
did not mention.** Read §0 first.

---

## §0. Executive summary

| # | Reported as | Actually |
|---|---|---|
| 1 | Missing question navigation | **Design line is real** — navigator is exam-only — **but the shared quiz is *structurally* forward-only**, which is a separate and worse problem (§1) |
| 2 | Bottom menu should be hidden on every quiz | **The mechanism already exists** and is applied to Board Exam + Long Exam only. Extending it is a product decision, not a bug fix — and for *this* surface it only affects signed-in recipients (§2) |
| 3 | Broken CTA button | **Confirmed** — `whitespace-nowrap` + fixed `h-10` (§4) |
| 4 | No way to copy the note(s) | **Not a missing menu** — the payload carries no note identity at all, deliberately (§5) |
| 5 | — | **NEW: `questionGroup` is dropped from `PublicQuizItem`**, so matching questions are unanswerable on this surface (§3) |
| 6 | — | **NEW: `concept` renders as bare grey text under the stem**, reading as part of the question (§3) |

**Item 5 is the most serious** — it is the same defect class `v0.110.0` fixed for MULTI_SELECT, and it
means a recipient can be scored on a question the surface cannot present.

---

## §1. Question navigation

**The honest answer to "did we design it this way?" — yes, but the design line is exam-mode, not
this surface.**

`QuestionNavigator` (`components/exam-mode/question-navigator.tsx`) has exactly **two** consumers:
`app/notes/[id]/long-exam/page.tsx` and `app/study-packs/[id]/challenge-quiz/page.tsx:2377-2385`.
Its tone type is `"challenge" | "long-exam" | "board-exam"` — all exam modes. **Quick Review,
Adaptive Practice, Interview Practice and the shared quiz all lack it.** So the shared quiz is
consistent with every non-exam surface.

**⚠️ But that consistency hides the real problem, and it is worse than a missing grid: the shared
quiz cannot go back at all, structurally.** Answers accumulate in an **append-only array**:

```ts
const nextAnswers = [...answers, isMultiSelect ? null : selectedAnswer];   // :99
setCurrentIndex((index) => index + 1);                                     // :107
```

There is no index-keyed answer store, so revisiting question 3 would *append* rather than replace.
**A misclick is therefore unrecoverable** — and unlike every in-app mode, there is no session row to
fall back on (`GeneratedQuizService` and `QuizShareLinkService` reference `quick_review_sessions`
zero times), so state is pure client React state and a refresh already loses the whole attempt.

**Recommendation — and this is where I'd challenge the framing.** The ask is a navigator; the need is
**back/forward**. Ship:

- re-key `answers` / `multiAnswers` from append-only arrays to **index-addressed** storage;
- add **Previous / Next**, with Next disabled until the current question is answered (preserving
  today's "must answer to advance" rule);
- keep the existing single primary button semantics on the last question (`Submit Answers`).

**Do NOT add the full `QuestionNavigator` grid here in v1.** It is an exam affordance for 20–50
question sessions with a time limit; this is a 10-question one-pass quiz often taken by someone
without an account. Back/forward removes the trap; the grid adds a surface with no evidence behind
it. **If the owner wants parity with Challenge Quiz, that is a deliberate scope call, not a defect
fix** — say so rather than inferring it.

**⚠️ Do not add answer persistence in this release.** It implies either a session row for an
anonymous recipient or `localStorage`, and `AGENTS.md` records that public note pages must not
persist anonymous session state. Out of scope; state the refresh-loses-progress residual as a Known
limitation instead.

---

## §2. Bottom navigation during quizzes

**The mechanism already exists — this is not new construction.** `ExamFocusProvider` /
`useExamFocusMode` (`components/exam-mode/exam-focus-context.tsx`) drives
`shouldShowMobileBottomTabs = user.mobileTabBarEnabled && !isExamFocusActive && !isBottomViewportClaimed`
(`app-shell.tsx:510`) and also collapses the sidebar, header and drawer (`:525-648`).

**Where it is applied today — narrower than expected:**

| Surface | Focus mode |
|---|---|
| Long Exam | **Yes** — `useExamFocusMode(phase === "running")` (`long-exam/page.tsx:255`) |
| Challenge Quiz — Board Exam mode | **Yes** — `useExamFocusMode(isBoardExamMode && phase === "running")` (`:1502`) |
| Challenge Quiz — ordinary | **No** |
| Quick Review · Adaptive Practice · Interview Practice | **No** |
| Shared quiz | **No** |

**⚠️ So "the bottom menu should be hidden on every quiz" is a product change extending a
Board-Exam-only affordance to every quiz — not a bug fix.** Stated plainly because it will otherwise
be implemented as though something regressed.

**⚠️ And for this surface specifically it is narrower than the screenshot suggests.** The shell only
renders when `shouldUseAuthenticatedShell(hasAuthUser, pathname)` is true (`app-shell.tsx:63`), so an
**anonymous recipient already sees no bottom nav.** Only a signed-in user taking someone else's
shared quiz sees it — which is exactly the screenshot. The gap is real but affects a narrower
population here than on Quick Review or ordinary Challenge Quiz, where *every* user sees it.

**Recommendation:** apply `useExamFocusMode(true)` while a quiz is in progress on the shared quiz,
Quick Review, Adaptive Practice, Interview Practice and ordinary Challenge Quiz — one hook call per
page, no new mechanism.

**⚠️ One trade to decide before implementing, not after: focus mode removes the escape route.** Board
Exam and Long Exam can afford that because they are formal timed sessions with a `BackLink` in the
page. **The shared quiz page renders no `BackLink` at all**, so hiding the tab bar leaves a signed-in
recipient with the browser back button as the only exit. If focus mode is applied here, **the page
owes an in-page exit affordance** — otherwise this fix trades one annoyance for a worse one.

---

## §3. ⚠️ NEW — matching questions are unanswerable on this surface

**Found during the audit; not in the report; the most serious item here.**

`QuizItem` carries **`questionGroup`** (`dto/QuizItem.java:27`). `PublicQuizItem` carries only
`question`, `choices`, `concept`, `questionFormat` — **`questionGroup` is dropped** at
`QuizShareLinkService.java:157`.

Matching questions in NoteLib are a **group of sibling questions** sharing a `questionGroup` label,
rendered together by `components/study-pack/quiz-matching-group.tsx`; `ChallengeQuizService`'s
`shuffleQuestionOrderPreservingMatchingGroups` exists specifically to keep those siblings contiguous.
**With the group label stripped, the shared quiz page has no way to render them as a group** — it
branches on exactly one format (`isMultiSelect = questionFormat === "MULTI_SELECT"`, `:82`) and
treats everything else as single-choice.

Screenshot 1 is exactly this: a stem listing four elements to match (*1) Grading 2) Lighting
3) Native Planting 4) Circulation*) with four choices and a **single** selection. The recipient
cannot express a matching answer, and is then scored on it.

**This is the same defect class `v0.110.0` fixed** — that release added `questionFormat` to
`PublicQuizItem` because MULTI_SELECT questions were silently mis-graded. `questionGroup` was missed
in the same sweep.

**Two candidate fixes — recommendation: (b) for v1.**

- **(a) Support matching on the shared surface** — carry `questionGroup` and reuse
  `QuizMatchingGroup`. Correct, larger, and touches grading (`getSharedQuizResults` is positional).
- **(b) Exclude matching questions from shareable quizzes at generation time**, so the format never
  reaches a surface that cannot present it. Smaller, and honest.
  **⚠️ But it does nothing for already-shared quizzes**, which is the same residual shape
  `v0.110.1` recorded — state it rather than implying a full fix.

**⚠️ Do NOT "fix" this by hiding the concept line or rewording the stem.** The stem is correct; the
presentation contract is what is missing.

**Also in this item — §6 from the summary:** `currentQuestion.concept` renders as bare grey text
directly beneath the stem (`:274-275`) with no label. In the screenshot *"environmental regulation"*
reads as part of the question. Recommendation: label it or drop it — it is metadata, not question
text, and no other quiz surface renders a bare concept under the stem.

---

## §4. The broken CTA button — confirmed, root cause exact

```tsx
<TrackedLink ... className={buttonVariants({ className: "w-full sm:w-auto" })}>
  Save your score and start studying with your own notes
</TrackedLink>                                                    // :193-201
```

`buttonVariants`' base string contains **`whitespace-nowrap`** and `size="default"` resolves to
**`h-10`** — a fixed height (`components/ui/button.tsx:18`, `:31`). A 54-character label inside
`w-full` on a ~390px viewport can neither wrap nor grow, so it overflows both rounded edges. That is
precisely screenshot 2.

**Recommendation — do both:**

1. **Shorten the label.** *"Save your score"* or *"Save your score — create a free account"*. The
   current sentence tries to carry the whole value proposition in a button.
2. **Allow wrapping for this instance** — override to `whitespace-normal h-auto py-2.5 text-center`.
   The supporting sentence, if wanted, belongs in a `<p>` beneath the button, not inside it.

**⚠️ Check for siblings before shipping.** Any other long-label `buttonVariants` call in a
`w-full` mobile context has the same latent break; this is a sweep-by-surface item, not a one-line
fix.

---

## §5. "What's next" — copying the note(s)

**⚠️ This is not a missing menu. The payload carries no note identity at all.**

```java
public record PublicSharedQuizResponse(UUID quizId, String noteTitle, List<PublicQuizItem> questions)
```

There is **no note id, no visibility, and no source-note list** — only a display title. The endpoint
is `permitAll`, so this is almost certainly deliberate: exposing note ids on an anonymous surface
would leak the identity of what may be a **private** note. And `v0.110.0` shipped **combined**
quizzes spanning several notes, which is why the owner's instinct says "note or notes" — but the
response models neither.

**So "add a copy menu" is a capability decision, not a fix.** Two constraints make it narrow:

1. **Only PUBLIC source notes can be offered.** A shared quiz generated from a private note has
   nothing the recipient may copy, and offering it would contradict the note-sharing model
   (`v0.91.0`: sharing a note is an explicit grant, not a side effect of receiving a quiz).
2. **Copying requires an account**, so for an anonymous recipient the honest next step is still
   signup — which is what the CTA already is.

**Recommendation for v1:** extend `PublicSharedQuizResponse` with
`sourceNotes: [{ id, title }]` **filtered to PUBLIC notes only**, and render a *"Study these notes"*
section on the results screen linking to the public note pages — where **Copy to my Library already
exists**. Do not build a second copy mechanism on this surface.

**⚠️ If the source notes are private, render nothing** — not a disabled control, not an explanation
that reveals a private note exists.

**⚠️ Do NOT propagate anything to the quiz owner, and do not notify them** — out of scope, and it is
the adjacent shape `v0.115.0`/`v0.116.0` deliberately fence off.

---

## §6. Slices and routing

| Slice | Content | Route |
|---|---|---|
| **1** | CTA fix + concept label (§4, §3 tail) | Claude Code inline |
| **2** | Index-keyed answers + Previous/Next + in-page exit affordance (§1) | Claude Code inline |
| **3** | Focus mode across quiz surfaces (§2) | Claude Code inline |
| **4** | Matching-question exclusion **or** group support (§3) | **Codex** if (a); inline if (b) |
| **5** | Public source notes on the results screen (§5) | **Codex** — DTO + service + UI |

**Not for v0.118.0.** That release is mid-flight on Note + Study Pack regeneration; these are an
unrelated surface. Recommend **v0.119.0**.

**Slice 4 is the one to sequence first if only one ships** — it is the only item where a recipient is
currently *scored on a question they cannot answer*.

---

## §7. Verification

**Tier: a single `advisor()` call** for slices 1–3 (presentation only, no permission substrate, no
money semantics). **One scoped cold agent** if slice 5 ships, because it adds a new field to an
anonymous `permitAll` payload — a cross-boundary read.

**Pre-declared discriminating guards:**

1. **§1** — answer question 1, advance, go **back**, change it, submit: the changed answer must be
   graded. *A forward-only fixture passes under the append-only defect and proves nothing.*
2. **§3** — a shared quiz containing a matching group must either render it as a group or **not
   contain one**. Assert against a quiz that actually has `questionGroup` set.
3. **§4** — assert the CTA's rendered label at a narrow viewport, or pin a shorter string. *A desktop
   fixture passes while mobile overflows.*
4. **§5** — a shared quiz whose source note is **PRIVATE** must expose **no note id** in the response.
   *A public-note fixture passes under a version that leaks private ids.*
5. **§2** — the tab bar must be hidden while a quiz is running **and restored on exit**, including
   when the recipient leaves mid-quiz.

## §8. Anti-drift

- **⚠️ Do NOT persist anonymous recipient answers** (session row or `localStorage`) — `AGENTS.md`
  forbids anonymous session state on public surfaces. Refresh-loses-progress stays a Known limitation.
- **⚠️ Do NOT expose `correctIndex`, `correctIndices` or `explanation` in `PublicQuizItem`** — the
  record's own javadoc says it is the only thing enforcing that.
- **⚠️ Do NOT expose private note ids or titles** on this surface (§5).
- **⚠️ Do NOT change `QUIZ_SHARE_LINK_CREATED` or `QUIZ_SHARE_LINK_OPENED`** — dated checkpoints read
  them; slice 5 adds a surface, not an event change.
- **⚠️ Do NOT add the full QuestionNavigator grid** without an explicit owner decision (§1).
- **⚠️ No quota, entitlement or meter change; no new quiz mode or sub-mode.**
- **⚠️ `frontend/app/onboarding` is untouched.**
