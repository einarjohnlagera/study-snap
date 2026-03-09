# study-library.md — Study Snap Feature Context

This file extracts and consolidates all Study Library-related context from the legacy docs.

## Goal

The Study Library turns Study Snap from a one-shot generator into a reusable study workspace.

Core idea:
- users generate Study Packs
- saved Study Packs can be revisited later
- the library becomes a long-term learning workspace

## MVP behavior

The dashboard is primarily for authenticated users.

It should support:
- listing saved Study Packs
- opening a saved Study Pack
- deleting a saved Study Pack

Recommended list metadata:
- id
- title
- summary preview
- createdAt
- quiz question count
- tags when available

### Study consistency card (UI guidance)

The dashboard includes a lightweight Study Consistency card to encourage repeated study behavior.

Purpose:
- keep the dashboard action-oriented even with few saved Study Packs
- prompt users to continue reviewing or create a new Study Pack
- reinforce a regular study habit without pressure

Current behavior:
- motivational message and clear actions (continue studying, create new Study Pack)
- if no Study Packs exist, guidance focuses on creating the first Study Pack
- no streak numbers and no fake activity data

Future direction:
- real streak/activity tracking can be layered later when backend activity signals are available

### Dashboard CTA hierarchy refinement

To keep the Study Library focused and intentional:
- the hero section owns the main primary action (`New Study Pack`)
- continue studying appears only when at least one Study Pack exists
- the study consistency card stays supportive and motivational (not a duplicate CTA area)
- empty state owns the `Create your first Study Pack` action for new users

The dashboard should avoid repeating multiple equivalent primary actions that all point to the same route.

## User-account dependency

The Study Library works better after user accounts exist because:
- ownership is explicit
- access control is simpler
- free/premium history becomes easier
- future family or shared access can be layered later

## Future directions

Future versions may support:
- rename
- search
- filters
- folders / collections
- reviewed status
- richer dashboard organization

## Tags

Tags may be used for lightweight organization.

Purpose:
- subject/topic grouping
- dashboard filtering
- future search
- future analytics

Initial recommendation:
- store tags as a simple array field on the Study Pack record

Possible sources for tags:
- generated title
- detected topic
- subject selected by user
- manual editing later

## API support needed

Required backend support:
- list my Study Packs
- fetch Study Pack by id
- delete Study Pack

### Study Pack detail page

The Study Library now includes a dedicated Study Pack detail page for authenticated users.

Purpose:
- make Open/Continue actions meaningful
- support focused studying from saved Study Packs
- reinforce Study Snap as a reusable study workspace

Frontend route:
- `/study-packs/[id]`

API detail endpoint:
- `GET /api/study-packs/{id}`

Dashboard integration:
- Continue Studying and Study Pack card Open actions now navigate to the detail route

Response model separation:
- list endpoint stays lightweight for dashboard usage (`id`, `title`, `summaryPreview`, `quizCount`, `createdAt`, `tags`)
- detail endpoint returns full study content (`title`, `summary`, `keyConcepts`, `quiz`, `createdAt`, `tags`)

Quiz rendering consistency:
- Study Pack detail quiz cards should match Study page quiz card behavior
- correct answer choice is highlighted in green with the same "Correct answer" treatment
- shared quiz rendering components should be preferred to avoid UI drift

### Quick Review Mode

The Study Library includes a focused Quick Review Mode for saved Study Packs.

Route:
- `/study-packs/[id]/quick-review`

Purpose:
- give users a lightweight way to study quiz content one question at a time
- make Study Snap feel like an active study workflow, not only a generator and library

Behavior (v1):
- show one question at a time
- reveal correct answer and explanation immediately after selection
- highlight the correct answer in green
- show progress (`Question X of Y`)
- show final results with score, retry, and return-to-detail actions

Activity tracking:
- start event: `STARTED_QUICK_REVIEW`
- completion event: `COMPLETED_QUICK_REVIEW`
- tracking failures should not block the review experience

## Roadmap note

The original roadmap placed Study Library after several other phases.
The updated direction places user accounts before a fully authenticated library so ownership is cleaner.

## Legacy preservation note

This context was extracted from:
- `SPEC.md`
- `ARCHITECTURE.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`
