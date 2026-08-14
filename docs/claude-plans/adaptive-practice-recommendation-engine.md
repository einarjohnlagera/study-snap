# Direction: Adaptive Practice as the recommendation engine

**Status: PHILOSOPHY RATIFIED 2026-08-14 — scope NOT ratified, no version opened, nothing authorized for implementation.**
**Self-contained; written for a product/architecture second opinion. No repo access needed.**

**⚠️ This document contains a hard sequencing constraint (§4). Read it before scoping anything.**

---

## 1. Context for an outside reader

NoteLib is a notes-first study workspace, mainly for Philippine board-exam takers. A learner captures notes, generates an AI "Study Pack", and practises with **exactly five quiz modes** (a locked contract):

- **Quick Review** — the saved 5-question quiz on the Study Pack
- **Challenge Quiz** — generates fresh questions, progressive (5 → 20)
- **Adaptive Practice** — targeted remediation on weak concepts, **quota-limited** (Free: 3/month)
- **Long Exam** / **Board Exam Mode** — full-length and exam-condition simulation

**`ConceptHealth` is the mastery signal.** It is locked (since `v0.37.0`) to move **only from genuine assessment** — self-review surfaces like Flashcards and Memorization are firewalled from it by design.

**The product's positioning line is: "Always know what to learn next."**

---

## 2. The problem with today's model

Today Adaptive Practice is surfaced as **the next step after a quiz**. The learner finishes a Challenge Quiz and is offered Adaptive Practice.

That teaches the wrong mental model. It makes Adaptive Practice feel like *"the system wants me to take another quiz"* — a required next step in a chain — rather than *"the system noticed something I keep struggling with."*

It also spends a scarce, quota-limited resource on weak evidence: **a single poor quiz result is not proof of a persistent weakness.**

---

## 3. Ratified philosophy

**Adaptive Practice stops being "another quiz mode" and becomes the system's recommendation engine.**

- It should appear because the learner has **demonstrated a persistent weakness**, not because they just finished a quiz.
- The signal should come from **patterns across multiple assessments**, not one session.
- It should be **primarily dashboard-driven** — surfaced in Today's Focus / Weak Concepts when evidence has accumulated, e.g. *"You're consistently struggling with Related Rates. Practice now?"*
- **Challenge Quiz completion should celebrate the quiz**, and stop offering Adaptive Practice.
- The learner should feel the system is making a **thoughtful recommendation based on evidence**, not exposing more functionality.

This is the first mechanism that would make *"Always know what to learn next"* literally true rather than aspirational.

**Note this is a smaller change than it sounds in one respect:** the Dashboard already routes to Adaptive Practice from both its due-concepts and weak-concepts branches. The direction is not *"add a dashboard surface"* — it is **"raise the evidence bar before it fires, and make it the primary route."**

---

## 4. ⚠️ Hard sequencing constraint — do not remove the Challenge Quiz entry point before 2026-09-12

**`v0.74.0` already removed Adaptive Practice from the Quick Review result screen**, and that removal carries an open `[CHECKPOINT — due 2026-09-12]` measuring exactly this question: *did removing that route cost Adaptive Practice adoption?* The metric is `ADAPTIVE_PRACTICE_STARTED` per active learner, post-deploy vs. the equivalent pre-deploy window.

**Removing the Challenge Quiz entry point too, before that date, confounds the read.** Two removals, one metric — if adoption falls you cannot attribute it, and the read is destroyed rather than answered. This project has been burned by exactly this class of collision before.

**There is a second, subtler problem.** That checkpoint's stated kill criterion is:

> if Adaptive Practice starts fall materially, the Quick Review route was carrying real discovery and the removal should be reconsidered — **restore a demoted entry point** rather than reverting the whole decision.

**That remedy directly contradicts the direction ratified here.** If adoption drops in September, the standing instruction says put an entry point back; this direction says the answer is a better evidence-based recommendation instead.

**Both were actioned on 2026-08-14, before the read — and the timing is the point.** Changing a kill criterion *after* seeing data would be rationalisation; changing it beforehand with the reason recorded is a decision.

1. **Hold the Challenge Quiz removal until after 2026-09-12**, so the existing read stays clean and attributable.
2. **The checkpoint's remedy is re-specified** in the Backlog Index row. **The result now changes sequencing rather than placement:**
   - **If starts fall materially** — the Quick Review route *was* carrying discovery, so the dashboard recommendation **must be built and proven before any further entry point is removed.** It becomes a *prerequisite* for the Challenge Quiz removal, not an independent improvement.
   - **If starts hold** — the existing dashboard surfaces already suffice and the Challenge Quiz removal is low-risk.

### The read that would actually falsify this direction is not yet measurable

The whole direction rests on learners discovering remediation **from the dashboard**. Testing that requires knowing **where** a start originated — and `ADAPTIVE_PRACTICE_STARTED` (`QuickReviewAdaptivePracticeService.java:201`) carries only `session_id` and `weak_concept_count`, **with no entry-point attribution.**

So the September read can tell us *whether* starts fell, but not *where the surviving ones come from*. **If dashboard-originated starts are near zero, the dashboard-first premise is in trouble before anything is built on it** — and today we could not tell.

This is the same shape as this project's own Challenge-adoption read (c), which sat NOT MEASURABLE for months because `CHALLENGE_QUIZ_STARTED` could not separate *seen-and-ignored* from *never-reached*, and was only closed once impression and click events shipped.

**The prerequisite is small: a source/entry field on that one event.** Adding it does not disturb the primary before/after comparison, which is a total count. **Recommend shipping it before 2026-09-12** so the September read answers the question that matters, rather than only the one that is currently askable.

---

## 5. Architecture finding: the missing layer is CONCEPT IDENTITY

The ratification correctly intuited a missing intermediate layer between mastery and recommendation. The diagnosis is sharper than "we need a learning signal."

**What exists today.** `ConceptHealthEntity` stores, per row:

| field | meaning |
|---|---|
| `user_id`, `study_pack_id`, `concept` | the key |
| `last_correct_at`, `last_incorrect_at` | recency |
| **`incorrect_streak`** | **consecutive incorrect answers** |

**Good news: `incorrect_streak` already exists.** "Repeated evidence rather than one bad session" is **partly available today** — a v1 recommendation does not need a new observation log.

**The real problem: `concept` is a free-text string, keyed per Study Pack.** There is no canonical concept entity. So:

- *"You keep struggling with Related Rates"* works **within one Study Pack**.
- Across Study Packs it **breaks** — the same idea may be stored as "Related Rates" in one pack and "Related Rates Problems" in another, and nothing relates them.

**This is the same class of problem `ADR-001` solved for programs**: a free-text field doing an identity job. It has the same shape and would need the same kind of answer.

**Consequence for sequencing — this is the most useful thing in this document:**

- **Within-pack recommendation is buildable now**, on data that already exists.
- **Cross-pack recommendation** — *"across everything you've studied, this keeps coming back"* — **requires concept identity first**, which is an ADR-sized decision, not a feature.

The ratified language (*"observed across multiple assessments"*) is ambiguous between these two, and they differ by roughly an order of magnitude in cost. **Deciding which one is meant is the first thing to settle.**

---

## 6. Architecture fork: this either consolidates 8 resolvers or becomes the 9th

A pressure test conducted for the Companion Guidance Doctrine found **8 independent "what's next" resolvers already live** across Dashboard, Collection detail and Progress — each with documented reasons for diverging — sitting on only **2 shared signal primitives**. It concluded that merging them is *"a real, costly merge target, not free"*, and set an explicit go/no-go criterion before any merge is attempted.

**A recommendation engine is therefore either the thing that finally justifies consolidating those 8 — or it becomes the 9th.** That fork should be chosen deliberately, not discovered.

**One dependency to be aware of:** that doctrine's **Phase 0 is the unresolved "Primary Review Set vs. Study/Exam Focus" philosophy question**, which is recorded as *blocking*, with three items already waiting behind it. A dashboard-first recommendation engine probably cannot dodge it — *"what should I study next"* and *"what is this learner's primary goal"* are close to the same question.

---

## 7. Product tension to decide early: the Free tier may never see it

Adaptive Practice is quota-limited at **3 sessions/month on Free**. Raising the evidence bar means a Free learner may **never accumulate enough signal to receive a recommendation at all** — making the feature invisible to precisely the tier most likely to convert on it.

The instinct behind the change is right: stop spending scarce quota on one bad quiz. The risk is overshooting into never firing.

*Options to consider: a tier-aware evidence bar, a deliberately generous first recommendation, or surfacing the recommendation while gating the session.*

---

## 8. What this direction does NOT change

- **The five-mode contract is locked.** This does not add a mode, and Adaptive Practice remains one of the five.
- **`ConceptHealth` integrity is untouched** — it moves only from genuine assessment. A recommendation engine *reads* it; it must never write to it.
- **No change to quota amounts or pricing.**
- **Flashcards and Memorization stay firewalled** from readiness.
- **Adaptive Practice stays reachable directly** from the mode-selection screen. This raises the bar on *recommendation*, not on *access* — a learner who knows they want it must still be able to choose it.

---

## 9. What we want from a second opinion

1. **§5 — within-pack vs cross-pack.** Is a within-pack recommendation genuinely useful on its own, or is the cross-pack view ("this keeps coming back everywhere") the thing that makes it feel intelligent? This decides whether concept identity is a prerequisite or a later upgrade.
2. **§6 — is this the justification for consolidating the 8 resolvers**, or should it deliberately stay separate for now?
3. **§4 — is holding the Challenge Quiz removal until after 2026-09-12 right**, or is the read already compromised enough that waiting buys nothing?
4. **§7 — how do you keep an evidence-gated feature discoverable on a tier that may never trigger it?**
5. **What is the right evidence bar?** `incorrect_streak >= 2`? Three assessments? A ratio? What makes a learner feel *"the system noticed"* rather than *"the system nagged"*?
6. **Is there a risk that a purely evidence-gated Adaptive Practice simply stops being used** — trading a too-eager prompt for one that never comes?

---

## 10. The wider philosophy this belongs to

Both this and the *Support Another Learner* direction come from the same place, ratified 2026-08-14:

> **NoteLib should stop adding features and get better at guiding learning.** Whether the system is helping someone support another learner or recommending what to study next, it should make **thoughtful recommendations based on evidence** rather than exposing more functionality.

Future design work should start from that philosophy.
