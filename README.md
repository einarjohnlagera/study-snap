# NoteLib

> Rebrand update: this project was renamed from StudySnap to NoteLib. Core behavior and database schema remain unchanged.

Current release baseline: `v0.13.0 - Complete the Promise, Reach New Audiences`

NoteLib turns notes into exam-ready study materials. Students and board exam takers paste or generate notes, then use summaries, key concepts, quizzes, and adaptive practice to understand and retain what matters.

It is built for:

- students who want to move from passive reading to active recall
- board exam takers who need structured practice and weak-area drilling
- teachers who create quiz materials and classroom review resources

## Key features

| Feature | What it does |
|---|---|
| **Study Packs** | Generate a summary, key concepts, and a practice quiz from any note |
| **Adaptive Practice** | Focus your review on the concepts you're weakest at |
| **Board Exam Mode** | Simulate high-stakes exam conditions with timed Challenge Quizzes |
| **Exports** | Download quizzes as PDF or DOCX for offline review or classroom use |
| **Public Library** | Discover and copy publicly shared notes into your own library |

## One-liner

Turn your notes into exam-ready study materials in seconds.

## Public Positioning

- Exam-focused study tool, not a generic AI utility
- Every feature connects to a learning outcome: understand, practice, improve
- Study Pack generation is structured for retention, not one-shot output
- Adaptive Practice trains on weak concepts until they stick
- Board Exam Mode simulates real exam pressure for serious prep
- Public Library is a discovery surface for shared notes, not a paid feature
- Exports enable offline and classroom use (PDF/DOCX)
- Free → Plus → Pro follows the natural arc of a growing learner

## Plans

NoteLib uses a 3-tier learning-focused plan structure:

- Free
  - 10 Study Packs / month
  - 5 Quizzes / month
  - 2 exports / month
  - Summary + Key Concepts
- Plus
  - ₱149 first month, then ₱179/month in the Philippines when intro pricing applies
  - 50 Study Packs / month
  - 25 Quizzes / month
  - 15 exports / month
  - Adaptive Practice (10 sessions / month)
  - Higher note generation limits
- Pro
  - ₱199 first month, then ₱249/month in the Philippines when intro pricing applies
  - 100 Study Packs / month
  - 50 Quizzes / month
  - Unlimited exports
  - Adaptive Practice (30 sessions / month)
  - Difficulty selection
  - Board Exam Mode

Pricing UI is kept consistent across the app through a shared frontend plan config, while checkout amounts remain backend-owned.

Current runtime gating note:

- Board Exam Mode is Pro-only
- Difficulty selection is Pro-only

## Monetization flow

- usage-based limits are enforced per plan for Study Packs, quizzes, exports, OCR, and topic note generation
- hitting a limit opens a context-aware paywall that explains the blocked action and the value of upgrading
- paywalls compare Plus and Pro directly, with Pro highlighted as the stronger review tier
- note-creation upgrade attempts preserve progress before checkout so users do not lose in-progress work
- after successful payment, users are returned to the interrupted flow and Study Pack generation can resume automatically from the saved note

## Local auth setup

Email/password login works without Google OAuth configuration.

Google login requires:

- Backend: `GOOGLE_CLIENT_ID`
- Frontend: `NEXT_PUBLIC_GOOGLE_CLIENT_ID`
- Optional backend override: `GOOGLE_CERTIFICATES_URL` (defaults to Google's public certs endpoint)

Configure a Google OAuth web client in Google Cloud Console and add `http://localhost:3000` to the allowed JavaScript origins for local development. The frontend uses Google Identity Services to obtain an ID token, and the backend verifies issuer, audience, expiry, signature, and `email_verified` before issuing the normal NoteLib JWT/refresh-token response.

## Brand Assets

Primary brand assets live in `frontend/public`:

- `notelib-logo-monogram.png`
  - use for navbar/app-shell brand marks, favicon, apple-touch icon, and other small-logo placements
- `notelib-logo-full-light.svg`
  - use on light marketing/public surfaces
- `notelib-logo-full-dark.svg`
  - use on dark marketing/public surfaces
- `notelib-logo-icon.svg`
  - use as a product illustration, not as the navbar logo or favicon
- `og-image.png`
  - social-share image for landing and public metadata

Branding rule:

- monogram = small brand mark
- full logo = public/marketing headers and footer
- product icon = illustration only

## Core Concept

Note-first model:

- Note is the main entity.
- Study Pack is the AI-generated enhancement state of a Note.
- Users create and save Notes first, then generate Study Packs from those Notes.

Note states:

- `Draft`: Note exists with user-authored content only.
- `Generating`: Study Pack generation is queued or running.
- `Failed`: The last Study Pack generation attempt did not complete.
- `Study Pack Ready`: AI-generated study outputs are linked to the Note.

Generated Study Pack outputs include:

- AI-generated title (optional)
- subject (optional)
- tags (optional)
- summary
- key concepts
- practice quiz
- Challenge Quiz
- Adaptive Practice

## Copy Model

NoteLib does not overwrite existing generated content.

Users use `Make a Copy` on a Note, edit the copied Note, and generate a new Study Pack from that copied Note.

Copy behavior:

- Copy includes:
  - title
  - subject
  - tags
  - note content
- Copy does not include:
  - summary
  - key concepts
  - quizzes
  - performance history
  - quiz sessions

This supports iterative learning and avoids accidental overwrites.

## User Flow

1. User creates or saves a Note.
2. Note is stored in the system.
3. User clicks `Generate Study Pack`.
4. AI generates summary, key concepts, and quizzes.
5. User reviews with Quick Review, Challenge Quiz, and Adaptive Practice.
6. To improve content, user makes a copy, edits it, and generates a new Study Pack from the copy.
7. User can set note visibility:
   - `Make Public` -> appears in Public Library
   - `Make Private` -> only visible in Library
8. User can copy public notes into Library and continue studying.
9. User can open a creator's Public Profile to view public notes and contribution stats.

## Navigation

Sidebar structure:

- Main: Dashboard, Library, Public Library
- Account: Profile, Settings

Primary product pages:

- Library: `/library`
- Public Library: `/public/library`
- Note Detail: `/notes/{id}`
- Public Note Detail (read-only): `/public/library/{subject}/{slug}`
- Public Profile: `/public/profile/{userId}`

Page responsibilities:

- Dashboard = what to do now
- Library = private workspace
- Public Library = discovery
- Public Profile = public showcase
- Profile = identity
- Settings = app preferences

Library organization:

- search matches note titles and tags in real time
- subject filtering is exposed as single-select chips with `All` as the default
- tag filtering is exposed as multi-select chips and combines with search + subject on the loaded note list
- notes without an explicit subject temporarily derive their filter/display subject from existing metadata so the Library remains groupable

## Personalized Dashboard

- Dashboard presentation adapts to `Profile Type` without changing the shared core system.
- `STUDENT` emphasizes review continuity, weak concepts, and recent notes.
- `BOARD_EXAM` emphasizes challenge-quiz practice, weak areas, adaptive practice, and exam countdown.
- `TEACHER` emphasizes material upload, quiz creation, and recently generated quiz-ready notes.
- All variants still use the same note -> Study Pack -> quiz -> activity workflow.
- Teacher CTAs use mode-based entry routes on the same note editor so quiz creation, paste, and upload flows remain unified.

## Shared Learning Engine

All profile types use the same core system:

`Note -> Study Pack -> Quiz -> Activity -> Weak Concepts`

Profile type changes presentation and workflow emphasis only. It does not create separate note, quiz, or activity systems.

## Architecture Overview

High-level model:

- `notes` table stores user-authored fields (`title`, `subject`, `content`, `tags`).
- `notes.visibility` controls listing behavior (`PRIVATE` or `PUBLIC`).
- AI-generated fields are linked to the same Note (`summary`, `key concepts`, `quizzes`).
- quiz sessions and performance history are linked by `noteId`.
- copying creates a new Draft Note row and copies only user-authored fields.

## Verification and OCR

- Unverified users can access the app but cannot generate Study Packs.
- Unverified users also cannot use OCR upload.
- OCR is optional on Create/Edit Note and is used to populate Note content for review before save/generate.

## Profile and Settings

- `Profile` owns identity and learning profile information:
  - `firstName`
  - `lastName`
  - `displayName`
  - `email`
  - `profileType`
  - `learnerLevel` — controls quiz difficulty, explanation depth, and content complexity
  - `courseProgram` — provides domain context so examples and questions stay relevant
  - `bio`
  - `View Public Profile`
- `Public Profile` owns public-page controls:
  - `Share Profile`
  - Public visibility toggle
  - owner-only `Edit Profile`
- `Settings` owns preferences and app behavior:
  - theme
  - notifications
  - `Learning Style`
  - `Study Reminders`
  - account settings
  - billing and usage
- Email changes are not applied immediately.
- Requested email changes are stored as `pendingEmail` and only replace the current email after the new address is verified.

## Note Creation UX

- `Library` includes a direct `Create Note` entry so note creation does not depend on `Dashboard`.
- Note Editor keeps a short primary CTA with profile-aware labels:
  - `Generate`
  - `Practice`
  - `Create Quiz`
- Longer action explanations live below the primary button:
  - `Creates summary, key concepts, and quiz.`
  - `Generates a new quiz from your material.`
  - `Generates quiz questions from your material.`
- Desktop repeats note actions at the top and bottom of long forms.
- Mobile keeps generation visible through a floating primary CTA.
- Entry modes reuse the same note pipeline:
  - `/notes/new`
  - `/notes/new?mode=quiz`
  - `/notes/new?source=paste`
  - `/notes/new?source=upload`
- Default post-generation behavior:
  - `STUDENT` -> open Summary first
  - `BOARD_EXAM` -> open Quiz first
  - `TEACHER` -> open Quiz first

## Onboarding

- Verified users go through a short activation onboarding once.
- Current onboarding flow:
  - `Profile Type`
  - `Study Goal`
  - `Input Method`
  - `Study Pack Generation`
  - `Completion`
- `Exam Date` is optional and shown inline for `BOARD_EXAM` users during the Study Goal step.
- The onboarding flow persists `profileType`, optional `examDate`, and `onboardingCompletedAt`.
- `learnerLevel`, `courseProgram`, `bio`, `Learning Style`, and reminder preferences are deferred to `/profile` and `/settings`.
- Learner level is surfaced after onboarding through the Dashboard prompt `Too easy or too hard?`, which navigates directly to `/profile?from=dashboard#learning-profile`.

## Product Philosophy

Learning loop:

**Capture -> Generate -> Review -> Improve -> Copy -> Repeat**

NoteLib is designed for iterative understanding, not one-time summary generation.

This repo currently centers on:

- note-to-study-pack generation
- OCR support for image-based notes
- landing-page positioning that explains NoteLib as a notes library and review workspace
- Library and Public Library support
- Public Profiles for creator discovery and public-note browsing
- shared public-note card previews and whole-card navigation
- responsive action patterns, standardized icons, and tab-based note-detail navigation
- demo mode
- shareable Study Pack links
- Free / Plus / Pro plans with Xendit hosted checkout and webhook-confirmed activation
- future user accounts and authenticated ownership

## Billing (Current)

Plans: Free, Plus, Pro. Paid upgrades use Xendit hosted invoice checkout with manual renewal (no automatic charges).

| Plan | Monthly (PH) | Intro first month (PH) |
|------|-------------|------------------------|
| Free | ₱0 | — |
| Plus | ₱179 | ₱149 |
| Pro | ₱249 | ₱199 |

- Annual Pro is available at ₱1,999/year (PH). Plus annual is not yet available.
- Checkout is initiated via `POST /api/payments/create` and redirects to the Xendit hosted page.
- Paid access is activated only after the backend receives and validates `POST /api/webhooks/xendit`.
- Success and failure redirect pages are informational; they do not grant paid access directly.
- `subscriptions` is the source of truth for plan state. Only one active subscription row exists per user at a time.
- Webhook events handled: `PAID`, `FAILED`, `EXPIRED`.

## Tech stack

### Frontend
- Next.js (App Router) + TypeScript
- Tailwind CSS
- shadcn/ui
- next-themes (light/dark)

### Backend
- Spring Boot
- PostgreSQL
- OCR provider (Google Cloud Vision direction)
- LLM provider (OpenAI direction)

## Repo structure

```text
notelib/
  frontend/
  backend/
  docs/
    product/
    architecture/
    features/
    ai/
    legacy/
  AGENTS.md
  README.md
  .env.example
```

## Documentation map

Canonical documentation now lives in `/docs`:

- `docs/product/SPEC.md` - product behavior and UX rules
- `docs/product/ROADMAP.md` - development phases and sequencing
- `docs/architecture/ARCHITECTURE.md` - backend system design and flows
- `docs/architecture/DATA_MODEL.md` - consolidated entity and table design
- `docs/features/` - feature-specific execution context
- `docs/ai/PROMPTS.md` - prompt assets and JSON contract
- `AGENTS.md` - coding-agent rules for implementation

## Legacy preservation

No original context was discarded during this refactor.

The previous versions of the original markdown files are preserved under `/docs/legacy` so the repo keeps both:

- the new organized structure
- the original source documents for reference and migration

## Roadmap

Current release: `v0.12.0 - Learning Experience, Discovery, and Retention` (in progress)

Planned for v0.12.0 (in priority order):

- **Public Library conversion** — public note pages become shareable learning pages; mini quiz preview for visitors; soft conversion CTA; teach-first CTA ordering
- Learner Level + Course/Program UX refinements (difficulty-aware quiz prompts, context-narrowed autocomplete)
- Conversion funnel optimization (plan-aware upgrade CTAs, post-quiz nudges, analytics events)
- Retention loops (continue-studying prompts, weak-concept reminder emails, near-limit banners)
- Backend Public Library filtering + shareable URLs
- Social login — Google first
- Faster quiz generation investigation
- Multi-topic exam / Long Exam mode planning (design only)

See `docs/product/ROADMAP.md` for the full planned scope and implementation stances.

## MVP goal

Capture notes -> generate Study Pack outputs -> review -> improve -> copy -> repeat.

## Privacy

Uploaded images are deleted after OCR processing.
Avoid logging raw images or full extracted text.
