# branding.md - NoteLib Feature Context

## Goal

Keep NoteLib branding consistent across the app shell, landing page, public pages, metadata, and platform icons.

## Brand Assets

Primary files in `frontend/public`:

- `notelib-logo-monogram.png`
- `notelib-logo-full-light.svg`
- `notelib-logo-full-dark.svg`
- `notelib-logo-icon.svg`
- `og-image-source.svg`
- `og-image.png`

## Logo Usage

Use the NL monogram for:

- public navbar logo
- authenticated app-shell logo
- mobile header logo
- favicon
- apple-touch icon
- other small-logo placements

Use the full logo for:

- landing hero
- Learn header
- Pricing header
- public marketing headers
- public footer
- Open Graph branding

Theme rule:

- `notelib-logo-full-light.svg` on light surfaces
- `notelib-logo-full-dark.svg` on dark surfaces

Use the product icon for:

- feature illustrations
- landing/product illustrations
- Learn-page illustrations
- Open Graph illustration

Do not use the product icon for:

- navbar logo
- favicon
- apple-touch icon

## Favicon Set

Required favicon/public app-icon files:

- `favicon.ico`
- `favicon-16x16.png`
- `favicon-32x32.png`
- `apple-touch-icon.png`
- `favicon-192x192.png`
- `favicon-512x512.png`
- `site.webmanifest`

All of these should derive from the NL monogram.

## Open Graph Image

`og-image.png` should be 1200 x 630 and include:

- NoteLib full logo
- notes-and-lightning illustration
- headline: `Build your notes library`
- supporting line: `Turn your notes into summaries and quizzes`

Source-of-truth artwork file:

- `og-image-source.svg`
