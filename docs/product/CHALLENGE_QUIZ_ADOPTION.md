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

## Decided approach (June 2026)

> This is a **value-is-unclear (motivation) problem, not a button-placement problem.**

That framing is the load-bearing part of the decision, and it is what rules out the obvious alternative of moving or enlarging the entry point. Two reinforcing moves follow from it.

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

The first is the one that falsifies the decision's own framing. The third has a hard requirement the others do not: it needs a **before** figure spanning the June 2026 change, so the further this slips the harder it gets to answer honestly.

These are unmeasured obligations rather than backlog candidates — they judge something already shipped. If they are to be tracked rather than remembered, they belong as a Backlog Index row in `docs/product/ROADMAP.md` with an explicit gate.

---

## Key marketing principle (retained from the same source)

> People do not visit NoteLib because they want NoteLib.
>
> People visit NoteLib because they want to answer questions, learn concepts, and prepare for exams.

Always lead with the learner's goal. Never lead with the product.
