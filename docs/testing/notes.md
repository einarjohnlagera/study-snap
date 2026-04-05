# notes.md - Testing Guidance

## Subject Suggestions

- Create or edit a note with a custom course/program and save it.
- Reopen Note Editor, Profile, or Onboarding and confirm the saved course/program appears in the `Course / Program` autocomplete suggestions.
- Save equivalent course/program variants such as `Senior High-STEM`, `senior high - stem`, and `Senior High – STEM`, then confirm autocomplete/filter suggestions collapse them into one reusable course/program label.
- Create a note with a custom subject and save it.
- Reopen Note Editor and confirm the saved custom subject appears in the `Subject` autocomplete suggestions.
- Save equivalent subject variants such as `Biology-Cell Division`, `biology - cell division`, and `Biology – Cell Division`, then confirm autocomplete/filter suggestions collapse them into one reusable subject label.
- Generate a Study Pack from a note with detailed academic content and confirm the AI subject suggestion is specific enough to help library filtering.
- Reject broad AI subject suggestions such as `Engineering`, `Medicine`, `Education`, `Law`, or `Business` during backend validation so the generation flow retries once before failing.
- Verify structured subjects such as `Criminal Law – Crimes Against Persons` are accepted and remain selectable in later autocomplete/filter flows.

## AI Metadata Suggestions

- Confirm both `Create Note` and `Note Detail` use the same AI metadata suggestion modal.
- Confirm user-entered `title` and `subject` default to `Keep Mine`.
- Confirm tags default to `Merge` when the note already has tags and `Use AI` when the note has none.
