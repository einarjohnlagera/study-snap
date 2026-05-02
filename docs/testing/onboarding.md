# onboarding.md - Testing Notes

Verify these onboarding cases:

- verified users see the current 5-step activation flow:
  - `Profile Type`
  - `Study Goal`
  - `Input Method`
  - `Study Pack Generation`
  - `Completion`
- Board Taker users get the inline optional `Exam Date` field on the Study Goal step
- `Generate a note` path requires topic input, creates an editable generated draft first, then allows `Generate Study Pack →`
- `Write or paste my own note` path requires note content before `Generate Study Pack →`
- onboarding does **not** ask for `learnerLevel`, `courseProgram`, `bio`, `Learning Style`, or reminder preferences
- users who already completed onboarding are redirected away from `/onboarding`
- once Study Pack generation starts, the generating state hides the `Back` button and shows `Your Study Pack is being created. This step can't be undone.`
- repeated generation attempts do not create duplicate notes or duplicate Study Packs for the same onboarding flow
- retry after a generation failure reuses the saved note instead of creating a new one
- completion persists onboarding through `onboardingCompletedAt`
- onboarding-generated Study Packs auto-apply generated `subject` and `tags` when the source note metadata is empty
