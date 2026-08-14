# NoteLib — Quiz & Practice Modes

> **Module — not a standalone brief.** Paste `GPT_CONTEXT.md` first; this file assumes it.
> Paste this module when the conversation is about **quiz modes, exam simulation, or practice mechanics**.
> Last updated: v0.76.0 - 2026-08-14

---

## Quiz / Practice Mode Contract

The product has a locked hierarchy of five top-level modes:

1. **Quick Review** - all plans, saved questions, lightweight practice — writes `ConceptHealth` on completion (since 2026-07-11) same as the other assessment modes below. **Since `v0.74.0` it also gates the Study Pack's Quiz tab:** that tab renders the saved quiz *with answers revealed*, and Quick Review administers those same questions — so it was the answer key to its own test. The tab is now **locked (visible, not hidden)** until the learner scores a **perfect** Quick Review; a perfect score reached via the retry round counts. Challenge Quiz stays open from the start because it generates its own questions and cannot be spoiled. **Adaptive Practice is no longer offered from the Quick Review result screen at all** — it remains reachable from the Dashboard and the mode-selection screen.
2. **Challenge Quiz** - all plans with quota, progressive generation up to 20 questions per session.
3. **Adaptive Practice** - Plus/Pro practice targeting weak concepts.
4. **Long Exam** - Pro exam mode, fixed long-form practice, supports multi-note sources.
5. **Board Exam** - Pro high-stakes exam simulation for Exam Reviewer profile.

Professional **Interview Practice** is a sub-mode of Adaptive Practice, not a sixth top-level mode.

Rules:

- Do not add a sixth top-level mode without updating `docs/product/EXAM_MODES.md` and roadmap/spec docs together.
- Premium exam paywalls fire from Start CTAs after setup/prescreen, not from card click.
- Study Plan premium-exam launches carry `collectionId` and scope additional-note pickers to quiz-ready notes in that plan.

### Question formats (a separate axis from modes)

Within the five modes, individual questions carry a `questionFormat`: `MCQ`, `TRUE_FALSE`, `MULTI_SELECT`, `MATCHING`, plus two free-text formats:

- **Identification** — fill-in-the-blank / name-the-term. Scored deterministically against a generation-time `acceptableAnswers[]` list — no per-submission LLM call.
- **Enumeration** — name every item in a 2–5 item set. Scored all-or-nothing via exhaustive bipartite matching against `acceptableAnswerGroups[]` — no partial credit.

Both formats are Challenge Quiz-only for now, and both are **ungated across every plan tier** — a deliberate stance: question-format variety is a learning-quality dimension, not a monetization lever. Monetization stays in mode-level and quota-level gates.

### Non-engine review surfaces: Flashcards and Memorization

Both are free on every plan, live on the Note Detail **Key Concepts tab** (deliberately *not* the quiz-mode CTA row), are hidden in Teacher mode, and exist only on private authenticated Note Detail — never on public notes, public library, or shared quiz links. Neither is a quiz mode: no `QuickReviewSessionEntity`, no session row, no timer, no score, no result screen, nothing in quiz history.

They are frequently mistaken for two skins on the same feature. They are pedagogically different tools, and the difference is load-bearing.

**Flashcards is a coverage pass. Memorization is a retention engine.**

Flashcards is **stateless** — a linear deck with previous/next, flipping concept → definition at your own pace. Nothing is recorded, so every visit yields the identical deck. Memorization is **stateful** — it shows **one due card at a time**, you self-grade it, and that grade rewrites when the card returns, persisted per `user_id` + `study_pack_id` + normalized `concept` in `memorization_cards`.

**Four distinctions that matter:**

1. **Recognition vs. committed retrieval.** Flashcards lets you flip at the first flicker of familiarity — the fluency illusion, where recognizing an answer feels like knowing it. Memorization forces a judgment *after* the attempt, and the judgment has a consequence.
2. **Massed vs. spaced.** Flashcards has no concept of time at all: one sitting, any order, all cards. Memorization distributes across days. Spacing is the mechanism that produces durable memory, and it is the entire reason Memorization is a separate surface rather than a button on the deck.
3. **Coverage vs. drillability — and they deliberately disagree.** Flashcards shows **every** key concept, rendering `No definition yet for this concept.` where no explanation matched. Memorization **excludes** those concepts entirely, because self-grading a card with no answer is meaningless. Only ~56% of key concepts get a matched definition (up from ~18% under exact-only matching), and this is a **permanent structural limit, not a bug to keep chasing**: `keyConcepts` (5–10) and `quiz` (a smaller fixed count) are independently generated, so there will always be more concepts than explanations. **Consequence to expect: Memorization legitimately shows fewer cards than Flashcards on the same note, and shows a caught-up state on days when Flashcards still offers the full deck.**
4. **Neither one measures the learner.** See the firewall below.

**When each is the right tool:** just-generated Study Pack → Flashcards (see the whole landscape, including the gaps). Weeks out from an exam, returning regularly → Memorization (spacing builds durability; the due-card loop is the return mechanism). Night-before cramming → Flashcards (no scheduler telling you a card isn't due). Want to know actual readiness → **neither; take a quiz.**

One-line version: **Flashcards answers "what's in this note?" — Memorization answers "what have I not yet made stick?"**

**Memorization's scheduling algorithm (simplified SM-2).** New cards start `repetitions = 0`, `intervalDays = 0`, `easeFactor = 2.5`, due now. Grades:

| Grade | Interval | Ease factor | Repetitions |
|---|---|---|---|
| **Again** | `0` — due now, returns in the same session | −0.20 (floor `1.3`) | **reset to 0** |
| **Hard** | `max(1, previous × 1.2)` | −0.15 (floor `1.3`) | +1 |
| **Good** | `1` day on first success, then `previous × easeFactor` | unchanged | +1 |
| **Easy** | `4` days on first success, then `previous × easeFactor × 1.3` | **+0.15** | +1 |

On a brand-new card all four collapse to *now / 1 / 1 / 4 days* — they only fan out once a card has history, because Good and Easy compound through `easeFactor` while Hard erodes it. **Again is the only destructive grade:** it zeroes `repetitions`, so a card built up to a 30-day interval restarts from scratch.

**Three firewalls — the most likely things for a future proposal to try to break:**

- **Never writes `ConceptHealth`.** Excluded from `ProgressReportService`, note readiness, plan readiness, My Progress, and `Overall Readiness`. The reasoning, not just the rule: **self-assessment is rehearsal, not evidence.** Only objectively graded quizzes and exams move mastery. A learner could rate every card "Easy" for a month and readiness would not move — that is correct. Wiring SRS recall into readiness would let a learner grade themselves ready, which is exactly the assessment-only mastery boundary this preserves.
- **Never calls the LLM.** Concepts with no matched explanation are *excluded* (Memorization) or shown with an explicit empty state (Flashcards) — never filled by generation. Closing the gap properly would need a real per-concept definition field in Study Pack generation: a schema and prompt change with real token cost, where existing packs would only benefit after regeneration. That is a deliberate non-goal, not an oversight.
- **Not routed through the Quiz Session Engine.** Do not add a `quizSession` discriminator, create a session row, or make either surface count toward quiz performance.

---
