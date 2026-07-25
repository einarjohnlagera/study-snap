# Plans — Source of Truth

This document is the canonical reference for NoteLib's Free / Plus / Pro plans, their limits, and their feature scope. The numbers here are extracted from `frontend/lib/pricing-config.ts` and `frontend/src/config/plans.ts` (the runtime sources of truth). If pricing or limits change, update those files first, then sync this doc.

## Plans

### Free — *For getting started*

Create notes, generate Study Packs, and review basic concepts.

**Monthly limits**

- `10` Study Packs / month
- `5` Quizzes / month
- `3` Adaptive Practice sessions / month
- `2` exports / month (PDF / DOCX)
- `3` shareable quiz links / month

**Features**

- Summary + Key Concepts
- Quick Review
- Adaptive Practice (taste — `3` sessions / month)

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
- `10` shareable quiz links / month

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
- `10` Interview Practice sessions / month
- `12` Long Exam sessions / month
- `10` Board Exam sessions / month
- Unlimited exports
- Unlimited shareable quiz links

**Features**

- Adaptive Practice (higher limit — `30` sessions / month)
- Interview Practice (`10` sessions / month, Professional profile)
- Long Exam Mode (`12` sessions / month)
- Board Exam Mode (`10` sessions / month, also uses the shared Quiz budget)
- Highest note generation limits (`100` topic notes / month)
- Everything in Plus

---

## Upgrade Ladder

```
Free → Plus → Pro
```

The ladder reflects the user's *study stage*, not just price:

- **Free** is for users who are exploring NoteLib and need basic note → Study Pack → Quick Review, with a small Adaptive Practice allowance to close the weak-area loop.
- **Plus** is for *regular study* — users who study consistently and want Adaptive Practice on weak areas without committing to exam-prep volume.
- **Pro** is for *exam preparation* — board exam takers and serious learners who need Board Exam Mode and the highest generation limits.

Upgrade CTAs must respect the ladder:

- **Free users** see two CTAs: primary `Upgrade to Plus`, secondary `Go Pro`.
- **Plus users** see one CTA: `Upgrade to Pro`.
- **Pro users** see no upgrade CTA.

This rule applies to quiz result pages, the paywall modal, limit-reached screens, and the post-success upgrade nudge.

---

## Runtime Feature Gates

These limits and feature toggles are enforced in the backend; the frontend reads `GET /billing/usage` and `note.adaptivePracticeAvailable` flags to decide what UI to show.

| Capability | Free | Plus | Pro |
| --- | --- | --- | --- |
| Study Packs / month | 10 | 50 | 100 |
| Quizzes / month | 5 | 25 | 50 |
| Exports / month | 2 | 15 | Unlimited |
| Shareable quiz links / month | 3 | 10 | Unlimited |
| Topic note generation | Limited | Higher | Highest |
| Adaptive Practice | 3 sessions | 10 sessions | 30 sessions |
| Interview Practice | — | — | 10 sessions |
| Board Exam Mode | — | — | 10 sessions |
| Long Exam Mode | — | — | 12 sessions |
| Summary + Key Concepts | ✓ | ✓ | ✓ |

---

## Profile-Aware Plan Rules (Implemented)

Some plan limits are adjusted based on the user's profile type. These overrides sit on top of the base plan — the user still pays the same price, but their primary workflow gets more headroom where it matters.

### Teacher Profile — Export Override

**Problem:** For Teacher-profile users, DOCX export is the terminal action of their entire workflow — Generate → View → **Export**. Capping exports at `2` (Free) or `15` (Plus) directly blocks teachers from doing their job, since a single teacher may prepare quizzes across many subjects and classes per month. The LLM cost of a DOCX export is zero (it uses stored `generatedQuiz` data, not new AI calls), so the cost of being generous here is minimal.

**Decision:**

| Plan | Standard export limit | Teacher export limit |
| --- | --- | --- |
| Free | 2 / month | 10 / month |
| Plus | 15 / month | Unlimited |
| Pro | Unlimited | Unlimited (unchanged) |

This applies to **DOCX exports only** (the Teacher Flow format). PDF exports use the standard plan limits — PDF is a student-facing format and does not need a teacher override.

**Rationale:**
- A Teacher on Plus (₱179/mo PH) gets a complete, professional quiz-authoring workflow with no export ceiling.
- Advanced exam-prep features that teachers do not need — Board Exam Mode, Long Exam Mode, Interview Practice, and higher Adaptive Practice volume — remain paid-plan differentiators. The Pro ladder is intact.
- The export override costs nothing in LLM spend. The risk of abuse (non-teachers claiming Teacher profile to get unlimited exports) is low and the downside is limited to a cost-free feature.
- This reflects a deliberate product value: NoteLib should be genuinely useful to Filipino teachers, for whom ₱249/mo Pro is proportionally steep relative to a government teacher's salary.

**UI implications:**
- The Plus plan card (landing page, Settings → Plan & Billing) should surface a teacher-specific callout: *"Teachers get unlimited quiz exports on Plus."*
- The upgrade CTA shown to Teacher-profile Free users should lead with export headroom: *"Unlock more exports — get Plus"* rather than the generic study-focused copy.
- A Teacher-profile Plus user should **not** see *"Upgrade to Pro for unlimited exports"* — that message is no longer true for them. The upgrade nudge for Teacher Plus should focus on the quiz generation limits, not exports.

**Implementation note:** `FeatureGateService` resolves DOCX export limits with `profileType == TEACHER` before applying the plan-based default. The resolved-limit API response exposes separate DOCX and PDF limit / usage fields, and Settings → Plan & Billing must read those profile-aware fields instead of deriving limits from raw plan config.

**Verification:** Profile type is user-declared (honor system). No external verification is required for v0.15.0. If abuse becomes measurable in a future release, a lightweight signal (e.g., checking that the user has generated at least one `generatedQuiz`) can be added without changing the rule structure.

---

## Upgrade Ladder — Teacher Profile Variant

For Teacher-profile users, the upgrade story is about **generation volume**, not quiz modes:

- **Free → Plus:** more exports (10 → unlimited), more Study Packs (10 → 50), more quiz generation (5 → 25), longer teacher quizzes (20 or 30 questions instead of the Free 10-question format), and multiple deterministic exam versions for anti-cheating DOCX exports
- **Plus → Pro:** highest generation limits (50 Study Packs → 100, 25 quizzes → 50), multi-note Exam Builder without session limits

The standard exam-prep framing ("Board Exam Mode", "Adaptive Practice") does not resonate with teachers. Upgrade CTAs shown to Teacher-profile users must use teacher-framed copy. Use `getUpgradeCtas(currentPlan, { profileType })` so Teacher Plus nudges focus on generation volume instead of export headroom.

---

## Where to update

When plan changes happen, update in this order:

1. `frontend/lib/pricing-config.ts` — runtime numbers and prices.
2. `frontend/src/config/plans.ts` — feature lists and CTA labels.
3. Backend plan rules (`PlanRulesService` / `FeatureGateService`) — runtime enforcement.
4. This document — narrative + ladder.
5. `docs/product/SPEC.md` — referencing rules in spec context.
