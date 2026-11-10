# Product UX second opinion: the v0.180.0 deploy plan (decisions needed before the owner is asked to approve)

Written 2026-11-10 by the Claude Code release session. Paste this whole file to Product UX. It is self-contained. The full plan is `docs/claude-plans/2026-11-10-v0180-deploy-plan.md` (attach it if you can read files); this prompt carries only what needs a decision.

## 1. Where we are, in plain words

`v0.180.0 — Public Page Reliability and Privacy` is built, verified and merged into its release branch (`releases/v0.180.0`, tip `d598a1e2`). It is **frontend only** and nothing is deployed. It does four things:

1. **Fixes a recorded privacy defect.** Creator and profile pages could show a withdrawn note, an old bio or a profile switched to private for up to 5 minutes. They now read through a strict 120-second in-memory cache that never serves data older than that, and fails closed instead of showing old data.
2. Shows a calm "We couldn't load this profile" state (with "Try again") and a `noindex` head when a profile cannot be loaded. The HTTP status of such a page stays 200 (no crawler-visible error handling is claimed).
3. Keeps the exam hub's notes when only its official review sets fail to load.
4. Stops `.` and `..` usernames from reaching the backend.

It was verified locally, on an isolated Vercel Preview (13 of 13 checks on the second run) and by two independent cold reviews. **Not verified:** real production traffic, and a production deployment's own settings.

**The deploy is one merge into `main`.** Vercel rebuilds production from it. Because `backend/pom.xml` carries the new version number, **Render will also restart the backend with unchanged code unless the phrase `[skip render]` in the merge message works** (untested on this service). Both earlier redeploys of unchanged code were followed by clean one-hour database reads, but `CLAUDE.md` records that every deploy since 2026-10-25 except `v0.178.0`'s was followed by a Postgres kill, so the plan treats the Render restart as a deploy.

## 2. What the plan proposes (details in the plan file)

- **Timing:** the 24-hour gate after the last deploy opens 2026-11-11T09:52Z (17:52 PHT); the first slot that is also off-peak is **Thursday 2026-11-12 05:00 PHT**. The owner picks the slot.
- **`main` is held** from the merge until the 24-hour watch ends (the queued Pricing release and the Note Strategist docs PRs wait), because any later merge removes Vercel's one-step rollback.
- **Hard stops (pause and decide, never automatic rollback):** any Postgres signal-9 kill; backend 5xx above 2% sustained across five consecutive one-minute periods; connections above 25 for 5 minutes; memory above 260 MB at two consecutive reads; **plus two new ones for this release:** a production creator or profile response whose `cache-control` lacks `no-store` or contains `public` or `s-maxage` (tested on meaning, not the exact string), or whose `x-vercel-cache` is `HIT` or `STALE`; and the profile-read error share above 20% in any 15 minutes that contain at least 10 reads.
- **Findings, not stops:** a `CATALOG_CACHE_DEADLINE class=profile` line, any `EXAM_HUB_REVIEW_SETS_UNAVAILABLE` line, wait buckets of 10 seconds or more, and the backend-load observation below.
- **Order:** sign-off commit on the release branch before the merge (so there is exactly ONE merge to `main`), merge with `[skip render]`, watch for 72 hours, then tag `v0.180.0` and publish the GitHub Release.
- **A required production smoke check:** one request each to a real creator page and a real profile page at about 5 minutes after the deploy, checking that the response is not cacheable (`no-store`, no `public`, no `s-maxage`, not a cache `HIT` or `STALE`). The owner picks the pages and runs it or authorizes Claude to issue exactly those two requests.

## 3. Facts you should weigh (all read 2026-11-10)

- The database log has no kill line in the last 24 hours; Vercel production has no cache-setting overrides; the rollback target is `dpl_39MdpRZxjCPNoU31endwJYmei4o2`, and rolling back would bring the privacy defect back.
- **There is no clean measure of backend load from creator and profile reads, before or after the deploy.** Render keeps no request logs for this service, its request-count metric cannot filter by these paths, and the diagnostics line does not attribute them. The nearest database statement (the lookup of a user by username) is shared with sign-in: 2,847 calls in about 9.7 days (about 12 an hour on average), of which the profile share is unknown. Any "did backend profile reads rise" reading will therefore be coarse.
- The new cache is per server instance, so with many instances it can send MORE reads to the backend than the old shared cache (accepted earlier as D2, never measured; up to 19 to 21 instances appeared in one Preview burst).
- **The profile read code writes no log line when a read fails.** Only a read that outlasts its 15-second deadline logs. Failures are visible through an error count in the cache statistics line, and through the framework's own error log, whose presence for this case was not confirmed in the Preview logs.

## 4. Decisions needed

1. **Backend-load observation threshold.** The plan proposes treating either of these as a finding: the username lookup above three times its pre-deploy hourly rate (the largest of the three hours before the deploy, at least 12 an hour) in two consecutive hours, or total backend requests above twice the busiest hour of the 24 hours before the deploy. Is that acceptable as a coarse finding, given no per-path baseline exists and the statement is shared with sign-in? Would you rather it be a stop, or dropped?
2. **The profile error-share stop** (above 20% of at least 10 reads in 15 minutes). Is it the right stop, given failures are visible only through the cache statistics line?
3. **Sign-off before the merge or a second docs PR after the watch.** The plan signs off first (release notes written, status Released with the +72 h read pending, as with `v0.179.0`) so that one merge exists; if the watch fails the notes are corrected. Confirm.
4. **Is a further cold review of the merged tip wanted?** Two independent reviews ran on this exact code; the merge added no code.
5. **Should a missing failure log line block the deploy, or be recorded as a follow-up?** Adding one is a code change and a new verification round; the plan treats it as a follow-up and relies on the `err` count.
6. **The `[skip render]` test.** If it works, `scripts/check-deploys.sh` will report Render as BEHIND (exit 1) because Render stays on the previous commit; the plan calls that expected and acceptable and says not to redeploy Render. Confirm.

Please answer each numbered decision with accept, change (say how) or reject, and say anything else that should stop the owner from approving. Nothing is deployed or approved by this prompt.
