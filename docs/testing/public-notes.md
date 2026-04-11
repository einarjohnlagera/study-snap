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
- public note detail non-owner CTA is `Copy to My Library`
- Public Library cards may show `Copy to My Library` directly
- if the viewer already copied the note, show `Already in your library` and `Open in My Library`
- copying a public note creates or reuses a Draft note with attribution preserved
- successful public copies offer `Open in My Library` and `Start Quick Review`
- public pages never show email
