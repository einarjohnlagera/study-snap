package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminRegenerateSummariesResponse;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStudyPackServiceTest {
    private static final String ENRICHED_SUMMARY_MARKER = "|";

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private AdminStudyPackTransactionHelper transactionHelper;
    @Mock
    private RegenerationProgressTracker progressTracker;

    private AdminStudyPackService adminStudyPackService;

    @BeforeEach
    void setUp() {
        AsyncTaskExecutor directExecutor = Runnable::run;
        adminStudyPackService = new AdminStudyPackService(
                userRepository,
                studyPackRepository,
                transactionHelper,
                progressTracker,
                directExecutor
        );
    }

    @Test
    void regenerateOfficialSummaries_skipsEnrichedSummaries() {
        UserEntity admin = buildAdminUser();
        List<UUID> adminUserIds = List.of(admin.getId());
        when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of(admin));
        when(studyPackRepository.countByOwnerUserIdIn(adminUserIds)).thenReturn(1L);
        when(studyPackRepository.findByOwnerUserIdInAndSummaryNotEnriched(
                eq(adminUserIds),
                eq(ENRICHED_SUMMARY_MARKER)
        )).thenReturn(List.of());

        AdminRegenerateSummariesResponse response = adminStudyPackService.regenerateOfficialSummaries();

        assertThat(response.queued()).isZero();
        assertThat(response.skipped()).isEqualTo(1);
        verify(transactionHelper, never()).regenerateOnePack(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void regenerateOfficialSummaries_queuesUnenrichedPacks() {
        UserEntity admin = buildAdminUser();
        List<UUID> adminUserIds = List.of(admin.getId());
        StudyPackEntity pack = new StudyPackEntity();
        pack.setId(UUID.randomUUID());
        pack.setOwnerUserId(admin.getId());
        pack.setSummary("Plain summary");
        when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of(admin));
        when(studyPackRepository.countByOwnerUserIdIn(adminUserIds)).thenReturn(1L);
        when(studyPackRepository.findByOwnerUserIdInAndSummaryNotEnriched(
                eq(adminUserIds),
                eq(ENRICHED_SUMMARY_MARKER)
        )).thenReturn(List.of(pack));

        AdminRegenerateSummariesResponse response = adminStudyPackService.regenerateOfficialSummaries();

        assertThat(response.queued()).isEqualTo(1);
        assertThat(response.skipped()).isZero();
        verify(transactionHelper).regenerateOnePack(pack);
    }

    @Test
    void regenerateOfficialSummaries_returnsZero_whenNoAdminUsers() {
        when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of());

        AdminRegenerateSummariesResponse response = adminStudyPackService.regenerateOfficialSummaries();

        assertThat(response.queued()).isZero();
        assertThat(response.skipped()).isZero();
        verify(studyPackRepository, never()).countByOwnerUserIdIn(org.mockito.ArgumentMatchers.any());
        verify(transactionHelper, never()).regenerateOnePack(org.mockito.ArgumentMatchers.any());
    }

    private UserEntity buildAdminUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setRole(UserRole.ADMIN);
        return user;
    }
}
