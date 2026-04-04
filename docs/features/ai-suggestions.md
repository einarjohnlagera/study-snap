# ai-suggestions.md - NoteLib Feature Context

## Goal

Keep AI metadata suggestions collaborative and easy to review after Study Pack generation.

## Current Scope

The shared AI Suggestions modal appears after generation when AI suggests:

- `title`
- `subject`
- `tags`

Users decide per field which suggestions to keep or apply.

## Decision Rules

- `Title`
  - `Keep My Title`
  - `Use AI Title`
- `Subject`
  - `Keep My Subject`
  - `Use AI Subject`
- `Tags`
  - `Keep My Tags`
  - `Merge My Tags + AI Tags`
  - `Use AI Tags Only`

## Default Selections

- existing title -> `Keep My Title`
- empty title -> `Use AI Title`
- existing subject -> `Keep My Subject`
- empty subject -> `Use AI Subject`
- existing tags -> `Merge My Tags + AI Tags`
- empty tags -> `Use AI Tags Only`

## UI Rules

- treat the modal as a review-and-decision screen, not a button wall
- show `Your` value and `AI` value side by side when space allows
- use radio buttons for decisions
- use chips for tags
- include a live preview of the final metadata outcome
- primary footer action: `Apply Changes`
- secondary footer action: `Skip`

## Responsive Rules

- desktop:
  - compact modal
  - around `640px` max width
  - around `80vh` max height
  - internal scroll if needed
- mobile:
  - full-screen modal
  - scrollable content
  - sticky footer so `Apply Changes` stays visible
