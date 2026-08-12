-- v0.74.0 -- which notes render math as raw LaTeX?
--
-- Purpose: list the individual notes to regenerate by hand. Production sizing on 2026-08-12
-- put the affected population at roughly 23 of 5,472 study packs (~0.4%), which is why the
-- admin backfill (Part C) was dropped in favour of manual regeneration.
--
-- What actually breaks: the KaTeX renderer only activates INSIDE delimiters
-- ($...$, \(...\), $$...$$, \[...\]). Anything else prints literally, backslash and all.
-- The generation prompts never told the model to emit delimiters, so it mixes styles --
-- sometimes correct, sometimes bare -- even within one quiz.
--
-- Read `verdict` first:
--   NEEDS_FIX        -- math constructs present, NO delimiter anywhere. Regenerate these.
--   MIXED_CHECK_IT   -- has delimiters AND bare carets. Eyeball it; often partly fine.
--   LIKELY_OK        -- delimiters present, no bare carets. Probably renders correctly.
--
-- Note on carets: `x^2` is the common bare case. It is matched separately from backslash
-- commands because a pack can have one without the other.

SELECT n.id                                                   AS note_id,
       LEFT(n.title, 60)                                      AS note_title,
       u.email                                                AS owner_email,
       n.visibility,
       sp.created_at::date                                    AS pack_created,
       CASE
           WHEN sp.quiz::text !~ '\\[()\[\]]' AND sp.quiz::text NOT LIKE '%$%'
               THEN 'NEEDS_FIX'
           WHEN sp.quiz::text ~ '\^[0-9{]'
               THEN 'MIXED_CHECK_IT'
           ELSE 'LIKELY_OK'
       END                                                    AS verdict,
       sp.quiz::text ~ '\\(frac|sqrt|sum|int|cdot|times|div|le|ge|pm|alpha|beta|theta)'
                                                              AS has_latex_command,
       sp.quiz::text ~ '\^[0-9{]'                             AS has_bare_caret,
       sp.quiz::text ~ '\\[()\[\]]'                           AS has_paren_delimiter,
       sp.quiz::text LIKE '%$%'                               AS has_dollar
FROM study_packs sp
JOIN notes n ON n.id = sp.note_id
JOIN users u ON u.id = n.owner_user_id
WHERE sp.quiz IS NOT NULL
  AND (
        sp.quiz::text ~ '\\(frac|sqrt|sum|int|cdot|times|div|le|ge|pm|alpha|beta|theta)'
     OR sp.quiz::text ~ '\^[0-9{]'
      )
ORDER BY verdict, sp.created_at DESC;


-- Companion query -- see the offending text itself, to judge whether a regenerate is worth it.
-- Run after the list above, once you know which note_ids you care about.
SELECT n.id                                        AS note_id,
       LEFT(n.title, 40)                           AS note_title,
       LEFT(q->>'question', 90)                    AS question,
       LEFT((q->'choices')::text, 160)             AS choices
FROM study_packs sp
JOIN notes n ON n.id = sp.note_id,
     LATERAL jsonb_array_elements(sp.quiz) q
WHERE (q->>'question') ~ '\\(frac|sqrt|sum|int)|\^[0-9{]'
   OR (q->'choices')::text ~ '\\(frac|sqrt|sum|int)|\^[0-9{]'
ORDER BY n.id;
