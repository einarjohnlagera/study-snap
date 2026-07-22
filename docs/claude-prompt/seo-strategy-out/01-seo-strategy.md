# Planning Analysis: Organic Search / SEO Strategy (PNLE, LET, ALE discovery)

Run 2026-07-17 via the `fable` model. Prompt: `../seo-strategy-prompts/01-seo-strategy.txt`.
Session plan: `../seo-strategy-session-plan.md`.

Grounding files referenced throughout: `frontend/app/sitemap.ts`, `frontend/app/robots.ts`, `frontend/lib/exam-hub-config.ts`, `frontend/app/exam/[slug]/page.tsx`, `frontend/lib/learn-guides.ts`, `frontend/lib/public-library-discovery.ts`, `docs/features/seo.md`, `docs/features/exam-hub.md`.

---

## 1. Diagnosis: why "free PNLE notes" doesn't find NoteLib

Ranked from dominant blocker to minor, with evidence from the codebase.

### Blocker 1 (dominant, and unfixable by code alone): domain authority in a brutally competitive SERP

"Free PNLE notes" and its bigger siblings ("PNLE reviewer", "free NLE reviewer", "nursing board exam reviewer") are owned by entrenched players: Nurseslabs, RNpedia/RNspeak, Scribd uploads, review-center blogs (Carl Balita, Royal Pentagon), Facebook groups, and YouTube. These sites have a decade of backlinks and topical depth. NoteLib is a young domain with essentially zero inbound links. On competitive head terms, perfect on-page work on a zero-authority domain gets position 40, not position 4. This means: (a) the near-term winnable battlefield is long-tail and sub-topic queries, not the head term; (b) some portion of the answer is off-page (links, mentions in nursing-student communities) and cannot be a code deliverable.

### Blocker 2 (co-dominant, and fully fixable): vocabulary mismatch — the site says "notes," the market searches "reviewer"

Filipino board-exam takers overwhelmingly search **"reviewer"** ("PNLE reviewer", "free LET reviewer 2026", "med surg reviewer PDF"), and year-qualified variants. Audit of what actually ships:

- `/exam/pnle` title (`app/exam/[slug]/page.tsx:138`): `Philippine Nurse Licensure Examination (PNLE) Notes and Practice Quizzes | NoteLib`. Contains "PNLE" and "Notes" — decent — but the word **"free" appears nowhere** in any exam hub metadata, H1, or body copy, and "reviewer" appears only in the ambiguous phrase "for nursing board exam reviewers" (`exam-hub-config.ts:25`), where it means *people*, not *review materials*. Google's understanding of the page never connects it to the "reviewer (material)" query family.
- All 10 Learn guides (`lib/learn-guides.ts`) are generic study-technique how-tos. Not one contains "PNLE," "LET," or "ALE" in its title or slug.
- Public note titles are user-authored, so long-tail coverage there is accidental, not designed.

### Blocker 3 (secondary): page architecture hides the depth that exists

Production has 63 PNLE-bucket notes — real content — but the hub page renders at most **18** of them (3 discovery sections × `DISCOVERY_SECTION_LIMIT = 6`, `lib/public-library-discovery.ts:4`) and offers no on-page path to the rest; the "browse all" CTA is auth-gated copy in a sidebar card. Also, the hub is one monolithic page per exam, but real long-tail queries are per-subject ("fundamentals of nursing notes", "med surg reviewer", "MCN notes") — a structure NoteLib already has as per-subject Public Library pages, but the exam hub doesn't surface or link them.

### Blocker 4 (minor — mostly solved): technical discoverability

Robots allows public routes, the sitemap now covers landing/Learn/Public Library/exam hubs/per-note pages, JSON-LD ships on every major surface, canonicals via `buildPageMetadata`, ISR (`revalidate = 300`), internal linking exists both directions. Two residual gaps: (a) exam hub `CollectionPage` JSON-LD has no `ItemList`/`hasPart` of the actual notes; (b) exam hub sitemap entries have no `lastModified`. And one unknown that dwarfs both: since the sitemap fix shipped only 2026-07-15 and **no Search Console exists**, nobody can currently confirm the exam hubs are indexed *at all*.

**Bottom line:** technical ~90% done; on-page relevance is the highest-leverage fixable gap; content architecture second; authority is the long-pole that gates head terms and is only partially an engineering problem.

---

## 2. Prioritized candidates

Classified per `docs/skills/roadmap-feature-audit.md` (Core Feature / Polish / Future Enhancement / Low-Priority Idea).

### P1 — Google Search Console setup + indexation baseline — **Core Feature** (infrastructure, non-code)
Verify `notelib.app` in GSC, submit the sitemap, record a baseline of indexed URLs and existing query impressions. Everything else is unfalsifiable without it. **Effort: ops task, DNS/domain access, <1 hour — not a code deliverable.**

### P2 — "Free reviewer" vocabulary pass on exam hubs — **Polish**
Rewrite exam hub titles, meta descriptions, H1 subline, value strip to speak the market's language ("Free PNLE Reviewer Notes...") while staying inside the notes-library identity — "free" is honest (public notes are free to read), "reviewer" is the market's term. Lives in `exam-hub-config.ts` + `app/exam/[slug]/page.tsx`. **Effort: frontend copy only, 2 files, well under 50 LOC.**

### P3 — Exam-named Learn guides (3 articles) — **Core Feature** (content-authoring)
One human-written Learn guide per exam ("How to Build a Free PNLE Reviewer From Your Own Notes", + LET/ALE) covering each exam's real subject structure, linking into the matching hub and relevant subject pages. Reuses `learn-guides.ts` + existing `Article` JSON-LD — no new system. **Effort: content-authoring (the hard part — must be accurate) + small frontend (3 entries).**

### P4 — Exam hub subject breakdown + full-inventory path — **Core Feature**
Add (a) a "Browse by subject" section per hub with real note counts linking to subject pages, (b) an unauthenticated "Browse all {N} notes →" link so all 63 PNLE notes are reachable, not just the 18-card cap. **Effort: frontend, moderate — one new section component, no backend change.**

### P5 — `ItemList` inside exam hub CollectionPage JSON-LD — **Polish**
Extend structured data so the hub's `CollectionPage` asserts its actual member notes (name + canonical URL), not just a collection with no visible members. **Effort: frontend, small.**

### P6 — Organic-landing attribution in the existing analytics system — **Core Feature** (measurement)
Extend page-view metadata with a coarse `referrerSource` (`google`/`other-search`/`social`/`direct`, bucketed from `document.referrer`, no raw URLs), fed through the existing fire-and-forget pipeline, surfaced as an admin dashboard panel. No new `AnalyticsEventType` needed for v1. **Effort: frontend small + admin dashboard panel (the larger half).**

### P7 — Exam quick-facts block per hub — **Future Enhancement**
Static, human-maintained facts (schedule, PRC, subject areas). Deferred: creates a recurring editorial maintenance obligation; see first from GSC (P1) whether these queries even reach NoteLib.

### P8 — Off-page: community presence and backlinks — **Future Enhancement** (non-engineering)
Deliberate outreach into nursing/education student communities. The only lever against Blocker 1 (authority); not a code deliverable. Sequence after P2–P4 ship — don't drive links to unfinished pages.

### P9 — Wave 2 exam hubs (CPALE, Civil Service, engineering boards) — **Low-Priority Idea, explicitly deferred**
Already listed in `docs/features/exam-hub.md` as Wave 2. Deferred **for SEO reasons specifically**: zero-note buckets would be thin-content pages, the exact failure mode the content-depth constraint warns against. Gate: open a Wave 2 hub only once its bucket crosses ~25–30 public notes.

---

## 3. Measurement plan

**Instrument (weeks 0–2):** GSC domain verification + sitemap submission (P1); `referrerSource` on public page-view events (P6, no new event types needed); one admin dashboard panel — organic landings per week per surface, organic-landing → exam-CTA click rate.

**What "it's working" looks like at 90 days (staged, not a single number):**
- **Indexation (weeks 1–4):** all 4 exam-surface URLs and ≥90% of public note/subject pages "Indexed" in GSC — pass/fail; failure means a still-invisible technical problem.
- **Impressions (days 30–90):** non-branded exam-family query impressions going from ~0 to a consistent, growing weekly count.
- **Position (days 60–90):** first appearances at positions 10–30 for long-tail phrases — **not** top-10 for "free PNLE notes" itself; promising that inside 90 days on this domain's authority would be dishonest.
- **Behavior (days 30–90):** `referrerSource=google` landings on exam hubs trending up; organic-landing → CTA-click rate comparable to the surface's overall rate; first `intent=exam` signups attributable to organic sessions.
- **Review checkpoint at day 90:** impressions up but clicks flat → fix titles/descriptions. Impressions themselves flat despite confirmed indexation → bottleneck is authority, escalate P8.

---

## 4. Explicit rejections

1. **Programmatic keyword landing pages** (a page per query permutation). Violates "curation, never generation"; at 43–63 notes/bucket the pages would be demonstrably thin.
2. **AI-generating reviewer content to fill exam syllabi.** The most tempting fix for the depth gap and the most explicitly forbidden — the product's differentiation *is* real human notes.
3. **Chasing "PNLE reviewer PDF" with downloadable PDF bait.** Misrepresents the product and invents PDF-export scope this session has no mandate for.
4. **Nudging or defaulting more user notes to public.** Locked visibility/ownership rules; rejected without further analysis.
5. **PRC results/news pages.** News-publisher turf, off-identity, unstaffable freshness treadmill. (P7's scoped facts block is the acceptable fraction of this idea.)
6. **Re-recommending the sitemap fix or a new SEO framework.** Already shipped 2026-07-15; everything above extends the existing `buildPageMetadata`/JSON-LD/robots architecture, nothing replaces it.
7. **hreflang / Filipino-language localization.** Philippine board-exam content is searched/consumed overwhelmingly in English/Taglish; not a current blocker.
8. **Immediate Wave 2 hub expansion.** Rejected as a *now* item (kept gated as P9) — more empty hubs is more thin content.

**Fable's suggested sequencing:** P1 + P2 immediately (near-zero effort, unblocks measurement and fixes the vocabulary gap); P3 + P4 as a content/feature pair for a later release; P5 + P6 ride along as polish/measurement; P7–P9 gated on what the first 90 days of GSC data shows.

## Status

Run complete 2026-07-17. Reviewed by the user before any item is scoped into a release — see `RELEASES.md`/`ROADMAP.md` for what, if anything, was folded into v0.50.4.
