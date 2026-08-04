-- Backfills the curator-classified High School notes and the Senior High strand learner levels.
-- course_program is deliberately retained and never written: changing it is cosmetic once
-- domain_context wins the resolver fallback and would make this legacy cleanup irreversible.
-- Deliberately excluded as unclassifiable, with both new axes left NULL:
--   7411902d-1264-4fd0-9908-d422cdd9e862 — General biology is framed for Software Engineers.
--   bdfaad4c-4341-4d61-abd8-3963a864f32f — Volcanic Activity uses geological and engineering contexts.
--   84d49a06-c46c-4fa3-aab8-a48aebe58f0f — Mosquito biting is general-interest science with no grade anchor.
--   9844846d-c0d1-4ef3-8528-e441bacd9c10 — Flying insects at lights is general-interest science with no grade anchor.
--   e33f27fb-827d-4b38-a9db-c31858abef3a — The smell of roads after rain is general-interest science with no grade anchor.
--   2ec3253f-d51b-4541-87e7-873020bed467 — Teaching others is a general-interest learning explainer with no grade anchor.
-- Senior High strand notes receive no domain_context so their strand remains the effective
-- authoring domain through the course_program fallback instead of collapsing into one domain.
-- The ILIKE prefix is intentional: production uses U+2013 in the three strand labels, and a
-- prefix match avoids silently missing rows because a dash was retyped or normalized.
-- The curator review listed 11 High School notes on 2026-08-03. One of the five it classified
-- (5afb255d-3c87-4a93-9d71-b310ae11ef7f, "Newton's Laws of Motion") was an exact byte-identical
-- duplicate of 2a19d8ec and was deleted by the curator before this migration was written, so
-- 10 notes and 4 classified IDs remain. Recorded here because 10-high-school-classification.sql
-- documents an expected 11 rows, and the discrepancy is otherwise unexplainable later.
-- Exact inverse: set learner_level and domain_context back to NULL on the four classified IDs;
-- set learner_level back to NULL on Senior High prefix rows. course_program already stays intact.

UPDATE notes
SET learner_level = 'JUNIOR_HIGH',
    domain_context = 'GENERAL_EDUCATION'
WHERE id IN (
    '858ccbbe-61d2-4c23-b8db-9e702af08e88',
    '2a19d8ec-93db-4c08-b1cb-b2db77c2d980',
    '31066362-1616-4b1b-84b3-143ad5a4cc0c'
)
  AND learner_level IS NULL
  AND domain_context IS NULL;

UPDATE notes
SET learner_level = 'SENIOR_HIGH',
    domain_context = 'GENERAL_EDUCATION'
WHERE id = '8726bf38-4343-45ed-a285-dc35db7b4ff9'
  AND learner_level IS NULL
  AND domain_context IS NULL;

UPDATE notes
SET learner_level = 'SENIOR_HIGH'
WHERE course_program ILIKE 'Senior High%'
  AND learner_level IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM notes
        WHERE course_program ILIKE 'Senior High%'
          AND learner_level IS NULL
    ) THEN
        RAISE EXCEPTION 'V105 failed: a Senior High strand note remains without learner_level';
    END IF;

    -- NOTE ON REACH: because this predicate mirrors the UPDATEs above it, it only fires on a
    -- DIVERGENCE between the two ID lists (someone drops an id from one and not the other). Given
    -- identical lists it is unreachable in production by construction. It cannot detect a shared
    -- wrong assumption -- that was retired by the curator review query, not by this guard.
    -- Matches the (NULL, NULL) precondition both classification UPDATEs require, so a note a
    -- curator has already partly classified through the authoring UI -- domain_context set,
    -- learner_level left blank -- is skipped by the UPDATE and by this guard alike, rather than
    -- raising and blocking the deploy over a state the curator chose. Same reasoning as V104.
    IF EXISTS (
        SELECT 1
        FROM notes
        WHERE id IN (
            '858ccbbe-61d2-4c23-b8db-9e702af08e88',
            '2a19d8ec-93db-4c08-b1cb-b2db77c2d980',
            '31066362-1616-4b1b-84b3-143ad5a4cc0c',
            '8726bf38-4343-45ed-a285-dc35db7b4ff9'
        )
          AND learner_level IS NULL
          AND domain_context IS NULL
    ) THEN
        RAISE EXCEPTION 'V105 failed: a curator-classified High School note remains without learner_level';
    END IF;

    -- Informational, never fatal. The classified IDs come from a curator review run against
    -- production on 2026-08-03; a note may legitimately be deleted between then and deploy (one
    -- already was -- an exact duplicate). A missing row must not block a release, but it should
    -- leave evidence in the deploy log rather than passing silently.
    RAISE NOTICE 'V105: % of 4 curator-classified High School notes found', (
        SELECT count(*) FROM notes WHERE id IN (
            '858ccbbe-61d2-4c23-b8db-9e702af08e88',
            '2a19d8ec-93db-4c08-b1cb-b2db77c2d980',
            '31066362-1616-4b1b-84b3-143ad5a4cc0c',
            '8726bf38-4343-45ed-a285-dc35db7b4ff9'
        )
    );

    IF EXISTS (
        SELECT 1
        FROM notes
        WHERE id IN (
            '7411902d-1264-4fd0-9908-d422cdd9e862',
            'bdfaad4c-4341-4d61-abd8-3963a864f32f',
            '84d49a06-c46c-4fa3-aab8-a48aebe58f0f',
            '9844846d-c0d1-4ef3-8528-e441bacd9c10',
            'e33f27fb-827d-4b38-a9db-c31858abef3a',
            '2ec3253f-d51b-4541-87e7-873020bed467'
        )
          AND (learner_level IS NOT NULL OR domain_context IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'V105 failed: an unclassifiable High School note was modified';
    END IF;
END $$;
