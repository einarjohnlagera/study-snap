# library.md - Testing Notes

Verify these cases for Library:

- Library note cards stay whole-card clickable and open Note Detail.
- Library note cards show the owned-note context menu only for the current user's notes.
- Library context menu options are `Edit`, `Delete`, `Make a Copy`, and `Share`.
- `Edit` routes Draft notes to the full editor and Study Pack Ready notes to Note Detail metadata editing.
- `Share` from a public note copies the public note link.
- `Share` from a private note requires making the note public first.
- `Delete` uses the shared delete confirmation modal.
- Public Library does not show the owned-note context menu.
