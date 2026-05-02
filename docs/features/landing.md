# landing.md - NoteLib Feature Context

This document is the landing-page source of truth for the current public marketing flow.

## Goal

Explain NoteLib in seconds, show why it is different from generic AI tools, and drive signups without feeling like a checkout page.

`/how-it-works` is now the dedicated deep-dive walkthrough page. The landing page should stay faster to scan and should point users there when they want a fuller product explanation.

Landing-page goals:

- explain the product quickly
- position NoteLib as a note-to-study-pack workflow
- make Board Exam Mode visible
- keep the main CTA obvious
- stay clean on mobile

## Product Positioning

NoteLib should still read as:

- a study system
- a note-based study workspace
- a product that guides users from input -> understanding -> practice -> mastery

The public homepage may lead with summaries, quizzes, and exam simulations, but the supporting copy should still make it clear that the workflow starts from the user’s own notes.

## Hero

Primary landing headline:

`Turn anything into a complete study flow`

Supporting copy should explain the active-learning benefit:

- `Write, paste, or generate notes — then turn them into summaries, key concepts, quizzes, and exam-ready practice.`
- notes still remain the reusable core workspace
- Study Packs and quizzes stay connected to the user's learning loop
- active recall and reinforcement are the value, not passive rereading

Hero CTA rules:

- primary CTA: `Start for Free`
- secondary CTA: `See how it works` -> `/how-it-works`
- supporting links may include demo access and Public Library discovery

Board Exam Mode must be visible in the hero with:

- `Board Exam Mode`
- `Pro`
- hero screenshot: `/landing/feature-study-pack.jpg`

## Required Landing Sections

1. Hero
2. How It Works
3. Short feature/value summary
4. Differentiation
5. Target Users
6. Pricing Preview
7. Final CTA

## Learning Loop Section

Landing should add a clear learning-loop section directly below the hero:

1. `Create`
2. `Understand`
3. `Practice`
4. `Challenge`
5. `Improve`

Section title:

`How NoteLib helps you study`

The section should still include a CTA into `/how-it-works` for users who want the fuller walkthrough.

## Features

Landing should now keep screenshots minimal:

- hero uses one main product screenshot only
- the rest of the homepage should use short feature/value summaries instead of a full multi-screenshot walkthrough
- detailed screenshot storytelling belongs on `/how-it-works`

## Screenshot Styling Rules

All public marketing screenshots should share one presentation pattern:

- use `next/image`
- keep the original aspect ratio
- `rounded-2xl` / `16px` corner radius
- soft modern shadow
- `overflow-hidden`
- subtle desktop hover scale only
- below-the-fold screenshots should lazy load naturally
- set explicit width and height to avoid layout shift

Responsive rules:

- mobile stacks image above text
- desktop alternates image-left / image-right to keep the page balanced
- do not add heavy overlays or aggressive motion

## How It Works Page

`/how-it-works` is the main walkthrough destination.

It should include:

1. hero / intro
2. the same simple 3-step flow
3. the full 4-image walkthrough:
   - `/landing/feature-note-editor.jpg`
   - `/landing/feature-study-pack.jpg`
   - `/landing/feature-quiz.jpg`
   - `/landing/feature-results.jpg`
4. value-summary reinforcement
5. Board Exam Mode highlight
6. closing CTA

## Differentiation

Landing should compare NoteLib against generic AI tools in practical terms:

- one-off output vs reusable study workspace
- generic prompt interaction vs note-based workflow
- shallow answer generation vs structured quiz practice
- no exam simulation vs Board Exam Mode

The goal is not to attack generic AI tools, but to show why NoteLib fits repeated study better.

## Target Users

Landing should clearly call out ideal users such as:

- students
- board exam reviewees
- teachers or tutors

The copy should stay broad enough for the public homepage while still reflecting the strongest review use cases.

## Pricing Preview

Landing should preview Free vs Plus vs Pro without becoming a payment page.

Pricing preview rules:

- show Free, Plus, and Pro at a glance
- keep Board Exam Mode visible in the preview
- include a clear link to `/pricing`
- keep upgrade language student-friendly and non-aggressive

## Final CTA

The bottom CTA should reinforce the core promise:

- start from your own notes
- turn them into structured review tools
- begin for free

Recommended primary action:

- `Start for Free`

Recommended secondary action:

- `View Pricing`

## Public Marketing Rules

- Public navigation remains:
  - `Home`
  - `How it Works`
  - `Public Library`
  - `Learn`
  - `Pricing`
  - `Login`
  - `Get Started`
- Public Library must stay accessible without login
- Demo access must remain available without signup
- Landing metadata should remain aligned with NoteLib’s note-library-first SEO positioning even if the on-page hero becomes more conversion-forward
