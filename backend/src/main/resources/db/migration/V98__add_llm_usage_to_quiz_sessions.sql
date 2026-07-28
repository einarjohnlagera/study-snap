alter table quick_review_sessions
    add column if not exists model_used varchar(64),
    add column if not exists input_tokens integer,
    add column if not exists output_tokens integer,
    add column if not exists cached_input_tokens integer;
