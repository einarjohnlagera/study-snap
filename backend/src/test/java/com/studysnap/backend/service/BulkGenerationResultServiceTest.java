package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerationResultResponse;
import com.studysnap.backend.entity.BulkGenerationResultEntity;
import com.studysnap.backend.exception.BulkGenerationResultNotFoundException;
import com.studysnap.backend.repository.BulkGenerationResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkGenerationResultServiceTest {
    private static final String SUBJECT = "Maternal Health";
    private static final String COURSE_PROGRAM = "Nursing";
    private static final String TARGET_PROFILE_TYPE = "BOARD_TAKER";

    @Mock
    private BulkGenerationResultRepository repository;

    @Test
    void recordResult_persistsReceiptFields() {
        BulkGenerationResultService service = new BulkGenerationResultService(repository);
        UUID resultId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        service.recordResult(
                resultId,
                ownerUserId,
                SUBJECT,
                COURSE_PROGRAM,
                TARGET_PROFILE_TYPE,
                true,
                2,
                1,
                List.of("Prenatal Care")
        );

        ArgumentCaptor<BulkGenerationResultEntity> captor =
                ArgumentCaptor.forClass(BulkGenerationResultEntity.class);
        verify(repository).save(captor.capture());
        BulkGenerationResultEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(resultId);
        assertThat(entity.getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(entity.getSubject()).isEqualTo(SUBJECT);
        assertThat(entity.getCourseProgram()).isEqualTo(COURSE_PROGRAM);
        assertThat(entity.getTargetProfileType()).isEqualTo(TARGET_PROFILE_TYPE);
        assertThat(entity.getMakePublic()).isTrue();
        assertThat(entity.getRequestedCount()).isEqualTo(2);
        assertThat(entity.getCreatedCount()).isEqualTo(1);
        assertThat(entity.getFailedTopics()).containsExactly("Prenatal Care");
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void consumeResult_returnsReceiptThenDeletesIt() {
        BulkGenerationResultService service = new BulkGenerationResultService(repository);
        UUID resultId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        BulkGenerationResultEntity entity = entity(resultId, ownerUserId);
        when(repository.findByIdAndOwnerUserId(resultId, ownerUserId)).thenReturn(Optional.of(entity));

        BulkGenerationResultResponse response = service.consumeResult(resultId, ownerUserId);

        assertThat(response.id()).isEqualTo(resultId);
        assertThat(response.subject()).isEqualTo(SUBJECT);
        assertThat(response.courseProgram()).isEqualTo(COURSE_PROGRAM);
        assertThat(response.targetProfileType()).isEqualTo(TARGET_PROFILE_TYPE);
        assertThat(response.makePublic()).isTrue();
        assertThat(response.requestedCount()).isEqualTo(2);
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.failedTopics()).containsExactly("Prenatal Care");
        verify(repository).delete(entity);
    }

    @Test
    void consumeResult_throwsNotFoundWhenMissingOrOwnedByAnotherUser() {
        BulkGenerationResultService service = new BulkGenerationResultService(repository);
        UUID resultId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        when(repository.findByIdAndOwnerUserId(resultId, ownerUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeResult(resultId, ownerUserId))
                .isInstanceOf(BulkGenerationResultNotFoundException.class);
    }

    @Test
    void deleteExpiredReceipts_deletesOlderThanTwentyFourHours() {
        BulkGenerationResultService service = new BulkGenerationResultService(repository);
        OffsetDateTime now = OffsetDateTime.of(2026, 6, 17, 12, 0, 0, 0, ZoneOffset.UTC);
        when(repository.deleteByCreatedAtBefore(now.minusHours(24))).thenReturn(3L);

        long deletedCount = service.deleteExpiredReceipts(now);

        assertThat(deletedCount).isEqualTo(3L);
        verify(repository).deleteByCreatedAtBefore(now.minusHours(24));
    }

    private BulkGenerationResultEntity entity(UUID resultId, UUID ownerUserId) {
        BulkGenerationResultEntity entity = new BulkGenerationResultEntity();
        entity.setId(resultId);
        entity.setOwnerUserId(ownerUserId);
        entity.setSubject(SUBJECT);
        entity.setCourseProgram(COURSE_PROGRAM);
        entity.setTargetProfileType(TARGET_PROFILE_TYPE);
        entity.setMakePublic(true);
        entity.setRequestedCount(2);
        entity.setCreatedCount(1);
        entity.setFailedTopics(List.of("Prenatal Care"));
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return entity;
    }
}
