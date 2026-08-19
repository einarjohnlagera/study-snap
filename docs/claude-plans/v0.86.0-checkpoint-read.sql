-- v0.86.0 checkpoint read — [CHECKPOINT — due 2026-09-01]. Run 14 days after DEPLOY (not merge).
-- Read (a) is the primary: it needs NO marker column, because a working sweeper
-- is defined by the ABSENCE of over-bound rows, not by a record of its own action.

-- (a) PRIMARY / KILL CRITERION. Any row still non-terminal past its own bound means the
--     sweeper is not doing its job. Expected: 0, 0, 0. Non-zero on any line = it is not running,
--     not committing, or is being poisoned before it reaches these rows.
select 'pool PENDING over bound'    as surface,
       count(*) as rows_past_bound
from exam_question_pool
where generation_status = 'PENDING'
  and generation_status_at < now() - interval '60 minutes'
union all
select 'pool GENERATING over bound',
       count(*)
from exam_question_pool
where generation_status = 'GENERATING'
  and generation_status_at < now() - interval '60 minutes'
union all
select 'long exam session over bound',
       count(*)
from quick_review_sessions
where status = 'GENERATING' and session_mode = 'LONG_EXAM'
  and created_at < now() - interval '30 minutes'
union all
select 'note over bound',
       count(*)
from notes
where status = 'GENERATING'
  and generation_enqueued_at < now() - interval '120 minutes';

-- (b) SECONDARY — are the bounds too TIGHT? A bound that fires on live work shows up as churn:
--     the same pool repeatedly swept to FAILED and rebuilt by the next-use refresh. There is no
--     recovered-at marker, so this is read as an unexpectedly high rate of pools sitting FAILED.
--     A healthy steady state is a small FAILED population that drains on next use.
select generation_status, count(*) as pools
from exam_question_pool
group by generation_status
order by 1;

-- (c) DENOMINATOR CHECK — was there anything to sweep at all in the window? If the stuck
--     population was already drained by the first post-deploy sweep and nothing new arrived,
--     read (a) returning 0 is consistent with "no deploys interrupted generation" and is NOT
--     evidence the sweeper works. In that case the checkpoint is a RE-DATE, not a verdict.
--
--     CORRECTED 2026-08-19. This previously counted `generation_status <> 'READY'` as
--     "non_terminal", which silently includes FAILED. FAILED is TERMINAL and self-healing
--     (refreshPool rebuilds it on next use), so the old form would have reported the 76 FAILED
--     pools observed on 2026-08-19 as 76 non-terminal rows -- inviting exactly the wrong verdict
--     on the due date: "the sweeper is drowning" when in fact nothing is stuck at all.
select count(*) filter (where generation_status in ('PENDING', 'GENERATING')) as non_terminal_pools,
       count(*) filter (where generation_status = 'FAILED')                   as failed_pools_awaiting_reuse,
       count(*)                                                               as total_pools
from exam_question_pool;

-- (d) ATTRIBUTION — added 2026-08-19 after the early read. Reads (a)-(c) establish that nothing
--     is stuck; none of them establish WHO unstuck it. That distinction matters: if something
--     other than the sweeper cleared the backlog, the sweeper can still be inert and the next
--     stranding will sit forever.
--
--     Step 6a of the sizing query CANNOT answer this -- it selects created_at, the clock v0.86.0
--     itself declared unusable for pools because rows are reused (which is why V118 added
--     generation_status_at). Read that column instead.
--
--     THE DISCRIMINATOR IS SHARPER THAN A DATE COMPARISON. V118 seeded generation_status_at
--     ONLY for rows that were PENDING or GENERATING at deploy:
--         WHERE generation_status IN ('PENDING','GENERATING') AND generation_status_at IS NULL
--     Terminal rows were never touched. So:
--       * FAILED with a NON-NULL stamp  => seeded at deploy, then written by a status write.
--                                          For a row that was stuck, that write IS the sweep.
--       * FAILED with a NULL stamp      => already FAILED before V118, never re-written. Not swept.
--     EXPECTED if the sweeper did it: roughly 37 FAILED rows stamped, the remaining FAILED rows
--     NULL, and every stamp at least one bound (60 min) after the 2026-08-18 deploy.
--     ALL FAILED rows NULL => the sweeper never wrote anything and something else cleared the
--     backlog. Diagnose before trusting it, regardless of what read (a) says.
select generation_status,
       count(*)                                             as pools,
       count(*) filter (where generation_status_at is null) as unstamped,
       min(generation_status_at)                            as oldest_status_at,
       max(generation_status_at)                            as newest_status_at
from exam_question_pool
group by generation_status
order by 1;

-- (e) SESSION HALF — the more severe surface, and still unread as of 2026-08-19. A stuck pool
--     degraded an exam start to on-demand generation; a stuck LONG_EXAM session was a HARD
--     PERMANENT BLOCK, because LongExamService:169 hands the learner back the stuck session
--     rather than creating a new one. One session was stranded from 2026-05-19.
--     EXPECTED: no GENERATING LONG_EXAM row older than the 30-minute bound.
select status,
       session_mode,
       count(*)                  as sessions,
       count(distinct user_id)   as distinct_users,
       min(created_at)           as oldest_created
from quick_review_sessions
where status <> 'COMPLETED'
group by 1, 2
order by 1, 2;
