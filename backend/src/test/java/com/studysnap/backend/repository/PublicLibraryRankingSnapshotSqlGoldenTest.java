package com.studysnap.backend.repository;

import com.studysnap.backend.model.PublicLibrarySort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PublicLibraryRankingSnapshotSqlGoldenTest {
    @Autowired private PublicLibraryRepositoryImpl repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void h2ExecutesIdFirstPageAndMapsCorrelatedProgramsArray() {
        jdbcTemplate.execute("create table notes (id uuid primary key, owner_user_id uuid, title varchar(255),"
                + " course_program varchar(120), domain_context varchar(120), learner_level varchar(120),"
                + " subject varchar(120), tags varchar array, content clob, status varchar(40),"
                + " visibility varchar(40), created_at timestamp with time zone,"
                + " updated_at timestamp with time zone, copied_from_note_id uuid, copied_from_public boolean)");
        jdbcTemplate.execute("create table course_programs (id uuid primary key, name varchar(120))");
        jdbcTemplate.execute("create table note_course_program (id uuid primary key, note_id uuid,"
                + " course_program_id uuid)");
        UUID owner = UUID.randomUUID();
        UUID note = UUID.randomUUID();
        UUID noProgram = UUID.randomUUID();
        UUID program = UUID.randomUUID();
        jdbcTemplate.update("insert into notes (id, owner_user_id, title, content, visibility, tags,"
                        + " created_at, updated_at) values (?, ?, 'H2 projection', 'body',"
                        + " 'PUBLIC', ARRAY['tag'], current_timestamp, current_timestamp)", note, owner);
        jdbcTemplate.update("insert into notes (id, owner_user_id, title, content, visibility, tags,"
                        + " created_at, updated_at) values (?, ?, 'No program', 'body',"
                        + " 'PUBLIC', ARRAY[], current_timestamp, current_timestamp)",
                noProgram, owner);
        jdbcTemplate.update("insert into course_programs (id, name) values (?, 'Nursing')", program);
        jdbcTemplate.update("insert into note_course_program (id, note_id, course_program_id) values (?, ?, ?)",
                UUID.randomUUID(), note, program);
        var criteria = new PublicLibraryFilterCriteria(null, null, null, null,
                List.of(), null, null, null, false, List.of());
        for (PublicLibrarySort sort : List.of(PublicLibrarySort.RECENT, PublicLibrarySort.TITLE)) {
            var rows = repository.findPublicLibraryPage(criteria, sort, 0, 20);
            assertThat(rows).extracting(row -> row.getId()).contains(note);
            assertThat(rows.stream().filter(row -> row.getId().equals(note)).findFirst().orElseThrow()
                    .getApplicablePrograms()).containsExactly("Nursing");
            assertThat(rows.stream().filter(row -> row.getId().equals(noProgram)).findFirst().orElseThrow()
                    .getApplicablePrograms()).isEmpty();
            assertThat(repository.findPublicLibraryPage(criteria, sort, 10000, 20)).isEmpty();
        }
        assertThat(repository.findPublicLibraryListItemProjectionsByIdIn(List.of(note)).getFirst()
                .getApplicablePrograms()).containsExactly("Nursing");
    }

    @Test
    void snapshotSqlHasIndependentGoldensForAllSixSorts() throws Exception {
        var criteria = new PublicLibraryFilterCriteria(null, null, null, null,
                List.of("fixture-tag"), null, null, null, false, List.of());
        var snapshot = new PublicLibraryRankingSnapshot(1, OffsetDateTime.now(), Map.of(),
                Map.of(), Map.of(), Map.of("fixture-tag", Set.of(UUID.randomUUID())), 1, 1);
        for (PublicLibrarySort sort : List.of(PublicLibrarySort.RECOMMENDED,
                PublicLibrarySort.FEATURED, PublicLibrarySort.POPULAR, PublicLibrarySort.COPIED,
                PublicLibrarySort.VIEWS, PublicLibrarySort.MOST_COPIED)) {
            assertThat(repository.snapshotPageSql(criteria, sort, snapshot))
                    .isEqualTo(golden(sort, "page"));
            assertThat(repository.snapshotCountSql(criteria, sort, snapshot))
                    .isEqualTo(golden(sort, "count"));
        }
    }

    private String golden(PublicLibrarySort sort, String kind) throws Exception {
        String path = "/sql/public-library-ranking-snapshot/" + sort.name() + "." + kind + ".sql";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
