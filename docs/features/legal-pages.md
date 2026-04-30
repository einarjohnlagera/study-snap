# legal-pages.md - NoteLib Feature Context

## Goal

Provide public legal pages required for launch readiness, payment integration, and user trust.

## Routes

- `GET /privacy`
- `GET /terms`

These routes must remain publicly accessible without login.

## SEO Indexing

- `/privacy` and `/terms` should appear in the public XML sitemap.
- Legal pages are crawlable public routes and should not be gated behind auth, onboarding, or app-shell access rules.

## Privacy Policy

The Privacy Policy page should cover:

- what NoteLib is
- account information collected
- notes, study activity, and usage data collected
- how information is used to operate and improve the service
- AI and OCR processing disclosures
- data storage and security language
- account, reminder, and billing emails
- Xendit payment processing
- contact at `support@mail.notelib.app`

## Terms of Service

The Terms page should cover:

- use of service as a study tool
- user responsibility for uploaded content
- account responsibility and abuse suspension
- acceptable use restrictions
- paid-plan manual renewal and cancellation-at-period-end
- service availability and feature changes
- contact at `support@mail.notelib.app`

## Footer Links

Public footer links should appear on:

- Landing page
- Login page
- Signup page

Footer links should include:

- `Privacy Policy`
- `Terms of Service`
- `Contact`

## UX Notes

- Legal pages can stay simple and text-focused.
- Use readable spacing, headings, and a visible last-updated date.
- Do not gate legal pages behind auth or onboarding.
