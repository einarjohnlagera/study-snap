package com.studysnap.backend.service;

import com.studysnap.backend.dto.PublicProfileResponse;
import com.studysnap.backend.repository.PublicProfileMetricsRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({PublicProfileService.class, PublicProfileMetricsRepository.class, NoteCourseProgramRepository.class})
class PublicProfilePostgresIntegrationTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PublicProfileService service;

    @Test
    void fullTotalsAndFrontendPicksSurviveBoundedHydration() {
        UUID creator = user("profile-creator");
        UUID copier = user("profile-copier");
        jdbc.update("update users set public_profile_visible = true, display_name = 'Profile Creator' where id = ?", creator);

        List<MetricNote> all = new ArrayList<>();
        long expectedCopies = 0;
        long expectedShares = 0;
        long expectedViews = 0;
        for (int i = 0; i < 210; i++) {
            // Fifty-five identical titles and metrics make UUID ordering a false tie-breaker:
            // the frontend's stable sort preserves the old updated_at-desc profile order.
            UUID noteId = i < 55 ? new UUID(0, 10_000 + i) : UUID.randomUUID();
            String title = i < 55 ? "Tie Note" : i < 61 ? "alpha" : "Note %03d".formatted(i);
            int copies = i < 61 ? 10 : i == 209 ? 100 : i >= 207 ? 0 : i % 5;
            int views = i < 61 ? 1 : i == 207 ? 80 : i >= 208 ? 0 : i % 7;
            int shares = i < 61 ? 1 : i == 208 ? 90 : i >= 207 ? 0 : i % 9;
            note(noteId, creator, title, "PUBLIC", null, false);
            jdbc.update("update notes set subject = ?, course_program = ? where id = ?",
                    i < 160 ? "Math" : "Physics", i < 160 ? "Math" : "Physics", noteId);
            if (i < 55) {
                jdbc.update("update notes set updated_at = now() + (? * interval '1 second') where id = ?", i, noteId);
            }
            for (int copy = 0; copy < copies; copy++) {
                note(UUID.randomUUID(), copier, "Copy " + i + "-" + copy, "PRIVATE", noteId, true);
            }
            for (int view = 0; view < views; view++) {
                event(noteId, "PUBLIC_NOTE_VIEWED");
            }
            for (int share = 0; share < shares; share++) {
                event(noteId, "PUBLIC_NOTE_SHARED");
            }
            all.add(new MetricNote(noteId, title, copies, views, shares, i));
            expectedCopies += copies;
            expectedViews += views;
            expectedShares += shares;
        }
        // A private note must contribute neither to totals nor to the candidates.
        note(UUID.randomUUID(), creator, "Private extra", "PRIVATE", null, false);

        PublicProfileResponse byId = service.getByUserId(creator.toString(), null);
        PublicProfileResponse byUsername = service.getByUsername("profile-creator", null);
        var focus = service.getFocusByUserId(creator.toString(), null);
        assertThat(focus.subjects()).extracting("label", "count")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Math", 160L),
                        org.assertj.core.groups.Tuple.tuple("Physics", 50L));
        assertThat(focus.coursePrograms()).extracting("label", "count")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Math", 160L),
                        org.assertj.core.groups.Tuple.tuple("Physics", 50L));
        for (PublicProfileResponse profile : List.of(byId, byUsername)) {
            assertThat(profile.publicNotesCount()).isEqualTo(210);
            assertThat(profile.totalCopies()).isEqualTo(expectedCopies);
            assertThat(profile.totalShares()).isEqualTo(expectedShares);
            assertThat(profile.totalViews()).isEqualTo(expectedViews);
            assertThat(profile.publicNotes()).hasSizeLessThanOrEqualTo(200).hasSizeLessThan(210);
            // This fixture must kill a totals-from-candidates mutant, not merely have >50 notes.
            assertThat(profile.publicNotes().stream().mapToLong(note -> note.copyCount()).sum())
                    .isLessThan(expectedCopies);
            assertThat(profile.publicNotes().stream().mapToLong(note -> note.shareCount()).sum())
                    .isLessThan(expectedShares);
            assertThat(profile.publicNotes().stream().mapToLong(note -> note.viewCount()).sum())
                    .isLessThan(expectedViews);
            Set<UUID> hydrated = new HashSet<>(profile.publicNotes().stream()
                    .map(note -> UUID.fromString(note.noteId())).toList());
            Comparator<MetricNote> byTitle = Comparator.comparing(MetricNote::title, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Comparator.comparingInt(MetricNote::recency).reversed());
            Comparator<MetricNote> compound = Comparator.comparingInt(MetricNote::copies).reversed()
                    .thenComparing(Comparator.comparingInt(MetricNote::views).reversed())
                    .thenComparing(Comparator.comparingInt(MetricNote::shares).reversed())
                    .thenComparing(byTitle);
            assertThat(hydrated).containsAll(all.stream().sorted(compound).limit(8).map(MetricNote::id).toList());
            assertThat(hydrated).contains(
                    all.stream().sorted(Comparator.comparingInt(MetricNote::copies).reversed().thenComparing(byTitle)).findFirst().orElseThrow().id(),
                    all.stream().sorted(Comparator.comparingInt(MetricNote::views).reversed().thenComparing(byTitle)).findFirst().orElseThrow().id(),
                    all.stream().sorted(Comparator.comparingInt(MetricNote::shares).reversed().thenComparing(byTitle)).findFirst().orElseThrow().id());
        }
    }

    private UUID user(String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, username, password_hash, role, first_name, last_name, created_at, updated_at)"
                        + " values (?, ?, ?, 'x', 'USER', 'Test', 'User', now(), now())",
                id, username + "@example.test", username);
        return id;
    }

    private void note(UUID id, UUID owner, String title, String visibility, UUID source, boolean copiedFromPublic) {
        jdbc.update("insert into notes (id, owner_user_id, title, subject, content, visibility, tags,"
                        + " target_profile_type, status, copied_from_note_id, copied_from_public, created_at, updated_at)"
                        + " values (?, ?, ?, 'Biology', 'full content', ?, '{}', 'STUDENT', 'GENERATED', ?, ?, now(), now())",
                id, owner, title, visibility, source, copiedFromPublic);
    }

    private void event(UUID noteId, String type) {
        jdbc.update("insert into analytics_events (id, event_type, entity_id) values (?, ?, ?)",
                UUID.randomUUID(), type, noteId);
    }

    private record MetricNote(UUID id, String title, int copies, int views, int shares, int recency) {
    }
}
