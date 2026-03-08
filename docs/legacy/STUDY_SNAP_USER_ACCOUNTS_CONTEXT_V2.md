# STUDY_SNAP_USER_ACCOUNTS_CONTEXT.md

This file extends the existing Study Snap project docs and defines the agreed direction for the **User Accounts foundation**.

It should be read together with:
- `README.md`
- `SPEC.md`
- `ROADMAP.md`
- `ARCHITECTURE.md`
- `AGENTS.md`

---

## Why this exists

Study Snap is evolving from a one-shot review generator into a reusable study workspace.

The product already treats generated outputs as **study packs** that users can revisit later in a Study Library.
Because of that, user accounts should be introduced **before** fully implementing the Study Library dashboard.

This keeps ownership, saved history, plan limits, and future premium features consistent.

---

## Product alignment

Study Snap core value remains:

**Notes → Study Pack → Revisit later**

A Study Pack contains:
- title
- summary
- key concepts
- practice quiz

The User Accounts foundation exists to support:
- saved study packs
- authenticated Study Library access
- future usage limits by plan
- future subscription analytics
- future premium feature access

This is intentionally **not** a full classroom/family management system in the first version.

---

## Naming decisions

### Review → Study Pack

The backend/domain should gradually move from the term **Review** to **Study Pack** where practical.

Reason:
- “Review” is becoming vague as the product grows.
- “Study Pack” matches product language in README, SPEC, and roadmap.
- The Study Library is conceptually a library of study packs.

Transitional note:
- Existing code may still use `Review` in some classes/endpoints.
- New schema and future-facing design should prefer **StudyPack** naming.

---

## User account design principles

The user account system should separate concerns instead of putting all business logic into one table.

Recommended separation:
- **users**: identity + core profile
- **subscriptions**: plan history and billing state
- **study_packs**: user-owned generated content
- future tables later for usage tracking, account links, etc.

This separation is preferred because Study Snap is expected to grow into:
- freemium plan controls
- premium subscriptions
- library/history
- analytics

---

## Agreed MVP-friendly user structure

### users

Purpose:
- store authentication identity
- store lightweight personal profile
- support ownership of saved study packs

Recommended fields:
- `id`
- `email`
- `password_hash` (or auth provider fields later)
- `first_name`
- `last_name`
- `display_name` (optional)
- `country_code` (optional, ISO-style such as `PH`, `US`)
- `profile_type` (nullable enum)
- `status`
- `created_at`
- `updated_at`
- `last_login_at` (nullable)
- `email_verified_at` (nullable)

### display_name behavior

`display_name` is optional.

Frontend behavior:
- when the user types `firstName`, auto-fill `displayName`
- if the user later customizes `displayName`, preserve the customized value
- if `displayName` is blank, UI should fall back to `firstName`

Backend/API behavior:
- treat `displayName` as optional
- when building response DTOs, expose a resolved display label if needed:
  - `displayName` if present
  - otherwise `firstName`

This keeps onboarding simple while still allowing user-friendly personalization.

---

## profile_type

Internal field name:
- `profileType`

Purpose:
- lightweight onboarding / segmentation / personalization
- not authorization
- not billing
- not permissions

UI copy:
- **“I’m using Study Snap as a…”**

Allowed initial values:
- `STUDENT`
- `PARENT`
- `PROFESSIONAL`

Notes:
- `TEACHER` is intentionally parked for now
- `profileType` is a product/profile classification, not a security role
- a more user-friendly label can be shown in UI while keeping `profileType` as the technical field name

---

## subscriptions

A dedicated `subscriptions` table is preferred instead of storing only a plan enum on `users`.

Reason:
- easier subscription history tracking
- better analytics
- cleaner future integration with billing providers
- easier to reason about active vs expired subscriptions

Recommended fields:
- `id`
- `user_id`
- `plan_type`
- `status`
- `start_at`
- `end_at` (nullable)
- `created_at`
- `updated_at`

Possible future additions:
- `provider`
- `provider_customer_id`
- `provider_subscription_id`
- `cancelled_at`
- `renewal_at`
- `trial_ends_at`

UI naming:
- The product/UI can say **Plan Type**
- The database/history model can remain **subscriptions**

Initial plan direction:
- `FREE`
- `PREMIUM`

Demo mode remains separate from authenticated account subscriptions.

---

## Study Packs ownership

Each authenticated user should own many Study Packs.

Relationship:
- `User` 1 → many `StudyPack`

Recommended ownership field on study packs:
- `owner_user_id`

This should power the future Study Library dashboard for:
- list my study packs
- open a study pack
- delete a study pack

---

## Deferred features (parked, not in v1 schema)

### Family plan / parent-child linking

This is a valid future direction, but should **not** be implemented in the first user-account schema.

Why it is deferred:
- adds permission complexity
- adds invitation/linking flows
- adds privacy considerations
- adds questions about ownership and visibility

Keep it in roadmap/context as a future feature.

Potential future design direction:
- `account_links`
  - `id`
  - `owner_user_id`
  - `linked_user_id`
  - `relationship_type`
  - `status`

Possible future relationship types:
- `PARENT_OF`
- `GUARDIAN_OF`

This should only be introduced when family plans or managed learner accounts become a real product need.

### Teacher mode

Teacher-oriented functionality is also intentionally deferred.

It may return later as:
- classroom sharing
- teacher-owned collections
- student progress visibility

But it is out of scope for the first user-account implementation.

---

## Recommended implementation order

### Phase 1
Build user accounts foundation:
- signup
- login
- profile fields
- account status fields

### Phase 2
Link generated Study Packs to authenticated users.

### Phase 3
Build the Study Library dashboard on top of authenticated ownership.

### Phase 4
Add rate limiting / usage limits by plan.

### Phase 5
Add subscription lifecycle improvements and analytics.

This order is preferred because it minimizes rework in library queries and ownership handling.

---

## Codex / implementation guidance

When implementing this feature, prefer:
- thin controllers
- service-layer orchestration
- DTOs for API responses
- migrations before entity wiring
- clean naming aligned with Study Pack terminology

Do not overbuild yet:
- no family-account logic
- no teacher flows
- no multi-user linked dashboards
- no advanced billing provider integration in first pass

Focus on:
- user identity
- authenticated ownership
- plan/subscription history shape
- clean path to Study Library

---

## Final direction snapshot

Study Snap should now move toward this structure:

- **users** = identity + basic profile
- **subscriptions** = plan history
- **study_packs** = saved generated content

And the agreed UX details are:
- `displayName` is optional
- auto-fill `displayName` from `firstName`
- if empty, fall back to `firstName`
- technical field name: `profileType`
- UI label: **“I’m using Study Snap as a…”**
- values for now: Student, Parent, Professional

This context should guide the next implementation work for User Accounts and the future authenticated Study Library.
