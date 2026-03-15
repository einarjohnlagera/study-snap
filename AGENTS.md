# AGENTS.md — Study Snap

You are an AI coding agent helping implement Study Snap.
Follow these rules strictly to keep the codebase consistent and shippable.

When working on a feature, always check the corresponding document under docs/features/.

## Product summary

Study Snap converts study notes into structured study materials and practice quizzes.

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
- route `/` should clearly communicate Study Snap value for unauthenticated users
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
- payments / Stripe
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

### Theme + Navbar requirements
- Support light/dark theme with a toggle in the navbar.
- Tailwind should use class-based dark mode.
- Navbar appears on all pages.
- Navbar includes Study Snap brand text, logo placeholder, menu links, and theme toggle.
- Theme toggle avoids hydration mismatch by using a mounted guard.

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
- Premium includes everything in Free, plus weak concept detection, adaptive quiz generation, and advanced review capabilities

Plan & Billing UX:
- Settings includes a `Plan & Billing` section where users can view plan and monthly usage progress
- Premium upgrade action can use a placeholder flow until billing integration is implemented
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

Study Snap is not only a generator; it is a study workspace.

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

Quick Review post-results confidence feedback:
- results should include an optional confidence prompt (`Very confident`, `Somewhat confident`, `Not confident`)
- map to stored values `HIGH`, `MEDIUM`, `LOW`
- confidence feedback must be non-blocking (session remains valid if skipped)
- confidence data is intended for future adaptive recommendations and learning insights (no heavy analytics in this scope)

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
