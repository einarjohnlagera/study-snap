create table if not exists challenge_quiz_question_bank (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    study_pack_id uuid not null references study_packs(id) on delete cascade,
    origin_session_id uuid references quick_review_sessions(id) on delete set null,
    question_key varchar(512) not null,
    question jsonb not null,
    learner_level varchar(50),
    last_known_outcome varchar(16) not null default 'UNANSWERED',
    claimed_session_id uuid references quick_review_sessions(id) on delete set null,
    generated_at timestamptz not null default now(),
    constraint uq_challenge_quiz_question_bank_user_pack_key unique (user_id, study_pack_id, question_key),
    constraint chk_challenge_quiz_question_bank_outcome check (last_known_outcome in ('UNANSWERED', 'CORRECT', 'INCORRECT'))
);

create index if not exists idx_challenge_quiz_question_bank_claimable
    on challenge_quiz_question_bank(user_id, study_pack_id, learner_level, claimed_session_id, generated_at);
