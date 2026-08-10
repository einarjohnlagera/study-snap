# Diagnostic Read — Round 2 results

**Run:** 2026-08-07, against production, read-only. Queries: `17-diagnostic-read-round2.sql`.
**Round 1** ran 2026-07-24/25 and was inconclusive by construction — the surge cohort's 14-day window had not
closed. It has now.

---

## The headline is not the one we went looking for

We pulled Round 2 because **Query 4 expires**: `v0.71.0` slice 5 opens practice-first beyond `BOARD_EXAM`, and
once that deploys, "users who could have adopted but authored instead" stops being a coherent group. That query
came back **inconclusive**. What came back instead is a far larger finding.

| Read | Result | Denominator |
|---|---|---|
| Blended W1→W2 | **0.91%** | 1 of 110 |
| **Open-ended learners** | **0%** | **0 of 74** |
| Exam-bound, scored | 16.67% | 1 of 6 — **not measurable** |
| create-first (covered tracks) | 0% | 0 of 33 |
| practice-first (covered tracks) | 5.56% | 1 of 18 |

**Cohort:** 164 signups since 2026-07-01; 110 eligible (window closed); 54 still in flight. Latest signup
2026-08-07 — acquisition is ongoing.

The segmentation cross-checks: exam-bound 6 + 51 = 57 and open-ended 74 + 33 = 107 sum to 164, matching Query 1.
The single W2 returner must be exam-bound, since open-ended returned zero.

## Instrumentation was verified before drawing any conclusion

1 of 110 users firing *any* event in days 8–14 is extreme enough to suspect a broken pipe rather than broken
retention. It is not broken:

| Source | Rows since ramp | Distinct users | Most recent |
|---|---|---|---|
| `analytics_events` | 16,903 | 172 | 2026-08-07 12:24 |
| `user_activity_events` | 928 | 97 | 2026-08-07 06:16 |

Both are alive, current, and carry healthy volume. **~98 analytics events per user, concentrated early, then
silence.** The read stands.

A second corroboration: `returned_any_event` and `returned_meaningful_study` are **identical at 1**. Round 1's
design treated divergence between them as its own finding — high any-event with low meaningful-study would mean
people return without studying. There is no divergence, because essentially nobody returns at all.

## What each result licenses, and what it does not

**Open-ended learners: 0 of 74.** A real denominator, not a small-n artefact. This is the strongest and most
uncomfortable number in the read: no open-ended learner who signed up since 2026-07-01 came back in week 2.

**Blended 0.91%** is *below* the historical 2.4%/127 that the Company Redefinition was launched to address. Two
caveats before treating that as a decline: the cohorts differ, and 1-of-110 is close enough to 3-of-127 that the
gap is not itself the finding. **The finding is that neither is meaningfully above zero.**

**Exam-bound: 1 of 6 — report the raw count, not the percentage.** "16.67%" from a denominator of 6 is exactly
the trap the ratified definition warns about. Note also that **51 of 57 exam-bound users are `in_flight`** —
their exam dates have not arrived, so they cannot be scored yet. This segment stays unmeasurable for now and
will carry real signal later.

**The 0/41 exam-dated finding is not excused by the reframe.** That measured disengagement *before* the goal,
which is a real problem under either frame. The reframe only stops penalising expected disengagement *after* the
exam date.

## Query 4 — inconclusive, and the window is now closed

**create-first 0 of 33 · practice-first 1 of 18.** One user separates the arms. That decides nothing.

The honest outcome: **the comparison was captured before it expired, and there was never enough signal in it to
decide anything.** Recording this matters more than the number — it means the question was asked and answered
"insufficient data", rather than being silently dropped when slice 5 shipped.

**Consequence:** slice 5's practice-first opening is **no longer gated on this read.** There is no comparison
left to protect.

## What this does and does not say about `v0.71.0`

**It does not validate the Intent Router.** Nothing here shows that offering two doors improves retention, and
claiming otherwise would be reading a hypothesis into a null result.

**It does raise the stakes on what the release is for.** A ~1% week-2 return rate says the problem is not which
door a learner picks at signup — it is that nothing brings them back afterwards. The Intent Router shortens
time-to-first-value, which is plausibly necessary and clearly not sufficient.

**The gap this exposes is a return trigger, not an entry point.** There is no scheduled reason for a learner to
come back: no reminder tied to an exam date, no streak, no queued next session. That is a product gap this
roadmap has not yet scoped, and on this evidence it outranks further entry-point work.

## Recommended next steps

1. **Unblock and merge slice 5 stage 3** (PR #1008). Its gate is discharged — inconclusively, but discharged.
2. **Re-read the exam-bound segment once more exam dates pass.** 51 of 57 are `in_flight` today; that segment is
   where the ratified definition actually bites, and it is currently unmeasurable rather than negative.
3. **Scope a return trigger as its own initiative**, ahead of further entry-point work. This read is the
   evidence for it. Do not fold it into `v0.71.0`.
4. **Do not re-run Query 4.** The comparison group ceases to exist at deploy; this file is its final record.
