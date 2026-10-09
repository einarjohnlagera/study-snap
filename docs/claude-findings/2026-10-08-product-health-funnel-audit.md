# NoteLib Product Health, Funnel, Retention & Monetization Audit

**Date:** 2026-10-08 · **Branch at audit:** `releases/v0.167.0` (HEAD `b8623230`) · **Type:** investigation / decision support only. No code, pricing, entitlement, git or production changes were made.

**Sources synthesized** (session scratchpad, not committed): Audit A (entitlements, cost, checkout), Audit B (conversion surfaces), Audit C (infra and doc drift), and the Production DB dossier (read-only Render MCP `SELECT`s against `notelib-db-prod`, 2026-10-08). The synthesis pass added two direct code reads: `PaymentService.java:73` and `:338-345`, and `frontend/lib/pricing-config.ts:44-77`, plus one repo read of `docs/product/ROADMAP.md:710-711,822-823`.

**Four correction passes (same day).** The first caught a miscount (the "distinct checkout users" figure was taken from the `CHECKOUT_INITIATED` analytics event's distinct-user count, not from `payment_transactions`) and a retention proxy contradicted by its own data. The second caught that the correlation query from the first pass was partly circular (same-event double-counting), a paywall-trigger query with a silent `LIMIT 20` cutting off the tail, an off-by-one in the "same session" checkout count, an un-excluded owner account in two headline figures, and an unverified "hero CTA" claim. The third caught that "10 of 11 chose Plus" was presented as independent evidence when the dominant click source (the dashboard's ambient card) is hard-coded to offer only Plus, re-counted per-user instead of per-row (7 of 11 had ≤1 prior session, not "roughly half"), validated the retention/adoption finding against async-job contamination, and added a real, previously-missing usage/cost read (§17). **The fourth found that this usage/cost read had itself produced a contradiction** ("nobody approaches the quota" vs. "76 users hit it") — root-caused to a Study Pack quota-metering defect (free copies counted the same as paid generations), confirmed directly against production, which turned out to be the audit's single most concrete and actionable finding (§14, §17, §20 E4). **All numbers below reflect the fully corrected, re-run queries** (git log / Render event / Render deploy verification for §18). The original "10 users / 9 genuine" figure used earlier in this conversation was wrong — the correct figure is **13 distinct checkout users, 11 genuine external.**

**Labels used throughout:**
- **MEASURED**: read directly from code or production data.
- **DERIVED**: computed from measured facts.
- **INFERRED**: plausible but not proven.
- **UNKNOWN**: evidence is missing. This report does not fill those gaps.

---

## 1. Executive Diagnosis

**One-line read.** The funnel leaks heavily at two places we can measure:

- **Early return.** On a corrected, activity-based measure (any authenticated analytics event, not just the single `last_login_at` timestamp — see §7), about 10.7% of accounts were ever seen again more than a day after signup (DERIVED, right-censored). The simpler `last_login_at`-only proxy understates this (8.8%) and is independently shown to miss real return visits (§7).
- **Checkout completion.** 0 of **11 genuine** external checkout-starters (13 distinct users including 2 owner test accounts; 16 FAILED transactions total) completed a payment, and all 16 failed invoices ended `EXPIRED` (MEASURED).

We do not yet have the evidence to say that price is the binding constraint. Every number past "paywall viewed" is a small count (n ≤ 16), so it is directional at best.

| Constraint | Rank | Basis |
|---|---|---|
| **Checkout completion** | **CRITICAL** | MEASURED: 11 genuine users reached a Xendit invoice and 0 paid (13 distinct users total including 2 owner-test accounts; 16 FAILED `payment_transactions` rows). All 16 map 1:1 to Xendit `EXPIRED` webhooks. **Every one of the 11 genuine attempts was already at the discounted intro price (₱149 Plus / ₱199 Pro), not full price.** The population reaching checkout is mixed, counted per distinct genuine user at their first attempt: 7 of 11 had at most one completed session (4 with zero, 3 with one); the other 4 had meaningfully more (2, 3, 3, and 12). The joint condition "came via the Plus-only ambient card AND had ≤1 prior session" holds for 5 of 11 — real, but short of a majority, so this is borderline rather than clearly one story or the other. The card's button is **hard-coded to Plus only** (`free-plan-upgrade-card.tsx:23-27` — not a generic default), so "chose Plus" is partly a button-wiring artifact, not demonstrated proof these buyers didn't want Pro/Board Exam. See §11 for the full per-user breakdown. The pipeline works end to end: the owner's ₱149 test succeeded. The cause of abandonment past "didn't finish the Xendit page" is UNKNOWN. |
| **Retention / return** | **CRITICAL** | DERIVED, corrected methodology, owner accounts excluded: activity-based (any `analytics_events` row with `user_id` set) return holds for 43/417 (10.3%) after 1 day, 26/417 (6.2%) after 7 days, 14/417 (3.4%) after 30 days. This supersedes the `last_login_at`-only proxy, which is MEASURED to miss real returns: `last_login_at` is written at exactly one call site (`AuthService.java:893`, the login action) and does not update on token-refresh-sustained sessions — confirmed by a genuine checkout-abandoner whose second checkout attempt landed 30 days after their recorded `last_login_at`. Low in either reading. The prior H1+H5 retention experiment hit its kill criterion: 9 shown, 0 committed (`ROADMAP.md:917`). A properly time-bounded, non-circular correlation (§6, validated against async-event contamination) shows adopting a Study Plan in the first 24h associates with roughly 10x higher D7 return — directional only, since the non-adopter baseline is just 2 returners. |
| **Reliability (public SEO surfaces)** | **HIGH** | MEASURED: 24 `server_failed` restarts since 2026-09-01, the last one 2026-10-04T18:02 — **before** the `v0.165.0` fix deployed (Render `deploy_ended`, succeeded, 2026-10-05T15:21Z, confirmed via Render event log in this pass). **Zero `server_failed` events from 2026-10-05 15:21Z through 2026-10-08** (this pass's query), a genuinely positive interim signal, but only ~3 days against a 2026-10-12 checkpoint — too early to close. `v0.166.0` is confirmed **merged to `main`** (`git log main`: `db1c7c5d`) **and confirmed deployed and live**: Render's deploy list shows `dep-db32k8c9v7es73c8l5h0`, commit `db1c7c5d`, status `live`, finished 2026-10-07T11:19:29Z — an exact commit-to-deploy match, not an inferred one. **`ROADMAP.md:823`'s "not yet merged to main" is confirmed stale and should be corrected.** The `v0.166.0` "deploy + 7 days" checkpoint is therefore due **2026-10-14T11:19:29Z**. Business impact of the outages is still UNKNOWN. |
| **Instrumentation** | **HIGH** | MEASURED: there is no anonymous or session ID, so anonymous views cannot be joined to signups. There is no bot telemetry and no request logs. 109 of 420 accounts lack a `SIGNUP_COMPLETED` row. `analytics.md` documents 31 of 135 event types. Several of the questions this audit was asked cannot be answered at all. |
| **Paywall → upgrade intent** | **HIGH** | MEASURED and resolved (re-run without the silent `LIMIT 20` that truncated the first pass, owner excluded): **121** distinct paywall-viewers → **16** upgrade-clickers. **The single dominant trigger is the Study Pack generation quota near-limit warning** (`near_limit`/`study_pack_limit`, **77 distinct users**), not Board Exam Mode lock (**49** users, a strong but smaller second) — correcting an INFERRED claim in §10/§20 that assumed Board Exam was the dominant driver. **This is genuine quota exhaustion, not an early warning: all 76 distinct users who viewed the `near_limit`-tagged banner specifically had `remaining = 0` when it fired (§17; the 77-user family total also includes 1 user who hit only the separate hard `GENERATE_STUDY_PACK_LIMIT` modal) — a direct read of the live quota system, root-caused to free copies consuming the same meter as paid-for generation (§17).** The single largest source of actual upgrade *clicks* (**11 of 16**) is grouped under sources other than near-limit or board-exam — dominated by the passive `dashboard_free_plan_card`, shown on every Free dashboard load, not a specific feature lock. Rule 6 still applies: a paywall view or an ambient-card click is not purchase intent. |
| **Learning continuity** | **MEDIUM** | MEASURED in code: real singular "next step" surfaces exist (`PostSessionNextStep`, `ContinueSpotlight`, `DashboardStudyPlanSection`). However, the BOARD_EXAM branch (71% of profile-typed users) has no note-level resume card, and its default CTA points at a Pro-only mode (Audit B §5). |
| **Activation** | **MEDIUM** | MEASURED: onboarding completion is 269/420 (64%), confounded by the Public-Note copy path skipping onboarding by design (Audit B §2). No single "activated" definition exists in the product, but **the data now answers which first-day behavior correlates with return (§6, corrected for circularity): completing a session alone shows no D7-return lift (1.3-1.4%) over doing neither — only adopting a Study Plan in the first 24h does (≈14.6-14.8%, roughly 10x).** The old "create/generate first" activation definition is not the behavior that predicts return here; plan adoption is. |
| **Packaging** | **HIGH** | **MEASURED, and this is the most concrete, most fixable finding in the audit (§17):** the Study Pack quota counts free note copies the same as paid LLM generations (`StudyPackUsageService.java:24-29`). All 76 real users who hit the quota paywall did so via copying/adopting (avg 81.8 copies, 75 of 76 via a Study Plan adoption in the prior 30 days), not generation demand — contradicting the "consumption should be cheaper than creation" principle this audit tested (§13, §14, §19). It also collides directly with §6's strongest retention lead: adopting a plan is both what predicts return and what triggers this paywall. Fixing the meter (§20 E4) is cheap and should ship before prompting more adoption (§20 E2). Separately: Ask Companion has a zero-taste paywall at the twice-missed moment, and Board Exam (BOARD_EXAM's default, Pro-only) is a smaller paywall driver than the quota defect above. |
| **Signup conversion** | **UNKNOWN** | There is no visitor-level join. Aggregates exist (1,681 landing views, 913 `SIGNUP_STARTED`, 420 users), but human vs bot traffic is undecomposable. |
| **Acquisition quality** | **UNKNOWN (leaning MEDIUM)** | 56,645 anonymous `PUBLIC_NOTE_VIEWED` events, with no way to tell human from bot. Signups show a campaign spike-and-decay (Jun–Jul), then about 4/week. Signups concentrate in PH board-exam programs. |
| **Pricing (level)** | **LOW–UNKNOWN** | No observed event isolates price as the reason for non-payment (§12). |

---

## 2. Data Coverage

| Source | Period | Reliability | Limitations |
|---|---|---|---|
| `users` table (prod) | 2026-03-19 → 2026-10-08 | MEASURED, high | `last_login_at` is one rolling timestamp, not a login log. `referrer`/`utm_*` are captured at the signup moment only, so delayed or assisted discovery reads as "direct". `current_streak` reset semantics are unverified. |
| `analytics_events` (prod) | Per event. `PUBLIC_NOTE_VIEWED` from 2026-03-31; `EXPLORE_VIEWED` from 2026-07-31; `CHECKOUT_INITIATED` from 2026-06-24 | MEASURED counts; mixed completeness | No session or anonymous ID (`V25`, `V77`, `V137`). `PUBLIC_NOTE_VIEWED` is fired server-side on every fetch (`NoteService.java:1187`), so it includes bots. `trackEvent` swallows exceptions. Event types were added at different times, so windows differ. **Owner/admin accounts are excluded by email pattern in all headline counts in this report (121/16/43/26/14/77/49/etc.) — applied directly in this pass's queries, not left for the owner to redo.** |
| `payment_transactions`, `webhook_events`, `subscriptions` (prod) | 2026-05-01 → 2026-09-16 | MEASURED, high | **Effective price and voucher per FAILED row are now read directly (§11/§12)** — all 11 genuine attempts were at the intro-discounted price. It still holds no record of payment channel or on-page behaviour; that gap can only be closed from the Xendit dashboard (§23 #1). |
| Backend and frontend code | `releases/v0.167.0` | MEASURED | Prices and quotas are **env-var defaults**. Live Render env values were not read (Audit A §1). |
| Render events (via `docs/claude-findings/*` and `ROADMAP.md`) | 2026-09-01 → 2026-10-05 | MEASURED by prior sessions | No request, path or user-agent logs. Render log `type` only returns `app`/`build` (Audit C §2). |
| Vercel Web Analytics | Installed 2026-09-23, so at most ~2 weeks of data | **NOT ACCESSED** | There is no Vercel MCP in this session. Page views, referrers, devices and the human/bot split are **UNKNOWN in this report**. |
| Xendit dashboard | All time | **NOT ACCESSED** | The channel picked, attempts within an invoice, and time on the hosted page are all UNKNOWN. This is the single highest-value missing source for §11. |
| User feedback / interviews | n/a | none | Rule 8: absence of feedback is not satisfaction. |

**Prominent limitation (MEASURED, confirmed independently by Audit C §3 and the dossier):** there is **no way to join an anonymous Public Note or landing-page view to a later signup.** `analytics_events` has only `id, user_id, event_type, entity_id, metadata_json, created_at`, and `trackAnalyticsEvent` (`frontend/lib/api.ts:3498-3511`) sends no client ID. Every "Public Note → signup" or "Google → signup" statement in this report is an **aggregate or directional comparison, never a per-user funnel.**

---

## 3. Traffic Health

- **MEASURED:** 56,645 `PUBLIC_NOTE_VIEWED` events, all anonymous, 2026-03-31 → 2026-10-08. 1,681 `LANDING_PAGE_VIEWED` (1,623 anonymous). 302 `LANDING_CTA_CLICKED`. 242 `EXPLORE_VIEWED` since 2026-07-31.
- **Bot caveat (rules 2 and 3):** these are **requests, not humans.** `PUBLIC_NOTE_VIEWED` is emitted server-side on every backend fetch, including crawler and link-unfurl fetches and ISR revalidation paths that reach the backend. Nothing in the stack classifies user agents (Audit C §2). The restart investigations themselves found crawler-shaped bursts: for example, 1,587 views in the 30 minutes before the 2026-09-07 restart, and "572 views in the prior 30 min" on 2026-09-26. **The human share of the 56,645 is UNKNOWN.**
- **Search:** only 26 users carry `referrer = google` at signup. **This does not show that Google drives few signups**: the field records only the signup moment (§2). Organic search's real contribution is **UNKNOWN**.
- **Landing vs public notes (DERIVED, directional):** Public Note fetches outnumber landing views about 34:1. Even allowing heavy bot inflation, the SEO note pages are very likely the dominant *entry* surface, not the landing page. This is INFERRED: the ratio is measured, but the human mix is not.
- **Referral:** 71 "other" referrers and 2 `chatgpt.com`. Too sparse to segment.

---

## 4. Funnel

The strongest funnel the evidence supports is below. Steps are marked anon (anonymous) or auth (authenticated). **Anon → auth is not user-joinable**, so steps 1–3 cannot be divided into step 4.

| # | Stage | Count | Join level | Label |
|---|---|---|---|---|
| 1 | Public Note fetches | 56,645 events | anon, includes bots | MEASURED (requests) |
| 1b | Landing page views | 1,681 events | anon | MEASURED (requests) |
| 2 | `SIGNUP_STARTED` | 913 events (899 anon) | anon | MEASURED (events, not people) |
| — | **JOIN BREAK: no visitor ID** | — | — | — |
| 3 | Accounts created | 420 USER rows (`SIGNUP_COMPLETED`: 311) | auth | MEASURED. The 109-row gap is UNKNOWN; the event may predate some accounts |
| 4 | Email verified | 410 (97.6%) | auth | MEASURED |
| 5 | Onboarding completed | 269 (64%) | auth | MEASURED, **confounded** (copy-intent path skips onboarding by design) |
| 6 | **Activation (first learning action)** | **Partly MEASURED** | — | No single activation event exists, but §6 now measures first-24h plan adoption and session completion directly: 102 of 252 post-06-25 accounts adopted a plan in the first 24h |
| 7 | Returned >1d / >7d / >30d (activity-based, owner excluded) | 43 / 26 / 14 (of 417) | auth | DERIVED proxy, right-censored. Supersedes an earlier `last_login_at`-only read shown to undercount (§7) |
| 8 | `PAYWALL_VIEWED` | 121 distinct users | auth | MEASURED, owner excluded |
| 9 | `UPGRADE_CLICKED` | 16 distinct users | auth | MEASURED, owner excluded |
| 10 | Checkout started (real invoice) | 13 users / 17 transactions total (11 genuine external, 16 FAILED + 1 SUCCESS) | auth | MEASURED, corrected — this pass recounted `payment_transactions` directly (the earlier "10/9" figure wrongly reused `CHECKOUT_INITIATED`'s distinct-user count) |
| 11 | **Paid (external)** | **0** | auth | MEASURED |

**Missing stages, now partly filled:** first-24h plan adoption and session completion are measured directly in §6 (102 of 252 post-06-25 accounts adopted a plan in the first 24h; adopters return at ≈10x the rate of non-adopters — directional only, against a 2-returner non-adopter baseline). What remains unqueried is a full repeat-session cadence beyond the D1/D7/D30 snapshots in §7.

---

## 5. Segment Findings

- **Profile type (MEASURED, closes Audit B's open question):** BOARD_EXAM 197 (71.4% of those with a type set), STUDENT 71 (25.7%), PROFESSIONAL 5, TEACHER 3, NULL 144. The undated code comment at `app/onboarding/page.tsx:1176` (~71%/27%) is **confirmed current.**
- **Program concentration (MEASURED):**
  - Education (LET) 60, Architecture 43, Nursing 33, Accountancy 51.
  - "Professional / Board Exam Review" 17.
  - **Traction is PH professional board-exam review**, not generic students.
- **Organic search:** UNKNOWN (§3).
- **Public Note visitors:** a per-user segment is UNKNOWN because of the join break. In aggregate it is the largest entry surface (DERIVED, §3).
- **Landing page visitors:** 1,681 views; same join break.
- **Study Plan / Review Set adopters — now MEASURED, this pass's single most useful read:** adopters return at roughly 10x the rate of non-adopters (§6) — directional only, since the non-adopter baseline is just 2 returners. This was the report's biggest early gap; it is now the strongest lead in the dataset, not an open item.
- **Checkout-starters (DERIVED from user timestamps, corrected count — 11 genuine users, not 9):** **10 of the 11** genuine users' first checkout attempt landed within 24 hours of signup (most within minutes to hours of onboarding — "within 24h" per the raw interval, not necessarily the same calendar date); 1 waited 52 days before attempting. 2 of the 10 fast-attempters retried a second time weeks later (13 and 30 days after their first attempt) and let that invoice expire too. **Activity-based follow-up (this pass):** of the 11 genuine users, only 3 have any recorded `analytics_events` activity more than 1 day after signup — 8 show no activity at all beyond their first day.
- **Plan choice, with a caution (MEASURED + resolved, this pass):** 10 of the 11 genuine users' transactions were for Plus, 1 for Pro (the Pro buyer's last recorded upgrade-click source before checkout was `challenge_quiz_page`, both attempts; this only describes that one click, not every interaction they had). **This does not cleanly show "buyers didn't want Board Exam"**: 8 of the 11 users' last upgrade click before checkout traces to the dashboard's `FreePlanUpgradeCard`, whose button is **hard-coded to `planType="PLUS"`** (`frontend/app/dashboard/free-plan-upgrade-card.tsx:23-27`) — its own copy even says "go Pro for Board Exam Mode," but the button itself offers no Pro option. So "chose Plus" substantially reflects which button was clicked, not an independently demonstrated preference. See §11 for the full per-user forensic breakdown.

---

## 6. Activation

- **What "activation" means in the product today (MEASURED in code):**
  - There is no single defined activation event.
  - The flow treats completing onboarding (`ONBOARDING_V2_COMPLETED`) or the copy-intent landing in Quick Review on the copied note as the start of value (Audit B §2 step 5).
  - Onboarding Step 4 offers two equal doors (`app/onboarding/page.tsx:1674-1719`), "ready-made Official Review Set" first and "build from my own notes" second, so onboarding does **not** force note authoring.
- **What correlates with return — MEASURED in this pass, on a corrected, non-circular query.** A first attempt at this (dropped) defined "adopted"/"completed" with no time bound, which let the same late event (e.g. a plan adopted on day 10) count as both the defining behavior and the "returned after day 7" outcome — a built-in correlation, not a found one. The corrected version restricts the behavior to the **first 24 hours** after signup, measures "returned" strictly **after day 7** using a separate event, restricts the cohort to accounts created on/after 2026-06-25 (when `STUDY_PLAN_ADOPTED` instrumentation began) and old enough to have had 7 days to return, and excludes owner accounts:

  | Adopted a plan in first 24h? | Completed a session in first 24h? | n | Returned after D7 | Rate |
  |---|---|---|---|---|
  | No | No | 72 | 1 | 1.4% |
  | No | Yes | 78 | 1 | 1.3% |
  | Yes | No | 48 | 7 | 14.6% |
  | Yes | Yes | 54 | 8 | 14.8% |

  **Adopting a Study Plan within the first 24 hours associates with roughly 10x higher D7 return** (≈14.6-14.8% vs ≈1.3-1.4%) **than not adopting one — and whether a session was also completed that day barely moves the number** (14.6% vs 14.8%) once adoption is accounted for. **⚠️ The non-adopter baseline is only 2 returners (1 of 72, 1 of 78) — a count this small can swing the ratio sharply on one or two more/fewer returns, so treat "10x" as directional, not a precise multiplier.** **Validity check (this pass):** the "returned" signal was re-measured excluding every event type that could plausibly fire from an async job rather than a real visit (`PUBLIC_NOTE_COPIED`/`SHARED_NOTE_COPIED` — plausibly reachable from an async plan-update-sync path in `NoteCollectionService.java:2690` (the method's shape matches a sync job; this session saw the code, not a runtime trace proving it executes asynchronously) — plus `STUDY_PLAN_ADOPTED`, `STUDY_PACK_GENERATED`, collection and email events); **the counts were identical**, so the returning users have other, unambiguous real actions (logins, quiz starts, paywall views) backing them. **Rule 1 still applies strictly** — this is correlational. Users who adopt a plan may simply be more engaged to begin with; this does not show that *prompting* adoption would cause the same return rate. It is still the single most useful, honestly-measured lead in this dataset for where to invest in continuity (§10, §20).
- **Audit B's top-ranked finding, reconciled.** Audit B ranked the `/verify-email` wall (`lib/auth.ts:207-215`) as the #1 leak. Production shows 410 of 420 accounts (97.6%) are verified, so **it is not a measured leak among accounts that exist.** It could still deter people before they create an account, which is not measurable (§2). It is therefore not ranked here.
- **Confound to state:** the 144 NULL-`profile_type` accounts mix true drop-offs with copy-intent lightweight completions (`setPendingLightweightProfileCompletion`, `lib/auth.ts:178-193`). Treat "36% never onboarded" as a mix of two populations whose sizes are UNKNOWN, not as pure drop-off.

---

## 7. Retention

- **Corrected, activity-based measure (this pass):** instead of the single `last_login_at` timestamp, return is measured as "any `analytics_events` row with a non-null `user_id`, dated after `created_at` + N days." Of 420 accounts: **10.7% (45) show activity after 1 day, 6.7% (28) after 7 days, 3.8% (16) after 30 days.**
- **Why the switch was necessary (MEASURED, not a footnote):** the original `last_login_at`-only read (8.8% / 6.2% / 3.6%) was contradicted by its own data — one genuine checkout-abandoner's second checkout attempt (a real authenticated action) landed 30 days after their recorded `last_login_at`. Grepping the codebase confirms `last_login_at` is written at exactly one call site, the login action (`AuthService.java:893`); it is not a general activity timestamp and plausibly misses sessions sustained by refresh tokens without a fresh login. The activity-based measure is strictly ≥ the `last_login_at`-based one (45≥37, 28≥26, 16≥15) and is the one to use going forward.
- **Caveats that still apply:**
  1. This is still "ever seen after N days, as of today," not a true D1/D7/D30 cohort curve with a fixed horizon per cohort.
  2. **Right-censoring:** accounts created within the last 1, 7 or 30 days cannot qualify yet. With about 4 signups a week recently, this understates the rates only slightly, because the bulk of accounts are from Jun–Jul.
  3. `current_streak > 0` for 57% is **not usable** until the reset semantics are verified.
- **Strongest observed pattern (DERIVED):** the cohort is dominated by the Jun–Jul spike: 80, 44, 44 and 74 signups in peak weeks. Those accounts largely did not persist. This is consistent with campaign-acquired users trying the product once (INFERRED; there is no campaign attribution data).
- **Prior evidence:** the commitment-device retention experiment H1+H5 (`v0.72.0`) fired its kill criterion. 9 learners were shown it and **0 committed** (`ROADMAP.md:917`). The next retention lever should therefore not be another "commit to return" prompt.

---

## 8. Public Note → Learning Journey

This summarizes Audit B §2–§3. Every item is MEASURED in code.

**What works:**
- The copy-intent pipeline is well engineered. The cookie is set before signup (`public-seo-copy-cta.tsx:171`) and survives `/verify-email`. Onboarding is skipped through lightweight completion, and the user lands in Quick Review on the exact note (`app/verify-email/page.tsx:56-79`).
- Google OAuth skips verification altogether (`AuthService.java:1093`).

**Gaps:**
1. **No note → plan link.** A public note never says "this belongs to Official Review Set X". It only shows "more notes like this" rails (`page.tsx:345-459`). The "learning journey is the product" story is invisible at the largest entry surface.
2. **30-minute intent TTL.** `COPY_INTENT_COOKIE_MAX_AGE_SECONDS = 1800` (`lib/public-note-copy.ts:3`). A slow email round trip loses the intent silently.
3. **Flashcards promise not honoured.** The redirect hard-codes `quick-review` (`verify-email/page.tsx:72`), even when the visitor clicked "continue with the full deck".
4. **Cross-device verification** (signing up on desktop and clicking the link on a phone) loses the cookie. Whether it happens is UNKNOWN; there is no server-side fallback.
5. **Explore-adopt asymmetry.** The same 30-minute TTL (`lib/discovery-intent.ts:2`) must survive verification **plus full 8-step onboarding**. If it expires, the user lands on a dashboard with no adopted plan and no explanation.

**Not measurable:** how often each gap fires. No event records an expired intent.

---

## 9. Landing Page

From Audit B §1, MEASURED:

- **Positioning mismatch:**
  - The visible H1 is "Build your notes library and turn notes into quizzes" (`app/page.tsx:182-186`): a notes-first, generator framing.
  - The OG/meta text uses "your notes become your study system" (`:51`).
  - The newer "always know what to learn next" journey positioning appears nowhere in the rendered body.
- **Product mismatch:** zero mentions of Study Plans, Review Sets, Official journeys, Companion, or readiness in the landing body. Yet these are the product's first-listed onboarding door, its post-session recommendation engine, and the content behind the 71% board-exam majority.
- **Persona mismatch (DERIVED):** the default tab is "Students" (`profile-learning-section.tsx:182`), while 71% of typed users are board-exam reviewers. The only body path to Exam Hubs is the non-default "Exam Reviewers" tab.
- **Mitigation:** the navbar links `/explore` and Exam Hubs on every page (`navbar.tsx:11-23`). The precise claim is "undersold in body copy", not "unreachable".
- **Caveat:** the landing page is likely not the main entry surface (§3), so landing-copy fixes have a bounded upside. That is why no landing redesign appears in §20.

---

## 10. Learning Continuity

From Audit B §5, MEASURED:

- **STUDENT / PROFESSIONAL:** three real, singular recommendation surfaces exist (`DashboardPrimaryCollectionHero`/`ContinueSpotlight`, `DashboardStudyPlanSection`, `PostSessionNextStep`). However, up to about 6 independently conditional cards can stack above the resume card, among them `FreePlanUpgradeCard` (shown on every Free dashboard load, `dashboard/page.tsx:726`), `NearLimitBanner`, and prompts.
- **BOARD_EXAM (the majority):**
  - There is no note-level "Continue Studying" card.
  - The primary CTA, "Start Board Exam" (`:960-969`), pre-selects Board Exam Mode, which is Pro-only. Free users reach an honestly labelled "Unlock Board Exam - Pro" button (`challenge-quiz/page.tsx:2395-2397`) and need "Choose another mode" to reach free Challenge Quiz.
  - This is transparent friction, not a trap. But **the majority persona's default path ends at a paywall unless they take a deliberate extra step.**
- **Pattern behind the funnel — RESOLVED this pass, and it corrects an assumption.** The `PAYWALL_VIEWED`/`UPGRADE_CLICKED` `metadata_json` was read directly (grouped by `source`/`feature`/`paywallType`, excluding owner accounts, re-run without a `LIMIT` that silently truncated an earlier pass). **The dominant paywall trigger is the Study Pack generation quota near-limit warning** (`near_limit`/`study_pack_limit`, **77 distinct users**), with **Board Exam Mode lock a strong second** (**49 distinct users** via `challenge_quiz_page`/`BOARD_EXAM_MODE_LOCKED`). The board-exam-steering chain in the paragraph above is real but is **not** the single largest path into the paywall — running out of Study Pack generations is. Separately, the single largest source of actual *upgrade clicks* (**11 of 16**) is a residual "other" bucket dominated by the passive `dashboard_free_plan_card`, shown on every Free dashboard load, not a feature-specific lock at all — though that card's button is hard-coded to Plus (§5), so this partly reflects button wiring rather than independently measured intent. The real-money picture is more mixed than either framing suggests: see §11's per-transaction breakdown.

---

## 11. Monetization Funnel

**MEASURED, authenticated, user-joinable:**

```
PAYWALL_VIEWED       121 distinct users, owner excluded   2026-04-01 → 2026-10-08
UPGRADE_CLICKED       16 distinct users, owner excluded   2026-04-01 → 2026-10-06
Checkout started      13 users / 17 payment_transactions (11 genuine external, 2 owner test)
Paid (external)        0
Paid (owner test)      1   ₱149 PLUS MONTHLY (INTRO voucher), 2026-05-01, lapsed 2026-06-01
```

**Correction to an earlier count:** this report, and the conversation that produced it, initially said "10 users / 9 genuine." That number was taken from the `CHECKOUT_INITIATED` analytics event's distinct-user count (10), not from `payment_transactions` directly. Recounting `payment_transactions` by `user_id` gives **13 distinct users (11 genuine external, 2 owner test accounts), 16 FAILED + 1 SUCCESS = 17 rows.** All figures below use the corrected count.

All rates past "paywall viewed" are small counts (n < 30). Do not read them as conversion rates.

### Checkout forensics (front and centre)

**MEASURED:**
- **16 FAILED transactions** belong to 13 distinct users, **11 of them genuine external users** (one `.edu.ph` address, several personal Gmail addresses; 2 of the 13 are the owner's own test accounts), spanning 2026-05-02 → 2026-09-16, across PLUS and PRO. Two genuine users (one Plus, one Pro-then-Plus) each attempted twice.
- **All 16 map 1:1 to Xendit `EXPIRED` webhooks.** The only `PAID` webhooks (2) are the owner's single success, delivered twice and deduped.
- **The invoice lifetime is 24 hours:** `INVOICE_DURATION_SECONDS = 86_400` (`PaymentService.java:73`, sent as `invoice_duration` at `:342`). So `EXPIRED` means **no payment completed within 24h of the invoice being created.**
- **No `payment_methods` restriction is sent** (`PaymentService.java:338-345`). The hosted page shows whatever channels are enabled on the Xendit account. Which channels are enabled is UNKNOWN.
- **The pipeline is proven:** a payment that completes activates correctly (owner test; webhook idempotency in `WebhookEventService.reserveEvent`).
- **Every single genuine attempt was already at the discounted intro price, not full price (MEASURED, this pass, `payment_transactions.amount`/`voucher_id`):** all 11 genuine users' invoices show `amount = ₱149` (Plus, discounted from ₱179) or `amount = ₱199` (Pro, discounted from ₱249) via the live `INTRO-PH-*` vouchers (`V52` migration). **Nobody who reached checkout was asked to pay full price, and 100% still abandoned.** This is new evidence not in the original dossier and it matters directly for §12.
- **Per-transaction forensics (this pass, joining each of the 13 genuine FAILED rows against the user's own prior events):**

  | Txn date | Last `UPGRADE_CLICKED` source before checkout | Prior completed sessions | Saw quota paywall first |
  |---|---|---|---|
  | 05-29 | `dashboard_free_plan_card` | 0 | — |
  | 06-09 | `challenge_quiz_page` | 3 | no |
  | 06-20 | *(none recorded)* | 1 | — |
  | 06-21 | `dashboard_free_plan_card` | 12 | — |
  | 06-29 | `dashboard_free_plan_card` | 1 | — |
  | 07-02 (am) | `dashboard_free_plan_card` | 2 | — |
  | 07-02 (pm) | `private_note_detail` | 1 | no |
  | 07-03 | *(none recorded)* | 1 | — |
  | 07-10 | `challenge_quiz_page` | 6 | no |
  | 07-18 | `dashboard_free_plan_card` | 3 | yes |
  | 08-08 | `dashboard_free_plan_card` | 0 | yes |
  | 09-07 | `dashboard_free_plan_card` | 0 | yes |
  | 09-16 | `dashboard_free_plan_card` | 0 | — |

  **This is a genuinely mixed population, not a clean story in either direction. Counted per distinct genuine user at their first attempt (not per row, which double-counts the 2 repeat-attempters): 7 of 11 had at most one completed session before checkout (4 with zero, 3 with one); the other 4 had meaningfully more (2, 3, 3, and 12).** The joint condition "came via the Plus-only ambient card AND had ≤1 prior session" holds for 5 of 11 — real, but short of a majority. **This tilts toward low engagement rather than splitting evenly**, though it is not uniform either way. Neither "these are all serious buyers blocked only by Xendit" nor "these are all idle curiosity clicks" is supported — both the CRITICAL ranking (§1) and E1 (§20) should be read with this mix in mind, not as proof of strong uniform purchase intent.
- **All 17 transactions ever created (including the owner's) used `MONTHLY` billing — zero used `EXAM_CYCLE`.** Pro's `EXAM_CYCLE` (₱599/90 days) is a live, code-confirmed primary CTA on the `/pricing` page when available (`pricing-plans-section.tsx:144-212`), shipped 2026-06-09 (`f963c424`) — roughly 4 of these 7 months, so "it just hasn't shipped long enough" is not the explanation. **The two click-source rows with no recorded `UPGRADE_CLICKED` event (§11's table) mean this session cannot say with certainty that literally none of the 13 transactions touched `/pricing`.** What IS fully measured: `EXAM_CYCLE` is Pro-only by design (`f963c424`'s own commit message: "keeping Plus and non-PH cycles inactive"), and 10 of the 11 genuine users bought **Plus**, which was never offered an exam-cycle option at all. Separately, 8 of the 11 users' last click traces to the dashboard's ambient card, which is hard-coded to Plus and cannot offer Pro or the exam pass in the first place (§5). **The cleanest, fully-measured version of this finding: the most-clicked upgrade surface in the product cannot sell Pro or the ₱599 exam pass at all — not that users saw the exam pass and chose Monthly instead.**
- **Timing (DERIVED, recomputed on the corrected 11 genuine users):** 10 of 11 attempted their first checkout within 24 hours of signup (most within minutes to hours of onboarding); 1 waited 52 days; the 2 repeat-attempters tried again 13 and 30 days after their first attempt. **Activity-based follow-up:** only 3 of the 11 genuine users show any recorded activity more than 1 day after signup — 8 show none at all.

**What `EXPIRED` does and does not tell us:**
- It is MEASURED that nobody paid within 24h.
- It **cannot separate** these causes:
  - (a) the user abandoned without trying;
  - (b) friction choosing a channel (GCash, Maya, card, OTC);
  - (c) a failed attempt *inside* the invoice;
  - (d) second thoughts after seeing the hosted page;
  - (e) distrust of an unfamiliar payment page.
- **The "no declined payment" reading is weaker than it looks** (INFERRED, needs confirming against Xendit docs or dashboard). With Xendit hosted invoices, a declined card or a failed e-wallet attempt typically leaves the invoice open for retry and does not emit an invoice-level FAILED webhook. The invoice then ends `EXPIRED`. **Zero decline webhooks is therefore partly an artifact of the instrument**, not proof that no attempt failed. Only the Xendit dashboard can tell.

**Secondary instrumentation finding (DERIVED):**
- `CHECKOUT_INITIATED` (11 events) undercounts transactions (17). Its first event is 2026-06-24, while failed transactions start on 2026-05-02. Since the event fires on both the fresh and the reuse path (`PaymentService.java:126,186`), the gap is most likely **the event shipping later than checkout**, not a logic defect.
- **Abandoned checkout is invisible to the product.** No recovery or reminder email exists. Whether a "payment didn't complete" state is shown on `failure_redirect_url` is UNKNOWN. A stale pending row is only marked FAILED reactively, the next time the same user starts checkout (Audit A §5).

**Interpretation (INFERRED, the strongest the evidence allows):** real users formed intent fast enough to open a payment page at the price shown in NoteLib. Every one of them then failed to complete within 24 hours. **The measured leak is at the payment page, not at the price decision**, though the mechanism is UNKNOWN.

---

## 12. Pricing Assessment

**Current price surface:**
- Code defaults (MEASURED): Plus ₱179/30d; Pro ₱249/30d, ₱1,999/yr, and **₱599/90d (the hero CTA, `docs/features/pricing.md:68,109`)**; intro Plus ₱149 / Pro ₱199 (`pricing-config.ts:68-72`).
- **Live production values are UNKNOWN**: env overrides were not read (deliberately — they may hold secrets alongside prices). **Which price each of the 11 genuine users actually faced is now MEASURED** (this pass, §11): all 11 saw the discounted intro price (₱149 Plus / ₱199 Pro), not full price.

**Does the evidence support testing ₱79 / ₱149? No, not yet — and this pass found evidence that makes the case stronger, not weaker.**

1. **Every genuine checkout attempt was already at the discounted intro price, and still failed (MEASURED, this pass).** All 11 genuine users' invoices show the live `INTRO-PH-*` price — ₱149 for Plus (not ₱179) or ₱199 for Pro (not ₱249) — and 100% still ended `EXPIRED`. This is stronger than "no observed price rejection" (the original framing): **the evidence directly shows that a ~17-20% discount off current pricing did not convert a single genuine user.** ₱79/₱149 would be a further 47%/25% cut below a discount that already didn't work.
2. **No observed price rejection, independent of point 1.** Every genuine user who reached the money step did so *after* seeing a NoteLib price, then lost the invoice to expiry, not to a recorded "no". This does not prove price is fine (rule 7; reason (d) in §11 is open). It does mean the data contains **no event that isolates price.**
3. **A lower price does not fix a payment page nobody finishes.** If the cause is channel friction, trust, or distraction, ₱79 expires exactly like ₱149 did. A price test now would be confounded by the checkout leak, so its result would be uninterpretable.
4. **The n is too small to read a price test.** About 2-3 checkout-starters a month recently, and about 4 signups a week. A 2-arm price test would need many months to show anything, and would mostly measure noise.
5. **Rule 12:** there is no repeatable paid conversion yet, so optimizing price per payer is premature.
6. **Rule 9:** competitor prices do not establish NoteLib's price.

**What would change this:** suppose checkout completion gets fixed (≥1 genuine completion) and paywall → upgrade stays near 13% (16/121), but surveys or interviews with the 11 users name price as the reason. Then a price test becomes justified.

---

## 13. Entitlement Cost Map

**This A–E scale was defined for this synthesis; it is not an established repo taxonomy.** Audit A used A, C and D; B and E were added here. A and D overlap (both are effectively free); D marks "render/export, no LLM at all".

- **A** = zero or near-zero marginal cost (reuse or read)
- **B** = storage or DB only
- **C** = bounded per-use LLM cost
- **D** = no LLM (render or export)
- **E** = unbounded or uncapped LLM exposure

Quotas are code defaults (`StudySnapProperties.java:124-327`; MEASURED defaults, live values UNKNOWN).

| Capability | Free | Plus | Pro | Class | Note |
|---|---|---|---|---|---|
| Quick Review | per pack | per pack | per pack | **A** | Reuses the Study Pack quiz, no LLM |
| Reading Official / public notes, adopting a copy | **∞ (copying itself is not capped)** | ∞ | ∞ | **B** | Copy is storage, not LLM, and copying is genuinely unlimited — **the bug is downstream**: each copy also inserts a `study_packs` row counted against the *Study Pack generation* quota (`StudyPackUsageService.java:24-29`), so a Free user who copies/adopts heavily finds their **own future generation blocked** for the month (all 76 near-limit-paywall viewers got there this way, §14/§17/§20 E4), even though the copying itself never hit a wall. |
| Static Companion guide (adopted) | ∞ | ∞ | ∞ | **A** | Generated once by an admin (`companion.md:5`) |
| DOCX / PDF export | 2/2 | 15/15 | ∞ | **D** | Reads stored quiz JSON |
| Quiz share links | 3 | 10 | ∞ | **B/D** | Scoring only, no LLM (`QuizShareController.java:83-89`) |
| Challenge Quiz | 20 | 100 | 200 | **C→A** | Per-user bank + Official template copy; LLM only for the shortfall |
| Board Exam Mode | 0 | 0 | 10 | **C** | Partly bank/template-shielded; Review-Set path skips the warm pool |
| Multi-note Challenge/Board | 2 | 10 | 200* | **C** | *Pro 200 was never a chosen entitlement (`PLANS.md:75`) |
| Study Pack gen/regen | 10 | 50 | 100 | **C** | Paid plans use the **premium** model (`StudyPackService.java:721-723`) |
| AI note generation | 10 | 25 | 100 | **C** | |
| OCR uploads | 20 | 50 | 100 | **C** | Per-call external cost |
| Interview Practice | 0 | 0 | 10 | **C** | Critique tier (cheap), cached per answer |
| Ask Companion | **0** | 20 | 20 | **C** | Critique tier, ≤6 turns/session |
| **Adaptive Practice** | 3 | 10 | 30 | **C, E-risk** | **Always fresh, no reuse** (`challenge-quiz.md:119`) |
| **Long Exam** | 0 | 0 | 12 | **C, E-risk** | Parallel fan-out, premium model, no reuse (`LongExamService.java:154-155`) |

Nothing is class E *today*, because every LLM mode is quota-capped. Adaptive Practice and Long Exam are the two that become E the moment a cap is removed.

---

## 14. "Unlimited" Risk

**Rule 11: "Unlimited" must never mean unbounded LLM spend.**

**Could safely become generous or unlimited (MEASURED mechanisms exist):**
- Quick Review, reading or adopting Official/public content, the static Companion guide, exports, and share links: classes A, B and D.
- **Challenge Quiz on Official content.** `OfficialChallengeQuizTemplateService.copyTemplateQuestions` (`:195-268`) gives adopters template questions with zero LLM calls. Marginal cost trends to zero once a note's bank or template fills. A cap on the *shortfall LLM path* would still be needed.
- **Board Exam on Official content**, partly, through the same bank. The Review-Set path skips the warm pool, so it is less shielded.

**Must stay capped (MEASURED, no reuse mechanism exists):**
- **Adaptive Practice.** Every session is a fresh generation.
- **Long Exam.** Fan-out, premium model, fresh every session.
- Also Study Pack generation (premium model on paid plans), AI note generation, OCR, and Ask Companion turns.

**Control gap (MEASURED by grep, Audit A §4):**
- There is **no global spend ceiling, no per-user $ cap, and no cost circuit-breaker** anywhere.
- The only throughput guard is the in-memory, per-process `AiRateLimitService` (5/min Free, 20/min paid), which resets on restart. Restarts happened 24 times in five weeks (§18).
- Any "Unlimited" tier shipped today would rely on monthly quotas alone.

**⚠️ The architecture does not yet cleanly separate "consumption" from "generation" even where it intends to (§17, fully MEASURED against production, not merely theorized).** `StudyPackUsageService.resolveUsage` (`StudyPackUsageService.java:24-29`) counts a monthly user's Study Pack quota as `max(generation counter, COUNT(*) of study_packs rows created in the period)` — and copying an Official/public note inserts a `study_packs` row, so **a free copy spends the same meter as a paid-for generation.** Confirmed directly: all 76 real users who hit `remaining = 0` had copied notes (avg 81.8) in the preceding 30 days, and 75 of 76 had adopted a Study Plan in that same window — adoption, which copies every note in the plan at once, is what drains the quota, not generation demand (lifetime original-generation volume among non-owner users is only 227 rows). **Before any "generous for consumption, capped for generation" packaging change ships, this specific meter needs to stop counting copies.** It is the one place in the codebase where this report found the intended A/C cost-class separation (§13) silently collapsed into one counter.

**Conclusion:** an "Unlimited" tier is feasible only as **"unlimited consumption of existing/Official content + capped generation"**, and only after (a) a global spend breaker exists and (b) the Study Pack quota counter above is fixed to stop charging copies. Unlimited Adaptive Practice or Long Exam should not ship at any price.

---

## 15. Lifetime Assessment

- **MEASURED:** no `LIFETIME` plan type exists (`PlanType`: `FREE, PLUS, PRO`). However, an `ACTIVE` subscription with `end_at = NULL` already counts as perpetually paid (`BILLING_ADDENDUM.md:81`; `billing.md:71`), so lifetime needs **no schema change.** This makes it easy to grant, and so easy to leak.
- **MEASURED:** `BillingUsageResetJob` refills the full quota table every month, regardless of tenure or payment history.
- **DERIVED risk:** a lifetime Pro holder gets 30 Adaptive Practice and 12 Long Exam sessions a month, both premium or unshielded, indefinitely. These two modes have no "runs out of new content" ceiling, because they regenerate against the same notes.
- **Cost per call:** UNKNOWN (§17). The *shape* is unbounded in time, which is the problem regardless of the number.
- **Assessment:** do not sell lifetime now. If it is ever offered, it needs:
  - (a) a global spend breaker;
  - (b) Adaptive Practice and Long Exam excluded or separately capped for lifetime rows;
  - (c) a deliberate price model.

  None of these exists, and there is no evidence of demand for lifetime.

---

## 16. Free Plan Assessment

**Does Free create a habit before monetization? No evidence that it does, and some evidence that monetization arrives first.**

- **DERIVED:** retention is low (§7), so whatever habit Free builds, few users stay long enough to form it.
- **MEASURED / DERIVED:**
  - 121 of 417 non-owner users (29%) saw a paywall (MEASURED).
  - 10 of 11 genuine checkout-starters' first attempt landed within 24 hours of signup (DERIVED from timestamps, §11); 8 of the 11 show no recorded activity beyond their first day. Per-user, 7 of 11 had at most one completed session before checkout (§11) — this tilts toward low engagement, though 4 of 11 had meaningfully more usage first, so it is not a uniform "impulse checkout" story either.
  - Monetization pressure therefore reaches many users before any return visit.
  - `FreePlanUpgradeCard` renders on every Free dashboard load (`dashboard/page.tsx:726`).
- **MEASURED quota generosity, and a packaging defect, not just a generosity question:** the code-default caps look generous (10 Study Packs, 20 Challenge Quizzes, Quick Review ungated, Adaptive Practice gives a real taste of 3). But **§17 found that all 76 real users who viewed the Study Pack near-limit banner had `remaining = 0`, and the root cause is that `StudyPackUsageService` counts free note copies against the same monthly quota as fresh LLM generations** (`StudyPackUsageService.java:24-29`). It is not that the cap is too stingy or that generation demand is high (lifetime original-generation volume is only 227 rows across all non-owner users) — it is that **copying, a zero-cost action, consumes the same meter as generating, an LLM-cost action.** An earlier draft of this section concluded "quotas are generous enough that limits are not the problem," reasoning from generation-event counts alone; that reasoning is retracted, though the net effect — Free users hitting a wall before finding a reason to return — may be similar in practice to what that draft described, just for a different, fixable reason.
  - Ask Companion has **zero taste** and is surfaced exactly when a learner misses a concept twice (`twice-missed-ask-companion-card.tsx:26-60`). The copy says "Ask about this Review Set" even to Free users with no Review Set context.
  - For BOARD_EXAM users, the default mode is Pro-only, though Board Exam lock is a smaller paywall trigger than Study Pack near-limit (§10).
- **INFERRED, revised:** the paywall most Free users actually meet is triggered by copying Official/public content, not by generation demand or Board Exam access. This reframes the product question: it's not "is Free too generous" but "is the Study Pack quota metering the wrong action." See §14, §19, §20's replacement for E4.

---

## 17. Unit Economics

- **Revenue:** ₱0 from external users (MEASURED).
- **Usage quantities, now MEASURED directly (this pass queried `study_packs`, which stores `input_tokens`/`output_tokens`/`cached_input_tokens`/`estimated_cost` per row):**
  - **`estimated_cost` is a real column but is never actually populated** — every row, across all tiers and roles, is NULL. The schema anticipated this measurement; the write path doesn't do it. This is the single missing billing input, not a peso figure this session can estimate — it needs either backfilling the write path or the raw OpenAI usage dashboard (not accessible from this session).
  - **Token averages, restricted to original (non-copied) notes, owner excluded, to avoid mixing in duplicated token values from free copies:** 226 original rows used the cheaper model tier, averaging ~2,820 input / ~877 output tokens; only **1** original row among non-owner accounts used the premium model tier (too small a sample to average meaningfully — the ADMIN account separately holds ~2,000 premium-tier rows, almost all bulk curation, peaking at **1,140 `STUDY_PACK_GENERATED` events in a single month**, confirming admin activity must be excluded before any count means anything about real users). `estimated_cost` was NULL on every one of these rows too.
  - **⚠️ `study_packs` row counts are dominated by free copies — resolved, not left as a contradiction.** Of 9,804 owner-excluded USER-owned rows, only 227 (2.3%) are original generations; the rest (9,577, 97.7%) have `copied_from_note_id IS NOT NULL`. Tracing the single heaviest month (571 rows, one user) confirmed **all 571 are copies** — zero fresh LLM calls, consistent with Audit A's classification of copying as free. **Row counts measure library size, not LLM call volume.**
  - **⚠️ Root cause found for why 76 real users hit `remaining = 0` on a "generous" 10/month cap, and it is a genuine, fully MEASURED packaging defect, not a measurement illusion.** `STUDY_PACK_GENERATED` analytics events peak at only 6/month per real user (p95 of 2) — generation alone cannot explain 76 users hitting zero on a 10 cap, and lifetime original-generation volume (227 rows total, above) rules out "the event count undercounts generations" as the explanation. The real cause is in the quota math itself: **`StudyPackUsageService.resolveUsage` (`StudyPackUsageService.java:24-29`) computes monthly usage as `max(tracked generation counter, COUNT(*) FROM study_packs created in the period)` — and every note copy, including a free copy of an Official/public note, inserts a new `study_packs` row** (`NoteService.java:420-427`, `copySourceStudyPack`). **Copying spends the same quota meter as generating, even though it costs zero LLM calls.**
  - **This is no longer just code-traced — it is directly confirmed against production.** For each of the 76 users, checking the 30 days before their first `remaining = 0` view: **all 76 (100%) had copied notes in that window (averaging 81.8 copies each — far more than the entire 10-credit quota), and 75 of 76 (99%) also had a `STUDY_PLAN_ADOPTED` event in that same window.** Adopting an Official Study Plan — which copies every note in the plan via `copySourceItems` → `copyNote(..., true)` — is what is draining the quota, not generation demand.
  - **This directly contradicts the Part 19 principle this audit was asked to test** ("creation may need limits; consuming existing material can be more generous") — the current architecture does the opposite for Study Packs specifically. See §13, §14, §16, §19, §20.
  - **⚠️ This also collides with §6's strongest lead.** The single behavior most associated with returning (adopting a plan in the first 24h, §6) is the *same* behavior that drains the Free Study Pack quota and triggers the paywall. Prompting more adoption (§20 E2) without first fixing the quota meter (§20 E4) could push more first-day users straight into a paywall. **E4 should ship before E2 is run,** or E2's own near-limit/paywall views should be added as an explicit guardrail.
  - **Was this deliberate? Checked via `git log -S`, and the timeline settles it.** The `max()` quota logic was introduced in `dbdb47be` (2026-04-02), titled "synchronize Study Pack limit enforcement, warnings, and suggestion workflows" — "unify backend/frontend usage calculations" and "display accurate remaining counts." Nothing in the commit message or diff mentions copies or a creation-vs-consumption tradeoff. **`copySourceStudyPack` (the method that makes copying insert a `study_packs` row) didn't ship until `bdeaa0e7`, 2026-06-04 — two months later.** The author of the quota safeguard could not have been reasoning about copies at all, since the interaction didn't exist yet. **This is an accidental defect, not a deliberate boundary.** Changing it changes quota semantics and has a real cost (below), which is the owner's call (§23).
- **Best defensible cost envelope (still DERIVED, since `estimated_cost` isn't populated and no OpenAI invoice was read):**
  - Monthly worst case per user = Σ(quota × per-call cost), using the premium model for paid Study Pack generation and Long Exam.
  - The worst-case drivers in order: Pro Study Packs (100, premium), Adaptive Practice (30, fresh), Long Exam (12, fan-out), Board Exam (10).
  - Without a populated `estimated_cost` or OpenAI billing data, this cannot be turned into pesos. No price-per-token figure is invented here.
- **Infra:** the database runs on a **0.1 CPU / 256 MB** instance (Audit C §1.2 #6), so infra is currently the binding *reliability* constraint more than a cost line.
- **Gaps, explicitly:**
  1. No global LLM budget or circuit-breaker.
  2. `estimated_cost` exists in schema but is not populated — no per-user $ tracking exists anywhere in practice.
  3. Rate limiter state is in-memory and lost on restart.
  4. No mapping from cost to plan revenue.
  5. The missing billing input is per-token model pricing and/or the OpenAI usage dashboard — neither was available to this session, and neither should be estimated from memory.
- **Gate on pricing work:** any price below the current one should wait until (1) and (2) exist. The quota-exhaustion finding above does **not** itself raise LLM-cost risk — copies cost no LLM money, so hitting `remaining = 0` via copying doesn't raise marginal spend today. **But *fixing* it does:** restoring real generation headroom to the 76 currently-blocked users is the whole point of §20 E4, and that fix is new, real demand (~2.8M tokens/month DERIVED, §23 #1) — the spend ceiling or circuit-breaker in gap (1) should exist, or at minimum be sized, before E4 ships, not after.

---

## 18. Infrastructure Health

This section is kept separate from product-behaviour findings.

**MEASURED (Audit C §1; the 2026-10-01 finding):**
- **24 `server_failed` events since 2026-09-01.** 23 were "HTTP health check failed (timed out after 5 seconds)".
- Each meant a 40–90 s window of 499/500/502 responses, plus a cold-start tail.
- **15 of 24** were preceded by one public note fetched 11–18 times in 4 minutes. In the root-caused mechanism, two "walk the whole catalog, re-rank in JS" discovery rails ran ~18–30 statements per render, against a 0.1-CPU database (queries up to 23 s), starving Tomcat threads.
- 3 restarts were broad crawling; 6 remain unexplained.
- **The failure sits on exactly the public SEO surfaces** (public note pages, library, exam hub, sitemap) where organic traffic lands. The whole backend goes down during each window, including authenticated users.

**Fix and checkpoint status (verified directly against Render and git in this correction pass):**
- **Leg B ("Bounded Discovery", `v0.165.0`) is deployed.** Confirmed via Render's own event log: `deploy_ended` succeeded 2026-10-05T15:21:14Z on `srv-d6u0jkvgi27c73dvl9k0` (`notelib-backend-prod`). `[CHECKPOINT — due 2026-10-12T15:24:27Z]` is **open, not yet due**, but there is a genuinely positive interim signal: **zero `server_failed` events from that deploy through 2026-10-08** (this pass queried Render's event log directly), versus the last pre-fix failure at 2026-10-04T18:02Z. ~3 days is too short to close the checkpoint, but it is the right direction.
- **`v0.166.0` is confirmed MERGED to `main`:** `git log main --oneline | grep 1479` returns `db1c7c5d Merge pull request #1479 from einarjohnlagera/releases/v0.166.0` (verified directly in this pass). **A further Render deploy succeeded 2026-10-07T11:19:29Z**, matching the service's own `updatedAt` — strong evidence `v0.166.0` is both merged and live, not merely merged.
- **⚠️ `ROADMAP.md:823` is confirmed stale and should be corrected.** It says *"`v0.166.0` is not yet merged to `main` … neither fix is live"*; both the git log and the Render deploy event contradict this directly. This report does not edit `ROADMAP.md` (out of scope for an investigation deliverable) — flagging it as a concrete, verified correction for the owner to make.

**Business impact: UNKNOWN.** No conversion-during-outage analysis exists. INFERRED: repeated failures on the dominant entry surface plausibly cost both visitors and crawl health, but the size of that cost is not measurable with current data.

---

## 19. Instrumentation Gaps

Ranked by decision value:

1. **No Xendit-side checkout detail captured** (channel picked, attempt failures, time on page). This blocks diagnosing the CRITICAL leak. *Fix by:* the owner pulls the Xendit dashboard (no code needed), then decides whether to capture invoice callbacks with more fields.
2. **No anonymous or session ID** on `analytics_events` or the client payload. Anonymous views can never be joined to signups, so neither Public Note nor organic-search conversion can be measured.
3. **Paywall trigger-source — RESOLVED in this pass, no longer a gap.** `source`/`feature`/`paywallType` in `metadata_json` were read directly (re-run without a silent `LIMIT 20` that truncated the first attempt): Study Pack near-limit is the dominant `PAYWALL_VIEWED` trigger (77 distinct users), Board Exam lock second (49 users), and the dominant `UPGRADE_CLICKED` source (11 of 16) is a residual bucket dominated by the passive `dashboard_free_plan_card`, not a feature-specific lock. See §10, §11, §20 E2.
4. **No bot or crawler telemetry and no Render request logs.** 56,645 views are undecomposable, and the restart triggers are partly unattributable.
5. **No defined activation event or retention log.** `last_login_at` is a single timestamp, so true cohort curves are impossible.
6. **`SIGNUP_COMPLETED` covers 311 of 420 accounts** and `CHECKOUT_INITIATED` 11 of 17 transactions. Both are likely late-added events, and `trackEvent` swallows failures. The gaps are UNKNOWN in cause.
7. **No expired-intent event** for the copy or discovery cookies (§8).
8. **`estimated_cost` exists in schema but is never populated** (§17) — the one place cost telemetry was designed in and never finished.
9. **RESOLVED this pass, not a gap any more: the Study Pack quota metering defect (§17).** `StudyPackUsageService` counted free note copies against the same monthly counter as LLM generations. Confirmed against production, not just code: all 76 real users who hit `remaining = 0` had copied notes in the prior 30 days (avg 81.8), and 75 of 76 had adopted a Study Plan in that window. See §14, §16, §20 E4 — and note E4 should ship before E2, since adoption is both the thing that correlates with return (§6) and the thing that triggers this paywall.
10. **Doc drift:**
   - `analytics.md` names 31 of 135 event types (≈23%).
   - `billing.md` omits the live 90-day `EXAM_CYCLE` (`BillingCycle.java:6`, `PaymentService.java:453`) and is headed `v0.11.0`.
   - `pricing.md` omits Interview Practice from Pro (unverified).
   - `pricing-config.ts` has no `examCycle`.

   This lowers the reliability of any future analysis or Codex prompt that reads these docs.

---

## 20. Top 5 Product Experiments

These are small and reversible. Volume is tiny, so **support and reject outcomes are stated as raw counts over calendar windows, not rates.**

### E1: Diagnose and repair checkout completion (highest priority)

- **HYPOTHESIS:** genuine users abandon on the Xendit hosted page because of channel friction, trust, or distraction, not price — though §11's per-user breakdown shows this population is mixed (7 of 11 had ≤1 prior session), so treat this as the leading hypothesis to test, not a settled conclusion. All 11 already saw a discounted intro price (₱149/₱199, not full price) and still abandoned, which narrows "just lower the price" as a standalone fix (§12) without proving price plays no role at all.
- **CHANGE:**
  - *Step 0, owner, no code:* pull the 16 invoices in the Xendit dashboard (channel shown or chosen, attempts, failures). Message the **11** genuine users with one question: "what stopped you from finishing payment?"
  - *Step 1, small code:* show a clear "Payment not completed — resume / choose another method" state on return, and send one reminder email about 1h after an unpaid invoice, before the 24h expiry. If the Xendit data shows a channel gap, enable or feature GCash/Maya.
- **PRIMARY METRIC:** genuine external `SUCCESS` transactions (count).
- **GUARDRAIL:** no duplicate charges or activations (the webhook dedupe is already in place); no rise in support complaints.
- **OBSERVATION WINDOW:** 8 weeks after the change, or until the next 8 genuine checkout-starters, whichever is longer.
- **SUPPORTS:** ≥1 genuine completed payment, or Xendit data shows channel or attempt failures.
- **REJECTS:** ≥8 more genuine starters, 0 completions, and outreach replies that cite price. That would move the question to E5 / pricing.

**Resolved this pass, not one of the five (kept here for traceability, not counted toward the top 5):** which paywall produces upgrade intent was going to be a read-only investigation step; it has already been run twice (a first pass had a silent `LIMIT 20` that truncated results, fixed in a second pass). `PAYWALL_VIEWED` is dominated by Study Pack near-limit (77 distinct users) over Board Exam lock (49). `UPGRADE_CLICKED` is dominated (11 of 16) by sources other than either of those, mainly the ambient `dashboard_free_plan_card` — which is hard-coded to offer only Plus (§5), so this is a wiring artifact as much as a measured preference. Net: Board Exam unlock is a real but secondary paywall trigger; Study Pack quota pressure is larger. See §10, §11, §19.

### E2: Test whether prompting first-24h Study Plan adoption increases return (causal, not just correlational)

- **HYPOTHESIS:** §6's strongest lead — adopting a Study Plan in the first 24h associates with ≈10x higher D7 return (≈14.6-14.8% vs ≈1.3-1.4%) — reflects a real continuity effect, not just that more-engaged users both adopt and return (rule 1).
- **CHANGE:** on a Public Note (the dominant anonymous entry surface, §3/§8) and on the post-signup/onboarding "own note" path, add an explicit "Part of Official Review Set X — adopt it" prompt where one applies (closing the §8 gap: today a public note never links to the plan/set it belongs to). This also directly answers whether the Public Note → learning-journey gap (§8) is fixable with a small change.
- **PRIMARY METRIC:** first-24h plan-adoption share among new signups (count and rate), and D7 return rate of the *newly*-adopting cohort versus the existing ≈1.3-1.4% non-adopter baseline.
- **GUARDRAIL:** no drop in first-24h session completion among non-adopters (the prompt should not crowd out the "build from my own notes" door, §6); **and run this only after E4 ships** — Study Pack near-limit `PAYWALL_VIEWED` among new adopters should not spike, since adoption is confirmed to drain that quota (§17, E4).
- **OBSERVATION WINDOW:** ~100 new signups, which at the current ~4/week signup rate is roughly **25 weeks** — long, but honest given the denominator. **⚠️ The reject criterion is underpowered**: the existing non-adopter baseline is only 2 returners out of 150 (§6); a reject reading from a similarly small post-change sample would itself be noisy. Treat any single run of this experiment as a first data point, not a conclusive test.
- **SUPPORTS:** adoption share rises and the new adopters' D7 return stays near or above the existing ≈14.6-14.8% baseline.
- **REJECTS:** adoption share rises but the new adopters' D7 return falls toward the ≈1.3-1.4% non-adopter baseline — that would mean the original correlation was selection (more-engaged users self-selecting into adoption), not the adoption prompt causing return.

### E3: Fix the Explore-adopt intent loss and the flashcards redirect

- **HYPOTHESIS:** some adopt and flashcards intents are silently lost (30-minute TTL across verification plus 8-step onboarding; hard-coded `quick-review`).
- **CHANGE:** raise both cookie TTLs (for example to 24h) and route the flashcards intent to flashcards. Optionally add an `*_INTENT_EXPIRED` event.
- **PRIMARY METRIC:** `ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED` plus discovery-adopt resolutions per week.
- **GUARDRAIL:** no double adoption (the StrictMode guard is already in place).
- **WINDOW:** 6 weeks.
- **SUPPORTS:** a non-zero `*_INTENT_EXPIRED` count before the fix, or a visible rise in resolved adoptions.
- **REJECTS:** zero expiries ever recorded. The leak was theoretical; leave it.

### E4: Stop charging free note copies against the Study Pack generation quota

- **HYPOTHESIS:** this is the most concrete, fully MEASURED finding in the audit (§17), not a hypothesis in the usual sense — but the effect of *fixing* it on behavior is untested. `StudyPackUsageService.resolveUsage` counts `COUNT(*) FROM study_packs` in the period, and copying a note inserts a row there, so copying Official/public content spends the same meter as LLM generation. All 76 real users who hit the near-limit paywall had `remaining = 0`; all 76 had copied notes (avg 81.8) in the prior 30 days and 75 of 76 had adopted a Study Plan in that window — adoption-driven copying, not generation demand, is the exhaustion source. **This should ship before E2**, since prompting more adoption without this fix could push more users straight into this paywall on day one.
- **CHANGE:** change the usage query to count only rows where `copied_from_note_id IS NULL` (true generations), or track generation count independently of the raw row count and drop the `max()` with persisted rows. This is the direct fix for the Part-19 principle this audit was asked to test (consumption should be cheaper than creation) — today it is not, for this one meter.
- **⚠️ Expected effect, stated plainly so it isn't mistaken for a regression:** today, an adopter's quota reads `max(real generations, ~82 copied rows)` = effectively maxed out, which *blocks* their own generation for the month. After the fix, their usedCount drops to their real generation count, and they regain up to their plan's full generation quota (10/month Free). **`STUDY_PACK_GENERATED` volume among adopters should RISE, not stay flat — that is the fix working, not a regression.** This is new, real LLM spend, not free: a rough DERIVED bound for the 76 currently-blocked users, if each used their full regained Free quota, is 76 × 10 generations/month × ~3,700 tokens/generation (§17's averages) ≈ 2.8M tokens/month — no peso figure, since per-token pricing wasn't available (§17).
- **PRIMARY METRIC:** Study Pack near-limit `PAYWALL_VIEWED` count/week among adopters (should drop, since they're no longer artificially maxed by copying) alongside `STUDY_PACK_GENERATED` volume among that same group (expected to rise — track both together, not generation alone).
- **GUARDRAIL:** per-user generation volume stays at or below their plan's cap (the fix should not create unlimited generation, only restore the real cap); and the owner sets a monthly spend/volume ceiling for this newly-unblocked group before shipping, so the expected rise doesn't become an unbounded one (§17, §23 #1).
- **WINDOW:** 4 weeks — the effect should be visible almost immediately since it's a direct accounting fix, not a behavior-dependent change.
- **SUPPORTS:** near-limit paywall views among adopters drop and generation volume among that group rises within the plan's cap — both expected, together.
- **REJECTS:** `remaining = 0` views persist among users whose real generation count that month is below the cap — that would mean something else (a lower live quota value, or a different counting bug) is also at play, and this session's root-cause read was incomplete. (A drop-free reading where unblocked adopters simply generate up to their real cap and see the banner legitimately would NOT reject this fix — distinguish the two before concluding.)

**Moved out of the five, demoted by this pass's data:** the original E4 hypothesis (Board Exam's Pro-only default pushing first-session users to a paywall) is weakened on two fronts now — Board Exam lock is a smaller `PAYWALL_VIEWED` trigger than Study Pack near-limit (49 vs. 77 users), and that near-limit trigger turned out to be a copy-metering defect, not demand pressure. It's still a plausible secondary contributor; revisit after E4 above ships, not before.

### E5: Give Free one Ask Companion answer at the twice-missed moment

- **HYPOTHESIS:** a zero-taste paywall at the moment of struggle teaches Free users that help costs money. One sampled answer builds habit and creates real paid-value encounters.
- **CHANGE:** Free gets 1–3 Companion turns per month, shown only through `TwiceMissedAskCompanionCard`. Fix the "this Review Set" copy for users with no Review Set. This is a cheap critique-tier, capped cost (class C).
- **PRIMARY METRIC:** `ASK_COMPANION_STARTED` by Free users, and `UPGRADE_CLICKED` from `ASK_COMPANION_QUOTA_EXHAUSTED`.
- **GUARDRAIL:** a monthly LLM spend line for the Companion. Kill the experiment if Free Companion calls exceed a preset count.
- **WINDOW:** 8 weeks.
- **SUPPORTS:** Free users use the sample, and ≥1 later upgrade-click comes from Companion exhaustion.
- **REJECTS:** near-zero sample use. The moment isn't valued, and the paywall wasn't the issue.

**Explicitly not in the five:** price tests, Unlimited or lifetime tiers, a landing redesign, and new onboarding. Each is larger than the question it would answer, or is blocked by E1 (rule 13).

---

## 21. Recommended Sequence

**NOW** (this week, mostly owner reads, no code):
- ~~Verify the `v0.166.0` deploy state and correct `ROADMAP.md:823`~~ — **done in this pass**: `v0.166.0` is confirmed merged (`db1c7c5d`, #1479) and deployed (Render event 2026-10-07T11:19Z); `ROADMAP.md:823` is confirmed stale and still needs the owner (or a doc-only follow-up commit) to correct it.
- Read the `v0.165.0` restart checkpoint when it falls due on 2026-10-12 — interim read in this pass shows **zero `server_failed` events since the fix deployed**, a good early sign.
- Pull the Xendit dashboard for the 16 invoices and contact the **11** genuine checkout-starters (E1 step 0) — **this still needs the owner**, Xendit's own dashboard was not reachable from this session.
- ~~Run the read-only `SELECT`s listed in §23~~ — **done, including a second correction round**: effective price and per-transaction forensics (all 11 genuine attempts at the discounted intro price; a mixed engagement picture, §11), paywall source (Study Pack near-limit dominates Board Exam lock, 77 vs 49 users), and a corrected, non-circular adoption-return correlation (§6: ≈10x, not the first pass's inflated/circular reading).
- Confirm the live price **env override** values (not read in this session — would require reading Render environment variables, which may hold secrets alongside prices) and whether intro pricing is still actively offered to new signups today.

**NEXT** (once the reads land):
- E1 step 1: payment-not-completed state, reminder email, channel fix if indicated.
- **E4 (new, §20): stop counting free note copies against the Study Pack generation quota** — a fully root-caused fix (§17), cheap, and the primary metric should move almost immediately.
- **E2 (new, §20): causal test of first-24h plan adoption → return**, via a Public Note → "part of Review Set X" link — the single best-supported behavioral lead in the whole audit (§6).
- E3: intent TTL and flashcards redirect.
- Start the global LLM spend breaker design (prerequisite for any generosity or price move).

**LATER:**
- E5. The original Board-Exam-dashboard-CTA idea (demoted from E4, §20) — revisit only after the quota-meter fix ships, since it was built on a paywall driver that turned out to be smaller than Study Pack near-limit.
- Landing body copy aligned to board-exam journeys.
- Anonymous visitor ID (respecting the "no anonymous session state" rule, so a client-only analytics ID, not a session).
- Doc drift fixes (`billing.md` EXAM_CYCLE, `analytics.md` inventory).

**DO NOT DO YET:**
- Lower prices (₱79/₱149).
- An "Unlimited" tier.
- Lifetime Pro.
- Changes to Pro packaging or the exam pass.
- A landing or onboarding redesign.

Each waits until there is ≥1 repeatable external paid conversion and a spend breaker exists.

---

## 22. Pricing Decision

### **A. KEEP CURRENT PRICING FOR NOW (insufficient evidence that price is the constraint).**

**Justification from the evidence:**
- 100% of genuine checkout failures (16 transactions, 11 genuine users) ended as Xendit `EXPIRED` after a 24-hour invoice (`PaymentService.java:73`). None was a recorded rejection.
- **Every one of those 11 users was already offered the discounted intro price (₱149 Plus / ₱199 Pro, confirmed via `payment_transactions.amount`/`voucher_id` in this pass), not full price — and still abandoned.** **Rule 1 still applies: there is no full-price comparison group in this data, so this does not prove price in general is fine.** What it does show, concretely, is that a ~17-20% discount — in the same direction as, though smaller than, the proposed ₱79/₱149 cut — was not by itself sufficient to convert a single genuine prospect who got as far as opening a payment page. That narrows the space of "lower the price and it'll convert" as a standalone fix; it does not rule price out entirely.
- The leak is measured *after* the price decision and is unexplained. A further price cut is not a lever on a payment page nobody finishes, and a price test run now would be confounded by it and underpowered (about 2-3 starters a month).
- This decision does **not** claim the price is right. It claims the evidence cannot yet say whether it is wrong.
- Revisit it once E1 produces either completions or outreach and Xendit data that cite price.
- **Why the Study Pack quota-metering defect (§17/§20 E4) does not move this to C (repackage before price test):** that finding is about a Free-tier quota counter, not a Plus/Pro plan boundary — it changes nothing about what paid plans cost or include. The git timeline (§17) settles that it's an accidental bug, not a chosen packaging decision, and checkout/retention remain the higher-ranked constraints (§1) regardless of how it's resolved. Fix it because it's cheap and concrete (§20 E4), not because it bears on A vs. C.

---

## 23. Owner Decisions

**Update: Q1, Q2 and a Q3-equivalent were run directly across three correction passes** (all read-only `SELECT`s, consistent with CLAUDE.md's production rules) — their results are folded into §5, §6, §10, §11, §12, §15, §17, §19, §20 above. Final results, summarized here rather than left as pending owner work:

- **Q1 result:** every genuine checkout attempt (11 users, 13 of 16 FAILED rows) was already at the discounted intro price (₱149 Plus / ₱199 Pro); 10 of 11 attempted within 24h of signup; 10 of 11 chose Plus, not Pro; zero of all 17 transactions ever created used the code-confirmed `EXAM_CYCLE` hero cycle. See §11/§12.
- **Q2 result (re-run without the `LIMIT` that truncated the first pass):** Study Pack generation near-limit is the dominant paywall trigger (77 users) vs. Board Exam lock (49 users); the dominant upgrade-click source (11 of 16) is a residual bucket dominated by the passive `dashboard_free_plan_card`, not Board Exam lock. See §10/§19/§20 E2.
- **Q3-equivalent result (corrected for circularity — behavior measured in the first 24h, "returned" measured strictly after day 7, cohort restricted to accounts created on/after 2026-06-25, owner excluded):** adopting a Study Plan in the first 24h associates with roughly 10x higher D7 return (≈14.6-14.8% vs ≈1.3-1.4%); completing a session on top of adopting barely changes it. See §6.

What genuinely remains for the owner, because no amount of repo or production-DB reading can answer it:

1. **Do you want the Study Pack quota fixed to stop counting free copies (E4, §20)?** This is the most concrete, fully-measured finding in the audit (§17), and the git timeline settles that it's **accidental**: the quota's `max()` counting logic shipped 2026-04-02, two months before `copySourceStudyPack` (2026-06-04) made copying insert a `study_packs` row at all — its author could not have been reasoning about copies. The fix is cheap, but it has a real cost: it restores up to 10 Free generations/month for each of the 76 currently-blocked users, roughly **2.8M tokens/month** of new LLM demand (DERIVED, no peso figure available, §17) if they all used their full regained quota. **Setting a spend ceiling for this group, tied to decision #5's LLM risk tolerance, is yours to decide before this ships.**
2. **Will you pull the Xendit dashboard and contact the 11 genuine checkout-starters?** Only the owner has Xendit access (channel chosen, attempt-level detail) and the standing to email users. This is the highest-value remaining input in the whole audit — it is the one thing in §11 this session could not reach.
3. **Which offer should the in-app paywalls and the dashboard card lead with?** The `/pricing` page's hero is code-confirmed to be the ₱599/90-day exam pass when available (§11), but the dashboard's ambient upgrade card — the single most-clicked upgrade surface (§10/§20 E2) — is hard-coded to Plus and structurally cannot offer Pro or the exam pass at all (§5). 10 of the 11 genuine buyers bought Plus. The real positioning question is whether that card (and the other in-app paywalls) should be able to offer Pro/the exam pass at all, not a question about the `/pricing` page itself. The data cannot answer what they *should* lead with — that's yours — but it does show the most-used upgrade surface and the ₱599 hero are effectively disconnected today.
4. **Should a Free BOARD_EXAM user's default path lead with a free mode?** This idea (demoted from the top-5 experiments, §20) is a product-philosophy call about how early to show Pro to the majority persona. It's now a secondary question — §17 found the dominant paywall trigger is a Study Pack quota-metering defect, not Board Exam's Pro-only default — but it may still be worth testing after that fix ships. Any version of it that touches the mode-picker's pre-selection, not just the dashboard card, would amend the locked `EXAM_MODES.md:180` "default emphasis" contract. That is yours to authorize.
5. **How much monthly LLM spend are you willing to put at risk on Free generosity (E5) before a spend breaker exists?** This is a risk-tolerance number that no data here can set.
6. **Is the product's primary promise "your notes → quizzes" or "board-exam review journeys"?** The landing H1 says one thing and the user base (71% board exam) says the other. Choosing affects landing copy, Public Note → plan linking, and what Pro is sold as.
7. **Will you (or should a follow-up doc-only commit) correct `ROADMAP.md:823`'s stale merge-status claim, and add this file's Backlog Index row?** Per `CLAUDE.md`, a new `docs/claude-findings/` file needs an index row in the same commit that adds it. This report has not been committed (the branch, `releases/v0.167.0`, is already signed off — committing this needs a branch decision, which is the owner's call per the commit-workflow rules, not this session's to make unilaterally).

**Verified SQL (run directly against `notelib-db-prod`, read-only, in this pass — kept here for reproducibility, not as pending work):**

```sql
-- Q1. What price did each checkout actually face, and how soon after signup was it started?
SELECT pt.created_at, pt.plan_type, pt.billing_cycle, pt.status,
       pt.original_amount, pt.discount_amount, pt.amount, pt.voucher_id,
       pt.created_at - u.created_at AS time_since_signup, pt.expires_at,
       (u.email ILIKE '%einarjohnlagera%' OR u.email = 'einar.lagera@gmail.com') AS is_owner
FROM payment_transactions pt JOIN users u ON u.id = pt.user_id
ORDER BY pt.created_at;

-- Q2 (corrected: no LIMIT). Which gate produced paywall views and upgrade clicks? Owner excluded.
WITH fam AS (
  SELECT ae.user_id, ae.event_type,
    CASE
      WHEN ae.metadata_json->>'feature' IN ('near_limit','study_pack_limit') OR ae.metadata_json->>'paywallType' = 'GENERATE_STUDY_PACK_LIMIT' THEN 'study_pack_quota'
      WHEN ae.metadata_json->>'feature' = 'board_exam' OR ae.metadata_json->>'paywallType' = 'BOARD_EXAM_MODE_LOCKED' THEN 'board_exam'
      ELSE 'other'
    END AS family
  FROM analytics_events ae JOIN users u ON u.id = ae.user_id
  WHERE ae.event_type IN ('PAYWALL_VIEWED','UPGRADE_CLICKED') AND u.role='USER'
    AND u.email NOT ILIKE '%einarjohnlagera%' AND u.email != 'einar.lagera@gmail.com'
)
SELECT event_type, family, count(DISTINCT user_id) AS distinct_users, count(*) AS events
FROM fam GROUP BY 1,2 ORDER BY 1, 3 DESC;

-- Q3 (corrected: time-bounded, non-circular). Does adopting a plan in the first 24h associate with D7 return?
WITH behaviour_24h AS (
  SELECT e.user_id,
    bool_or(e.event_type IN ('STUDY_PLAN_ADOPTED','ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED')) AS adopted_first_day,
    bool_or(e.event_type IN ('STUDY_PACK_GENERATED','QUICK_REVIEW_COMPLETED','CHALLENGE_QUIZ_COMPLETED','ADAPTIVE_PRACTICE_COMPLETED')) AS completed_first_day
  FROM analytics_events e JOIN users u2 ON u2.id = e.user_id
  WHERE e.created_at <= u2.created_at + interval '24 hours'
  GROUP BY e.user_id
),
returned AS (
  SELECT DISTINCT e.user_id FROM analytics_events e JOIN users u2 ON u2.id = e.user_id
  WHERE e.created_at > u2.created_at + interval '7 days'
)
SELECT coalesce(b.adopted_first_day, false) AS adopted_first_day,
       coalesce(b.completed_first_day, false) AS completed_first_day,
       count(*) AS n, count(r.user_id) AS returned_after_d7
FROM users u
LEFT JOIN behaviour_24h b ON b.user_id = u.id
LEFT JOIN returned r ON r.user_id = u.id
WHERE u.role='USER' AND u.email NOT ILIKE '%einarjohnlagera%' AND u.email != 'einar.lagera@gmail.com'
  AND u.created_at >= '2026-06-25' AND u.created_at < now() - interval '7 days'
GROUP BY 1,2 ORDER BY 1,2;
```

Column names were verified against `V14__billing_provider_agnostic_subscription_and_transactions.sql:36-47`, `V47` (`expires_at`) and `V51__payment_transaction_pricing_and_vouchers.sql`. Metadata keys were verified against the emitting components named in §19/§20. No raw email addresses appear in this file (checked by grep before publishing) — Q1/Q2/Q3's owner-exclusion filters were applied server-side during this pass and only aggregate counts are reported above.
