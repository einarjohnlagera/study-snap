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
- A redesigned OG image that matches the `Build your notes library` positioning is still a `v0.6.0` follow-up task and should not be documented as shipped until the asset is replaced.

## Structured Data

- Landing page should emit `WebSite` JSON-LD.
- Public Library index should emit `CollectionPage` JSON-LD.
- Canonical public note pages should emit `Article` JSON-LD using real note data only.
