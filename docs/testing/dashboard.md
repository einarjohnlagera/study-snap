# dashboard.md - Testing Notes

Verify these cases for Dashboard:

- `Continue Studying` shows the note title prominently instead of only the quiz-mode label.
- `Continue Studying` shows `Subject • Course / Program` when both values exist and collapses cleanly when one is missing.
- `Continue Studying` progress copy reflects the saved question position (`Question X of Y`) for in-progress sessions.
- `Continue Studying` uses the backend `resumeType` to label the action as `Resume Quick Review`, `Resume Challenge Quiz`, or `Resume Adaptive Practice`.
- `Continue Studying` routes directly to the correct note-scoped resume path without an extra frontend fetch.
- Mobile dashboard keeps the resume title readable across two lines max and keeps the resume action full width.
- Dashboard continues to render even when continue-study data is unavailable.
