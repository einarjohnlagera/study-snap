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

Frontend environment variables should be set in `frontend/.env.local`.

## Rules
- Pages are thin; logic in `lib/` and small components/hooks.
- All backend calls go through `lib/api.ts`.
- Always handle loading/error/empty states.
- Use shadcn tokens (`bg-background`, `text-foreground`) for theme consistency.

## Theme + Navbar (required)
- Tailwind dark mode uses `class`.
- Use `next-themes`.
- Navbar in `app/layout.tsx` and includes brand, logo placeholder, menu links, theme toggle.
