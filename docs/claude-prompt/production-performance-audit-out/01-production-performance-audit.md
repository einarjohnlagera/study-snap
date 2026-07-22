# Planning Analysis: Production Read-Path Performance Audit (Private Library, Public Library, Note Collection Detail, Dashboard)

Run 2026-07-17 via the `fable` model. Prompt: `../production-performance-audit-prompts/01-production-performance-audit.txt`.
Session plan: `../production-performance-audit-session-plan.md`.

Grounding: 4 parallel direct-codebase investigation forks (Private Library, Public Library, Note Collection detail, Dashboard), cross-referenced against `docs/releases/v0.38.0.md` (the prior Read-Path Optimization Pass).

---

## 0. The `listMine` discrepancy — RESOLVED (with evidence)

Not a regression, not release-note imprecision. It's a **naming collision between two different `listMine` methods**:

- v0.38.0's "Private Library List Projection" commit (`50b2f631`) — *"perf: project private library **study pack** list … preserve cursor pagination and StudyPackListItemResponse mapping."* It targeted **`StudyPackService.listMine(userId, limit, cursor)`** (`StudyPackService.java:348`), which genuinely is cursor-paginated. The tell: v0.38.0's notes say "loaded the entire `quiz` JSONB column per page item" — notes don't have a `quiz` column; Study Packs do.
- **`NoteService.listMine(ownerUserId)`** (`NoteService.java:333` → `NoteRepository.findByOwnerUserIdOrderByUpdatedAtDesc`, `NoteRepository.java:32`) backs `/library`'s actual note list. `git log -S findByOwnerUserIdOrderByUpdatedAtDesc` shows this has been unbounded **since the original notes commit (`ce359415`)** — never paginated, never regressed, simply never in v0.38.0's scope. The release notes' "Private Library" label ambiguously named the surface while fixing only the Study-Pack-list half.

**Consequence:** the note-list path is untouched greenfield for this pass — no "restore old pagination" shortcut exists. Worse than previously known: `listMine` returns full `NoteEntity` rows, so **every note's full `content` text ships on every library load and every poll tick** — a projection win independent of any bounding decision.

## 1. Root-cause ranking (perceived slowness in practice)

Two failure modes degrade differently: unbounded queries get worse every month as content grows; waterfalls are a fixed latency multiplier.

1. **Public Library unbounded fetch** — most severe, and the only finding that scales with *global* content, not per-user. Several hundred notes fully loaded/enriched/shipped on every visit, zero caching, the server's own `size` clamp is dead code (null passthrough). Only page whose cost curve points opposite the app's entire optimization history.
2. **Dashboard-overview's two unpaged history scans** — identical bug class to 4 paths v0.38.0 already fixed, just never applied here. Every completed quiz session ever, plus (Board-Exam profiles) every Study Pack ever as full entities. Dashboard is the most-visited page in the app.
3. **Private Library unbounded `listMine` + poll amplification** — full entities including `content`, no bound, 8-9 enrichment queries per fetch, and a poller re-runs the entire fan-out on a timer while any note is generating. Shared root cause with Dashboard Stage 1.
4. **The waterfalls** (Collection detail's 3 waves; Dashboard's 2 waves + per-note fan-out) — real and user-perceived, but bounded work with a fixed ceiling, not a growing-forever cost. Cheapest to fix per unit of perceived improvement.

## 2. Prioritized fix list

### F1 — Dashboard-overview: lean projections + SQL-side bounding of the two history scans
**Fixes:** Dashboard. **Effort:** Backend, S-M. **Tier: Polish.** Byte-identical — a pure v0.38.0-playbook replay (lean JPQL projections, bound at the DB where an effective window exists). No product decision needed. Ships first.

### F2 — `NoteService.listMine`: lean projection + optional `limit` param
**Fixes:** Private Library and Dashboard. **Effort:** Backend + one-line frontend, S-M. **Tier: Polish.** Byte-identical by construction: (a) lean projection dropping `content`; (b) optional `limit` param, absent = current unbounded behavior. Dashboard's Stage-1 call passes `limit=N`; `/library` keeps calling unbounded for now — decouples the transparent fix from the UX-decision part (F7).

### F3 — Public Library: fix null-`size` passthrough, apply a default bound, send `size` from the frontend, replace the copied-lookup call
**Fixes:** Public Library. **Effort:** Both, M. **Tier: Polish (bounding), flagged as a real UX change, not disguised.** Recommendation: cap + "Load more" now (stopgap), real server-side browse later (F8). Also: replace the unbounded "all my notes" copied-lookup call with a lean id-only lookup. Needs one human UX sign-off (cap/load-more shape) before starting.

### F4 — Private Library poller narrowing
**Fixes:** Private Library. **Effort:** Frontend-only, S. **Tier: Polish.** Byte-identical, no API change. Poll only the generating note's own status; re-run the full list fetch only on a status transition, not on a timer.

### F5 — Collection detail waterfall flattening
**Fixes:** Collection detail. **Effort:** Frontend-only, S-M. **Tier: Polish.** Byte-identical (same calls, different scheduling) — hoist Wave 3's three effects into Wave 1's parallel batch; parallelize or speculatively fire the Goal call. 3 waves → 1 (or 1 + one conditional). Includes two verification tasks (not assumed bugs): the possible duplicate `listNotes()` call site, and whether v0.38.0's per-child readiness batching still holds under 3 post-v0.38.0 features riding along.

### F6 — Dashboard Stage 2: batch the per-note fan-out
**Fixes:** Dashboard. **Effort:** Both, M. **Tier: Polish.** New batched ids-in endpoint replacing 8 (Teacher) / 4 (Student) individual per-note fetches — zero user-visible change, don't widen the existing Stage-1 DTO.

### F7 — Real backend pagination for Private Library `/library`
**Fixes:** Private Library. **Effort:** Both, M-L. **Tier: Future Enhancement (next release).** Explicit UX/architecture change — filters move server-side. Deferred deliberately: F2+F4 remove most pain transparently first.

### F8 — Server-side filtering + full pagination UX for Public Library
**Fixes:** Public Library. **Effort:** Both, L. **Tier: Core Feature** — genuinely changes what the product surfaces. Depends on F3 (stopgap) first; candidate for its own release item.

### F9 — Lightweight client caching (SWR-style)
**Tier: Future Enhancement.** Re-evaluate only after F1-F6 land — don't adopt speculatively over a deliberately component-local-state architecture.

### F10 — Denormalized engagement counts to eliminate enrichment queries
**Tier: Low-Priority Idea.** Same write-path-sync-burden reasoning v0.38.0 used to reject persisting `question_count` — gated on slow-query-log evidence after bounding lands.

## 3. Sequencing recommendation

Follow the v0.38.0 precedent: separate, independently verified PRs, each with real-DB integration tests, byte-identical wherever claimed.

1. **F1** — zero-risk, biggest per-request DB win, most-visited page. No decision needed, ships immediately.
2. **F2** — byte-identical, helps 2 of 4 pages.
3. **F3** — most severe finding, but needs one human UX decision (cap + load-more shape) first — get that decision now so F3 isn't blocked.
4. **F4** and **F5** — frontend-only, low risk, parallel with 1-3 (different files).
5. **F6** — last; smallest marginal win once F1 lands.
6. **Next release:** F7, F8 as properly-scoped items with real pagination UX design.

Routing: F1/F2/F6 touch backend service/repository layers across multiple files with anti-drift rules → **Codex prompts**. F4 is small enough for direct implementation; F5's speculative-fetch restructuring likely crosses ~50 LOC → Codex too.

## 4. Explicit rejections

1. **Redis / CDN edge cache.** Every finding is unbounded work or serialized scheduling, not expensive-but-necessary work worth caching. A cache in front of an unbounded query masks growth with none of the payload savings. Revisit only if bounded queries still show in slow-query logs.
2. **ISR/Server Component conversion of Public Library now.** The page mixes shared content with user-specific copied-state — real architecture work; F3+F8 get the same outcome cheaper. Legitimate future candidate after F8.
3. **Server Component rewrite of Collection detail.** Named as its own structural candidate per the hard constraint, not folded into F5. Revisit only if post-F5 time-to-interactive is still poor.
4. **Widening existing DTOs to kill Dashboard Stage 2.** Breaks byte-identical discipline on a shipped endpoint; a new batch endpoint achieves the same with no contract mutation.
5. **One combined mega-PR.** v0.38.0's separate-PR, per-fix-verified precedent is the model — reject a single large rewrite.
6. **React Query/SWR adoption in this pass.** Deferred (F9), not rejected forever — sequenced after the structural fixes it might otherwise paper over.

## 5. Open verification items (carry into the first PR's scoping)

1. Collection detail's second "list all my notes" call site — mount-triggered or action-triggered.
2. Goal per-child readiness batching integrity under the three post-v0.38.0 ride-along features (Companion, weekly countdown, Mentor Tips).
3. Quick audit of "continue studying" and "today's focus" backend paths — flagged, not deep-audited.

Key files: `backend/src/main/java/com/studysnap/backend/service/NoteService.java:333`, `.../repository/NoteRepository.java:32`, `.../service/StudyPackService.java:348` (the *other* `listMine` — do not conflate in any Codex prompt, name the class explicitly), `.../controller/NoteController.java:434`, `docs/releases/v0.38.0.md`.

## Status

Run complete 2026-07-17. Reviewed by the user before any item is scoped into a release.
