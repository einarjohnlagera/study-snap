# Product UX addendum: what changed after your Stage B decisions, and what needs your acknowledgement (v0.179.0)

Written 2026-11-04 by the Release Implementor session. Paste this whole file to Product UX. It is self-contained and follows `docs/claude-plans/2026-11-03-v0179-stage-b-cold-review-product-ux-prompt.md` (your D1 to D5).

## Where things stand

Your decisions D1 to D5 were implemented (PR #1556: B3, B3b, B3c) and verified on a real Vercel Preview with a fake catalog: **22 of 22 checks passed**, including the slow walk failing closed at 90.3 s, the 400-page guard, strict `hasMore` handling, junk subject URLs not cached, and recovery 3.7 s after a failure window. Stage A (the backend half) has been live since 2026-11-03T10:56Z with no kill, no 5xx, a survived walk-shaped burst, and the new listing queries running about 33 times (unfiltered) and 1.9 times (`courseProgram`) faster. Its 24 h and 72 h reads are still open, and **Stage B is not production-ready**: nothing deploys without those reads, your acknowledgements below, the owner's platform-limit read and the owner's separate approval. The draft deploy plan is `docs/claude-plans/2026-11-04-v0179-stage-b-deploy-plan.md`.

Eight things differ from, or were not in, what you approved. Each needs one line from you.

## 1. D3: option (b) was not available (the two constraints)
You preferred validating subject slugs before walking, with a genuine HTTP 404, and authorized "not caching empty results" if (b) was uneconomical. Two facts blocked (b): there is **no subject registry without backend work** (the only list is a backend query on the 300 s stale-while-revalidate cache that fails open to an empty list, and subjects are free text), and **a genuine 404 is impossible while L1 stands** (verified on the Preview: an unknown exam slug already calls `notFound()` and still returns HTTP 200 with a noindex shell, because the root layout commits the status first). Fallback (a) was built: empty subject-slug walks are returned and not cached. **Cost you should know:** a junk or empty subject URL now costs one backend walk per request (the page's metadata and body share it; measured), where before Stage B it was cached for 300 s.
*Your answer:* accept fallback (a) and the no-404 limitation, or name an alternative.

## 2. D1: the sitemap deadline is 240 s, not the approved 10 minutes
The platform function limit is believed to be about 300 s on this plan (unverified; the owner is reading Settings, Functions). A 10-minute deadline could never fire, so a slow sitemap would end as a platform timeout instead of a clean failure. 240 s is provisional. It cannot be tested on the Preview (the harness shortens the sitemap TTL, which clamps the deadline).
*Your answer:* confirm 240 s, or say what to use if the owner's read shows a different limit.

## 3. Not in your response: a 15 s failure backoff (owner decision)
After any failed load (error, malformed reply or deadline) a key rejects for 15 s without calling the backend and never serves data. Why: aborting a frontend request does **not** cancel the backend request or its database query (production `statement_timeout` is 0 and the backend sets no query timeout), so an immediate retry would stack a fresh walk on the abandoned one during a slow-database episode. It reverses the earlier "a failure is not cached" rule for that window. **Visitor effect:** a transient failure shows the existing error shell for up to 15 s on that page instead of recovering on the next request; rails render empty; the sitemap returns 500 for up to 15 s.
*Your answer:* accept 15 s, change the window, or reject the backoff (the cost of rejecting is the stacked-work risk above).

## 4. Not in your response: walks are de-duplicated by note id
A note published mid-walk shifts the offset and repeated one item (the sitemap could carry a duplicate URL). A completed walk now keeps the first occurrence of each id. **Not fixed:** a note unpublished mid-walk can still make a walk skip one item (needs a consistent snapshot); the list is accepted as complete and lasts one TTL (the sitemap: one hour).
*Your answer:* accept the unfixed skip case.

## 5. New measurement: the first visitor after an expiry waits for the whole walk
With a hard TTL and no stale-while-revalidate, the first request after the 120 s expiry waits for a fresh whole walk (concurrent visitors share it). Stage A's production numbers put deep unfiltered pages at about 0.44 s each, so a whole-catalog walk of about 46 pages is **about 20 seconds** (a rough estimate: it assumes Stage A's unfiltered calls were whole-catalog walks, which is unproven). A `courseProgram` exam-hub walk runs at about 1.3 to 1.6 s per page. The cache is also per instance (one Preview burst reached 19 to 21 instances), so many visitors can each pay a cold walk. This is the price of the hard bound you approved; the draft deploy plan reports the real cold-walk times from the first hour of logs.
*Your answer:* accept a cold wait of tens of seconds for `general` and for hubs, or say what you would rather trade (the only levers that do not weaken the privacy bound are accepting it, or later changing what the page walks).

## 6. Observability limits
Vercel Hobby keeps runtime logs for **one hour**, so the walk durations, deadline lines and guard-breach lines (your "operational signal" for the 400-page guard) are only visible by watching live in the first hour after a deploy. Anything beyond that is database-side reads only. No paid service is proposed (your anti-drift list).
*Your answer:* acknowledge.

## 7. Rollback and a freeze on `main`
On the Hobby plan Vercel can roll back only to the **previous** production deployment, and every merge to `main` (even docs-only) creates a new production deployment. So `main` must be frozen from the Stage B deploy until the 24-hour watch ends, or the one-step rollback disappears.
*Your answer:* acknowledge.

## 8. D4 is recorded as a confirmed privacy defect
Creator and profile pages stay on the 300 s data cache (`frontend/lib/server-public-profiles.ts:20,45,53`); a Backlog row records it as an explicit, temporary exception, prioritized after Stage B stabilizes, with the rule that no statement may claim every public listing meets the 2-minute or 5-minute bound. Two further known limits from a second cold review are recorded in `RELEASES.md` (ages use `Date.now`; a TTL override below the walk duration would fail every cold load; production has no such override as of 2026-11-04).
*Your answer:* acknowledge.

## What is not being asked
No database upgrade, no crawler blocking, rate limit, robots or `noindex` change, no sign-in wall, no change to the busy copy or Retry, no backend change, no production deployment. L1 stays accepted. Production deployment needs the owner's separate explicit approval.

## How to answer
For each of 1 to 8: one line, "accept", "change to X" or "reject", with a reason if you change or reject. If you disagree with a stated fact, name it and the cited file or run will be re-read with you.
