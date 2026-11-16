# Infrastructure cost, resource use and unit economics: audit (read-only)

## Status: diagnosis only. Nothing in code, config, DNS, plans, pricing, models, deployments or data was changed; no paid API call was made. Cash figures are marked **VERIFIED**, **ESTIMATE** (with its assumption) or **OWNER DATA REQUIRED**. No monetization roadmap is proposed (Feature Planner and Product UX own that). No Backlog Index row written by this session (Release Implementor's to add). Does not modify `docs/claude-plans/2026-11-16-googlebot-crawl-and-public-library-seo-plan.md` and does not touch v0.182.0, Study Plan Discovery or the queued SEO work.

Written 2026-11-16 by the Prod Investigator session. Sources: Vercel MCP billing and deployment reads, Render MCP service, Postgres, event and metric reads, `SELECT` on `notelib_db_prod`, the repository, and earlier findings (`2026-11-15-googlebot-crawl-spike-and-public-library-seo.md`, `2026-10-15-*` and `2026-10-16-*` reads). No invoices, bank records or provider dashboards were available, so most monthly cash lines are OWNER DATA REQUIRED. Prices used for LLM estimates are the repository's own constants (`StudySnapProperties.java:605-610`), "not provider confirmation".

## A. Executive summary

- **What is verified (as effective cost, not cash):** the only cost I can see in billing records is Vercel, and only for the last 9 days: the billing API returns nothing before 2026-11-08. It shows a **$20 a month seat line plus about $10 a month of usage** (effective cost; extrapolated from 9 days, low confidence; the amount actually invoiced is not visible and was $0.00 in 3 of 4 windows). Three quarters of the usage is **build minutes**; of the last 100 Vercel deployments **82 were previews, 6 production and 12 unreadable**, so builds probably come mostly from branch pushes (an inference from deployment counts, not from a billing line). **CDN delivery was about $0.08 in 9 days (roughly $0.27 a month)**, and those 9 days include Google's crawl spike of Nov 11 to 13.
- **One contradiction the invoices must settle:** the founder reports Vercel charges rising over several months, but there is **no billing history before 2026-11-08** (`costs_not_found`), my own note from 2026-10-29 records the team as **Hobby**, and the seat line is already present on the first day of data. That fits a move from Hobby to Pro around early November, so the "increase" may simply be the new seat. Please check the invoices, and check Vercel's Hobby terms on commercial use: if they exclude a product with paid plans, the seat is required, not a saving.
- **What is not known:** every other recurring cost. The Render backend is on a Standard tier and the database on `basic_256mb` with a 15 GB disk, but their charges, the OpenAI invoice, Resend, Xendit fees, Cloudflare and the domain are **OWNER DATA REQUIRED**. Monthly burn cannot be computed yet.
- **Revenue is almost nothing and the cost to serve learners is tiny.** One successful payment ever (₱149, May 2026), no paying user today, and 17 checkouts that expired or failed without payment. Measured Study Pack generation cost is about **$1 to $4 a month**, and **98% of it is the owner's own curation runs**; learners generated only 3 to 11 packs a month since August. So fixed costs, not per-learner costs, are what must be covered.
- **Where savings are most likely:** (1) Vercel preview builds (at most about $7 a month), (2) turning off or confirming off the owner-side LLM prewarm and curation flags, (3) the unmeasured OpenAI bill once it is read. The Render and database tiers should **not** be cut on current evidence (below).
- **Migration:** not justified by this evidence. The whole Vercel bill is roughly $30 a month, so moving to Cloudflare Workers or a VPS could save at most that, against engineering time and production risk, and no reliable alternative cost estimate exists.

## B. Monthly cost inventory

| # | Provider / service | Tier (verified) | Verified monthly cash | Fixed or usage | Source | Rises with learners or bots? | Confidence / missing |
|---|---|---|---|---|---|---|---|
| 1 | Vercel, plan seat | Pro-level seat line, 1 seat | **VERIFIED effective cost $20.00 a month** ($0.667 a day, "Subscription Licenses"); **invoiced amount not visible** | Fixed | `list_billing_charges` 2026-11-08 to 11-17 | No | Medium. Invoice needed. **My own note from 2026-10-29 says Hobby and no billing data exists before 11-08**, so the plan appears to have changed around then (date OWNER DATA REQUIRED) |
| 2 | Vercel, usage | Build, functions, observability, ISR, CDN | **ESTIMATE ≈ $10 a month** ($2.94 over 9 days) | Usage | same | Builds with deploys and pushes; functions and CDN slightly with traffic | Low. "Billed" cost was $0.00 in 3 of 4 windows and $0.42 on one day (Nov 12), so what is actually invoiced vs covered by an allowance is unclear: **invoice required** |
| 3 | Render web service `notelib-backend-prod` | Standard (`plan-srv-008`), 1 CPU, 2 GB, Singapore, 1 instance; changed from `plan-srv-006` on 2026-07-04 | **OWNER DATA REQUIRED** | Fixed | `list_services`, `list_events` | No (CPU peaks 0.2 to 0.5% of 1 core) | Tier verified, price not |
| 4 | Render Postgres `notelib-db-prod` | `basic_256mb`, 15 GB disk, no replica, no HA, PG 18 | **OWNER DATA REQUIRED** | Fixed | `get_postgres` | Pressure rises with public reads; the 15 GB disk holds roughly 0.3 GB of data and cannot be shrunk | Tier verified, price not |
| 5 | OpenAI API | gpt-6.1-sol, gpt-6-luna, gpt-4.1, gpt-4.1-mini | **OWNER DATA REQUIRED**; measured part ESTIMATE $1 to $4 a month (section D). Documented owner-run spend outside the app's logs: the v0.176/v0.177 evaluations at about $3 to $4 per rerun and the v0.182 Stage 1 cap of $12 (conditionally approved 2026-11-13), from the repo docs | Usage | `study_packs`, `quick_review_sessions` token columns | Only with learner generation (now near zero) and owner curation | Low. Only Study Packs and some Challenge top-ups log tokens; other workflows do not |
| 6 | Google Cloud Vision (OCR) | Dependency present; provider default `stub` | Likely $0, **OWNER DATA REQUIRED to confirm** | Usage | code; 0 image-input packs; 2 OCR extractions ever; "OCR disabled" notices in analytics | No | Medium; production env value not read |
| 7 | Resend (email) | Unknown plan; config `daily-limit: 100`, `transactional-reserve: 40` | **OWNER DATA REQUIRED** | Usage | `email_log` | Yes, with learner count and reminder settings | Volume verified: Aug 2,158, Sep 2,041, Oct 1,794, Nov to the 16th 852 emails |
| 8 | Xendit (payments) | Hosted checkout | Fees **OWNER DATA REQUIRED** | Per transaction | `payment_transactions` | With paying users | 1 success, 17 failed or expired checkouts ever |
| 9 | Cloudflare (DNS only, not proxied) | Unknown plan. Supporting evidence: the domain's nameservers are Cloudflare (`kipp` and `malavika.ns.cloudflare.com`), `www` resolves to Vercel (`vercel-dns` and 216.150.x addresses) and live responses carry `server: Vercel`, which fits DNS only | **OWNER DATA REQUIRED** | Fixed | not readable (no access) | No | Not verifiable from the repo or the MCPs |
| 10 | Domain `notelib.app` | n/a | **OWNER DATA REQUIRED** | Annual | none | No | |
| 11 | Vercel Web Analytics, Observability, Speed Insights Plus | Add-ons | Inside row 2 (Web Analytics ≈ $0.018 a day, observability events ≈ $0.02 a day; Speed Insights line $0) | Usage | billing API | Yes, with page views (about 97% bots) | Medium |
| 12 | Monitoring / error tracking | None found (no Sentry or similar dependency) | $0 | n/a | `package.json`, `pom.xml` | n/a | |
| 13 | Development tooling (Codex, Claude, GitHub) | Recorded in my notes as Codex Plus and Claude Pro | **OWNER DATA REQUIRED** | Fixed | memory note only | No | Outside infrastructure, but part of the founder's burn |

Architecture check against the repository and MCPs: Next.js 16 frontend on Vercel (one project, production on `main`, no `vercel.json`, no project routes), Spring Boot backend in Docker on Render Singapore, PostgreSQL on Render, no Redis or cron services in Render, Cloudflare DNS not visible to me. Other integrations present in code: Resend, Xendit, OpenAI (default base URL), Google Cloud Vision.

## B2. Vercel and CDN (Investigation B)

- **Which categories:** over the 9 days available (Nov 8 08:00Z to Nov 17 08:00Z, effective cost, services listed by the API): Build CPU Minutes $2.18 (74% of usage), Deployment Storage $0.14, Fluid Provisioned Memory $0.13, Observability Events $0.08, Fluid Active CPU $0.06, ISR Writes $0.06, **CDN Requests $0.055 and Fast Data Transfer $0.024**, Function Storage $0.05, Fast Origin Transfer $0.04, ISR Reads $0.01; image optimization about $0.00. The API lists 50 of 77 service groups, so the remainder is small but not shown.
- **Builds are probably the driver (inference).** `list_deployments` since Nov 5 shows 100 deployments in 12 days: **82 preview, 6 production and 12 whose target could not be read**; the busiest day had 25 previews (Nov 12). The link from builds to the Build CPU line comes from these counts, not from any billing record. Each branch or release push builds a preview. Whether the project skips builds for docs-only or backend-only pushes (an "Ignored Build Step") is not readable from the MCP.
- **Googlebot and bots do not move the bill.** The window includes Nov 11 to 13, when Google made 2,019 / 2,423 / 3,995 requests a day; CDN and transfer lines stayed at cents. Unverified: bot traffic can still add to function and origin lines, but those total about $0.35 over the 9 days.
- **Dynamic note pages:** `cache-control: private, no-cache, no-store` and `x-vercel-cache: MISS` on the public note page (live check, 2026-11-15), so every crawl is a function render; function lines (memory, CPU, origin transfer) were about $0.24 in 9 days, so the cost is small but is the part that scales with bots.
- **Cost controls and flat-rate options:** I cannot see Spend Management or any alternative CDN billing mode through the MCP. Fluid compute is in use (the Fluid lines). **OWNER DATA REQUIRED:** the Spend Management setting and the plan-change date.
- **Unnecessary frontend to backend calls:** they cost Render CPU rather than Vercel dollars; the backend's CPU is idle (0.2 to 0.5% of one core), so there is no billing effect I can show.

## C. Infrastructure comparison

| Option | Evidence-based view | Monthly cost estimate | Risk and effort |
|---|---|---|---|
| **A. Retain and optimize Vercel** | Whole Vercel bill ≈ $30 a month ($20 seat + ≈ $10 usage), CDN ≈ $0.3. Controllable part: preview builds (≈ $7 a month at most). | Saves ≤ about $7 a month effective; cash effect unknown until the invoice shows whether usage is billed | Low risk (a Vercel setting, production builds unaffected if scoped to non-production); XS effort |
| **B. Cloudflare Workers** | Possible with an OpenNext-style adapter, but Next.js 16 server rendering, the dynamic note pages, `no-store` fetches, in-process caches (`lib/catalog-cache.ts`) and view events would all need re-verification. DNS is already on Cloudflare (DNS only). | **Not estimated:** no reliable Workers cost or traffic model. The ceiling on savings is the whole Vercel bill (≈ $30 a month). | High effort and risk to SEO, caching and privacy behaviour for at most ≈ $30 a month; payback **not calculated** (no reliable estimate) |
| **C. Self-hosted Next.js** (a VPS or a second Render service) | Adds deployment, TLS, monitoring, rollback and security work to one person; the backend already sits on Render. | **Not estimated** (needs Render or VPS pricing from the owner) | High; no saving shown |

Not recommended now: changing DNS proxy status (a Cloudflare proxy in front of Vercel would not cut the Vercel lines that matter, and it is outside this audit's authority).

## C2. Backend and database sizing (Investigation C)

- **Render backend:** CPU limit 1 core; **6-hour maximum CPU 0.002 to 0.005 (0.2 to 0.5%)** from Nov 9 to 16 (plus start-up spikes); memory about 740 to 905 MB of a 2 GB limit (35 to 45%). A previous tier (`plan-srv-006`) was replaced on 2026-07-04. The upgrade followed documented memory trouble on the 512 MB tier (the Dockerfile notes a 512 MB Starter where the JVM heap starved and the process hit out-of-memory under load), and the JVM runs with `MaxRAMPercentage=50`, so its memory grows with the tier and current use does not show the true minimum. Classification: **idle on CPU; memory need cannot be determined from available evidence**, so I do not recommend downsizing.
- **Database:** `basic_256mb` (0.1 CPU, 256 MB). Known kills and pressure (the Oct 21 to 28 series of 28 kills, the Nov 11 kill, database memory touching 99.1% on Nov 13 per the repo notes). The owner has ruled out a database upgrade (2026-11-02). Classification: **underprovisioned for its public-read load**, so any cost cut that touches it adds risk. The 15 GB disk against about 0.3 GB of data is a sunk, unshrinkable over-allocation (Render disks only grow).
- **Scheduled work:** 19 `@Scheduled` jobs, none heavy by cadence except the 2-second pool-saturation poll, a 5-second diagnostics flush and a once-a-minute goal-recovery poll; the hourly `CurationCostAlertJob` raises an in-app alert at $10 estimated Study Pack cost. None is shown to cost cash on its own.
- **Crawler and analytics load:** see the 2026-11-15 finding (heaviest queries: public-profile metrics, listings, view-count lookups). That is a capacity and stability matter, not a measured cash one.

## D. Feature unit economics (Investigation D)

Prices are the repository's constants per million input / output tokens: Sol $2 / $10, Luna $0.10 / $0.50, gpt-4.1-mini $0.40 / $1.60, gpt-4.1 $2 / $8. Costs below are **upper bounds**: they ignore the cached-input discount (cached tokens are a large share on Sol, for example 473k of 635k input in October).

| Workflow | Route and baseline configuration | Measurement available | Cost per operation (ESTIMATE) | Free vs paid and caps | Notes |
|---|---|---|---|---|---|
| Study Pack generation | `LLM_MODEL_FREE` (default Luna) for FREE; `LLM_MODEL_PREMIUM` (default Sol) for Plus, Pro and **every ADMIN**; production Render sets them explicitly. Data: Sol 20+163 packs in Oct and Nov; Luna 5; the older packs on gpt-4.1-mini | **Yes:** `study_packs` tokens, 2026-04 onward | gpt-4.1-mini ≈ $0.003 (3.5k in / 1.05k out); **Sol ≈ $0.0235** (3.9k / 1.6k); **Luna ≈ $0.0015** (4.5k / 2.1k) | Free 10 a month, Plus 50, Pro 100 | Sol is about 8x mini and 15x Luna per pack. Worst case at cap: Plus 50 packs ≈ $1.18, Pro 100 packs ≈ $2.35, Free 10 packs on Luna ≈ $0.015 |
| Challenge Quiz | Served from the per-user question bank; top-ups use `LLM_MODEL_INTERACTIVE_QUIZ` (default gpt-4.1-mini) | Partly: tokens only on a few top-up sessions (Aug 14 sessions: 41k in / 30k out) | ≈ $0.005 per measured top-up session | Free 20 a month, Plus 100, Pro 200 | Most sessions log no tokens because they read the bank |
| Quick Review | Reads the stored Study Pack quiz; study tip off by default (`QUICK_REVIEW_STUDY_TIP_ENABLED:false`, production value not read) | n/a | ≈ $0 | Unlimited | No LLM call per session |
| Adaptive Practice, Board and Long Exam, teacher quizzes | `INTERACTIVE_QUIZ` / `PAID_INTERACTIVE` routes (gpt-4.1-mini, gpt-4.1) | **No token logging** | **OWNER DATA REQUIRED** | Adaptive 3 / 10 / 30 a month; usage is tiny (2 to 8 sessions a month) | Board and Long Exam pools are seeded; `EXAM_POOL_PREWARM_ENABLED` defaults off (production value not read) |
| Ask Companion, Interview Practice, critique | `PAID_INTERACTIVE`, `CRITIQUE` (gpt-4.1, gpt-4.1-mini) | **No token logging** | **OWNER DATA REQUIRED** | Companion 0 / 20 / 20; Interview Pro only, 10 a month | Observed use: 3 companion generations in Sep |
| OCR | Google Cloud Vision when enabled | 2 extractions ever; 0 image-input packs; "OCR disabled" notices shown | ≈ $0 observed | Free 20 / Plus 50 / Pro 100 | Appears disabled |
| Note generation from topics, template seeding, summary regeneration, quiz repair | Learner-facing note generation has its own quota (10 / 25 / 100 a month); the others are owner and curation operations named in the code's own cost-alert scope note | **No token logging** | **OWNER DATA REQUIRED** | Quota as stated | Excluded from the built-in cost alert |
| Multi-note Challenge Quiz and statement-pair verifier | Multi-note: 2 / 10 / 200 a month, free source cap 3. Verifier switches default off (primary Sol, secondary Luna) | **No token logging** | **OWNER DATA REQUIRED** | Verifier off by default (production value not read) | |
| Exam pool and Challenge bank generation | Large seeded stores: 45,768 pool questions and 31,795 bank rows (99.9% of the bank is the owner account's) | **No token logging** | **Unmeasured historical spend** | n/a | The built-in cost alert covers Study Pack generation only, by its own wording |

Measured monthly Study Pack generation (upper bound, repo prices): Jun $1.37, Jul $0.94, Aug $2.16, **Sep $3.00, Oct $3.85**, Nov to date $0.46. **Owner account share:** Aug 653 of 664 packs, Sep 654 of 661, Oct 164 of 167. Learner packs: Jun 136, Jul 64, Aug 11, Sep 7, Oct 3, Nov 4.

Retry and failure exposure: the answer-key validator retries a flagged question once and omits it on a second failure (code, 2026-10-14 read), so failures add tokens; failed generation counts are not recorded as a cost. Migration economics (v0.182.0) are not modelled here; the baseline above is production as configured today.

## E. Financial sustainability assessment

| Item | What exists | Can it be calculated? |
|---|---|---|
| Learners | 447 non-admin (437 verified); 30-day activity: 25 with any recorded event, 13 with a quiz session | Yes (small) |
| Purchase model | `PREPAID`, `MONTHLY`, 30-day access, hosted checkout, manual renewal, so a renewal is a new purchase. No recurring revenue exists | Verified in `payment_transactions` and code |
| Gross collected | **₱149 lifetime** (one SUCCESS, 2026-05-01, Plus; list ₱179, voucher ₱30) | Yes |
| Failed payments | **17 FAILED rows, 14 distinct users** (May to Oct, Plus and Pro) against 1 SUCCESS. `FAILED` is also set for an invoice that expired or was replaced (`PaymentService.java:137-139`) and for a provider FAILED or EXPIRED webhook (`:271`, `:326`); **all 17 had a checkout URL and an expiry already past**, so most are likely abandoned or expired checkouts rather than declined cards, which this table cannot separate | Counts yes; reasons **OWNER DATA REQUIRED** (Xendit) |
| Refunds | A `REFUNDED` status exists in the code; **0 rows** use it | Zero recorded in the database; Xendit-side refunds **OWNER DATA REQUIRED** |
| Paying users now | **0** (the only active paid-plan subscription is the owner's admin grant, `provider NONE`; the one Xendit Plus expired 2026-06-01) | Yes |
| Current prices | Plus ₱99, Pro ₱149 monthly in the Philippines since 2026-11-13; Pro yearly and the exam pass hidden. No purchase recorded since | Yes |
| Conversion | 1 paid of 447 learners lifetime (0.2%); 1 of 18 checkout attempts succeeded | Yes (tiny n) |
| Variable cost per active user | Learner Study Pack cost ≈ $0.01 a month total (Oct, 3 packs); other workflows unmeasured | Partial |
| Net revenue, contribution margin | Needs Xendit fees and the OpenAI invoice | **No: OWNER DATA REQUIRED** |
| **Monthly cash burn** | Needs invoices for rows 3 to 10 and 13 | **No.** Only the Vercel seat ($20) and usage (≈ $10) are known |
| Break-even | Formula: fixed monthly cost ÷ net contribution per purchase. **Illustration only, not a finding:** at an assumed ₱58 per US dollar (replace with the owner's rate), one Plus pass at ₱99 is about $1.71 and a Pro pass at ₱149 about $2.57 **gross, before Xendit fees**, so each $100 of monthly fixed cost needs about **59 Plus passes or 39 Pro passes** a month; the known Vercel $20 to $30 alone needs roughly 12 to 18 Plus passes | Partly; the real figure needs all costs and fees |

## F. Prioritized opportunities

Ranked by estimated saving, effort, risk, user impact and confidence.

**P0: material leakage or production risk**
1. **Close the measurement gap on LLM spend.** The largest variable cost is currently invisible: only Study Packs and some Challenge top-ups log tokens, and Interview, Companion, Adaptive, exam pools and the question stores do not. First step is owner-side (an OpenAI usage export by day and model; section G); a later code item would log tokens on every route. No saving claimed; benefit is knowing the real bill. Risk none; confidence high.
2. **Do not cut the database or the backend tier to save money.** The database is already underprovisioned, and the backend's memory exceeds the next tier down. Recorded as a constraint, not a saving.

**P1: high-value, low-risk**
3. **Skip Vercel builds that cannot change the frontend** (docs and plans, backend-only branches) with an Ignored Build Step or path filter on preview builds. Estimated saving ≤ about $7 a month effective (Build CPU is 74% of usage); cash effect unknown until the invoice shows what is billed. Effort XS (owner Vercel setting); risk low if production deploys are excluded from the rule; no user impact; confidence medium. Cost dependency on the SEO plan: its work will add preview builds.
4. **Confirm the owner-only LLM flags are off in production** (`EXAM_POOL_PREWARM_ENABLED`, `QUICK_REVIEW_STUDY_TIP_ENABLED`, `OCR_PROVIDER`): a screen read of the Render environment by the owner, no change. Saving unknown, possibly meaningful if prewarm is on (the code comment calls it pure cost with no Pro users). Effort XS; risk none; confidence low until read.
5. **Decide whether owner curation should keep routing to Sol.** Every ADMIN generation uses `LLM_MODEL_PREMIUM` (about 8 to 15 times a Luna or mini pack, but only $2 to $4 a month measured). A product and quality decision, not a technical one; saving small today.

**P2: longer term**
6. **Email volume:** 1,100 to 1,700 inactivity emails a month to a base of 447 learners; only matters if Resend is on a paid plan (the 100-a-day config suggests a free tier). Product decision with the retention data; saving unknown.
7. **Render backend tier and JVM memory:** revisit only after the owner supplies the invoice and a stable database; high risk, saving unknown.
8. **Hosting migration:** no case on current evidence; revisit only if the Vercel invoice shows costs far above this window's.

## G. Owner data checklist (minimum, no credentials or card data)

1. **Vercel:** monthly invoices for the last 6 months; the Usage page screenshot (Build Minutes, Edge Requests, Fast Data Transfer, Function duration); the Spend Management setting; the date the team moved from Hobby to Pro, if it did.
2. **Render:** the last 6 invoices with line items (backend service, Postgres, disk) and the plan history.
3. **OpenAI:** usage export by day and model since March (and by API key or project if more than one key is used, including the owner's benchmark runs), plus the last 6 invoices.
4. **Resend:** current plan and the last 3 invoices or usage reports.
5. **Xendit:** a transaction report with fees for all attempts, failure reasons if shown, and any refunds.
6. **Cloudflare and domain:** plan and the domain renewal invoice.
7. **Google Cloud:** confirm Vision billing is unused or send the project's billing summary.
8. **Dev tooling subscriptions** (Codex, Claude, GitHub), if they count toward the burn the founder wants measured.
9. **A bank or card summary** of NoteLib-related monthly debits, to reconcile the above.
10. **Four environment values by screen read (values only, no secrets):** `OCR_PROVIDER`, `EXAM_POOL_PREWARM_ENABLED`, `QUICK_REVIEW_STUDY_TIP_ENABLED`, `LLM_MODEL_FREE` and `LLM_MODEL_PREMIUM`.

## H. Handoff to Feature Planner (verified findings only; no roadmap)

- **There is no recurring revenue to retain:** purchases are 30-day prepaid passes with manual renewal; one customer paid once (₱149, May) and lapsed on 2026-06-01. Any retention or renewal metric needs a purchase model decision first.
- **Checkout start-to-payment is the largest leak seen:** 18 checkouts created (14 distinct users in the 17 non-successful ones) and 1 paid across May to October. The `FAILED` status mixes abandoned or expired invoices with provider failures and every one had expired, so this is probably abandonment rather than declined cards, which Xendit data would confirm. No purchase has been recorded since the Nov 13 price change.
- **Cost to serve is small and fixed costs dominate:** learners generated 3 to 11 Study Packs a month since August; measured LLM cost is $1 to $4 a month, nearly all owner curation. Fixed hosting is the amount a revenue plan must cover.
- **Pricing margin watch:** at repository prices a Sol Study Pack is about $0.0235 (upper bound), so a Plus user at the 50-pack cap is about $1.18 and a Pro user at 100 packs about $2.35 of generation cost, against ₱99 and ₱149 (about $1.7 and $2.6 at an illustrative ₱58 per dollar, before payment fees). Typical users are far below the caps, but heavy Pro use on Sol is near break-even on generation alone.
- **Free-tier cost is negligible** (10 packs a month on Luna ≈ $0.015); free access is not what drives cost.
- **Most public traffic is not learners** (about 97% of web analytics visitors are automated), so traffic growth does not imply learner growth or cost growth; Vercel CDN spend is about cents.
- **Active base is small:** 447 learners, 25 with any event in 30 days, 13 with a quiz session; about 13% ever return (2026-10-28 read).
- **Unmeasured:** Interview, Companion, Adaptive, Board and Long Exam usage and cost, which are the paid-tier benefits in the pricing cards.

## I. What I could not verify

Render and database prices; the OpenAI, Resend, Xendit, Cloudflare and domain charges; Vercel billing before 2026-11-08 and what is actually invoiced versus covered by an allowance; whether Vercel skips builds for non-frontend pushes; Cloudflare configuration; the production values of OCR, prewarm and study-tip flags; per-workflow tokens for routes that do not log them; refunds.
