# AGENTS.md — NoteLib

You are an AI coding agent helping implement NoteLib.
Follow these rules strictly to keep the codebase consistent and shippable.

Rebrand note: StudySnap has been renamed to NoteLib. Keep database schema/table names unchanged unless explicitly requested.

When working on a feature, always check the corresponding document under docs/features/.

## Product summary

NoteLib converts study notes into structured study materials and practice quizzes.

Core feature is **not** “solve a question.”
It is:

**Notes → Study Pack → Revisit later**

Inputs:
- pasted notes text
- uploaded image of notes (OCR)

Outputs:
- title
- summary
- key concepts / definitions
- practice quiz questions

Tone:
- calm
- patient
- non-judgmental
- supportive

## V2 Notes and Study Pack architecture (required)

- V2 uses a strict `1 Note <-> 1 current Study Pack` relationship.
- Users can create and save notes.
- Each note has one current Study Pack.
- Regenerating a Study Pack replaces the current Study Pack for that same note.
- Do not implement visible Study Pack version history in V2.

Future direction (not V2):
- If versioning is required later, use a dedicated `study_pack_history` snapshot table.
- Do not turn V2 into a multi-pack-per-note architecture.
- Candidate snapshot fields may include: `id`, `parent_study_pack_id`, `note_id`, `title`, `summary`, `subject`, `concepts_json`, `questions_json`, `version_number`, `archived_at`.

Refer to:
- `docs/product/SPEC.md` for product behavior
- `docs/architecture/ARCHITECTURE.md` for system design
- `docs/architecture/DATA_MODEL.md` for entities and ownership
- `docs/features/` for feature-specific context
- `docs/ai/PROMPTS.md` for LLM prompt assets and JSON contract

## MVP scope (do not expand without request)

Pages:
- Landing
- Study (paste notes + upload image)
- Results (study pack sheet + quiz)

Public landing page requirements:
- route `/` should clearly communicate NoteLib value for unauthenticated users
- include hero, Study Pack preview, how-it-works, feature highlights, pricing teaser, final CTA, and FAQ
- FAQ should be placed before the footer and use a lightweight accordion layout
- primary CTA should drive account creation; secondary CTA may drive demo exploration
- demo exploration route should be `/demo`

Backend:
- One primary endpoint family for Study Pack generation
- OCR confirmation flow for image-based inputs
- Share endpoints for public Study Pack links
- Health endpoint optional

MVP includes:
- Study Pack generation from text
- OCR flow for images with low-confidence fallback
- Global navbar + theme toggle
- Images deleted after processing
- Study Library direction
- Demo mode guardrails
- Settings `Plan & Billing` section (current plan, monthly usage, upgrade CTA)
- Dashboard Free-plan usage indicator with upgrade path

Not in MVP unless explicitly requested:
- flashcards / spaced repetition
- gamification
- heavy analytics dashboards
- classroom management
- teacher mode
- family linking
- full exam simulation grading engine

## UX principles

- “Friendly academic”: clean like Khan Academy, slightly warm, not childish
- Slightly guided flow
- Minimal distractions
- Error states are supportive and actionable
- Streak mechanics are opt-in only through user engagement mode; default experience stays calm (`FOCUSED`)
- For Study Pack detail guidance, prefer compact data-driven coaching (latest weak concepts + next step) before adding new LLM calls

Microcopy:
- Prefer: “Let’s turn your notes into a study pack.”
- Avoid: “Crush this!”, “Hurry up!”, “Wrong!”

## Frontend conventions (`/frontend`)

Stack:
- Next.js App Router
- TypeScript
- Tailwind CSS
- shadcn/ui preferred
- lucide-react icons
- next-themes for theme switching

Rules:
1. Use shadcn/ui components for Button/Input/Card/Alert/etc.
2. Keep pages thin; place logic in `lib/`, hooks, and focused components.
3. All backend calls go through `lib/api.ts`.
4. Always handle loading and error states.
5. Use shadcn tokens (`bg-background`, `text-foreground`, etc.) for theme consistency.

### Auth session behavior
- Centralize authenticated request handling in `frontend/lib/api.ts` (avoid per-page 401 handling).
- When a protected API call returns `401`, clear auth state and redirect to `/login`.
- Preserve destination on login redirects using `redirect` query (for example `/login?redirect=/study-packs/{id}`).
- Include a session-expired hint on login when redirecting after invalid session (`reason=session_expired`).
- Protected routes should use shared route guards so unauthenticated access is redirected consistently.
- Users may sign up and log in before email verification is completed.
- Unverified users must not generate Study Packs; generation endpoints should enforce `email_verified_at`.
- Show a non-disruptive verification banner in authenticated shell pages until verified.
- Verification-required generation blocks should return structured `403` errors with `code=EMAIL_VERIFICATION_REQUIRED` and `action=RESEND_VERIFICATION`.
- Verification email flow should use a provider-agnostic `EmailService`; keep provider-specific logic in implementation classes (for example Resend).
- Transactional email content should use file-based templates with parameter placeholders; do not store templates in database tables.
- Email campaigns/blasts are out of scope unless explicitly requested.

### Theme + Navbar requirements
- Support light/dark theme with a toggle in the navbar.
- Tailwind should use class-based dark mode.
- Navbar appears on all pages.
- Navbar includes NoteLib brand text, logo placeholder, menu links, and theme toggle.
- Theme toggle avoids hydration mismatch by using a mounted guard.

### Profile vs Settings ownership
- Profile page should focus on identity information and profile type only.
- Profile may include a lightweight account-information overview (`Member since`, `Plan`, `Study Packs created`).
- Keep plan management under Settings `Plan & Billing`.
- Do not add a generic actions section (for example `Manage Plan`) to Profile.

## Backend conventions (`/backend`)

Rules:
1. Controllers stay thin; business logic belongs in services.
2. Prefer one orchestrator service for Study Pack generation:
   validate → OCR (if image) → normalize → LLM → validate output → persist → return
3. Enforce input limits server-side.
4. Do not store uploaded images permanently; delete them after OCR.
5. Log request id, latency, and failure codes.
6. Avoid logging raw images or full extracted text.
7. Persist only validated LLM output.
8. Favor Study Pack terminology in new code, even if some legacy code still uses study pack.

## Backend module plan (Spring Boot)

Controllers:
- `StudyPackController` / future `StudyPackController` transition path
- `ShareController`
- `HealthController` (optional)
- future auth controllers when user accounts are implemented

Services:
- `StudyPackService` or `StudyPackService` orchestrator
- `OcrService`
- `LlmStudyPackService`
- `UsageLimitService`
- `ShareService`
- future `UserAccountService`
- future `SubscriptionService`

Persistence:
- `StudyPackRepository` / future `StudyPackRepository`
- `ShareLinkRepository`
- optional `StudyPackDraftRepository` for OCR confirmation flow
- future `UserRepository`
- future `SubscriptionRepository`

## API contract guidance

Current and near-future endpoints are documented in:
- `docs/architecture/ARCHITECTURE.md`
- feature files under `docs/features/`

Agent rule:
- preserve backward compatibility where practical
- prefer evolving the domain language from study pack toward Study Pack
- do not silently invent new payload fields not described in the docs

## Cost control (required)

Use tiered model strategy:
- cheap model: text cleanup / OCR formatting (optional)
- standard model: summary + key concepts + practice quiz
- higher quality model: premium-only features later

Initial model decision:
- Demo and Free plans use `gpt-4.1-mini`

Plan and quota policy:
- Free: 5 Study Packs/month
- Free includes AI summaries, key concepts, Quick Review, retry, Study Library, Today's Focus, and AI Study Coach
- Premium: 100 Study Packs/month
- Premium includes everything in Free, plus weak concept detection, adaptive practice, Challenge Quiz, and advanced review capabilities
- Premium Challenge Quiz quota: 50 sessions/month
- Premium Adaptive Practice quota: 50 sessions/month
- Challenge Quiz and Adaptive Practice usage are tracked separately from Study Pack generation credits

Plan & Billing UX:
- Settings includes a `Plan & Billing` section where users can view plan and monthly usage progress
- Plan & Billing usage visibility should show separate buckets with progress bars:
  - Study Packs (monthly plan quota)
  - Challenge Quiz (50/month)
  - Adaptive Practice (50/month)
- Do not merge quiz usage into Study Pack usage; each bucket is independent
- Premium upgrade uses Stripe Checkout
- Stripe webhook events should keep `FREE`/`PREMIUM` plan state in sync
- Premium-gated prompts should direct users to Settings `Plan & Billing` (`/settings#plan-billing`)
- Dashboard should show a Free-plan monthly usage indicator and subtle upgrade action

Config knobs may include:
- `LLM_MODEL_FREE`
- `LLM_MODEL_PREMIUM`
- `QUIZ_QUESTIONS_FREE`
- `QUIZ_QUESTIONS_PREMIUM`
- `MAX_NOTES_CHARS_FREE`

## Demo guardrail

Demo mode must not call the real LLM generation pipeline.

For demo mode (`/demo` or `?demo=true`):
- prefill sample notes
- simulate generation delay
- return static placeholder study pack
- do not save to database
- do not count toward usage
- do not trigger OpenAI API calls

## Study Library direction

NoteLib is not only a generator; it is a study workspace.

Generated outputs should be treated as reusable Study Packs.

The dashboard should present saved Study Packs in a clean library-style layout.
The dashboard is guidance-first and non-destructive; destructive actions such as deleting a Study Pack belong in the Library page.
Dashboard guidance should include a lightweight `Mastery Snapshot` summary card based on existing completed Quick Review session data (average recent score, best recent score, Study Packs reviewed), with a supportive empty state when no completed reviews exist.

MVP library actions:
- open
- delete (Library page only; not from Dashboard)
- delete actions in Library must use explicit confirmation
- search by title/tags and lightweight client-side sorting should be available in Library for browsing
- subject filtering should be available in Library as single-select (`All subjects` default)
- tag filtering should be available in Library as a multi-select dropdown with OR matching across selected tags
- subject/tag filters should combine with search and stay frontend-only on currently loaded Study Pack items
- active filter chips (subject/tags) should support per-chip removal and `Clear all`
- show tags as chips on Library cards to improve scanability
- use clickable Study Pack cards/titles as the primary open interaction (avoid explicit `Open` buttons)
- keep Quick Review as a secondary action where shown
- use paginated Study Pack loading in Library (cursor-based, default page size `20`) with explicit `Load More`

Future actions:
- rename
- search
- advanced filtering
- reviewed status
- folders / collections

## Shareable Study Packs

Study Packs support public token sharing and remixing.

Rules:
- public share links should use `/p/{token}`
- shared page layout is auth-aware:
  - unauthenticated viewers use the public minimal navbar
  - authenticated viewers use the app shell/sidebar layout
- shared Study Pack pages are read-only
- shared pages can show title, summary, key concepts, and quiz preview
- remix/copy should duplicate into the current user's Study Library
- when remixing, duplicate titles for the same user must auto-resolve:
  - `{Title}`
  - `{Title} (Copy)`
  - `{Title} (Copy 2)`, `{Title} (Copy 3)`, ...
- remix must not trigger a new LLM generation request
- original shared Study Pack remains immutable
- in-product sharing should be discoverable on Study Pack detail (`Copy Link` + confirmation feedback)
- successful remix should show confirmation feedback (`Study Pack copied to your library.`)

## OCR strategy guidance

Use hybrid OCR:
1. quick text detection
2. full OCR only if text is detected

Normalize OCR text before sending it to the LLM:
- trim whitespace
- collapse repeated spaces
- remove broken line breaks where possible
- preserve paragraph structure

## Quiz quality guidance

Practice quizzes should feel like real study reviewers, not generic AI trivia.

When generating quiz questions:
- mix recall, understanding, and application question types
- keep questions answerable from the notes
- prioritize clarity over cleverness
- avoid hallucinating specific details not present in the notes

Quiz answer feedback semantics (Quick Review and Adaptive Quiz):
- correct answer state uses green with `✓ Correct`
- selected incorrect answer state uses red with `✗ Incorrect`
- if a user selects the wrong option, show both wrong selection (red) and correct option (green)
- non-selected, non-correct options remain neutral
- do not use blue for correct/incorrect answer states

Challenge Quiz guidance:
- Challenge Quiz is Premium-only and timed
- Challenge Quiz generates new questions via LLM
- Challenge input must use only Study Pack summary + key concepts (never extracted OCR text)
- question count and difficulty should adapt to the latest Quick Review score:
  - score < 50: 10 questions, easy-medium
  - score < 80: 12 questions, medium
  - otherwise: 15 questions, medium-hard
- Challenge generation must not duplicate stored Study Pack quiz questions
- Challenge sessions must reuse existing generated quiz when an in-progress session exists (avoid extra LLM calls)
- Challenge Quiz usage must be tracked separately from Study Pack generation quota

Adaptive Practice guidance:
- Adaptive Practice is Premium-only and LLM-generated
- Adaptive input must use summary + key concepts + weak concepts only
- adaptive question count should follow weak-concept volume:
  - <= 2 weak concepts: 5 questions
  - <= 4 weak concepts: 7 questions
  - otherwise: 10 questions
- Adaptive generation must avoid duplicates with stored quiz questions
- Adaptive sessions must reuse existing generated quiz while in progress (avoid extra LLM calls)

Quick Review post-results confidence feedback:
- results should include an optional confidence prompt (`Very confident`, `Somewhat confident`, `Not confident`)
- map to stored values `HIGH`, `MEDIUM`, `LOW`
- confidence feedback must be non-blocking (session remains valid if skipped)
- confidence data is intended for future adaptive recommendations and learning insights (no heavy analytics in this scope)

Quick Review guided next-step CTA behavior:
- if results indicate struggle (for example lower score or weak concepts), prefer an Adaptive Practice next-step CTA
- if results indicate strong performance, prefer a Challenge Quiz next-step CTA
- keep `Back to Study Pack` as the primary completion action

Study Pack detail quiz entry hierarchy:
- `Start Quick Review` is the primary action
- `Challenge Quiz` is secondary and visible (premium-gated for Free users)
- `Adaptive Practice` should be shown only when weak concepts are available

## Legacy preservation note

The legacy project docs remain available under `/legacy`.
When differences exist:
- use the refactored docs as the organized working set
- use `/legacy` for historical cross-checking

## Testing

Refer to docs/testing/TESTING_STRATEGY.md for testing guidelines.

When creating tests:
- follow the testing pyramid
- prefer service-level tests
- avoid excessive controller tests

## Feature documentation

When working on a feature, always check the corresponding file under docs/features/.

Examples:
- docs/features/quick-review.md
- docs/features/dashboard-recommendation.md

SPEC.md contains high-level product behavior.
Feature docs contain detailed feature rules and workflows.
