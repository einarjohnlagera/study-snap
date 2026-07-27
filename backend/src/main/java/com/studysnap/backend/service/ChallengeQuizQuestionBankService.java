package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.ChallengeQuizQuestionBankUnavailableException;
import com.studysnap.backend.exception.NotEnoughMissedChallengeQuestionsException;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionReviewUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChallengeQuizQuestionBankService {
    public static final int MINIMUM_REDO_MISSED_QUESTIONS = 3;
    private static final String OUTCOME_UNANSWERED = "UNANSWERED";
    private static final String OUTCOME_CORRECT = "CORRECT";
    private static final String OUTCOME_INCORRECT = "INCORRECT";

    private final ChallengeQuizQuestionBankRepository challengeQuizQuestionBankRepository;

    /**
     * Claims questions under the same transaction as the Challenge session change. A claimed item
     * stays unavailable to other in-progress sessions until the owning session completes or is
     * forfeited, then returns to this user's reusable bank.
     */
    public List<QuizItem> claimEligibleQuestions(
            UUID userId,
            UUID studyPackId,
            LearnerLevel learnerLevel,
            UUID sessionId,
            Set<String> disallowedQuestionKeys,
            int count
    ) {
        if (count <= 0) {
            return List.of();
        }
        try {
            Set<String> excluded = disallowedQuestionKeys == null
                    ? Set.of()
                    : new LinkedHashSet<>(disallowedQuestionKeys);
            List<ChallengeQuizQuestionBankEntity> candidates = challengeQuizQuestionBankRepository.findClaimableForUpdate(
                    userId,
                    studyPackId,
                    learnerLevelName(learnerLevel),
                    sessionId
            );
            List<QuizItem> claimed = new ArrayList<>(count);
            Set<String> selectedKeys = new LinkedHashSet<>(excluded);
            for (ChallengeQuizQuestionBankEntity candidate : candidates) {
                String questionKey = candidate.getQuestionKey();
                if (questionKey == null || questionKey.isBlank() || !selectedKeys.add(questionKey)) {
                    continue;
                }
                candidate.setClaimedSessionId(sessionId);
                claimed.add(candidate.getQuestion());
                if (claimed.size() == count) {
                    break;
                }
            }
            if (!claimed.isEmpty()) {
                challengeQuizQuestionBankRepository.saveAll(candidates);
            }
            return List.copyOf(claimed);
        } catch (RuntimeException exception) {
            log.warn("Challenge Quiz question-bank read failed for userId={}, studyPackId={}", userId, studyPackId, exception);
            return List.of();
        }
    }

    public void persistGeneratedQuestions(
            UUID userId,
            UUID studyPackId,
            UUID sessionId,
            LearnerLevel learnerLevel,
            List<QuizItem> questions
    ) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<ChallengeQuizQuestionBankEntity> entries = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (QuizItem question : questions) {
                String questionKey = question == null ? "" : QuizDeduplicationUtils.normalizeQuestion(question.question());
                if (questionKey.isBlank() || !seen.add(questionKey)) {
                    continue;
                }
                ChallengeQuizQuestionBankEntity entry = new ChallengeQuizQuestionBankEntity();
                entry.setId(UUID.randomUUID());
                entry.setUserId(userId);
                entry.setStudyPackId(studyPackId);
                entry.setOriginSessionId(sessionId);
                entry.setQuestionKey(questionKey);
                entry.setQuestion(question);
                entry.setLearnerLevel(learnerLevelName(learnerLevel));
                entry.setLastKnownOutcome(OUTCOME_UNANSWERED);
                entry.setClaimedSessionId(sessionId);
                entry.setGeneratedAt(now);
                entries.add(entry);
            }
            if (!entries.isEmpty()) {
                challengeQuizQuestionBankRepository.saveAll(entries);
            }
        } catch (RuntimeException exception) {
            log.warn("Challenge Quiz question-bank write failed for userId={}, studyPackId={}", userId, studyPackId, exception);
        }
    }

    public long countEligibleIncorrectQuestions(UUID userId, UUID studyPackId, LearnerLevel learnerLevel) {
        try {
            return challengeQuizQuestionBankRepository.countIncorrectEligibleQuestions(
                    userId,
                    studyPackId,
                    learnerLevelName(learnerLevel),
                    OUTCOME_INCORRECT
            );
        } catch (RuntimeException exception) {
            log.warn("Challenge Quiz missed-question count failed for userId={}, studyPackId={}", userId, studyPackId, exception);
            return 0L;
        }
    }

    /**
     * Claims only questions that the learner last answered incorrectly. Unlike ordinary Challenge
     * starts, this has no LLM fallback: a bank failure or short race must fail closed.
     */
    public List<QuizItem> claimIncorrectQuestions(
            UUID userId,
            UUID studyPackId,
            LearnerLevel learnerLevel,
            UUID sessionId,
            int count,
            int minimumCount
    ) {
        try {
            List<ChallengeQuizQuestionBankEntity> candidates = challengeQuizQuestionBankRepository
                    .findIncorrectClaimableForUpdate(
                            userId,
                            studyPackId,
                            learnerLevelName(learnerLevel),
                            OUTCOME_INCORRECT
                    );
            int claimCount = Math.min(count, candidates.size());
            if (claimCount < minimumCount) {
                throw new NotEnoughMissedChallengeQuestionsException();
            }
            List<QuizItem> claimed = new ArrayList<>(claimCount);
            for (int index = 0; index < claimCount; index++) {
                ChallengeQuizQuestionBankEntity candidate = candidates.get(index);
                candidate.setClaimedSessionId(sessionId);
                claimed.add(candidate.getQuestion());
            }
            challengeQuizQuestionBankRepository.saveAll(candidates.subList(0, claimCount));
            return List.copyOf(claimed);
        } catch (NotEnoughMissedChallengeQuestionsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Challenge Quiz missed-question claim failed for userId={}, studyPackId={}", userId, studyPackId, exception);
            throw new ChallengeQuizQuestionBankUnavailableException();
        }
    }

    public void updateOutcomesAndReleaseClaims(
            UUID userId,
            UUID studyPackId,
            UUID sessionId,
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers
    ) {
        try {
            List<ChallengeQuizQuestionBankEntity> claimed = challengeQuizQuestionBankRepository
                    .findByUserIdAndStudyPackIdAndClaimedSessionId(userId, studyPackId, sessionId);
            if (claimed.isEmpty()) {
                return;
            }
            Map<String, Integer> indexesByQuestionKey = new java.util.HashMap<>();
            for (int index = 0; index < quiz.size(); index++) {
                QuizItem question = quiz.get(index);
                String key = question == null ? "" : QuizDeduplicationUtils.normalizeQuestion(question.question());
                if (!key.isBlank()) {
                    indexesByQuestionKey.put(key, index);
                }
            }
            for (ChallengeQuizQuestionBankEntity entry : claimed) {
                Integer questionIndex = indexesByQuestionKey.get(entry.getQuestionKey());
                entry.setLastKnownOutcome(questionIndex == null
                        ? OUTCOME_UNANSWERED
                        : resolveOutcome(
                                quiz.get(questionIndex),
                                questionIndex,
                                selectedChoices,
                                selectedMultiChoices,
                                selectedIdentificationAnswers,
                                selectedEnumerationAnswers
                        ));
                entry.setClaimedSessionId(null);
            }
            challengeQuizQuestionBankRepository.saveAll(claimed);
        } catch (RuntimeException exception) {
            log.warn("Challenge Quiz question-bank outcome update failed for sessionId={}", sessionId, exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaims(UUID userId, UUID studyPackId, UUID sessionId) {
        try {
            List<ChallengeQuizQuestionBankEntity> claimed = challengeQuizQuestionBankRepository
                    .findByUserIdAndStudyPackIdAndClaimedSessionId(userId, studyPackId, sessionId);
            claimed.forEach(entry -> entry.setClaimedSessionId(null));
            if (!claimed.isEmpty()) {
                challengeQuizQuestionBankRepository.saveAll(claimed);
            }
        } catch (RuntimeException exception) {
            log.warn("Challenge Quiz question-bank claim release failed for sessionId={}", sessionId, exception);
        }
    }

    private String resolveOutcome(
            QuizItem question,
            int questionIndex,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers
    ) {
        boolean answered = (selectedChoices != null && selectedChoices.containsKey(questionIndex))
                || (selectedMultiChoices != null && selectedMultiChoices.containsKey(questionIndex))
                || (selectedIdentificationAnswers != null && selectedIdentificationAnswers.containsKey(questionIndex))
                || (selectedEnumerationAnswers != null && selectedEnumerationAnswers.containsKey(questionIndex));
        if (!answered) {
            return OUTCOME_UNANSWERED;
        }
        return QuizSessionReviewUtils.isAnswerCorrect(
                question,
                questionIndex,
                selectedChoices,
                selectedMultiChoices,
                selectedIdentificationAnswers,
                selectedEnumerationAnswers
        ) ? OUTCOME_CORRECT : OUTCOME_INCORRECT;
    }

    private String learnerLevelName(LearnerLevel learnerLevel) {
        return learnerLevel == null ? null : learnerLevel.name();
    }
}
