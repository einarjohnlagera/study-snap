# dashboard.md - Testing Notes

Verify these Dashboard cases:

- `Continue Studying` shows the note title prominently instead of only the quiz-mode label
- `Continue Studying` shows `Subject • Course / Program` when both values exist and collapses cleanly when one is missing
- `Continue Studying` progress copy reflects the saved question position (`Question X of Y`) for in-progress sessions
- `Continue Studying` uses the backend `resumeType` to label the action as `Resume Quick Review`, `Resume Challenge Quiz`, or `Resume Adaptive Practice`
- `Continue Studying` routes directly to the correct note-scoped resume path without an extra frontend fetch
- the learner-level prompt appears after onboarding when it has not been dismissed:
  - title `Too easy or too hard?`
  - CTA `Adjust level`
- clicking `Adjust level` routes to `/profile?from=dashboard#learning-profile`
- dismissing the learner-level prompt hides it and persists the dismissal for the same user
- mobile Dashboard keeps the resume title readable and primary actions full width
- Dashboard still renders when continue-study or overview data is unavailable
