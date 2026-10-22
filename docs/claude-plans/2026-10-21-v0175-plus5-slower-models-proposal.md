# v0.175.0: "+5 questions" on the slower models, an implementation proposal for owner approval

**Status:** **APPROVED by the owner, 2026-10-21 ("approve the proposal"):** background +5, the recommended option, including its learner-facing copy as written below. Not built yet. It is built with item 6, after 5b merges. It is required by the owner's Q5
decision (`docs/claude-plans/2026-10-21-owner-decisions-v0175-open-questions-and-priorities.md`) before item 6
routes +5 to the tiered models. The owner kept item 6 in v0.175.0 on 2026-10-21.

**Indexing note:** a release artifact for item 6, under the Backlog Index's exemption. It is referenced from
item 6 in `RELEASES.md` once approved.

## What happens today (read from code, 2026-10-21)

- **The server holds a lock for the whole model call.** `generateMoreQuestions` locks the session row
  (`findChallengeSessionForUpdateOrThrow`), then claims from the bank and copies templates, then **calls the
  model inside the same transaction, still holding the lock**, and only then saves.
  - On today's interactive model that takes seconds.
  - On the tiered models a +5 call takes about 55–105 s typically, up to the 180 s client timeout, so each +5
    would hold a database connection and the session lock that long. **That is a defect for item 6, whatever
    the UX choice.**
- **The timer keeps running.** The deadline is fixed when Start Quiz is pressed. While +5 runs, the page
  disables answering. The +90 s per added question is granted **only if +5 succeeds**. So:
  - a slow +5 eats quiz time while the learner can't answer;
  - a failed +5 loses that time entirely;
  - a deadline that passes mid-wait can end the quiz.
- **Duplicate calls:** the page blocks a second click, but the server does not prevent two concurrent +5
  requests on one session.

## Recommendation: background +5 (the direction the owner prefers)

**What the learner sees:**
- They press **+5 Questions** and **keep answering**. A small status shows "Adding questions…", with no time
  promise.
- When the questions are ready, they are **appended at the end** of the quiz with a short notice, "5 questions
  added". Answered questions never move, and no answer or progress is touched.
- If it fails, a notice says "Couldn't add questions" and offers **Try again**. Nothing else changes.
- **Timer fairness:** the learner never loses answering time, because they are never blocked. The existing
  +90 s per added question is applied when the questions are appended. A +5 that hasn't finished when the
  timer ends is discarded; the quiz ends normally.
- **The 45–60 s requirement:** after about 45 s, the status changes to "Still adding questions…" with a **Stop
  adding** option. Stopping cancels the pending +5 for the learner.

**How it works:**
1. `POST …/generate-more` validates and records an **in-flight marker** in the session state, under a short
   lock. It returns at once, starting no model call inside the request.
   - A second +5 while one is in flight returns 409 `MORE_IN_PROGRESS`. **This prevents duplicate paid
     calls.**
2. A background job, on the existing preparation executor and outside any transaction, claims from the bank,
   copies templates, generates the shortfall with the tiered model, and enforces the same deadline rule as
   5b (no paid call starts after it).
3. **The finish step** takes the session lock and checks that the session is still running, the marker is
   still the same, and the timer hasn't expired. Then it persists the new bank rows, appends the questions,
   extends the time limit, and clears the marker.
   - **If the learner stopped or the quiz ended:** the generated questions are saved **unclaimed** to the
     bank for later reuse, nothing is appended, and the time limit is not extended.
4. **The page** polls the existing in-progress read while a marker is set, and appends when the quiz grows. A
   reload keeps working, because the marker and the quiz both live server-side.

**Quota:** +5 consumes no quota today, and that doesn't change.

**Model:** Plus/Pro +5 uses the premium model, so there is no silent downgrade. Item 6 sets the route.

## Alternative: keep +5 blocking and make it fair

- Move the model call out of the transaction and lock. This is required anyway.
- Credit the server-measured wait time back to the time limit, whether +5 succeeds or fails.
- After about 45 s, offer "Continue without them". The request still finishes server-side and banks the
  questions unclaimed.

**It is simpler, but the learner still sits blocked for 1–3 minutes on the slower models.** That conflicts
with "learners should not be forced to wait unnecessarily". **Not recommended.**

## Scope, tests and risk

- **Scope:** item 6 (routing) plus this +5 change, in one Codex brief after 5b merges.
- **Required tests:**
  - on real PostgreSQL: the append versus a concurrent progress save, two concurrent +5 requests (409),
    stopping while in flight (questions banked unclaimed, no extension), and a timer expiring mid-generation
    (nothing appended);
  - page tests: keep answering while adding, the append notice, reload during a +5, and Stop adding;
  - a MockMvc test for the new 409.
- **Verification tier:** Tier H. It changes the running-quiz state, so it is an input to the scoped cold
  review at signoff.
- **Unchanged:**
  - prompts and schemas (the +5 exclusion rule stays OFF behind its switch, Q2);
  - quota;
  - Board Exam;
  - the timer's start rule.

## What I need from the owner

**Approve background +5 as described**, including the new learner-facing copy:
- "Adding questions…";
- "{n} questions added" (the actual count, which can be under 5 after dedup);
- "Couldn't add questions" / "Try again";
- "Still adding questions…" / "Stop adding".

Or choose the blocking alternative instead.
