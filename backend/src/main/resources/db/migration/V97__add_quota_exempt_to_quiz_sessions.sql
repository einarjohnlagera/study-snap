alter table quick_review_sessions
    add column if not exists quota_exempt boolean not null default false;
