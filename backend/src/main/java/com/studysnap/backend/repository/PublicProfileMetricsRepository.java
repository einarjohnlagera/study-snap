package com.studysnap.backend.repository;

import com.studysnap.backend.dto.PublicProfileFocusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Counts only note IDs and metric rows; full note content and Study Pack JSON stay out of ranking. */
@Repository
@RequiredArgsConstructor
public class PublicProfileMetricsRepository {
    public static final int CANDIDATES_PER_RANKING = 50;

    private static final String METRICS = """
            with public_notes as (
                select id, title, updated_at from notes where owner_user_id = ? and visibility = 'PUBLIC'
            ), copies as (
                select copied_from_note_id as note_id, count(*) as copy_count
                from notes where copied_from_public = true
                  and copied_from_note_id in (select id from public_notes)
                group by copied_from_note_id
            ), events as (
                select entity_id as note_id,
                       count(*) filter (where event_type = 'PUBLIC_NOTE_SHARED') as share_count,
                       count(*) filter (where event_type = 'PUBLIC_NOTE_VIEWED') as view_count
                from analytics_events
                where event_type in ('PUBLIC_NOTE_SHARED', 'PUBLIC_NOTE_VIEWED')
                  and entity_id in (select id from public_notes)
                group by entity_id
            ), metrics as (
                select n.id, n.title, n.updated_at,
                       coalesce(c.copy_count, 0) as copy_count,
                       coalesce(e.share_count, 0) as share_count,
                       coalesce(e.view_count, 0) as view_count
                from public_notes n
                left join copies c on c.note_id = n.id
                left join events e on e.note_id = n.id
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public Totals totals(UUID ownerId) {
        return jdbcTemplate.queryForObject(METRICS + """
                select coalesce(sum(copy_count), 0) as copies,
                       coalesce(sum(share_count), 0) as shares,
                       coalesce(sum(view_count), 0) as views
                from metrics
                """, (rs, row) -> new Totals(rs.getLong("copies"), rs.getLong("shares"), rs.getLong("views")), ownerId);
    }

    public List<UUID> candidateNoteIds(UUID ownerId) {
        // The fourth ranking pins the frontend's compound top eight even when many notes tie
        // on copies. The other three preserve the individual featured-note choices.
        return jdbcTemplate.query(METRICS + """
                , ranked as (
                    select id,
                           row_number() over (order by copy_count desc, title collate "und-x-icu" asc nulls first,
                                                updated_at desc, id) as copy_rank,
                           row_number() over (order by view_count desc, title collate "und-x-icu" asc nulls first,
                                                updated_at desc, id) as view_rank,
                           row_number() over (order by share_count desc, title collate "und-x-icu" asc nulls first,
                                                updated_at desc, id) as share_rank,
                           row_number() over (order by copy_count desc, view_count desc,
                                                share_count desc, title collate "und-x-icu" asc nulls first,
                                                updated_at desc, id) as compound_rank
                    from metrics
                )
                select id from ranked
                where copy_rank <= ? or view_rank <= ? or share_rank <= ? or compound_rank <= ?
                """, (rs, row) -> rs.getObject("id", UUID.class), ownerId,
                CANDIDATES_PER_RANKING, CANDIDATES_PER_RANKING,
                CANDIDATES_PER_RANKING, CANDIDATES_PER_RANKING);
    }

    public PublicProfileFocusResponse focus(UUID ownerId) {
        return new PublicProfileFocusResponse(
                labelCounts(ownerId, "course_program"),
                labelCounts(ownerId, "subject"));
    }

    private List<PublicProfileFocusResponse.LabelCount> labelCounts(UUID ownerId, String column) {
        // Column names are this class's two fixed literals, never client input.
        return jdbcTemplate.query("select " + column + " as label, count(*) as note_count from notes"
                        + " where owner_user_id = ? and visibility = 'PUBLIC' and " + column
                        + " is not null and trim(" + column + ") <> '' group by " + column,
                (rs, row) -> new PublicProfileFocusResponse.LabelCount(
                        rs.getString("label"), rs.getLong("note_count")), ownerId);
    }

    public record Totals(long copies, long shares, long views) {
    }
}
