package com.studysnap.backend.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Commits dispatch failure after the request transaction has already committed. */
@Component
public class ChallengePreparationFailureHandler {
    private final TransactionTemplate transactionTemplate;
    private final GenerationRecoveryRowWriter rowWriter;

    public ChallengePreparationFailureHandler(
            PlatformTransactionManager transactionManager,
            GenerationRecoveryRowWriter rowWriter
    ) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rowWriter = rowWriter;
    }

    public void fail(UUID sessionId) {
        transactionTemplate.executeWithoutResult(status -> rowWriter.failChallengeSession(sessionId));
    }
}
