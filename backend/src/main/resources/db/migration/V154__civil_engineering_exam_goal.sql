ALTER TABLE course_programs DROP CONSTRAINT ck_course_programs_exam_goal_slug;
ALTER TABLE course_programs ADD CONSTRAINT ck_course_programs_exam_goal_slug
    CHECK (exam_goal_slug IS NULL OR exam_goal_slug IN ('ale', 'pnle', 'let', 'cpale', 'ce'));

DO $$
DECLARE affected integer;
BEGIN
    UPDATE course_programs SET exam_goal_slug = 'ce'
    WHERE name = 'Civil Engineering';
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one Civil Engineering course_programs row; found %', affected;
    END IF;
END $$;
