# RELEASES.md - NoteLib

## v0.7.0 - Learning & Metadata Foundation (In Progress)

### New Features

- User profiles now support `Learner Level` plus optional `Course / Program` as part of the learning-profile foundation.
- Onboarding now includes a dedicated `Learning Profile` step that collects learner level, optional course/program, and optional bio.
- Notes now support optional per-note `Course / Program`, defaulted from the user's profile and editable per note.
- Note metadata suggestions now use a shared field-level AI review modal for `title`, `subject`, and `tags`.

### Improvements

- Private Profile now separates `Identity`, `Learning Profile`, and `Profile Type` into distinct saveable cards.
- Public Profile can now show learner level and course/program when the owner chooses to provide them.
- Learner-level and course/program inputs now reuse the same subject-style combobox UX as the Note Editor `Subject` field.
- Fixed-option learner-level comboboxes now snap back to the last valid saved value if a user types an unsupported option and closes the field.
- Note Editor now includes `Course / Program`, subject autocomplete, optional tags guidance, and the same metadata shape in both create and edit modes.
- Saved custom subjects now feed future autocomplete suggestions through the existing distinct-subject backend source.
- AI-generated subjects now bias toward more specific academic library labels instead of broad catch-all categories.

### Technical Changes

- Added `users.learner_level` and `users.course_program` with backward-compatible nullable storage for existing users.
- Backend Study Pack generation now prepares learner-level and course/program metadata in generation context for future prompt tuning, alongside note subject and tags.
- Refactored the OpenAI Study Pack service to share request/response/error handling across Study Pack, study-tip, and quiz generation flows, and added direct unit coverage for the refactored service.
- Added `notes.course_program` plus note-service create/update/copy handling so note metadata can diverge from the profile default when needed.

### Fixes

- Restored distinct Note Editor create vs edit behavior so existing notes now render `Edit Note` copy, correct edit-mode actions, and the generated-note content lock without falling back to create-note messaging.

## v0.6.0 - Landing Revamp & Positioning (In Progress)

### New Features

- Landing page now frames NoteLib as a notes library and study workspace, not just a one-time quiz generator.
- Public marketing navigation now exposes `Home`, `Public Library`, `Learn`, `Pricing`, `Login`, and `Get Started`.
- NoteLib now has a standardized favicon and app-icon set based on the NL monogram for desktop, mobile, and home-screen usage.

### Improvements

- Major action buttons now keep icon + text labels on mobile across the app’s shared action surfaces instead of collapsing to icon-only.
- Profile now supports a short bio on the private identity page, and Public Profile now renders that bio with avatar/initial styling and derived subject chips.
- Public Profile now uses a page-level `Back` action above the header card, based on navigation history instead of a hardcoded return link to Public Library.
- Private Library and Public Library now share the same `Search`, `Filter`, `Sort`, notes-list structure, with mobile-friendly filter/sort sheets instead of always-visible controls.
- Library, Public Library, and Public Profile note cards now stay action-free preview surfaces so note management happens consistently in Note Detail.
- Private Note Detail `Summary` and `Quiz` tabs now keep text labels on mobile for clearer view switching.
- Landing page now positions NoteLib as a notes library and study workspace first, with stronger Public Library and active-recall messaging.
- Public Library is now promoted directly from the landing page as a discovery surface that stays accessible without login.
- The landing page now integrates the Learn / active-recall message so new users understand the study method, not only the generation workflow.
- Learn article pages now use a consistent content-marketing structure with introduction, summary, key concepts, sample practice questions, and a bottom account-creation CTA.
- Landing page SEO title, meta description, and Open Graph metadata now align with the notes-library positioning update.
- Pricing page messaging now frames NoteLib as a notes library plus review workflow, with Free/Premium copy aligned around core note creation and heavier exam review periods.
- Pricing page now includes a `Why Go Premium` section that explains Premium in terms of serious review, practice, and exam preparation rather than only limits.
- Pricing no longer treats Public Library as a paid-plan feature.
- Theme toggle is now available on the shared public navbar and syncs with a persisted user theme preference for authenticated users.
- Navbar and app-shell logos now use the NL monogram, while marketing headers and the public footer use the full NoteLib wordmark.
- The Open Graph image now uses the standardized NoteLib branding, notes-and-lightning illustration, and notes-library messaging.
- Study Pack generation surfaces now use student-friendly monthly-limit banners and plan-specific limit modals for both Free and Premium instead of relying on disabled generate actions.
- Public Library now supports discovery sorting by newest, most copied, most shared, and most viewed.
- Public note detail now uses a stronger copy-first growth CTA for non-owners, including a handoff into their own Library note for generation.

### Fixes

- Auth redirect logic now returns users to interrupted protected pages through explicit redirect intent while sending manual public-page logins to `Dashboard`.
- Login-page auth messaging now distinguishes `session_expired`, `logged_out`, and `auth_required` so manual logout no longer shows the expired-session warning.
- Manual logout now suppresses late expired-session redirects from in-flight protected requests so logout messaging stays neutral.
- Study Pack limit enforcement and usage warnings now use the same effective usage calculation so users are no longer told they have credit left while generation is already blocked.
- Free-plan near-limit messaging now shows the actual remaining Study Pack count instead of a generic warning.
- Note Detail generation now applies the same title/subject/tag suggestion flow as Create Note.
- Note Detail tab switching no longer refetches the note or snaps long pages back to the top when `?tab=` changes.
- Mobile Note Editor no longer lets the global `Send Feedback` launcher overlap the primary Generate CTA.
- Library-style note cards no longer mix management menus into preview surfaces, avoiding conflicting card-navigation behavior.

### Technical Changes

- Shared responsive action components now default to mobile icon + text labels, with explicit opt-out reserved for true icon-only utility controls.
- Added shared library toolbar and sheet components so private/public library controls stay consistent across desktop and mobile.
- Added shared brand-asset components for the monogram, full logo, and product icon, plus a local OG-image render pipeline and web manifest for the public icon set.
- Added a shared backend Study Pack usage resolver so plan summary and generation-limit enforcement stay synchronized across services.

## v0.5.0 - Public Profiles & Public Notes

Public Profile:

- Public profile page at `/public/profile/{userId}`
- Public identity uses `displayName`; public pages never show email
- Public profile shows `Profile Type`, public-note stats, and total copies
- Public profile visibility can be turned `On` or `Off`
- Owner-only public-page controls live on Public Profile:
  - `Edit Profile`
  - `Share Profile`
  - Public visibility badge/dropdown
- Non-owners can view/share public profiles only when the profile is public

Public Notes:

- Public notes appear in Public Library and Public Profile
- Public author labels are viewer-relative:
  - `By You`
  - `By NoteLib` with `Official`
  - `By {Display Name}`
- Public author labels link to Public Profile
- Public note detail remains read/copy/share only
- Public note copying preserves attribution to the source note and creator

UI and UX:

- Shared note-card layout across Library, Public Library, Public Profile, and public subject pages
- Whole-card click behavior across library-style note cards
- Removed redundant `Open Note` buttons from public showcase/discovery cards
- Shared cards now show clamped `Note Preview` plus `Summary Preview`
- Private Note Detail now uses underline tabs for `Summary` and `Quiz`
- Icon usage is standardized across navigation and common actions
- Quick Review, Challenge Quiz, and Adaptive Practice use distinct icons
- Action buttons now follow a shared responsive desktop/mobile pattern
- Dark-mode outline buttons use higher-contrast borders, lighter text, and clearer hover states
- Profile page is split into Display Name, Identity, and Profile Type cards with per-section save actions
- Public profile controls were moved off `/profile` and onto the Public Profile page
- Auth recovery now returns users to their interrupted or last visited page after login instead of always forcing `Dashboard`

Documentation baseline:

- `v0.5.0` is the documentation lock point for Public Profiles and Public Notes
- next planned milestone is `v0.6.0 - Landing Revamp & Positioning`

## v0.4.0 - Profile-Based Experience & UX

- Profile identity management
- Email change verification
- Onboarding per profile type
- Personalized dashboards
- Teacher workflow and quiz-first note creation
- Note editor UX improvements across desktop and mobile
- First-time activation flow from verification through first Study Pack and first quiz guidance
