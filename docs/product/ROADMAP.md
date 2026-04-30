# ROADMAP.md - NoteLib

Rebrand note: StudySnap has been rebranded to NoteLib while preserving current database schema naming unless explicitly changed.

Goal: evolve NoteLib from a one-shot generator into a reusable note-first study workspace.

## Current Release Baseline

`v0.5.0 - Public Profiles & Public Notes` is complete and is now the documentation baseline.

Completed in `v0.5.0`:

- note-first Library and Public Library flows
- public-note discovery and canonical public note routes
- public creator profiles with owner-only public-page controls
- shared note-card previews across Library, Public Library, Public Profile, and public subject pages
- profile/public-profile responsibility split
- standardized icon/button/tab interaction rules
- auth recovery that returns users to the interrupted or last visited page after login

## Next Release

### v0.11.0 - Learning Flow Foundation

Primary focus:

- learning loop positioning across the landing page and product messaging
- onboarding flow redesign: experience-first 5-step flow that ends with a generated Study Pack
- Generate Note from topic available in both onboarding and Create Note
- Create Note UX improvements with write vs generate entry options
- Xendit payment integration with hosted checkout and webhook-confirmed Premium activation
- Xendit payment hardening:
  - correct PHP invoice amount handling
  - pending checkout reuse instead of duplicate pending payments
  - config-driven Monthly and Annual manual checkout amounts
  - automatic intro-offer and voucher application during checkout
  - voucher redemption persistence only after successful `PAID` webhook
  - safe internal `returnUrl` support back to the interrupted page
  - success-page routing that returns Settings/Billing upgrades to Dashboard and paywall upgrades to the interrupted flow
  - polished billing success and failed result pages
  - manual-renewal Premium expiry windows after Monthly (`30` days) and Annual (`365` days) payments
  - subscriptions-table source of truth for plan state, active-subscription history preservation, and webhook-driven renewal extension
- legacy billing-provider runtime removal and local ngrok-based webhook testing support
- copy alignment around `Generate Study Pack`
- activation improvement: users leave onboarding with real content, not an empty dashboard

Implementation stance:

- reposition NoteLib as a guided study system, not only a note-to-quiz utility
- keep Generate Note lightweight and reuse the existing LLM infrastructure
- defer learner level, course/program, engagement mode, and reminders to post-onboarding settings
- avoid heavy backend refactors while making the learning loop more visible in product UX

See `docs/features/onboarding.md` for the full onboarding flow spec.

### v0.6.0 - Landing Revamp & Positioning

Primary focus:

- Landing-page messaging revamp that positions NoteLib as a notes library and study workspace first
- Public Library promotion as a top-level public discovery route
- Learn-page integration for the active-recall study method
- Public navbar alignment across landing, learn, pricing, login, and Public Library
- SEO title, meta description, and Open Graph metadata alignment with the new positioning
- Open Graph image refresh to match the new messaging before the release is cut
- Landing pricing section updated to Free / Plus / Pro cards with intro offer pricing and "Manual renewal. No automatic charges." footer
- Demo page redesigned as a 5-step interactive flow (choose start → input → generated note → Study Pack CTA → Study Pack results) using static prebuilt content only — no backend or LLM calls

Implementation stance:

- keep NoteLib positioned as `Notes Library first, Study Pack second`
- treat Public Library as a public growth and discovery feature, not a paid feature
- keep public marketing pages accessible without login
- align landing, SEO, and README messaging around the same product identity before `v0.6.0` is tagged

### v0.7.0 - Learning & Metadata Foundation

Primary focus:

- Learner Level on the user profile and onboarding
- required Learning Profile `Course / Program` plus optional per-note `Course / Program` metadata
- note-level `courseProgram` metadata with profile-defaulted note creation
- stronger note metadata quality through subject autocomplete, saved custom subjects, and tag guidance
- field-level AI metadata suggestions so users keep final control of title, subject, and tags
- a dedicated `Learning Profile` card on private Profile
- richer Public Profile identity with learner-level/course context when provided
- generation-context plumbing so future quiz prompts can use learner metadata safely

Implementation stance:

- keep learner metadata on the existing `users` aggregate instead of creating profile-type-specific tables
- keep note-level `Course / Program` optional while requiring it for onboarding and later Learning Profile saves
- prepare smarter quiz generation by passing learner metadata through backend generation context before prompt behavior changes
- improve library/public-profile structure over time without changing note ownership or page responsibilities

### v0.8.0 - Board Exam Mode

Primary focus:

- Async Study Pack generation handoff from Note Editor to Note Detail
- Graceful Study Pack generation failure and retry recovery
- Quiz start integrity locks for exam-like Challenge Quiz starts
- Exam Countdown
- Exam Readiness Score
- Study Plan
- Mock Exam Mode
- Performance Analytics

Implementation stance:

- keep Board Exam Mode on the same shared note-first engine
- do not fork entities or tables by profile type
- use the existing `Note -> Study Pack -> Quiz -> Activity -> Weak Concepts` pipeline
- emphasize exam-prep presentation, recommendations, and analytics without merging page responsibilities

## Current Product Shape

Navigation:

- Main:
  - Dashboard
  - Library
  - Public Library
- Account:
  - Profile
  - Settings
  - Admin (admins only)

Core routes:

- `/dashboard`
- `/library`
- `/library/public`
- `/notes/{id}`
- `/notes/{id}/sessions/{sessionId}` for session review
- `/public/library/{subject}`
- `/public/library/{subject}/{slug}`
- `/public/profile/{userId}`

Current session-review UX:

- desktop and mobile both open the same dedicated session-review page from `Recent Sessions`
- Note Detail stays the entry point for history, while the dedicated review page owns focused answer review

## Future Directions

Potential expansion areas after `v0.8.0`:

- richer note workspace
- deeper progress insights from quiz history
- board-exam-specific recommendations and weak-area planning
- optional public-profile enhancements such as followers, likes, and creator bios
- optional snapshot/history tables if product value is proven

### Billing Improvements (Future)

- recurring subscription support
- coupon-code entry UI
- cancel subscription flow
- billing portal / self-serve billing management
- automatic renewal
- invoices / receipts UI
- billing history UI improvements
- plan switching and downgrade flows
- provider-managed recurring billing via `provider_subscription_id`

### Public Library persona filtering (roadmap)

Planned for a future release after the mode system matures:

- persona-based note recommendations in Public Library discovery (same-profile notes ranked higher)
- cross-profile discovery still allowed so learners can find materials outside their profile type
- filtering UI: optional "Relevant to me" toggle that uses the current user's profile mode for ranking
- implementation must remain additive — no ranking change without the toggle enabled
- do not build until there are enough public notes per profile type to make filtering meaningful

## Product Learning Loop

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

Roadmap decisions should reinforce this loop rather than one-time output generation.

## Legacy planning context

Older phase-by-phase roadmap details are preserved in `/docs/legacy/ROADMAP.md`.
