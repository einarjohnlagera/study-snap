# navigation.md - Testing Notes

## Back Navigation

Verify these cases for back navigation links:

### Pages with a back link (sub-pages)

- Note Details (`/notes/{id}`) shows `← Library` linking to `/library`
- Public Note Details shows `← Public Library` linking to `/public/library` for authenticated users; no link for anonymous users
- Public note subject listing page (`/public/library/{subject}/{slug}`) shows `← Public Library` for authenticated users
- Quick Review shows `← Note` linking to the note detail page at the top of the page
- Challenge Quiz shows `← Note` linking to the note detail page (hidden while quiz is running — `Leave Quiz` appears instead)
- Adaptive Practice shows `← Note` linking to the note detail page at the top of the page
- Create Note shows `← Library` linking to `/library`
- Edit Note shows `← Note` linking to the note being edited (`/notes/{id}`)
- Profile / Edit Profile shows `← Profile` linking to the user's public profile page
- Public Profile (non-owner viewing another user) shows `← Public Library` linking to `/public/library`
- Learn article pages show `← Learn` linking to `/learn`
- Shared Study Pack (`/p/{token}`) shows `← Home` linking to `/`

### Pages without a back link (main pages)

- Dashboard — no back link
- Library — no back link
- Public Library — no back link
- My Profile (owner viewing their own public profile) — no back link
- Settings — no back link

### Back link style

- All back links use `BackLink` component (`components/ui/back-link.tsx`)
- Displayed as `← {label}` with a small `ArrowLeft` icon
- Blue link color (`text-blue-600 dark:text-blue-400`), underlines on hover — matches the "View Full Notes →" link style
- Not bold, not a button — rendered as a plain `<a>` tag
- Left-aligned, at the top of the page above the header card
- Never placed at the bottom of the page
- Compact — does not push the header far down on mobile

### Back link destinations

| Destination | Label | Route |
|-------------|-------|-------|
| My Library | Library | `/library` |
| Public Library | Public Library | `/public/library` |
| Note Details | Note | `/notes/{id}` |
| My Profile (public) | Profile | `/public/profile/{userId}` |
| Learn index | Learn | `/learn` |
| Home | Home | `/` |

### Text rules

- Do NOT use `Back to Library`, `Back to Note`, etc. — just the destination name
- The arrow already communicates direction — no need for the word "Back"
- All "Back to Note" inline card action buttons (inside error/limit/complete cards in quiz pages) use the label `Note` to match this convention

### Mobile behavior

- Back link is compact and does not consume excessive vertical space
- Spacing between back link and header card is consistent across pages
- No back link displayed when it should not be (main pages)
