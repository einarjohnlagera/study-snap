# notes.md - Testing Guidance

## Subject Suggestions

- Create a note with a custom subject and save it.
- Reopen Note Editor and confirm the saved custom subject appears in the `Subject` autocomplete suggestions.
- Generate a Study Pack from a note with detailed academic content and confirm the AI subject suggestion is specific enough to help library filtering.
- Reject broad AI subject suggestions such as `Engineering`, `Medicine`, `Education`, `Law`, or `Business` during backend validation so the generation flow retries once before failing.
- Verify structured subjects such as `Criminal Law – Crimes Against Persons` are accepted and remain selectable in later autocomplete/filter flows.

## AI Metadata Suggestions

- Confirm both `Create Note` and `Note Detail` use the same AI metadata suggestion modal.
- Confirm user-entered `title` and `subject` default to `Keep Mine`.
- Confirm tags default to `Merge` when the note already has tags and `Use AI` when the note has none.
