# NoteLib Product Specification

Rebrand note: StudySnap has been rebranded to NoteLib. Database schema/table names remain unchanged unless explicitly requested.

## Product Overview

NoteLib helps students, board exam reviewees, and teachers turn notes into summaries, key concepts, and quizzes so they can study and prepare for exams faster.

The goal is to support active recall and repeated practice through a calm, iterative learning workflow built around summaries, key concepts, quiz practice, weak concepts, and adaptive review.

## Core Concept

Note-first model:

- Note is the main entity.
- Study Pack is the AI-generated enhancement of a Note.
- Users first save Notes, then generate Study Packs from those Notes.

Note states:

- `Draft` (no AI-generated content yet)
- `Study Pack Ready` (AI-generated content exists)
- visibility: `PRIVATE` or `PUBLIC`

Generated Study Pack outputs include:

- AI-generated title (optional)
- subject (optional)
- tags (optional)
- summary
- key concepts
- practice quiz
- Challenge Quiz
- Adaptive Practice

## Versioning Model (Copy)

NoteLib does not overwrite existing generated content.

Users make a copy of a Note, edit that copy, and generate a new Study Pack from the copied Note.

Copy behavior:

- Copy includes user-authored fields:
  - title
  - subject
  - tags
  - note content
- Copy does not include AI/generated history fields:
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
6. If the user wants to improve the note, they make a copy, edit it, and generate a new Study Pack from the copy.
7. If the note should be shared broadly, user sets visibility to `PUBLIC` and it appears in Public Library.
8. Public notes can be copied into Library as new Draft notes.

## Architecture Overview

High-level model:

- `notes` table stores user-authored fields (`title`, `subject`, `content`, `tags`).
- `notes.visibility` controls whether notes are private or listed in Public Library.
- Generated fields are stored and linked to the same Note (`summary`, `key concepts`, `quizzes`).
- Quiz sessions and performance are linked by `noteId`.
- Copy creates a new Draft Note row with copied user-authored fields only.

## Profile Types

Supported profile types:

- `STUDENT`
- `BOARD_EXAM`
- `TEACHER`

Profile type is a personalization setting on `User`.

Profile type affects:

- Dashboard layout and section priority
- CTA behavior
- Labels and wording
- Workflow emphasis
- Recommendations
- Default tab after generation

Profile type does not affect:

- Note ownership
- Study Pack generation pipeline
- Quiz-session persistence
- Activity history
- Weak-concept storage
- Core table structure

## Shared Learning Engine

All users share the same learning engine:

`Note -> Study Pack -> Quiz -> Activity -> Weak Concepts`

Do not create separate note, study-pack, quiz, or activity systems per profile type.

## Product Philosophy

Learning loop:

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

NoteLib is designed to help users iteratively improve understanding, not just generate summaries once.

---

## Key Features

### Public Landing Page

Route: `/`

Required sections:

- hero
- how-it-works
- who-it's-for
- feature highlights
- pricing teaser
- final CTA

CTA behavior:

- primary CTA: account creation
- secondary CTA: demo exploration (`/demo`)

Hero positioning:

- headline: `Turn Notes Into Quizzes`
- subheadline: `Study Smarter. Not Harder.`
- supporting text should focus on summaries, key concepts, and practice quizzes rather than AI tooling

### Study Pack Generation

- Input modes: pasted notes text or uploaded image notes (OCR)
- Output: title, summary, key concepts, quiz questions, metadata (`subject`, `tags`)
- OCR upload is part of Note authoring (Create/Edit Note) and populates Note `content` for manual review.
- OCR upload does not auto-save and does not auto-generate.
- Note Editor should keep `Generate` as the primary action:
  - desktop: sticky top actions plus repeated bottom actions
  - mobile: fixed floating primary generate CTA
  - `Save` stays secondary
- Generate button copy should stay short and may vary by `profileType` without changing backend generation:
  - `STUDENT` -> `Generate`
  - `BOARD_EXAM` -> `Practice`
  - `TEACHER` -> `Create Quiz`
- The longer explanation belongs in helper text below the primary generate button:
  - `STUDENT` -> `Creates summary, key concepts, and quiz.`
  - `BOARD_EXAM` -> `Generates a new quiz from your material.`
  - `TEACHER` -> `Generates quiz questions from your material.`
- Default behavior after generation stays on the same unified note route:
  - `STUDENT` -> open `tab=summary`
  - `BOARD_EXAM` -> open `tab=quiz`
  - `TEACHER` -> open `tab=quiz`
- Entry modes reuse the same note pipeline:
  - `/notes/new` -> normal note creation
  - `/notes/new?mode=quiz` -> quiz-first flow
  - `/notes/new?source=paste` -> paste-material flow
  - `/notes/new?source=upload` -> upload-material flow
- Demo mode must not call real generation pipeline, persist data, or consume usage
- Unverified users are blocked from generation with structured `403`:
  - `code=EMAIL_VERIFICATION_REQUIRED`
  - `action=RESEND_VERIFICATION`
- Unverified users are also blocked from OCR upload in Create/Edit Note.

### Library

Library is the user's private workspace for managing and revisiting their own notes (Draft and Study Pack Ready).

Users can:

- view their saved notes
- search by title/tags/content preview
- filter by subject (single select, `All subjects` default)
- filter by tags (multi-select OR matching)
- combine search + subject + tag filters on the loaded note list
- sort by recent/title/recently reviewed
- open by clicking card/title
- start Quick Review for Study Pack Ready notes
- manage note visibility (`Make Public` / `Make Private`)
- make a copy (`Make a Copy`) to create a new Draft version
- create a new note directly from the Library header
- empty-state actions should include:
  - `Create Your First Note`
  - `Try Demo`

### Public Library

Public Library lists notes where `visibility=PUBLIC`, including the current user's own public notes.

Users can:

- browse public notes
- filter by search, subject, and tags
- open read-only public note detail
- copy a public note into Library (`Copy to Library`)
- see source badges on cards:
  - `By You` for their own public notes
  - `By NoteLib` plus an `Official` badge for the official NoteLib account
  - `By {displayName}` for other users' public notes
- Public Library author labels are viewer-relative:
  - if `note.ownerId == currentUser.id` -> `By You`
  - else if the note author is the official NoteLib account -> `By NoteLib` with `Official`
  - else -> `By {displayName}`
- Public Library author labels should link to `/public/profile/{userId}`.
- Public note detail should change the primary action by ownership:
  - owner -> `Open Note`
  - non-owner -> `Make a Copy`
- Public note detail header should show `Subject • Author` using the same viewer-relative author label.
- Public note detail author label should link to `/public/profile/{userId}`.
- Public subject pages should reuse the existing `/public/library/{subject}` route and show:
  - subject heading
  - descriptive subtitle
  - list of public notes for that subject
  - empty state when a known subject has no notes
- Subject pages should reuse the same Subject badge styling as Public Library cards and note detail headers.
- Library, Public Library, Public Profile, and public subject listing cards should share the same note-card content stack:
  - Subject badge
  - Copy count when available
  - Title
  - `Note Preview` from note content
  - `Summary Preview` from generated Study Pack summary
  - Tags
- Note-card previews should clamp to 2-3 lines so cards stay visually consistent in a grid.
- If a note has no generated summary yet, cards should show `No summary available yet.`
- Public note detail is read/copy/share only and should not show edit, delete, or study actions.
- Public Profiles should use `/public/profile/{userId}` in V1 and show:
  - Display Name
  - Profile Type
  - Official badge when the account is official/admin
  - Total public notes
  - Total copies across that user's public notes
  - list of that user's public notes with Title, Subject badge, Tags, and Copy count
- Public Profiles must only include notes where `visibility=PUBLIC`.
- Public Profiles must never show email.
- Public Profile owner controls belong on `/public/profile/{userId}`, not on `/profile`.
- Owner-only Public Profile controls are:
  - `Edit Profile` -> routes to `/profile`
  - `Share Profile`
  - `Public Profile On` / `Public Profile Off`
- Public Profile owner controls should follow the Note Detail header pattern:
  - visibility is shown as a badge/dropdown near the title cluster
  - stats remain in their own section below the identity summary
  - `Share Profile` sits in the lower-right action row of the header card
- Non-owners must not see `Edit Profile` or the visibility toggle on Public Profile.
- If `publicProfileVisible = false`, non-owners should see `This profile is private.`
- If the user has no public notes, show `This user has no public notes yet.`
- Public Profile note cards should use the same interaction model as Library and Public Library:
  - whole card click opens the public note detail page
  - do not add a redundant `Open Note` button inside the card
- Public Profile note cards should use the shared note-card preview layout, including both `Note Preview` and `Summary Preview`.
- Public Profile remains a public showcase page and should not mix in private workspace actions, even for the owner.
- Owner actions on public note detail may include:
  - `Open Note`
  - `Share`
- Non-owner actions on public note detail may include:
  - `Make a Copy`
  - `Share`
- Private Note Detail owns:
  - `Edit`
  - `Delete`
  - `Generate Study Pack`
- Study surfaces own:
  - `Quick Review`
  - `Challenge Quiz`
  - `Adaptive Practice`
- Action-button behavior should stay consistent across Dashboard, Library, Public Library, Note Detail, Public Note Detail, Profile, Public Profile, Settings, and Admin surfaces:
  - desktop buttons show icon + text
  - mobile buttons default to icon-only and must keep an `aria-label`
  - primary CTAs may keep text on mobile when icon-only would reduce clarity
  - visibility controls use badge/dropdown presentation, not large standalone buttons
  - header actions sit top-right and card actions sit bottom-right when present
- Dark-mode outline button styling should remain readable across secondary actions such as `Challenge Quiz`, `Adaptive Practice`, `Make a Copy`, `Share`, and `Edit`:
  - use a lighter dark-mode border than the surrounding card background
  - use near-white text in dark mode
  - keep a visible dark-mode hover fill
  - do not reuse light-mode border contrast assumptions in dark mode
- Navigation icon mapping should stay fixed across desktop and mobile sidebars:
  - `Dashboard` -> `Home`
  - `Library` -> `Book`
  - `Public Library` -> `Globe`
  - `Profile` -> `User`
  - `Settings` -> `Gear`
  - `Admin` -> `Shield`
- Standard action icon mapping should remain consistent:
  - `Edit` -> `Pencil`
  - `Delete` -> `Trash`
  - `Share` -> `Share` / `Link`
  - `Copy` -> `Duplicate`
  - `Open` / `View` -> open / external arrow icon
  - `Public` -> `Globe`
  - `Private` -> `Lock`
  - `Library` -> `Book`
  - `Dashboard` -> `Home`
  - `Profile` -> `User`
  - `Settings` -> `Gear`
  - `Admin` -> `Shield`
  - `Back` -> `Arrow Left`
  - `Save` -> `Disk`
  - `Quick Review` -> `Lightning`
  - `Challenge Quiz` -> `Trophy` / challenge icon
  - `Adaptive Practice` -> `Target` / focused-practice icon
  - `Study Pack` -> `Sparkles` / book icon
- Use outline-style icons only and do not mix filled and outline icon sets on the same product surface.
- Do not use emoji as icons in product UI.
- Quiz entry buttons on Note Detail and other quiz launch surfaces should use distinct icons so users can distinguish fast review, exam challenge, and weak-area practice at a glance.
- `Summary` and `Quiz` on Private Note Detail are view tabs, not action buttons:
  - place the tab row below `Note Content` and above Study Pack content
  - use underline-style navigation with muted inactive labels and an active bottom border
  - desktop tabs show icon + text and mobile tabs show icon only with an accessible label
  - tab switches should stay on the same note view and update content without a full page reload

### Display Name And Official Badge

- `users.display_name` is the public author name field.
- `Profile -> Identity` owns:
  - `firstName`
  - `lastName`
  - `displayName`
  - `email`
- `Profile` should include:
  - a top Display Name card with avatar, display name, email, and right-aligned `View Public Page ->` navigation
  - an `Identity` card with `firstName`, `lastName`, `displayName`, and `email`
  - a `Profile Type` card with the profile-type selector
- `Profile` save actions should be section-specific:
  - `Save Identity` only saves identity fields
  - `Save Profile Type` only saves the profile type field
- `View Public Page ->` on `/profile` is navigation only and should live in the top Display Name card.
- `Profile` should not own Public Profile sharing or visibility controls.
- If `displayName` is blank, public author fallback is `firstName`.
- Public pages must never show the user's email address.
- Reserved display names are rejected server-side. The following are reserved case-insensitively:
  - `notelib`
  - `admin`
  - `support`
  - `official`
  - `moderator`
  - `staff`
  - `team`
- Any display name containing `notelib` is also rejected.
- Validation message: `This display name is reserved. Please choose another name.`
- Official/public-profile badge state is backend-driven from the configured official email plus eligible admin accounts.
- The official NoteLib account renders as:
  - author label -> `By NoteLib`
  - badge -> `Official`
- Subject display should stay consistent across Library, Public Library, Private Note Detail, and Public Note Detail:
  - render subject as a reusable badge, not `Subject: ...` text
  - place subject on the same line as the author label on note headers
- Subject persistence and suggestions:
  - `notes.subject` remains the persisted source of truth
  - subject suggestions come from `GET /api/subjects` using distinct existing note subject values
  - Library and Note Editor use the authenticated `mine` subject scope
  - Public Library uses the `public` subject scope
  - users can still type a custom subject and save it directly into `notes.subject`
  - no normalized `subjects` table is required for the current version

Dashboard guidance rules:

- Dashboard is non-destructive and guidance-first.
- Deletion is not available from Dashboard.
- Dashboard is personalized by `profileType` presentation only; it must not create separate note, study-pack, quiz, or activity systems.
- `STUDENT` dashboard should prioritize:
  - `Continue Studying`
  - `Weak Concepts`
  - `Recent Notes`
  - `Quick Review`
  - `Usage / Progress`
  - main CTA -> `Continue Studying`
- `BOARD_EXAM` dashboard should prioritize:
  - `Exam Countdown` when `examDate` exists
  - `Practice Challenge Quiz`
  - `Weak Areas`
  - `Adaptive Practice`
  - `Study Activity This Week`
  - `Usage / Progress`
  - main CTA -> `Practice Challenge Quiz`
- `TEACHER` dashboard should prioritize:
  - `Create Quiz`
  - `Upload / Paste Material`
  - `Recent Materials`
  - `Recently Generated Quizzes`
  - `Activity`
  - `Usage`
  - main CTA -> `Create Quiz`
- Note entry modes may change the initial editor focus and post-generation destination without changing the underlying note pipeline:
  - `/notes/new?mode=quiz` focuses quiz creation and should open note detail with `tab=quiz`
  - `/notes/new?source=paste` focuses pasted material entry and should open note detail with `tab=quiz`
  - `/notes/new?source=upload` focuses the upload panel and should open note detail with `tab=quiz`
  - `/notes/new` remains the normal note-creation flow and should open note detail with `tab=summary`
- Dashboard performance and weak-concept insights must be computed from existing quiz session data only.
- Dashboard must not use LLM calls for statistics or recommendations.
- `Focus Areas` should show the top weak concepts and route Premium users to Adaptive Practice through `noteId`.
- Free users should see the same weak concepts but hit the soft Premium paywall when trying to start Adaptive Practice from Dashboard.
- Board Exam CTA wording may use exam-prep labels such as `Practice Challenge Quiz` and `Weak Areas`, but must still use the same shared note, quiz-session, activity, and usage data.
- Teacher CTA wording may use material and quiz-generation labels, but the underlying workflow remains `Note -> Study Pack -> Quiz`.
- Post-generation note detail should stay on the same unified note route and use `tab=summary` or `tab=quiz` to choose the initial study view rather than creating separate note-detail pages.
- Dashboard monthly usage should show:
  - Study Packs
  - Challenge Quiz
  - Adaptive Practice for Premium only
- OCR usage must stay hidden from the dashboard UI.

### Shareable Study Packs

- Public share links use `/p/{token}`
- Shared pages are read-only and auth-aware
- Share page can show title, summary, key concepts, and quiz preview
- Remix/copy duplicates into current user library and must not call LLM
- Duplicate title resolution:
  - `{Title}`
  - `{Title} (Copy)`
  - `{Title} (Copy 2)`, `{Title} (Copy 3)`, ...
- Success feedback: `Study Pack copied to your library.`

### Navigation

Sidebar groups:

- Main: Dashboard, Library, Public Library
- Account: Profile, Settings

Primary routes:

- `/library` (Library, private workspace)
- `/library/public` (Public Library)
- `/notes/{id}` (Note Detail)
- `/public/library/{subject}` (Public Subject Listing, SEO)
- `/public/library/{subject}/{slug}` (Public Note Detail, read-only, SEO)
- `/public/profile/{userId}` (Public Profile, public/non-canonical V1 route)

Page responsibilities:

- Dashboard = what to do now
- Library = private workspace
- Public Library = discovery
- Public Profile = public showcase
- Profile = identity
- Settings = app preferences

### Quick Review

- Primary quiz mode for a Study Pack-ready Note
- Immediate correctness feedback (`green = correct`, `red = incorrect`)
- Retry incorrect questions once
- Optional confidence feedback (`HIGH`, `MEDIUM`, `LOW`)
- Session history persists for progress tracking

### Challenge Quiz (Premium)

- Timed exam-style mode (10 minutes)
- Generated from Study Pack summary + key concepts only
- Difficulty and question count adapt by latest Quick Review score:
  - `<50`: 10 questions, easy-medium
  - `<80`: 12 questions, medium
  - `>=80`: 15 questions, medium-hard
- Reuse existing in-progress session to avoid duplicate LLM calls
- Persist in-progress state (answers, index, timer basis)
- Usage limit: 50/month (separate from Study Pack generation quota)

### Adaptive Practice (Premium)

- Generated from Study Pack summary + key concepts + weak concepts only
- Question count by weak-concept volume:
  - `<=2`: 5
  - `<=4`: 7
  - `>=5`: 10
- Reuse existing in-progress session to avoid duplicate LLM calls
- Usage limit: 50/month (separate from Study Pack generation quota)

## Plan Usage Display

- `Settings -> Plan & Billing` shows billing-cycle usage bars instead of raw counters.
- Free users see:
  - `Study Packs`
  - `Challenge Quiz`
- Premium users also see:
  - `Adaptive Practice`
- OCR usage is tracked in backend but hidden from the Settings UI.
- Usage bars use warning colors:
  - `0-60%` normal
  - `60-85%` warning
  - `85-100%` danger
- Usage reset dates are based on the billing cycle, not the calendar month.
- When a Free user hits a visible limit, Settings should show an `Upgrade to Premium` CTA.

### Authentication Session Handling

- Protected routes require auth
- `401` on protected API calls clears auth and redirects to `/login`
- Preserve destination with `redirect` query param
- Session-expired redirects include `reason=session_expired`
- After a successful login, the frontend must route with `router.replace(...)` to the resolved authenticated home instead of relying on shell visibility alone.
- Verified users who log in successfully should land on `Dashboard`.
- Auth pages (`/auth`, `/login`, `/signup`) must immediately redirect authenticated users away from the auth form.
- Auth pages must not remain visible once authentication succeeds.
- Users can sign up/login before verification; unverified users are blocked from generation
- Unverified users are also blocked from OCR upload
- Verification email delivery uses provider-agnostic `EmailService`
- Transactional email content uses file-based templates
- Retention emails use Resend-backed delivery with file-based templates and `email_log` cooldown tracking
- User-facing email templates should use first-name personalization when available and fall back to `Hi there,`
- User-facing email templates should share the standard footer:
  - `— NoteLib`
  - `Turn Notes Into Quizzes`
  - `https://notelib.app`
- Retention reminders include:
  - inactive-user reminder after `3` days without meaningful study activity
  - weak-concept reminder after `3` days without follow-up practice on weak Challenge Quiz concepts
  - weekly progress summary every Sunday at `6:00 PM`
- Session-expiry recovery must clear stale local auth state before redirecting to login so a re-login behaves like a fresh auth success.
- First-study product onboarding is separate from preferences onboarding and guides new users through:
  - verify email and see the first-study activation welcome screen
  - create note
  - generate Study Pack
  - start the first Challenge Quiz from the Study Pack success banner
  - review weak concepts after the first quiz result
  - return to Dashboard
- After email verification, first-time users with `studyPackCount == 0` should see a welcome CTA before landing on an empty dashboard:
  - `Create First Note`
  - `Go to Dashboard`
- Dashboard empty state for first-time users should be explicit:
  - title: `You don't have any Study Packs yet`
  - description: `Create a note and generate your first quiz in a few minutes.`
  - primary CTA: `Create Your First Note`
- After the first Study Pack is generated, Note Detail should show a success banner that points the user to `Start Challenge Quiz`.
- After the first Challenge Quiz is completed, the result screen should show a weak-concepts guidance banner with `View Weak Concepts`.

### Preferences Onboarding

Route: `/onboarding`

Preferences onboarding is reused and extended rather than duplicated.

Current onboarding order:

1. `Profile Type`
2. `Learning Style`
3. `Study Reminder Frequency`
4. `Exam Date` only when `profileType = BOARD_EXAM`
5. finish and redirect to `Dashboard`

Profile Type options:

- `STUDENT`
- `BOARD_EXAM`
- `TEACHER`

Rules:

- `Learning Style` and `Study Reminder Frequency` remain the existing onboarding steps
- `Exam Date` is conditional and must be skipped for `STUDENT` and `TEACHER`
- onboarding state is loaded from `GET /auth/me`
- onboarding completion is persisted through the existing onboarding completion flow
- users who already completed onboarding should be redirected to `Dashboard`

### Profile

Route: `/profile`

`Profile` owns identity and account-oriented fields only.

Identity section:

- `firstName`
- `lastName`
- `email`
- `Save Identity`

Profile section:

- `profileType`
- separate `Save Profile Type` action

Account information section:

- `Member Since`
- `Plan`
- `Study Packs Created`

`Profile` must not include:

- `Learning Style`
- `Study Reminder Frequency`
- other app-behavior preferences that belong in Settings

Email change flow:

- saving identity updates `firstName` and `lastName` immediately
- if `email` changes, NoteLib stores the new value in `pendingEmail`
- verification is sent to `pendingEmail`
- the UI should tell the user: `Please verify your new email address before it replaces your current email.`
- after verification, `email = pendingEmail`, `pendingEmail = null`, and `emailVerifiedAt` is refreshed
- email changes must never replace the active account email before verification

### Email Templates

Current user-facing template set:

- verification email
- welcome email
- inactivity reminder
- weak concept reminder
- weekly summary
- premium waitlist confirmation

Welcome email requirements:

- position NoteLib as `Turn Notes Into Quizzes`
- Free plan includes:
  - `10` Study Packs per month
  - Quick Review
  - Challenge Quiz with a monthly limit
  - Public Library access
- Premium includes:
  - Adaptive Practice
  - Weak Concept Training
  - Difficulty Selection
  - Higher monthly limits
- welcome copy must not say Challenge Quiz is Premium-only

### Settings Preferences

Route: `/settings`

`Settings > Preferences` remains the only place for:

- `Learning Style`
- `Study Reminders`
- future behavior and reminder preferences

### Plan and Billing

Settings route section: `Plan & Billing`

- show plan (`FREE` or `PREMIUM`)
- support Premium billing cycle selection:
  - `MONTHLY`
  - `YEARLY`
- show usage buckets separately:
  - Study Packs (monthly quota)
  - Challenge Quiz (plan-based monthly quota)
  - Adaptive Practice (Premium-only, plan-based monthly quota)
  - OCR (tracked internally, hidden from user-facing usage UI)
- PayMongo recurring subscription checkout for upgrade
- Billing webhook sync keeps plan state aligned (webhook-driven source of truth)
  - `subscription.activated`
  - `subscription.invoice.paid`
  - `subscription.invoice.payment_failed`
  - `subscription.past_due`
  - `subscription.unpaid`
  - `subscription.updated`
- Premium-gated upgrade prompts should link to `/settings#plan-billing`

Plan limits:

- Free: unlimited notes, 10 Study Packs/month, 5 Challenge Quizzes/month, OCR quota, file uploads, weak concept visibility
- Premium: 100 Study Packs/month, 50 Challenge Quizzes/month, 30 Adaptive Practice sessions/month, higher OCR quota, difficulty selection, priority AI
- Usage windows are billing-cycle-based:
  - Free resets monthly from account creation date
  - Premium resets from the active subscription billing window

---

## Activity Tracking

Track lightweight events such as:

- `CREATED_STUDY_PACK`
- `STARTED_QUICK_REVIEW`
- `COMPLETED_QUICK_REVIEW`
- `COMPLETED_ADAPTIVE_QUIZ`

Events are linked to user + note context and timestamp.

Canonical ownership rule:

- Generated summaries, key concepts, quiz content, and all practice sessions are note-scoped (`noteId`).
- Any legacy `studyPackId` fields are compatibility fields only.

---

## Non-Goals (Current Scope)

Not included unless explicitly requested:

- spaced repetition scheduling
- full exam simulation grading engine
- heavy analytics dashboards
- classroom/teacher management
- collaborative/family linking features
