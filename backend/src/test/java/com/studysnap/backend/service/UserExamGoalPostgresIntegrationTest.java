package com.studysnap.backend.service;

import com.studysnap.backend.dto.UpdateFocusSubjectsRequest;
import com.studysnap.backend.dto.UpdateStudyGoalRequest;
import com.studysnap.backend.controller.UserProfileController;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.dto.GoalNudgeResponse;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@Testcontainers
class UserExamGoalPostgresIntegrationTest {
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthService authService;
    @Autowired private AccountDataExportService accountDataExportService;
    @Autowired private PostSessionNextStepService postSessionNextStepService;
    @Autowired private UserRepository userRepository;

    @Test
    void realHibernateTransitionsRespectPartialIndexAndPreserveContent() {
        assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename = 'user_exam_goals'", String.class))
                .contains("ux_user_exam_goals_one_active");
        UUID user = user("multi-goal");
        UUID note = UUID.randomUUID();
        jdbc.update("insert into notes (id, owner_user_id, title, subject, content, visibility, status, target_profile_type) "
                + "values (?, ?, 'My note', 'Biology', 'Body', 'PRIVATE', 'DRAFT', 'STUDENT')", note, user);
        UUID collection = UUID.randomUUID();
        jdbc.update("insert into note_collections (id, owner_user_id, title, visibility) values (?, ?, 'My set', 'PRIVATE')",
                collection, user);
        UUID pack = UUID.randomUUID();
        jdbc.update("insert into study_packs (id, owner_user_id, note_id, input_type, title, summary, "
                        + "key_concepts, quiz, model_used, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'TEXT', 'Pack', 'Summary', '[]'::jsonb, '[]'::jsonb, 'test', 'DONE', now(), now())",
                pack, user, note);
        jdbc.update("insert into quick_review_sessions (id, user_id, study_pack_id, note_id, session_mode, "
                        + "status, current_round, current_question_index, total_questions, quota_exempt, "
                        + "verified_correct_answers, created_at, completed_at) "
                        + "values (?, ?, ?, ?, 'QUICK_REVIEW', 'COMPLETED', 'INITIAL', 0, 0, false, 0, now(), now())",
                UUID.randomUUID(), user, pack, note);
        int notesBefore = count("select count(*) from notes where owner_user_id = ?", user);
        int collectionsBefore = count("select count(*) from note_collections where owner_user_id = ?", user);
        int packsBefore = count("select count(*) from study_packs where owner_user_id = ?", user);
        int sessionsBefore = count("select count(*) from quick_review_sessions where user_id = ?", user);

        authService.addExamGoal(user, "ale");
        authService.addExamGoal(user, "pnle"); // INSERT must follow a flushed deactivation.
        assertThat(active(user)).isEqualTo("pnle");
        assertThat(count("select count(*) from user_exam_goals where user_id = ?", user)).isEqualTo(2);

        authService.activateExamGoal(user, "ale");
        assertThat(active(user)).isEqualTo("ale");
        authService.addExamGoal(user, "ale"); // Repeated add activates the existing row.
        assertThat(active(user)).isEqualTo("ale");
        assertThat(count("select count(*) from user_exam_goals where user_id = ?", user)).isEqualTo(2);

        authService.removeExamGoal(user, "ale"); // DELETE must flush before promotion.
        assertThat(active(user)).isEqualTo("pnle");
        authService.removeExamGoal(user, "pnle");
        assertThat(count("select count(*) from user_exam_goals where user_id = ? and is_active", user)).isZero();
        assertThat(count("select count(*) from notes where owner_user_id = ?", user)).isEqualTo(notesBefore);
        assertThat(count("select count(*) from note_collections where owner_user_id = ?", user)).isEqualTo(collectionsBefore);
        assertThat(count("select count(*) from study_packs where owner_user_id = ?", user)).isEqualTo(packsBefore);
        assertThat(count("select count(*) from quick_review_sessions where user_id = ?", user)).isEqualTo(sessionsBefore);
    }

    @Test
    void activeRemovalPromotesMostRecentlyAddedRemainingGoal() {
        UUID user = user("promotion");
        authService.addExamGoal(user, "ale");
        authService.addExamGoal(user, "pnle");
        authService.addExamGoal(user, "let");
        authService.removeExamGoal(user, "let");
        assertThat(active(user)).isEqualTo("pnle");
    }

    @Test
    void equalCreationTimesPromoteLowestId() {
        UUID user = user("promotion-tie");
        authService.addExamGoal(user, "ale");
        authService.addExamGoal(user, "pnle");
        authService.addExamGoal(user, "let");
        jdbc.update("update user_exam_goals set created_at = '2026-01-01T00:00:00Z' where user_id = ? and exam_goal_slug in ('ale', 'pnle')", user);
        String expected = jdbc.queryForObject("select exam_goal_slug from user_exam_goals where user_id = ? "
                + "and exam_goal_slug in ('ale', 'pnle') order by id asc limit 1", String.class, user);
        authService.removeExamGoal(user, "let");
        assertThat(active(user)).isEqualTo(expected);
    }

    @Test
    void focusExclusivityAndLegacyScalarBranches() {
        UUID user = user("focus");
        jdbc.update("update users set study_goal = 'some free text goal' where id = ?", user);
        authService.addExamGoal(user, "ale");
        assertThat(jdbc.queryForObject("select study_goal from users where id = ?", String.class, user)).isNull();
        authService.addExamGoal(user, "pnle");
        authService.updateFocusSubjects(user, new UpdateFocusSubjectsRequest(List.of("Biology")));
        assertThat(count("select count(*) from user_exam_goals where user_id = ?", user)).isZero();

        authService.updateStudyGoal(user, new UpdateStudyGoalRequest("ale"));
        assertThat(active(user)).isEqualTo("ale");
        assertThat(count("select cardinality(focus_subjects) from users where id = ?", user)).isZero();
        authService.updateStudyGoal(user, new UpdateStudyGoalRequest("custom subject focus"));
        assertThat(count("select count(*) from user_exam_goals where user_id = ?", user)).isZero();
        assertThat(jdbc.queryForObject("select study_goal from users where id = ?", String.class, user))
                .isEqualTo("custom subject focus");
        authService.updateStudyGoal(user, new UpdateStudyGoalRequest(null));
        assertThat(jdbc.queryForObject("select study_goal from users where id = ?", String.class, user)).isNull();
    }

    @Test
    void dataExportIncludesActiveAndInactiveGoals() {
        UUID user = user("export");
        authService.addExamGoal(user, "ale");
        authService.addExamGoal(user, "pnle");

        var account = accountDataExportService.exportForUser(user).account();
        assertThat(account.studyGoal()).isEqualTo("pnle");
        assertThat(account.examGoals()).extracting(com.studysnap.backend.dto.ExamGoalResponse::slug)
                .containsExactly("pnle", "ale");
    }

    @Test
    void oldClientBareSlugRequestAddsAndActivatesWithoutWritingScalar() throws Exception {
        UUID user = user("old-client");
        authService.addExamGoal(user, "ale");
        AuthenticatedUser principal = new AuthenticatedUser(user, UserRole.USER, true, 1);
        var mvc = standaloneSetup(new UserProfileController(authService))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory factory) {
                        return principal;
                    }
                }).build();

        mvc.perform(put("/users/profile/goal").contentType(MediaType.APPLICATION_JSON)
                .content("{\"studyGoal\":\"pnle\"}"))
                .andExpect(status().isOk());
        assertThat(active(user)).isEqualTo("pnle");
        assertThat(count("select count(*) from user_exam_goals where user_id = ?", user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select study_goal from users where id = ?", String.class, user)).isNull();
    }

    @Test
    void unadoptedGoalCannotBeActivatedOrRemoved() {
        UUID user = user("unadopted");
        assertThatThrownBy(() -> authService.activateExamGoal(user, "ale"))
                .isInstanceOf(com.studysnap.backend.exception.AppException.class);
        assertThatThrownBy(() -> authService.removeExamGoal(user, "ale"))
                .isInstanceOf(com.studysnap.backend.exception.AppException.class);
    }

    @Test
    void completedNotesRealProgramRedirectsNudgeAndUnrelatedNoteUsesActiveGoal() {
        UUID user = user("session-context");
        authService.addExamGoal(user, "pnle");
        authService.addExamGoal(user, "ale");
        UUID note = UUID.randomUUID();
        jdbc.update("insert into notes (id, owner_user_id, title, subject, course_program, content, visibility, "
                + "status, target_profile_type) values (?, ?, 'Session note', 'Biology', 'Nursing', 'Body', "
                + "'PRIVATE', 'DRAFT', 'STUDENT')", note, user);
        StudyPackEntity pack = new StudyPackEntity();
        pack.setId(UUID.randomUUID());
        pack.setNoteId(note);
        var learner = userRepository.findById(user).orElseThrow();

        GoalNudgeResponse redirected = ReflectionTestUtils.invokeMethod(
                postSessionNextStepService, "resolveGoalNudge", learner, pack);
        assertThat(redirected).isNotNull();
        assertThat(redirected.studyGoal()).isEqualTo("pnle");

        jdbc.update("update notes set course_program = 'Unrelated Program' where id = ?", note);
        GoalNudgeResponse fallback = ReflectionTestUtils.invokeMethod(
                postSessionNextStepService, "resolveGoalNudge", learner, pack);
        assertThat(fallback).isNotNull();
        assertThat(fallback.studyGoal()).isEqualTo("ale");
    }

    private UUID user(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, first_name, username, profile_type) values (?, ?, 'Tester', ?, 'STUDENT')",
                id, prefix + id + "@example.com", prefix + id.toString().substring(0, 8));
        return id;
    }

    private String active(UUID user) {
        return jdbc.queryForObject("select exam_goal_slug from user_exam_goals where user_id = ? and is_active", String.class, user);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
