# Study Snap Frontend (Next.js)

Web UI for Study Snap.

## Stack
- Next.js (App Router)
- TypeScript
- Tailwind CSS
- shadcn/ui
- next-themes (light/dark)
- lucide-react icons

## Run locally

```bash
npm install
npm run dev
```

Default: http://localhost:3000

## Folder structure (recommended)

```
frontend/
  app/
    page.tsx
    solve/page.tsx
    dashboard/page.tsx
  components/
    navbar.tsx
    theme-provider.tsx
    theme-toggle.tsx
  lib/
    api.ts
  types/
    solve.ts
```

## Rules
- Pages are thin; business logic in `lib/` and small components/hooks.
- All backend calls go through `lib/api.ts` (no scattered `fetch`).
- Always handle loading/error/empty states.
- Use shadcn tokens (`bg-background`, `text-foreground`, etc.) for theme consistency.

## UI constraints (MVP)
- Container: `max-w-3xl`
- Buttons: only 2 variants
  - Primary (blue solid)
  - Secondary (gray outline)
- Cards: `bg-gray-50`, `border-gray-200`, `shadow-sm`, `rounded-xl`
- No gradients
- No emoji icons (use lucide)

## Theme + Navbar (required)
- Tailwind dark mode uses `class`.
- Use `next-themes` for toggling.
- Navbar is implemented in `app/layout.tsx` so it appears on all pages.
- Navbar includes:
  - Study Snap brand + logo placeholder
  - Dashboard link
  - Theme toggle (with mounted guard)
