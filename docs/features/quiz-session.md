# quiz-session.md - NoteLib Feature Context

## Goal

Quiz sessions persist progress separately from generated Study Pack content so users can leave and resume review safely.

## Session Modes

Shared session storage supports:

- `QUICK_REVIEW`
- `CHALLENGE`
- `ADAPTIVE`
- `LONG_EXAM`

## Status Lifecycle

Shared session status values:

- `GENERATING` — generated question set is being created and committed to session state
- `FAILED` — generation failed; the caller can recover without losing note or Study Pack data
- `IN_PROGRESS` — session is active and accepting progress updates
- `PAUSED` — Long Exam session is paused and resumable; active-session exclusivity still applies
- `COMPLETED` — session has been submitted and scored
- `FORFEITED` — session was abandoned through an explicit forfeit flow

## Dashboard Resume Metadata

Dashboard resume recommendations must stay note-based even though session data lives on quiz-session rows.

The backend should join:

- quiz session
- study pack
- note

Required resume metadata:

- `noteId`
- `noteTitle`
- `subject`
- optional `courseProgram`
- `resumeType`
- `currentQuestionIndex`
- `totalQuestions`
- `lastReviewedAt`

Rules:

- `resumeType` comes from session mode, not frontend heuristics
- dashboard resume payloads should use one API response
- note metadata should prefer current note values over older generated Study Pack metadata when both exist
- if note metadata is missing, fallback display should still remain usable

## Long Exam Multi-source State

Long Exam sessions stay anchored to the primary `studyPackId`. When the user adds same-subject notes, additional source attribution is stored in `sessionState.sourceNoteRefs`.

Each entry contains:

- `studyPackId`
- `noteId`
- `noteTitle`
- `questionCount`

This keeps multi-source Long Exam generation inside the shared quiz-session lifecycle without adding a new persistence aggregate.
