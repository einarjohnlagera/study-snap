# quiz.md - NoteLib Feature Context

## Goal

Quiz features turn a Study Pack-ready note into active-recall practice without breaking the note-first ownership model.

Shared ownership rule:

- generated quiz content belongs to `noteId`
- Quick Review, Challenge Quiz, and Adaptive Practice sessions are note-scoped

## Current quiz modes

### Quick Review

- lightweight review mode
- available on Free, Plus, and Pro
- uses the base Study Pack quiz
- immediate answer feedback
- one retry round for incorrect questions

### Challenge Quiz

- timed quiz mode
- available on Free, Plus, and Pro with plan-based monthly limits
- generated separately from Quick Review
- uses the shared mode-selection entry

### Board Exam Mode

- strict exam-simulation presentation of the Challenge Quiz engine
- Pro-only at entry
- selected from the same mode-selection screen
- keeps separate framing and stricter setup, but still uses note-owned session persistence

### Adaptive Practice

- weak-area follow-up mode
- currently gated to Pro in runtime
- quota-limited on Pro
- generated separately from Quick Review and Challenge Quiz

## Learner-level context

All quiz generation should keep using the existing learner-level system.

Rules:

- learner level remains the primary difficulty and explanation-depth driver
- course/program remains a contextual/domain hint
- Challenge Quiz, Board Exam, and Adaptive Practice resolve Course/Program from the source note first, then fall back to the user's profile Course/Program
- Board Exam Mode keeps its fixed exam-style UX; it should not use progressive generation, but its LLM context still follows the same note-first Course/Program rule
- when learner level is missing, quiz generation falls back to the existing default behavior

Current learner-level adjustment UI:

- Quick Review result page
- Challenge Quiz result page

Current save toast:

- `Learner level updated. Future Study Packs and quizzes will match this level.`

## Result-screen rule

Result screens should guide the next action instead of presenting many equal-weight controls.

Current pattern:

- one dominant primary CTA
- answer review and other controls stay secondary
- `← Back to Note` sits below the action group instead of becoming a primary button

Quick Review:

- `Practice Weak Areas` when weak concepts exist and Adaptive Practice is available
- `Take Another Challenge` after strong/perfect results
- `Practice Again` otherwise

Challenge Quiz:

- `Practice Weak Concepts` when weak concepts exist
- otherwise retry / another challenge becomes the main next action

Adaptive Practice:

- `Generate New Set` remains the primary next action

## Confidence and feedback

Quick Review keeps confidence input as a secondary result-screen action.

Feedback rules:

- result screens may show inline helpfulness / feedback actions
- floating feedback launchers should stay off focused quiz flows

## Generation lock and idempotency

Quick Review:

- does not use the LLM-generation lock flow

Challenge Quiz and Adaptive Practice:

- must reserve and reuse `GENERATING` sessions
- must reuse `IN_PROGRESS` sessions instead of creating duplicates
- may retry only after `FAILED`
- should preserve timing/progress across refresh and recovery

## Leave / forfeit rules

- active quiz sessions show `Leave Quiz`
- app navigation, browser back, and refresh should be guarded while a session is active
- confirmed leaves mark the session `FORFEITED`
- Challenge Quiz and Adaptive Practice forfeits do not refund quota

## Session review

- completed quiz sessions remain note-owned
- Note Detail is the history entry surface
- detailed answer review lives on the dedicated session-review route
- review/export uses stored session data only
