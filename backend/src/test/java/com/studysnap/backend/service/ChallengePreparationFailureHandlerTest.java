package com.studysnap.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChallengePreparationFailureHandlerTest {
    @Test
    void dispatchFailureUsesRequiresNewTransactionForRecovery() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        GenerationRecoveryRowWriter rowWriter = mock(GenerationRecoveryRowWriter.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        org.mockito.ArgumentCaptor<TransactionDefinition> definition =
                org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        UUID sessionId = UUID.randomUUID();

        new ChallengePreparationFailureHandler(transactionManager, rowWriter).fail(sessionId);

        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(rowWriter).failChallengeSession(sessionId);
        verify(transactionManager).commit(transactionStatus);
    }
}
