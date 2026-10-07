-- ============================================================================
-- R4 pilot: replace generated Note bodies with the curator-authored bodies
-- Prepared 2026-10-06 by Claude. NOT RUN BY CLAUDE. THE OWNER EXECUTES THIS.
-- (CLAUDE.md: production writes are owner-only; routing them through a tool does not change that.)
--
-- WHAT IT DOES
--   Updates notes.content (and updated_at) on 1 existing Note row(s). Nothing else:
--   no Subject, Domain Context, Applicable Programs, Authored Depth, visibility, tags,
--   status, Study Pack or collection is touched.
--
-- WHY PLAIN TEXT
--   The Full Notes view is PLAIN TEXT (whitespace-pre-wrap, not markdown; see
--   frontend/components/notes/private-note-detail-page-client.tsx). So these bodies are a plain-text
--   rendition of the authored markdown: headings as capitalised lines, tables as bullet lines, code
--   indented by four spaces. Pasting the .md files would show raw ## and | symbols.
--
-- GUARDS (re-read from production on 2026-10-06, after your Applicable Programs fix)
--   Each UPDATE matches only if the id, title, Domain Context, the OLD body length AND the exact current
--   updated_at all still match. If anything changed since, the statement updates 0 rows instead of
--   overwriting something unexpected.
--
-- HOW TO RUN (in a transaction)
--   1. Run STEP 0 (read-only). 0a must return guard_matches = 1. Save the output of 0b: it is your
--      rollback copy of the old bodies.
--   2. Run STEP 1 inside BEGIN ... check each UPDATE returned exactly 1 row.
--      If ANY statement returned 0 rows, or a length differs from the expected one: ROLLBACK, do not COMMIT,
--      and tell Claude what step 0 showed.
--   3. COMMIT, then run STEP 2 (read-only) to verify.
--
-- AFTER THE SQL (in the application, not SQL)
--   * Open each updated Note and generate its Study Pack ("Generate Study Pack"; it reads notes.content).
--     This needs your explicit confirmation as usual and updates the existing pack in place.
--   * DO NOT use "Regenerate note" or "Bulk regenerate" on these Notes: that regenerates the BODY from
--     the title and would overwrite the authored text.
--   * Do not edit the generated pack. Tell Claude "bodies replaced and packs regenerated" and it will
--     re-read and re-score with SELECT only.
--
-- NOT COVERED
--   Applicable Programs are not changed here. They were read on 2026-10-06 and already match the shaping
--   TSV (Computer Science, Information Systems, Information Technology; Note E: Computer Science only).
-- ============================================================================

-- ============================== STEP 0 (read-only) ==============================
-- 0a. Do the guards match? Expected: guard_matches = 1
SELECT count(*) AS guard_matches
FROM notes
WHERE (id = '583c82dc-fc5b-4088-ae21-dd474a787be3'::uuid AND title = 'Algorithms and Their Properties' AND domain_context = 'COMPUTING' AND length(content) = 1915 AND updated_at = timestamptz '2026-10-06 01:00:34.497489+00');

-- 0b. SAVE THIS OUTPUT: it is the current (generated) body of each Note, your rollback copy.
SELECT id::text AS id, title, length(content) AS length, updated_at, content
FROM notes WHERE id IN ('583c82dc-fc5b-4088-ae21-dd474a787be3') ORDER BY title;

-- ============================== STEP 1 (WRITES: owner only) ==============================
BEGIN;

-- Note B: "Algorithms and Their Properties"
-- Expected: exactly 1 row. Old body 1915 characters; new body 7575 characters.
UPDATE notes
SET content = $note_body$Algorithms and Their Properties

OVERVIEW

An algorithm is a finite, precise sequence of steps that solves a problem or accomplishes a task. Algorithms are the central idea of computing: before any program is written, someone must be able to say exactly how a problem will be solved. An algorithm is independent of any particular programming language, machine or notation. The same algorithm can be written in everyday language, in pseudocode, as a diagram, or as a program in any language.

This note defines what an algorithm is, the properties that separate an algorithm from a vague set of instructions, the basic building blocks every algorithm is made from, and the difference between an algorithm and a program.

A FIRST EXAMPLE

Problem: given a list of numbers, find the largest one.

A procedure written in plain steps:

1. Treat the first number as the largest seen so far.
2. Look at each remaining number in turn.
3. If the number is larger than the largest seen so far, make it the new largest.
4. When no numbers remain, the largest seen so far is the answer.

The same algorithm in pseudocode:

    ALGORITHM FindLargest(list)
        largest ← first item of list
        FOR EACH item IN the rest of list
            IF item > largest THEN
                largest ← item
            END IF
        END FOR
        RETURN largest
    END ALGORITHM

Tracing it on the list 4, 9, 2, 7:

- (start): 4
- 9: 9
- 2: 9
- 7: 9

The result is 9. Notice that the algorithm says what to do at each step without mentioning any programming language.

PROPERTIES OF AN ALGORITHM

A set of instructions is an algorithm only if it has these properties.

1. Input. It accepts zero or more well-defined inputs (here, a list of numbers).
2. Output. It produces at least one result related to the inputs (here, the largest number).
3. Definiteness. Every step is stated precisely and unambiguously, so two people following it get the same behaviour. "Pick a good number" is not definite; "pick the first number" is.
4. Finiteness. It stops after a finite number of steps for every valid input. A procedure that can run forever is not an algorithm for that problem.
5. Effectiveness. Each step is basic enough to be carried out exactly, in principle by a person with pencil and paper. "Compute the exact decimal expansion of π" is not an effective step.

A further requirement is correctness: for every valid input, the output must actually solve the problem. The first five properties describe what makes something an algorithm; correctness describes whether it is a good algorithm for a given problem.

Examples and non-examples

- "Add the numbers from 1 to n." (Algorithm?: Yes; Why: Precise, finite for any whole number n.)
- "Make the tastiest soup." (Algorithm?: No; Why: Not definite: "tastiest" is undefined.)
- "Keep adding 1 to the total and report when you are done." (Algorithm?: No; Why: Never finishes, so not finite, and "done" is never defined.)
- "Divide by the number of items." (if the list may be empty) (Algorithm?: Incomplete; Why: Fails on an input the problem allows.)

THE BUILDING BLOCKS OF ALGORITHMS

Any algorithm can be built from three kinds of step.

- Sequence: steps carried out one after another, in order.
- Selection (decision): choose between alternatives depending on a condition (IF ... THEN ... ELSE).
- Repetition (iteration): repeat steps while or until a condition holds (WHILE, FOR EACH).

A second example uses all three. Euclid's algorithm finds the greatest common divisor of two positive whole numbers a and b:

    ALGORITHM GCD(a, b)
        WHILE b ≠ 0
            remainder ← a mod b
            a ← b
            b ← remainder
        END WHILE
        RETURN a
    END ALGORITHM

For a = 48 and b = 18: (48, 18) → (18, 12) → (12, 6) → (6, 0), so the result is 6. The repetition ends because the remainder strictly decreases and cannot go below zero, which is why it is finite.

WAYS TO EXPRESS AN ALGORITHM

- Natural language (Strength: Easy to read at first; Limitation: Prone to ambiguity)
- Pseudocode (Strength: Precise and structured, not tied to a language; Limitation: No formal standard; must be written carefully)
- Flowchart (Strength: Shows the flow of decisions and repetition visually; Limitation: Becomes unwieldy for large algorithms)
- Program (Strength: Can be executed by a machine; Limitation: Includes language details that hide the idea)

Choosing a representation is a communication decision. The algorithm itself stays the same.

ALGORITHM VERSUS PROGRAM

A program is an implementation of an algorithm in a particular programming language, for a particular machine or system. The algorithm is the idea; the program is one concrete expression of it. The same algorithm can be implemented as many different programs, and a program can contain details (memory handling, input and output formats, error messages) that the algorithm deliberately leaves out.

This distinction matters in practice: you can reason about correctness and efficiency at the level of the algorithm, then translate it into code as a separate step.

CORRECTNESS AND EFFICIENCY

Several different algorithms can solve the same problem. To compare them we ask two questions.

- Is it correct? Does it give the right answer for every valid input, including edge cases such as an empty list, a single item, or repeated values?
- Is it efficient? How does the number of steps, or the amount of memory, grow as the input gets larger?

For example, to find a value in a list that is already sorted, one algorithm checks items one by one from the start. A different algorithm repeatedly looks at the middle item and discards half the remaining list. Both are correct, but for large lists the second needs far fewer steps. Faster hardware helps, but it does not replace a better algorithm: the difference in growth quickly outweighs a constant speed-up.

CHECKING AN ALGORITHM BY TRACING

A reliable way to understand or test an algorithm is to trace it by hand: choose an input, execute the steps exactly, and record how the values change. Good test inputs include:

- a typical case;
- the smallest valid case (such as an empty or one-item list);
- cases at the boundary of a condition;
- cases with repeated or equal values.

Tracing finds missing cases before any code exists.

COMMON MISCONCEPTIONS

- "An algorithm is code." Code is one way to express it; the algorithm exists independently of any language.
- "If it runs, it is correct." A program can run and still give wrong answers on inputs nobody tried.
- "There is one algorithm per problem." Many problems have several algorithms with different trade-offs.
- "A faster computer fixes a slow algorithm." Growth in the work required can outpace any fixed speed-up.
- "Vague steps are fine if a smart reader will understand." Definiteness means the steps are unambiguous to anyone, and to a machine.

KEY POINTS TO REMEMBER

- An algorithm is a finite, precise, effective sequence of steps that turns inputs into outputs.
- Its defining properties are input, output, definiteness, finiteness and effectiveness; correctness measures whether it solves the problem for all valid inputs.
- Every algorithm is built from sequence, selection and repetition.
- Algorithms can be written as plain language, pseudocode or flowcharts, and are independent of programming languages.
- A program implements an algorithm; several algorithms can solve the same problem with different efficiency.
- Tracing with well-chosen inputs, including edge cases, is the basic way to check an algorithm.$note_body$,
    updated_at = now()
WHERE id = '583c82dc-fc5b-4088-ae21-dd474a787be3'::uuid
  AND title = 'Algorithms and Their Properties'
  AND domain_context = 'COMPUTING'
  AND length(content) = 1915                                -- still the untouched generated body
  AND updated_at = timestamptz '2026-10-06 01:00:34.497489+00'     -- unchanged since your Applicable Programs fix (2026-10-06)
RETURNING id::text AS id, title, length(content) AS new_length;

-- Each UPDATE above must have returned exactly 1 row. If so:
COMMIT;
-- Otherwise:
-- ROLLBACK;


-- ============================== STEP 2 (read-only verification) ==============================
-- Expected new_length: Algorithms and Their Properties = 7575
-- Expected first_line = the Note title; every other column (subject, domain_context, learner_level, visibility, status) unchanged.
SELECT id::text AS id, title, subject, domain_context, learner_level, visibility, status,
       length(content) AS new_length, split_part(content, E'\n', 1) AS first_line, updated_at
FROM notes WHERE id IN ('583c82dc-fc5b-4088-ae21-dd474a787be3') ORDER BY title;

-- The Study Packs are untouched until you regenerate them: their source_text still reflects the OLD body.
SELECT sp.note_id::text AS note_id, sp.id::text AS pack_id, sp.created_at, length(sp.source_text) AS pack_source_text_length
FROM study_packs sp WHERE sp.note_id IN ('583c82dc-fc5b-4088-ae21-dd474a787be3') ORDER BY sp.created_at;
