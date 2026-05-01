# pricing.md - NoteLib Feature Context

## Goal

Explain the difference between Free, Plus, and Pro clearly while keeping upgrade flow simple and trustworthy.

The pricing page is a positioning surface first:

- show the core Free workflow
- make Plus and Pro value easy to understand
- keep upgrade messaging student-friendly
- let verified users move into hosted checkout without embedding payment UI on the page

## Plans

NoteLib currently presents three plans:

### Free

- `10` Study Packs / month
- `5` Challenge Quizzes / month
- `2` exports / month
- Summary + Key Concepts

Free-plan limitations that should stay visible on pricing:

- Adaptive Practice is Pro-only
- Difficulty selection is Pro-only
- Board Exam Mode is Pro-only
- Higher note generation limits are on paid plans

Primary CTA:

- `Start for Free`

### Plus

- `₱149` intro first month (PH) when eligible
- `₱179/month` regular price (PH)
- `50` Study Packs / month
- `25` Challenge Quizzes / month
- `15` exports / month
- higher note generation limits

Primary CTA:

- `Choose Plus`

### Pro

- `₱199` intro first month (PH) when eligible
- `₱249/month` regular price (PH)
- `₱1,999/year` regular price (PH)
- `100` Study Packs / month
- `50` Challenge Quizzes / month
- unlimited exports
- Adaptive Practice
- Difficulty selection
- Board Exam Mode

Primary CTA:

- `Choose Pro`

### Pricing Source

- Backend pricing is config-driven and resolved from billing config plus pricing services.
- Intro offers come from the voucher system, not frontend-only flags.
- Pricing page display may use backend pricing data when available and safe frontend fallbacks when not.
- PHP pricing stays visible for Xendit reviewer clarity, and the Regional Pricing block shows PHP plus international USD values.

Current rollout rule:

- Pricing remains a positioning-first surface
- upgrade CTAs start hosted Xendit checkout through the backend payment API

## Pricing page structure

The pricing page should stay clean and mobile-friendly:

1. Hero / framing
2. Free, Plus, and Pro pricing cards
3. Feature comparison table
4. Regional Pricing block (PHP and USD visible)
5. FAQ

Avoid adding embedded payment-step UI, dense billing details, or aggressive conversion copy.

## Comparison table

The pricing page comparison table should use a simple `Feature | Free | Plus | Pro` structure.

Current rows:

- Study Packs / month
- Challenge Quizzes / month
- Exports / month
- Topic note generation
- Summary + Key Concepts
- Adaptive Practice
- Difficulty selection
- Board Exam Mode

## Messaging rules

- Position Plus as the practical step-up for regular study and Pro as the full exam-prep tier
- Keep Free useful and respectful; do not make the Free plan feel broken
- Use simple labels:
  - `Free`
  - `Plus`
  - `Pro`
  - `Start for Free`
  - `Choose Plus`
  - `Choose Pro`
- Keep checkout trust signals short and clear

## Mobile UX

- Pricing cards should stack cleanly on mobile
- CTAs should remain full-width and easy to tap
- The comparison table may scroll horizontally, but it must remain readable
- Intro pricing and regular monthly pricing should remain readable without hiding the renewal amount
