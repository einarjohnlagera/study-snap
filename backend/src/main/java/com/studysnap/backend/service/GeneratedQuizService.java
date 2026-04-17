package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.GeneratedQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.GeneratedQuizGenerationFailedException;
import com.studysnap.backend.exception.GeneratedQuizNotFoundException;
import com.studysnap.backend.exception.MonthlyQuizCreditLimitReachedException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class GeneratedQuizService {
    private static final String AI_RATE_LIMIT_SCOPE = "generated-quiz";
    private static final int TEACHER_QUIZ_QUESTION_COUNT = 10;

    private final NoteRepository noteRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final QuizGenerationService quizGenerationService;
    private final SubscriptionService subscriptionService;
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final AuthService authService;
    private final AiRateLimitService aiRateLimitService;
    private final StudyPackGenerationContextResolver generationContextResolver;

    @Transactional(readOnly = true)
    public GeneratedQuizResponse getByNoteId(String noteIdRaw, UUID userId) {
        authService.requireEmailVerified(userId);
        UUID noteId = parseNoteId(noteIdRaw);
        findOwnedNoteOrThrow(noteId, userId);
        GeneratedQuizEntity generatedQuiz = generatedQuizRepository.findByNoteIdAndOwnerUserId(noteId, userId)
                .orElseThrow(GeneratedQuizNotFoundException::new);
        return toResponse(generatedQuiz);
    }

    public GeneratedQuizResponse generate(String noteIdRaw, UUID userId) {
        authService.requireEmailVerified(userId);
        UUID noteId = parseNoteId(noteIdRaw);
        NoteEntity note = findOwnedNoteOrThrow(noteId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        assertQuizCreditAvailable(userId, planType);
        aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);

        GeneratedQuizEntity existing = generatedQuizRepository.findByNoteIdAndOwnerUserId(noteId, userId)
                .orElse(null);
        List<String> disallowedQuestions = existing == null
                ? List.of()
                : extractQuestionTexts(existing.getQuestions());
        StudyPackGenerationContext generationContext = generationContextResolver.resolve(userId, note);

        try {
            List<QuizItem> generatedQuestions = quizGenerationService.generateTeacherQuiz(
                    note.getTitle(),
                    note.getContent(),
                    disallowedQuestions,
                    TEACHER_QUIZ_QUESTION_COUNT,
                    generationContext
            );
            List<QuizItem> uniqueQuestions = QuizDeduplicationUtils.uniqueQuestions(
                    generatedQuestions,
                    QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions)
            );
            if (uniqueQuestions.size() != TEACHER_QUIZ_QUESTION_COUNT) {
                throw new GeneratedQuizGenerationFailedException();
            }

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            GeneratedQuizEntity entity = existing == null ? new GeneratedQuizEntity() : existing;
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            entity.setOwnerUserId(userId);
            entity.setNoteId(noteId);
            entity.setQuestions(uniqueQuestions);
            entity.setGeneratedAt(now);
            entity.setUpdatedAt(now);
            GeneratedQuizEntity saved = generatedQuizRepository.save(entity);

            note.setUpdatedAt(now);
            noteRepository.save(note);
            userUsageService.incrementChallengeQuizGeneration(userId, now);
            return toResponse(saved);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GeneratedQuizGenerationFailedException();
        }
    }

    private int assertQuizCreditAvailable(UUID userId, PlanType planType) {
        int usedThisMonth = userUsageService.getMonthlyUsage(userId, OffsetDateTime.now(ZoneOffset.UTC))
                .challengeQuizGenerations();
        int monthlyLimit = properties.getPricing().resolveMonthlyChallengeQuizLimit(planType);
        if (usedThisMonth < monthlyLimit) {
            return usedThisMonth;
        }
        throw new MonthlyQuizCreditLimitReachedException();
    }

    private UUID parseNoteId(String noteIdRaw) {
        return UuidParsingUtils.parseUuidOrThrow(noteIdRaw, NoteNotFoundException::new);
    }

    private NoteEntity findOwnedNoteOrThrow(UUID noteId, UUID userId) {
        return noteRepository.findByIdAndOwnerUserId(noteId, userId)
                .orElseThrow(NoteNotFoundException::new);
    }

    private List<String> extractQuestionTexts(List<QuizItem> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        return questions.stream()
                .map(QuizItem::question)
                .filter(question -> question != null && !question.isBlank())
                .toList();
    }

    private GeneratedQuizResponse toResponse(GeneratedQuizEntity entity) {
        return new GeneratedQuizResponse(
                entity.getNoteId().toString(),
                entity.getQuestions() == null ? List.of() : entity.getQuestions(),
                entity.getGeneratedAt()
        );
    }
}
