# seo.md - NoteLib Feature Context

## Goal

Keep public metadata aligned with NoteLib's notes-library-first positioning so search and social previews describe the same product users see on the landing page.

## Landing Metadata

Canonical landing values:

- title:
  - `NoteLib — Build your notes library and turn notes into quizzes`
- description:
  - `NoteLib is a notes library where you can organize notes and turn them into summaries, key concepts, and practice quizzes to review more effectively.`
- canonical URL:
  - `https://www.notelib.app`

## Open Graph and Twitter

Landing page should publish:

- `og:title` aligned with the landing title
- `og:description` aligned with the landing description
- `og:type=website`
- `og:url=https://www.notelib.app`
- `og:image=https://www.notelib.app/og-image.png`
- `twitter:card=summary_large_image`
- `twitter:title` aligned with the landing title
- `twitter:description` aligned with the landing description
- `twitter:image=https://www.notelib.app/og-image.png`

## Messaging Rule

- Metadata must position NoteLib as a notes library and study workspace.
- Avoid generic AI-tool wording.
- Public Library and active-recall messaging should reinforce, not replace, the note-first core identity.

## Open Graph Image Status

- The landing page currently points to `frontend/public/og-image.png`.
- The metadata update is already live.
- The current OG image now uses:
  - NoteLib full logo
  - notes-and-lightning illustration
  - `Build your notes library`
  - `Turn your notes into summaries and quizzes`
- The SVG source of truth is `frontend/public/og-image-source.svg`.

## Structured Data

- Landing page should emit `WebSite` JSON-LD.
- Landing page should also emit `FAQPage` JSON-LD for its FAQ section, built from the same array that renders the visible Q&A cards (`buildFaqPageStructuredData` in `lib/structured-data.ts`) so copy and markup can't drift.
- Public Library index should emit `CollectionPage` JSON-LD.
- Public Library subject landing pages (`/public/library/{subject}`) should also emit `CollectionPage` JSON-LD.
- Canonical public note pages should emit `Article` JSON-LD using real note data only.
- Learn articles should emit `Article` JSON-LD (title, description, canonical URL, category as `articleSection`) — Learn is the flagship SEO content surface and previously had no structured data at all.
- Exam Hub pages (`/exam/{slug}`) should also emit `CollectionPage` JSON-LD.

## Sitemap

`frontend/app/sitemap.ts` must include every publicly indexable route with real search intent behind
it: landing, Learn, Public Library (index, per-subject, per-note), and the Exam Hub index plus each
`/exam/{slug}` page (`ale`, `pnle`, `let`). The Exam Hub pages were missing from the sitemap for a
period despite being indexable and already emitting `CollectionPage` JSON-LD — when adding a new public
route with search intent, add it here too; a page can be fully built and structured-data-correct and
still be invisible to search if it isn't in the sitemap.

## Learn Content Marketing Pages

- Learn hub and Learn article pages are public SEO surfaces.
- Learn articles should provide standalone student value before asking for signup.
- Each Learn article should include:
  - clear title
  - short introduction
  - summary
  - key concepts
  - practice questions
  - bottom CTA into account creation
- Learn CTA copy should position NoteLib as the place to turn personal notes into summaries and quizzes, not as a generic AI tool.
