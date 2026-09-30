-- Recover the Engineering Mathematics Subject Plan's sections from note subjects.
--
-- WHY THIS IS NEEDED. The Builder renders the synthetic "Ungrouped" bucket as an editable
-- section header with no guard (study-plan-builder-page-client.tsx:541), and rename relabels
-- every item matching the old section name (:1309). One blur event on that header therefore
-- stamped a single label onto all 77 items. Confirmed: `SELECT label, count(*)` returned
-- exactly `Algebra, 77`.
--
-- WHY THE FIX IS MECHANICAL. Bulk generation preserves the curator's batch subject verbatim —
-- StudyPackService.applyBulkGeneratedMetadataToNote:1072 writes `setSubject(preservedSubject)`,
-- NOT the LLM's suggestion. So notes.subject already holds the intended section for every note:
--   Engineering Economics 11 · Differential Calculus 10 · Algebra 9 · Integral Calculus 9
--   Analytic Geometry 8 · Trigonometry 8 · Numerical Methods 8 · Probability and Statistics 7
--   Differential Equations 7   (= 77)
--
-- WHY A DIRECT WRITE IS SAFE HERE. `label` is opaque to the backend. Its only read paths are
-- response mapping, the adoption copy, and length validation — nothing interprets or derives
-- from it, and no cache or event depends on it. Section grouping and section ORDER are computed
-- client-side at render time from the items already returned.
--
-- ⚠️ RUN STEP 0 FIRST. note_collection_items has created_at only — no updated_at, no history,
-- no @Version — so a prior label value is unrecoverable once overwritten. Step 0 is the backup.

-- Replace this in all four steps.
--   :plan_id = the Engineering Mathematics Subject Plan's collection id


-- STEP 0 — BACKUP. Capture the current state before writing anything. Save the output.
-- (The expected current state is all rows at 'Algebra', so the revert is Step 3 — but capture
-- it anyway rather than trusting that expectation.)
select i.note_id, i.position, i.label, n.subject
from note_collection_items i
join notes n on n.id = i.note_id
where i.collection_id = :plan_id
order by i.position asc;


-- STEP 1 — PREVIEW. Exactly what Step 2 will write, and nothing yet written.
-- Check the counts match the nine subjects above before proceeding.
select n.subject                as new_section,
       count(*)                 as notes,
       min(i.position)          as first_position,
       max(i.position)          as last_position,
       max(length(n.subject))   as longest_name
from note_collection_items i
join notes n on n.id = i.note_id
where i.collection_id = :plan_id
  and n.subject is not null
  and btrim(n.subject) <> ''
group by n.subject
order by min(i.position) asc;
-- `first_position`/`last_position` also tell you whether each subject's notes are CONTIGUOUS.
-- Section order is derived client-side from first appearance, so contiguous blocks in curriculum
-- order (which is what per-area bulk batches produce, since each batch appends at the tail) give
-- the correct section order for free. Overlapping ranges would interleave sections instead.
-- ⚠️ longest_name must be <= 120; the column is VARCHAR(120).


-- STEP 2 — THE WRITE. Sets each item's section to its note's subject.
-- Notes with a null/blank subject are deliberately left alone; they fall to Ungrouped.
update note_collection_items i
   set label = btrim(n.subject)
  from notes n
 where n.id = i.note_id
   and i.collection_id = :plan_id
   and n.subject is not null
   and btrim(n.subject) <> ''
   and length(btrim(n.subject)) <= 120;

-- VERIFY — should now mirror the nine-row shape from Step 1.
select label, count(*)
from note_collection_items
where collection_id = :plan_id
group by label
order by count(*) desc;


-- STEP 3 — REVERT, only if needed. Returns every item to unsectioned, which is the clean
-- starting state (NOT the 'Algebra, 77' state, which was itself the bug).
-- update note_collection_items set label = null where collection_id = :plan_id;


-- ============================================================================
-- STEP 2b — OPTIONAL. Normalize positions so each section is contiguous.
--
-- ADDED after Step 1 was run against the real plan. Eight of the nine subjects were already
-- contiguous; Trigonometry was not — 7 notes at positions 9-15 plus one stray at 76, added
-- after its original batch.
--
-- YOU MAY NOT NEED THIS. Grouping is by label and section ORDER derives from first appearance,
-- so after Step 2 the plan already renders as nine sections in the correct curriculum order,
-- with the stray simply appearing last inside Trigonometry.
--
-- WHAT IT FIXES. The UNDERLYING order still interleaves. PostSessionNextStepService walks
-- position order to pick "the next unpracticed note in your plan", so a learner working
-- sequentially would reach that one Trigonometry note last, after Engineering Economics,
-- instead of with its section-mates.
--
-- THE CHEAPER ALTERNATIVE: drag that one note into place in the Builder. The reorder rewrites
-- every position anyway, so it self-heals with no SQL.
--
-- WHY THIS WRITE IS CONSISTENT WITH THE APP. `position` carries no unique constraint, and the
-- app's own setOrder assigns position by array index across the whole collection on every save
-- (NoteCollectionService:1069-1077), with removeItem densifying to 0..n-1. A dense full
-- renumber is exactly the invariant the app already maintains.
--
-- Ordering rule: sections keep their first-appearance order; notes keep their relative order
-- within a section. Nothing is resequenced by name.
-- ============================================================================

with section_order as (
    select n.subject      as section,
           min(i.position) as section_first
    from note_collection_items i
    join notes n on n.id = i.note_id
    where i.collection_id = :plan_id
      and n.subject is not null
      and btrim(n.subject) <> ''
    group by n.subject
),
renumbered as (
    select i.note_id,
           row_number() over (order by s.section_first asc, i.position asc) - 1 as new_position
    from note_collection_items i
    join notes n on n.id = i.note_id
    join section_order s on s.section = n.subject
    where i.collection_id = :plan_id
)
update note_collection_items i
   set position = r.new_position
  from renumbered r
 where r.note_id = i.note_id
   and i.collection_id = :plan_id;

-- VERIFY 2b — every section's positions should now be a contiguous run, and the runs should
-- follow curriculum order. Expect last_position = first_position + notes - 1 on every row.
select n.subject          as section,
       count(*)           as notes,
       min(i.position)    as first_position,
       max(i.position)    as last_position,
       max(i.position) - min(i.position) + 1 = count(*) as is_contiguous
from note_collection_items i
join notes n on n.id = i.note_id
where i.collection_id = :plan_id
group by n.subject
order by min(i.position) asc;
