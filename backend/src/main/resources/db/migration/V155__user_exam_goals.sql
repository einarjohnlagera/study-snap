CREATE TABLE user_exam_goals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exam_goal_slug text NOT NULL CHECK (exam_goal_slug IN ('ale', 'pnle', 'let', 'cpale', 'ce')),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_user_exam_goals_user_slug UNIQUE (user_id, exam_goal_slug)
);

CREATE UNIQUE INDEX ux_user_exam_goals_one_active ON user_exam_goals (user_id) WHERE is_active;

DO $$
DECLARE
    expected integer;
    affected integer;
BEGIN
    SELECT count(*) INTO expected FROM users
    WHERE lower(trim(study_goal)) IN ('ale', 'pnle', 'let', 'cpale', 'ce');

    INSERT INTO user_exam_goals (user_id, exam_goal_slug, is_active)
    SELECT id, lower(trim(study_goal)), true FROM users
    WHERE lower(trim(study_goal)) IN ('ale', 'pnle', 'let', 'cpale', 'ce');
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> expected THEN
        RAISE EXCEPTION 'Expected % migrated exam goals; inserted %', expected, affected;
    END IF;

    UPDATE users SET study_goal = NULL
    WHERE lower(trim(study_goal)) IN ('ale', 'pnle', 'let', 'cpale', 'ce');
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> expected THEN
        RAISE EXCEPTION 'Expected % cleared scalar exam goals; updated %', expected, affected;
    END IF;
END $$;
