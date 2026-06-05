ALTER TABLE users RENAME COLUMN exam_goal TO study_goal;
ALTER TABLE users ALTER COLUMN study_goal TYPE TEXT;
