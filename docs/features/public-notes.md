# public-notes.md - NoteLib Feature Context

## Goal

Public Notes let users discover, read, and copy notes that creators have made public.

Primary surfaces:

- Public Library
- Public subject listing pages
- Public Note Detail
- Public Profile note list

## Public Note Rules

- a note is public only when `visibility=PUBLIC`
- public pages must never show author email
- public author identity comes from `users.display_name` with viewer-relative labeling

Author labels:

- owner viewing own public note -> `By You`
- official NoteLib content -> `By NoteLib` with `Official`
- all other public notes -> `By {Display Name}`

Author labels link to `/public/profile/{userId}`.

## Public Library

Canonical discovery route:

- `/public/library`

App-shell route:

- `/public/library`

Growth behavior:

- Public Library should help users discover useful notes and copy them into their own Library quickly.
- Public Library sorting should support:
  - `Newest`
  - `Most Copied`
  - `Most Shared`
  - `Most Viewed`
- Discovery sorting should use real copy/share/view signals when available.

Canonical note/detail routes:

- `/public/library/{subject}`
- `/public/library/{subject}/{slug}`

## Public Note Cards

Shared public-facing note cards should show:

- subject badge
- copy count when available
- title
- `Note Preview`
- `Summary Preview`
- tags

Interaction rules:

- whole card is clickable
- do not add redundant `Open Note` buttons inside cards

## Public Note Detail

Public note detail is read/copy/share only.

Owner actions:

- `Open Note`
- `Share this note`

Non-owner actions:

- `Quiz yourself on this note` (primary conversion CTA)
- `Add to Library` (secondary; note plus its available Study Pack)
- `Share this note`

Public note detail must not expose edit, delete, generation, or run an inline full quiz on the public page itself.
The note **content** stays primary — the page is note-first for reading and SEO, and the page hierarchy (summary, full notes) is not reordered behind a quiz.
The *conversion CTA*, however, may be quiz-framed: `Quiz yourself on this note` is the primary CTA and routes through the copy → instant Quick Review flow (the actual quiz runs on the viewer's own copy, never on the public page). `Add to Library` and `Share this note` are the visitor-comprehensible secondary actions; editable-draft copying is not a peer public-detail CTA.

When a visitor completes the visible Quick Check, the page shows an inline outcome prompt with the existing `Quiz yourself on this note` CTA. It reuses the normal copy-intent/signup path; completing the Quick Check itself creates no anonymous session, cookie, backend call, or durable result. A ready note with visible Summary, Key Concepts, and Quick Check sections also shows a short attribution line that connects those study tools to the source note and invites visitors to get the same tools for their own notes. The line stays hidden when those previews are unavailable.

Post-copy landing must match the CTA's verb. `Quiz yourself on this note` (a quiz promise) auto-launches Quick Review on the copy (`redirectTarget="quick-review"` → `?copied=1&generate=1&startQuickReview=1`). `Add to Library` is the copy-promise secondary: it lands on the **copied note's detail page** with the available Study Pack rather than dropping the viewer straight into a quiz. Do not route copy-verb CTAs to `quick-review`.

Related discovery stays supplementary and its two sections omit independently. The public detail page offers a `More in {Subject}` module when the existing `GET /notes/public?subject=X` query returns at least two other notes: it excludes the current note, caps the grid at three cards, reuses `shared-note-card.tsx`, and links onward through `See all in {Subject}` to the canonical subject landing page. Empty or too-thin subject results, including fetch failures, omit the section silently. It also offers a `More from {Display Name}` link using the existing creator-filtered Public Library URL, omitted when author fields are unavailable.

Copy-first generation rule:

- `Create your own Study Pack` on a public note should first copy the note into the viewer's Library.
- The viewer then continues generation on their own private note route.
- Public note detail itself stays read-only.

## Copy Rules

Copying a public note:

- creates a new Draft note in the current user's Library
- copies `title`, `subject`, `tags`, and `content`
- does not copy generated outputs or quiz/performance history
- preserves attribution through `copiedFromNoteId` and `copiedFromUserId`

Adoptable study plans reuse the public-note copy spine per source item, with `includeStudyPack=true`, so adopted plan notes become normal owned notes and feed Progress through the existing practice loop. The published source plan remains a snapshot source, not a live link.

## Copy On Signup

When an unauthenticated visitor clicks a public note copy CTA and chooses `Sign Up`, the frontend stores the source note id in a short-lived `notelib-copy-intent` cookie.

Cookie behavior:

- value: public note id
- path: `/`
- expiry: 30 minutes
- same-site policy: `SameSite=Strict`
- cleared after it is consumed or after a returning-user login succeeds

The existing `?copy=1&intent=library` redirect remains in place for returning users who log in and should continue the normal authenticated public-note copy flow.

New-user signup behavior:

- Google signup users are verified immediately, so the auth page consumes the cookie after Google auth succeeds.
- Email/password signup users are routed to `/verify-email`; the cookie persists through the verification round trip and is consumed after successful verification.
- Consuming the cookie calls `POST /api/notes/copy-on-signup`, which copies the public note into the new user's private Library and starts Study Pack generation on the copied note.
- After the copy starts, the user is sent to `/notes/{copiedNoteId}?copied=1&generate=1&startQuickReview=1`.

This path exempts successful copies from the immediate onboarding redirect so public-note visitors first land in the note-to-summary-to-quiz review loop they came for. A per-user lightweight-profile-completion marker carries that exemption until the user later completes Profile Type, Learner Level, Course / Program, and optional Board Exam date through the non-blocking Dashboard prompt. If the copy-on-signup API fails, the cookie is cleared and the user falls back to the normal authenticated home/onboarding destination.

## Share Cards & SEO Metadata

Public note detail pages expose SEO and social-share metadata:

- canonical URL, OpenGraph, and Twitter card via the shared `buildPageMetadata` helper
- `Article` JSON-LD structured data (`buildArticleStructuredData`) with title, description, author, `dateModified`, tags, and `articleSection` (subject); subject landing pages carry `CollectionPage` JSON-LD
- a **dynamic per-note Open Graph image** generated by `app/public/library/[subject]/[slug]/opengraph-image.tsx` (Next.js `next/og` `ImageResponse`, 1200×630). The card shows the note title, a subject pill, and `{N} practice questions · Quiz yourself in seconds`

Rules:

- the note page's `generateMetadata` must leave `openGraph.images` and `twitter.images` unset so the file-convention `opengraph-image` is used; setting a static `images` array would override the dynamic card
- Twitter has no separate `twitter-image` convention; it falls back to the dynamic `og:image`
- the OG route resolves the note via `getServerPublicNoteBySeoPath` and falls back to generic "Study Note" framing when the note is missing — it must never throw
