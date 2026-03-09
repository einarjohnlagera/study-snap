# SPEC.md — Study Snap

## Product positioning

Study Snap turns study notes into structured study pack materials and practice quizzes.

It is a calm, on-demand tutor workflow:

**upload notes → get a Study Pack you can study from**

Tone:
- calm
- structured
- supportive
- non-judgmental

## Product statement

Study Snap helps users turn messy notes into structured study materials and reusable Study Packs using AI.

## Core product idea

A Study Pack is the product’s core output.

A Study Pack includes:
- title
- summary
- key concepts
- practice quiz

Users can save generated Study Packs and revisit them in a Study Library.

## Target users

Study Snap is designed for:
- students preparing for exams
- learners reviewing lecture notes
- professionals preparing for interviews
- developers reviewing technical concepts

## MVP scope

Included:
- landing page
- study page (paste notes + image upload)
- results view (study pack sheet + quiz)
- friendly error handling
- light/dark theme + global navbar
- demo mode
- OCR direction
- Study Library direction
- shareable Study Pack direction

Excluded for now:
- full exam simulation with grading analytics
- deep explanation mode (premium later)
- flashcards / spaced repetition
- payments / Stripe
- advanced dashboards / analytics
- gamification
- classroom management
- teacher mode
- family linking

## Primary user flow

### 1) Landing (`/`)
Headline and CTA focus on **NOTES → study pack** or **NOTES → STUDY PACK**, not question solving.

CTA:
- “Turn Notes into Study Pack” → `/study`

### 2) Study page (`/study`)
Input modes:
- paste notes
- upload image (`jpeg/png/webp` where supported)

States:
1. idle: nothing provided, Generate disabled
2. ready: notes text or image present, Generate enabled
3. loading: “Creating your study pack materials…”
4. OCR needs confirmation: show editable extracted text, user confirms
5. result: show Study Pack + quiz
6. error: friendly message + recovery path

## Demo mode

Study Snap provides a demo mode for first-time users.

Behavior:
- activated via `/study?demo=true`
- prefill example notes
- simulate generation delay
- return a static placeholder Study Pack
- does not call the backend study pack API
- does not call OpenAI
- does not write to the database
- does not count as a real usage event

Purpose:
- show product value instantly
- avoid abuse of paid LLM calls

## Results view

### For all plans
Sections:
1. title
2. summary
3. key concepts
4. practice quiz
5. actions:
   - Try Another
   - Edit Notes (optional)
   - future: Regenerate / More questions / Flashcards

## Quiz quality

Practice quizzes should include a balanced mix of:
- recall questions
- understanding questions
- application questions

Purpose:
- make quizzes feel more like real study reviewers
- improve usefulness for exam preparation and interview preparation

If notes are too short or simple, quizzes may prioritize recall and understanding.

## Study Library

Study Snap includes a Study Library for saved generated Study Packs.

Purpose:
- let users revisit past Study Packs
- build a reusable study pack collection over time
- make Study Snap feel like a long-term study workspace

### Dashboard

The dashboard is primarily for authenticated users.

It shows:
- saved Study Packs
- title
- created date
- short summary preview
- quiz count
- actions: open, delete

Saved Study Pack cards may display tags when available.

### MVP Library behavior

For the first version, the Study Library supports:
- viewing saved Study Packs
- opening a Study Pack
- deleting a Study Pack

Future versions may support:
- rename
- search
- filters
- folders / collections
- reviewed status

### Tags

Each saved Study Pack may include one or more tags.

Purpose:
- help users organize Study Packs by subject or topic
- support filtering and search in the Study Library
- improve future topic-based insights

Examples:
- Biology
- Chemistry
- Algebra
- Java
- Spring Boot
- Interview Prep
- REST API Design

For the first version, tags may be:
- auto-generated from the detected topic or title
- derived from user-selected subject input
- manually editable later

Future Study Library features may allow:
- filtering by tag
- searching by tag
- grouping Study Packs by tag

## Shareable Study Packs

After generating a Study Pack, users can create a shareable link:
- public URL: `/share/[token]`

Rules:
- shared page shows generated content
- raw uploaded image must not be exposed
- raw notes text may be hidden by default
- tokens must be unguessable
- expiration may be added later for premium plans

## Pricing model

Study Snap follows a freemium model.

### Demo
- no login
- 1 demo generation direction
- summary + key concepts + 3-question quiz
- no saving

### Free account
- 3 study packs per day
- summary + key concepts + 5-question quiz
- can save and view history
- access to Study Library

### Premium
- up to 200 study packs per month
- access to Study Library
- mock exam mode later
- analytics and mastery tracking later

### Model usage by plan

Demo:
- `gpt-4.1-mini`

Free:
- `gpt-4.1-mini`

Premium:
- may use a higher quality model later for premium-only features such as:
  - mock exam generation
  - deeper explanations
  - analytics and topic mastery

## User accounts direction

User accounts are intended to support:
- authenticated ownership of Study Packs
- Study Library access
- future usage limits by plan
- future subscription analytics
- future premium feature access

Current account flow direction:
- signup asks for first name, email, password, and optional display name
- email verification is required before real Study Pack generation
- onboarding asks profile type after signup/login

UI copy for onboarding profile selection:
- “I’m using Study Snap as a…”

Initial values:
- Student
- Parent
- Professional

Teacher mode is intentionally deferred.

Family / linked-child accounts are intentionally deferred.

## OCR image processing

Study Snap supports image-based note uploads.

Uploaded images are processed through an OCR pipeline to extract text before generating Study Packs.

### OCR flow
1. user uploads image
2. system validates the image
3. quick text detection is performed
4. if text is detected, full OCR extraction runs
5. extracted text is cleaned and sent to the LLM generator

### Image guardrails
- images must contain detectable text
- maximum image size limits are enforced
- unsupported formats are rejected

If an image contains no readable text, the system returns a message prompting the user to upload clearer notes.

## Privacy

- uploaded images are deleted after OCR processing
- avoid logging raw images or full extracted text

## Design system (locked for MVP)

Goal:
- “Friendly academic” — clean like Khan Academy, slightly warm

Visual direction:
- background: white
- surface/cards: gray-50
- border: gray-200
- text primary: gray-900
- text secondary: gray-600
- primary accent: blue-600
- success accent: emerald-500

Rules:
- no gradients for MVP
- one primary accent for primary actions
- use shadcn tokens for theme consistency
- cards: rounded-xl, shadow-sm
- inputs/buttons: rounded-lg

## Global layout

- global navbar appears on all pages
- navbar includes Study Snap brand + logo placeholder
- menu links
- light/dark theme toggle
- theme toggle avoids hydration mismatch

## Legacy carryover note

This file preserves and reorganizes content from:
- `SPEC.md`
- `PROJECT_CONTEXT.md`
- later user-account decisions

The original source files remain under `/legacy`.



