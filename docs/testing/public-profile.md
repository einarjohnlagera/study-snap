# public-profile.md - Testing Notes

Verify these cases for Public Profile:

- public profile page loads for a public profile
- public profile shows avatar/initials, bio (or the blank-bio fallback), subjects, and public-note count
- owner sees `Edit Profile`, `Share Profile`, and visibility toggle
- owner sees a note-card context menu on their own Public Profile
- owner note-card menu options are `Delete`, `Make Private`, and `Make a Copy`
- non-owner does not see owner-only controls
- non-owner does not see note-card context menus on Public Profile
- private profile shows `This profile is private.` to non-owners
- profile shows `displayName`, `profileType`, `publicNotesCount`, and `totalCopies`
- profile `Back` button uses history navigation instead of a hardcoded library link
- profile note cards open the canonical public note route
- profile note cards do not show redundant inline action buttons in the card body
- only public notes are listed
- email is never rendered on public profile
