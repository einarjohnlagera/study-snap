# ai-suggestions.md - NoteLib Feature Context

## Goal

Keep AI metadata suggestions collaborative and easy to review after Study Pack generation.

## Current Scope

The shared AI Suggestions modal appears after generation when AI suggests:

- `title`
- `subject`
- `tags`

Users decide per field which suggestions to keep or apply.

In the normal note flow, these suggestions are transient UI state only. They must not be written back to the note until the user explicitly applies them.

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
- existing tags with new AI tags -> `Merge My Tags + AI Tags`
- existing tags with no new AI tags -> `Keep My Tags`
- empty tags -> `Use AI Tags Only`

## Tag Deduplication Rules

- compare tags case-insensitively after trimming whitespace
- preserve original casing for display
- only show net-new AI tags as suggestions
- if an AI tag already exists on the note, show it as already included instead of as a fresh suggestion
- when all AI tags already exist, show `No new tag suggestions.`
- applying suggestions should merge tags with case-insensitive deduplication
- skipping or closing the modal must not persist any metadata changes

## Subject Resilience Rules

- AI subject suggestions are optional metadata and must not fail Study Pack generation.
- Broad AI-suggested subjects such as `Business`, `Medicine`, `Engineering`, and `Law` are ignored/rejected safely instead of being saved silently.
- Valid specific suggestions such as `Electrical Engineering`, `Clinical Chemistry`, `Accountancy`, and `Criminal Law` remain available for the normal review/apply flow.
- Existing note subjects remain unchanged when the AI subject suggestion is invalid.

## Onboarding Exception

Onboarding may explicitly opt into backend auto-apply for empty metadata fields so the guided first-win flow stays low-friction. That exception must stay separate from the normal note flow.

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
