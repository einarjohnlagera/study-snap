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

`analytics_events.user_id` is intentionally nullable and indexed but has no hard foreign key to `users(id)`.
Telemetry must survive referential timing during signup and account lifecycle changes; orphaned `user_id`
values are acceptable for analytics reporting.

## Event Tracking Rule

- Analytics must be fire-and-forget.
- Tracking failures must never break note creation, generation, review, auth, billing, or copy flows.
- Backend analytics persistence fires after the surrounding transaction commits via
  `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`.
- The after-commit listener dispatches the actual database write to `analyticsTaskExecutor`, preserving
  off-request, non-blocking persistence. Events fired outside a transaction use fallback execution.
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
- `CHECKOUT_INITIATED`
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
- `PUBLISHED_PLANS_VIEWED` — the Official Review Set / published Study Plan catalog mounted
- `OFFLINE_FALLBACK_SERVED` — the service worker served `offline.html` after a navigation request failed; metadata carries the failed path

### First-touch signup attribution

New users may carry nullable `utm_source`, `utm_medium`, `utm_campaign`, `utm_content`, `utm_term`, and `referrer` fields on `users`. The frontend captures these from the first browser landing in session storage, then both email/password and Google signup submit paths send the same payload. Attribution is written only while creating a new user and is never overwritten by a later login.

### Page-view referrer attribution

The shared `AnalyticsPageViewTracker` adds a `referrerSource` metadata field to every page-view event it emits. Its only permitted values are `google`, `other-search`, `social`, and `direct`; raw referrer URLs and hostnames are never written to analytics events for this use. This page-view-scoped bucket is intentionally separate from first-touch signup attribution above.

Feature-activation funnel events (v0.28.0):

- `GUIDANCE_TIP_SHOWN` — a `trackAnalytics` `GuidanceTip` became visible (impression); `metadata.tipId` identifies the tip
- `GUIDANCE_TIP_CTA_CLICKED` — the tip's action button was used; `metadata.tipId` identifies the tip
- `QUIZ_REVIEW_EXPORTED` — a session review PDF was successfully exported; `entity_id` is the note id, `metadata` carries `exportType` and `sessionMode`
- `DASHBOARD_RECOMMENDATION_SHOWN` — the Continue Studying card rendered; `entity_id` is the note id and `metadata` carries `reason` and `resumeType`
- `DASHBOARD_RECOMMENDATION_CTA_CLICKED` — the Continue Studying CTA was clicked; `entity_id` is the note id and `metadata` carries `reason` and `resumeType`

These form impression → click → use funnels: tip/recommendation shown → CTA click → feature use (an existing `*_STARTED` event, or `QUIZ_REVIEW_EXPORTED` for export). The events are browser-emitted via the analytics API and are fire-and-forget. The Challenge Quiz activation path is `DASHBOARD_RECOMMENDATION_SHOWN` → `DASHBOARD_RECOMMENDATION_CTA_CLICKED` → `CHALLENGE_QUIZ_STARTED`.

Upgrade checkout funnel event:

- `CHECKOUT_INITIATED` — backend-owned; fired by `PaymentService.createCheckoutSession(...)` after a hosted Xendit checkout URL is successfully created or reused. `entity_id` is the payment transaction id when available, and metadata is limited to `planType` and `billingCycle`. It is not fired when checkout creation fails.

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
- checkout initiation after a hosted checkout URL is secured
- webhook-confirmed subscription activation

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

`GET /api/admin/dashboard/organic-landings` is a separate Admin dashboard read for the recent eight-week page-view window. It returns week/surface/referrer-source landing rows plus the aggregate Google `EXAM_HUB_VIEWED` → `EXAM_HUB_CTA_CLICKED` ratio. It is not a session-correlated funnel.
