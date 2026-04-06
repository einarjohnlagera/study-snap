# public-profile.md - Testing Notes

Verify these cases for Public Profile:

- public profile page loads for a public profile
- public profile shows avatar/initials, bio (or the blank-bio fallback), optional learner level/course/program, subjects, and public-note count
- public profile metrics use real values only: `publicNotesCount`, `totalCopies`, and when available `totalShares` / `totalViews`
- `Featured note` only appears when a real public note has copy/share/view signal
- public profile can derive a lightweight learning-focus summary from real public-note subjects/course-programs
- owner sees `Edit Profile`, `Share Profile`, and visibility toggle
- non-owner does not see owner-only controls (`Edit Profile`, visibility toggle)
- non-owner sees `Share Profile` when the profile is public
- profile note cards do not show note-card context menus for owners or non-owners
- private profile shows `This profile is private.` to non-owners
- profile shows `displayName`, `profileType`, `publicNotesCount`, and `totalCopies`
- learner level and course/program are rendered only when values exist
- profile `Back` button uses history navigation instead of a hardcoded library link
- profile `Back` button sits above the header card instead of inside it
- profile note cards open the canonical public note route
- profile note cards do not show redundant inline action buttons in the card body
- only public notes are listed
- email is never rendered on public profile

Share Profile behavior:

- clicking `Share Profile` on a public profile opens a modal titled `Share this profile`
- the share modal shows a `Shareable URL` field and `Copy Link` + `Close` buttons
- clicking `Copy Link` copies the URL to clipboard and shows `Link copied` feedback inline
- clicking `Close` dismisses the share modal
- when the owner's profile is private, clicking `Share Profile` opens a `This profile is private` confirm modal
- the private confirm modal offers `Cancel` and `Make Public & Share`
- `Make Public & Share` sets the profile to public and then opens the share modal
- the share modal never opens directly for a private profile without owner confirmation
- share behavior for profiles uses the same modal component and layout as note sharing
