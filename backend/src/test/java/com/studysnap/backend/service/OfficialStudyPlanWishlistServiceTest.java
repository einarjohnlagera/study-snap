package com.studysnap.backend.service;

import com.studysnap.backend.repository.OfficialStudyPlanWishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfficialStudyPlanWishlistServiceTest {
    private static final String NURSING = "Nursing";
    private static final String NORMALIZED_NURSING = "nursing";
    private static final String ACCOUNTANCY = "Accountancy";
    private static final String NORMALIZED_ACCOUNTANCY = "accountancy";

    @Mock
    private OfficialStudyPlanWishlistRepository wishlistRepository;

    private OfficialStudyPlanWishlistService wishlistService;

    @BeforeEach
    void setUp() {
        wishlistService = new OfficialStudyPlanWishlistService(wishlistRepository);
    }

    @Test
    void requestPlan_persistsFirstRequest() {
        UUID userId = UUID.randomUUID();
        when(wishlistRepository.existsByUserIdAndNormalizedCourseProgram(userId, NORMALIZED_NURSING))
                .thenReturn(false);

        boolean requested = wishlistService.requestPlan(userId, " " + NURSING + " ");

        assertThat(requested).isTrue();
        verify(wishlistRepository).insertIfAbsent(
                any(UUID.class),
                eq(userId),
                eq(NURSING),
                eq(NORMALIZED_NURSING),
                any()
        );
    }

    @Test
    void requestPlan_secondNormalizedRequestIsSuccessfulNoOp() {
        UUID userId = UUID.randomUUID();
        when(wishlistRepository.existsByUserIdAndNormalizedCourseProgram(userId, NORMALIZED_NURSING))
                .thenReturn(true);

        boolean requested = wishlistService.requestPlan(userId, " " + NORMALIZED_NURSING + " ");

        assertThat(requested).isTrue();
        verify(wishlistRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    void requestPlan_allowsDifferentProgramsForOneLearner() {
        UUID userId = UUID.randomUUID();
        when(wishlistRepository.existsByUserIdAndNormalizedCourseProgram(userId, NORMALIZED_NURSING))
                .thenReturn(false);
        when(wishlistRepository.existsByUserIdAndNormalizedCourseProgram(userId, NORMALIZED_ACCOUNTANCY))
                .thenReturn(false);

        wishlistService.requestPlan(userId, NURSING);
        wishlistService.requestPlan(userId, ACCOUNTANCY);

        verify(wishlistRepository).insertIfAbsent(any(), eq(userId), eq(NURSING), eq(NORMALIZED_NURSING), any());
        verify(wishlistRepository).insertIfAbsent(any(), eq(userId), eq(ACCOUNTANCY), eq(NORMALIZED_ACCOUNTANCY), any());
    }

    @Test
    void requestPlan_caseAndWhitespaceVariantsUseOneUniquenessKey() {
        UUID userId = UUID.randomUUID();
        when(wishlistRepository.existsByUserIdAndNormalizedCourseProgram(userId, NORMALIZED_NURSING))
                .thenReturn(false, true, true);

        List.of(NURSING, NORMALIZED_NURSING, " " + NURSING + " ")
                .forEach(program -> wishlistService.requestPlan(userId, program));

        verify(wishlistRepository).insertIfAbsent(any(), eq(userId), eq(NURSING), eq(NORMALIZED_NURSING), any());
        verify(wishlistRepository, org.mockito.Mockito.times(3))
                .existsByUserIdAndNormalizedCourseProgram(userId, NORMALIZED_NURSING);
    }

    @Test
    void requestPlan_keepsExistenceCheckAndWriteInOneTransaction() throws NoSuchMethodException {
        Method method = OfficialStudyPlanWishlistService.class.getMethod("requestPlan", UUID.class, String.class);

        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }
}
