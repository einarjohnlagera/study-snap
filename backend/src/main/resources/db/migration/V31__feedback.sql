create table if not exists feedback (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    email varchar(255) not null,
    message text not null,
    page_url text,
    status varchar(32) not null,
    created_at timestamptz not null
);

create index if not exists idx_feedback_user_id on feedback(user_id);
create index if not exists idx_feedback_created_at on feedback(created_at desc);
create index if not exists idx_feedback_status on feedback(status);
