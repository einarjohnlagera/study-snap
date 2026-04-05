# note-detail.md - Testing Notes

Verify these cases for private Note Detail:

- `Summary`, `Key Concepts`, `Quiz`, and `Full Notes` render as tabs, not action buttons
- `?tab=quiz` opens the quiz view directly
- `?tab=full-notes` opens the full-note view directly
- switching tabs updates the query state without a full page reload
- switching tabs does not jump the page to the top
- switching tabs does not refetch the note or flash the loading state
- tab switching keeps the user near the Study Pack content area on mobile and desktop
- `Full Notes` shows the complete note content without entering edit mode
- opening `/notes/{id}/edit` for a Draft note shows `Edit Note` plus `Save Changes`, `Cancel`, and `Generate Study Pack`
- opening `/notes/{id}/edit` for a Study Pack Ready note keeps `title`, `subject`, and `tags` editable while locking `content`
- Study Pack Ready edit mode shows `Save Changes`, `Cancel`, and `Make a Copy` instead of create-note buttons
