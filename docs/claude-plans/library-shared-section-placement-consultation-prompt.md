# Consultation prompt — where does "Shared with you" belong in the Library?

**Status:** open question, no decision taken. Raised by the owner 2026-08-27 from a live screenshot.
**Audience:** an external GPT session acting as long-term PM/UX strategist for NoteLib.
**How to use:** paste `docs/gpt-contexts/GPT_CONTEXT.md` (core brief) first, then
`docs/gpt-contexts/SURFACES_AND_FEATURES_CONTEXT.md` (the Library surface module), then this file.
**Both were restamped to `v0.92.0` on 2026-08-27 specifically for this consultation.**

---

## 1. The question

`v0.91.0` shipped controlled note sharing. A recipient's shared notes appear in a **"Shared with you"** section
in the Library. That section renders **below the owned-notes grid, and below the grid's "Load more" button.**

**Is that placement right, and if not, what replaces it?**

## 2. What we have already established — please challenge it, but read it first

We have done the code read. These are facts, not impressions:

- **The owned-notes grid paginates at `LIBRARY_PAGE_SIZE = 20`**, two columns.
- **The shared section renders after the "Load more" button**, not before it.
- **The section is hidden entirely when empty** (deliberate, and correct — see constraint C2).
- **The shared list has its own independent pagination** ("Load more shared notes").
- **`sharedItems` is fetched on mount in its own `useEffect`, independent of the owned-notes fetch.** It starts
  empty, so anything rendered from it at the TOP of the page appears after first paint — a naive header pointer
  would shift the whole page. This is a real implementation constraint on any "announce it at the top" answer.
- **`library/page.tsx` is ~2,400 lines with no tab structure.**

**Our current reading of the defect — the part most worth challenging:** this is not primarily a *distance*
problem, it is a *terminal signal* problem. "Load more" reads as the end of the content, so material below it is
page furniture. If that reading is right, the fix is to **announce** the section, not to **move** it. If that
reading is wrong, say so — it drives everything else.

Sizing: with 20 notes the section is ~10 rows past the stop cue. Each "Load more" click pushes it 20 cards
further. A curator with a 77-note library would be three clicks deep.

## 3. Hard constraints — these are ratified and NOT open for re-litigation

Please design within these. If you believe one is wrong, say so **explicitly and separately** rather than
quietly assuming it away.

- **C1 — Shared notes are NEVER mixed into the owned-notes grid.** Ratified in `v0.91.0`. The reason is not
  cosmetic: the Library is the learner's own workspace, ownership drives every action available on a card
  (edit, delete, visibility, add-to-plan), and shared notes support none of them.
- **C2 — The section is hidden when empty and must stay hidden when empty.** Most learners have zero shared
  notes and always will. **Any answer that costs something when the section is empty is worse than the status
  quo**, because it taxes the majority to serve a minority.
- **C3 — Shared notes cannot join a Study Plan without being copied first.** Deliberate v1 boundary.
- **C4 — The learner's own material is the priority.** This is why we have already rejected *"move the section
  above the owned notes"*: it would fix discoverability by making every visit slightly worse for everyone,
  including the many learners with nothing shared.
- **C5 — No new backend work is wanted for this.** The shared list, its count and its pagination all already
  exist client-side.

## 4. What we are NOT asking

- **Not** whether to build a `[ My Notes ] [ Shared with me ]` tab shell. That is understood to be the eventual
  answer and is priced as its own release (~2,400-line component, no tab shell today). **If your answer is
  "just do the tabs", say so — but say what the interim should be**, since the tab work will not ship this week.
- **Not** a redesign of the Library.
- **Not** anything about who may share, what is visible, or the privacy model. All settled.

## 5. Context that should change your answer

- **Adoption is ZERO.** `linked_learner_relationships` was completely empty in production on 2026-08-26. Nobody
  is hitting this today. The feature's discoverability problem is one layer up (does anyone form a connection
  at all), and `v0.91.0` fixed the two known causes — a landing page advertising it as *Coming Soon* for three
  releases after it shipped, and a Help Center with no supporter section.
  **⚠️ Consider explicitly: does zero adoption argue for fixing this NOW (so the first arrivals are not lost)
  or for waiting (so we design against real behaviour rather than a guess)?** We genuinely do not know.
- **The motivating use case is a parent and a child**, or a tutor and a student — the recipient is often the
  LESS sophisticated user, and often has FEW notes of their own. A learner with 3 notes has a very different
  experience of this layout than one with 77. **Our sizing above assumed the 77 case; the 3-note case may
  invert the argument entirely.**
- **This product has a documented failure mode of shipping a stopgap that becomes permanent.** If the pointer
  is a stopgap, we would like it named as one, with what makes it retire.

## 6. What we would like back

1. **A verdict on the framing** — terminal-signal problem, distance problem, or neither?
2. **A recommended interim answer** that satisfies C1–C5, with its cost when the section is empty.
3. **What you would NOT do**, and why — we find the rejected options as useful as the chosen one.
4. **A trigger for revisiting it** — what observation should make us build the tab shell?
5. **Whether this ships now or opens the next release.** We have already folded five items into `v0.92.0` and
   signoff keeps receding; that is a real cost, not a rhetorical one. Say which you would choose.

## 7. One thing we may be getting wrong

We have been reasoning about this as a *layout* problem. It may be an *information* problem: the learner has no
model of "someone shared something with me" as a thing that happens, because nothing outside the Library ever
tells them. There is a Dashboard, a nav, and a Learning connections page — none of which mention shared
material. **If the real answer is "the Library is the wrong place to solve this", we would rather hear that.**
