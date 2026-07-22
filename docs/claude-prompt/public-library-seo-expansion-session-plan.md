# Session Plan — Public Library SEO Expansion & Broader Google Surfacing

> **Purpose.** The prior SEO Fable session (`seo-strategy-out/01-seo-strategy.md`) diagnosed why NoteLib
> doesn't surface for exam-named searches ("free PNLE notes") and shipped a narrow fix (v0.50.4, "free
> reviewer" vocabulary on the 3 Exam Hub pages + all Public Library subject pages). The user then searched
> "biology reviewer for grade school" directly and confirmed NoteLib does not appear at all — dominated by
> Scribd, FilipiKnow, CliffsNotes. This surfaced a genuinely new question the prior session didn't scope:
> should NoteLib invest in broader Google surfacing beyond the 3 curated Exam Hubs, and if so, where and
> how — this session is planning-only, sourcing candidates for a future release, not a scoping pass.

## Why this is a distinct question from the prior session, not a rerun

The prior session's mandate was narrow by design: "for PNLE and the other exam communities NoteLib
already serves." It correctly stayed inside that scope and produced P1–P9. This session asks the question
the user just raised empirically: general Public Library subject pages (Biology, and hundreds of other
subjects) now carry the same "free reviewer" vocabulary fix (shipped same day, v0.50.4), but that alone
doesn't answer whether pursuing broad general-education queries is a good use of NoteLib's limited
domain-authority-building effort, or whether it dilutes focus away from the board-exam-taker market the
product and prior SEO work were built around.

## What already shipped (treat as ground truth)

- v0.50.4: Exam Hub pages (`/exam/ale`, `/exam/pnle`, `/exam/let`) and **all** Public Library subject
  pages (`/public/library/{subject}`) now use "Free {X} Reviewer Notes & Practice Quizzes" style titles,
  descriptions, and H1 sublines instead of "Study Notes, Summaries, and Quizzes."
- The prior session's P1 (Search Console setup), P3 (exam-named Learn guides), P4 (Exam Hub subject
  breakdown + full-inventory path), P5 (ItemList JSON-LD), P6 (organic-referrer analytics) remain
  unscoped — see `docs/product/ROADMAP.md`'s Backlog Index. This session should treat those as already
  decided in principle for the Exam Hub surface specifically, and evaluate whether/how they extend to the
  broader Public Library rather than re-deriving them from scratch.

## A confirmed, concrete gap (already known, don't spend session time rediscovering it)

A live Google search for "biology reviewer for grade school" (2026-07-17) returns zero NoteLib results
in the first page — dominated by Scribd document uploads, FilipiKnow, CliffsNotes, SlideShare. This is
being handed to Fable as ground truth, not a rediscovery task.

## Hard constraints repeated in the prompt (Fable starts cold)

Same locked constraints as the prior SEO session — "curation, never generation," no pricing/paywall/
funnel changes, no new quiz modes, public note visibility/ownership rules locked, build on the existing
shipped SEO architecture. Repeated in full in the prompt file.

## New question this session must actually answer

The prior session assumed the market (Philippine board-exam takers). This session must explicitly address
whether NoteLib's organic-search investment should stay scoped to that market, or whether general
K-12/higher-ed subject queries (like "biology reviewer for grade school") are worth pursuing — and if so,
under what conditions (content depth, distinct curation effort, etc.), given the same domain-authority and
thin-content constraints apply universally, likely harder on broader/more competitive general-education
terms than on the narrower exam-specific niche.

## Prompt

Full paste-ready prompt: `public-library-seo-expansion-prompts/01-public-library-seo-expansion.txt`

## Output

`docs/claude-prompt/public-library-seo-expansion-out/01-public-library-seo-expansion.md` (once the user
runs the session)

## Status

Run 2026-07-17. Output in `public-library-seo-expansion-out/01-public-library-seo-expansion.md`.
Verdict: **not now** for broad general-subject SEO investment (asymmetric to the exam-hub case — no
curation machinery, unknown/likely-thin depth, worse competitive field, dilutes a young domain's topical
authority). But a real, more-urgent-than-expected finding: the same-day P2 vocabulary ship gave every
subject page — including 1-note or 0-note ones — an indexable "Free {Subject} Reviewer" title with no
sitemap depth filter, a genuine thin-content liability that can drag down the exam hubs. Recommended
narrow fix: N1 (content-depth indexation gate, `noindex` below a note-count threshold) after N2 (one SQL
depth-inventory query). Reviewed by the user before scoping — see `RELEASES.md`/`ROADMAP.md`.
