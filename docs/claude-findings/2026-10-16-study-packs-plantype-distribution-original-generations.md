# `study_packs` PlanType distribution — original generations, read-only

## Status: diagnosis only. No code, config, or data changed. Requested by Feature Planner (cross-session) to resolve a contradiction blocking v0.174.0 LLM-tiering cost modeling (deliverable #7) and owner decision #4 (Sol-for-curated-content). No Backlog Index row written by this session — not applicable; this is a bounded production read, not a release item in its own right. No `.sql` handoff — no write was identified as needed.

Written 2026-10-16 by the Prod Investigator session, against `notelib_db_prod` (`dpg-d6tvb8fkijhs73fda4m0-a`) and local code (`backend/src/main/java/com/studysnap/backend/...`). Read-only throughout: `SELECT`/`SHOW` only, via `mcp__render__query_render_postgres`, plus `get_metrics`/`list_logs` for the merge-gate safety check and local code reads. All numbers below are a 2026-10-16 snapshot (`study_packs.max(created_at)` = `2026-10-15T12:10:33Z` — identical to the 2026-10-15 read's dataset; nothing has changed underneath since then).

## Merge-gate safety check (done first, per standing practice from 2026-10-15)

`v0.173.0`'s `[CHECKPOINT — due 2026-10-22]` signal-9 read window is still open. Checked the window since the last confirmed-clean point (`2026-10-15T15:16Z`) through now (`2026-10-16T07:15Z`):

- `list_logs` for `*terminated by signal 9*` on the DB resource, that window: **zero kills.**
- `get_metrics memory_usage`, same window: peak **260.5 MB** at 2026-10-15T17:00Z, against the 268,435,460-byte ceiling. Never breached.

This session's own queries (below) are a handful of bounded joins against `study_packs` (14,017 rows), `notes` (~14k rows), `subscriptions` and `payment_transactions` (tiny tables) — no corpus-wide scan like 2026-10-15's contamination re-scan. No safety concern.

## The question (verbatim substance, from Feature Planner)

Among the 2,234 **original** `study_packs` generations (`notes.copied_from_note_id IS NULL`): what's the PlanType distribution of the generating account, split (1) owner's known test account(s) vs. all other accounts, and (2) among "other," FREE/PLUS/PRO breakdown and whether any PLUS/PRO account there is a real Xendit-paid subscriber or an administratively-granted plan type (checking role/billing status).

This was blocking: the owner's brief says "no paying users," but the 2026-10-15 read found 98% of the whole table tagged `model_tier = PREMIUM`, which `StudyPackService.java:721` sets from `planType.isPaid()` — an apparent contradiction that determines whether the pending PREMIUM-routing bug fix (Backlog, routed to v0.174.0) will silently route curated public-catalog generation to the new Sol model at ~20x Luna's rate.

## §1 — The answer: it's one administrative grant, not revenue

```sql
WITH originals AS (
  SELECT sp.id AS pack_id, n.owner_user_id
  FROM study_packs sp JOIN notes n ON n.id = sp.note_id
  WHERE n.copied_from_note_id IS NULL
),
current_sub AS (
  SELECT DISTINCT ON (user_id) user_id, plan_type, provider, status, start_at, end_at
  FROM subscriptions WHERE plan_type IN ('PLUS','PRO')
  ORDER BY user_id,
    CASE WHEN status='ACTIVE' AND (end_at IS NULL OR end_at > now()) THEN 0 ELSE 1 END,
    created_at DESC
)
SELECT CASE WHEN o.owner_user_id = 'dee4225c-...' THEN 'owner_test_account' ELSE 'other' END AS bucket,
       u.role, COALESCE(cs.plan_type::text,'FREE') AS resolved_plan_type, cs.provider::text, cs.status::text,
       count(*), count(DISTINCT o.owner_user_id)
FROM originals o JOIN users u ON u.id = o.owner_user_id LEFT JOIN current_sub cs ON cs.user_id = o.owner_user_id
GROUP BY 1,2,3,4,5;
```

| bucket | role | plan_type (CURRENT, resolvePlan() rules) | provider | sub status | n_packs | n_accounts |
|---|---|---|---|---|---|---|
| owner_test_account (`dee4225c-...`) | ADMIN | PRO | **NONE** | ACTIVE | 2,002 | 1 |
| other | USER | FREE (no subscription row at all) | — | — | 228 | 197 |
| other | USER | **held PLUS, now EXPIRED** | XENDIT | EXPIRED | 4 | 1 |

2,002 + 228 + 4 = 2,234 ✓.

**`provider` distinguishes administrative grant from real payment** (`SubscriptionService.java` lines 528-531, `isBillableProvider`): `BillingProvider` is `NONE | INTERNAL_MIGRATION | XENDIT`, and `isBillableProvider` explicitly excludes `NONE` and `INTERNAL_MIGRATION`. The owner's own `dee4225c` account's PRO grant has `provider = NONE` — provably not a payment, by the code's own definition of what counts as billable.

That grant's full row:

```
user_id: dee4225c-e460-4f89-a6e5-cd43f6dd1972 | plan_type: PRO | provider: NONE | status: ACTIVE
start_at: 2026-03-19T15:30:13Z | end_at: NULL | cancel_at_period_end: false
```

Indefinite, no expiry, running since 2026-03-19. It is also currently the **only** `ACTIVE` paid-plan subscription of any provider in the entire `subscriptions` table (checked table-wide, not just among study-pack generators):

```sql
SELECT provider, status, count(*), count(DISTINCT user_id) FROM subscriptions
WHERE status='ACTIVE' AND plan_type IN ('PLUS','PRO') GROUP BY provider, status;
-- {"provider":"NONE","status":"ACTIVE","n":1,"n_distinct_users":1}
```

Zero XENDIT-provider active paid subscriptions exist today, anywhere in the product.

## §2 — "No paying users" is correct in substance; one historical, now-lapsed exception

`payment_transactions`, table-wide:

| status | n | distinct users | sum(amount) |
|---|---|---|---|
| SUCCESS | **1** | 1 | ₱149.00 |
| FAILED | 16 | 13 | ₱2,734.00 |

**Exactly one successful payment has ever been recorded**, by user `141530ce-d0f9-4938-bfde-39b76daca2ab` (role `USER`, `profile_type BOARD_EXAM` — a real exam-prep usage pattern, not an admin/QA shape):

```
2026-05-01  SUCCESS  PLUS/MONTHLY  amount=149.00  original=179.00  discount=30.00  voucher_id=10000000-...-009
2026-05-02  FAILED   PRO/MONTHLY   amount=249.00  (upgrade attempt)
2026-06-24  FAILED   PRO/MONTHLY   amount=249.00  (upgrade attempt)
```

Real money changed hands (₱149, not a 100%-off voucher), the discount is a normal partial promo code, and the account is role `USER` — this reads as a genuine learner who paid once, tried twice to upgrade and failed both times, and (per the subscriptions table) that PLUS subscription has since lapsed to `EXPIRED` — `resolvePlan()` would return **FREE** for this user today, not PLUS. ("Resolved PLUS" in the §1 table header is the historical label on the subscription row, not what the app would currently compute — flagging to avoid the same category of misread this read is here to resolve.)

This account's 4 original packs: 3 tagged `model_tier=PREMIUM`, 1 `FREE` — consistent with 3 of the 4 having been generated while the PLUS window was active and 1 outside it. None of this user's packs were ever copied by anyone (`copies_of_other_originals = 0`, checked directly — see §3).

**Conclusion: the owner's "no paying users" claim is true for the product today, and was true for all but one historical, now-lapsed account whose total lifetime contribution to the question-pool is 4 non-propagating packs.** It does not explain the 98% PREMIUM figure.

## §3 — What actually explains 98% PREMIUM: one grant, copied everywhere

```sql
SELECT count(*), count(*) FILTER (WHERE n.copied_from_note_id IN (SELECT id FROM notes WHERE owner_user_id='dee4225c-...' AND copied_from_note_id IS NULL))
FROM study_packs sp JOIN notes n ON n.id=sp.note_id WHERE n.copied_from_note_id IS NOT NULL;
-- total_copy_packs=11783, copies_of_owner_test_originals=11783, copies_of_other_originals=0
```

**All 11,783 copy packs in the entire table trace back to the owner's own `dee4225c` account's original content.** Not one copy anywhere in production descends from any other account's generation. Combined with §1 (2,002 of that account's 2,234-originals-wide... 2,234 total originals, of which this account owns 2,002), the owner's single admin account is the root of essentially the entire public catalog.

Checking whether `model_tier` is inherited verbatim on copy (it should be, if copies are byte-identical duplicates as the 2026-09-19 incident doc found):

```sql
SELECT src.model_tier, copy_sp.model_tier, count(*)
FROM study_packs copy_sp JOIN notes copy_n ON copy_n.id=copy_sp.note_id
JOIN notes src_n ON src_n.id = copy_n.copied_from_note_id JOIN study_packs src ON src.note_id = src_n.id
WHERE copy_n.copied_from_note_id IS NOT NULL GROUP BY 1,2;
```

| source tier | copy tier | n |
|---|---|---|
| PREMIUM | PREMIUM | 11,745 |
| PREMIUM | **FREE** | **34** |
| FREE | FREE | 4 |

Mostly a diagonal (11,745/11,783 = 99.7%), but **34 copies show `FREE` where their source is `PREMIUM`** — not pure byte-identical inheritance. **Not chased further this pass** (out of scope for the blocking question) — flagging as an open item: either these 34 were regenerated in-place by a FREE-plan copying user after the copy was made (the Versioning rule in `CLAUDE.md` permits exactly this — regeneration updates the existing Study Pack in-place), or the copy path doesn't always carry `model_tier` verbatim. Worth a one-line check before anyone relies on "copies always inherit tier" as a hard invariant.

13,749 PREMIUM-tagged rows across the **whole** table (2026-10-15 figure; not re-run here since nothing has changed) = 2,004 PREMIUM originals (2,001 owner + 3 from the lapsed Xendit user) + 11,745 PREMIUM copies, consistent.

## §4 — Decision-relevant reframe for deliverables #4 and #7

- **The PREMIUM-routing bug fix's real blast radius is not "98% of the catalog."** Copies cost zero LLM tokens (2026-10-15 read: only 3 of 11,783 copies carry any token data at all). The fix only changes behavior for **new original generations**, going forward.
- **Who generates new originals, and at what rate** (last 8 weeks, weekly):

| week | owner_test_account n_packs | other n_packs |
|---|---|---|
| 08-24 | 387 | 0 |
| 08-31 | 220 | 1 |
| 09-07 | 271 | 3 |
| 09-14 | 138 | 2 |
| 09-21 | 66 | 1 |
| 09-28 | 5 | 0 |
| 10-05 | 0 | 1 |
| 10-12 | 0 | 1 |

Real external accounts generate **1–3 original packs/week**, essentially all on FREE plan (§1) — the routing fix changes nothing for them even after it ships, because they have no active paid plan to route on. **The owner's own admin account's generation rate has fallen to ~0/week since late September** — they've stopped seeding new public-catalog content recently, but the account's PRO/`provider=NONE` grant is indefinite (no `end_at`) and still `ACTIVE`, so the exposure returns in full the next time that account is used for bulk generation or remediation.
- Average tokens/original pack: **~3,500–4,900 input, ~900–1,050 output**, similar across both buckets — curator packs aren't notably bigger than organic ones, so the per-pack cost multiplier from switching Luna→Sol is uniform, not curator-inflated.
- **This makes owner decision #4 (should curated public-catalog remediation use Sol regardless of tier?) the dominant cost lever, not organic paid usage** — because the one account that would route to Sol post-fix *is* the curation account, and the moment it does is whenever the owner next runs a bulk-generation or remediation pass under it, not a function of real subscriber growth.

## §5 — One-line flag, not investigated this pass

`challenge_quiz_question_bank` has the same ownership shape (2026-10-15 read: 99.9% of its "public-note" rows trace to this same `dee4225c` account). Whether Challenge Quiz or exam-pool generation is plan-tiered the same way `study_packs` generation is was not checked here — open question for whether the routing fix's blast radius reaches those pools too.

## What this resolves and doesn't

- **Resolves:** the contradiction is not a contradiction. "No paying users" and "98% PREMIUM" are both true, for unrelated reasons — one administrative, indefinite PRO grant on the owner's own account, propagated via copies that don't carry new LLM cost.
- **Does not resolve:** the 34-row tier-inheritance anomaly (§3), whether Challenge Quiz/exam-pool generation is plan-tiered (§5), or what the owner intends for decision #4 — all left open for Feature Planner/the owner.
