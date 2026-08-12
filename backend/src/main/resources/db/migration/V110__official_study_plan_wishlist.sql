create table official_study_plan_wishlist (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    course_program varchar(120) not null,
    normalized_course_program varchar(120) not null,
    created_at timestamptz not null default current_timestamp
);

create unique index ux_official_study_plan_wishlist_user_program
    on official_study_plan_wishlist (user_id, normalized_course_program);
create index idx_official_study_plan_wishlist_program_demand
    on official_study_plan_wishlist (normalized_course_program, user_id)
    include (course_program);
