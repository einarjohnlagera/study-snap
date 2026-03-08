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
