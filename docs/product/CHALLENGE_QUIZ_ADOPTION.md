# CHALLENGE_QUIZ_ADOPTION.md — the Challenge Quiz adoption problem and the June 2026 decision

## Purpose

This document records **why** Challenge Quiz is promoted where it is, and what was deliberately decided about growing its adoption. The shipped *behavior* lives in `docs/features/quick-review.md` (post-session next-step rules) and `docs/features/challenge-quiz.md`; this document is the decision and its reasoning, which those feature docs do not carry.

**Provenance.** Originally section "Challenge Quiz Adoption Problem" of `docs/product/notelib-facebook-group-marketing-playbook.md`, which was superseded by `docs/gpt-contexts/NoteLib_Marketing_Strategist_Context_v2.md` on 2026-08-03. v2 is a GPT strategist role prompt and dropped this section, so it was restored here rather than left in git history — it is a product decision, not marketing copy, and the validation items below were never closed.

---

## The observation

Many users view notes and use Quick Review. Very few use Challenge Quiz.

## The hypothesis

Users understand Quick Review immediately. Challenge Quiz's value is unclear. Users may not realize that it:

- generates new questions
- creates fresh practice opportunities
- provides additional exam simulation

## ⚠️ Status of this document's central claim, as of 2026-08-13

**The framing below is UNCONFIRMED. Do not cite it as settled.**

Reads (a) and (b) were run against production on **2026-08-13**, before `v0.74.0` deployed, and the kill criterion recorded in `ROADMAP.md`'s Backlog Index was **met**:

- Conversion per completed Quick Review, before vs after the 5/5 → 4/5 promotion change: **41.2% → 44.7% at 24h (+0.37 SE)** and **58.8% → 47.0% at 7d (−1.25 SE)**. Neither difference is distinguishable from zero.
- **The population shift makes this a harder negative, not a softer one.** Sessions actually reaching the `>= 4/5` promotion went **68.6% → 92.0%**. Far more learners saw the promotion, and conversion still did not rise.
- Return rate of converted learners fell (20.0% → 11.0%; 13.3% → 9.6% on the 14-day-observable cut), though on numerators of 2–9 that only corroborates.

**The promotion change has already been reverted** — `v0.74.0` moved it back from `>= 4/5` to verified mastery — so nothing needs building in response. What is owed is a **reopening of the framing**, not another promotion tweak on top of it.

**The read that can actually settle this is now possible for the first time.** Read (c) — Challenge CTA impressions vs clicks — was blocked from June 2026 until `v0.74.0` shipped `POST_SESSION_CHALLENGE_CTA_IMPRESSION` and `_CLICKED`. Only that read separates *seen-and-ignored* (motivation) from *never-reached* (placement). It carries `[CHECKPOINT — due 2026-10-15]`.

**Until (c) reports, treat "motivation, not placement" as an open question.** In particular, the alternative it ruled out — moving or enlarging the entry point — is no longer ruled out on evidence.

---

## Decided approach (June 2026)

> This is a **value-is-unclear (motivation) problem, not a button-placement problem.**

That framing is the load-bearing part of the decision, and it is what rules out the obvious alternative of moving or enlarging the entry point. Two reinforcing moves follow from it. **See the status note above: this framing did not survive its own validation reads and is currently unconfirmed.**

### 1. Product — auto-promote Challenge at the right moment — **SHIPPED**

The post-session next-step promotes **Take a Challenge** as the *primary* next action after a strong-majority Quick Review (at most one missed concept, i.e. >= 4/5), not only a perfect 5/5. With a single miss, **Retry Incorrect Questions** is kept as a secondary action so the miss is not lost; two or more misses still lead with retry.

This matters because the converting cohort copies a public note and lands *directly* in a Quick Review session — they bypass the note-detail page and only ever see this post-session surface.

Current behavior and its edge cases are documented in `docs/features/quick-review.md` (see the post-session next-step rules and the note that this reads the completed session's stored `weakConcepts` rather than a `ConceptHealth` write from the same session). **That feature doc is the source of truth for the behavior; if the two ever disagree, the feature doc wins and this section should be corrected.**

### 2. Marketing — educate in the answer-reveal, never as a product pitch — **execution, not code**

Fold the Challenge Quiz education into the **Phase 3 answer-reveal** of the highest-engagement LET and PNLE posts (these keep pulling engagement for weeks). Do **not** post it as a standalone product comment, and do **not** put it in the original challenge post. ALE was skipped as low priority for that cycle.

Core framing to reuse:

> Quick Review uses saved questions (recall). Challenge Quiz generates NEW AI-powered questions every time — timed, so it feels like the real exam. Fresh practice, more variety, real exam simulation.

Attach a **tightly cropped screenshot** of the Challenge Quiz button plus the helper line *"Quick Review uses saved questions • Challenge Quiz generates new timed questions"*. Prefer the mobile crop, crop out the status bar, and screenshot from a non-admin account so the nav matches what a real user sees. Let the helper line carry the message: one highlight on the button, no busy chrome.

### The reinforcing loop

```text
answer-reveal → Public Library note link → copy → Quick Review → (at >= 4/5) product auto-nudges into Challenge Quiz
```

The reveal teaches the concept; the product walks them into Challenge at the right moment. Neither half works as well alone, which is why the decision is recorded as one decision rather than two.

---

## Validation — deliberately deferred, and still open as of 2026-08-03

The original decision said **"validate later, don't pre-optimize."** These three reads were named at the time and, as far as this repo records, none has been run:

| Question | What it discriminates |
|---|---|
| Challenge CTA impressions vs. clicks | seen-and-ignored (motivation) vs. never-reached (placement) — i.e. whether the core hypothesis was right |
| Return rate of converted users | if most are one-and-done, the lever is re-engagement, not buttons |
| Quick Review → Challenge conversion rate, before vs. after the 5/5 → 4/5 change | whether the shipped product half actually moved anything |

**Instrumentation audit, 2026-08-03 — the three do not have equal standing:**

- **Read 3 is measurable now.** `CHALLENGE_QUIZ_STARTED` (added 2026-03-23) and `QUICK_REVIEW_COMPLETED` (added 2026-05-05) both predate the June 2026 change, so a real before window exists. That window is fixed and already closed, so this read does not get *better* by waiting — it only accumulates denominator.
- **Read 2 is measurable now**, from `PUBLIC_NOTE_COPY_CLICKED` plus session data. It needs a query, not new events.
- **Read 1 is not measurable.** There is no impression event and no click event for the post-session Challenge CTA. `CHALLENGE_QUIZ_STARTED` cannot separate *seen-and-ignored* from *never-reached*, which is the entire discrimination. Closing it requires shipping two events first — a CTA impression and a CTA click. That is a prerequisite, not part of the read.

Read 1 being the un-instrumented one is the uncomfortable part: it is the only one that tests *why* adoption is low, and therefore the only one that can falsify the "motivation, not placement" framing. Reads 2 and 3 can only tell you whether the chosen fix moved the number.

**Tracked** as a `[CHECKPOINT — due 2026-09-30]` row in `docs/product/ROADMAP.md`'s Backlog Index, with a named kill criterion and a denominator clause. That row is the operative obligation; this section is the reasoning behind it.

---

## Key marketing principle (retained from the same source)

> People do not visit NoteLib because they want NoteLib.
>
> People visit NoteLib because they want to answer questions, learn concepts, and prepare for exams.

Always lead with the learner's goal. Never lead with the product.
