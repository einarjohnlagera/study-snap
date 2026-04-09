# pricing.md - NoteLib Feature Context

## Goal

Explain the difference between Free and Premium clearly, without turning the pricing page into a payment flow.

The pricing page is a positioning surface first:

- show the core Free workflow
- make Premium value easy to understand
- keep upgrade messaging student-friendly
- route upgrade intent into the Premium waitlist flow until checkout launches

## Plans

NoteLib currently presents two plans only:

### Free

- `10` Study Packs / month
- `5` Challenge Quizzes / month
- AI Summary + Key Concepts
- Weak Concepts tracking
- Board Exam Mode is available on Free for a limited time

Free-plan limitations that should stay visible on pricing:

- Adaptive Practice is Premium-only
- Difficulty selection is Premium-only

Primary CTA:

- `Start for Free`

### Premium

- higher monthly limits
- Adaptive Practice
- Difficulty selection
- Board Exam Mode

Premium pricing must still come from the backend pricing API / shared pricing components.

Primary CTA:

- `Upgrade to Premium`

Current rollout rule:

- Pricing is for positioning only
- upgrade CTAs open the Premium waitlist modal instead of checkout

## Pricing page structure

The pricing page should stay clean and mobile-friendly:

1. Hero / framing
2. Free and Premium pricing cards
3. Feature comparison table

Avoid adding payment-step UI, dense billing details, or aggressive conversion copy.

## Board Exam Mode positioning

Board Exam Mode must be visible in:

- pricing cards
- comparison table

Current pricing-page rule:

- Free card should show `Board Exam Mode (Free for limited time)`
- comparison table should make the limited-time Free access explicit

This helps users understand that Board Exam Mode exists today without implying a separate billing product.

## Comparison table

The pricing page comparison table should use a simple `Feature | Free | Premium` structure.

Current rows:

- Study Packs / month
- Challenge Quizzes / month
- AI Summary + Key Concepts
- Weak Concepts tracking
- Adaptive Practice
- Difficulty selection
- Board Exam Mode

## Messaging rules

- Position Premium as deeper quiz practice and heavier exam-week support, not just an AI upsell
- Keep Free useful and respectful; do not make the Free plan feel broken
- Use simple labels:
  - `Free`
  - `Premium`
  - `Start for Free`
  - `Upgrade to Premium`
- Avoid introducing payment-specific friction until checkout is ready

## Mobile UX

- Pricing cards should stack cleanly on mobile
- CTAs should remain full-width and easy to tap
- The comparison table may scroll horizontally, but it must remain readable
- Board Exam limited-time messaging should remain visible without requiring a tooltip or hidden disclosure
