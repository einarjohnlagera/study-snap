package com.studysnap.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the one thing no test covered before, which is exactly why this bug could exist:
 * <strong>whether Spring Security lets these paths through at all.</strong>
 *
 * <p>{@code SubjectControllerTest} and {@code CourseProgramControllerTest} already prove the controllers
 * serve {@code scope=public} anonymously and reject {@code scope=mine}. Both were correct and tested.
 * But neither path was ever listed in {@code SecurityConfig}, which ends {@code
 * .anyRequest().authenticated()} — so the 401 was issued before any controller ran, and
 * {@code PublicLibraryPageClient} swallowed it into an empty filter chip list. Correct, tested
 * controller logic sitting behind a config that never granted it.
 *
 * <p><strong>These tests assert reachability, not response bodies, and that is deliberate.</strong> This
 * {@code @SpringBootTest} context has no schema (Flyway is disabled in tests), so a permitted request
 * reaches the handler and then fails on {@code Table "NOTES" not found}. Asserting 200 here would mean
 * hand-rolling a schema to re-test query behaviour that is already covered elsewhere. What changed is
 * the permit rule, so what is asserted is the permit rule: not rejected by security for
 * {@code scope=public}, still rejected for {@code scope=mine}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicFacetAnonymousAccessSecurityIntegrationTest {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousReachesThePublicSubjectFacet() throws Exception {
        assertReachedHandler("/subjects?scope=public");
        assertReachedHandler("/subjects");
    }

    @Test
    void anonymousReachesThePublicCourseProgramFacet() throws Exception {
        assertReachedHandler("/course-programs?scope=public");
        assertReachedHandler("/course-programs");
    }

    @Test
    void anonymousStillCannotReadTheirOwnSubjects() throws Exception {
        // The permit rule opens the path; the controller's own gate is what keeps a caller's notes
        // private. This is the assertion that proves the widening did not overreach.
        mockMvc.perform(get("/subjects?scope=mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousStillCannotReadTheirOwnCoursePrograms() throws Exception {
        mockMvc.perform(get("/course-programs?scope=mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void theWidenedPathsAreGetOnly() throws Exception {
        // The permit rules are GET-scoped on purpose, so a write verb must not inherit the exemption.
        mockMvc.perform(post("/subjects")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/course-programs")).andExpect(status().isUnauthorized());
    }

    /**
     * Asserts the request was not turned away by Spring Security. A 500 from the handler counts as
     * reaching it — see the class comment on why 200 is not the assertion here.
     */
    private void assertReachedHandler(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
        assertThat(status)
                .as("%s must not be rejected by Spring Security for an anonymous caller", path)
                .isNotIn(UNAUTHORIZED, FORBIDDEN);
    }
}
