# AGENTS.md - NoteLib

You are an AI coding agent helping implement NoteLib.
Follow these rules to keep the codebase consistent and shippable.

Rebrand note: StudySnap has been renamed to NoteLib. Keep existing database schema/table names unless explicitly requested.

Current documentation baseline:

- `v0.5.0 - Public Profiles & Public Notes`
- next planned milestone: `v0.6.0 - Landing Revamp & Positioning`
- following milestone: `v0.7.0 - Board Exam Mode`

When working on a feature, always check the corresponding document under `docs/features/`.

## Product Summary

NoteLib converts notes into structured study outputs and review workflows.

Core loop:

`Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat`

## Implementation Workflow Rules

- After every completed prompt/task that results in code or doc changes, always include a suggested commit message in the final response.
- Keep the suggested commit message aligned with the repo's current style: concise subject line plus 3-5 high-signal bullets when useful.

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

- Do not regenerate/overwrite generated content on the same Note.
- Use `Make a Copy` only.
- Copy includes: `title`, `courseProgram`, `subject`, `tags`, `content`.
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
- When a user has `2` or `1` Study Packs remaining, show a non-blocking monthly-limit banner on Dashboard, Note Detail, and Study Pack generation surfaces.
- When Study Pack remaining reaches `0`, keep `Generate Study Pack` enabled and show a student-friendly monthly-limit modal on click instead of disabling the action.
- Upgrade messaging should position Premium as an exam-preparation and mastery tool for students.
- Pre-launch modal copy should make it clear that payments are still being enabled and that users can join the waitlist for launch access.
- Dashboard should show a Free-only upgrade card highlighting Challenge Quiz, Adaptive Practice, and the `100` Study Pack Premium limit.
- Pricing page should clearly compare Free vs Premium with localized backend pricing and student-oriented value messaging.

### Study Pack Usage Rule

- Study Pack enforcement, warning banners, and remaining-credit UI must use the same backend-resolved usage calculation.
- Allow Study Pack generation only when `used < limit`; block when `used >= limit`.
- Study Pack usage increments only after a successful Study Pack is persisted.
- Saving a note, opening generation surfaces, failed generations, and failed retries must not consume Study Pack quota.
- Frontend warning/blocking surfaces should use `GET /api/me/plan` remaining values and must not recalculate quota from local note lists.

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
- Keep the home page focused on hero, how-it-works, features, Free vs Premium pricing, demo access, and signup CTA.
- Demo access must be available without signup.
- Public Library should be treated as a public discovery feature and must remain accessible without login.
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

- Onboarding is active again for all verified users, not only Premium users.
- Onboarding should happen once after email verification / first verified entry into the app.
- Onboarding must stay short and reuse the existing step flow.
- Preferences onboarding order is:
  - `Profile Type`
  - `Learning Profile`
  - `Learning Style` (`engagementMode`)
  - `Study Reminder Frequency`
  - `Exam Date` only for `BOARD_EXAM`
- `Learning Profile` onboarding collects:
  - required `learnerLevel`
  - required `courseProgram`
  - optional `bio`
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
  - `Learning Profile`
  - `Profile Type`
  - `Public Profile Link`
- Identity uses:
  - `firstName`
  - `lastName`
  - `displayName`
  - `email`
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
- Learning Profile combobox-style inputs should reuse the same input-plus-suggestions pattern as the Note Editor `Subject` field.
- Learning Profile `Course / Program` helper text should adapt to `learnerLevel` so examples match the learner's current study stage.
- Saving `Learning Profile` requires both fields and should show:
  - `Please select your learner level.`
  - `Please select or enter your course / program.`
- Profile save buttons must remain section-specific rather than global.
- Do not move `Learning Style` or study-reminder preferences into `Profile`.
- Email changes must write `pendingEmail` first and only update `email` after verification.

### Public vs Private Profile Separation Rule

- `Public Profile` (`/public/profile/{userId}`) is the user's public learning-portfolio surface.
  - Shareable, view-only to non-owners.
  - Shows `displayName`, `bio`, `learnerLevel`, `courseProgram`, `profileType`, public metrics, and public notes only.
  - Owner controls (`Edit Profile`, `Share Profile`, visibility toggle) are on the Public Profile page only.
- `Profile Settings` (`/profile`) is the private account editing surface.
  - Editable identity, learning profile, and profile type.
  - Accessed via the `Edit Profile` button on the Public Profile page.
  - Does not own public-profile visibility or sharing.
- The authenticated app shell avatar dropdown must always offer:
  - `My Profile` → `/public/profile/{userId}` (public identity page)
  - `Settings` → `/settings` (account and app settings)
  - `Sign Out`
- The sidebar Account section must use:
  - `Profile` → `/public/profile/{userId}` (same as `My Profile` in the avatar dropdown)
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

- Library is note-based and contains the current user's notes (Draft + Study Pack Ready).
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
- Private Library filters should support:
  - `Course / Program`
  - `Subject`
  - `Tags`
  - `Study Pack Ready`
  - `Draft`
  - `Public`
  - `Private`
- Public Library filters should support:
  - `Course / Program`
  - `Learner Level` when public note results expose it
  - `Subject`
  - `Tags`
  - `By You`
  - `Official`
  - `Community`
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
- Public Library canonical SEO index route is `/public/library`; app-shell `/library/public` is not the canonical indexed route.
- Public subject listing pages use `/public/library/{subject}` and must reuse the existing route/data helpers rather than introducing parallel subject-page implementations.
- Public SEO note pages use `/public/library/{subject}/{slug}` as the canonical route.
- Public SEO pages must stay accessible without login and indexable only for `PUBLIC` notes.
- Public landing page should emit JSON-LD `WebSite` schema.
- Public Library index should emit JSON-LD `CollectionPage` schema.
- Public SEO note pages should emit JSON-LD `Article` schema using real note data only.
- `robots.txt` must allow public crawling and disallow authenticated/private app areas such as `/dashboard`, `/library`, `/notes`, `/settings`, `/admin`, and `/api`.
- `sitemap.xml` must include only public SEO-safe routes: `/`, `/privacy`, `/terms`, `/public/library`, canonical public subject URLs, and canonical public note URLs.
- Private notes must never be exposed through the public SEO route.
- Copying a public note must preserve attribution via `copiedFromNoteId` and `copiedFromUserId`.

### Card Interaction Rule

- Library cards, Public Library cards, and Public Profile cards must use a consistent interaction model.
- The whole card should be clickable to open the detail page.
- Do not add inline action buttons or note-card context menus to note cards.
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
- **Button hierarchy** on result screens:
  - Primary: next learning action (`Practice Weak Concepts` when available, `Practice Again` when Quick Review needs recovery and Adaptive Practice is locked/unavailable, `Start Another Challenge` when Challenge has no weak concepts, `Start Challenge Quiz` after strong Quick Review results, or `Generate New Set` for Adaptive Practice)
  - Secondary: review/repeat/support actions (`Review Answers`, secondary `Practice Again`, secondary `Start Another Challenge`, locked `Unlock Practice Weak Concepts`)
  - Navigation: `← Back to Note` link below
- Edge states such as empty quiz data, monthly limits, unavailable sessions, or missing weak-area labels must keep a clear next step and use text-link navigation rather than `Back to Note` buttons.
- **Confidence feedback** (Quick Review only): after selecting, option buttons are replaced by a badge — `🟢 Confident`, `🟡 Improving`, `🔴 Needs Practice`; "Thanks for the feedback." text is removed
- **Adaptive Practice completion**: "Generate New Set" is always the primary button; `← Back to Note` link below
- **Review Answers**: Quick Review, Challenge Quiz, and Adaptive Practice must use the shared post-quiz review pattern showing question text, selected answer, correct answer, explanation, and concept chip.
- Review Answers answer states must stay consistent: correct answer uses restrained green styling, incorrect selected answer uses restrained red styling, neutral distractors stay quiet, and selected-correct answers show both `Your answer` and `Correct answer`.
- Review Answers should use stored quiz/session data (`question`, `choices`, selected canonical choice indexes, `correctIndex`, `explanation`, `concept`) so completed-session history/review can reuse the same structure later.
- Motivation/feedback messages use `mapPerformanceLevel` thresholds for consistency (Excellent / Good / Fair / Needs Improvement)
- While a quiz session is active, replace normal header back navigation with active-session text plus `Leave Quiz`; navigation away must open the shared `Leave quiz?` confirmation instead of leaving immediately.
- The shared leave confirmation copy is `You are currently in an active quiz. Leaving will forfeit your progress.` with `Stay` and `Leave Quiz` actions.
- Confirmed leaves mark the session `FORFEITED`; Challenge Quiz and Adaptive Practice forfeits must not refund quiz credits or mark the session completed.

### Challenge Quiz — Exam Mode Rule

- Challenge Quiz must behave as an exam: **no correctness feedback during answering**.
- Do not render "Correct" / "Incorrect" labels, green/red highlights, or explanations while the quiz is in progress.
- Selected answers show a neutral exam-style visual state (blue border/background) only.
- The Challenge Quiz start screen must disable difficulty controls and the Start button immediately after Start is clicked.
- Duplicate Challenge Quiz start requests must be blocked while quiz initialization is in flight.
- All result calculations (score, performance level, concept breakdown, weak concepts) must be derived from quiz session data — **no LLM calls for statistics**.
- Use the utility functions in `lib/challenge-quiz-results.ts` for testable result computation.
- Performance level thresholds: 90–100 → Excellent, 75–89 → Good, 50–74 → Fair, 0–49 → Needs Improvement.
- Weak concept threshold: accuracy < 60% (`WEAK_CONCEPT_THRESHOLD`).
- The "Practice Weak Concepts" CTA must only appear when `weakConcepts.length > 0` and must link to Adaptive Practice.

### Note Card Consistency Rule

- Library, Public Library, Public Profile, and public subject listing pages should reuse the shared note-card layout.
- Note cards must use a shared layout and component across note-list pages; Public Library may add subtle discovery metadata such as views and copies, but the base card structure must remain consistent.
- Shared note-card content order is:
  - TOP ROW: Subject badge + Course/Program badge (neutral/gray) — above title
  - Title (with optional private-library visibility icon `Globe`/`Lock` trailing)
  - Study Pack Ready badge (green) — below title, only when applicable
  - Quality badges (High Quality, Popular) — below title alongside stateBadge
  - `Note Preview`
  - `Summary Preview`
  - Tags
  - subtle discovery metrics row (`views`, `copies`) when that surface has them
- `SharedNoteCard` props: `courseProgram` (neutral gray badge above title), `stateBadge` (Study Pack Ready, rendered below title), `metadataBadges` (quality badges)
- The "New" badge has been removed; quality badges are High Quality and Popular only
- `Note Preview` comes from note content and `Summary Preview` comes from generated Study Pack summary.
- `Note Preview` should read as the primary preview and `Summary Preview` should stay secondary.
- If no generated summary exists, show `No summary available yet.`
- Use clamped preview text so card heights stay consistent across listing grids.
- Do not render `Public` / `Private` as a large badge on note cards; use a subtle icon instead when the visibility distinction matters.

### Page Responsibility Rule

- Dashboard = what to do now
- Library = private workspace
- Public Library = discovery
- Public Profile = public showcase
- Profile = identity
- Settings = app preferences
- Do not merge responsibilities casually.

### Auth Redirect Rule

- If a session expires while the user is inside authenticated app pages, login should return them to that interrupted page through the explicit `redirect` query.
- If a logged-out user tries to open a protected route, login should return them to that requested protected page through the explicit `redirect` query.
- Manual login from public pages should land on `Dashboard`.
- Do not send users back to public marketing or discovery pages automatically after login unless a protected-route redirect explicitly requires it.

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
- Back links appear on sub-pages only. Main pages (Dashboard, Library, Public Library, My Profile, Settings) must NOT have a back link.
- Back navigation always uses explicit routing (`href` prop on `BackLink`) — never `router.back()`.
- Back link label is the destination page name only — do NOT use "Back to X" or "Back" alone.
- My Profile (owner's own public profile) is a main page — no back link.
- Non-owner viewing another user's public profile: `<BackLink href="/library/public" label="Public Library" />`.
- Inline card action buttons (quiz error/limit states etc.) should use short destination labels (`Note`, `Library`) — not "Back to Note" or "Back to Library".
- Back link is positioned above the page header card, left-aligned.

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
- Metadata hierarchy should stay:
  - `courseProgram` -> top-level track/domain
  - `subject` -> reusable academic topic
  - `tags` -> fine-grained keywords
- `learnerLevel` is required during onboarding but remains nullable in storage for pre-existing users.
- `courseProgram` is required during onboarding and later Learning Profile saves, but remains nullable in storage for pre-existing users until they update it.
- backend generation context may carry `learnerLevel`, `courseProgram`, `subject`, and `tags` for future prompt tuning without changing current UI behavior.

### Quiz Generation Rule

- Quick Review comes from the Study Pack quiz generated during note generation and should stay lightweight, fast, and learner-level aware.
- Challenge Quiz and Adaptive Practice use separate LLM generation flows and must receive learner-level context, defaulting to `COLLEGE` when the user has no saved learner level.
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
  - Library
  - Public Library
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
- `Focus Areas` should surface top weak concepts for all users, but Adaptive Practice CTA remains Premium-gated through the shared soft paywall for Free users.
- Keep destructive actions (delete) in Note Detail/Library with explicit confirmation.

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

LLM-generated subjects must be **domain-only** — a broad academic domain or curriculum category, no topic suffix.

- Correct: `Biology`, `Physics`, `Mathematics`, `Computer Science`, `English`, `Filipino`, `Engineering`, `Medicine`, `Law`
- Incorrect: `Biology – Cell Division`, `Physics: Ohm's Law`, `Mathematics – Derivatives`
- Topic-level specificity belongs in tags and key concepts, not in subject

Backend enforcement (`SubjectSanitizer.stripSubtopicSuffix`):
- Any separator (" – " or ":") triggers stripping → only the left/domain part is kept
- `"Electrical Engineering – Ohm's Law"` → `"Electrical Engineering"`
- Broad single-word domains (`Engineering`, `Medicine`, `Law`, `Business`, `Education`) are now valid and accepted without retry
- Empty/unusable result after stripping → throws `LLM_INVALID_OUTPUT`

## Study Pack Sanitization

`OpenAiLlmStudyPackService` validates and repairs LLM output before saving:

- **Subject**: max 6 words (`SubjectSanitizer`); overly-broad or course-echo subjects cause retry
- **Quiz concept**: max 4 words (`KeyConceptSanitizer.MAX_QUIZ_CONCEPT_WORDS`); filler prefixes stripped before truncation
- **Key concepts**: max 4 words each (`KeyConceptSanitizer.MAX_KEY_CONCEPT_WORDS`); repaired in-place, never block study pack creation due to word-count alone

Sanitizer classes live in `backend/.../util/SubjectSanitizer.java` and `KeyConceptSanitizer.java`.

## Public Library Discovery

Discovery mode layout order (no active filters):
1. 📚 Browse by Subject — top 8 subjects by note count (appears ABOVE note sections)
2. 🔥 Featured Notes — top 6 by engagement score
3. 📈 Most Popular — top 6 by copies (excludes Featured)
4. 🆕 Recently Added — top 6 by createdAt (excludes Featured + Popular)

Backend subject filtering: `GET /notes/public?subject=<value>` — case-insensitive, server-side.
