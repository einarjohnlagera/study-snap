# public-profile.md - Testing Notes

Verify these cases for Public Profile:

- public profile page loads for a public profile
- owner sees `Edit Profile`, `Share Profile`, and visibility toggle
- non-owner does not see owner-only controls
- private profile shows `This profile is private.` to non-owners
- profile shows `displayName`, `profileType`, `publicNotesCount`, and `totalCopies`
- profile note cards open the canonical public note route
- profile note cards do not show redundant `Open Note` buttons
- only public notes are listed
- email is never rendered on public profile
