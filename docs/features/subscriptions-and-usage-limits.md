# subscriptions-and-usage-limits.md — NoteLib Feature Context

## Goal

Support freemium usage control and future premium plan behavior.

## Current plan direction

### Demo
- separate from authenticated subscriptions
- should not hit the real LLM pipeline
- no saving
- public route `/demo`
- prebuilt Study Pack with no new LLM call and no real session persistence

### Free
- 5 Study Packs per month
- access to Study Library
- includes Study Pack generation, summaries, key concepts, Quick Review, retry, Today’s Focus, and AI Study Coach

### Premium
- up to 100 Study Packs per month
- access to Study Library
- includes everything in Free
- Premium-only features:
  - Weak Concept Detection
  - Adaptive Quiz Generation

## Subscription design

Use a dedicated `subscriptions` table rather than storing only a plan enum on the user.

Reason:
- plan history
- better analytics
- clearer billing evolution

## Anonymous guardrails

For unauthenticated real generation:
- rate limit by cookie/session and/or IP
- enforce input limits
- optional cooldown

## Future extensions
- billing provider integration
- renewal handling
- cancellation handling
- usage events
- richer plan enforcement

