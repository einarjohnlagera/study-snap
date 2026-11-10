# v0.179.0 Stage B: +72 h read (2026-11-10), read-only

Written 2026-11-10 by the Claude Code session from read-only Render, Vercel and git reads. **It changed nothing in production and sent no traffic to it.** Authority: the Stage B deploy plan (`2026-11-04-v0179-stage-b-deploy-plan.md`, sections 5 to 8) and the main-freeze conditions (`2026-11-08-v0179-503-reconciliation-and-request-volume.md`, section 4).

**T0 is 2026-11-06T15:56:32Z.** The production alias `www.notelib.app` was repointed to the Stage B deployment `dpl_3DYfi2WhryAmg8osG4wLtTBXKkN1` (commit `cd6bd183`) at that instant, read from the alias record, and it still points there. The +72 h mark was 2026-11-09T15:56:32Z. Window read: 2026-11-06T15:00Z to 2026-11-10T05:12Z (about 86 h). Previous production deployment (the one-step rollback target): `dpl_fZ7WPyhKuNpN4Tqs6faYqHz4Bvja` (the docs-only rebuild of PR #1570).

## Result against the criteria
| Criterion | Result | Evidence and method |
|---|---|---|
| Zero `signal 9` kills, no crash recovery | **PASS** | Production database log (`notelib-db-prod`), each term searched alone over the window: `signal`, `terminated` and `recovery` returned 0 lines. The same search returns the Postgres `checkpoint` lines, so the search works. (A first query that put six words in one request was discarded as unreliable.) |
| No backend restart or deploy | **PASS** | The newest Render deploy is still Stage A (live 2026-11-03T10:56:13Z). The same backend instance id appears in the logs at 2026-11-08 19:15Z, 2026-11-09 01:30Z and 2026-11-10 05:08Z. |
| Backend 5xx | **PASS** (one episode, already decided) | Render status-code counts, 30 min buckets: only 3 x HTTP 503 at 2026-11-07 06:30Z (the reconciled `PUBLIC_LIBRARY_RANKING_BUSY` episode, decided at +24 h); no 500, 502 or 504 anywhere in the window. |
| Database connections above 25 | **PASS** | 20 to 21 in every 30 min sample. |
| Database memory above 260 MB at two consecutive reads | **NOT TRIGGERED at this resolution** | Highest 30 min sample 256.0 MB (95.4% of the 268.4 MB limit). Finer samples can read higher: the +24 h read saw single samples of 265.5 and 266.0 MB with no kill. |
| A walk-shaped burst occurred (the denominator clause: a quiet window is not a pass) | **MET** | In Stage B's first day: three `exam-hub` walks of 52 pages (56, 59 and 52 listing requests in a minute) and three `sitemap` walks (44, 38 and 44), all HTTP 200 (+24 h report). |
| Stage A and Stage B reads complete | **PASS** | Stage A +72 h report, Stage B +24 h report, and this read. |

## What this read does NOT establish (stated, not hidden)
- **Database-load efficiency of Stage B: held, not shown to have fallen.** Stability is shown (no kill, memory below the stop, connections steady). A full-window comparison of listing workload against Stage A was not computed, and the cache hit, miss and join shares (`CATALOG_CACHE_STATS`) cannot be recovered: Vercel keeps runtime logs for one hour. The earlier finding stands: hub and sitemap walks are at least as frequent as under Stage A.
- The walk-side hard stops (deadline, guard breach, `outcome=error`, p95 above 45 s) are verified directly only for the last hour, as in the +24 h read.
- `/sitemap.xml` was not smoke-tested: no artificial traffic is sent to production.
- An overnight lull of about 6.5 h (about 2026-11-08 19:30Z to 2026-11-09 01:38Z) showed almost no backend requests. The backend stayed up (scheduled jobs logged, database memory flat) and traffic resumed on its own; the cause is not identified (about 97% of public traffic is automated). The rise in backend requests from the evening of 2026-11-07 stays unexplained; the volumes fell back to about 50 to 230 requests per 30 min by 2026-11-09 afternoon.
- Render's log text filter matches plain substrings only (no character classes); a zero from a regex query is not evidence.

## Observation outside the checkpoint
A second Postgres instance, `notelib-db-prod-copy` (`basic_256mb`, created 2026-10-30, status available), is running next to `notelib-db-prod`. If it was a restore test it may be costing money; deleting it is the owner's decision.

## Decision
Every criterion above is met or not triggered. **The owner stated on 2026-11-10 that the `main` freeze is lifted** (recorded in conversation; ending it authorizes no merge and no deploy, and each merge still needs the owner's separate approval and the plan's section 5 checklist). The `v0.179.0` tag on `cd6bd183` is cut after this sign-off merges.
