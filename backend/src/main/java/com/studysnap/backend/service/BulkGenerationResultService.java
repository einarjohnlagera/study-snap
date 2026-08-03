package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerationResultResponse;
import com.studysnap.backend.entity.BulkGenerationResultEntity;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.BulkGenerationResultNotFoundException;
import com.studysnap.backend.repository.BulkGenerationResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BulkGenerationResultService {
    private static final int RECEIPT_TTL_HOURS = 24;

    private final BulkGenerationResultRepository repository;

    public void recordResult(
            UUID id,
            UUID ownerUserId,
            String subject,
            String courseProgram,
            DomainContext domainContext,
            LearnerLevel learnerLevel,
            String targetProfileType,
            boolean makePublic,
            int requestedCount,
            int createdCount,
            List<String> failedTopics,
            List<String> quotaBlockedTopics
    ) {
        BulkGenerationResultEntity entity = new BulkGenerationResultEntity();
        entity.setId(id);
        entity.setOwnerUserId(ownerUserId);
        entity.setSubject(subject);
        entity.setCourseProgram(courseProgram);
        entity.setDomainContext(domainContext);
        entity.setLearnerLevel(learnerLevel);
        entity.setTargetProfileType(targetProfileType);
        entity.setMakePublic(makePublic);
        entity.setRequestedCount(requestedCount);
        entity.setCreatedCount(createdCount);
        entity.setFailedTopics(List.copyOf(failedTopics));
        entity.setQuotaBlockedTopics(List.copyOf(quotaBlockedTopics));
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(entity);
    }

    @Transactional
    public BulkGenerationResultResponse consumeResult(UUID resultId, UUID ownerUserId) {
        BulkGenerationResultEntity entity = repository.findByIdAndOwnerUserId(resultId, ownerUserId)
                .orElseThrow(BulkGenerationResultNotFoundException::new);
        BulkGenerationResultResponse response = toResponse(entity);
        repository.delete(entity);
        return response;
    }

    @Transactional
    public long deleteExpiredReceipts(OffsetDateTime now) {
        return repository.deleteByCreatedAtBefore(now.minusHours(RECEIPT_TTL_HOURS));
    }

    private BulkGenerationResultResponse toResponse(BulkGenerationResultEntity entity) {
        return new BulkGenerationResultResponse(
                entity.getId(),
                entity.getSubject(),
                entity.getCourseProgram(),
                entity.getDomainContext() == null ? null : entity.getDomainContext().name(),
                entity.getLearnerLevel() == null ? null : entity.getLearnerLevel().name(),
                entity.getTargetProfileType(),
                Boolean.TRUE.equals(entity.getMakePublic()),
                entity.getRequestedCount(),
                entity.getCreatedCount(),
                List.copyOf(entity.getFailedTopics()),
                List.copyOf(entity.getQuotaBlockedTopics()),
                entity.getCreatedAt()
        );
    }
}
