package com.studysnap.backend.service;

import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamGoalCourseProgramProviderTest {
    private static final String ALE = "ale";
    private static final String PNLE = "pnle";
    private static final String LET = "let";
    private static final String CPALE = "cpale";
    private static final String ARCHITECTURE = "Architecture";
    private static final String NURSING = "Nursing";
    private static final String EDUCATION = "Education";
    private static final String ACCOUNTANCY = "Accountancy";
    // Stands in for a row a curator adds after boot: it must become visible without a redeploy.
    private static final String CURATOR_ADDED_PROGRAM = "Midwifery";

    @Mock
    private CourseProgramCatalogRepository courseProgramCatalogRepository;

    @Test
    void getCoursePrograms_returnsCatalogValuesForEveryExamGoal() {
        when(courseProgramCatalogRepository.findNamesByExamGoalSlug(ALE)).thenReturn(List.of(ARCHITECTURE));
        when(courseProgramCatalogRepository.findNamesByExamGoalSlug(PNLE)).thenReturn(List.of(NURSING));
        when(courseProgramCatalogRepository.findNamesByExamGoalSlug(LET)).thenReturn(List.of(EDUCATION));
        when(courseProgramCatalogRepository.findNamesByExamGoalSlug(CPALE)).thenReturn(List.of(ACCOUNTANCY));
        ExamGoalCourseProgramProvider provider = new ExamGoalCourseProgramProvider(courseProgramCatalogRepository);

        assertThat(provider.getCoursePrograms(ALE)).containsExactly(ARCHITECTURE);
        assertThat(provider.getCoursePrograms(PNLE)).containsExactly(NURSING);
        assertThat(provider.getCoursePrograms(LET)).containsExactly(EDUCATION);
        assertThat(provider.getCoursePrograms(CPALE)).containsExactly(ACCOUNTANCY);
    }

    @Test
    void getCoursePrograms_cachesCatalogLookup() {
        when(courseProgramCatalogRepository.findNamesByExamGoalSlug(PNLE)).thenReturn(List.of(NURSING));
        ExamGoalCourseProgramProvider provider = new ExamGoalCourseProgramProvider(courseProgramCatalogRepository);

        assertThat(provider.getCoursePrograms("PNLE")).containsExactly(NURSING);
        assertThat(provider.getCoursePrograms(" pnle ")).containsExactly(NURSING);

        verify(courseProgramCatalogRepository).findNamesByExamGoalSlug(PNLE);
    }

    @Test
    void getCoursePrograms_failsOpenToLiteralValues() {
        when(courseProgramCatalogRepository.findNamesByExamGoalSlug(PNLE))
                .thenThrow(new IllegalStateException("catalog unavailable"));
        ExamGoalCourseProgramProvider provider = new ExamGoalCourseProgramProvider(courseProgramCatalogRepository);

        assertThat(provider.getCoursePrograms(PNLE)).containsExactly(NURSING);
    }

    @Test
    void getCoursePrograms_fallsBackWhenCatalogHasNoRows() {
        when(courseProgramCatalogRepository.findNamesByExamGoalSlug(PNLE)).thenReturn(List.of());
        ExamGoalCourseProgramProvider provider = new ExamGoalCourseProgramProvider(courseProgramCatalogRepository);

        assertThat(provider.getCoursePrograms(PNLE)).containsExactly(NURSING);
    }

    @Test
    void getCoursePrograms_recoversAfterTransientCatalogFailure() {
        when(courseProgramCatalogRepository.findNamesByExamGoalSlug(PNLE))
                .thenThrow(new IllegalStateException("catalog unavailable"))
                .thenReturn(List.of(NURSING, CURATOR_ADDED_PROGRAM));
        ExamGoalCourseProgramProvider provider = new ExamGoalCourseProgramProvider(courseProgramCatalogRepository);

        assertThat(provider.getCoursePrograms(PNLE)).containsExactly(NURSING);
        assertThat(provider.getCoursePrograms(PNLE)).containsExactly(NURSING, CURATOR_ADDED_PROGRAM);
    }

    @Test
    void getCoursePrograms_recoversAfterEmptyCatalogRead() {
        when(courseProgramCatalogRepository.findNamesByExamGoalSlug(PNLE))
                .thenReturn(List.of())
                .thenReturn(List.of(NURSING, CURATOR_ADDED_PROGRAM));
        ExamGoalCourseProgramProvider provider = new ExamGoalCourseProgramProvider(courseProgramCatalogRepository);

        assertThat(provider.getCoursePrograms(PNLE)).containsExactly(NURSING);
        assertThat(provider.getCoursePrograms(PNLE)).containsExactly(NURSING, CURATOR_ADDED_PROGRAM);
    }
}
