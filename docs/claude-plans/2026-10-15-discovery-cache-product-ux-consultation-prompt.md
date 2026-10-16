# Explore discovery sections: how stale may they be? Consultation prompt for Product UX

**How to use this:** paste `docs/gpt-contexts/GPT_CONTEXT.md`, then
`docs/gpt-contexts/SURFACES_AND_FEATURES_CONTEXT.md`, then everything below the horizontal rule, into
a Product UX session. The part below the rule is self-contained: it carries the incident, the real
production numbers and the constraints, so the answer does not depend on reading the repo.

**Why it exists.** `v0.173.0 — Cached Catalog` (kicked off 2026-10-15) scopes an in-process cache for
the Explore discovery sections. Before the Codex brief is written, the owner asked for Product UX's
opinion on the open choices: what may go stale, for how long, and what should refresh it.

**Companion documents** (do not paste; reference if asked for depth):
`docs/claude-findings/2026-10-14-db-oom-memory-ceiling-public-catalog.md` (diagnosis) and
`docs/claude-plans/2026-10-14-db-oom-memory-ceiling-fix-plan.md` (fix plan; the cache is Leg D's
"Durable follow-up").

**Indexing note:** this is a finished one-off consultation prompt, so it falls under the Backlog
Index's *release artifact* exemption. Record the resulting decision on `v0.173.0`'s scope item in
`RELEASES.md` and on the "Production DB memory ceiling" Backlog row, not in a new row.

**Facts below were read on 2026-10-15** from code on `releases/v0.173.0` and from read-only queries
against production.

---

# NoteLib: may the Explore discovery sections lag reality by a few minutes, and what should refresh them?

I need product-UX judgment on a narrow question inside an ongoing production-stability fix. A hard
budget constraint applies.

## The constraint

**NoteLib is pre-revenue, with no paying users yet.** The production database is Render's
`basic_256mb` plan (0.1 CPU, 256 MB). It was killed by the operating system for running out of memory
**9 times in 7 days** (2026-10-08 → 10-14). Each kill is a full crash-recovery that drops every
in-flight request. **Upgrading the plan is deferred by owner decision**, so the fix has to come from
doing less work per request.

## What already shipped (`v0.172.0`, deployed 2026-10-15)

The Explore page (`/explore`, and the anonymous SEO page `/public/library`) opens on a discovery
overview with three sections of **6 cards each**:

- **⭐ Featured Notes**: an engagement score with time decay.
- **🔥 Most Popular**: engagement, excluding anything already in Featured.
- **🆕 Recently Added**: newest first, excluding both of the above.

Ranking Featured and Popular means scoring **every public note (2,001 today)** by its copies, likes and
views. `v0.172.0` made that one database statement per page load instead of two, and stopped the page
re-fetching when a learner returns to the overview. Whether that was enough is measured on
2026-10-22 (7 days of kill counts after deploy).

## What this release proposes

Cache the **result of the ranking**, meaning which note ids appear in each section and in what
order, in the backend's memory for a short time (the fix plan says 60–300 seconds). Every visitor
inside that window gets the same ranked lists without the database scoring 2,001 notes again.

**What a card shows would NOT go stale. This is the key fact, and it is easy to assume otherwise.**
Each card's copy, like and view counts, and the viewer's own "liked" state, are loaded fresh on every
request by a separate, cheap query over just the 18 displayed notes. The cache would freeze only:

1. **Which notes are in each section, and their order.** A note whose engagement jumps would move up
   (or into Popular) up to one cache window late.
2. **Recently Added.** A newly created public note would appear up to one window late. The section
   orders by the note's **creation** date, not its publish date. So an older note made public today
   does not jump to the top whether or not there is a cache.
3. **Removals degrade, but nothing leaks.** If a cached note is made private or deleted, the
   per-request card query re-checks `visibility = 'PUBLIC'` and drops it. The section then shows **5
   cards instead of 6** until the window expires. A private note is never shown.

The ranking does not depend on who is viewing. One cached copy serves everyone, signed-in or
anonymous.

## Production numbers (last 14 days)

| | |
|---|---|
| Public notes | 2,001 |
| Public notes created | **0** |
| Public notes updated | 5 |
| Likes | **0** |
| Public-note copies | 3,166, but from only **10 users in 23 distinct minutes**: bulk Study Plan adoptions, not one-at-a-time copies |
| Public note page views | 13,844 |
| `EXPLORE_VIEWED` events | 45 (signed-in `/explore` only; the anonymous `/public/library` page has no page-view event) |
| Calls to the discovery endpoint | **Unknown.** Render emits no request logs for this service. The diagnosis inferred concurrent load from CPU spikes, not from counts. |

So the catalog barely changes in content day to day. What moves is engagement: mostly views, plus
copies arriving in bursts.

## The questions

1. **Is it acceptable for section membership and order to lag by a few minutes?** If yes, what
   window: 60 s, 120 s, 300 s, or longer given how slowly the catalog changes? If no, why not, given
   the constraint above?
2. **Should anything refresh the cache early?** The candidates, each a small code hook:
   - **(a) A note being published or unpublished.** A curator who creates and publishes a note, then
     opens Explore to check it, would not see it in Recently Added until the window expires. No public
     note was created in the last 14 days. Unpublishing already
     degrades safely (5 cards instead of 6).
   - **(b) A copy or a like.** The counts on cards are already live, so this would only make the
     *ordering* react faster. Copies arrive in adoption bursts of hundreds, so refreshing on every
     copy would mean recomputing many times per burst unless it is debounced.
   - **(c) Nothing: time only.** Simplest, and the cheapest for the database.
3. **Should Recently Added be cached at all?** It is a cheap query on its own. It is cached in this
   proposal only because it must exclude whatever Featured and Popular returned. Caching all three
   together keeps the sections consistent with each other.
4. **Does any of this need user-facing copy**, for example telling a curator their new note will
   appear shortly? Or is silence fine?
5. **Anything in the proposal you would push back on outright**, for example a reason a lagging
   "🔥 Most Popular" section would mislead a learner?

## Fixed, not up for decision here

- No database plan upgrade and no Redis or other new infrastructure. The backend runs as **one
  instance** (verified 2026-10-15), so an in-memory cache is consistent across visitors.
- No change to the ranking formula, weights, decay or thresholds, and no change to the paginated
  "browse all" listing.
- The cache does not ship before 2026-10-22, so it cannot muddy that day's measurement of the
  previous fix.

## What I need back

For each of questions 1–5: a decision, one or two sentences of rationale, and anything you would want
measured afterwards. Flag clearly if you think the cache should not ship at all.
