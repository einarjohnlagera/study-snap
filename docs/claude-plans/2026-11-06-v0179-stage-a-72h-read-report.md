# v0.179.0 Stage A: formal +72 h read (2026-11-06T10:56:40Z; window 2026-11-03T10:56:13Z to read time)

Written 2026-11-06 by the Release Implementor session, in the seven-item shape Product UX required in its Option C decision (2026-11-05). **Result: PASS on H1, H2 and H3. This is not a deploy approval and not a Stage B go decision; the owner's separate explicit approval is still required.** All reads were read-only (database logs and metrics, backend metrics, `SELECT` on `pg_stat_statements`, Web Analytics, Vercel deploy and environment listings). The numbers are a snapshot taken at the read time.

## 1. Gate results
- **H1, zero non-deploy database kills: MET.** No `signal` line and no crash-recovery, "interrupted", "terminated" or "automatic recovery" line in the database logs for the whole window; the last 16 minutes before the mark are also clean. Independent confirmation: `pg_stat_statements_info.stats_reset` is still `2026-11-01T20:49:16Z` (the time of the last kill), and a crash recovery since then would have reset it. Render shows no deploy after Stage A (`dep-dbkrqbp5efls73etm3i0`, live 2026-11-03T10:56:13Z).
- **H2, at least one minute of about 40 or more anonymous listing requests: MET, repeatedly.** Nine minutes with 41 to 68 listing requests, all HTTP 200, found by exact-string log searches for `listing:30,` to `listing:99,` (the log search cannot filter by count and cannot prove exhaustive coverage): 2026-11-03T12:50Z (54, 63 total), 14:16Z (45), 16:30Z (44); 2026-11-04T14:17Z (44); 2026-11-05T05:06Z (41), 05:07Z (68, all `courseProgram`-shaped, 109 requests in two minutes: the 2026-11-01 kill signature), 07:29Z (44), 14:22Z (44); 2026-11-06T02:15Z (44). All show `walk=none` (the old walker pattern; Stage B is not deployed).
- **H3, no 5xx above 2% for 5 minutes after a burst: MET.** Status totals over the window, from 3-hour buckets so approximate: about 18,900 HTTP 200, about 600 HTTP 499 (client aborts), about 110 HTTP 401 and a few dozen 403, 404 and 204; **no 500, 503 or 504**. The only 5xx are 8 HTTP 502s in the first 3-hour bucket; 2-minute and 5-minute windows from 10:56:13Z to 13:56Z show zero, so they predate the mark (bucket alignment; inferred, not verified individually) and belong to the Stage A deploy cutover. The buckets containing the bursts above show no 5xx.
- **Overall: PASS.**
- **Backend `unhandled_exception` errors:** about 18 or more ERROR lines from 2026-11-04T15:09Z to 2026-11-05T15:36Z. The one I read in full is `HttpMessageNotWritableException ... Broken pipe` (a client disconnected mid-response, consistent with the 499s); the others share the handler and were not each read. None produced a 5xx.

## 2. Database stability and memory
- **Connections:** 20 throughout (21 at one sample, 40 only at the deploy instant); never above 25 afterwards.
- **Memory, reported by resolution and not inferred from hourly samples:**
  - Hourly samples, 2026-11-03T10:56Z to 11-05T09:56Z: minimum 156.7 MB, maximum 251.7 MB (11-03T20:56Z).
  - 15-minute samples, 2026-11-05T10:00Z to 11-06T10:45Z: minimum 147.5 MB, **maximum 265.5 MB (98.9% of the 268.4 MB limit) at 2026-11-06T02:15Z, the minute of a 44-request whole-catalog walk; 208 MB at 02:30Z, no kill.**
  - 60-second samples around the 2026-11-05T05:06 burst: 158 MB at 05:05Z, peak 250.9 MB at 05:08 to 05:09Z, 220 MB by 05:12Z.
  - Earlier fine-grained reading: 256.4 MB at 2026-11-04T10:45Z.
  - Single-step drops of 60 to 80 MB occur often (for example 234 MB to 167 MB at 11-05T05:00Z); cause unknown, no restart evidence.
  - **Assessment: inconclusive.** The highest observed readings rise (251.7, 256.4, 265.5 MB) but come from different sampling resolutions and coincide with bursts; the true peak can be higher than any sample. No new threshold is introduced. Against the existing provisional target (maximum at or below 250 MB) the maximum was exceeded; reported, not enforced. The p95 was not computed.
  - **Relationship to bursts:** the 250.9 and 265.5 MB readings occur at or right after walk-shaped bursts; memory then falls within minutes.

## 3. Traffic and burst evidence
- **Public traffic is overwhelmingly automated** (Web Analytics, window): China 474 visitors, Hong Kong 359, Singapore 318, US 198, Mexico 108; Philippines 18 (246 pageviews, so engaged genuine browsers); desktop 1,454, mobile 41. Hourly visitors are flat around the clock. Earlier statements in this release's working notes that these were "real visitors" or "the busiest real-visitor day" were wrong and are corrected here.
- **Genuine learners** (database, owner accounts and ADMIN excluded): 2 signups on 2026-11-03; users with a quiz session per day 1 (11-03), 2 (11-04), 1 (11-05), 0 so far on 11-06.
- **Correction to the informal +48 h look (2026-11-05):** it said no second burst was found on day 2. That was wrong: day 2 contained the bursts of 2026-11-04T14:17Z and 2026-11-05T05:06Z, 05:07Z and 07:29Z; only the largest request buckets had been drilled into.
- **Latest 15-minute request totals** are ordinary; the largest 3-hour bucket (about 2,430 requests, 2026-11-05 from 16:56Z) includes curator and verification activity.

## 4. Listing-query performance (new measurements at 72 h; paired old and new shapes by query length; compare with the +24 h figures)
| Shape | Before Stage A | At +24 h | At +72 h (new) |
|---|---|---|---|
| Unfiltered offset page | 14,481.5 ms mean (86 calls) | 325 ms (328 calls) | **343.4 ms mean over 978 calls, max 3,697 ms (about 2.4% of before)** |
| `courseProgram` offset page | 2,995.2 ms mean (360 calls) | 1,214 ms (158 calls) | **1,245.0 ms mean over 445 calls, max 7,192 ms (about 41.6% of before)** |
Other statement shapes were not paired.

## 5. Remaining deployment blockers
- The owner's separate explicit approval and the deploy-time checks in the deploy plan (no non-deploy kill in the previous 24 hours, environment-override re-check, verified rollback target, backup decision, off-peak slot, first-hour log collection).
- **Seven docs pull requests (#1562 to #1568) were open at the read time.** On Hobby, any `main` merge after the Stage B deploy and before the end of its 24-hour watch removes the one-step rollback target, so each must be merged or held before the deploy day. `releases/v0.179.0-stage-b` is 3 commits behind `main` (docs only) and needs a merge-forward and re-check at deploy time.
- **Vercel environment (read 2026-11-06):** only `NEXT_PUBLIC_GOOGLE_CLIENT_ID` and `NEXT_PUBLIC_API_BASE_URL`; no `NOTELIB_CATALOG_*` override (values not read). The owner confirms Render has none.
- **Rollback target at the read time:** `dpl_81NyiV59QtAqGtAJo1GUdDvFmTa1` (`1fbe0ed9`); it changes with every `main` merge and is re-read at deploy time.

## 6. Recommended slot (Philippine time)
Public traffic is flat around the clock and bursts occur at all hours, so there is no genuine quiet period. The lowest request totals (3-hour buckets of 142 to 154 requests, 03:56 to 09:56 PHT on 2026-11-06) and the lowest learner activity (02:00 to 07:00 PHT; the busiest learner hour is 21:00 PHT) point to the early morning. **Recommended: Saturday 2026-11-07, 05:00 PHT (21:00Z Friday)**, with the owner present for the merge and approval and the log collection at +10, +30, +45 and +55 minutes. Fallback: a weekday morning PHT, for example Monday 2026-11-09 at 05:00 PHT. A Friday-evening slot is not supported by the evidence. A weekend deploy puts the +24 h and +72 h reads on Sunday and Tuesday respectively.

## 7. Recommendation
**GO-eligible: the formal Stage A gate passes.** Proceed to a deploy slot after the open docs PRs are decided and the deploy-time checks pass. **Flag for the owner and Product UX (no threshold changed):** the Stage B hard stop "database memory above 260 MB at two consecutive reads" has a premise ("the highest ordinary reading so far is 252.5 MB") that is now out of date: 265.5 MB was read once during an ordinary Stage A burst, so one burst can already approach the stop. **Exact approval required from the owner:** "I approve Claude opening the final pull request from `releases/v0.179.0-stage-b` into `main`, and I will merge it and deploy Stage B to production at [date, time PHT]. I have recorded the rollback target [Vercel deployment id], my backup decision is [taken / not needed], and I accept the 24-hour `main` freeze and the hard stops in the Stage B deploy plan. Claude collects the first-hour logs."

## Limits of this read
Burst coverage is not exhaustive; memory peaks are not known from samples; status totals are approximate (3-hour buckets); the 502 attribution is inferred; the `unhandled_exception` lines were not each read; the p95 memory and per-shape query pairs beyond the two listed were not computed; Web Analytics counts JavaScript-running visitors, which are mostly automated here. The Preview results that support Stage B used a stand-in catalog and measure behavior, not production-scale latency or capacity.
