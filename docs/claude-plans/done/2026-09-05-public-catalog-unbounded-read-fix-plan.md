# Fix plan — unbounded `/notes/public` read (2026-09-05 production outage)

**Written by:** Prod Investigator session, 2026-09-05. **For:** the implementing session.
**Evidence:** `docs/claude-findings/2026-09-05-prod-outage-public-catalog-unbounded-read.md`.
Read that first — this plan does not repeat the proof, and several items below look optional until
you have seen it.

**Status: NOT STARTED. No code, config or docs changed by the investigating session.** The only
artefacts it produced are that findings file and this plan.

---

## 1. Settled facts — do NOT re-derive these

Each was verified by opening code at `v0.117.0` (the version that was running) or by reading a log
line / metric live. Re-deriving them costs an hour and has already been paid for.

1. `GET /notes/public` is **anonymous** — `SecurityConfig:50` has `/notes/public/**` `permitAll`, and
   `NoteController.listPublic` handles `user == null`.
2. The leaked connection was acquired at `JpaTransactionManager.doBegin`, i.e. **at
   `@Transactional(readOnly = true)` entry**, and pinned for the whole method. ⚠️ **The hold is
   INSIDE the transaction. This is not an open-in-view problem — do not re-open that question here**,
   and do not set `open-in-view: false` as part of this work.
3. Branch selection is `boolean paginated = page != null || pageSize != null` (`NoteController:665`
   region). `size` is **not** a pagination parameter.
4. `listPublicLegacy` (`NoteService:831`) loads **every** public `NoteEntity` via
   `findByVisibilityOrderByUpdatedAtDesc`, then all projections, then all DTOs, then filters in Java,
   and applies `size` **last** (`:868`, `limitPublicLibraryItems:1095`).
5. `PublicLibrarySort.isSqlOrderable()` is `RECENT || TITLE` only. **The default is `RECOMMENDED`**
   (`parsePublicLibrarySort:1192-1195`), which is not SQL-orderable — so a request with no `sort`
   takes the full-candidate path **even when paginated**.
6. `countPublicLibraryMatches` (`PublicLibraryRepositoryImpl:146`) is a real `select count(*)` built
   from the same `buildFilter(criteria)`. It is the correct primitive for any count.
7. The two branches differ in **constant factor**, not just shape: legacy loads full `NoteEntity`
   rows (**including `content`**), the ranking branch loads a slim 9-column candidate projection
   (`:154`). Both are unbounded in row count; **legacy is far heavier and is the priority.**
8. Public note count is **1,442** (read live). It was ~950 in August.
9. `PublicNoteListResponse` (record) already carries `totalMatching` and `hasMore`, and its 2-arg
   constructor nulls them — so the paginated branch already returns a count for free.

---

## 2. The defect in one paragraph

`/notes/public` performs work proportional to the **entire public catalog** on every path a real
client actually uses, and holds a pooled JDBC connection for the whole of it. Twenty concurrent such
requests exhaust the 20-connection pool; `DataSourceHealthIndicator` then cannot answer
`/api/actuator/health` because it needs a connection from the same pool, and the platform restarts a
process whose only problem is that it is busy. This is the third occurrence of one shape — a latent
unbounded fetch over the public catalog crossing a threshold as data grows (2026-09-01 build failure,
2026-09-04 outage, 2026-09-05 outage).

---

## 3. Scope — three separable legs

They are independent and can ship in any order or separately. **(B) is the cheapest and buys the most
per line changed; (A) is the actual root cause; (C) is what turns an incident into an outage.**

### Leg B — stop the two callers that never needed a catalog (FRONTEND ONLY, do this first)

Both callers are anonymous, server-rendered, `revalidate: 300`.

| Caller | Sends today | Cost today |
|---|---|---|
| `frontend/app/page.tsx:501` → `getServerPublicNoteCount()` (`lib/server-public-notes.ts:126`) | `/notes/public?size=1` | 1,442 notes loaded to return **one integer** |
| `frontend/app/public/library/[subject]/[slug]/page.tsx:91` → `getServerPublicNotesBySubject()` (`lib/server-public-notes.ts:250`) | `/notes/public?subject=X&size=4` | 1,442 notes loaded to return **4 items**, on ~250 SEO pages |

⚠️ **Both can be fixed with no backend change, and this is the single highest-value finding in the
plan.** Adding `page=0&pageSize=N&sort=recent` moves the request onto the SQL-orderable paginated
path, which does `count(*)` + `LIMIT`:

- **Count:** `/notes/public?page=0&pageSize=1&sort=recent`. The paginated branch sets
  `totalMatching = countPublicLibraryMatches(criteria)` and returns `total = min(totalMatching, MAX_INT)`.
  `getServerPublicNoteCount` already reads `payload.total`, so **the field it reads keeps its exact
  meaning** — it is simply computed by a `count(*)` instead of by materialising the catalog.
- **Related-by-subject:** `/notes/public?subject=X&page=0&pageSize=4&sort=recent`.

⚠️ **`sort=recent` on the subject call is a PRODUCT CHANGE and must be raised, not absorbed.** Today
those four "related notes" come back in `RECOMMENDED` rank order; `recent` orders by recency. If the
ranked order is wanted, that case needs Leg A first — it cannot be had cheaply. See §5.2.

⚠️ **`frontend/lib/server-public-notes.test.ts` must be corrected, not merely extended.** Line 252
asserts the code *"never requests `/notes/public` without either a filter or a pageSize"*, yet line 24
pins `?size=1` as expected — and `?size=1` satisfies neither condition. **The existing guard permits
the exact shape that causes the outage.** Tighten it to require `pageSize`, and confirm the tightened
assertion fails against today's code before you change the source.

### Leg A — bound the server-side work in `/notes/public` (BACKEND, the root cause)

Two paths need bounding. Everything else is mitigation.

- **A1 — the legacy branch** (`NoteService.listPublicLegacy:831`). It exists to serve callers that
  send neither `page` nor `pageSize`. Decide its fate (§5.1): push its filters into SQL, cap it, or
  retire it once no caller needs it.
- **A2 — the ranking branch** (`NoteService:800-812`, via `findPublicLibraryCandidates`). Six of eight
  sorts, **including the default `RECOMMENDED`**, load every matching candidate and rank in Java.
- **A3 — `getPublicLibraryDiscoverySections`** (`NoteService:871`, mapped at
  `NoteController:725` `/public/discovery-sections`, therefore also anonymous) calls
  `findPublicLibraryCandidates` with **empty criteria** — an unconditional full-catalog load on an
  anonymous endpoint. It is a third instance of the same defect and should be assessed with A2.

⚠️ **The ranking is a real product behaviour, not incidental.** `PublicNotesScoringUtils` decides
Featured/Popular/Recent. Do **not** silently replace `RECOMMENDED` with a SQL `ORDER BY` that scores
differently — either move the scoring into SQL faithfully, or bound the candidate set with a stated,
reviewed rule. A change to what learners see on `/explore` is a product decision.

⚠️ **Do not "fix" this by moving work off the transaction.** The work itself is the problem; a shorter
transaction around an unbounded load still allocates the same heap and still scales with the catalog.

### Leg C — decouple the health check from the pool (CONFIG, owner decision — see §5.3)

`healthCheckPath` is `/api/actuator/health`; `DataSourceHealthIndicator` needs a pool connection to
answer it. ⚠️ **`connection-timeout: 5000` worked exactly as designed and did not prevent the
restart** — waiters failed at 5 s instead of 30 s and the instance was still killed, because failing
fast does not make a connection available to the health check. 2026-09-04 named this leg; nothing was
done. Do not record it as addressed by the `v0.112.0` config.

---

## 4. Explicit non-fixes — rejected with reasons, do not re-propose

- ⚠️ **Do NOT raise `maximum-pool-size`, instance memory, or the database plan.** The DB was not the
  constraint (CPU 0.089/1, memory 191 MB of 256 MB, `max_connections=103`). The holds are unbounded
  in **duration**, so a larger pool buys time proportional to nothing. It has now been raised once
  (10 → 20) and the same failure recurred at 20. **Say so in the release, or a later session raises it
  to 40 and buys another few weeks.**
- ⚠️ **Do NOT set `spring.jpa.open-in-view: false` here.** §1.2 — the hold is inside the transaction.
  That change has a real blast radius and `v0.112.0` ruled it wants a staging run.
- ⚠️ **Do NOT propose PgBouncer.** Standing rule from `v0.112.0`, and it addresses *too many clients*,
  not *connections held too long*.
- ⚠️ **Do NOT start `v0.112.0` Phase 3.** It is gated on `[CHECKPOINT — due 2026-10-04]` and
  restructures six services and twelve quota sites. It is nearby and must not be taken opportunistically.
- ⚠️ **Do NOT trim `contentPreview` / `summaryPreview` to shrink responses.** `v0.100.0` already
  rejected this: it moves the threshold, which is what caused the recurrence.
- ⚠️ **Do NOT make identifying the traffic source a prerequisite.** The trigger is unknowable from
  available telemetry (§5.4) and the endpoint is anonymous, so the unbounded read is a defect whoever
  called it.

---

## 5. Decisions owed BEFORE implementation

1. **Legacy branch: bound, or retire?** After Leg B, does any caller still need the unpaginated shape?
   Sweep `lib/api.ts` (`listPublicNotes`, `:5390`) and every `server-public-notes.ts` helper. If none
   do, retiring it is cleaner than optimising it — but `/notes/public` is a public HTTP contract and an
   unpaginated request must degrade to a sane bounded response, never a 400 or an unbounded load.
2. **Related-notes ordering** (Leg B): accept `recent` for the four related notes, or keep
   `RECOMMENDED` and wait for Leg A? Product call.
3. **Health check** (Leg C): point Render's `healthCheckPath` at a health group excluding `db`?
   **The trade must be stated, not assumed:** it stops counterproductive restarts when the app is
   healthy but its pool is saturated by its own query, and it also keeps an instance alive and serving
   errors when the database is genuinely gone. Owner's call.
4. **Trigger — accepted as unknowable.** Render request logging is not enabled for this service
   (`list_log_label_values` for `type` returns `["app","build"]`; `http_request_count` filtered by
   `httpPath` returns empty). Decide separately whether enabling request logging is worth it; it is
   **not** a blocker for any leg here.

---

## 6. Pre-declared guards

⚠️ This repo has shipped two consecutive silent no-ops (`v0.116.0` item 4, `v0.117.0` items 3-4) whose
tell was identical: **a diff changed behaviour while touching no test that runs it.** These guards are
written so a fixture cannot pass under both the defect and the fix.

- **Leg B, discriminating guard:** assert the **request URL** each server helper builds, and assert it
  carries `pageSize`. ⚠️ A guard asserting only that the returned count is correct **passes under both
  the defect and the fix** — the current code returns the right number, expensively.
- **Leg B, corrected guard:** the tightened `server-public-notes.test.ts:252` assertion must be shown
  **failing against unmodified source** before the source changes. Name the failing test.
- **Leg A, discriminating guard:** the fixture must contain **more public notes than the requested
  page size** — ideally enough to make an unbounded load visibly different — and assert the number of
  rows the repository returns, not just the response body. ⚠️ A fixture with fewer notes than the page
  size cannot distinguish a bounded query from an unbounded one and proves nothing.
- **Leg A, default-sort guard:** exercise the **no-`sort` request explicitly**. The whole finding in
  §1.5 is that the default is the unbounded path; a fixture that passes `sort=recent` tests the one
  branch that was already fine.
- **Leg A, behaviour-preservation guard:** for a given filter set, the items returned must be
  **unchanged** before and after, including ordering for each of the eight sorts. This is the guard
  that stops a performance fix from silently re-ranking `/explore`.
- **Leg C:** if the health group changes, assert that `/api/actuator/health` returns 200 while the
  pool is saturated **and** that database health is still observable somewhere.

---

## 7. Verification tier and routing (recommendation, not a ruling)

- **Leg B alone:** frontend-only, no contract change → a single `advisor()` call on the diff.
  **Routing: Claude Code inline** (two call sites plus a test correction).
- **Leg A:** changes what a `permitAll` production read path returns and touches native SQL and
  product-visible ranking → **one scoped cold agent framed as falsification**, with the
  behaviour-preservation guard above as its target. **Routing: Codex** (service + repository + tests).
- **Leg C:** config plus an owner decision → folds into whichever release carries it.

⚠️ Per the signoff gate, this does **not** reach the full three-agent tier: no permission substrate, no
cross-user read, no money or quota semantics, no migration.

---

## 8. Version and branch — read before cutting anything

⚠️ **`v0.118.0` is open** (Note and Study Pack Regeneration) with a locked anti-drift block: CODEX
routing, no migration, and a scope confined to the single-Note regeneration primitive. **This work is
outside that scope and must not land on `feat/v0.118.0-note-and-study-pack-regeneration` or on
`releases/v0.118.0`.**

**Recommendation: cut a `v0.117.1` patch from `main`.** Production is running **`v0.117.0`**, which is
released and tagged, and this repo's rule is that a patch is a `.1` of a version that **exists**
(recorded at the `v0.110.1` kickoff, where `v0.111.1` was proposed and corrected for exactly this
reason). That gets Leg B — and Leg C if approved — to production without waiting on `v0.118.0`, and
leaves Leg A to be sized on its own.

**Alternative if the owner prefers:** finish `v0.118.0` first, then open `v0.119.0` carrying all three
legs. Slower to production; the outage has now recurred twice in two days.

Whichever is chosen, **kickoff precedes any code** — the seven-file atomic commit on the release branch.

---

## 9. Obligations to discharge at signoff

- **Correct two comments that are now false**, or the next reader is misled by the tree itself:
  - `frontend/lib/server-public-notes.ts` — the block claiming pagination made this bounded. It bounded
    **response size** (correctly fixing the 2 MB Next.js data-cache defect) and **not** server-side
    work. Note that `fetchAllPublicNotePages("")` sends no `sort` and so issues ~29 full-catalog loads
    per sitemap generation at 1,442 notes.
  - `backend/src/main/resources/application.yaml` — the `maximum-pool-size: 20` block, which presents
    the bump as the answer to this class of failure.
- **`[CHECKPOINT — due 2026-10-04]`** asked whether the holds are across slow external calls or an
  outright leak. **This outage answers it early and the answer is neither:** an unbounded
  in-transaction result-set load on an anonymous read path. Record the answer against that row.
- **Add Backlog Index rows** for `docs/claude-findings/2026-09-05-prod-outage-public-catalog-unbounded-read.md`
  and for this plan. Kickoff step 8 names both directories precisely because incident files have gone
  unindexed there before.
- **Record the win:** HikariCP leak detection, shipped in `v0.112.0` Phase 1 for exactly this purpose,
  named the offending path on its first real firing. That is why this took hours rather than releases.
