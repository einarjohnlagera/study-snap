# CODEX PROMPT — NoteLib User Accounts Foundation (V2)

Use the existing project docs as the primary source of truth:
- `README.md`
- `SPEC.md`
- `ROADMAP.md`
- `ARCHITECTURE.md`
- `AGENTS.md`
- `STUDY_SNAP_USER_ACCOUNTS_CONTEXT.md` (or the latest user-accounts context file)

You are implementing the **User Accounts foundation** for NoteLib.

## Product reminder
NoteLib turns notes into reusable **Study Packs**.
The next major product direction is:
- authenticated users
- user-owned study packs
- future Study Library dashboard
- future usage limits by plan

This implementation is a foundation for those features.

---

## Important decisions already agreed

### Naming
- Prefer **StudyPack** terminology for new schema/design work.
- Existing `study pack` naming may still exist in current code; do not break everything unnecessarily.
- Use migration-safe, incremental refactors where possible.

### User profile rules
- `firstName` required
- `lastName` required
- `displayName` optional
- UI should auto-fill `displayName` from `firstName`
- If `displayName` is blank, frontend/UI should fall back to `firstName`
- Backend should treat `displayName` as optional

### Profile classification
Technical field name:
- `profileType`

UI prompt:
- **“I’m using NoteLib as a…”**

Allowed initial values:
- `STUDENT`
- `PARENT`
- `PROFESSIONAL`

This field is:
- for onboarding/personalization/analytics
- not authorization
- not permissions
- not billing

### Subscriptions
- Use a separate `subscriptions` table
- Keep subscription history clean for future analytics
- UI can say **Plan Type**
- Demo mode is separate and should not be modeled as a normal subscription row unless explicitly required later

### Deferred features
Do NOT implement now:
- family linking / parent-child relationships
- teacher workflows
- classroom management
- advanced billing provider integration

Keep the design extensible, but do not build those features yet.

---

## Your task
Produce a clean first-pass implementation plan and code changes for the backend user-account foundation.

## Expected deliverables

### 1. Database design
Create or propose Flyway migrations for these tables:

#### `users`
Recommended columns:
- `id` (UUID or project-consistent identifier)
- `email` (unique)
- `password_hash`
- `first_name`
- `last_name`
- `display_name` (nullable)
- `country_code` (nullable)
- `profile_type` (nullable)
- `status`
- `created_at`
- `updated_at`
- `last_login_at` (nullable)
- `email_verified_at` (nullable)

#### `subscriptions`
Recommended columns:
- `id`
- `user_id`
- `plan_type`
- `status`
- `start_at`
- `end_at` (nullable)
- `created_at`
- `updated_at`

Also recommend indexes and uniqueness rules where appropriate.

### 2. Domain model / JPA entities
Create Spring Boot entities or equivalent persistence models for:
- `User`
- `Subscription`

Use enums where appropriate for:
- `ProfileType`
- `UserStatus`
- `PlanType`
- `SubscriptionStatus`

### 3. Relationship preparation for Study Packs
Prepare the design so that saved study packs/study packs can later belong to a user via:
- `owner_user_id`

If the project currently still uses `study packs`, keep the implementation incremental and safe.
Do not force a giant rename if it would create unnecessary churn.

### 4. API design proposal
Propose or scaffold minimal endpoints for user accounts, such as:
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `GET /api/me`
- `PATCH /api/me`

If auth is not fully implemented yet, you may stub the API shape and DTOs first.

### 5. Service design
Follow NoteLib backend conventions:
- thin controllers
- service-layer business logic
- avoid overloading entities with UI logic

Suggested services:
- `AuthService`
- `UserService`
- `SubscriptionService`

### 6. Validation rules
Apply sensible validation such as:
- email required and unique
- firstName required
- lastName required
- displayName optional
- countryCode optional
- profileType optional but enum-constrained when present

### 7. Keep the implementation MVP-friendly
Do not add:
- social login
- password reset email flows
- Stripe integration
- family linking
- teacher features
- analytics dashboards

Only prepare the foundation cleanly.

---

## Architectural guidance
Follow existing project conventions from `AGENTS.md` and `ARCHITECTURE.md`:
- controllers should stay thin
- business logic belongs in services
- prefer DTOs for request/response models
- keep naming aligned with NoteLib product language
- keep the path clean for the future Study Library feature

Make decisions that support this product sequence:
1. User accounts
2. User-owned study packs
3. Study Library dashboard
4. Usage limits by plan
5. Launch prep / premium evolution

---

## Output format requested from Codex
Please return:
1. a short implementation summary
2. proposed schema/tables
3. proposed enums
4. package/class structure
5. API endpoints and DTOs
6. Flyway migration draft(s)
7. JPA entity draft(s)
8. anything risky or ambiguous that should be reviewed before coding

Favor concrete code scaffolding over vague advice.

