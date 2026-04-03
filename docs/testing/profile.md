# profile.md - Testing Notes

Verify these Profile cases when learner metadata changes:

- `Identity`, `Learning Profile`, and `Profile Type` remain separate cards with separate save actions
- `Save Identity` updates only identity fields
- `Save Learning Profile` updates `learnerLevel`, optional `courseProgram`, and optional `bio`
- `Save Profile Type` still uses the separate profile-type save path
- `View Public Page` remains navigation only
- Public Profile shows learner level and course/program only when values exist
