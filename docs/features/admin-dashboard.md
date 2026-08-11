# admin-dashboard.md - NoteLib Feature Context

## Goal

Provide an internal, mostly read-only Admin Dashboard for monitoring product usage, billing health, upgrades, and Public Library growth. Admin v1 has three narrow operational exceptions: admin-initiated billing refunds for exceptional payment issues, per-note Applicable Programs curation, and add-only Course / Program catalog management. **The two curation surfaces live on their own page at `/admin/course-programs`, not on the dashboard** — they are authoring tools, and the dashboard is what an admin scans for operational health, so mixing them buried the monitoring the page exists for.

## Access

- Admin Dashboard route: `/admin`
- Backend admin APIs:
  - `GET /api/admin/dashboard/summary`
  - `GET /api/admin/dashboard/top-content`
  - `GET /api/admin/dashboard/recent-events`
  - `POST /api/admin/jobs/subscription-expiry/{subscriptionId}` — expire a specific subscription and downgrade to Free (dev/ops use; subscription `end_at` must already be in the past)
  - `POST /api/admin/billing/refund` — issue a one-off Xendit refund for an eligible paid transaction
  - `GET /api/admin/notes/applicable-programs` — paginated note applicability curation data
  - `GET /api/course-program-catalog/similar?name=...` — find existing names before catalog creation
  - `POST /api/course-program-catalog` — add one catalog program with an optional existing family and supported exam goal
- Access is restricted to users with the `ADMIN` role.
- Non-admin users must not be able to use admin endpoints.
- Frontend should redirect authenticated non-admin users away from `/admin`.

## Admin v1 Scope

Keep Admin v1 simple and internal:

- summary cards
- billing summary cards
- engagement summary cards
- basic tables
- no editing actions beyond the narrow Xendit refund operation, per-note Applicable Programs, and add-only Course / Program catalog exceptions
- no complex filters or charts

## Summary Metrics

Overview cards should show:

- total users
- verified users
- premium users
- premium waitlist count (legacy interest metric, separate from live checkout)
- total notes
- total Study Packs generated
- total public notes
- total public note views
- total public note copies
- total upgrades

Billing section should show:

- active premium subscriptions
- monthly subscriptions
- yearly subscriptions
- cancel-at-period-end subscriptions
- failed payments
- estimated MRR
- estimated ARR

Engagement section should show:

- Study Packs generated this week
- Quick Review starts
- Challenge Quiz starts
- Adaptive Practice starts
- paywall views
- upgrade clicks
- signups
- verified accounts

## Tables

Admin v1 tables should include:

- most viewed public notes
- most copied public notes
- top subjects by Study Pack generation
- recent premium upgrades
- recent failed payments
- recent feedback
- one-click refund actions on Xendit recent premium upgrade rows that have a linked transaction

## Course / Program Catalog Management

**Both surfaces below render on `/admin/course-programs`, reached from a `Course / Programs →` link in the Admin dashboard header alongside Campaigns and Funnel. They are deliberately not on the dashboard itself.**

- a Course / Program catalog section listing each program and family, plus an add-only form for name, optional existing Program Family, and optional supported exam goal; it warns about near matches and keeps form input after errors
- a paginated notes table showing legacy Course / Program and explicit Applicable Programs, with a catalog-backed edit action that changes no other note metadata; its shared control can add every member of a Program Family in one action, after which the admin can trim the explicit set before saving

- Catalog growth is demand-driven: add a program when a canonical note is legitimately applicable to it. Do not pre-seed a vocabulary.
- Creation is add-only. Rename, delete, and new Program Family creation remain out of scope.
- Names are trimmed and compared case- and whitespace-insensitively. Duplicate conflicts identify the existing program instead of surfacing a database error.
- Family assignment is functional metadata, not decoration: assigning `Engineering` makes the program available to the shared picker’s unconditional Engineering-family expansion.
- The same create flow is available inline in Applicable Programs pickers to Admins, with near matches and an explicit confirmation. It never enables free-text applicability.

## Funnel Metrics

`GET /api/admin/funnel/metrics` powers the admin-only Conversion Funnel page.

Current sections:

- Activation: verified users, activated users, activation rate, median days to first Study Pack, and users stuck before generation.
- Paywall & Value Loop: Free quota-hit rate, paywall conversion, and first-pack → quiz-start value-loop closure.
- Checkout conversion: distinct users who clicked upgrade, distinct users who initiated checkout after an upgrade click, distinct users who subscribed after checkout, plus click→checkout, checkout→paid, and click→paid rates.
- W1→W2 retention: eligible activated users, returned week-2 users, overall return rate, and the last 8 activation-week cohorts.

Retention definitions:

- Activation = a user's first `STUDY_PACK_GENERATED` analytics event.
- Returned in week 2 = the user has at least one `analytics_events` row in `(firstPack + 7 days, firstPack + 14 days]`.
- Eligible = the activation timestamp is at least 14 days before the report time, so the week-2 window is complete.
- Weekly cohorts bucket eligible users by `date_trunc('week', firstPack)`.

Checkout funnel definitions:

- `UPGRADE_CLICKED` is the existing upgrade-intent event from frontend upgrade CTAs.
- `CHECKOUT_INITIATED` is recorded after the backend successfully obtains or reuses a hosted Xendit checkout URL.
- `SUBSCRIPTION_STARTED` is recorded after webhook-confirmed paid-plan activation.
- The checkout step is forward-looking from the `CHECKOUT_INITIATED` deployment and is not backfilled for earlier checkout sessions.

## Refund Action

- Refund buttons appear only for Recent Paid Upgrades rows where `provider=XENDIT` and a linked `transactionId` exists.
- Clicking Refund opens a confirmation modal with the user email, amount, and currency before submitting.
- Backend eligibility is authoritative: only successful Xendit payment transactions may be refunded, already-refunded transactions return a conflict, and missing transactions return not found.
- Successful refunds mark the transaction `REFUNDED` and send the user a refund confirmation email.
- Refunds are operational actions only; they are not included in dashboard summary metrics or charts.

## Data Sources

Reuse existing data sources where possible:

- `analytics_events` for funnel, paywall, upgrade, and public-note metrics
- `premium_waitlist` for legacy paid-plan interest reporting when that data still exists
- `subscriptions` for active paid-plan state and cancel-at-period-end status
- `payment_transactions` for upgrade history, failed payments, and billing estimates
- `feedback` for recent user feedback during soft launch
- `notes` and `study_packs` for library and generation counts

## Notes

- Admin note-authoring surfaces use the shared Note Editor and Bulk Generate forms, plus Note Detail's inline metadata panel for notes that already have a Study Pack. All three expose optional Domain Context and Note Learner Level selects alongside Target Audience; the values apply to the saved note or every note in the batch and drive generation through the shared resolver.
- Revenue numbers are estimates for internal monitoring, not accounting-grade reporting.
- Admin Dashboard should support dark mode and remain usable on smaller screens, but it is primarily desktop-oriented.
