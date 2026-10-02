# Public Note discovery rail ordering — owner decision (2026-10-01)

**Status: approved, binding. Supersedes the open rail-order question in
`docs/claude-plans/2026-10-01-public-note-page-fanout-fix-plan.md` (Leg B).** Reached via
`docs/claude-plans/public-note-discovery-rail-ordering-product-ux-consultation-prompt.md`. This
package is content, not re-derived here — read it for the full product reasoning; this file is the
instruction surface for implementation.

---

## The decision

For the two contextual discovery rails on an individual Public Note page — **"More from {Subject}"**
and **"More {Program} notes"** — switch ordering to **most recent eligible/ready public Notes**, via a
single bounded page-0 fetch per rail with a small fixed result size. **Do not preserve live popularity
ranking on these rails.** Labels stay as-is ("More from {Subject}", "More {Program} notes") — they
describe the relationship, not the ranking mechanism, and don't need to change to "Recent".

## Why

These rails are secondary discovery for a visitor who already reached a Public Note, usually via search
or a shared link. They answer "what else can I explore from this Subject/Program?", not "what are the
globally most popular Notes?" Live popularity ranking does not earn its request-time cost here. The
production failure mode (24 restarts/30 days) is unacceptable, the database is deliberately sized for a
pre-revenue product, and **raising the DB plan is not an option** — optimize the read path, don't buy
headroom.

## Reliability invariant (adopt)

> Anonymous Public Note traffic must never pay the cost of calculating a global popularity ranking.

Broader principle: use live queries for discovery signals that are cheap to derive; precompute expensive
aggregate rankings only when popularity is genuinely useful. **Do not build new ranking infrastructure in
this release** unless the approved engineering plan already requires it — the immediate goal is
eliminating the expensive request-time behavior, nothing more.

## Rail contract

Per rail: one bounded DB page fetch; fetch only the candidates needed plus the minimum bounded
over-fetch to exclude the current note (if required); use the existing cheap/index-friendly `recent`
ordering; preserve existing eligibility semantics (public visibility, Study Pack readiness, Subject/
Program match, current-note exclusion, lifecycle requirements) unless the outage plan names one as
wrong. Do not: walk subsequent pages; fetch the whole Subject/Program; compute app-side popularity;
perform live ranking; introduce N+1; or swap one unbounded query for another.

## Recency is the safe baseline, not a reversal of product philosophy

Do not record this as "NoteLib decided popularity is bad." The narrower claim: **contextual Public Note
rails don't justify live popularity computation.** Popularity remains potentially useful on a deliberate
browse surface (Explore) — but expensive popularity should eventually be precomputed/cached, not
recomputed per anonymous request. Not built automatically in this release.

**Popularity-fairness rationale, recorded without overstating it as A/B evidence:** the existing score
(`views + 3×copies + 2×likes`) can create a rich-get-richer loop (popular → shown more → more views →
more popular), and views are inflated by crawler/search/shared-link traffic independent of quality.
Recency gives newly published knowledge a deterministic, temporary exposure window without a new
recommendation algorithm. No randomness added — deterministic recency is sufficient for this release.

## Follow-up audit performed (read-only) — see the Release Implementor's report

- **Explore's `/notes/public/discovery-sections`**: already bounded (fixed `limit=6` per section, no
  pagination walk, no count query). Still runs live `POPULAR`/`FEATURED` ranking per request — not
  cached — which is the legitimate future "precompute" case, not an active hazard. **Not touched in this
  release.**
- **Subject listing page, exam hub pages, sitemap**: share the exact "no `sort` → ranked branch → walks
  every page" defect, but are already covered by the existing engineering plan's item 3 (send an
  SQL-orderable `sort` from the shared walker) — not a new discovery.
- No other anonymous surface was found presenting the same immediate production-safety failure mode.

## Discovery v2 — backlog direction only, not implemented

Future question: can NoteLib use existing structured learning metadata (curriculum proximity, Section/
Subject Plan, Subject, Applicable Program, recency, possibly precomputed popularity) for cheap
deterministic contextual discovery, aligned with "Study Plans define the journey"? **Not implemented.**
No recommendation engine, no LLM calls, no vector search, no randomness, no manually maintained Featured
system — this is a backlog note for later investigation only.

## Explicit non-goals for this release

Do not: upgrade the DB plan; retain live popularity on these two rails; walk multiple pages; disguise an
expensive query as "bounded"; add Redis, a recommendation service, vector search, or an LLM call for
this; add random ranking; add a Featured flag; redesign Explore or the Public Note page; change global
Public Library ordering; remove popularity everywhere; implement Discovery v2; write to production
manually; or expand scope because adjacent improvements look attractive.

**Core invariant:** anonymous traffic should consume roughly constant discovery-query work per Public
Note render regardless of whether NoteLib has hundreds, thousands, or tens of thousands of public Notes.
