package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.exception.ChallengeQuizQuestionBankUnavailableException;
import com.studysnap.backend.exception.NotEnoughMissedChallengeQuestionsException;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionReviewUtils;
import com.studysnap.backend.util.QuizValidationUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChallengeQuizQuestionBankService {
    public static final int MINIMUM_REDO_MISSED_QUESTIONS = 3;
    private static final String OUTCOME_UNANSWERED = "UNANSWERED";
    private static final String OUTCOME_CORRECT = "CORRECT";
    private static final String OUTCOME_INCORRECT = "INCORRECT";

    private final ChallengeQuizQuestionBankRepository challengeQuizQuestionBankRepository;
    private final OfficialChallengeQuizTemplateResolver templateResolver;
    private final StudySnapProperties properties;

    public void invalidateForStudyPack(UUID studyPackId) {
        challengeQuizQuestionBankRepository.bulkDeleteAllForStudyPack(studyPackId);
    }

    /**
     * Claims questions under the same transaction as the Challenge session change. A claimed item
     * stays unavailable to other in-progress sessions until the owning session completes or is
     * forfeited, then returns to this user's reusable bank.
     */
    public List<QuizItem> claimEligibleQuestions(
            UUID userId,
            UUID studyPackId,
            LearnerLevel effectiveCurriculumLevel,
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
                    learnerLevelName(effectiveCurriculumLevel),
                    sessionId
            );
            List<QuizItem> claimed = new ArrayList<>(count);
            List<ChallengeQuizQuestionBankEntity> claimedRows = new ArrayList<>(count);
            Set<String> templateKeys = templateResolver.templateQuestionKeys(studyPackId);
            OffsetDateTime cutoff = properties.getChallengeQuiz().getStatementPairLegacyCutoff();
            Set<String> selectedKeys = new LinkedHashSet<>(excluded);
            for (ChallengeQuizQuestionBankEntity candidate : candidates) {
                if (isExcludedLegacyStatementPair(candidate, templateKeys, cutoff)) {
                    continue;
                }
                String questionKey = candidate.getQuestionKey();
                if (questionKey == null || questionKey.isBlank() || !selectedKeys.add(questionKey)) {
                    continue;
                }
                candidate.setClaimedSessionId(sessionId);
                claimedRows.add(candidate);
                claimed.add(candidate.getQuestion());
                if (claimed.size() == count) {
                    break;
                }
            }
            if (!claimed.isEmpty()) {
                challengeQuizQuestionBankRepository.saveAll(claimedRows);
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
            LearnerLevel effectiveCurriculumLevel,
            Long generationStamp,
            List<QuizItem> questions
    ) {
        persistQuestions(userId, studyPackId, sessionId, effectiveCurriculumLevel, generationStamp, questions, sessionId);
    }

    public void persistGeneratedQuestionsUnclaimed(
            UUID userId, UUID studyPackId, UUID sessionId, LearnerLevel effectiveCurriculumLevel,
            Long generationStamp, List<QuizItem> questions
    ) {
        persistQuestions(userId, studyPackId, sessionId, effectiveCurriculumLevel, generationStamp, questions, null);
    }

    public void releaseSelectedClaims(UUID userId, UUID sessionId, List<QuizItem> questions) {
        Set<String> keys = QuizDeduplicationUtils.toNormalizedQuestionSet(questions);
        if (!keys.isEmpty()) challengeQuizQuestionBankRepository.releaseSelectedClaims(userId, sessionId, keys);
    }

    public void releaseUnappendedClaims(UUID userId, UUID sessionId, List<QuizItem> quiz) {
        Set<String> appended = QuizDeduplicationUtils.toNormalizedQuestionSet(quiz);
        List<String> unappended = challengeQuizQuestionBankRepository.findByUserIdAndClaimedSessionId(userId, sessionId)
                .stream().map(ChallengeQuizQuestionBankEntity::getQuestionKey)
                .filter(key -> !appended.contains(key)).toList();
        if (!unappended.isEmpty()) {
            challengeQuizQuestionBankRepository.releaseSelectedClaims(userId, sessionId, unappended);
        }
    }

    private void persistQuestions(
            UUID userId, UUID studyPackId, UUID sessionId, LearnerLevel effectiveCurriculumLevel,
            Long generationStamp, List<QuizItem> questions, UUID claimedSessionId
    ) {
        Objects.requireNonNull(generationStamp, "generationStamp");
        if (questions == null || questions.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Set<String> seen = new LinkedHashSet<>();
        List<ChallengeQuizQuestionBankEntity> entries = new ArrayList<>(questions.size());
        for (QuizItem question : questions) {
            String questionKey = question == null ? "" : QuizDeduplicationUtils.normalizeQuestion(question.question());
            if (questionKey.isBlank() || !seen.add(questionKey)) {
                continue;
            }
            ChallengeQuizQuestionBankEntity entry = new ChallengeQuizQuestionBankEntity();
            entry.setId(UUID.randomUUID());
            entry.setUserId(userId);
            entry.setStudyPackId(studyPackId);
            entry.setGenerationStamp(generationStamp);
            entry.setOriginSessionId(sessionId);
            entry.setQuestionKey(questionKey);
            entry.setQuestion(question);
            entry.setLearnerLevel(learnerLevelName(effectiveCurriculumLevel));
            entry.setLastKnownOutcome(OUTCOME_UNANSWERED);
            entry.setClaimedSessionId(claimedSessionId);
            entry.setGeneratedAt(now);
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            return;
        }
        // Deliberately joins the CALLER's transaction. Isolating this write in a REQUIRES_NEW
        // transaction was tried in v0.81.0 and reverted: origin_session_id and claimed_session_id are
        // FKs to quick_review_sessions, and startSession inserts that session row in its own
        // uncommitted transaction — so a second connection cannot see the parent row and every insert
        // failed the FK check, deterministically and silently. Isolation is still the right long-term
        // shape, but it requires the session row to be visible first. See the Backlog Index row.
        try {
            for (ChallengeQuizQuestionBankEntity entry : entries) {
                if (ChallengeQuizQuestionBankInsert.insert(challengeQuizQuestionBankRepository, entry) == 0) {
                    log.debug("Challenge Quiz question-bank duplicate skipped for userId={}, studyPackId={}, keyPrefix={}",
                            userId, studyPackId, entry.getQuestionKey().substring(0, Math.min(24, entry.getQuestionKey().length())));
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Challenge Quiz question-bank write failed for userId={}, studyPackId={}",
                    userId, studyPackId, exception);
        }
    }

    public long countEligibleIncorrectQuestions(
            UUID userId,
            UUID studyPackId,
            LearnerLevel effectiveCurriculumLevel
    ) {
        try {
            Set<String> templateKeys = templateResolver.templateQuestionKeys(studyPackId);
            OffsetDateTime cutoff = properties.getChallengeQuiz().getStatementPairLegacyCutoff();
            return challengeQuizQuestionBankRepository.findIncorrectClaimable(
                    userId,
                    studyPackId,
                    learnerLevelName(effectiveCurriculumLevel),
                    OUTCOME_INCORRECT
            ).stream().filter(row -> !isExcludedLegacyStatementPair(row, templateKeys, cutoff)).count();
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
            LearnerLevel effectiveCurriculumLevel,
            UUID sessionId,
            int count,
            int minimumCount
    ) {
        try {
            Set<String> templateKeys = templateResolver.templateQuestionKeys(studyPackId);
            OffsetDateTime cutoff = properties.getChallengeQuiz().getStatementPairLegacyCutoff();
            List<ChallengeQuizQuestionBankEntity> candidates = challengeQuizQuestionBankRepository
                    .findIncorrectClaimableForUpdate(
                            userId,
                            studyPackId,
                            learnerLevelName(effectiveCurriculumLevel),
                            OUTCOME_INCORRECT
                    ).stream().filter(row -> !isExcludedLegacyStatementPair(row, templateKeys, cutoff)).toList();
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

    static boolean isExcludedLegacyStatementPair(
            ChallengeQuizQuestionBankEntity row, Set<String> templateKeys, OffsetDateTime cutoff
    ) {
        return row.getQuestion() != null
                && QuizValidationUtils.isStatementPair(row.getQuestion().question())
                && !templateKeys.contains(row.getQuestionKey())
                && (cutoff == null || (row.getGeneratedAt() != null && row.getGeneratedAt().isBefore(cutoff)));
    }

    public void updateOutcomesAndReleaseClaims(
            UUID userId,
            UUID sessionId,
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers
    ) {
        try {
            List<ChallengeQuizQuestionBankEntity> claimed = challengeQuizQuestionBankRepository
                    .findByUserIdAndClaimedSessionId(userId, sessionId);
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
                if (questionIndex != null) {
                    entry.setLastKnownOutcome(resolveOutcome(
                                quiz.get(questionIndex),
                                questionIndex,
                                selectedChoices,
                                selectedMultiChoices,
                                selectedIdentificationAnswers,
                                selectedEnumerationAnswers
                        ));
                }
                entry.setClaimedSessionId(null);
            }
            challengeQuizQuestionBankRepository.saveAll(claimed);
        } catch (RuntimeException exception) {
            log.warn("Challenge Quiz question-bank outcome update failed for sessionId={}", sessionId, exception);
        }
    }

    public void releaseClaims(UUID userId, UUID sessionId) {
        try {
            // Run on the caller's connection: a failed generate-more rolls back its own claim write,
            // while a failed start can see and release its still-uncommitted claim immediately.
            // A concurrent bank delete simply makes this targeted UPDATE affect zero rows.
            challengeQuizQuestionBankRepository.releaseClaims(userId, sessionId);
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

    private String learnerLevelName(LearnerLevel curriculumLevel) {
        return curriculumLevel == null ? null : curriculumLevel.name();
    }
}
