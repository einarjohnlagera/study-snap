package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminRegenerateSummariesResponse;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AdminStudyPackService {
    private static final String ENRICHED_SUMMARY_MARKER = "|";

    private final UserRepository userRepository;
    private final StudyPackRepository studyPackRepository;
    private final AdminStudyPackTransactionHelper transactionHelper;
    @Qualifier("llmParallelTaskExecutor")
    private final AsyncTaskExecutor llmParallelTaskExecutor;

    public AdminRegenerateSummariesResponse regenerateOfficialSummaries() {
        List<UUID> adminUserIds = userRepository.findByRole(UserRole.ADMIN).stream()
                .map(UserEntity::getId)
                .toList();
        if (adminUserIds.isEmpty()) {
            return new AdminRegenerateSummariesResponse(0, 0);
        }

        int totalAdminPacks = Math.toIntExact(studyPackRepository.countByOwnerUserIdIn(adminUserIds));
        List<StudyPackEntity> packs = studyPackRepository.findByOwnerUserIdInAndSummaryNotEnriched(
                adminUserIds,
                ENRICHED_SUMMARY_MARKER
        );
        packs.forEach(pack -> CompletableFuture.runAsync(
                () -> transactionHelper.regenerateOnePack(pack),
                llmParallelTaskExecutor
        ));

        return new AdminRegenerateSummariesResponse(packs.size(), totalAdminPacks - packs.size());
    }
}
