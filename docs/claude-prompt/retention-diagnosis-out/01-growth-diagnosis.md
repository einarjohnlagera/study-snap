# Retention Root-Cause Diagnosis — Growth/Retention Lens (Fable)

Source: Fable session, 2026-07-15. Grounded directly in the codebase (`docs/features/retention-emails.md`, `RetentionEmailScheduler`, `docs/features/dashboard.md`, `docs/features/onboarding.md`, `docs/product/notelib-facebook-group-marketing-playbook.md`) plus live production data supplied in-prompt (W1→W2 retention screenshot: 2.4%, 3/127; prior read: 5.6%, 2/36).

---

I've grounded this in the actual codebase — the retention email machinery (`docs/features/retention-emails.md`, `RetentionEmailScheduler`), the returning-user Dashboard composition (`docs/features/dashboard.md`), onboarding's persistence contract (`docs/features/onboarding.md`), and the acquisition playbook (`docs/product/notelib-facebook-group-marketing-playbook.md`). Here is the diagnosis.

## First, a calibration note on the data itself

3/127 and 2/36 are small numbers. The apparent worsening (5.6% → 2.4%) is not statistically meaningful — don't conclude the retention releases made things worse, or that anything changed at all. What IS meaningful: even the generous upper bound of the confidence interval on 3/127 is catastrophic. The level is real; the trend is noise. Also worth noting: the two retention releases were shipped **default-OFF**, so from the median user's perspective, you have not yet run a single retention experiment. You shipped retention features to a Settings page that only retained users visit. That's not "tried and failed" — it's "never actually tested."

## Ranked diagnosis

### 1. The return-trigger system is structurally disabled by its own design (highest confidence, highest leverage)

This is your candidate #3, and the code says it's even worse than your framing:

- **The dead loop is designed in.** Onboarding explicitly defers reminder preferences to `/settings` ("Deferred Personalization" section of `onboarding.md` — reminder prefs are *not* collected and *not* persisted at onboarding). The content-rich emails — due-concepts digest, weak-concept nudge, weekly summary — are all default-OFF. So the retention channel requires the user to already be retained (returned, found Settings, opted in) before it can function. For the 97% who never come back, those features effectively don't exist.
- **The one default-ON channel is generic AND globally throttled.** The inactivity email is budgeted against a shared Resend pool: `max(0, dailyLimit(100) − transactionalReserve(40) − sentToday)` — roughly **60 re-engagement sends per day across your entire user base**, with a 3-day cooldown making every inactive user re-eligible twice a week. With weekly cohorts of 16–47 signups accumulating into a mostly-inactive base, eligible-users-per-day plausibly exceeds 60 already, meaning your only default-on channel silently degrades as you grow. Skipped candidates aren't even logged, so you can't currently see the send-vs-eligible gap.
- **The trigger content is inverted from what works.** The generic email ("you've been inactive") is default-ON; the specific, motivating ones ("3 concepts from your Pedia notes are due; LET is in 41 days") are default-OFF. Everything your product knows that would make someone return — concept health, exam countdown, pacing — is rendered only on the Dashboard (`TodayFocusCard`, Board Exam pacing line), i.e., only visible to someone who already came back. The streak and PWA have the same property.
- **The channel itself may be wrong for this audience.** Your users are Filipino LET/PNLE/CPALE reviewees acquired through Facebook Groups. This demographic lives on Facebook/Messenger on mobile; email is a weak reach channel for it. You already have the data to check this: Resend open/click rates by email type. If inactivity-email opens are under ~15%, even a perfect email program has a low ceiling.

### 2. The return visit has a weaker value proposition than the first visit — and the product hides the part that fixes it (high confidence)

This is where I partially push back on the "single-serving utility" fear. It assumes the job-to-be-done is one-shot. But the core audience is board exam reviewees with **months-long, high-stakes, inherently daily study needs** — one of the most structurally habitual audiences that exists. If they don't return, it is not because their job is one-shot. It's because:

- **Session 1's magic is generative; session 2's value is retentional, and retention value is invisible at session 1.** First visit: paste notes → get a quiz, feels like magic. What's the pitch for visit 2? Re-quiz the same pack (feels done), or paste more notes (feels like work — the user must supply content every time). The actual reason to return — spaced review, decaying concept health, mastery toward an exam date — exists in the product but is never *taught or shown* during session 1. A user who finishes their first quiz has no idea the forgetting curve is coming or that NoteLib will manage it for them. They leave believing they extracted the full value.
- **Exam date — the single strongest retention primitive — is optional and Board-only.** Onboarding persists `examDate` only for `BOARD_EXAM`, optionally. The countdown and pacing mechanics that create a *daily reason* to return hinge on it, yet most users never set it. Students and professionals also have exams and deadlines.

Answer to the one-shot question directly: the one-shot behavior is a **symptom, not a structural ceiling** — for the board/exam segments. For casual students who came in via a viral challenge post, it may genuinely be single-serving (see #3).

### 3. Cohort intent: challenge-first Facebook acquisition selects for drive-by users (medium confidence — testable this week)

The marketing playbook is explicit: "People engage with a challenge first. They discover NoteLib second." That funnel recruits people whose intent at signup was *to answer a quiz post in a Facebook group*, not to adopt a study workspace. That population predicts exactly this data shape: high activation (they complete the funnel they came for — it's the same motion as the challenge), one closed loop, then the FB feed scrolls on and nothing re-enters it. Some fraction of the 2.4% is an acquisition-mix artifact, not a product defect. This doesn't exonerate the product — but it means the true retention rate among genuinely-intent users is unknown, and it should be measured before concluding anything about product quality.

## Dispatching the other candidates

- **Content quality: indeterminate from the data available — and the one metric that decides it is missing.** 58.8% quiz-within-7-days tells you the loop closes once; it doesn't tell you if the value was real. The discriminating metric is **week-1 depth**: of activated users, how many take a *second* study action (second pack, second quiz, a return session) within days 1–7, before any cross-week trigger is even relevant? If W1 depth is decent and only cross-week return dies → triggers are the problem (cause #1 dominates). If W1 depth is also near zero → session-1 value is hollow and no trigger will save it. Pull this before building anything.
- **UX friction on return: unlikely to be primary.** The returning-user Dashboard is genuinely well-composed (Continue Studying, Today Focus due-concepts, Focus Areas, streak). The problem is almost nobody reaches that surface. Friction can't be the killer when users never encounter it. Deprioritize.
- **Pricing: confirmed red herring, with one footnote.** Quota is never hit, and retention collapses long before payment relevance. The footnote: paid users retain better everywhere (commitment effect), but pricing cannot be used as a retention lever on a base where 97% are gone by week 2. Revisit pricing psychology only after W1→W2 clears ~15%.

## Hypotheses to validate (diagnosis-scoped, not build specs)

**For cause 1 (triggers):**
- **H1 — Default flip:** Make due-concepts digest and weak-concept emails default-ON for *new* signups (one-click unsubscribe already exists and is compliant). Compare W1→W2 for post-flip cohorts. Nearly zero build cost and the single fastest test of whether triggers move the number.
- **H2 — Capture intent at peak moment:** One question at onboarding completion or post-first-quiz ("Want us to tell you when these concepts are due for review?"). Measure opt-in rate and downstream return delta.
- **H3 — Channel viability audit (data pull, no build):** Resend open/click rates by email type, plus instrument the send-vs-eligible gap the budget cap creates. If email opens are poor for this audience, the strategic conclusion is Messenger/PWA-push territory — a bigger decision, but worth knowing now.

**For cause 2 (return value):**
- **H4 — Make the forgetting curve visible at session 1:** After the first quiz, explicitly show what happens next ("You'll lose ~half of this by Friday — we'll resurface the 3 concepts you missed"). Test whether teaching the retention loop at the moment of first success changes return. Copy/surface-level, not new mechanics.
- **H5 — Natural experiment, run today on existing data:** Compare return rates of users *with* an exam date set vs without. If exam-date users retain meaningfully better, expanding exam-date capture beyond Board-optional is the highest-confidence product change available.

**For cause 3 (cohort intent):**
- **H6 — Segment retention by acquisition source** (challenge-post referral vs organic/direct). If organic retains 3–5x better, part of the fix is funnel framing (position the challenge CTA as "save this topic to review before your exam," selecting for intent) rather than product mechanics.

**The single most important next step is not a feature — it's two data pulls (W1 depth, exam-date natural experiment) and one config-level experiment (default-ON flip for new cohorts).** Those three will tell you, within two or three weekly cohorts, how the blame actually splits between triggers, session-1 value, and acquisition mix — and they cost almost nothing against the current release cadence.
