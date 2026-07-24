package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeQuizQuestionBankRepository extends JpaRepository<ChallengeQuizQuestionBankEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select question
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
              and ((:learnerLevel is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
              and (question.claimedSessionId is null or question.claimedSessionId = :sessionId)
            order by question.generatedAt asc
            """)
    List<ChallengeQuizQuestionBankEntity> findClaimableForUpdate(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("learnerLevel") String learnerLevel,
            @Param("sessionId") UUID sessionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select question
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
              and ((:learnerLevel is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
              and question.lastKnownOutcome = :outcome
              and question.claimedSessionId is null
            order by question.generatedAt asc
            """)
    List<ChallengeQuizQuestionBankEntity> findIncorrectClaimableForUpdate(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("learnerLevel") String learnerLevel,
            @Param("outcome") String outcome
    );

    @Query("""
            select count(question)
            from ChallengeQuizQuestionBankEntity question
            where question.userId = :userId
              and question.studyPackId = :studyPackId
              and ((:learnerLevel is null and question.learnerLevel is null) or question.learnerLevel = :learnerLevel)
              and question.lastKnownOutcome = :outcome
              and question.claimedSessionId is null
            """)
    long countIncorrectEligibleQuestions(
            @Param("userId") UUID userId,
            @Param("studyPackId") UUID studyPackId,
            @Param("learnerLevel") String learnerLevel,
            @Param("outcome") String outcome
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ChallengeQuizQuestionBankEntity> findByUserIdAndStudyPackIdAndClaimedSessionId(
            UUID userId,
            UUID studyPackId,
            UUID claimedSessionId
    );
}
