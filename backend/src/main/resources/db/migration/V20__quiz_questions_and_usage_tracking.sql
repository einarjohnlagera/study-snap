CREATE TABLE IF NOT EXISTS quiz_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    study_pack_id UUID NOT NULL REFERENCES study_packs(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    question TEXT NOT NULL,
    choices JSONB NOT NULL,
    answer TEXT NOT NULL,
    explanation TEXT,
    concept TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_quiz_questions_study_pack_position
    ON quiz_questions(study_pack_id, position);

CREATE INDEX IF NOT EXISTS idx_quiz_questions_study_pack_created
    ON quiz_questions(study_pack_id, created_at DESC);

INSERT INTO quiz_questions (
    study_pack_id,
    position,
    question,
    choices,
    answer,
    explanation,
    concept,
    created_at
)
SELECT sp.id,
       (quiz_item.ordinality - 1)::INTEGER,
       quiz_item.question_text,
       quiz_item.choices_json,
       quiz_item.answer_text,
       quiz_item.explanation_text,
       quiz_item.concept_text,
       sp.created_at
FROM study_packs sp
CROSS JOIN LATERAL (
    SELECT q.ordinality,
           COALESCE(NULLIF(BTRIM(q.item ->> 'question'), ''), 'Generated question') AS question_text,
           COALESCE(q.item -> 'choices', '[]'::jsonb) AS choices_json,
           COALESCE(NULLIF(BTRIM(q.item ->> 'answer'), ''), 'N/A') AS answer_text,
           NULLIF(BTRIM(q.item ->> 'explanation'), '') AS explanation_text,
           NULLIF(BTRIM(q.item ->> 'concept'), '') AS concept_text
    FROM jsonb_array_elements(COALESCE(sp.quiz, '[]'::jsonb)) WITH ORDINALITY AS q(item, ordinality)
) AS quiz_item
ON CONFLICT (study_pack_id, position) DO NOTHING;

CREATE TABLE IF NOT EXISTS user_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month INTEGER NOT NULL,
    year INTEGER NOT NULL,
    study_pack_generations INTEGER NOT NULL DEFAULT 0,
    challenge_quiz_generations INTEGER NOT NULL DEFAULT 0,
    adaptive_quiz_generations INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_user_usage_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_user_usage_non_negative CHECK (
        study_pack_generations >= 0
        AND challenge_quiz_generations >= 0
        AND adaptive_quiz_generations >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_usage_user_year_month
    ON user_usage(user_id, year, month);

CREATE INDEX IF NOT EXISTS idx_user_usage_user_year_month
    ON user_usage(user_id, year, month);

CREATE OR REPLACE VIEW quiz_sessions AS
SELECT q.id,
       q.user_id,
       q.note_id,
       q.study_pack_id,
       q.session_mode AS type,
       q.score_percentage AS score,
       q.total_questions,
       q.completed_at,
       q.created_at
FROM quick_review_sessions q;

WITH month_bounds AS (
    SELECT date_trunc('month', NOW() AT TIME ZONE 'UTC')::timestamptz AS month_start
),
study_pack_counts AS (
    SELECT sp.owner_user_id AS user_id,
           COUNT(*)::INTEGER AS cnt
    FROM study_packs sp
    CROSS JOIN month_bounds b
    WHERE sp.created_at >= b.month_start
      AND sp.created_at < (b.month_start + INTERVAL '1 month')
    GROUP BY sp.owner_user_id
),
challenge_counts AS (
    SELECT q.user_id,
           COUNT(*)::INTEGER AS cnt
    FROM quick_review_sessions q
    CROSS JOIN month_bounds b
    WHERE q.session_mode = 'CHALLENGE'
      AND q.created_at >= b.month_start
      AND q.created_at < (b.month_start + INTERVAL '1 month')
    GROUP BY q.user_id
),
adaptive_counts AS (
    SELECT q.user_id,
           COUNT(*)::INTEGER AS cnt
    FROM quick_review_sessions q
    CROSS JOIN month_bounds b
    WHERE q.session_mode = 'ADAPTIVE'
      AND q.created_at >= b.month_start
      AND q.created_at < (b.month_start + INTERVAL '1 month')
    GROUP BY q.user_id
),
candidate_users AS (
    SELECT user_id FROM study_pack_counts
    UNION
    SELECT user_id FROM challenge_counts
    UNION
    SELECT user_id FROM adaptive_counts
)
INSERT INTO user_usage (
    id,
    user_id,
    month,
    year,
    study_pack_generations,
    challenge_quiz_generations,
    adaptive_quiz_generations,
    created_at
)
SELECT gen_random_uuid(),
       cu.user_id,
       EXTRACT(MONTH FROM b.month_start)::INTEGER AS month,
       EXTRACT(YEAR FROM b.month_start)::INTEGER AS year,
       COALESCE(spc.cnt, 0),
       COALESCE(cc.cnt, 0),
       COALESCE(ac.cnt, 0),
       NOW()
FROM candidate_users cu
CROSS JOIN month_bounds b
LEFT JOIN study_pack_counts spc ON spc.user_id = cu.user_id
LEFT JOIN challenge_counts cc ON cc.user_id = cu.user_id
LEFT JOIN adaptive_counts ac ON ac.user_id = cu.user_id
ON CONFLICT (user_id, year, month)
DO UPDATE SET
    study_pack_generations = GREATEST(user_usage.study_pack_generations, EXCLUDED.study_pack_generations),
    challenge_quiz_generations = GREATEST(user_usage.challenge_quiz_generations, EXCLUDED.challenge_quiz_generations),
    adaptive_quiz_generations = GREATEST(user_usage.adaptive_quiz_generations, EXCLUDED.adaptive_quiz_generations);
