# analytics.md - NoteLib Feature Context

## Goal

Track key product, conversion, and growth events without disrupting the core user flow.

Analytics should help answer:

- how users move from landing to signup and activation
- which paid-plan prompts lead to upgrade intent
- which Public Library pages drive growth
- how often core review and LLM-powered study flows are used

## Data Model

Analytics events are stored in `analytics_events`.

Fields:

- `id` (`uuid`)
- `user_id` (`uuid`, nullable)
- `event_type` (`string`)
- `entity_id` (`uuid`, nullable)
- `metadata_json` (`jsonb`)
- `created_at` (`timestamp`)

Indexes:

- `event_type`
- `user_id`
- `created_at`

## Event Tracking Rule

- Analytics must be fire-and-forget.
- Tracking failures must never break note creation, generation, review, auth, billing, or copy flows.
- Backend should record server-truth usage and conversion events.
- Frontend may post browser-only funnel events through `POST /api/analytics/events`.

## Event Types

Core usage and business events:

- `NOTE_CREATED`
- `STUDY_PACK_GENERATED`
- `QUICK_REVIEW_STARTED`
- `CHALLENGE_QUIZ_STARTED`
- `ADAPTIVE_PRACTICE_STARTED`
- `PAYWALL_VIEWED`
- `UPGRADE_CLICKED`
- `SUBSCRIPTION_STARTED`
- `PUBLIC_NOTE_VIEWED`
- `PUBLIC_NOTE_COPIED`
- `LOGIN`
- `SIGNUP`

Top-of-funnel and acquisition events:

- `LANDING_PAGE_VIEWED`
- `LANDING_CTA_CLICKED`
- `DEMO_OPENED`
- `PUBLIC_NOTE_COPY_CLICKED`
- `SIGNUP_STARTED`
- `SIGNUP_COMPLETED`
- `EMAIL_VERIFICATION_SENT`
- `EMAIL_VERIFIED`

Feature-activation funnel events (v0.28.0):

- `GUIDANCE_TIP_SHOWN` — a `trackAnalytics` `GuidanceTip` became visible (impression); `metadata.tipId` identifies the tip
- `GUIDANCE_TIP_CTA_CLICKED` — the tip's action button was used; `metadata.tipId` identifies the tip
- `QUIZ_REVIEW_EXPORTED` — a session review PDF was successfully exported; `entity_id` is the note id, `metadata` carries `exportType` and `sessionMode`

These form an impression → click → use funnel: tip shown → CTA click → feature use (an existing `*_STARTED` event, or `QUIZ_REVIEW_EXPORTED` for export). The events are browser-emitted via the analytics API and are fire-and-forget.

## Tracking Sources

Backend-owned events should be emitted from the services that complete the action, including:

- note creation
- Study Pack generation
- Quick Review start
- Challenge Quiz start
- Adaptive Practice start
- subscription activation
- public note view
- public note copy
- login
- signup and verification lifecycle

Frontend/browser-only events should use the analytics API for:

- landing page view
- landing CTA clicks
- demo open
- paywall modal view
- upgrade click
- public-note copy CTA click
- signup start

## Admin Summary Endpoint

`GET /api/admin/analytics/summary`

Returns rollups for:

- landing page views
- landing CTA clicks
- public note views
- public note copy clicks
- signups started
- signups completed
- email verifications completed
- total users
- total notes
- total Study Packs generated
- total Challenge Quiz starts
- total Adaptive Practice starts
- total upgrades

This endpoint is for internal/admin reporting only.
