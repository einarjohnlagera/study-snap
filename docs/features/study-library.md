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

## Roadmap note

The original roadmap placed Study Library after several other phases.
The updated direction places user accounts before a fully authenticated library so ownership is cleaner.

## Legacy preservation note

This context was extracted from:
- `SPEC.md`
- `ARCHITECTURE.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`
