-- v0.86.0 — Generation Recovery: production sizing for the stale-generation sweeper.
--
-- ⚠️ RUN AGAINST PRODUCTION. Every step here is READ-ONLY. Nothing writes.
-- No psql meta-commands, so it pastes into DBeaver / JetBrains / pgAdmin as-is.
--
-- WHY THIS EXISTS. A note is stamped GENERATING and COMMITTED before its generation task is
-- dispatched (StudyPackService:209-211, then dispatchAfterCommit). If that task never finishes,
-- the row stays GENERATING forever: resolveSourceNoteForGeneration:622 throws a 409
-- NOTE_GENERATION_IN_PROGRESS on every subsequent attempt, and no scheduled job sweeps stale
-- rows. The learner cannot get out of it. Deploy is the most frequent trigger — the executor
-- takes shutdownNow() and `main` auto-deploys on merge — but crash, OOM, SIGKILL and queue
-- overflow produce the identical row.
--
-- The fix is an age-threshold sweeper that resolves a stuck row to FAILED, which is the state the
-- existing "Retry Generation" button already acts on. Two things must come from data, not from
-- argument:
--
--   (a) THE THRESHOLD. Step 2's age distribution is what picks it. The measured upper bound on a
--       single LLM call is the 180s read timeout at OpenAiLlmConfig:26, so a legitimate
--       generation is bounded by a small multiple of that — but a BULK run enqueues in a loop
--       against core pool 3, so a note deep in the queue is legitimately GENERATING for much
--       longer before its first call. The threshold must sit above real bulk-queue waits and
--       below the stuck population. Step 2 is what shows whether those two ranges separate
--       cleanly or overlap.
--
--   (b) WHETHER A ONE-TIME DATA FIX IS OWED IN SCOPE. Step 1's count decides it. Notes already
--       stuck today will NOT be reached by a sweeper that keys on a new start-timestamp column
--       (they have no value in it), so if the count is non-trivial the release owes a V118 that
--       resolves the existing backlog explicitly.
--
-- ⚠️ ONE CAVEAT ON THE CLOCK, and it is the reason step 2 is a proxy rather than an answer.
-- There is no generation_started_at column today. `updated_at` is the only timestamp available,
-- and NoteService writes it on ORDINARY NOTE EDITS (NoteService:191, :251, :316, :459) — so an
-- edit made while a note was generating resets the age reported below, understating how long that
-- row has actually been stuck. Read step 2 as a LOWER BOUND on age, never an exact one. This is
-- also the direct argument for the new column: the sweeper cannot ship on this clock.


-- STEP 1 — How many notes are stuck right now, and whose are they?
-- Curator-owned rows are the visible catalog; learner-owned rows are the ones costing a person
-- their own work. Both matter, for different reasons.
select
    case when u.role = 'ADMIN' then 'curator' else 'learner' end as owner_kind,
    count(*)                                                     as stuck_notes,
    count(distinct n.owner_user_id)                              as distinct_owners,
    min(n.updated_at)                                            as oldest_seen,
    max(n.updated_at)                                            as newest_seen
from notes n
join users u on u.id = n.owner_user_id
where n.status = 'GENERATING'
group by 1
order by 1;


-- STEP 2 — Age distribution of the stuck population (LOWER BOUND — see the caveat above).
-- This is the query that picks the threshold. Expect a legitimate in-flight cluster in the first
-- bucket or two and, if the defect is as described, a long tail of rows days or weeks old with
-- nothing in between. If the buckets do NOT separate, the threshold cannot be set from this data
-- alone and the release should ship the start-timestamp column first and re-read after a week.
select
    case
        when now() - n.updated_at < interval '5 minutes'  then 'a. < 5 min (plausibly live)'
        when now() - n.updated_at < interval '15 minutes' then 'b. 5-15 min (plausibly live, bulk queue)'
        when now() - n.updated_at < interval '1 hour'     then 'c. 15-60 min (suspicious)'
        when now() - n.updated_at < interval '1 day'      then 'd. 1-24 hours (stuck)'
        when now() - n.updated_at < interval '7 days'     then 'e. 1-7 days (stuck)'
        else                                                   'f. > 7 days (stuck)'
    end                                     as age_bucket,
    count(*)                                as stuck_notes,
    count(*) filter (where u.role = 'ADMIN') as curator_owned
from notes n
join users u on u.id = n.owner_user_id
where n.status = 'GENERATING'
group by 1
order by 1;


-- STEP 3 — Does the stuck note already have a Study Pack?
-- A stuck row WITH a pack was a regeneration: the learner still has the old pack and lost only the
-- update. A stuck row WITHOUT one is a learner staring at a spinner over nothing. The split
-- changes how the frontend should present the recovered FAILED state, so it is worth knowing
-- before the copy is written.
select
    case when sp.id is null then 'no existing pack (first generation)' else 'has existing pack (regeneration)' end as shape,
    count(*) as stuck_notes
from notes n
left join study_packs sp on sp.note_id = n.id and sp.owner_user_id = n.owner_user_id
where n.status = 'GENERATING'
group by 1
order by 1;


-- STEP 4 — The same stuck shape on quiz sessions and exam pools, which share the SAME executors.
-- These are OUT of v0.86.0 scope until this read says otherwise. LongExamService:169 and
-- ExamQuestionPoolService:210 both refuse to restart while their row reads GENERATING, so they
-- carry the identical unrecoverable shape. If these counts are material the sweeper should cover
-- them in this release; if they are near zero, they are recorded as a Known Limitation instead.
select
    'quick_review_sessions' as surface,
    s.session_mode          as detail,
    count(*)                as stuck_rows,
    min(s.created_at)       as oldest_seen
from quick_review_sessions s
where s.status = 'GENERATING'
group by 1, 2

union all

select
    'exam_question_pool' as surface,
    p.generation_status  as detail,
    count(*)             as stuck_rows,
    min(p.created_at)    as oldest_seen
from exam_question_pool p
where p.generation_status = 'GENERATING'
group by 1, 2

order by 1, 2;


-- STEP 5 — Is the stuck population concentrated in bulk runs?
-- Bulk enqueues in a loop (NoteBulkGenerationService:323), so a single interrupted bulk run can
-- account for a large share of the backlog on its own. If one owner and one narrow time window
-- hold most of the rows, the threshold must be set with bulk queue waits in mind rather than from
-- the single-note case.
select
    date_trunc('hour', n.updated_at) as stuck_hour,
    n.owner_user_id,
    count(*)                         as stuck_notes
from notes n
where n.status = 'GENERATING'
group by 1, 2
having count(*) > 1
order by 3 desc, 1 desc
limit 25;
