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
  - `Study Pack Ready`
- Note visibility:
  - `PRIVATE`
  - `PUBLIC`

## Learning Loop

Primary loop (user-facing positioning):

`Create → Understand → Practice → Challenge → Improve`

Supporting product loop (internal model):

`Capture → Generate → Review → Improve → Copy → Repeat`

Onboarding is the entry point into the loop. Users complete a 5-step onboarding flow that ends with a generated Study Pack, placing them at the `Understand` stage before they touch the dashboard. This eliminates the empty-state activation problem.

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

## Core Domain Models

- User
- Note
- Study Pack (generated Note state)
- QuickReviewSession
- ActivityEvent

All generated outputs and quiz/practice sessions are note-scoped (`noteId`).

## Feature Documentation

- docs/features/onboarding.md
- docs/features/study-pack-generation.md
- docs/features/quick-review.md
- docs/features/dashboard-recommendation.md

## Architecture

See `docs/architecture/ARCHITECTURE.md`.
