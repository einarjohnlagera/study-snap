# AGENTS.md - NoteLib

You are an AI coding agent helping implement NoteLib.
Follow these rules to keep the codebase consistent and shippable.

Rebrand note: StudySnap has been renamed to NoteLib. Keep existing database schema/table names unless explicitly requested.

Current documentation baseline:

- `v0.68.0 - Topic Note Rename` (In Progress); previous: `v0.67.1 - Explore Convergence Follow-ups` (Released)

When working on a feature, always check the corresponding document under `docs/features/`.

Active release guardrail:

- v0.29.1 consciously allows one narrow relaxation of the v0.29.0 no batch/progress infrastructure rule: a single terminal-outcome `bulk_generation_result` receipt for bulk generation, written once at batch completion, read once by the owner, then deleted or expired after 24h. This receipt may carry requested/created counts, generation-failed topic strings, quota-blocked topic strings, and retry context. It is not a batch-job entity, live progress table, per-item status row, or new status enum; the broader no batch/progress infrastructure rule still applies everywhere else.
- Bulk generation is available to authenticated, onboarded users in v0.29.1. Non-admin users must stay on the existing quota-enforcing path; ADMIN bypasses bulk note-generation and Study Pack quota inside the bulk orchestration only.

## Product Summary

NoteLib converts notes into structured study outputs and review workflows.

Core loop:

`Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat`

Teacher flow rule:

- Do not reuse student quiz session logic for teacher preview.
- Teacher flow uses `generatedQuiz` only.

## AI Skills System

Reusable workflow patterns for AI-assisted development are documented in `docs/skills/`.

- `docs/skills/README.md` — philosophy, Claude vs Codex guidance, model/effort recommendations
- `docs/skills/codex-prompt-generator.md` — how to write a structured Codex implementation prompt
- `docs/skills/ux-product-review.md` — NoteLib UX philosophy and review categories
- `docs/skills/release-doc-alignment.md` — checklist for keeping docs aligned after feature work
- `docs/skills/roadmap-feature-audit.md` — how to classify and scope new work before starting

Use these skills before writing prompts, before starting new features, and after shipping work.

## Implementation Workflow Rules

- After every completed prompt/task that results in code or doc changes, always include a suggested commit message in the final response.
- Format the suggested commit message as a copy-friendly plain-text block:
  - first line: `type: concise subject`
  - following lines: flat `- ` bullets with 3-5 high-signal changes when useful
- Example:
  - `polish: refine Library filters with subject-first UX`
  - `- replace wrapped tag chips with a compact popular-tags rail`
  - `- keep subjects in a single horizontal scroll lane above the note grid`
  - `- add + More tag selection via shared mobile sheet / desktop modal`
  - `- preserve real-time title-and-tag search plus existing note navigation`
  - `- update Library docs and release notes for progressive tag disclosure`

## Backend Code Quality Rules

- Avoid hardcoding domain-significant string values in implementation code.
- When a string value is used for codes, messages, metadata keys, session keys, analytics names, action labels, query params, or other logic-bearing behavior, promote it to a constant where it belongs.
- Prefer `private static final` constants inside the owning class when the value is local to that class.
- If the same value is shared across multiple classes, move it to an appropriate shared constants/helper type instead of duplicating the literal.
- Reuse existing constants before introducing new ones.
- If helper logic is generic enough to be reused across methods/classes, move it into an existing utility class or create a new utility class in the appropriate package.
- Reuse existing utility classes before creating new ones, and do not create duplicate utility types with overlapping responsibilities.
- Reuse existing exception classes before creating new ones.
- When throwing application-level exceptions, prefer a dedicated exception type that extends `AppException` instead of scattering inline `new AppException(...)` calls.
- If no suitable exception type exists yet, create a new exception class that extends `AppException` and keep its code/status/message ownership there.
- New exception classes should stay close to the domain they represent and should not duplicate an existing `AppException` subclass with the same meaning.

### Sonar / Code Smell Rules (Backend)

- **`assertThatThrownBy` — one invocation only (S5778)**: the lambda passed to `assertThatThrownBy` must contain exactly one method invocation — the call expected to throw. Move all setup and preceding calls outside the lambda. Wrong: `assertThatThrownBy(() -> { setup(); service.call(); })`. Right: `setup(); assertThatThrownBy(() -> service.call())`. Apply this fix whenever modifying a test class that contains this violation.
- **Custom exceptions over raw `AppException`**: throw a named exception subclass (`NoteNotFoundException`, `StudyPackNotFoundException`, etc.) rather than `new AppException(ErrorCode.SOMETHING, "message")` inline. Named exceptions own their code, status, and message — no repeated string literals at throw sites. If no matching subclass exists, create one before throwing.
- **String literal duplication**: a string literal that appears two or more times in the same class must be extracted to a `private static final String` constant in that class. If the same literal appears in multiple classes, move it to an appropriate shared constants class. Apply this fix whenever modifying a class that already has the duplication — do not leave the violation in place.
- **Use `Math.clamp` for range-clamping**: the project targets Java 21. Prefer `Math.clamp(value, min, max)` over `Math.max(min, Math.min(value, max))` — the clamp form is cleaner and Sonar S6877 flags the nested min/max pattern.

## Required Product Architecture (Current)

- Note is the primary entity.
- Study Pack is generated content attached to a Note.
- A Note has state:
  - `DRAFT`
  - `GENERATING`
  - `FAILED`
  - `STUDY_PACK_READY`
- A Note also has visibility:
  - `PRIVATE`
  - `PUBLIC`

### Versioning Rule

- Never auto-regenerate generated content.
- Regeneration is allowed only as an explicit user-confirmed action on an owned note.
- Regeneration updates the existing Study Pack in-place so quiz/session history stays linked to the same Study Pack id.
- Copy includes: `title`, `courseProgram`, `subject`, `tags`, `content`.
- Owner self-copy does not include: generated `summary`, `key concepts`, `quiz`, session history, or performance history.
- Public-note copy is the documented exception: when the public source has a Study Pack, the copy includes the linked Study Pack and arrives as `STUDY_PACK_READY`.
- Public-note copies without a linked Study Pack and owner self-copies remain new `DRAFT` notes.

### Paid Plan Cancellation Rule

- Paid-plan cancellation must be confirmed in Settings before submission.
- Cancellation is scheduled at the end of the current billing period, not immediate.
- Paid access remains active until that period ends.
- Downgrade to Free happens through subscription lifecycle logic at period end.
- Canceling a paid plan must not remove notes or generated Study Packs from the user library.
- Settings billing should show scheduled end-of-period cancellation clearly in the subscription summary and must not imply immediate loss of access.

### Paid Upgrade Prompt Rule

- Free users should see a soft paywall modal before any paid-plan quiz feature or Study Pack limit block attempts a paid conversion flow.
- All paywalls must be context-aware. Never use generic upgrade prompts when the blocked action is known.
- Premium exam paywalls for Long Exam, Board Exam Mode, and Interview Practice must fire from the Start CTA after the user can view the mode setup/prescreen, not from the mode-selection card click.
- Study Plan premium-exam launch must route with `collectionId`, resolve profile-to-mode through `resolvePlanPremiumExamMode`, and scope additional-note pickers to quiz-ready notes from that plan only.
- Shared paywalls must explain the specific blocked action, the upgrade value, and the strongest next plan path for that action.
- Verified users who choose to upgrade should start the hosted checkout flow via `POST /api/payments/create`.
- Frontend upgrade actions should redirect only to the backend-returned Xendit checkout URL.
- Paywall upgrade attempts must preserve a safe internal return path and resume the interrupted flow after successful payment.
- Note-creation paywalls must save the current note or preserve a local draft before redirecting to checkout.
- When a user has `2` or `1` Study Packs remaining, show a non-blocking monthly-limit banner on Dashboard, Note Detail, and Study Pack generation surfaces.
- When Study Pack remaining reaches `0`, keep `Generate Study Pack` enabled and show a student-friendly monthly-limit modal on click instead of disabling the action.
- Upgrade messaging should position Plus as the practical step-up for regular study and Pro as the exam-preparation and mastery tier.
- Dashboard should show a Free-only upgrade card highlighting Challenge Quiz, Adaptive Practice, Board Exam Mode, and the `100` Study Pack Pro limit.
- Pricing page should clearly compare Free vs Plus vs Pro with localized backend pricing and student-oriented value messaging.

### Study Pack Usage Rule

- Study Pack enforcement, warning banners, and remaining-credit UI must use the same backend-resolved usage calculation.
- Allow Study Pack generation only when `used < limit`; block when `used >= limit`.
- Study Pack usage increments only after a successful Study Pack is persisted.
- Saving a note, opening generation surfaces, failed generations, and failed retries must not consume Study Pack quota.
- Frontend warning/blocking surfaces should use `GET /api/me/plan` remaining values and must not recalculate quota from local note lists.

### Study Plan Readiness Rule

- Plan readiness is rendered in the canonical `/progress?collectionId={id}` frontend view, still backed by owner-scoped `GET /collections/{id}/readiness`.
- The endpoint must resolve the collection exactly like `NoteCollectionService.get(collectionId, userId)`: missing, malformed, public-source, or not-owned plans return `CollectionNotFoundException` / `404`.
- Plan readiness must reuse `ProgressReportService` ConceptHealth classification and `masteryPercentage`; do not invent thresholds, persist readiness fields, add generated content, or call AI/LLM.
- Quick Review must not write to `ConceptHealth` and must not move mastery, due-state, Note readiness, Plan readiness, or Overall Readiness. It is a refresh-only mechanic; its own retry/missed-concept feedback must come from session metadata, not ConceptHealth writes.
- Collection detail execution rows, collection list cards, published-plan cards, and public source plans must keep the no-mastery rule: no subject mastery percentages, milestones, goals, streaks, or weakest-subject routing there. **Second named exception (formalized v0.66.1, shipped since the original Goal → Subject hierarchy feature):** a Goal's own detail page may show each child Subject plan's `overallReadinessPercentage` and a readiness progress bar on that child's card, plus a `mastered · due · not started` concept-count line (the due segment may use a warning color when `dueConcepts > 0`, presence-based only, no magnitude threshold) — this is the Goal owner reviewing their own curriculum's readiness, not a list/browse surface. Collection list cards, published-plan cards, public source plans, and per-note execution rows are unaffected and keep the plain no-mastery rule.
- Frontend readiness displays should reuse the shared `ReadinessSummary` component and vocabulary: `ready`, `mastered`, `due`, `not started`.
- The `/progress?collectionId={id}` plan-scoped view fires `PLAN_READINESS_VIEWED` once per distinct plan selected in a session (keyed by `collectionId`, not a fire-once boolean — switching plans without a remount must fire again for the newly selected plan).

### Study Plan Hierarchy Rule

- Study Plan nesting is constrained to exactly two collection levels: top-level Goal collections may contain child Subject plans, and Subject plans may contain note items and label-derived sections.
- The only hierarchy storage is nullable `note_collections.parent_collection_id`; deleting a Goal must set child `parent_collection_id` to null rather than cascading child collections.
- Backend hierarchy logic must stay profile-neutral. Goal/Subject wording is frontend-only through `getCollectionLabels`; services and API contracts must not branch on `ProfileType`.
- Set/clear parent must be owner-scoped and enforce: parent exists and is owned by the caller, parent is top-level, child is not self, and child has no children.
- Goal readiness is derived from child readiness counts only: `round(100 × Σ child.masteredConcepts / Σ child.totalConcepts)`, or `0` when total is `0`. Do not re-run concept classification over merged Goal notes, persist readiness, add thresholds, or call AI.
- Deeper nesting, recursive Goal adoption, direct note items on Goals, and per-module readiness remain out of scope unless explicitly introduced by a future release rule.

### Note Readiness Signal Rule

- Private Note Detail may show a compact per-note readiness signal for owned notes with a ready Study Pack and key concepts.
- The note signal must reuse the shared `ReadinessSummary` component and the same readiness vocabulary as Plan Readiness and My Progress.
- The note readiness signal is available to Free users: `% ready`, `X/Y mastered`, due count, not-started count, and per-concept readiness status.
- Free users receive the minimum `lastCorrectAt` signal needed to render accurate `Due`, `Mastered`, and `Not started` statuses. Detailed review timing (`daysSinceReview`, `Due - Nd ago` copy), incorrect-answer history, and `Needs work` remain PLUS/PRO only.
- This visibility split must not change prices, quotas, pass durations, checkout behavior, Adaptive Practice access, generated content, AI calls, or persisted readiness fields.
- Concept-health load failures must not hide or wipe note content; show a neutral readiness-unavailable state instead.

### Note Target Audience Rule

- Target Audience is required on every note.
- Student profiles must not see the Target Audience field; backend saves `STUDENT`.
- Board Exam profiles must not see the Target Audience field; backend saves `BOARD_TAKER`.
- Professional profiles must not see the Target Audience field; backend saves `PROFESSIONAL`.
- Teacher and Admin profiles must see the Target Audience field in Create/Edit Note, with a required indicator and all audience values selectable.
- Do not make Target Audience optional or replace hidden profile-based defaulting with a visible picker for Student, Board Exam, or Professional profiles.

### Async Study Pack Generation Rule

- Note-owned Study Pack generation must save the note first, mark it `GENERATING`, and redirect the user to Note Detail immediately.
- Note Detail owns generation observation: show a clear `GENERATING` state, friendly loading copy, and light polling until `STUDY_PACK_READY` or `FAILED`.
- `FAILED` must keep note content safe, show a friendly recovery message, and expose `Retry Generate`.
- Retry generation must reuse the saved note content and must not consume Study Pack quota unless a Study Pack is successfully persisted.
- Create/Edit Note should not keep users blocked on the editor while the LLM request runs.

### Marketing Landing Page Rule

- The landing page must explain NoteLib in student terms: notes -> summaries -> quizzes -> review.
- Position NoteLib as a notes library and long-term study workspace first, and as an AI-powered generator second.
- The homepage should make it clear that users build a reusable library of notes before turning those notes into Study Packs for review.
- Public marketing navigation should expose:
  - `Home`
  - `Public Library`
  - `Learn`
  - `Pricing`
  - `Login`
  - `Get Started`
- Public navbar hierarchy must stay clear:
  - navigation links grouped together
  - theme toggle treated as a utility control, not a CTA
  - `Login` as the secondary action
  - `Get Started` as the primary action
- On mobile public nav, keep the theme toggle in the top-header utility cluster and keep the opened menu focused on navigation links plus `Login` and `Get Started`.
- Do not duplicate the theme toggle or primary CTA between the public header and the opened mobile menu.
- Keep the home page focused on hero, how-it-works, features, Free vs Plus vs Pro pricing, demo access, and signup CTA.
- Demo access must be available without signup.
- Public Library should be treated as a public discovery feature and must remain accessible without login.
- The landing page Public Library feature section should pair discovery copy with a framed screenshot preview using `public/landing/feature-public-library.jpg` in a responsive text-left / preview-right layout; keep the screenshot constrained so it supports the section instead of dominating it.
- Pricing shown on marketing surfaces must still come from backend-owned pricing APIs or shared pricing components.
- Landing page metadata should position NoteLib as a note-to-study-pack product, not a generic AI assistant.
- Landing page title, meta description, and Open Graph metadata must stay aligned with the notes-library-first positioning.
- Public marketing/auth surfaces should expose footer links to:
  - `Privacy Policy`
  - `Terms of Service`
  - `Contact`

### Branding Rule

- `notelib-logo-monogram.png` is the primary small-logo mark.
- Use the monogram for:
  - public navbar
  - authenticated app shell
  - mobile headers
  - favicon
  - apple-touch icon
- `notelib-logo-full-light.svg` and `notelib-logo-full-dark.svg` are the public/marketing wordmarks.
- Use the full logo for:
  - landing hero
  - public footer
  - Learn header
  - Pricing header
  - other public marketing headers
  - Open Graph branding
- `notelib-logo-icon.svg` is a product illustration only.
- Do not use the product icon as the navbar logo or favicon.
- Keep favicon and home-screen assets aligned to the NL monogram set.

### Legal Pages Rule

- `Privacy Policy` and `Terms of Service` must remain public and accessible without login.
- Public routes are:
  - `/privacy`
  - `/terms`
- Legal copy should stay simple, readable, and professional rather than highly styled.
- Contact email for launch/legal pages is `support@mail.notelib.app`.

### Onboarding Rule

- Onboarding is active again for all verified users, not only paid-plan users.
- Onboarding should happen once after email verification / first verified entry into the app.
- Onboarding must stay short and reuse the existing step flow.
- Current `/onboarding` flow order is:
  - `Profile Type`
  - `Study Goal`
  - `Input Method`
  - `Study Pack Generation`
  - `Completion`
- `Exam Date` is optional and shown inline on the Study Goal step for `BOARD_EXAM`.
- After `BOARD_EXAM` Step 2 submits, if the collected course/program's top published Official Review Set has `itemCount > 0` and `readyCount > 0`, replace Steps 3–4 with Confirm & Practice: adopt the existing set, persist onboarding completion from `Start this plan`, and land on the adopted Review Set's detail page (not directly inside a quiz — Today's Focus / Continue Studying is one tap away from there). This branch must not author notes, invoke AI generation, launch another quiz mode, or render Step 5. Lookup failures and zero-depth sets fail open to the unchanged create-first flow; `STUDENT`, `TEACHER`, and `PROFESSIONAL` always retain that flow unchanged.
- Onboarding persists `profileType`, optional `examDate`, and `onboardingCompletedAt`.
- Profile Type is required before creating or generating study content. Client guards are UX only; backend content-creating mutations (note create, note-from-topic, Study Pack generation, note copy, bulk generation, batch import) must enforce this server-side through `ProfileSetupRequiredException` (`ONBOARDING_REQUIRED`) rather than silently defaulting null `profileType`. The guard (`OnboardingGuardService.assertProfileComplete`) fires only for the legacy completed-but-null cohort — `profileType == null && onboardingCompletedAt != null`. Do not narrow it to bare `profileType == null`: users mid-onboarding persist `profileType` only at the final step (after generating), and copy-on-signup runs pre-onboarding, so both are `onboardingCompletedAt`-null and must stay exempt or the activation funnel breaks.
- Users with `onboardingCompletedAt != null` but `profileType == null` must be re-prompted only for Profile Type. Do not force them through learner level, course/program, exam-date, note creation, or Study Pack generation again.
- Onboarding step 2 collects required `learnerLevel` and required `courseProgram` before the first Study Pack flow can continue.
- `bio`, `Learning Style`, and reminder preferences are deferred to `/profile` and `/settings`.
- Profile Type can be edited later in `Profile`.
- Learning Style can be edited later in `Settings > Preferences`.
- Study Reminder Frequency can be edited later in `Settings > Preferences`.
- Public pages and anonymous flows must not be blocked by onboarding.
- NoteLib also has a separate product-onboarding tracker for brand-new users with `studyPackCount == 0`.
- After email verification, first-time users should see a welcome CTA before an empty dashboard so they know to create their first note immediately.
- Empty dashboard states for first-time users must be instructional, not generic.
- After the first Study Pack is generated, Note Detail should point users to Challenge Quiz as the next action.
- After the first Challenge Quiz is completed, surface weak-concept guidance before returning users to normal study flows.
- Product onboarding completion is tracked separately from activation onboarding and should not reuse `onboardingCompletedAt`.
- **Onboarding Study Pack generation (Step 4) must be idempotent**: `handleStartStudyPack()` must check `draft.noteId` before creating a note; if a note already exists, navigate to Step 4 instead of creating another. This prevents duplicate notes and study packs from back/forward/refresh behavior.
- **Back button lock during Study Pack generation**: hide the Back button while generation is active (`studyPackGenerating || startingStudyPack`); replace the notice with `Your Study Pack is being created. This step can't be undone.`; restore the Back button on error or completion.
- **Onboarding-only metadata auto-apply**: onboarding may explicitly opt into backend auto-apply for empty `subject` and `tags` when it starts Study Pack generation from an existing note. Normal note generation must keep AI metadata suggestions transient until the user confirms them in the AI Suggestions modal.
- **Learner level is required from onboarding onward**: every completed account must keep a user/profile-level learner level. Teachers should see copy that frames it as the default quiz difficulty for material they teach, with per-generation Teacher quiz overrides remaining explicit.

### Profile Rule

- `Profile` owns identity and account-related information only.
- `Profile` sections are:
  - `Identity`
  - `Learning Profile`
  - `Profile Type`
  - `Public Profile Link`
  - Teacher-only `Teaching Info` for DOCX export defaults
- Identity uses:
  - `firstName`
  - `lastName`
  - `displayName`
  - `username`
  - `email`
- `displayName` is presentation-only and must never be used as a unique identity.
- `username` is the stable public identity / handle and is used for public attribution and profile links.
- Usernames must be unique, URL-safe, and must not expose emails or raw private user IDs.
- Login accepts either email or username through the same credential field; keep email login working.
- Learning Profile uses:
  - `learnerLevel`
  - `courseProgram`
  - `bio`
- Do not collapse `firstName` and `lastName` into one `name` field in product UI or API contracts unless explicitly requested.
- `Profile Type` remains editable in `Profile` as a separate save action.
- `Profile` may link to `View Public Profile`, but Public Profile sharing and visibility controls do not belong on `/profile`.
- `/profile` layout should stay split into:
  - a top Display Name card with avatar, display name, email, and `View Public Page`
  - an `Identity` card with its own `Save Identity` action
  - a `Learning Profile` card with its own `Save Learning Profile` action
  - a `Profile Type` card with its own `Save Profile Type` action
- The Learning Profile card must carry `id={PROFILE_LEARNING_PROFILE_SECTION_ID}` (`"learning-profile"`) so it is reachable via hash navigation.
- The Dashboard "Adjust Level" CTA must navigate to `/profile?from=dashboard#learning-profile` — this scrolls directly to the Learning Profile card and enables context-aware back navigation back to Dashboard.
- Learning Profile combobox-style inputs should reuse the same input-plus-suggestions pattern as the Note Editor `Subject` field.
- Learning Profile `Course / Program` helper text should adapt to `learnerLevel` so examples match the learner's current study stage.
- Saving `Learning Profile` requires both fields and should show:
  - `Please select your learner level.`
  - `Please select or enter your course / program.`
- Profile save buttons must remain section-specific rather than global.
- Do not move `Learning Style` or study-reminder preferences into `Profile`.
- Email changes must write `pendingEmail` first and only update `email` after verification.

### Hash Navigation Rule

- When a page links to an in-page section with a hash target, the destination `id` must live on a native DOM element such as `section`, `div`, or a heading wrapper. Do not rely on fragment targets attached only to custom wrapper components.
- App Router pages that can be opened directly with a hash must mount the shared `HashScrollListener` (`frontend/components/navigation/hash-scroll-listener.tsx`) with the allowed target ids so direct URL loads and later `hashchange` events scroll correctly after content mounts.
- Prefer concrete route-plus-hash deep links for cross-surface navigation such as `/profile?from=dashboard#learning-profile` when the destination page is known.
- Use the same shared hash-navigation pattern for future `View Full Notes`, settings-section, and profile-section deep links instead of one-off fragment handling.

### Public vs Private Profile Separation Rule

- `Public Profile` (`/public/creator/{username}` canonical, `/public/profile/{userId}` legacy-compatible) is the user's public learning-portfolio surface.
  - Shareable, view-only to non-owners.
  - Shows `displayName`, `bio`, `learnerLevel`, `courseProgram`, `profileType`, public metrics, and public notes only.
  - Owner controls (`Edit Profile`, `Share Profile`, visibility toggle) are on the Public Profile page only.
- `Profile Settings` (`/profile`) is the private account editing surface.
  - Editable identity, learning profile, and profile type.
  - Accessed via the `Edit Profile` button on the Public Profile page.
  - Does not own public-profile visibility or sharing.
- The authenticated app shell avatar dropdown must always offer:
  - `My Profile` → `/public/creator/{username}` when available, otherwise `/public/profile/{userId}` (public identity page)
  - `Settings` → `/settings` (account and app settings)
  - `Sign Out`
- The sidebar Account section must use:
  - `Profile` → `/public/creator/{username}` when available, otherwise `/public/profile/{userId}` (same as `My Profile` in the avatar dropdown)
  - `Settings` → `/settings`
- Terminology rule: **Profile = public identity page. Settings = account/app settings.** Do not use "Account Settings" as a nav label — use plain "Settings".

### Shared Share Behavior Rule

- NoteLib uses one share pattern for all shareable content (notes and profiles).
- For public content: clicking Share opens a modal with title, `Shareable URL` field, `Copy Link`, and `Close` buttons.
- For private content: clicking Share opens a confirm modal first. The confirm offers `Cancel` and `Make Public & Share`. The share modal only opens after the owner confirms the visibility change.
- Share modal structure:
  - Note share modal title: `Share this note`
  - Profile share modal title: `Share this profile`
- Private note confirm: title `This note is private`, body `You need to make this note public before sharing. Anyone with the link will be able to view and copy this note.`
- Private profile confirm: title `This profile is private`, body `You need to make this profile public before sharing. Anyone with the link will be able to view your public profile and notes.`
- Do not implement content-specific share flows. Reuse `AppModal` with the same layout for all share actions.
- Do not use toast-only or inline-text-only share confirmation as the primary share feedback.

### Preferences Rule

- `Settings` should show `Preferences` before `Plan & Billing` and `Account`.
- `Preferences` currently includes `Learning Style` plus `Study Reminders`.
- `Learning Style` is stored as `engagementMode`.
- Reminder toggles are:
  - `inactivityRemindersEnabled`
  - `weakConceptRemindersEnabled`
- Preference values must persist in backend and be returned by `GET /auth/me`.
- Future reminder cadence should be guided by `Learning Style`, but scheduling logic is a separate task.

### Account Deletion Rule

- Account deletion starts as a reversible soft-delete: set `PENDING_DELETION` + `deleted_at`, revoke sessions, block normal login, and allow reactivation during the 30-day grace window.
- The irreversible purge reassigns public notes, their retained Study Packs, and financial records to the fixed deleted-user sentinel, removes private owned study data, and never deletes `analytics_events`.

### Data Export Rule

- Account data export must stay owner-only: resolve the requester from the authenticated principal, never accept a `userId` parameter, and query content through owner/user-scoped finders only.
- Data export returns one synchronous JSON attachment and must exclude secrets/tokens, analytics events, and financial/billing records.

### Upgrade CTA Rule

- Upgrade CTAs must be plan-aware. Never hardcode `Go Pro` as the universal upgrade CTA.
- Use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`:
  - Free → primary `Upgrade to Plus`, secondary `Go Pro`.
  - Plus → primary `Upgrade to Pro`, no secondary.
  - Pro → no CTAs (already top plan).
- Upgrade CTAs that drive in-app plan selection must navigate to `/settings?section=plans`. The Settings page reads the `section` query param, scrolls to the Plan & Billing card, and applies a temporary highlight ring.
- The `/pricing` page is the public marketing landing surface and stays linked from the navbar/landing only.
- Apply this rule on quiz result screens, the paywall modal, the Study Pack limit modal, the post-success upgrade nudge, and any near-limit banners.
- `PLANS` source of truth is `docs/product/PLANS.md`; runtime numbers live in `frontend/lib/pricing-config.ts` and feature lists in `frontend/src/config/plans.ts`. Keep all three in sync when limits or plan copy change.

### Analytics Rule

- Track product, growth, and upgrade events through the shared analytics event model.
- Analytics must be non-blocking and must never break the primary user action if persistence fails.
- Backend analytics must publish after the surrounding transaction commits (`AFTER_COMMIT`) and persist off-request through `analyticsTaskExecutor`; never write analytics mid-transaction.
- Backend services should record server-truth events for note, Study Pack, review, auth, public-copy, and subscription flows.
- Frontend/browser-only funnel events may post through `/api/analytics/events`.
- Admin reporting should read from analytics events plus core entity counts via `/api/admin/analytics/summary`.
- **Tracked completion events**: `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, and `ADAPTIVE_PRACTICE_COMPLETED` are fired from the frontend in the `finally`/completion block of each quiz flow and must not block the primary action.
- **Tracked funnel events**: `FEATURE_LOCKED_CLICKED` and `UPGRADE_CLICKED` are fired from paywall surfaces and the `PostSuccessUpgradeNudge` component respectively.
- `AnalyticsEventType` in `frontend/lib/api.ts` is the canonical union of all allowed event names — add new event names there before using them.

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
- `WEEKLY_SUMMARY` should honor `weeklySummaryRemindersEnabled`, which defaults off until the user opts in.
- `RE_ENGAGEMENT_2025` should honor `marketingEmailsEnabled`, which defaults off until the user opts in.
- `DUE_CONCEPTS_DIGEST` is enabled by default for new email/password and Google signups only; `AuthService` owns those explicit signup defaults while the database default remains false, and existing users' persisted preferences must never be backfilled or changed implicitly.
- Transactional account and billing emails are never gated by optional email preferences.
- Transactional email is never gated by the re-engagement daily budget; the budget only caps optional retention dispatch.
- Resend bounce/complaint suppressions apply to all email sends; suppressed addresses are skipped instead of retried.
- Optional emails must carry a tokenized one-click unsubscribe that maps category to the existing preference flag; transactional emails never carry unsubscribe links or headers, and unsubscribe tokens must not include PII beyond the opaque user id.
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
- Welcome email copy must reflect the current Free / Plus / Pro plan:
  - Free includes `10` Study Packs/month, Quick Review, limited Challenge Quiz, and Public Library access
  - Plus messaging highlights higher monthly limits and exports for regular study
  - Pro messaging highlights Adaptive Practice, Weak Concept Training, Board Exam Mode, and the highest limits
- Do not describe Challenge Quiz as paid-only in onboarding, welcome, or reminder emails.

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

- Backend owns subscription pricing, region detection, voucher eligibility, and Xendit checkout creation.
- Never hardcode backend checkout pricing; always load billable amounts from billing config or pricing services.
- Frontend must use the billing pricing API for pricing display in Settings, pricing surfaces, and upgrade prompts.
- Pricing UI copy, plan descriptions, CTA labels, and feature lists must come from the centralized frontend plan config.
- Never hardcode pricing-card features or plan CTA labels directly in UI components when the shared plan config already owns them.
- Shared pricing surfaces may keep the existing reviewer-safe PHP and USD display config, but checkout creation and upgrade eligibility stay backend-owned.
- Intro pricing and first-time promos must be implemented through the voucher/promotion system, not as a boolean on `User`.
- If pricing-surface messaging and runtime feature gating diverge, backend plan enforcement plus `GET /api/me/plan` remain the behavior source of truth until the product intentionally changes the gate.

### Payments Safety Rule

- Never grant paid access from frontend logic, success pages, or redirect callbacks.
- Only validated webhook-confirmed payments may update user paid-plan status.
- All plan and entitlement logic must use the `subscriptions` table as the source of truth.
- Preserve subscription history in `subscriptions`; do not collapse the table to one row per user.
- Only one `ACTIVE` subscription row should exist per user at a time.
- Do not introduce plan flags or paid-state fields on `users`.
- Always validate the Xendit `x-callback-token` before processing webhook payloads.
- Webhook handling must stay idempotent through persisted provider event records and payment transaction lookups.
- Voucher redemption history must be written only after a confirmed `PAID` webhook, never while checkout is still pending.
- Payment-flow doc updates are required whenever checkout, webhook, returnUrl, or paid-plan expiry behavior changes.

### Billing History Rule

- `Settings -> Plan & Billing` should include a read-only billing history section below the current plan and usage card.
- The billing summary card should show current plan, subscription status, billing cycle, and renewal or end date.
- If `cancelAtPeriodEnd=true`, show that the active paid plan will end on the stored date and will not renew.
- Payment history must come from `PaymentTransactionEntity` data via `GET /api/billing/history`.
- Billing history rows should stay user-friendly and must not expose raw webhook event names.

### Library Rule

- Library is note-based and contains the current user's notes (Draft + Study Pack Ready).
- `Study Pack Ready` is the learner-facing readiness indicator for normal Library browsing.
- `Quiz Ready` is a Teacher/exam-export workflow indicator. Show `Quiz Ready` badges and filters only for Teacher private-library browsing or explicit exam-builder/admin-content contexts.
- Student and Board Taker profiles must not see `Quiz Ready` badges or filters in normal Library browsing; reset hidden `Quiz Ready` filter state if profile/context changes.
- Public Library must not expose Teacher-specific `Quiz Ready` UI.
- Do not remove generated-quiz readiness data from backend payloads; Exam Builder still needs it for selection, question counts, disabled states, and exports.
- Public Library is note-based and contains notes where `visibility=PUBLIC`.
- Public Profile is a public showcase of one creator's public notes and contribution stats.
- Public Profile may show `bio`, optional `learnerLevel`, optional `courseProgram`, and derived subject chips, but it remains a learning profile rather than a social-media profile.
- Public Profile should feel like a lightweight learning portfolio:
  - compact metrics only
  - real note-usage signals such as public-note count, copies, shares, and views when available
  - optional featured-note callout only when backed by real usage data
  - no follower/social-network patterns
- Private Library and Public Library should keep the same top-level list structure:
  - `Search`
  - `Filter`
  - `Sort`
  - notes list
- Current library filtering and sorting stay frontend-side over loaded note-list payloads.
- Backend note-list payloads must expose the metadata needed for library filtering/sorting, including note `courseProgram`, `createdAt`, `updatedAt`, and public-note owner `learnerLevel` when applicable.
- Private Library should expose its primary organization controls inline above the note list in this order:
  - `Search`
  - `Subject`
  - `Popular Tags`
- Private Library search should match note `title` and `tags` in real time.
- Private Library subject filtering should be single-select with `All` as the default chip and should use a one-line horizontal scroll rail rather than wrapping.
- Private Library should keep a `+ More` chip at the end of the subject rail so users can open the full searchable subject selector without adding vertical clutter.
- Private Library should not expose the full tag list by default; show only a limited `Popular Tags` rail plus a `+ More` control.
- Private Library `+ More` should open the shared selector surface:
  - subjects -> searchable single-select list
  - tags -> searchable multi-select list with a selected-tags quick-deselect section near the top
  - mobile -> bottom sheet
  - desktop -> modal/sheet
  - actions -> `Apply`, `Clear`
- Selector option ordering may prioritize recent use first, then frequency, then alphabetical order.
- Private Library tag filtering should remain multi-select, use OR logic within the tag group by default, and combine with search + subject on the loaded note list.
- Rationale: OR matching makes tag browsing feel broader and avoids false empty states when users combine tags from different notes.
- Notes without an explicit subject may derive a temporary fallback subject from existing saved metadata so Library grouping/filtering still works.
- Public Library filters should support:
  - `Course / Program`
  - `Learner Level` when public note results expose it
  - `Subject`
  - `Tags`
  - `By You`
  - `Official`
  - `Community`
- Public Library should keep search first, then one-line horizontal rails for `Subjects` and `Popular Tags` before the note grid.
- Public Library subject filtering should stay single-select with `All` as the default and use a `+ More` chip to open the full searchable selector.
- Public Library tag filtering should stay multi-select, use OR logic within the tag group by default, and expose only a limited `Popular Tags` rail plus a `+ More` selector.
- Public Library `+ More` should reuse the shared selector surface:
  - subjects -> searchable single-select list
  - tags -> searchable multi-select list with selected tags surfaced near the top
  - mobile -> bottom sheet
  - desktop -> modal/sheet
  - actions -> `Apply`, `Clear`
- Public Library is a curated discovery page first, not a flat generic list.
- Discovery mode should preserve:
  - `Featured Notes`
  - `Most Popular`
  - `Recently Added`
- Featured Notes should remain visually distinct from the rest of the Public Library sections.
- Control Public Library density with section limits and per-section `View More`, not by removing previews, tags, subject badges, source labels, or engagement metadata.
- Current discovery-home limits are:
  - Featured Notes -> 3
  - Most Popular -> 5
  - Recently Added -> 5
- `View More` may use the same Public Library route with section-specific state/query params as long as the curated discovery model remains intact.
- Public Library should include the current user's own public notes, other users' public notes, and official NoteLib public/sample notes.
- Public Library cards should label note source as:
  - `By You` for the current user's own public notes
  - `By NoteLib` plus `Official` badge for the official NoteLib account
  - `By {displayName} · @{username}` for other public notes when username is available
- Public author labels are viewer-relative:
  - owner viewing own public note -> `By You`
  - official NoteLib account -> `By NoteLib` with `Official`
  - all other public notes -> `By {displayName} · @{username}` when username is available, otherwise `By {displayName}`
- `displayName` is the readable public author label, not a unique creator identity.
- `username` is the stable public author identity and should back public creator links.
- Public Library cards and public note detail must not rely on `displayName` alone for creator identity when duplicate names exist.
- Creator links should use a stable public identifier:
  - preferred -> username / handle when available
  - fallback direction -> generated public slug
- When disambiguation is needed or a handle exists, public labels may render `By {displayName} · @{handle}` while keeping `displayName` first for readability.
- Never show public author emails or raw private user IDs on public surfaces.
- If stable public handles/slugs are introduced, existing public links must remain valid through compatibility or redirect handling.
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
  - non-owner -> `Copy to My Library`
- Public note detail header should show `Subject • Author` using the same viewer-relative label logic as library cards.
- Public note detail is read/copy/share only:
  - owner -> `Open Note`, `Share`
  - non-owner -> `Copy to My Library`, `Share`
- Public note detail should not expose edit, delete, generation, or study actions; generation remains a Note Editor responsibility and quizzes remain on study surfaces.
- Public and private note detail should both expose `Summary`, `Key Concepts`, `Quiz`, and `Full Notes` so the original note stays easy to inspect.
- Keep `Summary` as the default tab; `Full Notes` is for reading the complete original note body, not a collapsed preview.
- The `Summary` view should include a subtle `View Full Notes →` CTA that switches tabs without a full page reload and without interrupting the current reading position.
- Public Profile note cards should reuse the public-note route and must not expose private workspace actions.
- Subject UI rules:
  - render subjects as badges across library cards and note headers
  - note headers should place `Subject Badge • Author`
  - `notes.subject` remains the persisted source of truth; do not add a subjects table unless explicitly requested
  - note editor and library subject filters should use backend-driven distinct subject suggestions from persisted notes
  - subject inputs must still accept custom typed values and save them directly into `notes.subject`
  - normalize saved subjects for whitespace and dash formatting so equivalent values reuse the same subject suggestion/filter label when possible
  - treat subject reuse checks as case-insensitive while keeping a readable display label
  - AI-generated subjects should prefer specific reusable academic labels, often `Primary field – subtopic`, rather than broad umbrella fields
  - avoid broad generated labels such as `Medicine`, `Engineering`, `Education`, `Law`, or `Business` when the notes support a more specific subject
- Course / Program UI rules:
  - `courseProgram` is the top-level note-classification shelf above `subject` and `tags`
  - `users.courseProgram` and `notes.courseProgram` remain persisted string fields; do not add a `course_programs` table unless explicitly requested
  - note editor, onboarding, profile, and note-detail metadata course/program inputs should use one shared autocomplete behavior backed by saved-value suggestions plus curated defaults
  - authenticated course/program suggestions come from `GET /api/course-programs?scope=mine`
  - public/discovery course/program values may come from public note payloads or `GET /api/course-programs?scope=public`
  - course/program inputs must still allow custom typed values
  - typing should filter suggestions in real time, case-insensitively, with prefix matches ahead of contains matches
  - typing must not keep the full unfiltered list visible
  - existing matching suggestions should appear before the custom `Use "..."` action
  - exact case-insensitive matches should reuse the existing saved display label instead of creating a casing variant
  - saved course/program values should normalize whitespace and dash formatting so equivalent values reuse the same suggestion/filter label when possible
  - course/program reuse checks should be case-insensitive while keeping a readable display label
- Public Library canonical browsing route is `/public/library` for both signed-in and signed-out users.
- Do not introduce duplicate Public Library browse routes or wrappers such as `/library/public`; keep legacy paths as redirects only when compatibility is required.
- Public subject listing pages must not become second canonical list pages for query-filtered browsing; use `/public/library?subject={subjectSlug}` for shareable subject filtering. `/public/library/{subject}` is a separate, server-rendered canonical subject landing page (shipped v0.14.0 — see `docs/features/public-library.md`), not a redirect, and must not be merged with the query-filter view without a dedicated future refactor.
- Public SEO note pages use `/public/library/{subject}/{slug}` as the canonical route.
- Public SEO pages must stay accessible without login and indexable only for `PUBLIC` notes.
- Public landing page should emit JSON-LD `WebSite` schema.
- Public Library index should emit JSON-LD `CollectionPage` schema.
- Public Library filter state must stay in sync with URL query params; direct opens of filtered `/public/library?...` URLs must restore the same selected filters in the UI.
- Public Library search inputs must not update the URL on every keypress. Use local input state plus a short debounce, then `router.replace(..., { scroll: false })`.
- Public Library filter interactions must preserve focus and scroll position. Subject chips, tag chips, audience changes, sort changes, and clear-filter actions must not jump the page back to the top.
- Public Library tag browsing must always stay accessible through a dedicated action such as `Browse all` / `Browse tags`; do not rely on a disappearing `+ More` tag chip when the visible tag list is short.
- Searchable Public Library selector modals must keep the search input focused while typing; do not let modal rerenders or close-button autofocus steal the caret.
- Public SEO note pages should emit JSON-LD `Article` schema using real note data only.
- `robots.txt` must allow public crawling and disallow authenticated/private app areas such as `/dashboard`, `/library`, `/notes`, `/settings`, `/admin`, and `/api`.
- `sitemap.xml` must include only public SEO-safe routes: `/`, `/privacy`, `/terms`, `/public/library`, canonical public subject URLs, and canonical public note URLs.
- Private notes must never be exposed through the public SEO route.
- Copying a public note must preserve attribution via `copiedFromNoteId` and `copiedFromUserId`.
- Public Library copy UX should keep the existing copy endpoint but make public copies idempotent per user/source note.
- Public Library cards may include subtle inline `Save` plus lightweight heart/like controls as the allowed exception to the no-inline-card-actions rule on note cards.
- Public Library card CTAs should stay compact:
  - icon + short label
  - outline / ghost weight
  - never full width
  - aligned with author metadata in the footer row rather than taking over the full card width
- Guests clicking `Save` should see an auth prompt modal before auth navigation.
- Guests clicking the heart/like control should see an auth prompt modal before auth navigation.
- If the current user already has that public note in their library, replace `Save` with muted `Saved` instead of showing another navigation button inside the card.
- Successful Public Library copies should offer `View Note` and `Start Review` follow-up actions, with:
  - `Start Review` as the primary CTA
  - `View Note` as the secondary CTA
- Public Library copy-success feedback should use:
  - a desktop modal with a visible top-right close button
  - a mobile bottom sheet with tap-outside and swipe-down dismissal
  - a success-leading visual treatment with stronger title hierarchy and a subtle check indicator
  - concise body copy (`You can start reviewing now or come back later from your library.`)
  - desktop action alignment of `View Note` then `Start Review`, right-aligned
  - mobile action stacking with full-width buttons and `Start Review` visually first
  - compact spacing and softened depth so the surface feels product-grade without becoming heavy
- Copied private notes should display attribution as `Copied from {title} in Public Library.` when source metadata exists.
- Study Pack-ready Note Detail should keep quiz history on the note page:
  - show `Recent Sessions` below `Performance Overview`
  - merge completed Quick Review and Challenge Quiz attempts in reverse-chronological order
  - `Recent Sessions` is the entry point into session review on Note Detail
  - desktop and mobile both open the same dedicated session-review page with a clear back path to Note Detail
  - use stored session data only for answer review and concept breakdown; do not call LLMs for session history or review
  - allow graceful fallback summaries for older sessions that do not have full stored quiz detail
  - weak concepts in session review use the same `< 60%` accuracy threshold as other study surfaces

### Explore Navigation Rule

- Authenticated primary navigation order is Dashboard, the existing profile-aware Collections label, Library, Explore, Progress.
- `/explore` is an authenticated composite discovery front door with `Review Sets` and `Notes` tabs plus an Exam Hub index pointer.
- Explore must reuse the existing Official Review Set catalog and Public Library rendering. It must not replace, redirect, or redefine the canonical `/collections/published` and `/public/library` routes.
- Library stays structurally separate from Collections and Explore.
- The mobile bottom tab bar replaces its former Public Library tab with Explore and keeps the existing `mobileTabBarEnabled` preference gate.
- The anonymous marketing navbar is separate and must not gain the authenticated Explore item.
- Exam Hub Official Review Set enrichment uses exact normalized `courseProgram` matching only, remains anonymous-previewable, and must fail open so public-note content still renders.

### Card Interaction Rule

- Library cards, Public Library cards, and Public Profile cards must use a consistent interaction model.
- The whole card should be clickable to open the detail page.
- Do not add inline action buttons or note-card context menus to note cards, except for the Public Library `Save` / `Saved` CTA in the footer row.
- Note cards are preview/navigation surfaces only; note actions belong in Note Detail.

### Design System — Icons and Buttons

1. Use consistent icons for common actions (`edit`, `delete`, `share`, `copy`, `open`, `public/private`) and do not drift per page.
2. Desktop buttons must show icon + text.
3. Mobile buttons must show icon only.
4. Avoid note-card action buttons; if a non-note card needs actions, place them at the bottom-right.
5. Header/page actions should be placed at the top-right.
6. Visibility should be shown as a badge/dropdown, not a large button.
7. Entire note cards should be clickable; do not add `Open` buttons inside cards.
8. Do not introduce a new icon for an existing action without updating this document.
9. Sidebar navigation icons must stay consistent:
   - `Dashboard` -> `Home`
   - `Library` -> `Book`
   - `Public Library` -> `Globe`
   - `Profile` -> `User`
   - `Settings` -> `Gear`
   - `Admin` -> `Shield`
10. Use outline-style icons only. Do not mix outline and filled icon styles.
11. Do not use emoji as icons in product UI.

Primary CTAs may keep full text on mobile when the action would be ambiguous as icon-only.

### Tabs vs Buttons Rule

- Tabs are for switching views such as `Summary`, `Key Concepts`, `Quiz`, and `Full Notes` within the same note.
- Buttons are for actions such as `Start Quiz`, `Delete`, `Save`, and `Share`.
- Tabs should use an underline-style navigation treatment, not filled or outline button styling.
- Tabs may include small outline icons.
- Desktop tabs should show icon + text.
- Mobile tabs should also show icon + text when they switch major note views.
- Note Detail tab order should stay `Summary` -> `Key Concepts` -> `Quiz` -> `Full Notes`.
- Note Detail should still guide the reading flow from `Summary` into the source material through a subtle `View Full Notes →` CTA inside the summary view.
- Switching tabs must not reset page scroll to the top; preserve the current content area when the tab state changes.
- Query-string tab switches on Note Detail must not trigger a note refetch or loading-state remount.

### Mobile Button Rule

- Important action buttons must display icon + text on mobile.
- Do not use icon-only buttons for major actions such as navigation, quiz entry, copy/share, create, save, upgrade, or public-page actions.
- Prefer clarity over minimal UI.
- Keep this behavior consistent across Dashboard, Note Detail, Library, Public pages, Profile, and Settings.
- Small utility controls may remain icon-only only when the action is already highly familiar (`edit`, `delete`, `back`, menu, theme toggle, notifications, avatar).

### Dark Mode Button Contrast Rule

- Outline buttons must use lighter borders and lighter text in dark mode.
- Border contrast must be visibly brighter than the card/background surface behind it.
- Text should be near-white for readability in dark mode.
- Outline buttons should have a visible dark-mode hover fill.
- Do not reuse light-mode border contrast assumptions in dark mode.

### Quiz Mode Icon Rule

- `Quick Review` uses a lightning icon.
- `Challenge Quiz` uses a trophy or clipboard-style challenge icon.
- `Adaptive Practice` uses a target or focused-practice icon.
- Do not use the same icon for different quiz modes.

### Post-Quiz UX Consistency Rule

All three quiz flows (Quick Review, Challenge Quiz, Adaptive Practice) must follow the same UX pattern:

- **No "Note" button** on any quiz screen — `Note` as a `<Button>` is forbidden
- Navigation back to the note must be a `← Back to Note` **text link** (`BackLink` component), placed **below** action buttons, never grouped with them
- **Exactly one primary CTA per result screen.** Never show two equal-weight primary buttons.
- **Button hierarchy** on result screens:
  - Primary: next learning action — Quick Review: `Practice Weak Areas` (Adaptive) when struggling + available; `Practice Again` when struggling + adaptive unavailable; `Take Another Challenge` after strong/perfect result — Challenge Quiz: `Practice Weak Concepts` when weak concepts exist, otherwise `Take Another Challenge` — Adaptive Practice: `Generate New Set`
  - Secondary: review/repeat/support actions (`Review Answers`, upgrade nudge, secondary `Practice Again`)
  - Navigation: `← Back to Note` link below
- Edge states such as empty quiz data, monthly limits, unavailable sessions, or missing weak-area labels must keep a clear next step and use text-link navigation rather than `Back to Note` buttons.
- **Confidence feedback** (Quick Review only): moved to a secondary section below the primary CTA group; after selecting, option buttons are replaced by a badge — `🟢 Confident`, `🟡 Improving`, `🔴 Needs Practice`; "Thanks for the feedback." text is removed
- **Inline learner level selector** (Quick Review and Challenge Quiz result screens): pill-group selector loads the user's current `learnerLevel` via `GET /auth/me` when the result becomes visible; changing a level saves via `updateProfileLearnerLevel` in `lib/api.ts` and shows a toast; do NOT add a new learner level system — reuse the existing `LearnerLevel` enum and `LEARNER_LEVEL_OPTIONS`
- **Adaptive Practice completion**: "Generate New Set" is always the primary button; `← Back to Note` link below
- **Review Answers**: Quick Review, Challenge Quiz, and Adaptive Practice must use the shared post-quiz review pattern showing question text, selected answer, correct answer, explanation, and concept chip.
- Review Answers answer states must stay consistent: correct answer uses restrained green styling, incorrect selected answer uses restrained red styling, neutral distractors stay quiet, and selected-correct answers show both `Your answer` and `Correct answer`.
- Review Answers should use stored quiz/session data (`question`, `choices`, selected canonical choice indexes, `correctIndex`, `explanation`, `concept`) so completed-session history/review can reuse the same structure later.
- Motivation/feedback messages use `mapPerformanceLevel` thresholds for consistency (Excellent / Good / Fair / Needs Improvement)
- While a quiz session is active, replace normal header back navigation with active-session text plus `Leave Quiz`; navigation away must open the shared `Leave quiz?` confirmation instead of leaving immediately.
- The shared leave confirmation copy is `You are currently in an active quiz. Leaving will forfeit your progress.` with `Stay` and `Leave Quiz` actions.
- Confirmed leaves mark the session `FORFEITED`; Challenge Quiz and Adaptive Practice forfeits must not refund quiz credits or mark the session completed.
- Board Exam Mode is the exception to the generic leave-forfeit copy: it uses `Leave exam?` with `Stay` and `Submit & Leave`, and confirming the leave submits the current exam and counts it as completed.

### Challenge Quiz — Exam Mode Rule

- Challenge Quiz must behave as an exam: **no correctness feedback during answering**.
- Board Exam Mode is the explicit strict-exam presentation of the Challenge Quiz engine and must be available as a distinct Challenge mode for Pro users.
- Challenge Quiz entry must present both `Challenge Quiz` and `Board Exam Mode` as explicit mode choices rather than inferring Board Exam from billing or difficulty-selection capability.
- Board Exam Mode must use a formal `Board Exam setup` confirmation state with timer/question/result summary plus `Cancel` and `Start Exam`.
- Board Exam setup must also explain that the mode is a focused, distraction-free exam simulation, results are delayed until completion, and navigation will be limited intentionally during the session.
- Tapping `Start Exam` must show a confirmation modal before quiz generation starts so users understand the stricter flow.
- Board Exam Mode uses the same Challenge Quiz quota and credit rules as standard Challenge Quiz in the current product stage; do not create a separate billing gate for Board Exam Mode.
- Board Exam Mode always uses a fixed recommended difficulty/question count (`DIFFICULTY_MIXED`) — it never exposes a difficulty selector (v0.60.1 removed Challenge Quiz's manual selector entirely; Board Exam Mode never had one).
- Do not render "Correct" / "Incorrect" labels, green/red highlights, or explanations while the quiz is in progress.
- Standard Challenge Quiz may keep a lighter practice-oriented answering UI, but Board Exam Mode must use a more formal neutral selected-answer state and cleaner hierarchy.
- Board Exam running state should reinforce the mode visibly with `Board Exam Mode`, `Exam in progress`, and subtle copy that limited navigation is intentional.
- A one-time, dismissible Board Exam focus tip may explain that distractions are hidden to simulate a real test environment.
- Question-number navigation during Board Exam Mode may show current/answered/unanswered states, but must not reveal correctness.
- Board Exam timers must use persisted session timing as the source of truth (`timerStartedAtEpochSeconds + timeLimitSeconds`) and survive refresh/reload without resetting or extending the exam.
- Board Exam timer UI should surface calm warning states as time gets low, but once time expires it must lock answer changes/navigation immediately.
- Timer expiry must auto-submit exactly once per expiry event; if timeout submission fails, the page may expose explicit retry submission but must not silently keep auto-submitting every tick.
- Browser fullscreen/focus entry is best effort only; a denied fullscreen request must not block starting or resuming the exam.
- The Challenge Quiz start screen must disable difficulty controls and the Start button immediately after Start is clicked.
- Duplicate Challenge Quiz start requests must be blocked while quiz initialization is in flight.
- All result calculations (score, performance level, concept breakdown, weak concepts) must be derived from quiz session data — **no LLM calls for statistics**.
- Use the utility functions in `lib/challenge-quiz-results.ts` for testable result computation.
- Performance level thresholds: 90–100 → Excellent, 75–89 → Good, 50–74 → Fair, 0–49 → Needs Improvement.
- Weak concept threshold: accuracy < 60% (`WEAK_CONCEPT_THRESHOLD`).
- The "Practice Weak Concepts" CTA must only appear when `weakConcepts.length > 0` and must link to Adaptive Practice.

### Challenge Quiz — Progressive Generation

- Challenge mode starts with 5 questions (`INITIAL_CHALLENGE_QUIZ_COUNT`). Board Exam Mode generates a fixed count based on learner profile and is exempt from progressive generation.
- Users can request +5 more questions from the last question via `POST /challenge-quiz/sessions/{sessionId}/generate-more`, up to `MAX_CHALLENGE_QUIZ_QUESTIONS = 20` per session.
- The backend deduplicates generated questions by normalized text against all existing session questions; if fewer than 3 unique new questions survive, it returns `NOT_ENOUGH_NEW_QUESTIONS` (HTTP 409). The frontend must treat this as a soft end-of-questions state (`noMoreQuestions = true`), not an error.
- Score is based on **answered questions** (`selectedChoices.size()`), not the total question count in the session. This allows users to finish early without penalizing unattempted questions.
- Action bar at the last question (Challenge mode only): show `+5 Questions` / `Adding...` when under max and `noMoreQuestions` is false; always show `Complete Quiz` to submit.
- Board Exam Mode retains its existing submit label and flow unchanged.

### Challenge Quiz — Leave Guard Stability

- `onBeforeRouteLeave` and `onConfirmLeave` callbacks passed to `useQuizSessionGuard` must be stable references (wrapped in `useCallback`).
- The timer fires every second and causes a re-render on every tick. If these callbacks are inline arrow functions, `useQuizSessionGuard` recreates `LeaveQuizModal` as a new component function each tick — React unmounts and remounts the open modal every second.
- `onConfirmLeave` should read from `challengeSessionRef.current` instead of `challengeSession` state to avoid listing the session as a dep while still seeing the latest value.

### Note Card Consistency Rule

- Library, Public Library, Public Profile, and public subject listing pages should reuse the shared note-card layout.
- Note cards must use a shared layout and component across note-list pages; Public Library may add subtle discovery metadata such as views and copies, but the base card structure must remain consistent.
- Note cards must use a shared layout and component across note-list pages; Public Library may add subtle discovery metadata such as views, copies, and likes, but the base card structure must remain consistent.
- Shared note-card content order is:
  - TOP ROW: Subject badge + Course/Program badge (neutral/gray) — above title
  - Title (with optional private-library visibility icon `Globe`/`Lock` trailing)
  - Study Pack Ready badge (green) — below title, only when applicable
  - Quality badges (High Quality, Well liked, Popular) — below title alongside stateBadge
  - `Note Preview`
  - `Summary Preview`
  - Tags
  - subtle discovery metrics row (`views`, `copies`, `likes`) when that surface has them
- `SharedNoteCard` props: `courseProgram` (neutral gray badge above title), `stateBadge` (Study Pack Ready, rendered below title), `metadataBadges` (quality badges)
- The "New" badge has been removed; quality badges are High Quality, Well liked, and Popular only
- `Note Preview` comes from note content and `Summary Preview` comes from generated Study Pack summary.
- `Note Preview` should read as the primary preview and `Summary Preview` should stay secondary.
- If no generated summary exists, show `No summary available yet.`
- Use clamped preview text so card heights stay consistent across listing grids.
- Do not render `Public` / `Private` as a large badge on note cards; use a subtle icon instead when the visibility distinction matters.

### Page Responsibility Rule

| Page | Governing question |
|---|---|
| Dashboard | What should I do now? |
| Review Sets (the profile-aware Collections workspace) | What material have I organized into a study journey? |
| Library | What notes do I already have? |
| Explore | What material exists that I don't have yet? |
| Progress | How is my learning progressing? |
| Companion | What guidance applies to this curated journey? |
| Public Profile | What learning work do I share publicly? |
| Profile | Who am I as a learner? |
| Settings | How should NoteLib behave for me? |

**Locked doctrine (2026-07-30):**

- `/explore` is the single owner of content discovery. No other authenticated page may render an inline discovery catalog, adopt-picker, or public-note browse grid.
- Other pages may point at Explore with a link or a single pointer card; they may not do Explore's job.
- A bounded teaser is not discovery when it has a fixed small item count, no filters/paging/sort, no adopt/copy action, and one see-all link.
- `/public/library` and `/collections/published` remain canonical, separately-addressable routes for deep links, SEO, and anonymous access. This is a navigation-level claim, not a route deletion.
- `/onboarding` is exempt because it is a temporally scoped first-run wizard, not a persistent navigation page.
- Treat this as a deliberate, locked product rule rather than an informal convention.

**Amendment (dated 2026-07-31, pending Stage 0 — not ratified):** a future direction exists to name the **Discovery System** as the product-architecture concept this table already implements — Explore is its primary interface; Public Library, Official Review Sets, and Exam Hubs are its sources and content surfaces. "Explore Owns Discovery" (locked above) is that doctrine's authenticated-navigation scope; this amendment extends the same doctrine toward eventual anonymous access, it does not replace it. Under this framing, `/explore` may eventually absorb `/public/library`'s *list-page* traffic only, once `/explore` itself gains real anonymous rendering, canonical metadata, and structured data. This narrows — it does not reverse — both the "must not replace, redirect, or redefine" language in `### Explore Navigation Rule` below and the "navigation-level claim, not a route deletion" language above: both continue to mean subject-listing pages (`/public/library/{subject}`) and note-detail pages (`/public/library/{subject}/{slug}`) are never redirected, full stop; only the bare list page is a legitimate future redirect target, and only once this amendment and a concrete SEO-parity evidence bar both clear. This also revisits, but does not resolve by itself, the owner's own earlier "Public Library is not absorbed or removed" direction recorded in `ROADMAP.md`'s Review-Set-Centric Navigation section — under this framing that direction stays true (Public Library remains a Discovery System source and route family; only its navigation primacy changes), so this reads as a narrowing of that direction, not a reversal needing separate sign-off, but the owner should confirm that reading explicitly rather than have it asserted silently. Blocked on Explore gaining real anonymous rendering, canonical metadata, structured data, and a resolved sequencing decision against this release's own `[CHECKPOINT — due 2026-09-13]`. Tracked in `ROADMAP.md`'s Backlog Index as "Discovery System — Public Front Door."

### Companion Guidance Doctrine

Ratified 2026-07-31 (Company Redefinition Phase 4, considered and narrowed 2026-07-29 — see `ROADMAP.md`'s Backlog Index "Companion Guidance Doctrine" row for the full pressure-test history). "Companion" today names three structurally different things — admin-authored static content, learner-reactive derived guidance (i.e. "Coach"), and the LLM chat (Ask Companion) — and a literal system-merge of them was considered and rejected: it would remove the vocabulary that keeps them safely apart (a learner would see "Companion" guidance on Dashboard, then be told it's unavailable on a Review Set with no admin-authored content).

**Authoring doctrine (docs/copy only — applies to new guidance surfaces going forward):**

- **One learning responsibility per feature.** Each guidance surface answers exactly one governing question (see the Page Responsibility Rule table above). Do not let a guidance feature quietly answer a second surface's question.
- **One question per surface.** A given page should not present two independently-reasoned "what should I do next" answers competing for the same moment of attention.
- **De-duplication rule.** Before adding a new "what's next" resolver, check whether an existing one already covers the same signal on the same surface; extend it rather than adding a parallel resolver.

**Explicitly not done by this doctrine:** no rename of "Companion," "Ask Companion," or "Coach"; no new user-facing brand; no backend merge. `Feature.ASK_COMPANION`, the `ask_companion_sessions` table, and `AnalyticsEventType.ASK_COMPANION_*` are unchanged. The 8 existing, independently-justified "what's next" resolvers across Dashboard/Collection detail/Progress (e.g. Dashboard/collection pacing staying uncoupled per `docs/features/dashboard.md:95`) are not merged by this doctrine — a full audit-and-merge pass ("Phase 1" of the phased plan in `ROADMAP.md`) stays gated on the still-open Primary-Review-Set-vs-Study/Exam-Focus philosophy question, unresolved as of this doctrine's adoption.

### Auth Redirect Rule

- If a session expires while the user is inside authenticated app pages, login should return them to that interrupted page through the explicit `redirect` query.
- If a logged-out user tries to open a protected route, login should return them to that requested protected page through the explicit `redirect` query.
- Manual login from public pages should land on `Dashboard`.
- Manual sign-out must clear any remembered protected return path and must not reuse a stale `redirect` query on the next login.
- After manual sign-out, the next successful login should land on `Dashboard` unless verification or onboarding gating applies.
- Do not send users back to public marketing or discovery pages automatically after login unless a protected-route redirect explicitly requires it.

### Social Login Rule

- Google OAuth is an alternative sign-in method, not a replacement for email/password.
- Verify Google identity tokens on the backend; never trust frontend-only Google profile data.
- Only auto-link by email when Google reports `email_verified=true`.
- Store provider identity in `user_auth_providers`; do not store provider IDs directly on `users`.
- Existing email/password users with a verified matching Google email must be linked, not duplicated.
- Do not add Apple/Facebook/GitHub or unlink/provider-management UI unless explicitly requested.

### Auth Messaging Rule

- `Your session has expired. Please log in again.` must only appear when login is opened with `reason=session_expired`.
- Manual logout must not reuse the session-expired message; it may show a neutral `You have been logged out.` message instead.
- Manual logout intent must suppress late `401` redirects from in-flight protected requests so logout messaging stays neutral.
- Protected-route access while logged out should use neutral login messaging such as `Please log in to continue.`
- Login-page messaging must be driven by the current auth-route query state, not sticky component state from a previous redirect reason.

### Profile Page Responsibility Rule

- `/profile` is a private identity settings surface, not a public-page controls surface.
- Keep Public Profile sharing and visibility controls on `/public/profile/{userId}` only.
- `View Public Page` on `/profile` is navigation only and should not be grouped with save actions.

### Public Profile Owner Controls Rule

- Public Profile owner controls belong on `/public/profile/{userId}`, not on `/profile`.
- Only the profile owner may see `Edit Profile` and the Public Profile visibility toggle.
- Non-owners may see a share action on Public Profile, but they must not see owner-only editing or privacy controls.
- Public Profile note cards stay action-free for both owners and non-owners.
- If a public profile is turned off, non-owners should see `This profile is private.`

### UI Consistency Rule

- Public Profile should reuse the Note Detail control pattern for visibility and share actions.
- Visibility controls should appear as badge/dropdown controls near the header identity cluster, not as detached toggle buttons.
- Share actions should sit in the lower action row of the header card rather than in the top metadata cluster.

### Back Navigation Rule

- All back navigation uses the `BackLink` component (`components/ui/back-link.tsx`): renders `← {label}` with `ArrowLeft` icon, blue link color (`text-blue-600 dark:text-blue-400`), underlines on hover — same style as "View Full Notes →". Not a button.
- Back links appear on sub-pages only. Main pages (Dashboard, Library, profile-aware Collections, Explore, Progress, Public Library, My Profile, Settings) must NOT have a back link.
- Back navigation always uses explicit routing (`href` prop on `BackLink`) — never `router.back()`.
- Back link label is the destination page name only — do NOT use "Back to X" or "Back" alone.
- My Profile (owner's own public profile) is a main page — no back link.
- Non-owner viewing another user's public profile: `<BackLink href="/public/library" label="Public Library" />`.
- Inline card action buttons (quiz error/limit states etc.) should use short destination labels (`Note`, `Library`) — not "Back to Note" or "Back to Library".
- Back link is positioned above the page header card, left-aligned.
- **Context-aware back navigation**: Profile Settings (`/profile`) should render `← Dashboard` (href `/dashboard`) when reached via `?from=dashboard`, and `← Profile` (href public profile path) in all other cases. Pass `?from=dashboard` in the navigation URL to trigger this behavior.

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

### Learning Profile Metadata Rule

- `learnerLevel` lives on `User`, not on Note or a separate learner-profile table.
- `User.courseProgram` remains the profile-level default for new notes.
- Notes may also store an optional note-level `courseProgram`, defaulted from the user's profile and editable per note.
- For Study Pack generation, `notes.courseProgram` is the source of truth when present. Fall back to `users.courseProgram` only when the note has no saved course/program.
- Metadata hierarchy should stay:
  - `courseProgram` -> top-level track/domain
  - `subject` -> reusable academic topic
  - `tags` -> fine-grained keywords
- `learnerLevel` is required during onboarding but remains nullable in storage for pre-existing users.
- `courseProgram` is required during onboarding and later Learning Profile saves, but remains nullable in storage for pre-existing users until they update it.
- Backend generation context may carry `learnerLevel`, `courseProgram`, `subject`, and `tags`. Static note and Study Pack content uses course/program for calibration; learner level remains available for quizzes, exams, and exam-pool pre-warm.

### LLM Context Builder Rule

- All LLM calls must resolve context through `StudyPackGenerationContextResolver` (backend service).
- Static note and Study Pack content must call the content-context builder, which omits learner level and uses course/program to calibrate depth, vocabulary, terminology, and examples.
- Quiz and exam prompts must call `buildLearnerContextBlock()`, which includes learner level and course/program for taker-specific difficulty plus domain context.
- Never inline raw learner-level or course/program formatting in individual prompt builders.
- Learner level defaults to `COLLEGE` for quiz/exam prompts when the user has no saved `learnerLevel`; note and Study Pack content generation must also work when context learner level is null.
- Course/program is omitted from the context block when the user has no saved `courseProgram`.
- In the normal note flow, AI-generated `title`, `subject`, and `tags` must not be persisted before explicit user confirmation.
- When merging AI tags with existing note tags, always deduplicate case-insensitively after trimming whitespace.

### Quiz Generation Rule

- Quick Review comes from the Study Pack quiz generated with static content and should stay lightweight, fast, and course/program-leveled. Per-taker quiz/exam generation remains learner-level aware.
- Challenge Quiz and Adaptive Practice use separate LLM generation flows and must receive learner-level context, defaulting to `COLLEGE` when the user has no saved learner level.
- Local quiz UI development may use `QUIZ_GENERATION_MODE=mock` to stub Challenge Quiz, Adaptive Practice, and Board Exam generation without changing Study Pack generation or the default production LLM path.
- Optional local loading-state testing may add `QUIZ_GENERATION_MOCK_DELAY_MS`, but the default quiz-generation mode must remain real unless explicitly overridden.
- Quick Review must not use the Challenge/Adaptive LLM-generation hard lock or full-screen generation overlay because it does not run an LLM at quiz start.
- Challenge Quiz and Adaptive Practice must reserve a `GENERATING` session before calling the LLM, then transition to `IN_PROGRESS` when the quiz payload is ready or `FAILED` when generation fails.
- Challenge Quiz and Adaptive Practice start flows must be idempotent: return existing `GENERATING` sessions without another LLM call, return existing `IN_PROGRESS` quiz payloads without another LLM call, and allow retry only after `FAILED`.
- While Challenge Quiz or Adaptive Practice generation is active, the UI must disable start controls, difficulty/options controls, app links, sidebar/header navigation, and browser back/refresh through the shared generation lock and native `beforeunload` warning.
- Challenge Quiz and Adaptive Practice reload recovery must check existing session state first: `GENERATING` continues the loading/poll state, `IN_PROGRESS` resumes the quiz, and `FAILED` shows retry.
- Generated quiz JSON contracts must stay strict:
  - exactly 4 choices
  - `answer` must be `A` / `B` / `C` / `D`
  - `explanation` is required
  - `concept` is required
- `MULTI_SELECT` is a plan-agnostic quiz format for Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Teacher Quiz only; do not add it to Board Exam prompts or Board Exam UX.
- Multi-select questions must keep exactly 4 choices, use `correctIndices` with 2-3 correct zero-based indexes, and score all-or-nothing. Keep `correctIndex` populated with `correctIndices[0]` as a legacy fallback.
- Quiz session state must store multi-select answers under `selectedMultiChoices` through `QuizSessionStateUtils`; do not manipulate the session JSON directly in service code.
- `MATCHING` is a plan-agnostic quiz format for Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Teacher Quiz only; do not add it to Board Exam prompts or Board Exam UX.
- Matching groups use the shared `questionGroup` field, must be 2-4 consecutive items with identical 4-choice arrays, and each item remains single-correct with a distinct `correctIndex`.
- Matching answers use the existing `selectedChoices` session key; do not add a separate matching-answer JSONB key.
- Raw LLM quiz output may use answer letters, but canonical stored/shared quiz data must normalize to:
  - `question`
  - `choices`
  - `correctIndex`
  - `explanation`
  - `concept`
- `A` / `B` / `C` / `D` are UI-only labels derived from displayed order and must not be embedded into canonical choice strings.
- Backend quiz normalization must strip leading hardcoded choice prefixes such as `A. `, `B) `, `c. `, and `D) ` from generated and legacy choice strings before validation/storage.
- Quiz sessions must persist selected canonical choice indexes, not display letters or prefixed choice text.
- Compatibility loaders may accept legacy answer text, `answerIndex`, or string-based selected choices, but runtime grading/rendering must normalize them back to canonical indexes before use.
- Runtime grading must compare canonical choice indexes or explicit correctness metadata, never displayed letters or post-shuffle display positions.
- Quantitative subjects should allow computation and problem-solving questions when the note context supports them.
- Computation explanations should show short step-by-step solution flow rather than a one-line answer.

## UI Terminology (Use Consistently)

- `Dashboard`
- `Library`
- `Public Library`
- `Note Detail` (unified Note + Study Pack view)
- `New Note`
- `Generate Study Pack`
- `Make a Copy`
- `Copy to Library`
- `Make Public`
- `Make Private`

Avoid introducing older terms such as `Study Library` or regenerate/overwrite flows.

## Navigation Structure

Keep app shell grouping:

- Main:
  - Dashboard
  - Profile-aware review-workspace label resolved through `getCollectionLabels().navLabel`
  - Library
  - Explore
  - Progress
- Account:
  - Profile
  - Settings
  - Admin (admins only)

## UI Interaction Guardrails

- Keep note cards consistent across Dashboard, Library, and Public Library:
  - entire card click opens note detail
  - note cards stay action-free and rely on Note Detail for management actions
- Public Profile note cards should follow the same whole-card click pattern as Library and Public Library.
- `Library` should expose a direct `Create Note` entry in the header and empty state so users are not forced through `Dashboard` to start a note.
- Note Editor actions:
  - keep `Generate` as the primary CTA and `Save` as the secondary CTA
  - desktop should show actions at the top and bottom of long note forms
  - mobile should keep a floating primary generate button visible while scrolling
  - `/notes/new` stays in create mode with `Save` + `Generate`
  - `/notes/{id}/edit` for Draft notes stays in edit mode with `Save Changes`, `Cancel`, and `Generate`
  - `/notes/{id}/edit` for Study Pack Ready notes keeps metadata editing only and shows `Save Changes`, `Cancel`, and `Make a Copy`
  - edit routes must render `Edit Note` copy, not create-note copy
  - note editor metadata fields are `title`, `courseProgram`, `subject`, `tags`, and `content`
  - subject suggestions must come from persisted note subjects and still allow custom typed values
  - tags remain optional and should include helper guidance rather than hard validation pressure
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
  - `STUDY_PACK_READY`: Edit stays on Note Detail and allows only title/courseProgram/subject/tags
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
  - `You have reached your OCR limit for now. Please try again later or upgrade to Plus or Pro.`
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

## Bulk Material Import Rule

- `POST /notes/import-batch` is the deliberate auto-save exception to the single-file import flow.
- Bulk import creates one owned `DRAFT` note per successfully extracted file and must never auto-generate a Study Pack, set `GENERATING`, call an LLM, or add a new quota category.
- Bulk import must reuse the existing per-file extraction pipeline and its verification, file-size, page/text, OCR usage, and OCR rate-limit enforcement.
- Bulk import orchestration must not run inside a batch-wide transaction; one file failure must be recorded in the response and must not roll back notes already created from other files.
- Bulk import is universal and profile-agnostic; do not add `ProfileType` branching or teacher-only gates to the backend endpoint.



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
- Purchase a paid plan
- Use any LLM-powered feature

Verified users:
- Have full access based on plan (Free, Plus, or Pro)

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
- Library/Public Library flows
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
6. **Taxonomy / enumerated fields must use a shared combobox/dropdown, never a freetext `<input>`.** Course/program, learner level, subject, and target audience are all matched by normalization (e.g. a study plan's `courseProgram` is normalize-matched against the learner's profile value to surface it on the Dashboard); a freetext value that matches no learner silently never appears. Reach for `components/metadata/course-program-combobox.tsx`, `components/notes/subject-combobox.tsx`, or `components/ui/suggestion-combobox.tsx` first. This drift has recurred (Bulk Generate, then the Adoptable Study Plans publish card).

### Sonar / Code Smell Rules (Frontend)

- **Use `toLocaleLowerCase("en")` for subject normalization**: When normalizing note subject strings for comparison (e.g. `normalizeSubjectForMatch`), always use `.toLocaleLowerCase("en")` instead of `.toLowerCase()`. `toLowerCase()` is locale-dependent and can produce inconsistent results across environments. Apply this to every function that lowercases a user-supplied subject or tag string for comparison.
- **Use `globalThis` instead of `window`**: Sonar flags direct `window` access. Replace `window.addEventListener`, `window.removeEventListener`, `window.location`, `window.history`, and any other `window.*` global with the `globalThis.*` equivalent. Apply this fix whenever modifying a file that contains `window.` access outside of type guards.
- **Unknown TypeScript property**: Sonar flags accessing a property that is not in the inferred type of an object. Fix by adding the missing property to the TypeScript interface or type, using `Record<string, T>` when the object is keyed by dynamic strings, or using a type guard. Do not use `as any` to suppress the warning — resolve the underlying type gap. When Sonar reports "Unknown property 'text-sm'" or similar, the property is likely a CSS class key on a plain object; switch to a typed `Record<string, string>` or restructure the object so TypeScript knows the allowed keys.
- **Escape `>` in JSX text content**: Sonar requires bare `>` characters in visible JSX text to be escaped. Use the HTML entity `&gt;` or the JSX expression `{'>'}` instead. This applies only to `>` appearing as readable text between JSX tags, not to JSX syntax angle brackets (`<Component />`) or ternary expressions.

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
- Plus: 50 Study Packs/month
- Plus Challenge Quiz: 25/month
- Plus OCR: backend-configured monthly quota
- Pro: 100 Study Packs/month
- Pro Challenge Quiz: 50/month
- Pro Adaptive Practice: 30/month
- Pro OCR: backend-configured monthly quota
- Adaptive Practice is Pro-only and still quota-limited.
- Weak concepts remain visible to Free users.
- File upload is available on Free, Plus, and Pro.
- Study Pack, Challenge Quiz, and Adaptive Practice quotas are separate from each other.
- OCR usage has its own backend-configured monthly quota by plan.
- Frontend plan limits and feature availability must come from `GET /api/me/plan`, not hardcoded values.
- Settings usage UI should not show OCR counters; OCR remains backend-tracked and enforced.
- OCR limit UX in note import should use a modal:
  - Free: explain OCR is limited and offer `Upgrade`
  - Plus / Pro: explain reset happens on the next billing date
- Expensive OCR and AI generation endpoints must also enforce backend request-rate limits and return `429` with a friendly retry message.

## Billing Provider (Current)

- Active billing provider is `XENDIT`.
- Paid-plan checkout is currently a hosted Xendit invoice flow, not a recurring subscription flow.
- The current manual-renewal billing model is: Monthly checkout grants `30` days and Yearly checkout grants `365` days of paid access for the selected plan.
- Regional pricing is resolved from `CF-IPCountry` and mapped into pricing regions.
- Region pricing config contains localized currency/amounts plus optional intro pricing metadata used for display and eligibility.
- Voucher/promotion rules decide whether intro pricing is shown, but checkout itself stays on the current hosted Xendit invoice flow.
- Intro/first-time subscriber discounts must flow through voucher eligibility and voucher redemption records.
- Paid-plan activation is controlled by webhook-confirmed invoice outcomes only.
- Webhook-confirmed payments must create or extend `subscriptions`; they must not update plan state on `users`.
- If an upgrade starts from Settings/Billing, billing success should send the user to Dashboard instead of back to Settings.
- Success/failure redirect pages may help users return to their previous page, but those redirects never activate a paid plan.
- Xendit webhook statuses currently handled are:
  - `PAID`
  - `FAILED`
  - `EXPIRED`
- Do not create/update webhook registrations dynamically in app code.
- Payment endpoints are:
  - `POST /api/payments/create`
  - `POST /api/webhooks/xendit`
- Webhook processing safety:
  - store provider webhook events in `webhook_events` with unique `(provider, event_id)`
  - duplicate events must return success without reprocessing
  - keep provider transaction inserts idempotent via provider reference IDs
  - reject external or protocol-relative checkout `returnUrl` values
- Billing lifecycle safety jobs:
  - `SubscriptionExpiryJob` (daily): expire overdue active paid subscriptions and downgrade to Free
  - `BillingUsageResetJob` (daily): ensure usage rows exist for the current billing period window

## Dashboard and Library Guardrails

- Dashboard is guidance-first and non-destructive.
- Keep delete actions out of Dashboard.
- Dashboard should personalize section order, CTA emphasis, and labels by `profileType` while reusing the same shared note, quiz, activity, and usage data.
- `STUDENT` dashboard should prioritize `Continue Studying`, weak concepts, recent notes, and quick review.
- `Continue Studying` must show the current note title prominently and include subject plus course/program when available so users can recognize what they are resuming.
- Dashboard continue-study payloads must carry `noteId`, `noteTitle`, `subject`, optional `courseProgram`, and backend-owned `resumeType` in a single API response; do not add follow-up frontend fetches just to label the card.
- `Continue Studying` resume labels must reflect the backend `resumeType` (`Quick Review`, `Challenge Quiz`, `Adaptive Practice`) instead of hardcoding Quick Review copy.
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
- `Focus Areas` should surface top weak concepts for all users, but Adaptive Practice CTA remains Pro-gated through the shared soft paywall for Free and Plus users.
- Keep destructive actions (delete) in Note Detail/Library with explicit confirmation.

## Context Usage Rule
Always read and follow AGENTS.md, SPEC.md, and related feature docs before implementing any task.
Assume these files are the source of truth for architecture and UX decisions.

- docs/architecture for the architecture overview
- docs/features for the context of every feature
- docs/product for the spec and roadmap of the app
- docs/testing for the context of testing of every feature
- docs/ui for the ui design context

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

## Subject Generation Strategy

LLM-generated subjects must be reusable academic subject labels, with no topic suffix.

- Correct: `Biology`, `Physics`, `Mathematics`, `Computer Science`, `English`, `Filipino`, `Civil Engineering`, `Electrical Engineering`, `Nursing`, `Accountancy`, `Criminal Law`
- Incorrect: `Biology – Cell Division`, `Physics: Ohm's Law`, `Mathematics – Derivatives`
- Overly broad umbrella labels such as `Engineering`, `Medicine`, `Business`, and `Law` must be ignored/rejected safely when they come from AI metadata suggestions.
- Topic-level specificity belongs in tags and key concepts, not in subject

Backend enforcement (`SubjectSanitizer.stripSubtopicSuffix`):
- Any separator (" – " or ":") triggers stripping → only the left/domain part is kept
- `"Electrical Engineering – Ohm's Law"` → `"Electrical Engineering"`
- Broad single-word AI suggestions (`Engineering`, `Medicine`, `Law`, `Business`, `Education`) are ignored and must not fail Study Pack generation
- Empty/unusable result after stripping is ignored as missing subject metadata; Study Pack generation continues if core summary/key concept/quiz output is valid

## Study Pack Sanitization

`OpenAiLlmStudyPackService` validates and repairs LLM output before saving:

- **Subject**: max 6 words (`SubjectSanitizer`); invalid or overly broad AI subject suggestions are non-blocking and become no subject suggestion
- **Quiz concept**: max 4 words (`KeyConceptSanitizer.MAX_QUIZ_CONCEPT_WORDS`); filler prefixes stripped before truncation
- **Key concepts**: max 4 words each (`KeyConceptSanitizer.MAX_KEY_CONCEPT_WORDS`); repaired in-place, never block study pack creation due to word-count alone

Sanitizer classes live in `backend/.../util/SubjectSanitizer.java` and `KeyConceptSanitizer.java`.

## Public Library Discovery

Discovery mode layout order (no active filters):
1. Search toolbar with `Filter` and `Sort`
2. one-line `Subjects` rail with `All` and `+ More`
3. one-line `Popular Tags` rail with a dedicated `Browse all` action
4. 🔥 Featured Notes — top 3 eligible notes by quality + engagement
5. 📈 Most Popular — top 5 threshold-qualified notes by copies, then views, then likes (excludes Featured)
6. 🆕 Recently Added — top 5 by createdAt (excludes Featured + Popular)

Backend subject filtering: `GET /notes/public?subject=<value>` — case-insensitive, server-side.

Public Library ranking philosophy:
- Featured = quality + engagement
- Popular = social proof
- Recent = freshness
- Evaluation should stay lightweight: simple signals > complex social systems.

Ranking rules:
- Featured eligibility requires:
  - `visibility = PUBLIC`
  - `studyPackStatus = STUDY_PACK_READY`
  - meaningful summary preview
  - quiz/generated study content
  - non-empty note preview/content
- Featured score:
  - `viewCount + (copyCount * 3) + (likeCount * 2)`
- Featured tie-breakers:
  - `copyCount DESC`
  - `viewCount DESC`
  - `createdAt DESC`
- Likes:
  - authenticated users can toggle one like per public note
  - guests clicking like must see an auth modal instead of a silent failure
  - `Well liked` badge threshold is `likeCount >= 10`
- Popular threshold:
  - `copyCount >= 3` or `viewCount >= 20`
- Popular ordering:
  - `copyCount DESC`
  - `viewCount DESC`
  - `likeCount DESC`
  - `createdAt DESC`
- Recent ordering:
  - `createdAt DESC`
- Preserve the current clean discovery dedupe:
  - Popular excludes Featured
  - Recent excludes Featured and Popular

## UI / UX Responsiveness Guidelines

All UI implementations must be responsive and mobile-friendly by default.

### Requirements

- Components must work across:
  - desktop
  - tablet
  - mobile

- Avoid layout issues such as:
  - overflowing buttons or text
  - broken flex/grid layouts
  - elements exceeding container width

- Use responsive patterns:
  - flexible layouts (flex/grid with gap)
  - wrapping where necessary
  - stacked layouts on smaller screens

- Modal and card components must:
  - adapt to smaller widths
  - maintain readable spacing
  - prevent action button overflow

- Button labels must be concise to support smaller screens

### Principle

Design for mobile-first or mobile-safe behavior, even when implementing desktop UI.

UI should feel clean, usable, and visually stable across screen sizes.

## Product-First UI/UX Principles

These principles apply to all frontend work — landing page, demo, pricing, and in-product features.

### Clarity over feature density

- Show what matters to a student or board exam taker first.
- Do not stack features to make a page look impressive; a shorter, clearer page converts better.
- Visual hierarchy: content > actions > secondary info.
- Avoid competing CTAs on the same screen. One primary action per section.

### Align features with learning outcomes

Every visible feature must connect to a student's goal:

| Feature | Learning outcome |
|---|---|
| Study Pack | Understand and organize notes |
| Quizzes | Test retention and find gaps |
| Adaptive Practice | Reinforce weak concepts |
| Board Exam Mode | Simulate high-stakes exam conditions |
| Exports | Use materials offline or in class |

When writing copy for features, always frame them in terms of what the user gains or achieves — not what the system does.

### Avoid generic AI tool positioning

NoteLib is NOT:
- a general-purpose chatbot
- a one-shot summarizer
- a prompt playground

NoteLib IS:
- a structured study tool for students and board exam takers
- a note-first learning workspace with a repeating review loop
- a system for moving from notes → understanding → exam readiness

Do not write headlines or descriptions that could apply to any AI tool. Always anchor copy to study, retention, and exam preparation.

### Demo is a conversion tool

The `/demo` page is the strongest conversion driver on the site. Treat it as a guided learning experience, not a feature preview:
- Each step should feel like progress toward a learning goal.
- The quiz section must feel like a real exam mini-experience (interactive, not just showing answers).
- End the demo with a clear CTA that connects the experience to real use.

### Pricing copy must show progression

FREE → PLUS → PRO should feel like natural steps for a growing student:
- Free = getting started, not "limited"
- Plus = consistent, regular review
- Pro = serious exam preparation

Avoid describing lower plans as crippled. Describe them as suited for their stage.

## Prompting Mode Guidelines

Use two prompt modes depending on the type of task.

### 1. Long Prompt Mode
Use Long Prompt Mode when:
- implementing a new feature
- doing a non-trivial refactor
- changing data flow, persistence, routing, or architecture
- updating multiple related docs/specs
- the task has higher risk or more ambiguity

Long prompts should usually include:
- TASK
- GOAL
- CONTEXT
- implementation scope
- audit step if needed
- documentation updates
- testing expectations
- success criteria

### 2. Short Prompt Mode
Use Short Prompt Mode when:
- polishing UI
- fixing small bugs
- making follow-up refinements
- improving copy, spacing, labels, or interaction details
- the implementation is incremental and low-risk

Short prompts should usually include only:
- TASK
- GOAL
- short context
- implementation bullets
- essential docs/tests only if relevant
- success criteria

### Rule
Explicitly state the prompt mode in every prompt:
- Prompt mode: Long
- Prompt mode: Short

Default to Short Prompt Mode for incremental follow-ups unless the task clearly introduces a new feature or broader architectural change.

## Anti-Drift Rules (v0.12.0+)

These rules exist to prevent the most common forms of context drift across AI coding sessions. Read them before starting any task.

### Version Management Anti-Drift

- The current version is `v0.67.0`. Always keep `backend/pom.xml`, `frontend/package.json`, `RELEASES.md`, `README.md`, `ROADMAP.md`, `AGENTS.md`, and `CLAUDE.md` version references in sync when bumping a version.
- Do not change the version number during a feature implementation — only bump the version as a dedicated version-bump task.
- `RELEASES.md` is the canonical release log. Add new sections at the top. Do not delete old release entries.
- `docs/product/ROADMAP.md` is the canonical roadmap. The current release section must reflect the in-progress version.

### LLM Fan-Out Anti-Drift

- When introducing new LLM fan-out (`CompletableFuture.supplyAsync` patterns), use `llmParallelTaskExecutor`, never reuse the executor that dispatched the parent task — see `OpenAiLlmStudyPackService.generateLongExamParallel` for the canonical shape.

### Learner Level vs Course/Program Anti-Drift

- **Learner Level** and **Course/Program** are separate concerns. Never merge them into a single field, a single UI input, or a single LLM prompt variable.
- Static **note content and Study Pack content are leveled by course/program**, including depth, vocabulary, terminology, examples, and the embedded Quick Review. Shared/copied content must never be calibrated from a per-user learner level.
- `learnerLevel` controls taker-specific quiz/exam difficulty, explanation depth, vocabulary, and question complexity, and remains in `StudyPackGenerationContext` for exam-question pool pre-warm and `sameLearnerLevel` gating.
- Quiz/exam prompts receive learner level and course/program separately through `buildLearnerContextBlock()`; content prompts use the content-context builder and omit learner level.
- Study Pack, Challenge Quiz, Board Exam, and Adaptive Practice generation must use the shared note-first Course/Program resolver: note `courseProgram` wins, profile `courseProgram` is fallback only.
- Learner Level is required at the user/profile level for completed accounts, but generation context remains nullable for legacy/best-effort paths. Never reintroduce per-note learner level columns. Teacher quiz modal's `targetLearnerLevel` is the only per-generation override.
- See `docs/features/profile-learning-context.md` for the full rule set.

### Upgrade CTA Anti-Drift

- Never hardcode a plan name as the universal upgrade CTA (e.g., never just `Go Pro` for all users).
- Always use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts` to resolve plan-aware CTAs.
- Upgrade CTAs that drive in-app plan selection navigate to `/settings?section=plans`, not `/pricing`.
- `/pricing` is the public marketing surface only — linked from navbar and landing page, not from in-app paywalls.

### Analytics Event Anti-Drift

- Never use a string literal for an analytics event name without first adding it to the `AnalyticsEventType` union in `frontend/lib/api.ts`.
- All analytics calls are fire-and-forget (`void`). Do not `await` them or let failures block the primary flow.
- Analytics events fire after the surrounding transaction commits through the `AFTER_COMMIT` event listener, and `analytics_events.user_id` has no hard FK to `users(id)`. Never reintroduce that FK or persist analytics mid-transaction.
- Do not duplicate event tracking: `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, and `ADAPTIVE_PRACTICE_COMPLETED` are fired once per quiz completion, not per question or per partial step.

### Content Moderation Anti-Drift

- `ContentModerationService` applies token-based exact matching. It does NOT use substring matching — this is intentional to avoid false positives on words like "classic", "Damascus", "passage".
- Dictionary files live in `backend/src/main/resources/moderation/banned_words_*.txt`. Add new languages by dropping a new file — do not modify the service loader.
- `validateOrThrow()` is the integration point for validation boundaries. Call it after blank/length checks, not instead of them.
- The service allows all content when no dictionary files are loaded. Never silently skip loading errors in production.

### Paywall and Limit Surface Anti-Drift

- All paywall copy is action-aware. Never show generic "You've reached your limit" — always name the blocked action.
- `PaywallModal` resolves copy through `resolvePaywallAction(variant)` → `FREE_PAYWALL_CONTENT[action]`.
- `StudyPackLimitModal`, `NearLimitBanner`, and `PostSuccessUpgradeNudge` must use plan-aware CTAs, not hardcoded upgrade labels.
- When Study Pack credits reach `2` or `1`, show a `NearLimitBanner` — do not disable the Generate button.
- When Study Pack credits reach `0`, show a limit modal on click — do not disable the Generate button.

### Onboarding Anti-Drift

- Onboarding is one-way. Do not add a Back button to Step 4 (Study Pack generation) or Step 5 (Completion).
- When Study Pack generation is active, hide the Back button and replace notice copy with `Your Study Pack is being created. This step can't be undone.`
- When the Study Pack limit is reached during onboarding, bump `currentStep` to 5 with `studyPackLimitReached=true`. Step 5 renders the limit-reached layout. This reuses the existing `completeOnboarding` useEffect — do not add a separate completion trigger.
- `handleStartStudyPack()` must check `draft.noteId` before creating a note (idempotency rule).

### Quiz Generation Anti-Drift

- `buildLearnerContextBlock()` is the single formatting point for learner level + course/program in quiz/exam prompts. Static note and Study Pack prompts use the content-context builder, which deliberately excludes learner level.
- Quiz result statistics (score, performance level, weak concepts) are derived from stored session data only. No LLM calls for stats.
- Weak concept threshold is `< 60%` accuracy (`WEAK_CONCEPT_THRESHOLD`). Do not change this without a test covering the boundary.
- `lib/challenge-quiz-results.ts` owns quiz result computation utilities. Reuse them; do not duplicate the logic.

### Plan Configuration Anti-Drift

- `frontend/src/config/plans.ts` is the canonical source for plan names, CTA labels, and feature lists across all frontend surfaces.
- `docs/product/PLANS.md` is the canonical plan reference document.
- `frontend/lib/pricing-config.ts` owns runtime numeric limits.
- When plan limits or copy change, update all three. Do not update one and leave the others stale.

### Public Library Conversion Anti-Drift

- Public note pages are acquisition surfaces, not only app detail screens. Treat them as such when implementing any public note detail changes.
- The page order must be: teach → interact → convert. Do not move CTAs above the learning experience.
- `Share` must always be visible on public note pages regardless of auth state. Never hide it behind a login gate.
- Mini quiz preview is client-side only for anonymous users. Do not create a quiz session, persist a score, or call the quiz session API for unauthenticated users.
- The signup gate on the mini quiz continuation must appear only after the visitor has answered at least one question — not on page load.
- The soft conversion CTA (`Turn your own notes into something like this`) must appear before `Copy to My Library` and `Generate Study Pack` in the visual hierarchy.
- After a public visitor signs up from a public note page, route them toward copying that note or creating their own Study Pack — not back to the same public note preview.
- Generated note formatting improvements for public pages must not change how content is stored in the database or how authenticated note detail renders it.
- Before implementing any public note detail change, confirm the current RELEASES.md section and `docs/features/public-library.md` Public Note Detail section are current.

### v0.14.0 Post-Ship Anti-Drift

All v0.14.0 planned scope has shipped. Do not reopen these decisions:

- **Multi-note Long Exam** ships via `LongExamService.resolveAdditionalStudyPackIds` / `resolveSourceNoteRefs` / `generateQuizForSources`. It reuses the existing session lifecycle with no new persistence aggregate and no new `QuickReviewSessionMode` enum value. `LongExamStartRequest` accepts an optional `additionalStudyPackIds` list (max 3). Do not alter the proportional question distribution or subject-match validation without a product decision.
- **Interview Practice shipped as a sub-mode of Adaptive Practice** (JSONB `subMode: "INTERVIEW"` on the `ADAPTIVE` discriminator). The 5-mode contract is preserved. It uses a dedicated 10/month Pro-only quota, `gpt-4.1-mini` for critique calls, and `gpt-4.1` for generation. Do not revert or alter this cost split.
- When opening v0.15.1 work, run the release kickoff checklist in `CLAUDE.md` before the first feature commit.
