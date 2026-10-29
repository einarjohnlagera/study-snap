# The operational gate (database signal-9 kills) that holds two finished releases: consultation prompt for Product UX

> **SUPERSEDED 2026-10-29 by `docs/claude-plans/2026-10-29-v0178-production-stabilization-plan.md` and the owner's decisions of the same day.** Where this prompt disagrees, the plan wins: the owner **deferred the database upgrade** (D2 is now the fallback, with explicit triggers, not the recommendation); a **docs-only canary is not required** (D4's third condition is dropped, because a docs-only deploy still overlaps connections and reduces no load); normal feature releases stay blocked but **one narrow stability release is allowed as an exception**; and the Explore migration is kept separate from the first fix. Section 1b's Vercel evidence is still accurate; the plan adds that the production API is called **directly by browsers** (a Vercel firewall cannot protect it), that the dominant load is the **single-tag ranked listing**, and that **Vercel production builds walk the catalog against the production API** at deploy time. Read the plan first.

**How to use this:** paste into a Product UX session, in this order:
1. `docs/gpt-contexts/GPT_CONTEXT.md`;
2. everything below the horizontal rule.

**Then attach these files** (internal; no learner content, one owner-account id prefix appears in the finding, remove that if you share outside the team):
- `docs/claude-findings/2026-10-28-db-signal9-kills-crawler-shaped-public-traffic.md` (the 2026-10-28 diagnosis);
- `docs/claude-plans/2026-10-28-db-signal9-traffic-mitigation-options.md` (the options, owner actions first, code second);
- `docs/claude-plans/2026-10-14-db-oom-memory-ceiling-fix-plan.md` (the plan whose "Leg A" is still unbuilt);
- `docs/claude-plans/2026-10-29-challenge-quiz-quality-v0177-evaluation-report.md` (only for context on why the two held releases deliver no user-facing change).

**Why it exists:** the owner asked on 2026-10-29: "if we're going to hold it, why not fix the one that's holding the two releases already?" Two releases (`v0.176.0`, `v0.177.0`) are finished and closed on their branches, unmerged, because the production database is killed by signal 9 after every deploy and on its own during traffic bursts. The owner wants this fixed now, not scheduled behind more waiting.

**Indexing note:** a one-off consultation prompt, under the Backlog Index's *release artifact* exemption. Record the answers as owner decisions.

---

You are Product UX for NoteLib, a notes-first study app (voice: calm, helpful study companion; no learner-facing copy changes are asked here). I need your decisions on a production-stability problem so the Release Implementor can sequence the work. **I do not need an engineering design.** I need product and cost trade-offs decided, with a default for each so I can proceed.

## 1. The situation, with what was verified and when

**Verified by me on 2026-10-29 at about 06:20 UTC, read-only (Render metrics and logs, a `SELECT`):**
- The production database (`notelib-db-prod`, PostgreSQL 18, Singapore) is still on the **`basic_256mb` plan, memory limit 268,435,460 bytes**. Over the last 18 hours its memory ran **180 to 243 MB** (30-minute averages): about 70 to 90 percent of the limit even in quiet hours, with little headroom. Connections 21 of 103; `statement_timeout` is 0 (no limit); `shared_buffers` 64 MB.
- **No kill since 2026-10-28 13:38:47 UTC (about 16 hours 40 minutes).** I confirmed the log filter works by re-reading the 13 kills the investigator listed for 10-28 (08:28 to 13:38 UTC). The newest backend deploy is still `v0.175.0` (live 2026-10-26 14:58:47 UTC); nothing deployed since.

**From the Prod Investigator's 2026-10-28 finding (not re-derived by me):**
- **28 kills from 2026-10-21 to 2026-10-28** against a baseline of 9 in 7 days; 13 of them on 10-28 alone, 8 to 75 minutes apart. Three kills were adjacent to deploys (`v0.173.0`, `v0.174.0`, `v0.175.0`), and every deploy since 2026-10-25 was followed by one.
- The 10-28 kills **coincide with** a burst of anonymous, automated-looking traffic on public note pages (about 7,200 views, all anonymous, 99.8 percent on the owner's seed catalog, about 4 views per note; backend request volume 15 to 40 times normal). **The source is not identified**, and the finding says "consistent with", not "caused by": request volume stayed about 25 times normal after the kills stopped.
- The database spends that time on the public-catalog reads (slow statements up to 39 seconds). Users saw errors in the burst hours (hundreds of 502, 500 and 499 responses).

## 1b. New evidence from Vercel, read-only, 2026-10-29 (the Vercel connection is now live; I used read tools only)

- **The runtime logs for the burst are gone.** The Vercel team is on the **Hobby plan, which keeps runtime logs for 1 hour**; the 10-27 and 10-28 windows cannot be recovered, so the user agent and IP of the 7,200 note-page hits cannot be identified after the fact. (Pro keeps 1 day, and 30 days with Observability Plus.)
- **Vercel Web Analytics (real browsers that run JavaScript) did see a burst, and it is not the Philippine audience.** On `/public/library` pages, between 07:00 and 17:00 UTC on 10-28: **430 page views from 390 visitors**, from zero in the three hours before. **99% were desktop Chrome** (382 of 387 visitors; Windows 209, Mac 173, only 5 on mobile), **no referrer** (2 from Facebook), and by country **China 162, Hong Kong 91, Singapore 64, United States 44, Mexico 22, the Philippines 2**. Roughly one page view per visitor. That mix is consistent with an automated or scripted browser fleet, not learners; I cannot prove it from page views alone.
- **It points at a different page than the finding assumed.** 338 of the 430 views were the **listing page `/public/library`** (302 visitors), only 75 the individual note pages and 17 the subject pages. That page's client code is the likely caller of the **ranked tag-filtered listing the investigator could not attribute** (the heaviest database query), so these visitors may be what drives the slow listing queries. The 7,200 backend note-view events, by contrast, are mostly **not** in Web Analytics, so they came from clients that do not run JavaScript. So there are probably **two sources**: a non-JavaScript crawler hitting note pages, and a JavaScript-running desktop-Chrome fleet loading the listing page. One tool doing both is also possible.
- **No firewall rules exist.** The project has no firewall configuration at all (Vercel returned "not found" for both the config and the attack status), so no rate limit or bot rule is currently in place. What the Hobby plan allows is for the owner to confirm in the dashboard.
- **User-visible errors are on record.** Vercel's error summary shows the public note page failing with "Could not load public note." (70 occurrences, 32 users, last 10-28 12:51Z) and a backend connection closed mid-request ("other side closed") on `/signup` and `/public/creator/[username]` (11 occurrences, 9 users, last 10-28 13:28Z). The table does not say whether those counts are inside my window or lifetime, so treat them as "present", not as a measurement of the burst. It matters for D5: signup was among the routes affected.
- **Frontend and backend are on the same version.** Vercel production is `v0.175.0` (commit `06a09f7c`, 2026-10-26 14:56 UTC), matching the backend, so there is no version skew.

## 2. Why two finished releases are held, and why that is an avoidable catch-22

- `v0.176.0` and `v0.177.0` are Challenge Quiz quality work. Both ended as HOLD on the evaluation and **change no production code and no prompt** (the diff against `main` is tests, docs and version numbers only; I checked).
- Any merge to `main` auto-deploys. Every recent deploy was followed by a kill. So the project's rule is no deploy until the operational gate clears, which means **nothing can ship, including fixes for the gate itself**: the code mitigations below need a deploy.
- The owner-side actions below need no deploy and can act first. **A docs-and-tests-only release is also the safest possible canary** for the question "does a deploy still kill the database?", because it changes no behaviour.

## 3. What I can and cannot do

I cannot change the database plan, Vercel or Render settings, environment variables or any production data: those are the owner's, by a standing rule, and I will not do them. I can read production (read-only), write handoffs and Codex briefs for code, and sequence the work. So "fix it now" means **owner actions first, in the order below, then code after the gate lifts**.

## 4. Decisions I need, each with my recommendation

**D1. Handling the unidentified traffic (free, do first).** The Vercel read is done (section 1b): the logs are gone, but Web Analytics shows a desktop-Chrome burst from China, Hong Kong, Singapore, the United States and Mexico on the listing page, and no firewall rule exists. Product question: *what is our policy for automated traffic on public pages, and do we want a rule scoped to the listing page `/public/library`?* Options: (a) add an edge rule (Vercel firewall: a rate limit or a challenge on `/public/library`, tuned so verified search engines are never blocked; a country block is **not** recommended because Googlebot and Bingbot crawl from the United States and the audience includes Singapore); (b) leave it and rely on capacity (D2); (c) block broadly. **Recommend (a)** with a rate limit first, because the public catalog is an organic acquisition channel. Because Hobby keeps logs for 1 hour, also decide whether to **keep a longer record of the next burst** (a Vercel plan with longer log retention, or a log drain), so the source can be identified next time; I did not look up the cost.

**D2. The database plan ("Leg A").** The 10-14 plan's own trigger ("five or more kills in a week makes the plan upgrade the next lever") fired at 28 in 7 days. Memory sits near 80 percent of the limit even when quiet, so any traffic or a deploy's overlapping connections tips it over. **Recommend upgrading the plan**, confirming the target tier's CPU allocation as well as its RAM, since the finding shows CPU at 0.05 to 0.08 of a 0.1 limit in the kill hours. **I did not look up Render's prices and do not know whether a plan change restarts the database; the owner must confirm both on Render before scheduling** (off-peak). Product question: *what monthly cost is acceptable, and is a short restart acceptable?* If the answer is "no upgrade", say what the alternative is, because the other options only reduce load.

**D3. A statement timeout.** The owner would run a database setting that cancels statements over a set time (currently none; the slowest hit 39 seconds). It sheds runaway queries but fails those requests with an error. The 10-14 plan notes the migration tool shares the database role and must be exempted. **Recommend yes, after D1 and D2**, with a limit generous enough that normal pages never hit it. Product question: *is a failed slow request preferable to a database crash that fails everyone?*

**D4. When does the gate clear?** Right now it has no stated exit. **Proposal, for you to confirm or change:** the gate clears when (i) D2 is done or you explicitly accept the risk, **and** (ii) at least 72 hours pass with no non-deploy kill, **and** (iii) a controlled low-traffic deploy of the held docs-and-tests-only release is followed by no kill for 24 hours. Then `v0.176.0` and `v0.177.0` merge. If (iii) shows a kill, the deploy mechanism itself is the problem and the plan upgrade is not optional.

**D5. User-facing impact.** Users saw error responses during the 10-28 burst. There are no paying learners yet (owner, 2026-10-27). *Confirm that no in-product notice or communication is needed*, or say what you want.

**D6. The code fixes, as a later release (do not start until the gate clears).** Rank by product value: (1) make the public note page cacheable, which means deciding whether per-human view counts must keep firing on every page render (if yes, move the count to a small client beacon; this also excludes bot hits from the counts); (2) a stored, indexed slug column so the page lookup is an index lookup (a migration, the largest item); (3) cache the ranked tag-filtered listing (first find its caller); (4) find what repeats the owner's creator-impact query. *Which of these matter to the product, and is accurate view counting a requirement?*

## 5. What I will do with your answers

- D1 and D2 first (owner actions, no deploy), then a day of watching the kill log and memory.
- Then the controlled deploy of the held release as the canary, with the deploy time recorded.
- Then, only after the gate clears, a Codex brief for the D6 items you rank, as the next release.
- Everything stays inside the carried rules: no learner-facing copy that names a model or implies a plan difference, no pricing or entitlement change here, no production writes by me, no deploy until the gate clears.

**Please answer D1 to D6 as a short list, with a default I can proceed on for each, and say plainly which you want the owner to do first.**
