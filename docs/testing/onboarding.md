# onboarding.md - Testing Notes

Verify these onboarding cases when learner metadata changes:

- verified users see `Profile Type -> Learning Profile -> Learning Style -> Study Reminder Frequency`
- board-exam users also see the conditional `Exam Date` step
- learner-level and course/program use the same combobox interaction pattern as the Note Editor `Subject` field
- `Learner Level` is required before continuing past `Learning Profile`
- `Course / Program` is required before continuing past `Learning Profile`
- typing in onboarding `Course / Program` filters suggestions in real time and keeps matching saved values above the custom action
- `Bio` is optional and respects the character limit
- onboarding persists `learnerLevel`, required `courseProgram`, optional `bio`, `engagementMode`, reminder preferences, and conditional `examDate`
- users who already completed onboarding are still redirected away from `/onboarding`
