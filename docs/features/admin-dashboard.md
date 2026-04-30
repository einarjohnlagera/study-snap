# admin-dashboard.md - NoteLib Feature Context

## Goal

Provide an internal, read-only Admin Dashboard for monitoring product usage, billing health, upgrades, and Public Library growth.

## Access

- Admin Dashboard route: `/admin`
- Backend admin APIs:
  - `GET /api/admin/dashboard/summary`
  - `GET /api/admin/dashboard/top-content`
  - `GET /api/admin/dashboard/recent-events`
  - `POST /api/admin/jobs/subscription-expiry/{subscriptionId}` — expire a specific subscription and downgrade to Free (dev/ops use; subscription `end_at` must already be in the past)
- Access is restricted to users with the `ADMIN` role.
- Non-admin users must not be able to use admin endpoints.
- Frontend should redirect authenticated non-admin users away from `/admin`.

## Admin v1 Scope

Keep Admin v1 simple and internal:

- summary cards
- billing summary cards
- engagement summary cards
- basic tables
- no editing actions
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

## Data Sources

Reuse existing data sources where possible:

- `analytics_events` for funnel, paywall, upgrade, and public-note metrics
- `premium_waitlist` for legacy paid-plan interest reporting when that data still exists
- `subscriptions` for active paid-plan state and cancel-at-period-end status
- `payment_transactions` for upgrade history, failed payments, and billing estimates
- `feedback` for recent user feedback during soft launch
- `notes` and `study_packs` for library and generation counts

## Notes

- Revenue numbers are estimates for internal monitoring, not accounting-grade reporting.
- Admin Dashboard should support dark mode and remain usable on smaller screens, but it is primarily desktop-oriented.
