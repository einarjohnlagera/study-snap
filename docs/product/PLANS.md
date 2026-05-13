# Plans — Source of Truth

This document is the canonical reference for NoteLib's Free / Plus / Pro plans, their limits, and their feature scope. The numbers here are extracted from `frontend/lib/pricing-config.ts` and `frontend/src/config/plans.ts` (the runtime sources of truth). If pricing or limits change, update those files first, then sync this doc.

## Plans

### Free — *For getting started*

Create notes, generate Study Packs, and review basic concepts.

**Monthly limits**

- `10` Study Packs / month
- `5` Quizzes / month
- `2` exports / month (PDF / DOCX)

**Features**

- Summary + Key Concepts
- Quick Review

---

### Plus — *For regular study*

Perfect for students who want consistent review and better retention.

**Pricing**

- PH: ₱179 / month (intro: ₱149)
- Default (USD): $3.99 / month
- Annual not yet available

**Monthly limits**

- `50` Study Packs / month
- `25` Quizzes / month
- `15` exports / month
- `10` Adaptive Practice sessions / month

**Features**

- Adaptive Practice (limited — `10` sessions / month)
- Higher note generation limits (`25` topic notes / month)
- Everything in Free

---

### Pro — *Best for exam prep*

Designed for serious learners preparing for board and entrance exams.

**Pricing**

- PH: ₱249 / month (intro: ₱199), ₱1,999 / year
- Default (USD): $4.99 / month, $39.99 / year

**Monthly limits**

- `100` Study Packs / month
- `50` Quizzes / month
- `30` Adaptive Practice sessions / month
- Unlimited exports

**Features**

- Adaptive Practice (higher limit — `30` sessions / month)
- Difficulty selection (Easy / Medium / Hard)
- Board Exam Mode
- Long Exam Mode
- Highest note generation limits (`100` topic notes / month)
- Everything in Plus

---

## Upgrade Ladder

```
Free → Plus → Pro
```

The ladder reflects the user's *study stage*, not just price:

- **Free** is for users who are exploring NoteLib and need basic note → Study Pack → Quick Review.
- **Plus** is for *regular study* — users who study consistently and want Adaptive Practice on weak areas without committing to exam-prep volume.
- **Pro** is for *exam preparation* — board exam takers and serious learners who need difficulty selection, Board Exam Mode, and the highest generation limits.

Upgrade CTAs must respect the ladder:

- **Free users** see two CTAs: primary `Upgrade to Plus`, secondary `Go Pro`.
- **Plus users** see one CTA: `Upgrade to Pro`.
- **Pro users** see no upgrade CTA.

This rule applies to quiz result pages, the paywall modal, limit-reached screens, and the post-success upgrade nudge.

---

## Runtime Feature Gates

These limits and feature toggles are enforced in the backend; the frontend reads `GET /billing/usage` and `note.adaptivePracticeAvailable` / `note.difficultySelectionAvailable` flags to decide what UI to show.

| Capability | Free | Plus | Pro |
| --- | --- | --- | --- |
| Study Packs / month | 10 | 50 | 100 |
| Quizzes / month | 5 | 25 | 50 |
| Exports / month | 2 | 15 | Unlimited |
| Topic note generation | Limited | Higher | Highest |
| Adaptive Practice | — | 10 sessions | 30 sessions |
| Difficulty selection | — | — | ✓ |
| Board Exam Mode | — | — | ✓ |
| Long Exam Mode | — | — | ✓ |
| Summary + Key Concepts | ✓ | ✓ | ✓ |

---

## Where to update

When plan changes happen, update in this order:

1. `frontend/lib/pricing-config.ts` — runtime numbers and prices.
2. `frontend/src/config/plans.ts` — feature lists and CTA labels.
3. Backend plan rules (`PlanRulesService` etc.) — runtime enforcement.
4. This document — narrative + ladder.
5. `docs/product/SPEC.md` — referencing rules in spec context.
