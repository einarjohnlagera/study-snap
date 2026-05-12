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
- courseProgram
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
- `/public/library`
- `/notes/{id}` (Note Detail)
- `/public/library/{subject}/{slug}` (Public Note Detail, read-only)
- `/public/creator/{username}` (canonical Public Creator/Profile page)
- `/public/profile/{userId}` (legacy-compatible Public Profile route)

Private Library readiness:

- `Study Pack Ready` is the learner-facing readiness indicator for Student and Board Taker profiles.
- `Quiz Ready` is a Teacher/exam-export workflow indicator. Show it in normal private Library browsing only when the active profile is Teacher.
- Public Library must not expose Teacher-specific `Quiz Ready` filters or badges; public notes stay focused on summary, key concepts, Quick Check, and copy/share flows.
- Exam Builder may continue to use generated-quiz readiness internally for selection, question counts, and export eligibility.

## Hash Navigation Pattern

- Deep links to page sections are part of the product flow, not an incidental browser behavior.
- When a page exposes a section-targeted CTA such as `View Full Notes →` or `Adjust level`, the target section must use a native DOM `id`.
- App Router pages that can load directly with `#fragment` must re-apply scrolling after mount via the shared `HashScrollListener` so direct URL visits and later hash changes land on the intended section.
- Current examples:
  - `/profile?from=dashboard#learning-profile`
  - `/public/library/{subject}/{slug}#full-notes`

## Verification and OCR

- Unverified users are blocked from Study Pack generation.
- Unverified users are blocked from OCR upload.
- OCR is optional in Create/Edit Note and populates Note content for manual review before save/generate.
- Generate Note from topic is available in Create Note and fills editable note content before save.
- Generate Note from topic must use the current Create Note Course / Program selection on the first request. Profile Course / Program is fallback only when the draft field is blank; do not rely on stale profile defaults or merge unrelated Course / Program values.

## Authentication

- NoteLib supports email/password login and Google login.
- Google social login is an alternative, not a replacement; existing email/password accounts must keep working.
- Google ID tokens are verified server-side before account creation, login, or linking.
- Only Google accounts with `email_verified=true` may create or link accounts automatically.
- If a verified Google email matches an existing NoteLib user, the Google provider is linked to that user instead of creating a duplicate account.
- Google-only signups set `emailVerifiedAt` immediately and do not require a separate NoteLib verification email.
- Connected sign-in methods are shown in Profile so users can see whether email/password and Google are enabled.
- Public-note conversion flows may use Google login/signup to reduce signup friction, but redirect behavior must still follow the existing auth redirect rules.

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
- keep the note primary; the quiz preview supports engagement but does not replace the note

Public mini quiz rules:
- public visitors may answer a small preview of 1–2 questions
- answers are client-side only — no session row is created for anonymous users
- full quiz access, score persistence, and Study Pack generation require login
- the signup gate must appear only after the visitor has experienced some value, not on page load
- after the visitor answers the preview question, show a small next-step CTA such as `Create your own Study Pack` or `Copy to My Library`

Generated note formatting for public pages:
- prefer shorter sections, clearer headings, key-fact blocks, and quick recall blocks
- avoid long paragraph-dense LLM output; public pages should read like a study reviewer, not a transcript
- formatting improvements apply to generated content displayed on public note pages; they do not change the underlying storage format

CTA ordering on public note detail:
- show topic hook → mini quiz → summary/key concepts → soft conversion CTA → copy/generate CTA
- do not lead with `Copy to My Library` or `Create your own Study Pack` before the visitor has seen learning value
- use `Create your own Study Pack` for visitor-facing generation CTAs and keep `Copy to My Library` for library-oriented copy actions
- `Share this note` should stay visible as a secondary action regardless of auth state

Public creator identity direction:
- `displayName` is presentation-only; it is not a unique creator identity
- `username` is the stable public creator identity / handle and must be unique
- public note cards and public note detail use `displayName` for readability and `@username` for trust and duplicate-name disambiguation
- public creator links should use `/public/creator/{username}` when a username is available
- never expose raw user IDs or emails on public note or public profile surfaces
- legacy `/public/profile/{userId}` links remain valid for compatibility

## Quiz Mode Hierarchy

NoteLib supports exactly five quiz-flavored modes, organized into two families:

- **Practice modes** — Quick Review (all plans), Adaptive Practice (Plus / Pro per `PLANS.md`)
- **Exam modes** — Challenge Quiz (all plans), Long Exam Mode (Student-facing, planned), Board Exam Mode (Board Taker, Pro)

All five modes share a single backend pipeline ("Quiz Session Engine") via the `quizSession` aggregate. Mode discriminators (`QUICK_REVIEW`, `CHALLENGE`, `ADAPTIVE`, `LONG_EXAM`, `BOARD_EXAM`) parameterize behavior without forking storage or persistence.

Identity rules:

- **Challenge Quiz** is flexible, progressive, user-controlled — *practice with stakes*. It must keep its `+5 Questions`, early-submit, and learner-level adjustability.
- **Long Exam Mode** (Student profile) is long-form mastery testing — fixed at start, pause-friendly, mastery-report result. Multi-note coverage is a capability, not a separate mode.
- **Board Exam Mode** (Board Taker profile) is high-stakes simulation — pre-flight setup, no progressive generation, no inline learner-level adjustment, score-report result. It must never become "a longer Challenge Quiz."

Profile-aware presentation (implemented in `lib/exam-mode-visibility.ts`):

- Student → Challenge Quiz (Recommended) + Long Exam Mode (Coming Soon). Board Exam hidden; cross-profile escape-hatch line shown.
- Board Taker → Board Exam (Recommended, Pro) + Challenge Quiz. Long Exam not shown.
- Teacher → Challenge Quiz only; skips mode-selection screen to Challenge Quiz setup directly.

Profile-type filtering is presentational only; the engine accepts any mode for any user.

The canonical mode-hierarchy reference, identity contracts, monetization positioning, and roadmap pointers live in `docs/product/EXAM_MODES.md`. Adding a sixth quiz-flavored mode requires updating that document.

## v0.12.0 Direction

Current in-progress release: `v0.12.0 - Learning Experience, Discovery, and Retention`.

Key v0.12.0 changes to be aware of:

- **Learner Level drives quiz difficulty and style** — quiz generation prompts use `learnerLevel` for question complexity, explanation depth, and vocabulary; this is an LLM prompt enhancement, not a new UI field
- **Course/Program drives domain context** — course/program is passed to quiz and Study Pack generation prompts so examples and questions stay relevant to the student's discipline; it is a separate concern from Learner Level and must not be merged with it
- **Note metadata is the generation source of truth for domain context** — when a note has its own `courseProgram`, Study Pack, Challenge Quiz, Board Exam, and Adaptive Practice generation must use `notes.courseProgram` before falling back to the user's profile default
- **AI metadata suggestions stay transient in the normal note flow** — generated `title`, `subject`, and `tags` must not be persisted before user confirmation; onboarding may explicitly opt into auto-apply for empty metadata as a guided-flow exception
- **Upgrade CTA rule is now enforced** — all paywall and limit surfaces use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`; upgrade CTAs navigate to `/settings?section=plans`, not `/pricing`
- **Analytics funnel is tracked** — `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, and `UPGRADE_CLICKED` are in `AnalyticsEventType` and fired from the relevant completion blocks
- **Public Library filters are URL-driven and backend-backed** — subject, tags, course/program, audience, search, and sort flow through shareable `/public/library?...` URLs and the public-notes backend filter contract; the list page `Share this list` action must copy that same canonical filtered URL
- **Public Library route and filters are canonicalized** — `/public/library` is the only canonical public-library browsing route for signed-in and signed-out users; `/library/public` and `/public/library/{subject}` are compatibility redirects only, and active public-library filters must stay URL-driven and shareable
- **Public Library filter UX must stay stable while syncing the URL** — search uses local input state plus a short debounce before `replace`-ing the canonical `/public/library?...` URL; filter updates preserve scroll position, tag browsing stays reachable through a dedicated `Browse all` action, and selector search inputs must not lose focus during typing
- **Public creator identity safety** — `username` is now the stable public identifier for attribution and `/public/creator/{username}` links; `displayName` remains presentation-only, and legacy public-profile links stay compatible
- **Social login (Google)** — implemented as an alternative to email/password; verified Google emails create/link accounts without duplicates, and Profile shows connected sign-in methods
- **Content moderation** — `ContentModerationService` in the backend applies token-based dictionary matching to note titles, Study Pack topics, and note content at creation boundaries; dictionaries are in `classpath:/moderation/banned_words_*.txt`

## AI Development Workflow

NoteLib development uses a lightweight AI Skills system to reduce prompt fatigue and improve consistency across sessions.

- Claude is used for product thinking, UX reviews, roadmap alignment, and architecture discussions
- Codex is used for implementation, refactoring, cleanup, migrations, and tests
- Reusable workflow patterns are documented in `docs/skills/`

Skills cover: Codex prompt structure, UX product review, release/doc alignment, and roadmap/feature auditing. Read `docs/skills/README.md` for Claude vs Codex guidance and model/effort recommendations.

## Feature Documentation

- docs/features/onboarding.md
- docs/features/study-pack-generation.md
- docs/features/quick-review.md
- docs/features/dashboard-recommendation.md
- docs/features/profile-learning-context.md

## Architecture

See `docs/architecture/ARCHITECTURE.md`.
