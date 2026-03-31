# AGENTS.md - NoteLib

You are an AI coding agent helping implement NoteLib.
Follow these rules to keep the codebase consistent and shippable.

Rebrand note: StudySnap has been renamed to NoteLib. Keep existing database schema/table names unless explicitly requested.

When working on a feature, always check the corresponding document under `docs/features/`.

## Product Summary

NoteLib converts notes into structured study outputs and review workflows.

Core loop:

`Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat`

## Required Product Architecture (Current)

- Note is the primary entity.
- Study Pack is generated content attached to a Note.
- A Note has state:
  - `DRAFT`
  - `STUDY_PACK_READY`
- A Note also has visibility:
  - `PRIVATE`
  - `PUBLIC`

### Versioning Rule

- Do not regenerate/overwrite generated content on the same Note.
- Use `Make a Copy` only.
- Copy includes: `title`, `subject`, `tags`, `content`.
- Copy does not include: generated `summary`, `key concepts`, `quiz`, session history, or performance history.
- Copy result is a new `DRAFT` note.

### Premium Cancellation Rule

- Premium cancellation must be confirmed in Settings before submission.
- Cancellation is scheduled at the end of the current billing period, not immediate.
- Premium access remains active until that period ends.
- Downgrade to Free happens through subscription lifecycle logic at period end.
- Canceling Premium must not remove notes or generated Study Packs from the user library.
- Settings billing should show scheduled end-of-period cancellation clearly in the subscription summary and must not imply immediate loss of access.

### Premium Upgrade Prompt Rule

- Free users should see a soft paywall modal before any Premium-only quiz feature or Study Pack limit block attempts a paid conversion flow.
- During the current pre-launch phase, `Upgrade to Premium` should open a `Premium is coming soon` modal and offer `Join Waitlist`, not payment.
- Waitlist joins should call `POST /api/premium/waitlist` and remain idempotent per authenticated user.
- At `80%` of the Free Study Pack limit, show a non-blocking upgrade banner on Dashboard, Note Detail, and Study Pack generation surfaces.
- Upgrade messaging should position Premium as an exam-preparation and mastery tool for students.
- Pre-launch modal copy should make it clear that payments are still being enabled and that users can join the waitlist for launch access.
- Dashboard should show a Free-only upgrade card highlighting Challenge Quiz, Adaptive Practice, and the `100` Study Pack Premium limit.
- Pricing page should clearly compare Free vs Premium with localized backend pricing and student-oriented value messaging.

### Marketing Landing Page Rule

- The landing page must explain NoteLib in student terms: notes -> summaries -> quizzes -> review.
- Keep the home page focused on hero, how-it-works, features, Free vs Premium pricing, demo access, and signup CTA.
- Demo access must be available without signup.
- Pricing shown on marketing surfaces must still come from backend-owned pricing APIs or shared pricing components.
- Landing page metadata should position NoteLib as a note-to-study-pack product, not a generic AI assistant.
- Public marketing/auth surfaces should expose footer links to:
  - `Privacy Policy`
  - `Terms of Service`
  - `Contact`

### Legal Pages Rule

- `Privacy Policy` and `Terms of Service` must remain public and accessible without login.
- Public routes are:
  - `/privacy`
  - `/terms`
- Legal copy should stay simple, readable, and professional rather than highly styled.
- Contact email for launch/legal pages is `support@mail.notelib.app`.

### Onboarding Rule

- Onboarding is active again for all verified users, not only Premium users.
- Onboarding should happen once after email verification / first verified entry into the app.
- Onboarding must stay short and reuse the existing step flow.
- Preferences onboarding order is:
  - `Profile Type`
  - `Learning Style` (`engagementMode`)
  - `Study Reminder Frequency`
  - `Exam Date` only for `BOARD_EXAM`
- Profile Type can be edited later in `Profile`.
- Learning Style can be edited later in `Settings > Preferences`.
- Study Reminder Frequency can be edited later in `Settings > Preferences`.
- Public pages and anonymous flows must not be blocked by onboarding.
- NoteLib also has a separate first-study product onboarding flow for brand-new users with `studyPackCount == 0`.
- Product onboarding guides the first workflow:
  - `Verify Email`
  - `Create Note`
  - `Generate Study Pack`
  - `Challenge Quiz`
  - `Weak Concepts`
  - `Dashboard`
- After email verification, first-time users should see a welcome CTA before an empty dashboard so they know to create their first note immediately.
- Empty dashboard states for first-time users must be instructional, not generic.
- After the first Study Pack is generated, Note Detail should point users to Challenge Quiz as the next action.
- After the first Challenge Quiz is completed, surface weak-concept guidance before returning users to normal study flows.
- Product onboarding completion is tracked separately from preferences onboarding and should not reuse `onboardingCompletedAt`.

### Profile Rule

- `Profile` owns identity and account-related information only.
- `Profile` sections are:
  - `Identity`
  - `Profile Type`
  - `Account Information`
- Identity uses:
  - `firstName`
  - `lastName`
  - `email`
- Do not collapse `firstName` and `lastName` into one `name` field in product UI or API contracts unless explicitly requested.
- `Profile Type` remains editable in `Profile` as a separate save action.
- Do not move `Learning Style` or study-reminder preferences into `Profile`.
- Email changes must write `pendingEmail` first and only update `email` after verification.

### Preferences Rule

- `Settings` should show `Preferences` before `Plan & Billing` and `Account`.
- `Preferences` currently includes `Learning Style` plus `Study Reminders`.
- `Learning Style` is stored as `engagementMode`.
- Reminder toggles are:
  - `inactivityRemindersEnabled`
  - `weakConceptRemindersEnabled`
- Preference values must persist in backend and be returned by `GET /auth/me`.
- Future reminder cadence should be guided by `Learning Style`, but scheduling logic is a separate task.

### Analytics Rule

- Track product, growth, and upgrade events through the shared analytics event model.
- Analytics must be non-blocking and must never break the primary user action if persistence fails.
- Backend services should record server-truth events for note, Study Pack, review, auth, public-copy, and subscription flows.
- Frontend/browser-only funnel events may post through `/api/analytics/events`.
- Admin reporting should read from analytics events plus core entity counts via `/api/admin/analytics/summary`.

### Retention Email Rule

- Retention emails are scheduled backend jobs, not request-time actions.
- V1 email types are:
  - `WELCOME`
  - `INACTIVITY`
  - `WEAK_CONCEPT`
  - `UNFINISHED_NOTE`
- Retention emails must log sends in `email_log` and respect same-type cooldowns before sending again.
- `INACTIVITY` and `UNFINISHED_NOTE` should honor `inactivityRemindersEnabled`.
- `WEAK_CONCEPT` should honor `weakConceptRemindersEnabled`.
- Reminder cadence may later vary by `Learning Style`, but V1 stores the inputs and uses fixed thresholds.

### Verification Email Rule

- After a user successfully verifies their email, send a one-time welcome email.
- The welcome email should link to `Dashboard` and explain the first-study-pack flow.
- Welcome emails must only send once per user and should be guarded through `email_log`.
- User-facing email templates should greet recipients with:
  - `Hi {firstName},` when `firstName` exists
  - `Hi there,` when it does not
- User-facing email templates should end with the standard footer:
  - `— NoteLib`
  - `Turn Notes Into Quizzes`
  - `https://notelib.app`
- Welcome email copy must reflect the current Free vs Premium plan:
  - Free includes `10` Study Packs/month, Quick Review, limited Challenge Quiz, and Public Library access
  - Premium messaging highlights Adaptive Practice, Weak Concept Training, Difficulty Selection, and higher limits
- Do not describe Challenge Quiz as Premium-only in onboarding, welcome, or reminder emails.

### Admin Dashboard Rule

- Admin Dashboard is internal and read-only in v1.
- Access must be restricted to `ADMIN` users.
- Reuse existing analytics, billing, subscription, payment, and library data before adding new reporting storage.
- Prefer summary cards and simple tables over filters, charts, or exports unless explicitly requested.
- Admin v1 should cover overview, billing, engagement, public-content growth, recent upgrades, recent failed payments, and recent feedback.

### Feedback Rule

- Authenticated app users should be able to submit in-app feedback during soft launch.
- Feedback should capture `message`, authenticated `userId`, `email`, and the current page URL.
- Feedback submission must persist to the `feedback` table and may send a best-effort support notification email.
- Admin Dashboard should expose recent feedback in a read-only table.

### Pricing Rule

- Backend owns subscription pricing, region detection, voucher eligibility, and PayMongo plan selection.
- Frontend must use the billing pricing API for pricing display in Settings, pricing surfaces, and upgrade prompts.
- Do not hardcode Premium prices in frontend code.
- Intro pricing and first-time promos must be implemented through the voucher/promotion system, not as a boolean on `User`.
- While Premium checkout is pre-launch, pricing surfaces should still show backend pricing but route upgrade intent into the Premium waitlist modal instead of payment.

### Billing History Rule

- `Settings -> Plan & Billing` should include a read-only billing history section below the current plan and usage card.
- The billing summary card should show current plan, subscription status, billing cycle, and renewal or end date.
- If `cancelAtPeriodEnd=true`, show that Premium will end on the stored date and will not renew.
- Payment history must come from `PaymentTransactionEntity` data via `GET /api/billing/history`.
- Billing history rows should stay user-friendly and must not expose raw webhook event names.

### Library Rule

- My Library is note-based and contains the current user's notes (Draft + Study Pack Ready).
- Public Library is note-based and contains notes where `visibility=PUBLIC`.
- Public Library should include the current user's own public notes, other users' public notes, and official NoteLib public/sample notes.
- Public Library cards should label note source as:
  - `By You` for the current user's own public notes
  - `By NoteLib` plus `Official` badge for the official NoteLib account
  - `By {displayName}` for other public notes
- Public author labels are viewer-relative:
  - owner viewing own public note -> `By You`
  - official NoteLib account -> `By NoteLib` with `Official`
  - all other public notes -> `By {displayName}`
- `users.display_name` is the public author field. Never show public author emails.
- Reserved display names must be blocked server-side. Reject exact matches for:
  - `notelib`
  - `admin`
  - `support`
  - `official`
  - `moderator`
  - `staff`
  - `team`
- Also reject any display name containing `notelib` and return:
  - `This display name is reserved. Please choose another name.`
- Public note detail should switch its primary CTA by ownership:
  - owner -> `Open Note`
  - non-owner -> `Make a Copy`
- Public note detail header should show `Subject • Author` using the same viewer-relative label logic as library cards.
- Public note detail is read/copy/share only:
  - owner -> `Open Note`, `Share`
  - non-owner -> `Make a Copy`, `Share`
- Public note detail should not expose edit, delete, generation, or study actions; generation remains a Note Editor responsibility and quizzes remain on study surfaces.
- Public Library canonical SEO index route is `/public/library`; app-shell `/library/public` is not the canonical indexed route.
- Public SEO note pages use `/public/library/{subject}/{slug}` as the canonical route.
- Public SEO pages must stay accessible without login and indexable only for `PUBLIC` notes.
- Public landing page should emit JSON-LD `WebSite` schema.
- Public Library index should emit JSON-LD `CollectionPage` schema.
- Public SEO note pages should emit JSON-LD `Article` schema using real note data only.
- `robots.txt` must allow public crawling and disallow authenticated/private app areas such as `/dashboard`, `/library`, `/notes`, `/settings`, `/admin`, and `/api`.
- `sitemap.xml` must include only public SEO-safe routes: `/`, `/privacy`, `/terms`, `/public/library`, canonical public subject URLs, and canonical public note URLs.
- Private notes must never be exposed through the public SEO route.
- Copying a public note must preserve attribution via `copiedFromNoteId` and `copiedFromUserId`.

### Note Ownership Rule

- Generated outputs (summary/key concepts/quizzes), Quick Review, Challenge Quiz, Adaptive Practice, and performance are scoped to `noteId`.
- If legacy payload fields still expose `studyPackId`, treat them as compatibility fields, not primary ownership.

### Profile Type UX Rule

- Do not create separate entities or table flows per profile type.
- Profile Type only changes UI, workflow emphasis, labels, recommendations, and default presentation.
- Shared engine remains:
  - `Note -> Study Pack -> Quiz -> Activity -> Weak Concepts`
- `STUDENT` emphasizes review continuity.
- `BOARD_EXAM` emphasizes quiz practice and weak-area drilling.
- `TEACHER` emphasizes quiz creation from the same note pipeline.

## UI Terminology (Use Consistently)

- `Dashboard`
- `My Library`
- `Public Library`
- `Note Detail` (unified Note + Study Pack view)
- `New Note`
- `Generate Study Pack`
- `Make a Copy`
- `Copy to My Library`
- `Make Public`
- `Make Private`

Avoid introducing older terms such as `Study Library` or regenerate/overwrite flows.

## Navigation Structure

Keep app shell grouping:

- Main:
  - Dashboard
  - My Library
  - Public Library
- Account:
  - Profile
  - Settings
  - Admin (admins only)

## UI Interaction Guardrails

- Keep note cards consistent across Dashboard, My Library, and Public Library:
  - entire card click opens note detail
  - tertiary actions live in card menu (My Library) rather than primary card buttons
- `My Library` should expose a direct `Create Note` entry in the header and empty state so users are not forced through `Dashboard` to start a note.
- Note Editor actions:
  - keep `Generate` as the primary CTA and `Save` as the secondary CTA
  - desktop should show actions at the top and bottom of long note forms
  - mobile should keep a floating primary generate button visible while scrolling
- Generate button wording may vary by `profileType` (`Generate`, `Practice`, `Create Quiz`) but must still hit the same Study Pack generation flow.
- Keep primary button labels short; longer outcome explanations belong in helper text below the generate button.
- After generation, default tab should vary by `profileType`:
  - `STUDENT` -> `tab=summary`
  - `BOARD_EXAM` -> `tab=quiz`
  - `TEACHER` -> `tab=quiz`
- Teacher dashboard should prioritize quiz creation and material upload, but still use the shared note-first pipeline.
- Use one shared modal component for confirmations/dialogs (`AppModal`), including delete/share/visibility/leave-flow prompts.
- Do not use browser-native `window.confirm` or `alert` for product dialogs.
- Note Detail edit rules:
  - `DRAFT`: Edit routes to full editor (content + OCR)
  - `STUDY_PACK_READY`: Edit stays on Note Detail and allows only title/subject/tags
  - While inline metadata edit is active, hide/disable share/visibility/learning actions.
- Share flow for private notes:
  - click Share -> show private-note modal
  - confirm -> make note public
  - then open share-link modal with copy action

## Verification and Access Gating

- Users can sign up/log in before email verification.
- Unverified users must not generate Study Packs.
- Unverified users must not use OCR upload.
- Verification-gated API responses should use structured `403` with:
  - `code=EMAIL_VERIFICATION_REQUIRED`
  - `action=RESEND_VERIFICATION`
- Frontend should present a friendly message for OCR gating:
  - `Verify your email before using OCR upload.`

## OCR Flow (Create/Edit Note)

OCR is optional and attached to Note authoring (`New Note` / edit note).
Create/Edit Note uses one unified import pipeline for images and supported files.

Required behavior:

- User uploads note image.
- OCR extracts text.
- Extracted text is inserted/merged into Note `content`.
- User reviews and edits OCR text directly in the main `Content` field before save/generate.
- Do not add a second OCR-only review textarea in Create/Edit Note.
- If OCR confidence is low, show an inline warning near `Content` instead of a separate confirmation editor.
- OCR upload does not auto-save and does not auto-generate.
- Uploaded images are not stored permanently.
- Note import/extraction is backend-owned; frontend should not be the source of truth for OCR/PDF/DOCX parsing.
- OCR usage must be protected by backend-configured billing-period limits plus per-minute rate limiting.
- If OCR quota is exhausted, return:
  - `You have reached your OCR limit for now. Please try again later or upgrade to Premium.`
- If OCR request rate limit is exceeded, return:
  - `Too many requests. Please wait a moment and try again.`

## File Import Flow (Create/Edit Note)

File import is part of Note authoring and must populate the main `Content` field before any save or generation action.

Required behavior:

- Support `.txt`, `.pdf`, and `.docx` import in Create/Edit Note.
- Use the same unified upload entry point as image OCR.
- Imported text is inserted/merged into Note `content`.
- Users review and edit imported text directly in the main `Content` field.
- File import does not auto-save and does not auto-generate.
- Text-based PDFs are supported in this flow.
- If a PDF has no embedded text, use OCR fallback before treating it as unreadable.
- If a PDF has no extractable text, show a friendly scanned-PDF message and direct users to image OCR instead.
- File imports must enforce backend-configured size/type/text-length limits before content reaches Note `content`.
- If extracted import text exceeds the configured maximum, return:
  - `This file is too large to process. Please upload a smaller file.`



## User Access Model

NoteLib uses a hybrid verification model.

Unverified users CAN:
- Create notes
- Edit draft notes
- Copy notes
- Browse Public Library

Unverified users CANNOT:
- Generate Study Pack
- Use OCR
- Take Challenge Quiz
- Use Adaptive Practice
- Make notes public
- Purchase Premium
- Use any LLM-powered feature

Verified users:
- Have full access based on plan (Free or Premium)

This gating must be enforced both in frontend and backend.

## User State Routing

User states:

1. ANONYMOUS
2. UNVERIFIED
3. VERIFIED

Routing rules:

ANONYMOUS:
- Landing
- Public Library

UNVERIFIED:
- App shell
- Show verification banner
- Allow note creation and copying only

VERIFIED:
- App shell
- Dashboard as primary landing
- Full app access based on plan

Auth routing rules:

- After successful login, the frontend must navigate with `router.replace(...)` to the resolved authenticated home route.
- Do not rely on app-shell visibility to imply navigation away from `/auth` or `/login`.
- Auth pages (`/auth`, `/login`, `/signup`) must redirect authenticated users immediately.
- The authenticated app shell must not render on auth routes.
- Expired-session recovery must clear stale auth state before redirecting to login so re-login behaves like a fresh successful auth flow.

## MVP Scope (Do Not Expand Without Request)

In scope:

- Note creation/editing
- Study Pack generation from notes
- OCR-assisted note input
- My Library/Public Library flows
- Quick Review, Challenge Quiz, Adaptive Practice
- Share/copy flows
- Plan and billing usage display

Out of scope unless requested:

- flashcards/spaced repetition
- heavy analytics dashboards
- teacher/classroom tooling
- gamification-heavy systems

## Frontend Conventions (`/frontend`)

Stack:

- Next.js App Router
- TypeScript
- Tailwind CSS
- shadcn/ui
- lucide-react

Rules:

1. Keep pages thin; put logic in `lib/`, hooks, and focused components.
2. Route backend calls through `frontend/lib/api.ts`.
3. Always implement loading and error states.
4. Use theme tokens (`bg-background`, `text-foreground`, etc.).
5. Keep Note Detail unified; do not split Note vs Study Pack detail pages again.

## Backend Conventions (`/backend`)

Rules:

1. Keep controllers thin; business logic in services.
2. Keep generation orchestration in Study Pack service flow:
   validate -> OCR (if image) -> normalize -> LLM -> validate output -> persist.
3. Enforce server-side limits for text/image inputs and quotas.
4. Do not log raw images or full OCR text.
5. Persist only validated generated output.
6. Keep ownership checks note-centric.

## Cost and Quotas

- Free: 10 Study Packs/month
- Free Challenge Quiz: 5/month
- Free OCR: backend-configured monthly quota
- Premium: 100 Study Packs/month
- Premium Challenge Quiz: 50/month
- Premium Adaptive Practice: 30/month
- Premium OCR: backend-configured monthly quota
- Adaptive Practice is Premium-only and still quota-limited.
- Difficulty Selection is Premium-only and feature-gated.
- Weak concepts remain visible to Free users.
- File upload is available on both Free and Premium.
- Study Pack, Challenge Quiz, and Adaptive Practice quotas are separate from each other.
- OCR usage has its own backend-configured monthly quota by plan.
- Frontend plan limits and feature availability must come from `GET /api/me/plan`, not hardcoded values.
- Settings usage UI should not show OCR counters; OCR remains backend-tracked and enforced.
- OCR limit UX in note import should use a modal:
  - Free: explain OCR is limited and offer `Upgrade to Premium`
  - Premium: explain reset happens on the next billing date
- Expensive OCR and AI generation endpoints must also enforce backend request-rate limits and return `429` with a friendly retry message.

## Billing Provider (Current)

- Active billing provider is `PAYMONGO` (provider-agnostic billing interface remains in place).
- Premium recurring plans are selected by backend from region pricing config, not from frontend assumptions.
- Regional pricing is resolved from `CF-IPCountry` and mapped into pricing regions.
- Region pricing config contains localized currency/amounts plus standard and optional intro PayMongo plan IDs.
- Voucher/promotion rules decide whether checkout should use an intro plan ID or a standard plan ID.
- Intro/first-time subscriber discounts must flow through voucher eligibility and voucher redemption records.
- Premium launch is currently gated by the waitlist flow:
  - upgrade CTAs join `premium_waitlist`
  - admin dashboard tracks waitlist count
  - checkout plumbing remains provider-ready but is not the active user-facing path
- Webhook lifecycle is the source of truth for subscription state:
  - `subscription.activated`
  - `subscription.invoice.paid`
  - `subscription.invoice.payment_failed`
  - `subscription.past_due`
  - `subscription.unpaid`
  - `subscription.updated`
- Do not create/update webhook registrations dynamically in app code.
- Controllers must stay provider-agnostic; provider services map external events to `SubscriptionService` and `PaymentTransactionService`.
- Webhook processing safety:
  - store provider webhook events in `webhook_events` with unique `(provider, event_id)`
  - duplicate events must return success without reprocessing
  - keep provider transaction inserts idempotent via provider reference IDs
- Billing lifecycle safety jobs:
  - `SubscriptionExpiryJob` (daily): expire overdue active Premium subscriptions and downgrade to Free
  - `BillingUsageResetJob` (daily): ensure usage rows exist for the current billing period window

## Dashboard and Library Guardrails

- Dashboard is guidance-first and non-destructive.
- Keep delete actions out of Dashboard.
- Dashboard should personalize section order, CTA emphasis, and labels by `profileType` while reusing the same shared note, quiz, activity, and usage data.
- `STUDENT` dashboard should prioritize `Continue Studying`, weak concepts, recent notes, and quick review.
- `BOARD_EXAM` dashboard should prioritize challenge-quiz practice, weak areas, adaptive practice, exam countdown, and weekly activity.
- `TEACHER` dashboard should prioritize quiz creation, material upload, recent materials, and recently generated quizzes.
- Dashboard variants must not introduce separate entities or profile-specific tables; personalization is presentation only.
- Teacher CTA routes should stay explicit:
  - `Create Quiz` -> `/notes/new?mode=quiz`
  - `Paste Material` -> `/notes/new?source=paste`
  - `Upload Material` -> `/notes/new?source=upload`
  - normal `Add Material` -> `/notes/new`
- Post-generation default note-detail view should use query-driven presentation on the unified note route:
  - normal note flow -> `tab=summary`
  - board-exam flow -> `tab=quiz`
  - teacher quiz-focused entry modes -> `tab=quiz`
- Dashboard statistics and weak-concept insights must be computed from stored quiz sessions and activity logs only, never by LLM calls.
- `Focus Areas` should surface top weak concepts for all users, but Adaptive Practice CTA remains Premium-gated through the shared soft paywall for Free users.
- Keep destructive actions (delete) in Note Detail/My Library with explicit confirmation.

## Documentation Source of Truth

Primary docs:

- `README.md`
- `docs/product/SPEC.md`
- `docs/product/ROADMAP.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/DATA_MODEL.md`
- `docs/features/*`

If conflicts appear:

1. Follow current product docs under `docs/`.
2. Use `docs/legacy/` only for historical reference.

## Testing Rules

All new features must include unit tests.

When modifying existing behavior:
- Update existing tests if behavior changes
- Add new tests for new rules

Critical business rules that must always have tests:
- Note state (Draft vs Study Pack)
- Copy note behavior and attribution
- Email verification gating
- Study Pack credit usage
- Public visibility rules
- Quiz session rules (only one in-progress session)
- OCR limits and verification gating

A feature is not complete unless:
- Code compiles
- Tests pass
- New behavior has test coverage
