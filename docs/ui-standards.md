# UI Standards — NoteLib

Codex and Claude must read this file before implementing any page layout, navigation, or secondary-link pattern. These rules are product-level decisions, not preferences — do not deviate without an explicit instruction from the user.

---

## Page Header Card Pattern

Every authenticated main page and sub-page uses the `PageHeader` component (`components/page-header.tsx`) as its first visual element.

```tsx
<PageHeader
  eyebrow="SECTION NAME"    // all-caps, short label
  title="Page Title"
  description="One sentence that explains what the user does here."
  actions={<PrimaryCtaButton />}  // optional
/>
```

### Rules

- `eyebrow`: uppercase, no emoji, ≤ 3 words. This is the section identity badge.
- `title`: title-case, matches the sidebar nav label for main pages.
- `description`: one sentence, plain text, ends with a period. Tells the user what they accomplish here — not what the page contains.
- `actions`: optional right-aligned slot. Use for primary CTA only (e.g. "+ Create Note"). Do NOT put secondary or destructive actions here.
- The `PageHeader` is already a `Card` — do not wrap it in another Card.

### Applied examples

| Page | eyebrow | title | actions |
|------|---------|-------|---------|
| Library | `LIBRARY` | `Library` | `+ Create Note` button |
| Profile | `PROFILE` | `Profile` | _(none)_ |
| My Progress | `MY PROGRESS` | `My Progress` | _(none)_ |
| Settings | `SETTINGS` | `Settings` | _(none)_ |

### What NOT to do

- Do not use a bare `<header>` or `<div>` with manual h1 + p — use `PageHeader`.
- Do not add an inline eyebrow paragraph manually — it is already inside `PageHeader`.
- Do not put the back link inside the `PageHeader` card — it goes above it.

---

## Back Link Pattern

### Component

Always use the `BackLink` component (`components/ui/back-link.tsx`). Never use a raw `<Link>` or `<a>` tag for back navigation.

```tsx
<BackLink href="/dashboard" label="Dashboard" />
```

### Rules

- **Style**: blue (`text-blue-600 dark:text-blue-400`), underlines on hover, ArrowLeft icon. The component handles this — do not override the color.
- **Label**: destination page name only. No "Back to" prefix. No "Back" alone.
  - ✓ `"Dashboard"`, `"Library"`, `"Note"`, `"Profile"`
  - ✗ `"Back to Dashboard"`, `"Back to Note"`, `"← Back"`
- **Position**: above the `PageHeader` card, left-aligned. Nothing else goes between the back link and the page header.
- **When to use**: on sub-pages only (Note Detail, Quiz pages, Create/Edit Note, etc.). Main pages (Dashboard, Library, Public Library, My Profile, Settings, Progress) do not get a back link.
- **Routing**: always explicit `href`, never `router.back()`.

### Sub-page → destination reference

| Sub-page | `href` | `label` |
|----------|--------|---------|
| Note Detail | `/library` | `Library` |
| Public Note Detail | `/public/library` | `Public Library` |
| Quick Review / Challenge Quiz / Adaptive Practice | `/notes/{id}` | `Note` |
| Create Note | `/library` | `Library` |
| Edit Note | `/notes/{id}` | `Note` |
| My Progress | `/dashboard` | `Dashboard` |
| Profile Settings | `/dashboard` or public profile path | `Dashboard` or `Profile` (context-aware) |

### Inline card action links

When an error or empty state inside a card needs a navigation link (not the page-level back link), use a short label without "Back to":
- ✓ `"Note"`, `"Library"`, `"Dashboard"`
- ✗ `"Back to Note"`, `"Back to Library"`

---

## Secondary Navigation Links ("View all / View more")

### Placement rule

Where the link goes is determined by where it leads, not which section it appears in.

| Destination | Placement | Rationale |
|-------------|-----------|-----------|
| **Different page/feature** (e.g. "View progress report →" inside Focus Areas) | **Inline with section header, right-aligned** | Header-row pattern signals "broader destination" — same convention as GitHub, Linear, Notion |
| **More of the same content** (e.g. "View All in Library →" below a note grid) | **Below the card/grid, right-aligned** | Proximity reads as "see more of what's above" |

### Style

Always use blue, never muted gray:

```tsx
className="shrink-0 text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
```

Muted gray (`text-foreground/60`) is easy to miss and is reserved for body descriptors, not navigation links.

---

## Sidebar Navigation Placement

| Destination type | Nav group |
|-----------------|-----------|
| Learning activity (Library, Progress, Public Library) | **MAIN** |
| Account / identity management (Profile, Settings) | **ACCOUNT** |

If a new destination is "a place users go to do or review something," put it in MAIN. If it is "a place users go to manage account state," put it in ACCOUNT. Do not bury learning destinations inside profile or settings.

---

## Two-Column Layout Threshold

Only use a two-column card layout when both columns have roughly comparable content weight. If one column needs new API data just to justify its existence, do not build the two-column layout yet — use a single card with sections instead.

---

## Summary: before implementing any new page

1. Does the page need a back link? → `<BackLink />` above the header, label = destination name only, no "Back to" prefix.
2. Does the page have a header? → `<PageHeader eyebrow="..." title="..." description="..." />`.
3. Does the section have a "view all" link? → different destination = header-inline blue; same content = below-card blue.
4. Is this a nav item? → MAIN for learning, ACCOUNT for account management.
