# v0.179.0 Stage B: deploy record and formal +24 h read (T0 2026-11-06T15:56:32Z; read 2026-11-07T15:57:50Z)

Written 2026-11-08 by the Release Implementor session in the shape Product UX required in its "Stage B +24h Checkpoint and Database Load" decision (2026-11-07). **Result: PASS on the +24 h criteria as written, with one interpretive item (the 503 blip) for Product UX to confirm. This approves no deployment and no change; no production code, cache, infrastructure or configuration was changed.** All reads were read-only. Direct observations, calculations, hypotheses and unknowns are labelled.

## Deploy record
- PR #1571 (`releases/v0.179.0-stage-b` into `main`, tip `bf62857d`, 38 files, none under `backend/`, no version or package file) was merged by the owner at 2026-11-06T15:55:02Z as `cd6bd183`. The owner's approval was "lets do it now" (about 23:40 PHT), recorded in the PR; slot chosen by the owner (the recommended slot was Saturday 05:00 PHT; the deviation to a near-midnight slot was accepted).
- Vercel production deployment `dpl_3DYfi2WhryAmg8osG4wLtTBXKkN1` became READY at **15:56:32Z (T0)**, aliased to `www.notelib.app` and `notelib.app`. Render did not redeploy (still `dep-dbkrqbp5efls73etm3i0`, Stage A). Rollback target: `dpl_fZ7WPyhKuNpN4Tqs6faYqHz4Bvja` (the `f5367e2c` build); `main` frozen since the merge.
- First-hour runtime logs were saved at +10, +30, +45 and +55 minutes (scratchpad, not committed): three `CATALOG_WALK outcome=ok` subject walks of 1 page (555 to 972 ms), the first cache hits at 16:21Z, `w10p` 0, no error, deadline or guard-breach line, two instances seen.

## 1. Checkpoint verdict: PASS (one item to confirm)
- Zero non-deploy database kills: no `signal`, crash-recovery, "interrupted", "terminated" or "automatic recovery" line from T0 to the read; `pg_stat_statements_info.stats_reset` still `2026-11-01T20:49:16Z`; no Render deploy; connections 20 (21 at three samples), never above 25.
- No hard stop fired as written (see 2 and 3). **Limit:** Vercel keeps runtime logs for one hour, so the walk-side stops (deadline, guard breach, `outcome=error`, p95 above 45 s, any walk above 80 s) are verified directly only for the last hour (clean). For earlier hours the backend shows each large walk completing within about a minute (52 or 44 listing requests in one minute).

## 2. The 503 incident (2026-11-07, about 06:24 to 06:26Z)
- **Timestamps and count:** Render metrics: 2 HTTP 503 in the 06:25Z minute and 1 in the 06:26Z minute (3 in total, about two minutes). The application's diagnostics file them under minute `06:24Z`: `total=41`, `statuses=[200:38,503:3]`.
- **Rate:** Render counted 36 requests in the five-minute window 06:22 to 06:26Z (22 at 06:25, 14 at 06:26, none in 06:22 to 06:24): **3 of 36 = 8.3%**. Application minute: 3 of 41 = 7.3%. Minutes 06:27 to 06:29Z had no traffic.
- **Endpoint:** unknown (no request log is available; the diagnostics show 32 facets and 9 listing requests from 9 distinct browser-style clients, all direct to the backend, not via Vercel).
- **Nearby events:** two client-abort `unhandled_exception` lines (broken pipe) at 06:24:08 and 06:24:11Z, and 499s at 06:25 and 06:26Z.
- **Coincidence checks:** no catalog walk (`walk=none`; the 06:41Z sitemap walk came 15 minutes later); database memory 153 to 171 MB (no pressure), connections 20; no restart or deploy; the same instance served throughout.
- **Hard stop as written ("backend 5xx above 2% for 5 minutes"):** Reading A (sustained for five minutes): not met (two minutes). Reading B (any five-minute window above 2%): met (8.3%). This report applies Reading A and asks Product UX to state the intended reading. Under either reading the stop procedure is pause and report; a rollback would not address a fault whose cause is unknown and which did not involve Stage B's code path. **Cause: unknown.** **Update 2026-11-08: the mechanism was identified (the v0.178.0 ranking-snapshot fallback bulkhead, `PUBLIC_LIBRARY_RANKING_BUSY`; cumulative rejections 0 to 3) and the reading accepted by Product UX; see `docs/claude-plans/2026-11-08-v0179-503-reconciliation-and-request-volume.md`. The rejected requests and the expired-snapshot trigger remain partly inferred.**

## 3. Database health
- **Memory (observed samples, not inferred peaks):** hourly and 15-minute samples over T0 to the read: minimum 148 MB; highest 15-minute samples 251.7 MB (11:15Z) and **266.0 MB (99.1% of the 268.4 MB limit) at 2026-11-07T15:20:00Z**. 30-second samples around 15:20Z: 253.8, 254.4, 254.4, 266.0, then 171.1 MB at 15:20:30Z. **Only one reading is above 260 MB, so the "two consecutive reads" stop did not fire.**
- The 15:20Z spike was **not walk-driven** (4 to 5 requests a minute, `walk=none`). The 11:15Z spike coincided with a 52-page hub walk (157 MB at 11:14Z to 251.7 MB at 11:15Z, 230 to 250 MB for about 14 minutes, 177 MB by 11:32Z, 167 MB by 11:45Z). Spikes recover within minutes to half an hour. Drops of 60 to 95 MB in a single sample occur often, cause unknown.
- **Trend: inconclusive.** Highest observed samples: 265.5 MB (11-06), 251.7 MB, 266.0 MB; none produced a kill.

## 4. Catalog workload: Stage A versus Stage B
- **Correction of an earlier figure:** the first Stage B read quoted `courseProgram` at about 14 an hour against about 6. That used a 13.5-hour window containing all three hub walks. Over the 29.1 hours from the 72 h read (2026-11-06 about 10:51Z) to the read: `courseProgram` 445 to 669 calls (+224, **about 7.7 an hour against 6.2 in Stage A, roughly 24% higher**); unfiltered 978 to 1,452 (+474, **about 16.3 an hour against 13.6, roughly 20% higher**). That window includes about 5 hours before T0, so it is not a clean Stage B rate. In the 13.5 hours from 02:29Z: 12.1 an hour (`courseProgram`) and 18.4 an hour (unfiltered). Current means: 289.6 ms unfiltered, 1,274.8 ms `courseProgram`.
- **By walk type (minutes with 38 or more listing requests, all HTTP 200):**

| Window | Hub-shaped (`courseProgram`) | Sitemap-shaped / unfiltered 44-page |
|---|---|---|
| Stage A, 72 h | 3 episodes (about 1 a day): 11-03 12:50Z (54); 11-05 05:06Z and 05:07Z (41 and 68) | 6 (about 2 a day; unattributed at the time): 11-03 14:16Z (45), 16:30Z (44); 11-04 14:17Z (44); 11-05 07:29Z, 14:22Z (44); 11-06 02:15Z (44) |
| Stage B, 24 h | 3 `exam-hub` walks of 52 pages: 11-07 04:21Z (56), 11:14Z (59), 12:17Z (52) | 3 `sitemap` walks: 06:41Z (44), 13:01Z (38 plus 6 the next minute), 14:26Z (44) |

  A further small `exam-hub` walk of 6 pages occurred at 2026-11-06T23:23Z. `other:0` throughout. Counts of 3 against 3 are too small to establish a rate difference.
- **Investigation answers (direct observation unless stated):** (1) Different instances for the hub walks: unknown (logs rolled off); six distinct instances served traffic in the last hour. (2) Cache expiry: hub walks were at least 63 minutes apart, never within the 120 s TTL; each is a cold load after expiry. (3) More legitimate traffic: not indicated; Web Analytics pageviews (bot-dominated proxy) were 529, 889, 602, 414 on 11-03 to 11-06 and 267 on 11-07 to 15:00Z. (4) Same hub or different: unknown (no hub identity in the diagnostics or retained logs). (5) Sitemap: about 132 of the +249 unfiltered calls since 02:29Z; the first real sitemap walk took **23.5 s for 44 pages and 2,186 notes**. (6) Speculative fetching: no evidence (subject-walk attribution is 1 to 2 requests a minute with no ramp).
- **Hypothesis, not proven:** the per-instance 120 s cache causes repeated traversal. The evidence neither supports nor rules this out. Stage B bounds freshness; it is not shown to reduce walk load.

## 5. Cache effectiveness
- **Known (last hour, 15 `CATALOG_CACHE_STATS` lines, six instances, 197 rail reads):** 62 hits (31%), 72 shared loads (37%), 63 misses (32%): about 69% of rail reads avoided an upstream fetch. Non-zero lines only. `w10p` 0 throughout; about 47 of the 197 reads waited 1 to under 10 s (a cluster at 14:29 to 14:31Z, around the 14:26Z sitemap walk).
- **Unmeasured:** hub and sitemap hit ratios, whether dilution costs extra walks, anything before the last hour.

## 6. Learner experience
Not observable beyond the above: no cold-page wait data and no failure rate for learners. One note-page `fetch failed … other side closed` (baseline shape, about 10 an hour before the deploy). HTTP 200s do not show a good learner experience.

## 7. Main freeze
The plan lets the freeze end when the +24 h criteria pass. **Recommendation: keep it until Product UX confirms the 503 reading.** Ending it authorizes no further deployment. Merging this report into `main` is itself a freeze-breaking production deployment on Hobby (it would become the "previous" deployment and remove the one-step rollback target), so **this docs PR must wait for the freeze to end.**

## 8. +72 h plan and unresolved risks
Monitor kills, 5xx, memory and connections, walk attribution by type, listing counts per type from a recorded baseline (2026-11-07T15:57:50Z: unfiltered 1,452 calls, `courseProgram` 669, 1,572-length shape 1,089), `CATALOG_CACHE_STATS` in the last hour of each check, and the denominator clause (met by Stage B itself: five minutes of 44 to 59 attributed listing requests, all HTTP 200). Unresolved: hub and sitemap walk frequency versus Stage A, the 266.0 MB single samples (not walk-attributed), the 503 cause, the missing earlier-hour Vercel logs.

## 9. Recommendation
Continue under the existing plan. No rollback, no change, no optimization selected. Optimization of the repeated 52-page hub traversal is for a separate investigation and review; the 120 s TTL stays.
