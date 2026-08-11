package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreateCourseProgramCatalogRequest;
import com.studysnap.backend.service.CourseProgramCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CourseProgramCatalogControllerTest {
    @Test
    void createAndSimilarEndpointsAreAdminOnly() throws NoSuchMethodException {
        Method create = CourseProgramCatalogController.class.getMethod("create", CreateCourseProgramCatalogRequest.class);
        Method similar = CourseProgramCatalogController.class.getMethod("similar", String.class);

        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(similar.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(new CourseProgramCatalogController(mock(CourseProgramCatalogService.class))).isNotNull();
    }
}
