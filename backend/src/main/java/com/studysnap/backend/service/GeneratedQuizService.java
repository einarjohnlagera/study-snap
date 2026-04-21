package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.GeneratedQuizResponse;
import com.studysnap.backend.dto.QuizDocxExportMode;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.GeneratedQuizBatchExportValidationException;
import com.studysnap.backend.exception.GeneratedQuizExportNotAllowedException;
import com.studysnap.backend.exception.GeneratedQuizGenerationFailedException;
import com.studysnap.backend.exception.GeneratedQuizNotFoundException;
import com.studysnap.backend.exception.MonthlyQuizCreditLimitReachedException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final UserRepository userRepository;
    private final QuizDocxExportService quizDocxExportService;

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

    @Transactional(readOnly = true)
    public QuizDocxExportService.QuizDocxFile exportDocx(String quizIdRaw, UUID userId, QuizDocxExportMode mode) {
        authService.requireEmailVerified(userId);
        assertTeacherExportAllowed(userId);
        UUID quizId = parseQuizId(quizIdRaw);
        GeneratedQuizEntity generatedQuiz = generatedQuizRepository.findByIdAndOwnerUserId(quizId, userId)
                .orElseThrow(GeneratedQuizNotFoundException::new);
        NoteEntity note = findOwnedNoteOrThrow(generatedQuiz.getNoteId(), userId);
        QuizDocxExportService.ExportableQuiz exportableQuiz = new QuizDocxExportService.ExportableQuiz(
                note.getTitle(),
                note.getSubject(),
                generatedQuiz.getGeneratedAt(),
                generatedQuiz.getQuestions()
        );
        return new QuizDocxExportService.QuizDocxFile(
                quizDocxExportService.buildFilename(note.getTitle(), mode),
                quizDocxExportService.exportQuizToDocx(exportableQuiz, mode)
        );
    }

    @Transactional(readOnly = true)
    public QuizDocxExportService.QuizDocxFile exportCombinedDocx(
            List<String> noteIdRaws,
            UUID userId,
            boolean includeAnswerKey,
            boolean includeExplanations
    ) {
        authService.requireEmailVerified(userId);
        assertTeacherExportAllowed(userId);

        List<UUID> noteIds = parseOrderedNoteIds(noteIdRaws);
        if (noteIds.isEmpty()) {
            throw GeneratedQuizBatchExportValidationException.emptySelection();
        }

        Map<UUID, NoteEntity> notesById = new LinkedHashMap<>();
        for (NoteEntity note : noteRepository.findByOwnerUserIdAndIdIn(userId, noteIds)) {
            notesById.put(note.getId(), note);
        }
        if (notesById.size() != noteIds.size()) {
            throw GeneratedQuizBatchExportValidationException.unknownNote();
        }

        Map<UUID, GeneratedQuizEntity> generatedQuizByNoteId = new LinkedHashMap<>();
        for (GeneratedQuizEntity generatedQuiz : generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(userId, noteIds)) {
            generatedQuizByNoteId.put(generatedQuiz.getNoteId(), generatedQuiz);
        }

        List<QuizDocxExportService.ExportableQuiz> exportableQuizzes = noteIds.stream()
                .map(noteId -> buildExportableQuiz(notesById.get(noteId), generatedQuizByNoteId.get(noteId)))
                .toList();

        return new QuizDocxExportService.QuizDocxFile(
                quizDocxExportService.buildCombinedFilename(includeAnswerKey, includeExplanations),
                quizDocxExportService.exportCombinedQuizToDocx(
                        exportableQuizzes,
                        new QuizDocxExportService.CombinedQuizDocxOptions(includeAnswerKey, includeExplanations)
                )
        );
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

    private UUID parseQuizId(String quizIdRaw) {
        return UuidParsingUtils.parseUuidOrThrow(quizIdRaw, GeneratedQuizNotFoundException::new);
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

    private List<UUID> parseOrderedNoteIds(List<String> noteIdRaws) {
        if (noteIdRaws == null || noteIdRaws.isEmpty()) {
            return List.of();
        }
        return noteIdRaws.stream()
                .map(this::parseNoteId)
                .distinct()
                .toList();
    }

    private QuizDocxExportService.ExportableQuiz buildExportableQuiz(NoteEntity note, GeneratedQuizEntity generatedQuiz) {
        if (note == null) {
            throw GeneratedQuizBatchExportValidationException.unknownNote();
        }
        if (generatedQuiz == null || generatedQuiz.getQuestions() == null || generatedQuiz.getQuestions().isEmpty()) {
            throw GeneratedQuizBatchExportValidationException.noteWithoutGeneratedQuiz();
        }
        return new QuizDocxExportService.ExportableQuiz(
                note.getTitle(),
                note.getSubject(),
                generatedQuiz.getGeneratedAt(),
                generatedQuiz.getQuestions()
        );
    }

    private GeneratedQuizResponse toResponse(GeneratedQuizEntity entity) {
        return new GeneratedQuizResponse(
                entity.getId().toString(),
                entity.getNoteId().toString(),
                entity.getQuestions() == null ? List.of() : entity.getQuestions(),
                entity.getGeneratedAt()
        );
    }

    private void assertTeacherExportAllowed(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(GeneratedQuizExportNotAllowedException::new);
        if (user.getRole() == UserRole.ADMIN || user.getProfileType() == ProfileType.TEACHER) {
            return;
        }
        throw new GeneratedQuizExportNotAllowedException();
    }
}
