package com.studysnap.backend.service;

import com.studysnap.backend.dto.CampaignFeedbackStatusResponse;
import com.studysnap.backend.dto.SubmitCampaignFeedbackRequest;
import com.studysnap.backend.entity.CampaignFeedbackResponseEntity;
import com.studysnap.backend.entity.PlanIssue;
import com.studysnap.backend.entity.PrimaryBlocker;
import com.studysnap.backend.entity.QuizIssue;
import com.studysnap.backend.exception.CampaignClosedException;
import com.studysnap.backend.exception.InvalidCampaignFeedbackRequestException;
import com.studysnap.backend.repository.CampaignFeedbackResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignFeedbackServiceTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private CampaignFeedbackResponseRepository repository;
    @Mock
    private TransactionOperations transactionOperations;
    @Mock
    private TransactionStatus transactionStatus;

    private CampaignFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new CampaignFeedbackService(repository, transactionOperations);
        setClosesAt("2099-01-01T00:00:00Z");
    }

    @Test
    void submit_persistsBlockersAndConditionals() {
        runTransactionCallback();
        SubmitCampaignFeedbackRequest request = new SubmitCampaignFeedbackRequest(
                List.of(PrimaryBlocker.QUIZ_QUALITY, PrimaryBlocker.PRICING),
                List.of(QuizIssue.ANSWERS_SEEM_INCORRECT),
                PlanIssue.HAPPY_WITH_FREE,
                null,
                null,
                "More examples, please."
        );

        service.submit(USER_ID, request);

        ArgumentCaptor<CampaignFeedbackResponseEntity> captor = ArgumentCaptor.forClass(CampaignFeedbackResponseEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPrimaryBlockers()).containsExactly("QUIZ_QUALITY", "PRICING");
        assertThat(captor.getValue().getQuizIssues()).containsExactly("ANSWERS_SEEM_INCORRECT");
        assertThat(captor.getValue().getPlanIssue()).isEqualTo("HAPPY_WITH_FREE");
    }

    @Test
    void submit_freeTextOnlyNormalizesOmittedArraysToEmpty() {
        runTransactionCallback();

        service.submit(USER_ID, new SubmitCampaignFeedbackRequest(null, null, null, null, null, "A comment"));

        ArgumentCaptor<CampaignFeedbackResponseEntity> captor = ArgumentCaptor.forClass(CampaignFeedbackResponseEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPrimaryBlockers()).isEmpty();
        assertThat(captor.getValue().getQuizIssues()).isEmpty();
    }

    @Test
    void submit_existingResponseIsSuccessfulNoOpEvenAfterClose() {
        setClosesAt("2000-01-01T00:00:00Z");
        when(repository.findByUserIdAndCampaignId(USER_ID, "STUDY_FRICTION_2026_09"))
                .thenReturn(Optional.of(new CampaignFeedbackResponseEntity()));

        assertThatNoException().isThrownBy(() -> service.submit(USER_ID, blockerOnly()));

        verify(transactionOperations, never()).execute(any(TransactionCallback.class));
    }

    @Test
    void submit_concurrentUniqueIndexLossIsSuccessfulNoOp() {
        when(transactionOperations.execute(any(TransactionCallback.class)))
                .thenThrow(new DataIntegrityViolationException("unique index"));

        assertThatNoException().isThrownBy(() -> service.submit(USER_ID, blockerOnly()));
    }

    @Test
    void submit_newResponseAfterCloseIsRejectedBeforeInsert() {
        setClosesAt("2000-01-01T00:00:00Z");

        assertThatThrownBy(() -> service.submit(USER_ID, blockerOnly()))
                .isInstanceOf(CampaignClosedException.class);
        verify(transactionOperations, never()).execute(any(TransactionCallback.class));
    }

    @Test
    void getStatusReturnsAllSubmittedAndOpenCombinationsIndependently() {
        for (boolean submitted : List.of(false, true)) {
            when(repository.findByUserIdAndCampaignId(USER_ID, "STUDY_FRICTION_2026_09"))
                    .thenReturn(submitted
                            ? Optional.of(new CampaignFeedbackResponseEntity())
                            : Optional.empty());

            setClosesAt("2099-01-01T00:00:00Z");
            assertThat(service.getStatus(USER_ID))
                    .isEqualTo(new CampaignFeedbackStatusResponse(submitted, true));

            setClosesAt("2000-01-01T00:00:00Z");
            assertThat(service.getStatus(USER_ID))
                    .isEqualTo(new CampaignFeedbackStatusResponse(submitted, false));
        }
    }

    @Test
    void getStatusScopesTheReadToTheRequestedUser() {
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(repository.findByUserIdAndCampaignId(USER_ID, "STUDY_FRICTION_2026_09"))
                .thenReturn(Optional.of(new CampaignFeedbackResponseEntity()));
        when(repository.findByUserIdAndCampaignId(otherUserId, "STUDY_FRICTION_2026_09"))
                .thenReturn(Optional.empty());

        assertThat(service.getStatus(USER_ID).submitted()).isTrue();
        assertThat(service.getStatus(otherUserId).submitted()).isFalse();
    }

    @Test
    void submit_rejectsEmptyOrOrphanedOrOverLengthValues() {
        assertThatThrownBy(() -> service.submit(
                USER_ID,
                new SubmitCampaignFeedbackRequest(List.of(), List.of(), null, null, null, "  ")
        )).isInstanceOf(InvalidCampaignFeedbackRequestException.class)
                .hasMessageContaining("primaryBlocker");

        assertThatThrownBy(() -> service.submit(
                USER_ID,
                new SubmitCampaignFeedbackRequest(
                        List.of(PrimaryBlocker.TOO_MANY_STEPS),
                        List.of(QuizIssue.TOO_EASY),
                        null,
                        null,
                        null,
                        null
                )
        )).isInstanceOf(InvalidCampaignFeedbackRequestException.class)
                .hasMessageContaining("quizIssues");

        assertThatThrownBy(() -> service.submit(
                USER_ID,
                new SubmitCampaignFeedbackRequest(List.of(), List.of(), null, null, null, "x".repeat(2001))
        )).isInstanceOf(InvalidCampaignFeedbackRequestException.class)
                .hasMessageContaining("freeText");

        assertThatThrownBy(() -> service.submit(
                USER_ID,
                new SubmitCampaignFeedbackRequest(
                        List.of(PrimaryBlocker.MISSING_FEATURE), List.of(), null, "x".repeat(201), null, null
                )
        )).isInstanceOf(InvalidCampaignFeedbackRequestException.class)
                .hasMessageContaining("missingFeatureText");
    }

    private SubmitCampaignFeedbackRequest blockerOnly() {
        return new SubmitCampaignFeedbackRequest(
                List.of(PrimaryBlocker.TOO_MANY_STEPS), List.of(), null, null, null, null
        );
    }

    @SuppressWarnings("unchecked")
    private void runTransactionCallback() {
        when(transactionOperations.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }

    private void setClosesAt(String value) {
        ReflectionTestUtils.setField(service, "closesAtRaw", value);
        service.init();
    }
}
