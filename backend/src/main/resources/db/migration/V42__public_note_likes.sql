create table public_note_likes (
    id uuid primary key,
    note_id uuid not null references notes(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    created_at timestamptz not null default current_timestamp
);

create unique index ux_public_note_likes_note_user on public_note_likes (note_id, user_id);
create index idx_public_note_likes_note_id on public_note_likes (note_id);
create index idx_public_note_likes_user_id on public_note_likes (user_id);
