package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdoptGoalResponse;
import com.studysnap.backend.dto.GoalAdoptionStatusResponse;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.repository.GoalAdoptionJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@Testcontainers
class GoalAdoptionPostgresIntegrationTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private NoteCollectionService service;
    @Autowired private GoalAdoptionRecoveryService recoveryService;
    @Autowired private GoalAdoptionJobRepository jobs;
    @MockitoBean private GoalAdoptionDispatcher dispatcher;

    @Test
    void everyChildCommitsSeparatelyAndCompletionIsDurable() {
        Fixture fixture = seed(6, 3);
        AdoptGoalResponse started = service.adoptGoal(fixture.sourceGoal(), fixture.learner());

        assertThat(started.status()).isEqualTo("STARTED");
        assertThat(started.totalSubjectCount()).isEqualTo(6);
        assertThat(count("select count(*) from note_collections where owner_user_id = ? and parent_collection_id = ?",
                fixture.learner(), started.collectionId())).isZero();

        // Probe real PostgreSQL transaction IDs on Subject inserts. A Goal-wide transaction would
        // collapse these to one value and kill this test even if the final child count still matched.
        jdbc.execute("create table goal_adoption_tx_probe (collection_id uuid, transaction_id bigint)");
        jdbc.execute("create function goal_adoption_probe() returns trigger language plpgsql as $$ begin "
                + "if NEW.source_plan_id is not null then "
                + "insert into goal_adoption_tx_probe values (NEW.id, txid_current()); end if; return NEW; end $$");
        jdbc.execute("create trigger goal_adoption_probe_trigger after insert on note_collections "
                + "for each row execute function goal_adoption_probe()");

        service.runGoalAdoption(started.jobId());

        GoalAdoptionStatusResponse done = service.getGoalAdoptionStatus(started.collectionId(), fixture.learner());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.adoptedSubjectCount()).isEqualTo(6);
        assertThat(done.totalNotesCopied()).isEqualTo(18);
        assertThat(count("select count(distinct transaction_id) from goal_adoption_tx_probe")).isEqualTo(6);
        assertThat(count("select count(*) from analytics_events where id = ?", started.jobId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select companion_structure_snapshot is not null from note_collections where id = ?",
                Boolean.class, started.collectionId())).isTrue();
        assertThat(jdbc.queryForObject("select primary_collection_id from users where id = ?",
                UUID.class, fixture.learner())).isEqualTo(started.collectionId());
        assertThatThrownBy(() -> service.getGoalAdoptionStatus(started.collectionId(), fixture.stranger()))
                .isInstanceOf(CollectionNotFoundException.class);
        service.runGoalAdoption(started.jobId());
        assertThat(count("select count(*) from analytics_events where id = ?", started.jobId())).isEqualTo(1);
    }

    @Test
    void staleRunningJobResumesFromCommittedChildWithoutDuplicateNotes() {
        Fixture fixture = seed(3, 2);
        AdoptGoalResponse started = service.adoptGoal(fixture.sourceGoal(), fixture.learner());
        UUID firstChild = fixture.children().getFirst();
        UUID personalChild = service.adopt(firstChild, fixture.learner()).collectionId();
        jdbc.update("update note_collections set parent_collection_id = ?, sibling_position = 0 where id = ?",
                started.collectionId(), personalChild);
        jdbc.update("update goal_adoption_jobs set status = 'RUNNING', processed_subject_count = 1, "
                        + "adopted_subject_count = 1, total_notes_copied = 2, updated_at = now() - interval '20 minutes' "
                        + "where id = ?", started.jobId());

        recoveryService.reenqueueRecoverableJobs();
        verify(dispatcher, org.mockito.Mockito.atLeastOnce()).dispatch(eq(started.jobId()), any());
        service.runGoalAdoption(started.jobId());

        GoalAdoptionStatusResponse done = service.getGoalAdoptionStatus(started.collectionId(), fixture.learner());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.adoptedSubjectCount()).isEqualTo(3);
        assertThat(done.totalNotesCopied()).isEqualTo(6);
        assertThat(count("select count(*) from note_collections where owner_user_id = ? and parent_collection_id = ?",
                fixture.learner(), started.collectionId())).isEqualTo(3);
        assertThat(count("select count(*) from notes where owner_user_id = ? and copied_from_note_id is not null",
                fixture.learner())).isEqualTo(6);
        assertThat(count("select count(*) from analytics_events where id = ?", started.jobId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select companion_structure_snapshot is not null from note_collections where id = ?",
                Boolean.class, started.collectionId())).isTrue();
        assertThat(jdbc.queryForObject("select primary_collection_id from users where id = ?",
                UUID.class, fixture.learner())).isEqualTo(started.collectionId());
        assertThat(service.adoptGoal(fixture.sourceGoal(), fixture.learner()).jobId()).isEqualTo(started.jobId());
    }

    @Test
    void secondCallReturnsRunningJobAndFailedRetryBecomesPendingBeforePolling() {
        Fixture fixture = seed(1, 1);
        AdoptGoalResponse started = service.adoptGoal(fixture.sourceGoal(), fixture.learner());
        jdbc.update("update goal_adoption_jobs set status = 'RUNNING', updated_at = now() where id = ?",
                started.jobId());

        AdoptGoalResponse running = service.adoptGoal(fixture.sourceGoal(), fixture.learner());
        assertThat(running.jobId()).isEqualTo(started.jobId());
        assertThat(running.status()).isEqualTo("RUNNING");
        verify(dispatcher, times(1)).dispatch(eq(started.jobId()), any());

        jdbc.update("update goal_adoption_jobs set status = 'FAILED' where id = ?", started.jobId());
        AdoptGoalResponse retried = service.adoptGoal(fixture.sourceGoal(), fixture.learner());
        assertThat(retried.status()).isEqualTo("STARTED");
        assertThat(service.getGoalAdoptionStatus(started.collectionId(), fixture.learner()).status())
                .isEqualTo("PENDING");
        verify(dispatcher, times(2)).dispatch(eq(started.jobId()), any());
        service.runGoalAdoption(started.jobId());
        assertThat(service.getGoalAdoptionStatus(started.collectionId(), fixture.learner()).status())
                .isEqualTo("COMPLETED");
    }

    @Test
    void notePersistenceFailureRollsBackItsAttemptAndIsCountedAsSkipped() {
        Fixture fixture = seed(1, 2);
        UUID badNote = jdbc.queryForObject(
                "select note_id from note_collection_items where collection_id = ? and position = 0",
                UUID.class, fixture.children().getFirst());
        jdbc.execute("create function reject_bad_goal_note_copy() returns trigger language plpgsql as $$ begin "
                + "if NEW.copied_from_note_id = '" + badNote + "'::uuid then "
                + "raise exception 'fixture copy failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger reject_bad_goal_note_copy_trigger before insert on notes "
                + "for each row execute function reject_bad_goal_note_copy()");
        AdoptGoalResponse started = service.adoptGoal(fixture.sourceGoal(), fixture.learner());

        service.runGoalAdoption(started.jobId());

        GoalAdoptionStatusResponse done = service.getGoalAdoptionStatus(started.collectionId(), fixture.learner());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.adoptedSubjectCount()).isEqualTo(1);
        assertThat(done.totalNotesCopied()).isEqualTo(1);
        assertThat(done.totalNotesSkipped()).isEqualTo(1);
        assertThat(count("select count(*) from notes where owner_user_id = ? and copied_from_note_id is not null",
                fixture.learner())).isEqualTo(1);
        assertThat(count("select count(*) from analytics_events where id = ?", started.jobId())).isEqualTo(1);
    }

    @Test
    void stampedPrivateChildIsAdoptedTheSameAsAPublicOne() {
        // The publication stamp, not visibility, is the boundary inside an adopted Goal (the public
        // standalone adopt() route requires PUBLIC; adoptGoal's children are not standalone adopts).
        // A regression here would silently re-introduce the defect v0.161.0 fixed: a stamped-but-
        // PRIVATE Subject Plan throwing after the Goal was already persisted.
        UUID creator = user("creator");
        UUID learner = user("learner");
        UUID goal = UUID.randomUUID();
        jdbc.update("insert into note_collections (id, owner_user_id, title, visibility, published_at, companion) "
                        + "values (?, ?, 'Goal', 'PUBLIC', now(), ?::jsonb)", goal, creator,
                "{\"overview\":\"Plan\",\"studyStrategy\":\"Study\",\"commonMistakes\":\"None\","
                        + "\"resources\":\"\",\"faq\":[],\"mentorTips\":[]}");
        UUID privateChild = UUID.randomUUID();
        jdbc.update("insert into note_collections (id, owner_user_id, title, visibility, parent_collection_id, "
                        + "sibling_position, published_at) values (?, ?, 'Private Subject', 'PRIVATE', ?, 0, now())",
                privateChild, creator, goal);
        UUID note = UUID.randomUUID();
        jdbc.update("insert into notes (id, owner_user_id, title, subject, content, visibility, status, "
                        + "target_profile_type) values (?, ?, 'Note', 'Biology', 'Content', 'PUBLIC', 'DRAFT', 'STUDENT')",
                note, creator);
        jdbc.update("insert into note_collection_items (id, collection_id, note_id, position, published_at) "
                        + "values (?, ?, ?, 0, now())", UUID.randomUUID(), privateChild, note);

        AdoptGoalResponse started = service.adoptGoal(goal, learner);
        assertThat(started.totalSubjectCount()).isEqualTo(1);
        service.runGoalAdoption(started.jobId());

        GoalAdoptionStatusResponse done = service.getGoalAdoptionStatus(started.collectionId(), learner);
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.adoptedSubjectCount()).isEqualTo(1);
        assertThat(done.totalNotesCopied()).isEqualTo(1);
        assertThat(count("select count(*) from note_collections where owner_user_id = ? and parent_collection_id = ?",
                learner, started.collectionId())).isEqualTo(1);
        assertThat(count("select count(*) from notes where owner_user_id = ? and copied_from_note_id = ?",
                learner, note)).isEqualTo(1);
    }

    private Fixture seed(int subjectCount, int notesPerSubject) {
        UUID creator = user("creator");
        UUID learner = user("learner");
        UUID stranger = user("stranger");
        UUID goal = UUID.randomUUID();
        jdbc.update("insert into note_collections (id, owner_user_id, title, visibility, published_at, companion) "
                + "values (?, ?, 'Goal', 'PUBLIC', now(), ?::jsonb)", goal, creator,
                "{\"overview\":\"Plan\",\"studyStrategy\":\"Study\",\"commonMistakes\":\"None\","
                        + "\"resources\":\"\",\"faq\":[],\"mentorTips\":[]}");
        List<UUID> children = new ArrayList<>();
        for (int index = 0; index < subjectCount; index++) {
            UUID child = UUID.randomUUID();
            children.add(child);
            jdbc.update("insert into note_collections (id, owner_user_id, title, visibility, parent_collection_id, "
                    + "sibling_position, published_at) values (?, ?, ?, 'PUBLIC', ?, ?, now())",
                    child, creator, "Subject " + index, goal, index);
            for (int noteIndex = 0; noteIndex < notesPerSubject; noteIndex++) {
                UUID note = UUID.randomUUID();
                jdbc.update("insert into notes (id, owner_user_id, title, subject, content, visibility, status, "
                                + "target_profile_type) values (?, ?, ?, 'Biology', 'Content', 'PUBLIC', 'DRAFT', 'STUDENT')",
                        note, creator, "Note " + index + "/" + noteIndex);
                jdbc.update("insert into note_collection_items (id, collection_id, note_id, position, published_at) "
                        + "values (?, ?, ?, ?, now())", UUID.randomUUID(), child, note, noteIndex);
            }
        }
        return new Fixture(goal, learner, stranger, children);
    }

    private UUID user(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, first_name, username, profile_type) values (?, ?, 'Tester', ?, 'STUDENT')",
                id, prefix + id + "@example.com", prefix + id.toString().substring(0, 8));
        return id;
    }

    private int count(String query, Object... args) {
        Integer value = jdbc.queryForObject(query, Integer.class, args);
        return value == null ? 0 : value;
    }

    private record Fixture(UUID sourceGoal, UUID learner, UUID stranger, List<UUID> children) { }
}
