# Public note discovery rails — keep popularity ranking or switch to most-recent? Consultation prompt for product UX

**How to use this:** paste everything below the horizontal rule into a fresh (or existing) product-UX
session. It is self-contained — it carries the product context, the incident that forced this decision,
the real production numbers, and the hard technical/budget constraints, so the consultant does not
recommend something we cannot afford to run.

**Why it is shaped this way.** This is not a discretionary product-polish question. It is the one open
decision inside an active outage fix: the backend has restarted 24 times in the last 30 days because two
discovery rails on the public note page recompute a full popularity ranking across every public note in a
program or subject, on every anonymous visit, against a database sized for a pre-revenue product. The fix
removes the unbounded recomputation either way — what's undecided is only what the rails should then be
*ordered by* once they're cheap to run.

**Companion documents** (do not paste; reference if the consultant asks for depth):
`docs/claude-findings/2026-10-01-prod-restarts-public-note-page-fanout-db-saturation.md` (the full
diagnosis — root cause, 24-incident history, measurements) and
`docs/claude-plans/2026-10-01-public-note-page-fanout-fix-plan.md` (the engineering fix plan this
decision is Leg B of).

**Indexing note:** per the Backlog Index invariant, planning documents need an index row. This is a
finished one-off consultation prompt, so it falls under the narrow *release artifact* exemption and does
not need its own row — attach the resulting decision to the outage finding's existing Backlog row instead.

---

# NoteLib — should "more notes like this" on a public note page show what's popular, or what's newest?

I need product-UX judgment on one narrow question inside an active production-outage fix, with a hard
budget constraint on the table.

## What NoteLib is

A notes-first study workspace for Philippine learners — board-exam reviewers, college students, teachers,
professionals. Learners capture notes, generate AI Study Packs from them, and practise with quizzes.
Public notes (curator-published, free to read with no account) are the product's SEO and discovery
surface — a visitor usually lands here from search or a shared link, not from being logged in already.

## The constraint that rules out the "just pay for more headroom" answer

**We are pre-revenue. There are no paying users yet, and every dollar of infrastructure spend is coming
straight out of runway.** The database this bug saturates is `basic_256mb` — 0.1 CPU, 256 MB — specifically
because the product does not yet justify a bigger one. **Raising the database plan is not an option in
this decision.** The fix has to work within the current plan's ceiling, which means the answer should
lean toward whichever option costs the database less, not whichever looks marginally better and costs
more, unless the UX case for the pricier option is strong enough to justify deliberately picking it anyway.

## What's actually broken, briefly

Every public note page renders two "discovery" rails below the note itself:

- **"More from {Subject}"** — 3 cards.
- **"More {Program} notes"** — 4 cards (e.g. "More Civil Engineering notes").

To fill 7 cards total, each rail currently walks **every page** of that subject's or program's entire
public-note list (civil Engineering: 875 notes, 18 pages; Accountancy: 358 notes, 8 pages), computes a
full popularity ranking across the whole thing server-side on *every single page*, pulls it all into the
Next.js server, and only then picks the top few and re-sorts them in JavaScript. One render of a popular
Civil Engineering note can issue 30+ database statements, ~18 of them expensive ranking queries, to show 7
cards. A burst of repeat visits to one note (a shared link being opened a dozen times, a crawler re-fetch)
has caused the database to peg near its CPU ceiling and the backend to fail its health check and restart —
24 times in the last 30 days, with outages up to ~45 seconds of full downtime each time.

**The fix removes the "walk every page" behavior either way** — both options below change the rails to one
single, bounded database page fetch (page 0, a handful of rows) instead of the full walk. The ranking
behavior is the only part still open.

## The current ranking, exactly

Popularity score = `views + 3×copies + 2×likes`, computed in application code, highest first. This is what
both rails show today — e.g. "More Civil Engineering notes" shows the 4 most-viewed/copied/liked Civil
Engineering notes with a ready Study Pack, not the newest ones.

## The two options on the table

| | **Option 1 — most recent** | **Option 2 — keep popularity** |
|---|---|---|
| What it shows | The newest ready notes in that subject/program | The same popularity ranking as today, just computed over one page instead of the whole list |
| Database cost per rail, per render | One plain `ORDER BY created_at` page fetch + one count — cheap, index-friendly | Still 2 ranking queries + 2 ranked counts — real work, just **bounded to one page instead of up to 18** |
| Visible change to a returning visitor | Yes — the cards are different, and will reorder as new notes are published | None |
| Growth risk | Flat — cost doesn't scale with traffic | The underlying view-count table that feeds the ranking is already 49,499 rows and grew by 13,000+ in a single day during one crawler surge; every anonymous hit that reaches the ranked path adds to what future ranking queries scan |

**Neither option is "don't fix it."** Both stop the unbounded fan-out that's causing the outages. The
question is purely: given we have to choose a cheap, bounded way to fill these two rails, is showing the
newest notes an acceptable (or even better) substitute for showing the most popular ones — on a page a
first-time anonymous visitor almost certainly reached from a search result or a shared link, not a
returning user who'd notice or care that the list is "the same as always"?

One piece of precedent: a third, smaller rail further down the same page ("More in {Subject}", 2+ cards)
already switched to most-recent for the same cost reason, on 2026-09-06, without anyone raising a UX
objection since.

## What I want your judgment on

1. **Does popularity ranking earn its keep here**, specifically for a cold, mostly-anonymous discovery
   context (a visitor already reading one note, being offered 3-7 more), versus a context where it would
   matter more (e.g. a logged-in user browsing a full subject listing to decide what to study next)?
2. **Is there a UX cost to switching to most-recent that's worth paying 4-5x the database cost to avoid**,
   given we can't afford to just buy more database and this cost scales with traffic we're trying to grow?
3. **Is there a third option** — something that reads as curated/quality-signaling without requiring a
   live ranking computation per request? (For example: a single pre-computed "featured" flag a curator
   sets occasionally, a simple recency-with-a-quality-floor rule, or something else.) Flag it even if it
   needs its own follow-up scoping — I'm not asking you to design it in full, just to say whether it's
   worth a follow-up.
4. **Does your answer differ between the two rails** — "more in this subject" versus "more in this
   program" — or is this genuinely one decision?

## Hard constraints on any answer

- Whatever is chosen must be expressible as **one bounded SQL page fetch per rail** (page 0, a small fixed
  size) using a sort the database can execute as a plain index/`ORDER BY` — not a re-ranking computed over
  an unbounded result set. In this codebase that currently means **recent** or **alphabetical by title**;
  anything else (including "popularity, but just page 0 of it") is possible but costs meaningfully more
  per request than those two, as shown in the table above.
- No solution that re-introduces walking more than one page per rail.
- This is one leg of an active outage fix going into the next release. A "needs more design work before
  we can decide" answer is acceptable, but should come with a recommended *default* to ship now, since the
  restarts are ongoing.
