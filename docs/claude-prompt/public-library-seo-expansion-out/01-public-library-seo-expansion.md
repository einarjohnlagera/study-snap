# Planning Analysis: Public Library SEO Expansion & Broader Google Surfacing

Run 2026-07-17 via the `fable` model. Prompt: `../public-library-seo-expansion-prompts/01-public-library-seo-expansion.txt`.
Session plan: `../public-library-seo-expansion-session-plan.md`.

Grounding files referenced throughout: `frontend/app/public/library/[subject]/page.tsx` (title at line 144, indexable empty state at lines 205-215), `frontend/app/sitemap.ts` (unfiltered subject entries, lines 73-78), `frontend/lib/site-metadata.ts` (`buildPageMetadata` currently has no `robots` option), `docs/claude-prompt/seo-strategy-out/01-seo-strategy.md` (prior session's P1-P9).

---

## 1. Direct answer: **Not now as an offensive play — but a small defensive move is required, and it's more urgent than it was a week ago.**

NoteLib should **not** invest in ranking the broad Public Library for general-education queries now. It should ship one narrow, cheap, *defensive* change (a content-depth indexation gate), run one SQL query (a depth inventory), and put all remaining offensive capacity into deepening the 3 exam hubs (P3/P4 from the prior session). Revisit the general-subject question only after GSC (P1) exists and has ~90 days of data.

**Why "not now" — the asymmetry, spelled out:**

1. **The exam hubs earn SEO investment because the product already curates them; general subjects don't.** ALE/PNLE/LET have an allow-listed `coursePrograms` config, Bulk Generate, Official Review Sets, an exam-intent signup cookie, and known depth (43-63 notes/bucket). A general subject page is a mechanical `GROUP BY subject-string` with zero curation machinery behind it and genuinely unknown depth. SEO compounds where a content flywheel exists; there is no flywheel producing grade-school Biology notes, and no roadmap intent to build one.
2. **The competitive picture is *worse* than PNLE, not better.** "Biology reviewer for grade school" pits NoteLib against Scribd, SlideShare, CliffsNotes, and FilipiKnow — global platforms with decades of authority, on a query family with no geographic or community moat. At least "PNLE reviewer" is a Philippine-community query a focused young site can plausibly become known for; "biology reviewer" is not.
3. **Topical authority concentrates; a young domain spreads it thin at its peril.** Three Philippine board exams form one coherent topical cluster search engines can learn to associate with the domain. "Every school subject any user ever typed as a string" is the opposite of a cluster.
4. **The trigger event confirms the priors rather than revealing a new gap.** The "biology reviewer for grade school" search failing is exactly what the established diagnosis predicts, not new evidence that on-page work on subject pages would change the result.
5. **No measurement exists.** P1 (GSC) is still unscoped. Opening a second SEO front before the first can be measured makes both unfalsifiable. P1 should now be treated as a **blocking prerequisite** for any offensive SEO scope beyond what's already agreed.

**Why "ignore it entirely" is also wrong — the defensive problem P2 just created:**

The same-day P2 ship gave *every* subject page — including ones with 1 note — the title `Free {Subject} Reviewer Notes & Practice Quizzes` (`page.tsx:144`). Every one of those pages is in the sitemap at priority 0.8/`daily` with **no depth filter** (`sitemap.ts:73-78`), and a page with **zero** notes still renders an indexable empty state (`page.tsx:205-215` — no `noindex`, no 404). So the site now advertises, at scale, reviewer landing pages that may contain one note or none — the classic thin-content pattern sitewide quality classifiers punish. Because those signals are sitewide, an uncontrolled long tail of near-empty "Free X Reviewer" pages can drag down the exam hubs the team is actually investing in. **The general-subject surface is not neutral if left as-is; it subtracts from the exam-hub effort.**

## 2. Recommended candidates (the narrow middle path)

### N1 — Content-depth indexation gate for subject pages — **Core Feature** (defensive SEO infrastructure)

- **What:** A shared `SUBJECT_PAGE_INDEX_THRESHOLD` constant applied in two places that must agree: (a) `generateMetadata` in the subject page emits `robots: noindex` when the subject's public-note count is below threshold — the count is already in hand via `getServerPublicNotesBySubjectSlug`, no new backend work; (b) `sitemap.ts` filters subject entries by the same threshold. Zero-note pages keep their empty-state render (visibility rules untouched) but are always `noindex`. Individual note pages stay indexed regardless — a good single note is a legitimate long-tail asset; it's the *collection page* that's thin.
- **Why:** Removes the thin-content liability P2 amplified, protecting the exam-hub investment. Creates an "earned indexation" mechanic — a subject page becomes indexable automatically once community depth justifies it, zero editorial cost, consistent with "curation, never generation."
- **Threshold:** Recommend **≥ 4-6 notes** — deliberately far below P9's ~25-30 hub-opening gate, since the bars measure different things (a hub is a curated destination; a subject page just needs to honor its own `<title>`). At ~6 notes the Featured section actually fills (`DISCOVERY_SECTION_LIMIT = 6`). Set the final number after N2's inventory.
- **Effort:** Small frontend — `lib/site-metadata.ts` (extend `buildPageMetadata` with a robots option; has none today), the subject page, `sitemap.ts`, one shared constant, tests. No backend change. Needs `docs/features/seo.md` + `RELEASES.md` updates.
- **Dependencies:** N2 (to pick the number honestly). Not blocked on P1 — thin-page hygiene is correct regardless of what GSC will show.

### N2 — One-time per-subject depth inventory — **ops/measurement task, not a code deliverable**

- **What:** One SQL query against prod: public-note count per distinct subject string, sorted descending. Ten minutes.
- **Why:** Converts "depth is genuinely unknown" — the load-bearing unknown of this whole session — into a fact table. Sets N1's threshold, shows how many pages the gate will noindex, and reveals whether any non-exam subject already has real depth (feeding L2). Will likely also expose near-duplicate subject strings ("Bio" / "Biology"), quantifying the taxonomy-fragmentation problem for whenever that thread is picked up separately.
- **Dependencies:** none. Do it first.

### N3 — Re-sequence, don't re-scope: elevate P1 (GSC) to blocking prerequisite for *any* offensive SEO expansion

A sequencing decision, not new work: no new SEO scope beyond the already-agreed P3/P4 — exam or general — gets scoped until GSC is live with an indexation baseline. Extends the prior session's P1, doesn't re-litigate it.

### L1 — Exam-adjacent subject pages already get their lift through P4 — no new scope

The prior session's Blocker 3 already identified real long-tail queries as per-subject ("med surg reviewer," "fundamentals of nursing notes"), and P4 links exam hubs to those subject pages. Those exam-adjacent subject pages are precisely the slice of the Public Library where depth demonstrably exists — the exam-deepening plan already invests in the best of the general-subject surface. Widening beyond it buys almost nothing P4 doesn't.

### L2 — "Earned depth" pathway for non-exam subjects — **Future Enhancement, double-gated**

If (a) N2 shows a non-exam subject with genuinely deep content (~15-20+ notes), and (b) post-P1 GSC data shows organic impressions actually arriving on its (now-indexed, per N1) subject page, that specific subject earns incremental treatment — e.g. Learn-guide internal linking, eventually hub-style curation. Mirrors the P9 gating precedent: pages earn investment by crossing observable thresholds, never by speculation. Build nothing for it now.

## 3. The explicit alternative

Spend the capacity on **P3 (exam-named Learn guides)** and **P4 (hub subject breakdown + full-inventory path)** from the prior session. Deepen, don't widen. Every unit of authority-building (P8) should keep pointing at the board-exam identity the product has already chosen.

## 4. Explicit rejections

1. **A broad "Free {Subject} Reviewer" ranking push across all subjects.** Unknown-to-thin depth, no curation machinery, worse competitive field, topical-authority dilution on a young domain. The core "no."
2. **Curated hubs for general subjects (a "Biology Hub").** No demonstrated demand, no curation capacity, off the product's chosen focus.
3. **Using Bulk Generate to seed thin subjects into rankable depth.** The tool exists for exam curation; pointing it at subject strings to manufacture SEO depth is a content farm with extra steps — violates "curation, never generation" in spirit even without a new generation system.
4. **Rolling back the P2 subject-page vocabulary copy.** The copy is right for pages with content; the fix is gating indexation (N1), not un-shipping vocabulary.
5. **Noindexing all non-exam subject pages.** Overcorrection — deep exam-adjacent subject pages (Med Surg, Fundamentals of Nursing) are exactly the long-tail assets Blocker 3 wants surfaced, and P4 is about to link to them.
6. **404ing zero-note subject pages.** Empty states exist because notes can legitimately go private under locked visibility rules; `noindex` (N1) achieves the SEO outcome without touching those flows.
7. **Subject-string consolidation/taxonomy cleanup as SEO scope.** Real problem (N2 will quantify it), belongs to the existing combobox-taxonomy thread — bundling it here would be scope creep.
8. **Waiting for GSC data before shipping the N1 gate.** Defensive hygiene shouldn't queue behind measurement; the liability exists whether or not it's being measured.

**Suggested sequencing:** N2 (one query, today) → set threshold → N1 (small frontend PR) → P1 lands as prerequisite → P3/P4 as planned → L2 only if its two gates open.

## Status

Run complete 2026-07-17. Reviewed by the user before any item is scoped into a release — see `RELEASES.md`/`ROADMAP.md` for what, if anything, was folded in.
