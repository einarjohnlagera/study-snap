# subscriptions-and-usage-limits.md — Study Snap Feature Context

## Goal

Support freemium usage control and future premium plan behavior.

## Current plan direction

### Demo
- separate from authenticated subscriptions
- should not hit the real LLM pipeline
- no saving
- 3-question quiz direction

### Free
- 3 study packs per day
- access to Study Library
- 5-question quiz direction

### Premium
- up to 200 study packs per month
- access to Study Library
- premium-only features later

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

