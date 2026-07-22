# Session Plan — SEO / Organic Search Strategy

> **Purpose.** The user asked, in plain terms: if a nursing student searches "free PNLE notes" on
> Google, why doesn't NoteLib show up, and what would it take to fix that. This session is planning-only,
> sourcing candidates for a future release — not a scoping pass, not implementation. Same discipline as
> the other Fable sessions in this directory: hard constraints stated up front so Fable doesn't propose
> things that don't fit NoteLib, output classified through `docs/skills/roadmap-feature-audit.md`'s
> four-tier framework, nothing here is scoped or kicked off until a human reviews it.

---

## Why this needed real research before writing the prompt, not just a forwarded question

"We already have some SEO, right?" — yes, more than the user may realize, and the prompt below states
it directly rather than making Fable rediscover it blind (same discipline as the retention diagnosis's
"known-stale-input-excluded" section). Verified against the actual codebase 2026-07-15:

**What's real and already shipping:**
- A dynamic `frontend/app/sitemap.ts` and `robots.ts` (`allow: "/"`, only app/auth-gated routes disallowed)
- `generateMetadata`/`buildPageMetadata` used across ~10 route types for title/description/OG/canonical
- JSON-LD structured data already emitted: `WebSite` + `FAQPage` (landing), `CollectionPage` (Public
  Library index, per-subject pages, and all three Exam Hub pages), `Article` (Learn guides, public notes)
- A dedicated Exam Hub surface (`/exam`, `/exam/ale`, `/exam/pnle`, `/exam/let`) built specifically for
  exam-named search intent — exactly the "PNLE" shape of query in the user's example
- 10 Learn content-marketing articles (generic study-technique how-tos, not exam-specific)
- `docs/features/seo.md` already documents the messaging rule (notes-library-first, not "generic AI tool")

**A confirmed, concrete gap — not a guess:** all three Exam Hub pages (`/exam/ale`, `/exam/pnle`,
`/exam/let`) are entirely absent from `sitemap.ts` despite being `allow`-indexed and already emitting
`CollectionPage` JSON-LD. They are the single most query-relevant pages in the app for exactly the kind
of search the user described, and they are invisible to the sitemap Google actually crawls from. This is
handed to Fable as a known fact, not something it needs to rediscover — and it's cheap enough that it
may not even need to wait for the Fable session (see "Immediate, no-brainer fix" below).

**A real constraint Fable must respect:** per the 2026-07-15 production pull already on record
(`docs/claude-prompt/retention-diagnosis-session-plan.md`, "Interim-window pull results," Query 3),
Official Review Set coverage per exam bucket is real but modest — ALE 52, PNLE 63, LET 43 notes. A
searcher landing on a thin page hurts both search ranking (thin-content signal) and conversion (looks
empty). Any content-volume strategy Fable proposes has to be honest about this starting depth, not
assume it away.

**No search-performance measurement exists in the codebase** (no Search Console verification, no
tracked organic-search funnel) — Fable is asked to address how the user would know if any of this is
working, not just propose tactics blind.

## Immediate, no-brainer fix — separate from the Fable session

Adding the three Exam Hub URLs to `sitemap.ts` is a same-day, near-zero-risk fix (one array literal,
reusing the existing `EXAM_HUB_SLUGS`/`EXAM_HUBS` config already imported elsewhere) — it doesn't need
Fable's judgment, a roadmap entry, or a release slot. Flagged to the user directly; can be done
immediately on request, independent of whatever Fable comes back with.

## Hard constraints repeated in the prompt (Fable starts cold)

- **"Curation, never generation" stays locked.** No proposal that mass-generates AI-written landing
  pages or reviewer content per keyword — NoteLib's Public Library is real user-submitted notes, and
  `docs/features/seo.md`'s messaging rule already forbids "generic AI-tool" positioning. Thin
  AI-content-farm SEO is explicitly out.
- No pricing, paywall, quota, or conversion-funnel change riding along.
- No new quiz modes, no touching the 5-mode `EXAM_MODES.md` contract.
- Public note visibility/ownership rules are locked — no proposal that forces notes public or alters who
  controls note visibility for SEO's sake.
- Build on the shipped SEO architecture (sitemap/robots/JSON-LD/`buildPageMetadata` pattern) rather than
  proposing a parallel system.

## Prompt

Full paste-ready prompt: `seo-strategy-prompts/01-seo-strategy.txt`

## Output

`docs/claude-prompt/seo-strategy-out/01-seo-strategy.md` (once the user runs the session)

## Status

Run 2026-07-17. Output in `seo-strategy-out/01-seo-strategy.md`. Diagnosis: authority (unfixable by code)
is the dominant blocker on head terms; vocabulary mismatch ("notes" vs. market's "reviewer") is the
biggest fixable gap. 9 prioritized candidates (P1–P9), classified Core Feature/Polish/Future
Enhancement/Low-Priority. Reviewed by the user before deciding what folds into v0.50.4 — see
`RELEASES.md`/`ROADMAP.md` for the resolution.
