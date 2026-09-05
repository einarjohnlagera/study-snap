# 2026-09-05 — production outage: unbounded `/notes/public` read → pool exhaustion → platform restart

## Status: diagnosis only. Nothing fixed, no code or config changed, no release opened.

Sources: Render logs and metrics for `srv-d6u0jkvgi27c73dvl9k0` (`notelib-backend-prod`) and
`dpg-d6tvb8fkijhs73fda4m0-a` (`notelib-db-prod`), read live 2026-09-05; plus a code read at
`v0.117.0`, which is the version that was running. Every claim below is labelled **VERIFIED**
(observed in a log line, a metric, or code I opened) or **INFERRED**.

**This is the third occurrence of one shape**: a latent unbounded fetch over the public-note catalog
crossing a threshold as the catalog grows. `2026-09-01` (frontend build failure, 2.5 MB response) and
`2026-09-04` (pool exhaustion) are the first two. The catalog was ~950 public notes in August and is
**1,442 today** — a 52% increase.

---

## 1. What the previous finding left open, this one closes

`docs/claude-findings/2026-09-04-prod-outage-hikari-pool-exhaustion.md` §0 recorded, as its most
consequential open item, that all five falsification queries read tables recording **writes** and so
could not detect an anonymous **read** burst, and named `NoteCollectionService.listPublic:206` —
`permitAll`, unpaginated, read-only — as the one path the log showed starved. It called that a
hypothesis and said the decisive evidence was request-rate data, not Postgres.

**The hypothesis is now confirmed by direct evidence, on the sibling path.** HikariCP leak detection —
shipped in `v0.112.0` Phase 1 for exactly this purpose — fired and printed the stack.

**VERIFIED**, `2026-09-05T11:43:34.991Z`:

```
WARN [l-1:housekeeper] com.zaxxer.hikari.pool.ProxyLeakTask :
  Connection leak detection triggered for org.postgresql.jdbc.PgConnection@7441c52a
  on thread http-nio-10000-exec-30, stack trace follows
java.lang.Exception: Apparent connection leak detected
  at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:127)
  at org.hibernate...LogicalConnectionManagedImpl.acquire(LogicalConnectionManagedImpl.java:187)
  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.beginTransaction(HibernateJpaDialect.java:135)
  at org.springframework.orm.jpa.JpaTransactionManager.doBegin(JpaTransactionManager.java:411)
  ...
  at com.studysnap.backend.service.NoteService$$SpringCGLIB$$0.listPublic(<generated>)
  at com.studysnap.backend.controller.NoteController.listPublic(NoteController.java:665)
```

Returned to the pool 1.0 s later (`11:43:36.036`, "was returned to the pool (unleaked)").

**Two things this proves and one it does not.**

- **VERIFIED** — a single anonymous `GET /notes/public` request held a pooled connection for **more
  than 60 seconds**. `/notes/public/**` is `permitAll` (`SecurityConfig:50`) and the controller
  handles `user == null`, so no account is needed.
- **VERIFIED** — the connection was acquired at `JpaTransactionManager.doBegin`, i.e. at
  `@Transactional(readOnly = true)` entry. It is therefore pinned for the **whole** method: every
  entity load, every projection load, all DTO mapping, and all in-Java filtering and sorting.
  ⚠️ This means the hold is **inside the transaction**, not an open-in-view artefact after commit.
  Do not re-open the OSIV question on the strength of this incident.
- **NOT PROVEN** — *which* internal branch ran. Hikari captures the trace at acquisition, before any
  query executes, so the stack cannot distinguish them. §3 shows why it does not matter: both
  branches are O(entire public catalog).

---

## 2. Timeline (all UTC; local is +08, so 11:45 UTC = 19:45, the "around 7:45pm" reported)

| Time | Event | Source |
|---|---|---|
| 11:40:00.037 | `GenerationRecoveryJob` completes normally — DB reachable, pool healthy | app log **VERIFIED** |
| ~11:40–11:42 | Pool fills. No log line marks the transition | **INFERRED** |
| 11:42:56.145 | `DataSourceHealthIndicator: DataSource health check failed`, `CannotGetJdbcConnectionException` | app log **VERIFIED** |
| 11:42:56.148 | `HikariPool-1 … timed out after 5000ms (total=20, active=20, idle=0, waiting=0)` | app log **VERIFIED** |
| 11:43:34.991 | Leak detector names `NoteService.listPublic` (§1) | app log **VERIFIED** |
| 11:43:42 → 11:44:50 | Sustained `total=20, active=20, idle=0, waiting=3–5`; `GlobalExceptionHandler` logs unhandled 500s; `analytics_event_persist_failed … PUBLIC_NOTE_VIEWED` ×2 | app log **VERIFIED** |
| 11:44 | App memory **977 MB → 1.41 GB**; DB CPU 0.0076 → 0.076 (10×), DB memory 129 → 191 MB | metrics **VERIFIED** |
| 11:45:21.553 | `Commencing graceful shutdown` — SIGTERM, shutdown hook ran | app log **VERIFIED** |
| 11:45:32.053 | `Graceful shutdown complete` (10.5 s waiting for active requests) | app log **VERIFIED** |
| 11:45:36.935 | New JVM: `Starting BackendApplication v0.117.0` — **same version, no deploy** | app log **VERIFIED** |
| 11:45:47.734 | `==> Instance srv-…-d9npp restarted` | platform log **VERIFIED** |
| 11:46:04.833 | `Started BackendApplication in 29.17 seconds` | app log **VERIFIED** |

**Impact**: health-degraded from 11:42:56; **~43 s with no process** (11:45:21 → 11:46:04); 502s
peaked at **76** in the 11:46 bucket, with 17 × 500 and 5 × 401. Total user-visible window ≈ **3m 10s**.

### What is ruled out

- **OOM: ruled out.** Searching `["OutOfMemoryError", "GC overhead", "Java heap space"]` returns
  nothing (**VERIFIED**), and independently a `SIGKILL` runs no shutdown hook whereas one ran for
  10.5 s. Peak 1.41 GB against a 2 GB limit. ⚠️ An earlier search of mine used a pipe-regex in the
  `text` filter (`"A|B|C"`) and returned zero for terms that demonstrably existed — **that form
  matches nothing; pass separate array entries.** Any negative drawn from it is void.
- **Deploy: ruled out.** Same version on restart, no build output, same instance id (**VERIFIED**).
- **The database: not the constraint.** DB CPU peaked at 0.089 of 1, memory 191 MB of 256 MB,
  `max_connections = 103` (**VERIFIED**). ⚠️ The DB's `active_connections` metric reads **20 before,
  during and after** the incident — that is Hikari's steady state, because `minimumIdle` defaults to
  `maximumPoolSize`. It is **not** a saturation signal. Hikari's own `active=20, idle=0` is.
- **Restart cause: INFERRED, not proven.** No platform line attributes the restart to anything. A
  health-check auto-restart fits (the check had been failing for 2m25s), and it matches 2026-09-04.

### One apparent contradiction, reconciled

`http_request_count` shows ~0 completed requests in the 11:40 and 11:42 buckets, then 50 at 11:44 and
154 at 11:46 — which reads as "20 connections held with no traffic." **INFERRED** reconciliation: the
metric buckets on **completion**, so requests that started ~11:40–11:42 and ran 60 s+ only appear in
the 11:44/11:46 buckets. That makes those spikes the same burst, not a second event. Not verifiable
from the data available (see §5).

---

## 3. Root cause: `/notes/public` is O(entire public catalog) on essentially every path

`GET /notes/public` (`NoteController:665`) has two branches, chosen by
`boolean paginated = page != null || pageSize != null`.

**Branch A — no `page`/`pageSize` → `listPublicLegacy` (`NoteService:831`).** **VERIFIED by reading:**

```java
notes = noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC); // ALL 1,442 entities
List<UUID> noteIds = notes.stream().map(NoteEntity::getId).toList();                // all ids
projections = noteRepository.findPublicLibraryListItemProjectionsByIdIn(noteIds);   // all projections
List<NoteListItemResponse> allItems = toListItems(projections, viewerUserId, false);// all DTOs
items = filterPublicLibraryItems(allItems, search, subject, tags, courseProgram);   // filter in Java
... return new PublicNoteListResponse(limitPublicLibraryItems(items, size), total); // THEN limit
```

`size` is applied **last**. `?size=1` materialises 1,442 notes to return one.

**Branch B — paginated, but only for 2 of 8 sorts.** `PublicLibrarySort.isSqlOrderable()` is
`this == RECENT || this == TITLE`. For the other six the code calls
`noteRepository.findPublicLibraryCandidates(criteria)` — the **full candidate set** — ranks it in
Java, then slices the page.

⚠️ **The default sort is `RECOMMENDED`** (`parsePublicLibrarySort:1192-1195` falls back to
`PUBLIC_SORT_RECOMMENDED` when `sort` is null or blank), and `RECOMMENDED` is **not** SQL-orderable.
**So a request with no `sort` takes the full-catalog path whether it is paginated or not.**

**This falsifies two comments currently in the tree, and both will mislead the next reader:**

1. `frontend/lib/server-public-notes.ts` — "It is paginated so each response stays cacheable; an
   unbounded single fetch is the defect that broke the production build." Pagination bounded the
   **response size**, which correctly fixed the 2 MB Next.js data-cache defect. It did **not** bound
   the **server-side work**. `fetchAllPublicNotePages("")` sends no `sort`, so at
   `PUBLIC_NOTES_PAGE_SIZE = 50` over 1,442 notes it issues **~29 sequential requests, each loading
   the entire candidate set** — 29 full-catalog loads per sitemap generation.
2. `backend/.../application.yaml` — the `maximum-pool-size: 20` comment presents the bump as the
   answer to this class. **It is not.** The holds are unbounded in *duration*, so a larger pool buys
   time proportional to nothing.

### The two callers that never needed a catalog at all

| Caller | Request | Cost |
|---|---|---|
| `frontend/app/page.tsx:501` → `getServerPublicNoteCount()` | `/notes/public?size=1` | Loads **1,442** notes to return **one integer** (the `total` field) |
| `frontend/app/public/library/[subject]/[slug]/page.tsx:91` → `getServerPublicNotesBySubject()` | `/notes/public?subject=X&size=4` | Loads **1,442** notes, filters in Java, returns **4** |

Both are anonymous, server-rendered, `revalidate: 300`. The second sits on **every SEO note detail
page** (~250 of them), which is the amplifier: concurrent anonymous traffic across distinct slug pages
produces concurrent full-catalog loads with no shared cache between them.

⚠️ `frontend/lib/server-public-notes.test.ts:252` asserts the code "never requests `/notes/public`
without either a filter or a pageSize" — and `?size=1` satisfies neither, yet line 24 pins it as
expected. **The existing guard permits the exact shape that triggers Branch A.**

### Why this produced the observed numbers

20 concurrent full-catalog loads, each materialising entities + projections + DTOs for 1,442 notes,
is consistent with the **+433 MB** heap step at 11:44 and the 10× DB CPU rise. **INFERRED** — the
arithmetic fits but was not measured.

---

## 4. The second, independent failure: the health check starves on the pool it reports on

`healthCheckPath` is `/api/actuator/health` (**VERIFIED** from the service config), and
`DataSourceHealthIndicator` needs a **pool connection** to answer. When the pool is saturated by the
app's own slow query, the health check cannot answer, and the platform restarts a process whose only
problem is that it is busy.

**`connection-timeout: 5000` worked exactly as designed and did not prevent this.** Waiters failed at
5 s instead of 30 s (**VERIFIED** in every timeout line) — and the instance was still restarted,
because failing fast does not make a connection available to the health check. This is the leg
2026-09-04 named and nothing was done about it.

---

## 5. What could not be determined, and why

**The trigger is unknown.** Render request logging is not available for this service —
`list_log_label_values` for `type` returns only `["app", "build"]`, and `http_request_count` filtered
by `httpPath` returns empty (**VERIFIED**). So there is no per-path or per-client record.

Candidates, none confirmed: a crawler or burst across the ~250 SEO slug pages; landing-page ISR
revalidation; browser traffic on `/explore` or `/public/library` at the default sort. ⚠️ The two
`PUBLIC_NOTE_VIEWED` warnings are a **floor, not a count** — they appear only because the analytics
*write* failed while the pool was dry.

⚠️ **Do not treat "which client" as a prerequisite for fixing §3.** The endpoint is `permitAll` and
anonymous; any client can reach it, so the unbounded read is a defect regardless of who called it.

---

## 6. Obligations this raises

- **`[CHECKPOINT — due 2026-10-04]`** asks whether the holds are across slow external calls or an
  outright leak. **This outage answers it early and the answer is neither**: an unbounded in-transaction
  result-set load, on a `permitAll` read path. The checkpoint is answerable ahead of its date.
- Leak detection did exactly what the `v0.112.0` comment promised — it named the path. Record that as
  a win for the instrument; it is the reason this diagnosis took hours rather than releases.
- This file needs a **Backlog Index row** (kickoff step 8 names `docs/claude-findings/` precisely
  because two incident files went unindexed there).
