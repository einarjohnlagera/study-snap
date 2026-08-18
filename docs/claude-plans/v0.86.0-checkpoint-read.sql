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
select count(*) filter (where generation_status <> 'READY') as non_terminal_pools,
       count(*)                                            as total_pools
from exam_question_pool;
