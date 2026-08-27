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

`Build your notes library and turn notes into quizzes.`

The headline must match the landing title's concrete notes-to-quizzes promise:

`NoteLib — Build your notes library and turn notes into quizzes`

Supporting copy should explain the active-learning benefit:

- `Write, paste, or generate notes — then turn them into summaries, key concepts, quizzes, and exam-ready practice.`
- notes still remain the reusable core workspace
- Study Packs and quizzes stay connected to the user's learning loop
- active recall and reinforcement are the value, not passive rereading

Hero CTA rules:

- primary CTA: `Start for Free`
- secondary CTA: `See how it works` -> `/how-it-works`
- demo CTA: `Try the demo — no signup` -> `/demo`, rendered below the CTA row as a required hero slot (not optional fine print) — "show, don't tell" for a product whose proven strength is fast activation

Board Exam Mode must be visible in the hero with:

- `Board Exam Mode`
- `Pro`
- `— timed full-exam simulation` as a short outcome explanation
- hero screenshot: `/landing/feature-study-pack.jpg`

The Board Exam Mode badge renders in the same badge row as `5 study modes`, not as a caption beneath the hero screenshot — the screenshot shows the Study Pack view, and captioning it with a different feature's claim would misrepresent what the image depicts. Keeping the badge decoupled from the screenshot (a pill in the badge row, not image-adjacent caption text) is what keeps the claim honest.

### Live social proof

A lightweight strip sits directly below the Hero, before the deeper product sections. It displays the live `total` from unauthenticated `GET /notes/public?size=1` as:

`N public notes ready to explore for focused review.`

The count is fetched through the server-side public-notes helper with the existing five-minute revalidation. If the public endpoint is unavailable or returns an invalid count, the strip is omitted rather than showing an estimate, placeholder, or invented number. It is usage proof only: no testimonials or AI-capability claims.

## Required Landing Sections

1. Hero
2. How It Works
3. Short feature/value summary
4. Differentiation
5. Target Users
6. Pricing Preview
7. FAQ
8. Learning Connections
9. Final CTA

## Learning Connections Section

Sits between the FAQ and the Final CTA. It is the only public surface that describes helping someone else
learn, which makes its accuracy load-bearing.

**⚠️ It replaced a "Coming Soon" teaser that outlived the feature by three releases.** The section advertised
a waitlist for supporter progress — shipped in `v0.89.0` — and fired `GUARDIAN_INTEREST` on an
"I'm interested" click. So the one public mention of this capability told visitors it did not exist, and
collected an interest click instead of a signup. That event is retired: nothing fires it, it is gone from the
frontend's firing vocabulary, and the Java enum value is kept only because the historical rows are the
product's sole pre-launch interest baseline.

Rules:

- **Every claim must be live.** The section currently promises three things, all shipping: share a note and
  its Study Pack with someone you choose; see their readiness, study frequency and practice once they accept;
  and never see their notes. **Do not add a claim for a later phase** — activity sharing and per-scope
  progress permissions are not built.
- **Relationship-neutral copy.** Marketing may lead with the parent case because it is the clearest real-world
  pain point, but the product gates nothing on who someone is to you. Name several relationships — parents,
  tutors, siblings, study partners — and never imply a guardian mode, a supporter profile, or any account type
  that does not exist.
- **Outcome before feature.** No "social learning" framing, and no permissions, connections or sharing
  vocabulary in the headline copy.
- Its CTA reuses `LANDING_CTA_CLICKED` with `placement: "learning_connections_section"`. **No new event** —
  the landing page already has one CTA event and a placement dimension.

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

Landing should compare NoteLib against generic AI tools with concrete, felt contrasts rather than category-level abstractions — a claim with no example reads as marketing assertion, not proof:

- your notes stay: gone when the chat resets vs saved in your library, reusable for every future review
- weak areas remembered: starts fresh every session vs tracked across quizzes over time
- exam-ready flow: one answer then it stops vs note → study pack → quiz → exam, all from the same saved note

The goal is not to attack generic AI tools, but to show why NoteLib fits repeated study better. Keep the 3-column `What matters / Generic AI tools / NoteLib` table layout; only the row copy is felt/concrete, not the structure.

## Target Users

Landing should clearly call out ideal users such as:

- students
- board exam reviewees
- teachers or tutors
- professionals preparing for job interviews

The copy should stay broad enough for the public homepage while still reflecting the strongest review use cases.

Each audience panel includes a natural guide link into the corresponding Learn Hub category:

- Students → `See guides for students` → `/learn#students`
- Board exam reviewees → `See guides for board exams` → `/learn#board-exams`
- Teachers or tutors → `See guides for teachers` → `/learn#teachers`
- Professionals → `See guides for professionals` → `/learn#professionals`

The board-exam panel also includes `Explore Exam Hubs` → `/exam`. Learn category sections use native anchor targets and the shared hash-scroll listener so direct category links work after the page mounts.

## Pricing Preview

Landing should preview Free vs Plus vs Pro without becoming a payment page.

Pricing preview rules:

- show Free, Plus, and Pro at a glance
- keep Board Exam Mode visible in the preview
- include a clear link to `/pricing`
- keep upgrade language student-friendly and non-aggressive

## FAQ

Landing includes a short FAQ section between Pricing Preview and Final CTA, answering pre-signup objections: is it free, do I need to paste vs upload, which exams are supported, what's the difference between Free/Plus/Pro. The FAQ also emits `FAQPage` JSON-LD (`buildFaqPageStructuredData` in `lib/structured-data.ts`) built from the same array the visible cards render, so copy and structured data never drift.

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

- Public navigation is:
  - `Home`
  - `How it Works`
  - `Demo`
  - `Public Library`
  - `Exam Hubs`
  - `Learn`
  - `Pricing`
  - `Login`
  - `Get Started`
  (this list previously omitted `Exam Hubs`, which already shipped in v0.44.0's cross-linking pass — corrected here alongside adding `Demo`)
- Public Library must stay accessible without login
- Demo access must remain available without signup, and is now a required nav item plus a hero CTA — not optional fine print
- Landing metadata should remain aligned with NoteLib’s note-library-first SEO positioning even if the on-page hero becomes more conversion-forward
