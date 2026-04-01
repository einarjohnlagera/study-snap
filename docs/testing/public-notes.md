# public-notes.md - Testing Notes

Verify these cases for Public Notes:

- Public Library lists only notes with `visibility=PUBLIC`
- public note author label uses:
  - `By You`
  - `By NoteLib`
  - `By {Display Name}`
- author labels link to `/public/profile/{userId}`
- public note cards are fully clickable
- shared note cards show both `Note Preview` and `Summary Preview`
- public note detail owner CTA is `Open Note`
- public note detail non-owner CTA is `Make a Copy`
- copying a public note creates a Draft note with attribution preserved
- public pages never show email
