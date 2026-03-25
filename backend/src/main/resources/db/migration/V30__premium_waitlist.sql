create table premium_waitlist (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    email varchar(255) not null,
    created_at timestamptz not null default current_timestamp
);

create unique index ux_premium_waitlist_user_id on premium_waitlist (user_id);
create index idx_premium_waitlist_created_at on premium_waitlist (created_at desc);
