# landing.md - NoteLib Feature Context

This document is the current landing-page source of truth for `v0.6.0 - Landing Revamp & Positioning`.

## Goal

Explain NoteLib as a notes library and study workspace where users save notes first, then generate Study Packs when they are ready to review.

Primary message:

`Build your own library of notes. Turn them into summaries and quizzes when you're ready to review.`

## Product Positioning

NoteLib should be framed as:

- a notes library
- a long-term study workspace
- a place where notes become summaries, key concepts, and quizzes
- a product built around active recall

Do not frame NoteLib as only:

- a quiz generator
- a summarizer
- a generic AI tool

## Required Public Navigation

Shared public navbar items:

- `Home`
- `Public Library`
- `Learn`
- `Pricing`
- `Login`
- `Get Started`
- theme toggle

Public navbar hierarchy:

- navigation links should read as navigation, not buttons
- `Get Started` is the primary CTA
- `Login` is the secondary action
- theme toggle is a utility control and should not sit inside the CTA stack
- on mobile, keep the theme toggle in the header utility cluster and keep the opened menu panel focused on nav links plus `Login` and `Get Started`
- do not duplicate the theme toggle or visible primary CTA between the header and the opened mobile menu

Public Library must stay accessible without login.

## Required Sections

1. Hero
2. What Is NoteLib
3. How It Works
4. Public Library
5. Study Method / Learn CTA
6. Pricing teaser
7. Final CTA

## Hero

- Headline: `Build your own library of notes. Turn them into summaries and quizzes when you're ready to review.`
- Supporting copy should explain NoteLib as a notes library, study workspace, and review tool.
- Primary CTA: `Get Started`
- Secondary CTA: `View Public Library`

## What Is NoteLib

Title:

`Your Notes. Your Library. Your Review Tool.`

Purpose:

- explain the note-first workflow
- reinforce that users build a reusable library first
- connect note storage to later review and quiz practice

## How It Works

Use the shared 4-step explanation:

1. `Create a Note`
2. `Build Your Library`
3. `Generate Study Pack`
4. `Review & Practice`

## Public Library Section

- Public Library is a first-class discovery feature.
- Section CTA should route to `/public/library`.
- Messaging should explain that users can browse public notes, copy them into Library, and share their own notes later.

## Study Method Section

- Explain active recall in simple student terms.
- Link to `/learn`.
- Keep this section focused on why self-testing is more effective than rereading.

## SEO Alignment

- Landing copy, metadata, and Open Graph messaging should stay aligned.
- Canonical landing title:
  - `NoteLib — Build your notes library and turn notes into quizzes`
- Canonical landing description:
  - `NoteLib is a notes library where you can organize notes and turn them into summaries, key concepts, and practice quizzes to review more effectively.`
- See `docs/features/seo.md` for metadata-specific rules.
