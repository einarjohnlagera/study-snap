# profile-settings.md - Testing Notes

Verify these `Profile -> Learning Profile` cases:

- `Learner Level` and `Course / Program` both show the shared autocomplete/combobox behavior used in Note Editor.
- `Course / Program` helper text changes when `Learner Level` changes.
- Typing filters Course / Program suggestions in real time with prefix matches ahead of contains matches.
- Existing matching suggestions appear above the custom `Use "..."` action.
- `Save Learning Profile` shows `Please select your learner level.` when `Learner Level` is missing.
- `Save Learning Profile` shows `Please select or enter your course / program.` when `Course / Program` is missing.
- `Save Identity` and `Save Profile Type` still work independently even if Learning Profile fields are incomplete.
