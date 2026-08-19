-- Course / Program fragmentation read — the evidence the "One canonical Course / Program
-- catalog" Backlog Index row names as the thing that would settle its [EVIDENCE] gate:
--   "a production read of distinct course_program values on public notes and across accounts."
--
-- Written 2026-08-19. The row is gated [DECISION] + [EVIDENCE]; this file closes only the
-- EVIDENCE half. The decision it feeds is whether prospective community-content fragmentation
-- justifies a migration NOW, given Trust -> Habit -> Community puts that content last.
--
-- ⚠️ READ THIS BEFORE INTERPRETING ANY NUMBER BELOW.
-- v0.79.0 already made the catalog the sole source of PUBLIC filter chips, and made suggestions
-- catalog-first everywhere EXCEPT onboarding. So a high off-catalog rate on ACCOUNTS is expected
-- and is not by itself an argument for locking the field -- it is the known cost of the
-- deliberate onboarding exclusion, which is gated on [CHECKPOINT — due 2026-09-11].
-- The number that would actually argue for locking is fragmentation on NOTES (read 2), because
-- that is what reaches discovery and generation.
--
-- ⚠️ Do NOT let read 3's long tail alone decide this. A tail of one-off values is the normal
-- shape of any free-text field; the question is whether those values are REACHABLE (public,
-- filterable) or merely personal. Read 2 separates them.

-- 1) THE CATALOG ITSELF — the denominator for everything below.
select count(*)                                    as catalog_programs,
       count(*) filter (where exam_goal_slug is not null) as with_exam_goal,
       count(distinct program_family_id)           as families
from course_programs;

-- 2) NOTES — the load-bearing read. Splits by visibility, because a private note's
--    course_program feeds only its own generation prompt, while a public one also mints
--    (or fails to mint) a discovery facet.
--    ⚠️ ADMIN-owned notes are the curated catalog; learner-owned notes are the free-text risk.
--    Reporting them together would let 900+ curator notes drown the signal.
select case when u.role = 'ADMIN' then 'curator' else 'learner' end as owner_kind,
       n.visibility,
       count(*)                                                     as notes,
       count(*) filter (where n.course_program is null)             as null_program,
       count(distinct n.course_program)                             as distinct_values,
       count(*) filter (
           where n.course_program is not null
             and not exists (select 1 from course_programs c where c.name = n.course_program)
       )                                                            as off_catalog_notes
from notes n
join users u on u.id = n.owner_user_id
group by 1, 2
order by 1, 2;

-- 3) THE ACTUAL OFF-CATALOG VALUES ON NOTES, most common first.
--    This is the list a decision gets made against -- names, not just a rate. Look for:
--      (a) near-misses that a normaliser would fix   (e.g. "BS Nursing" vs "Nursing")
--      (b) ACADEMIC LEVELS used as programs          (e.g. "High School", "Grade School")
--          -- that is its own Backlog Index row, and this read will surface it again
--      (c) genuinely missing catalog entries         -> add to the catalog, do not lock the field
--    Only (a) argues for locking. (b) and (c) argue for a normaliser and a catalog addition.
select n.course_program,
       count(*)                                                  as notes,
       count(*) filter (where n.visibility = 'PUBLIC')           as public_notes,
       count(distinct n.owner_user_id)                           as distinct_owners
from notes n
where n.course_program is not null
  and not exists (select 1 from course_programs c where c.name = n.course_program)
group by 1
order by 2 desc, 1;

-- 4) ACCOUNTS — the profile field. Expected to be the worse number, because onboarding was
--    deliberately excluded from v0.79.0's catalog-first suggestions. Recorded for completeness
--    and to re-baseline the 13.9% figure v0.79.0 captured pre-deploy.
select count(*)                                            as users,
       count(*) filter (where course_program is null)      as null_program,
       count(distinct course_program)                      as distinct_values,
       count(*) filter (
           where course_program is not null
             and not exists (select 1 from course_programs c where c.name = users.course_program)
       )                                                   as off_catalog_users
from users;

-- 5) CASE / WHITESPACE NEAR-MISSES — values that are off-catalog ONLY because of casing or
--    spacing. These are the cheapest possible win: a normaliser fixes them with no migration,
--    no locked field, and no request queue. If this number is most of read 2's off_catalog_notes,
--    the ADR amendment is the wrong tool and normalisation is the right one.
select n.course_program                                    as note_value,
       c.name                                              as catalog_value,
       count(*)                                            as notes
from notes n
join course_programs c
  on lower(regexp_replace(btrim(n.course_program), '\s+', ' ', 'g'))
   = lower(regexp_replace(btrim(c.name), '\s+', ' ', 'g'))
where n.course_program is not null
  and n.course_program <> c.name
group by 1, 2
order by 3 desc;
