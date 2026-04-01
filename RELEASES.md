# RELEASES.md - NoteLib

## v0.4.0 - Personalization & UX

- Profile identity management
- Email change verification
- Onboarding per profile type
- Personalized dashboards
- Teacher workflow and quiz-first flow
- Mode-based note creation
- Note editor UX improvements across desktop and mobile
- Create note from My Library
- Profile-based button labels and CTAs
- Email template refresh with first-name personalization, shared NoteLib footer, and updated Free vs Premium welcome messaging
- Auth redirect fix for expired-session re-login so successful login returns cleanly to Dashboard without rendering the app shell on auth pages
- First-time activation flow improvements across verification, empty dashboard states, first Study Pack guidance, and first quiz weak-concept follow-up
- Public Library now includes your own public notes and labels cards as `By You`, `By NoteLib`, or `By {displayName}` with a backend-driven `Official` badge for NoteLib content
- Public note detail now stays read/copy/share only with `Open Note` for owners, `Make a Copy` for non-owners, and viewer-relative author labels in the header
- Profile identity now supports `displayName`, public notes render backend-driven author names, the official NoteLib account gets an `Official` badge, and reserved display names are blocked server-side
- Public Library subject pages now reuse the existing `/public/library/{subject}` route with subject-badge-consistent cards, empty states, and sitemap coverage for subject and note URLs
- Subject display is now standardized with shared badges across library cards and note headers, and subject suggestions now come from persisted backend `notes.subject` values while still allowing custom subjects
- Public Profile cards now follow the shared library interaction model with whole-card click navigation to public note detail and no redundant `Open Note` button
- Public Profile header controls now align with Note Detail by using a badge/dropdown visibility control near the header identity cluster and placing `Share Profile` in the lower-right action row
- Profile page layout is now split into a top Display Name summary card plus separate `Identity` and `Profile Type` cards, each with its own save action and a navigation-only `View Public Page ->` link
- Shared note cards across Library, Public Library, Public Profile, and public subject pages now show both a clamped `Note Preview` and `Summary Preview`, with a fallback message when no summary exists
- Shared action buttons now use a centralized icon mapping with consistent desktop icon+text, mobile icon-first behavior, and aligned controls across Dashboard, Library, Note Detail, Profile, Public Profile, and Settings
- Sidebar/navigation icon polish now assigns `Home`, `Book`, `Globe`, `User`, `Gear`, and `Shield` consistently, and action icons now use a single outline-style set with no emoji-based UI icons
- Outline buttons now use higher-contrast dark-mode borders, lighter text, and a clearer hover fill so secondary actions remain readable on dark surfaces
