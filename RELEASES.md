# RELEASES.md - NoteLib

## v0.6.0 - Landing Revamp & Positioning (In Progress)

### New Features

- Landing page now frames NoteLib as a notes library and study workspace, not just a one-time quiz generator.
- Public marketing navigation now exposes `Home`, `Public Library`, `Learn`, `Pricing`, `Login`, and `Get Started`.
- NoteLib now has a standardized favicon and app-icon set based on the NL monogram for desktop, mobile, and home-screen usage.

### Improvements

- Major action buttons now keep icon + text labels on mobile across the app’s shared action surfaces instead of collapsing to icon-only.
- Private Note Detail `Summary` and `Quiz` tabs now keep text labels on mobile for clearer view switching.
- Landing page now positions NoteLib as a notes library and study workspace first, with stronger Public Library and active-recall messaging.
- Public Library is now promoted directly from the landing page as a discovery surface that stays accessible without login.
- The landing page now integrates the Learn / active-recall message so new users understand the study method, not only the generation workflow.
- Landing page SEO title, meta description, and Open Graph metadata now align with the notes-library positioning update.
- Pricing no longer treats Public Library as a paid-plan feature.
- Theme toggle is now available on the shared public navbar and syncs with a persisted user theme preference for authenticated users.
- Navbar and app-shell logos now use the NL monogram, while marketing headers and the public footer use the full NoteLib wordmark.
- The Open Graph image now uses the standardized NoteLib branding, notes-and-lightning illustration, and notes-library messaging.

### Fixes

- Auth redirect logic now returns users to interrupted protected pages through explicit redirect intent while sending manual public-page logins to `Dashboard`.
- Login-page auth messaging now distinguishes `session_expired`, `logged_out`, and `auth_required` so manual logout no longer shows the expired-session warning.

### Technical Changes

- Shared responsive action components now default to mobile icon + text labels, with explicit opt-out reserved for true icon-only utility controls.
- Added shared brand-asset components for the monogram, full logo, and product icon, plus a local OG-image render pipeline and web manifest for the public icon set.

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
