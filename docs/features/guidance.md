# In-App Guidance System (v0.9.0)

NoteLib uses a three-layer guidance model: always-visible micro text, one-time dismissible tips, and a static help center. The goal is to surface contextual information at the moment it's useful — not before.

## Anti-drift rules

- Never block a user action with a tip or tutorial
- Never show more than one `GuidanceTip` per card or section
- Micro guidance must fit one line — cut content before adding line breaks
- Dismissed tips must never reappear (stored in `localStorage`)
- Guidance adds context, not instructions — users should still be able to figure things out without it

---

## Layer 1 — Micro Guidance

Simple `text-xs text-foreground/60` paragraphs placed near form fields or action buttons. Always visible, no state.

### Locations

| Surface | Field | Text |
|---------|-------|------|
| Note editor | Subject | "Helps organize notes and filter by topic in your Library." |
| Note editor | Course / Program | "Used to personalize content and quiz recommendations." |
| Profile settings | Course / Program | "Used to tailor content and quiz recommendations to your field." |
| Note detail | Quiz action buttons | "Quick Review uses saved questions · Challenge Quiz generates new timed questions" |

---

## Layer 2 — GuidanceTip Component

**File:** `components/ui/guidance-tip.tsx`

A dismissible one-time tip strip. Reads from `localStorage` on mount. If the user has already dismissed the tip (`localStorage` key = `notelib-guidance-dismissed-{tipId}` = `"1"`), the component returns `null`. On dismiss: starts fade-out transition (200ms), writes `"1"` to `localStorage`, then unmounts.

**Props:**
- `tipId: string` — unique identifier, used as the `localStorage` key suffix
- `message: string` — the tip text
- `className?: string` — optional extra classes

**State storage:** `lib/guidance.ts` exports `hasSeenTip(tipId)` and `markTipSeen(tipId)`.

### Active tips

| tipId | Condition | Location | Message |
|-------|-----------|----------|---------|
| `note-detail-try-quiz` | Study Pack ready AND `quickSummary.attempts === 0` AND `challengeSummary.attempts === 0` | Note Detail > Performance Overview | "Try Quick Review or Challenge Quiz to start tracking your performance on this note." |

### Adding a new tip

1. Choose a unique `tipId` string (e.g. `"dashboard-weak-concepts"`)
2. Determine the render condition — keep it tight; only show when relevant
3. Render `<GuidanceTip tipId="..." message="..." />` inside the relevant section
4. Add an entry to the Active tips table above
5. Write a test that verifies: tip shows when unseen, tip hidden when seen, dismiss calls `markTipSeen`

---

## Layer 3 — Help Center

**Route:** `/help`  
**File:** `app/help/page.tsx`  
**Access:** Avatar dropdown menu ("Help") and Settings page header ("Help Center" link)

A static authenticated page with six accordion-style sections. Each section contains short Q&A pairs — no long prose, no modals.

### Sections

1. **Getting Started** — What is NoteLib, creating a note, what is a Study Pack
2. **Creating Notes** — What to put in a note, Subject and Course fields, editing after generation
3. **Study Packs** — Summary tab, Key Concepts tab, Quiz tab
4. **Quiz Types** — Quick Review, Challenge Quiz, Adaptive Practice
5. **Performance Tracking** — Where to see results, Weak Concepts, Strongest Notes
6. **Exporting Quizzes** — How to export, file format and naming

### Rules for maintaining Help content

- Answers stay under 3 sentences
- Each Q&A describes a single concept
- If a feature is renamed or removed, update the relevant help section immediately
