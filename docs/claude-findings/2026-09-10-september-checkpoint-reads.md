# 2026-09-10 — the September checkpoint reads that came due, and what they returned

## Status: reads only. No code, config or prompt changed. No release opened.

Run at the `v0.139.0` kickoff, **before** scope was proposed — per the sequencing lesson recorded at
the `v0.138.0` kickoff (*"run step 8 before proposing scope"*). All queries are `SELECT`s executed
read-only against `notelib-db-prod` (`dpg-d6tvb8fkijhs73fda4m0-a`).

Claims are labelled **VERIFIED** (a query result, a metric, or code opened) or **INFERRED**.

---

## 1. ⚠️ `v0.72.0` Retention H1+H5 — `[CHECKPOINT — due 2026-09-10]`, PROXIMAL arm: **THE KILL CRITERION FIRES**

**The criterion, quoted from the row and written before the read:** *"if learners do not commit when
asked, or committers do not act on the digest they asked for, the retention question never arises —
the return-loop framing reverts to **unconfirmed** and is **reopened rather than iterated on with
further nudge tuning**."*

**VERIFIED**, window 2026-08-11 (deploy) → 2026-09-09:

| Event | Events | Distinct users |
|---|---|---|
| `REVIEW_COMMITMENT_PROMPT_SHOWN` | 9 | 9 |
| `REVIEW_COMMITMENT_COMMITTED` | **0** | **0** |
| `REVIEW_COMMITMENT_DECLINED` | 1 | 1 |
| `DUE_CONCEPTS_DIGEST_LANDED` | 11 | **0 — every row has a NULL `user_id`** |

**Nine learners were shown the commitment prompt over a month. Not one committed.**

### ⚠️ The zero is real, and two independent instruments agree

This is the check that mattered most, because `v0.116.0` and `v0.117.0` both shipped events that
could never fire:

1. **The call site is proven live.** `COMMITTED` and `DECLINED` are emitted from **the same line** —
   `review-commitment-prompt.tsx:102`, `declined ? "REVIEW_COMMITMENT_DECLINED" : "REVIEW_COMMITMENT_COMMITTED"`.
   `DECLINED` fired once, so that line executes. **The other branch simply never happened.**
2. **The entity table agrees, independent of analytics entirely.** `SELECT count(*) FROM users WHERE
   review_days IS NOT NULL AND cardinality(review_days) > 0` returns **0**. Not one account holds a
   stored review commitment. **This rules out analytics delivery loss as the explanation** — the
   failure mode that made `v0.80.0` necessary and that biases every event-only read.

### ⚠️ Denominator, stated honestly: **n = 9**

Nine is small, and this file does not claim the feature is proven dead. What it claims is what the
criterion asked: **learners do not commit when asked.** Nine saw it, one explicitly declined, none
opted in, and the ground-truth table is empty. **INFERRED:** at n=9 a modest true commit rate (~10%)
is not excluded by chance alone; a high one (≥30%, P(zero) ≈ 4%) effectively is.

### ⚠️ Two instrumentation defects found by running the read

- **8 of 9 abandoned silently and nothing recorded it.** `review-commitment-prompt.tsx:96-107` fires
  **only after a successful save** — closing or ignoring the prompt emits nothing. So the funnel
  cannot distinguish *"ignored"* from *"considered and rejected"*, which is the distinction that
  decides whether to fix the prompt or abandon the framing.
- **The checkpoint's SECOND metric is not computable as specified.** It asks for the
  `DIGEST_LANDED → FIRST_ANSWER_SUBMITTED` rate **among committers**. All 11 `DUE_CONCEPTS_DIGEST_LANDED`
  rows carry a **NULL `user_id`**, so they cannot be joined to a committer. ⚠️ **This half of the
  checkpoint was never answerable**, and nothing announced it — the same class as the `v0.78.0`
  checkpoint that asked a question its instrument could not answer.

## 2. ✅ `v0.134.0` async fan-out — `[CHECKPOINT — due deploy + 1 day]`, PROXIMAL arm: **PASS**

**Criterion:** *"if `idx_notifications_inbox` is absent or the fan-out timer does not resolve, the
release did not take effect — investigate BEFORE publishing any announcement, because a publish is
irreversible and its copy is immutable."*

**VERIFIED:** `idx_notifications_inbox` is **present** on `notifications` (alongside
`idx_notifications_recipient_dedup`, `idx_notifications_recipient_unread`, `notifications_pkey`).
**The release took effect and the owner is clear to publish.**

**DISTAL arm is NOT YET MEASURABLE, which is a re-date and not a verdict:** `notifications` = 0 rows,
`announcements` = 0 rows. No announcement has ever been published, so *"queued > 0 but zero rows
land"* has no denominator. This matches the row's own denominator clause.

### ⚠️ And the row's instrumentation claim is WRONG

The Status cell states the meters are *"externally readable: `application.yaml:125` exposes
`health,metrics`, so `GET /actuator/metrics/{name}` serves it."*

**VERIFIED FALSE as stated.** `GET /api/actuator/metrics/announcement.fanout.duration` returns
`{"code":"AUTH_REQUIRED"}`. **The code was always right and the row was always wrong** —
`application.yaml`'s own comment at that very line says *"not permitAll (see SecurityConfig), so this
stays authenticated-only."* `/actuator/health` is public (Render health-checks it); `/metrics` is not.

⚠️ **This is a fifth bad Backlog row, found the day after `v0.138.0`'s verification pass, by the rule
that pass created.** It is not in the four that pass corrected.

## 3. ⚠️ CORRECTED — `v0.138.0` DID auto-deploy on Vercel. I called a race an absence.

**⚠️⚠️ THIS SECTION ORIGINALLY CLAIMED VERCEL MISSED `v0.138.0` AND THAT IT WAS THE SECOND MISS IN
THREE RELEASES. BOTH CLAIMS ARE FALSE AND THE ORIGINAL TEXT IS KEPT BELOW SO THE ERROR IS LEGIBLE.**

**VERIFIED:** the merge landed at `2026-09-10T01:16:42Z`; **Vercel's Production deployment for
`05c367c4` was created at `01:21:06Z`, state `success` — 4 minutes 24 seconds later.** Render went
live at `01:18:59Z`. **Both platforms auto-deployed normally.** I read the deployments API inside that
window, saw no row, and reported an absence.

**The true record is ONE confirmed miss — `v0.136.0` — not two of three.** `v0.137.0` and `v0.138.0`
both fired on their own.

⚠️ **This is `v0.137.0`'s own rule broken the day after it was written:** *a claim about production
state is a snapshot, not a fact.* I took an instantaneous reading and asserted a standing property
from it — and then scoped a release section around it.

⚠️ **THE GENERAL LESSON IS FOR THE DETECTOR, NOT JUST FOR ME: a test for ABSENCE must wait past the
thing's normal latency, or it manufactures its own false positive.** Observed auto-deploy latency is
**~2–5 minutes** on both platforms (Render 2m17s, Vercel 4m24s for this release). A deploy check run
immediately after a merge is not measuring absence; it is measuring the deploy window. `/signoff`
should wait, or the script should say so.

⚠️ **ITEM 3'S DEFECT IS UNAFFECTED AND STILL REAL.** It was never contingent on this drift: it was
reproduced by mutation against the pre-fix script under stubbed conditions, and the pre-fix script
fails the regression guard with `exit=2 want=1` while printing `VERCEL … BEHIND`. **A wrong motivating
example does not make a verified defect go away — but it would have made the release describe a
platform problem that does not exist.**

### Original text, kept for the record — every claim below the first line is what I got wrong

**~~`v0.138.0` did NOT auto-deploy on Vercel — the second miss in three releases~~**

**VERIFIED.** Render auto-deployed `05c367c4` (`dep-dah09v15efls739b7t9g`, trigger `new_commit`,
**status `live`** at 2026-09-10T01:18:59Z). **Vercel has no Production deployment for that commit at
all** — its newest is `98ef1955`, the `v0.137.0` merge.

- **The consequence is cosmetic THIS TIME, and that is luck rather than design:** `v0.138.0`'s only
  frontend change is the `package.json` version bump, and **no controller or `lib/api` file changed**,
  so there is no API-form skew of the kind that killed Your Impact in `v0.136.0`.
- **Pattern: Vercel missed `v0.136.0`, fired for `v0.137.0`, missed `v0.138.0`.**
- **INFERRED** (unchanged from the `v0.136.0` diagnosis): transient GitHub push-event delivery loss to
  the Vercel GitHub App. Not fixable from this repository.

### ⚠️ `scripts/check-deploys.sh` reports this miss as "could not check", not as drift

**VERIFIED empirically** by running it against the live drift with no `RENDER_API_KEY`:

```
VERCEL: serving 98ef1955, but origin/main is 05c367c4 — BEHIND.
CANNOT CHECK Render: RENDER_API_KEY is not set.
EXIT CODE = 2
```

The Vercel finding **is printed** — but the script's own contract says **exit 1 = drift** and
**exit 2 = could not check**. `drift=1` is set at line 63 and then discarded by the `exit 2` at line
82 before it can be honoured. **A caller reading the exit code — which is what `/signoff` and any CI
job would do — sees "I could not look" when the truth is "Vercel is definitively behind."**

⚠️ **This is the script's own stated failure class, inverted.** It was built because *"I could not
look reported as all clear"* is dangerous; it now reports a confirmed finding as "I could not look."
**And the Vercel half needs no secret** — it reads GitHub's deployments API through `gh` — so the one
platform that has actually missed two deploys is the one whose result is being thrown away.

---

## 4. Obligations

- This file needs a **Backlog Index row** (kickoff step 8 names `docs/claude-findings/`).
- **`v0.72.0`'s proximal checkpoint must be marked FIRED**, with the return-loop framing recorded as
  **reopened**, per its own pre-committed rule. ⚠️ Do not silently re-date it — the rule said
  *reopened rather than iterated on with further nudge tuning*, and quietly tuning the prompt copy is
  exactly what it forbids.
- **`v0.134.0`'s proximal arm closes PASS**; its distal arm re-dates on a zero denominator.
- **The `v0.134.0` row's "externally readable" claim must be corrected** to authenticated-only.
