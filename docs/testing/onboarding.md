# onboarding.md - Testing Notes

Verify these onboarding cases when learner metadata changes:

- verified users see `Profile Type -> Learning Profile -> Learning Style -> Study Reminder Frequency`
- board-exam users also see the conditional `Exam Date` step
- `Learner Level` is required before continuing past `Learning Profile`
- `Course / Program` is optional and can use either a suggestion or a custom typed value
- `Bio` is optional and respects the character limit
- onboarding persists `learnerLevel`, optional `courseProgram`, optional `bio`, `engagementMode`, reminder preferences, and conditional `examDate`
- users who already completed onboarding are still redirected away from `/onboarding`
