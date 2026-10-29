select count(*) from notes n  left join study_packs sp on sp.note_id = n.id  where n.visibility = 'PUBLIC' and n.id = any(:rankTagIds::uuid[]) and (
    n.status = 'GENERATED'
    or (
        (n.status is null or n.status = 'DRAFT')
        and exists (select 1 from study_packs ready_sp where ready_sp.note_id = n.id)
    )
) and (sp.summary is not null and trim(sp.summary) <> '') and (n.content is not null and trim(n.content) <> '') and case when sp.quiz is null or trim(cast(sp.quiz as varchar)) in ('', '[]', 'null') then 0 else 1 end > 0 