# quiz.md - NoteLib Feature Context

## Goal

Quiz features turn a Study Pack-ready note into active-recall practice without creating separate product ownership models.

Shared ownership model:

- generated quiz content belongs to `noteId`
- Quick Review, Challenge Quiz, and Adaptive Practice sessions are note-scoped

## Quiz Modes

### Quick Review

- fast review flow
- available on Free and Premium
- icon: lightning

### Challenge Quiz

- exam-style challenge mode
- plan-gated through the shared Premium prompt flow
- icon: trophy

### Adaptive Practice

- weak-area follow-up mode
- shown when weak concepts exist and plan allows it
- icon: target

## Entry Surfaces

Quiz entry points appear in:

- Private Note Detail
- Dashboard recommendations
- other owner study surfaces derived from the same note

Use distinct icons for each mode and keep the action mapping consistent across pages.

## UI Rules

- quiz-mode launchers are buttons/actions
- `Summary` and `Quiz` on Note Detail are tabs/view navigation
- desktop action buttons show icon + text
- mobile action buttons default to icon-only unless text is needed for clarity

## Current v0.5.0 Scope

- Quick Review
- Challenge Quiz
- Adaptive Practice
- distinct quiz-mode icons
- tab-based `Summary` / `Quiz` switching on Note Detail

## v0.6.0 Direction

Board Exam Mode should build on the same note-first quiz foundation rather than introducing a separate quiz ownership model.
