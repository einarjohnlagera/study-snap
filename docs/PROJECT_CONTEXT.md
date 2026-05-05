# NoteLib Project Context

## What NoteLib Is

NoteLib is a structured study system, not a notes app. It guides users through a repeating learning loop:

`Create → Understand → Practice → Challenge → Improve`

Users can start with their own notes or generate a draft note from a topic. NoteLib converts that input into a Study Pack — summary, key concepts, and quiz material — and then supports repeated practice, challenge quizzes, and improvement through weak-concept tracking.

## Core Product Model

- Note is the primary entity.
- Study Pack is the generated enhancement state of a Note.
- Note states:
  - `Draft`
  - `Generating`
  - `Failed`
  - `Study Pack Ready`
- Note visibility:
  - `PRIVATE`
  - `PUBLIC`

## Learning Loop

Primary loop (user-facing positioning):

`Create → Understand → Practice → Challenge → Improve`

Supporting product loop (internal model):

`Capture → Generate → Review → Improve → Copy → Repeat`

Onboarding is the entry point into the loop. Verified users complete a 5-step activation flow (`Profile Type -> Study Goal -> Input Method -> Study Pack Generation -> Completion`) that ends with a generated Study Pack, placing them at the `Understand` stage before they touch the dashboard. Learner level and other preferences are deferred to Profile and Settings after this first win.

## Versioning Rule

NoteLib does not overwrite generated content on the same Note.

Users create a new version by using `Make a Copy`, editing the copied Note, and generating a new Study Pack from that copy.

Copy includes user-authored fields only:

- title
- subject
- tags
- note content

Copy does not include generated/performance fields:

- summary
- key concepts
- quizzes
- review performance history
- quiz sessions

## Library Structure

Sidebar navigation:

- Main: Dashboard, Library, Public Library
- Account: Profile, Settings

Primary routes:

- `/library`
- `/library/public`
- `/notes/{id}` (Note Detail)
- `/public/library/{subject}/{slug}` (Public Note Detail, read-only)
- `/public/profile/{userId}` (Public Profile)

## Verification and OCR

- Unverified users are blocked from Study Pack generation.
- Unverified users are blocked from OCR upload.
- OCR is optional in Create/Edit Note and populates Note content for manual review before save/generate.
- Generate Note from topic is available in Create Note and fills editable note content before save.

## Tech Stack

Backend: Spring Boot  
Frontend: Next.js  
Database: PostgreSQL  
AI: OpenAI LLM  
OCR: Google Vision
Payments: Xendit hosted checkout

## Plans

NoteLib has three plans: Free, Plus, and Pro.

| Plan | Monthly (PH) | Intro first month (PH) |
|------|-------------|------------------------|
| Free | ₱0 | — |
| Plus | ₱179 | ₱149 |
| Pro | ₱249 | ₱199 |

- Annual Pro is available at ₱1,999/year (PH).
- Plus annual is not yet available; Plus always uses monthly checkout.
- Billing is manual renewal — no automatic charges.
- Current runtime gating still treats Adaptive Practice, Difficulty Selection, and Board Exam Mode as Pro-only features even when some pricing surfaces position Plus as the regular-study step-up tier.

## Payments

- Paid upgrades use Xendit hosted invoice checkout (Plus and Pro).
- Current billing model is manual renewal with `30`-day Monthly access or `365`-day Annual access per successful payment.
- Billing checkout pricing is config-driven from backend billing region settings.
- Intro offers and automatic discounts use `discount_vouchers`.
- Successful discount usage is recorded in `voucher_redemptions` only after a confirmed `PAID` webhook.
- All plans and entitlements must be represented through the `subscriptions` table.
- Subscription history is preserved in `subscriptions`; only one active subscription row should exist per user at a time.
- User records must not store plan flags or plan state.
- Frontend starts checkout through `POST /api/payments/create` and redirects to the returned hosted URL.
- Paid access is activated only after the backend receives and validates `POST /api/webhooks/xendit`.
- Success and failure pages are user-facing status pages only; they do not grant paid access.
- Billing success returns users to the interrupted product flow when a safe paywall `returnUrl` exists, but Settings/Billing-origin upgrades land on Dashboard.
- Frontend redirects after checkout never activate paid access directly.
- After quiz completion, non-Pro users see a `PostSuccessUpgradeNudge` on Quick Review and Challenge Quiz result screens — a sessionStorage-dismissed inline banner with plan-aware CTAs linking to `/settings?section=plans`.

## Analytics

- Frontend quiz completion fires `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, and `ADAPTIVE_PRACTICE_COMPLETED` events via `trackAnalyticsEvent` in `frontend/lib/api.ts`.
- `UPGRADE_CLICKED` is fired from upgrade CTAs and the `PostSuccessUpgradeNudge` component.
- All analytics calls are fire-and-forget (`void`) and must not block or throw on the primary flow.
- `AnalyticsEventType` in `frontend/lib/api.ts` is the canonical union of all allowed event names.

## Core Domain Models

- User
- Note
- Study Pack (generated Note state)
- QuickReviewSession
- ActivityEvent

All generated outputs and quiz/practice sessions are note-scoped (`noteId`).

## Public Library as an Acquisition Surface

Public Library is not only a content browser for authenticated users. Public note pages are also top-of-funnel entry points — shareable links that visitors can open from Facebook, messaging apps, and other social channels without an account.

Public note pages must:
- teach first (topic, hook, mini quiz preview)
- let visitors interact lightly (1–2 unanswered questions, no account needed)
- then invite signup or note creation (soft CTA after value is shown)

Public mini quiz rules:
- public visitors may answer a small preview of 1–2 questions
- answers are client-side only — no session row is created for anonymous users
- full quiz access, score persistence, and Study Pack generation require login
- the signup gate must appear only after the visitor has experienced some value, not on page load

Generated note formatting for public pages:
- prefer shorter sections, clearer headings, key-fact blocks, and quick recall blocks
- avoid long paragraph-dense LLM output; public pages should read like a study reviewer, not a transcript
- formatting improvements apply to generated content displayed on public note pages; they do not change the underlying storage format

CTA ordering on public note detail:
- show topic hook → mini quiz → summary/key concepts → soft conversion CTA → copy/generate CTA
- do not lead with `Copy to My Library` or `Generate Study Pack` before the visitor has seen learning value
- `Share` must always be visible regardless of auth state

Public creator identity direction:
- `displayName` is presentation-only; it is not a unique creator identity
- public note cards and public note detail need a stable public creator identifier for trust and duplicate-name disambiguation
- preferred direction is username / handle when available, otherwise a generated public slug
- public-facing creator labels should keep `displayName` for readability and add the handle/slug when disambiguation is needed
- never expose raw user IDs or emails on public note or public profile surfaces
- current public links must remain valid if handle/slug-based public identity is introduced later

## v0.12.0 Direction

Current in-progress release: `v0.12.0 - Learning Experience, Discovery, and Retention`.

Key v0.12.0 changes to be aware of:

- **Learner Level drives quiz difficulty and style** — quiz generation prompts use `learnerLevel` for question complexity, explanation depth, and vocabulary; this is an LLM prompt enhancement, not a new UI field
- **Course/Program drives domain context** — course/program is passed to quiz and Study Pack generation prompts so examples and questions stay relevant to the student's discipline; it is a separate concern from Learner Level and must not be merged with it
- **Upgrade CTA rule is now enforced** — all paywall and limit surfaces use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`; upgrade CTAs navigate to `/settings?section=plans`, not `/pricing`
- **Analytics funnel is tracked** — `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, and `UPGRADE_CLICKED` are in `AnalyticsEventType` and fired from the relevant completion blocks
- **Public Library filters move backend** — subject, tags, learner level, and profile type become backend query params so filtered states are shareable; frontend filtering over local payloads is the interim approach until backend params land
- **Public creator identity safety** — Public Library cards and public note detail currently rely on `displayName` for presentation; v0.12.0 planning adds a stable public creator identifier for disambiguation and shareable creator links while keeping existing public URLs valid
- **Social login (Google)** — planned for this release cycle; must not break or replace existing email-and-password accounts
- **Content moderation** — `ContentModerationService` in the backend applies token-based dictionary matching to note titles, Study Pack topics, and note content at creation boundaries; dictionaries are in `classpath:/moderation/banned_words_*.txt`

## Feature Documentation

- docs/features/onboarding.md
- docs/features/study-pack-generation.md
- docs/features/quick-review.md
- docs/features/dashboard-recommendation.md
- docs/features/profile-learning-context.md

## Architecture

See `docs/architecture/ARCHITECTURE.md`.
