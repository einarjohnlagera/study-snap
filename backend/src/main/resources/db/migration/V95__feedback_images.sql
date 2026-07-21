create table if not exists feedback_image (
    feedback_id uuid primary key references feedback(id) on delete cascade,
    content_type varchar(32) not null,
    size_bytes integer not null check (size_bytes > 0 and size_bytes <= 2097152),
    image_bytes bytea not null,
    created_at timestamptz not null
);
