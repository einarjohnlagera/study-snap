-- 19-slice-2-facet-equivalence-impact.sql
--
-- Run against PRODUCTION before scoping Release B slice 2.
--
-- WHY THIS EXISTS
--
-- 18-release-b-slice-sequence.md states slice 2's safety property as:
--
--   "Facet counts today are count(*) grouped by course_program on notes. At exactly one join
--    row per note, the join version returns identical counts. So the regression test is
--    concrete: same filter, same facet counts, same result sets, before and after."
--
-- That premise is FALSE, and structurally so rather than by data accident. The V107 backfill
-- deliberately creates NO join row for a course_program value the catalog excludes -- correct per
-- ADR-001 and per v0.70.0's owner rulings, and not a thing to "fix". But it means the population
-- is not "exactly one join row per note"; it is "exactly one join row per note WHOSE VALUE THE
-- CATALOG INCLUDES, and zero for the rest."
--
-- So moving filters and facets to the join does not preserve counts. It changes the RESULT SET:
--   - a facet for an excluded value disappears entirely rather than returning a different number
--   - `course_program = '<excluded value>'` returns 0 notes instead of N
--   - on the public library, a shareable slug URL for an excluded value stops resolving
--
-- The regression test slice 2 is scheduled around therefore cannot pass as written, and slice 2
-- cannot be scoped until the owner rules on what happens to excluded-value notes.
--
-- WHY LOCAL NUMBERS CANNOT ANSWER THIS
--
-- The local dev DB measured 55 of 92 notes-with-a-string (60%) on excluded values, with
-- 'Software Engineering' alone at 53. Production is known to differ sharply: the v0.70.0
-- vocabulary read (15-vocabulary-and-impact-results.md) measured Software Engineering at 4 notes
-- and Information Technology at 74, and local has no IT notes at all. Local is not evidence here.
-- Only query A answers the question.

-- A. THE DECIDING QUERY. How many production notes lose their program facet under a pure-join
--    rewrite? Every in_catalog = false row is a note whose facet, filter, and (on the public
--    library) slug URL stop working if reads move to the join with no fallback.
SELECT n.course_program,
       count(*)                  AS notes,
       (cp.id IS NOT NULL)       AS in_catalog
FROM notes n
LEFT JOIN course_programs cp ON cp.name = n.course_program
WHERE n.course_program IS NOT NULL
  AND trim(n.course_program) <> ''
GROUP BY n.course_program, in_catalog
ORDER BY in_catalog, count(*) DESC;

-- B. The same split as a single headline pair, for the ruling.
SELECT (cp.id IS NOT NULL) AS in_catalog,
       count(*)            AS notes,
       count(DISTINCT n.course_program) AS distinct_values
FROM notes n
LEFT JOIN course_programs cp ON cp.name = n.course_program
WHERE n.course_program IS NOT NULL
  AND trim(n.course_program) <> ''
GROUP BY in_catalog;

-- C. PUBLIC library exposure specifically. These are shareable URLs already in the wild
--    (PublicLibraryRepositoryImpl:205 filters on a slug of the name, not exact equality), so an
--    excluded value here is a live link that breaks rather than an internal filter that narrows.
SELECT n.course_program,
       count(*) AS public_notes
FROM notes n
LEFT JOIN course_programs cp ON cp.name = n.course_program
WHERE n.visibility = 'PUBLIC'
  AND n.course_program IS NOT NULL
  AND trim(n.course_program) <> ''
  AND cp.id IS NULL
GROUP BY n.course_program
ORDER BY count(*) DESC;

-- D. IS THE ZERO-ROW POPULATION FIXED OR GROWING? CourseProgramCombobox defaults
--    allowCustom = true and neither the Note Editor nor the Note Detail inline panel overrides
--    it, so the legacy course/program field still accepts arbitrary freetext. If notes created
--    recently carry values outside the catalog, "accept the regression" degrades over time
--    instead of staying flat -- which changes which option is viable.
SELECT date_trunc('week', n.created_at) AS week,
       count(*) FILTER (WHERE cp.id IS NULL)     AS new_notes_outside_catalog,
       count(*) FILTER (WHERE cp.id IS NOT NULL) AS new_notes_in_catalog
FROM notes n
LEFT JOIN course_programs cp ON cp.name = n.course_program
WHERE n.course_program IS NOT NULL
  AND trim(n.course_program) <> ''
  AND n.created_at >= now() - interval '8 weeks'
GROUP BY week
ORDER BY week;

-- E. Sanity check that V107 actually deployed and backfilled as expected before reading any of
--    the above as a slice-2 input. Expect join_rows to equal the in_catalog note count from B.
--    If V107 has not deployed yet, join_rows is 0 and queries A-D still stand on their own --
--    they read the legacy string, not the join.
SELECT (SELECT count(*) FROM note_course_program)                       AS join_rows,
       (SELECT count(*) FROM note_course_program
         GROUP BY note_id HAVING count(*) > 1 LIMIT 1)                  AS any_note_with_multiple_rows,
       (SELECT count(*) FROM course_programs)                           AS catalog_rows;
