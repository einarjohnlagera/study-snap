# public-profile.md - NoteLib Feature Context

## Goal

Public Profile is the public showcase surface for one creator's public identity and public notes.

## Key Files

**Backend**
- `backend/src/main/java/com/studysnap/backend/controller/PublicProfileController.java` — `GET /public/creator/{username}`, `GET /public/profile/{userId}` (legacy compat), `PUT /users/profile/public-visibility`
- `backend/src/main/java/com/studysnap/backend/service/PublicProfileService.java` — profile resolution; public notes aggregation (capped at 8, sorted copies→views→shares→title); metric aggregation (`totalCopies`, `totalViews`, `totalShares`, `totalProfileShares`); public subject-count aggregation
- `backend/src/main/java/com/studysnap/backend/dto/PublicProfileResponse.java` — response shape: `displayName`, `username`, `publicNotesCount`, metrics, `notesBySubject`, `totalPublicSubjectCount`, capped note list
- `backend/src/main/java/com/studysnap/backend/controller/CreatorImpactController.java` — authenticated self-only `GET /creator-impact/me`; never accepts another creator id
- `backend/src/main/java/com/studysnap/backend/service/CreatorImpactService.java` — owner-private completed-session learner aggregation and per-note impact breakdown

**Frontend**
- `frontend/components/public/public-profile-page-client.tsx` — all public profile UI: header metrics, Learning Focus section (public note scope line, `learningFocusSummary`, subject chips), note grid, "View all notes →" link (v0.21.0), owner controls (Edit / Share / Visibility toggle)
- `frontend/app/public/creator/[username]/page.tsx` — server component entry for username-based route
- `frontend/app/public/profile/[userId]/page.tsx` — legacy `userId` route for backward-compat; resolves internally
- `frontend/lib/server-public-profiles.ts` — server-side `getPublicCreatorProfile(username)` fetch helper
- `frontend/lib/api.ts` — `getPublicCreatorProfile(username)`, `getPublicProfile(userId)` (legacy), `getCreatorImpact()`, `updatePublicProfileVisibility(visible)`

## Anti-drift Notes

- Public profile URLs use `username` for new links (`/public/creator/{username}`); `userId`-based links (`/public/profile/{userId}`) must remain compatible as redirects
- Learning Focus section shows a public-note scope line, the `learningFocusSummary` sentence when available, and note-count-backed subject chips that link into the creator-filtered Public Library
- "View all X notes →" link is conditional on `publicNotesCount > 8`; must use `buildPublicLibraryUrl({ creator: username })` — never hardcode the URL
- Owner controls (`Edit Profile`, visibility toggle) must not be visible to non-owners
- `Share Profile` must go through the same public/private gate as note sharing — confirm modal before opening share modal for private profiles

Route:

- `/public/profile/{userId}`

Current route compatibility note:

- Public Profile currently resolves through `/public/profile/{userId}`.
- If stable public handles or slugs are introduced later, existing public links must remain valid through compatibility lookup or redirect handling.

Related APIs:

- `GET /api/public/profile/{userId}`
- `GET /api/creator-impact/me` (authenticated owner only)
- `PUT /api/users/profile/public-visibility` (owner only)

## What Public Profile Shows

- `displayName`
- `bio` or `This user hasn't added a bio yet.`
- optional `learnerLevel`
- optional `courseProgram`
- avatar/initials
- `profileType`
- `Official` badge when the backend marks the creator as official
- subjects derived from public notes
- `publicNotesCount`
- `totalCopies`
- `totalShares` when the profile's notes have real share activity (counts `PUBLIC_NOTE_SHARED` events on individual note pages)
- `totalViews` when the profile's notes have real view activity
- `totalProfileShares` when the profile link has been shared (counts `PUBLIC_PROFILE_SHARED` events fired when someone copies the "Share Profile" link)
- `notesBySubject` as the top 5 public-note subjects by count, sorted descending
- `totalPublicSubjectCount` as the uncapped count of distinct public-note subjects
- list of public notes only (capped at 8, sorted by copies → views → shares → title)

Portfolio polish:

- Public Profile should feel like a lightweight learning portfolio, not a social feed.
- Header metrics stay compact and should use real values only.
- Derive learning-focus summary text from the user's real public-note subjects/course-programs when available.
- A `Featured note` callout may appear when a real public note has copy/share/view signal; do not fake one.

Public profile note cards reuse the shared note-card layout:

- subject badge
- copy count when available
- title
- `Note Preview`
- `Summary Preview`
- tags

Cards are whole-card links to the canonical public note route.
Cards stay action-free for both owners and non-owners; note management happens in Note Detail instead.

## Visibility

- Public Profile visibility is controlled by `users.public_profile_visible`
- owner can toggle `Public` / `Private` from the Public Profile header
- if the profile is private:
  - owner can still open the page
  - non-owners see `This profile is private.`

## Owner Controls

Owner-only controls belong on Public Profile, not on `/profile`:

- `Edit Profile`
- `Share Profile`
- visibility badge/dropdown

Non-owners:

- must not see `Edit Profile`
- must not see the visibility toggle
- may see `Share Profile` when the profile is public
- see a closing `Browse more notes from {Display Name} →` link at the bottom of the page, linking to the creator-filtered Public Library view (`?creator={username}`); falls back to a generic `Browse more notes in the Public Library →` link when the profile has no username. Owners never see this affordance on their own profile.

Navigation:

- use a `Back` button that calls browser/app history back
- place the `Back` button above the Public Profile header card
- do not hardcode Public Profile back navigation to Library or Public Library

## Share Profile Behavior

`Share Profile` must use the same share modal pattern used for note sharing:

- modal title: `Share this profile`
- labeled field: `Shareable URL`
- buttons: `Close` and `Copy Link`
- `Copy Link` copies the URL to the clipboard and shows `Link copied` feedback inline

If the profile is public:
- Clicking `Share Profile` opens the share modal directly.

If the profile is private and the owner is viewing:
- Clicking `Share Profile` opens a confirm modal: `This profile is private`
- The confirm modal body: `You need to make this profile public before sharing. Anyone with the link will be able to view your public profile and notes.`
- The confirm modal offers `Cancel` and `Make Public & Share`
- `Make Public & Share` sets the profile to public and then opens the share modal

Private profiles are not directly shareable. The share modal must not be opened for private content without the owner confirming the visibility change first.

Non-owners only see Share Profile when the profile is already public (they cannot reach the page if it is private).

## Public Identity Rules

- `displayName` is the readable presentation label, not the unique public identity
- public creator identity should use a stable public identifier for linking and duplicate-name disambiguation:
  - preferred: username / handle when available
  - fallback direction: generated public slug
- public surfaces may render `displayName` plus handle/slug together when needed, for example `Einar · @einarjohn`
- never show email on public profile or public note surfaces
- official badge is backend-derived only
- hide learner-level and course/program rows entirely when those values are empty

## View All Notes Link

When a creator has more notes than the capped list of 8 (i.e. `publicNotesCount > 8`), show a "View all X notes →" link below the note list. The link navigates to `/public/library?creator=<username>`, making the Public Library the canonical place to browse a creator's full catalog.

- Visible to all viewers (owner and non-owner) when the condition is met
- Hides when `publicNotesCount <= 8` (the full catalog is already shown)
- Uses the creator's `username` in the URL, not `userId` or `displayName`

## Learning Focus — Subject Badges

The Learning Focus section turns public-note subject coverage into a lightweight discovery surface:

- Header stat line: `X notes across Y subjects` when `totalPublicSubjectCount >= 2`; otherwise `X notes`
- Summary sentence: `learningFocusSummary` ("Mostly shares notes in…") derived from the creator's public note subjects and course programs
- Subject chips: up to 5 `notesBySubject` entries, each derived from public notes only and linked to `/public/library?creator=<username>&subject=<subject>`

Chips are hidden when `notesBySubject` is empty. Legacy `userId` profile routes remain compatible, but new subject-chip links must use the creator `username`.

## Your Impact — Owner-Only Dashboard

Every account can open its own Public Profile from the existing "View Public Page" link on the top card of `/profile` (unconditional, unchanged by this feature) — this is the entry point into the Impact section below, not a new gated link.

Below the existing public views/copies/shares stats, the profile owner sees a private `Your Impact` section backed by authenticated-only `GET /api/creator-impact/me`. This data is not part of `PublicProfileResponse`, is never served from `/public/**`, and is never rendered for another visitor or an admin viewing someone else's profile.

The dashboard shows:

- one headline count of distinct learners helped across all of the creator's public notes
- a per-note breakdown of distinct learners helped
- raw per-note views and copies as smaller secondary context

`Helped` has a deliberately stronger definition than copied or opened: a learner must copy the public note and complete at least one quiz session on that copied note (`quick_review_sessions.completed_at IS NOT NULL`). A session that was merely started does not count. The same learner is counted once in the creator-level headline even when they completed sessions from multiple notes, so per-note counts may add up to more than the headline.

The section has neutral zero and retryable error states. An Impact API failure does not hide or break the public profile header, existing public stats, notes, or owner controls. The existing public `totalViews` / `totalCopies` / `totalShares` block is unchanged and remains visible to all eligible profile visitors.

Owner views emit `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` once per page load. Publishing a note from a non-public state emits `PUBLIC_NOTE_PUBLISHED`; re-saving an already-public note does not emit it again.

Creators with at least one public note can opt into the `Knowledge Impact digest` under Settings → Email Preferences. The digest runs monthly and reports the distinct learners who completed a quiz from the creator's public notes during the trailing 30 days. It is off by default, sends nothing when that window has zero learners, and uses the existing Email Preferences unsubscribe flow. Its copy stays retrospective and aggregate, with no rankings, comparisons, streaks, badges, or urgency framing.

## Notes

- Public Profile remains a learning profile, not a social-media profile.
- Note cards stay preview/navigation-focused; note actions live in Note Detail.
- Public Profile note cards must keep using the shared note-card component and remain action-free for both owners and non-owners.
