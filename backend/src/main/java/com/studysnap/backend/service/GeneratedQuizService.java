package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.GeneratedQuizResponse;
import com.studysnap.backend.dto.MultiNoteQuizDocxExportRequest;
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
import java.util.Objects;
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
    private final ExportUsageProtectionService exportUsageProtectionService;

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

    public QuizDocxExportService.QuizDocxFile exportDocx(String quizIdRaw, UUID userId, QuizDocxExportMode mode) {
        authService.requireEmailVerified(userId);
        assertTeacherExportAllowed(userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        exportUsageProtectionService.assertQuotaAvailable(userId, planType);
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
        byte[] content = quizDocxExportService.exportQuizToDocx(exportableQuiz, mode);
        exportUsageProtectionService.recordUsage(userId, OffsetDateTime.now(ZoneOffset.UTC));
        return new QuizDocxExportService.QuizDocxFile(
                quizDocxExportService.buildFilename(note.getTitle(), mode),
                content
        );
    }

    public QuizDocxExportService.QuizDocxFile exportCombinedDocx(
            List<MultiNoteQuizDocxExportRequest.Section> sections,
            UUID userId,
            boolean includeAnswerKey,
            boolean includeExplanations
    ) {
        authService.requireEmailVerified(userId);
        assertTeacherExportAllowed(userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        exportUsageProtectionService.assertQuotaAvailable(userId, planType);

        List<ExportSectionRequest> requestedSections = parseSectionRequests(sections);
        List<UUID> noteIds = requestedSections.stream()
                .flatMap(section -> section.questionReferences().stream())
                .map(ExportQuestionReference::noteId)
                .distinct()
                .toList();
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

        List<QuizDocxExportService.ExportableSection> exportableSections = requestedSections.stream()
                .map(section -> buildExportableSection(section, notesById, generatedQuizByNoteId))
                .filter(section -> !section.questions().isEmpty())
                .toList();
        if (exportableSections.isEmpty()) {
            throw GeneratedQuizBatchExportValidationException.emptySelection();
        }

        byte[] content = quizDocxExportService.exportCombinedQuizToDocx(
                exportableSections,
                new QuizDocxExportService.CombinedQuizDocxOptions(includeAnswerKey, includeExplanations)
        );
        exportUsageProtectionService.recordUsage(userId, OffsetDateTime.now(ZoneOffset.UTC));
        return new QuizDocxExportService.QuizDocxFile(
                quizDocxExportService.buildCombinedFilename(includeAnswerKey, includeExplanations),
                content
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

    private List<ExportSectionRequest> parseSectionRequests(List<MultiNoteQuizDocxExportRequest.Section> sections) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }
        return sections.stream()
                .filter(Objects::nonNull)
                .map(section -> new ExportSectionRequest(
                        section.title(),
                        section.questionRefs() == null ? List.of() : section.questionRefs().stream()
                                .filter(Objects::nonNull)
                                .map(questionRef -> new ExportQuestionReference(
                                        parseNoteId(questionRef.noteId()),
                                        parseQuestionIndex(questionRef.questionIndex())
                                ))
                                .toList()
                ))
                .filter(section -> !section.questionReferences().isEmpty())
                .toList();
    }

    private QuizDocxExportService.ExportableSection buildExportableSection(
            ExportSectionRequest section,
            Map<UUID, NoteEntity> notesById,
            Map<UUID, GeneratedQuizEntity> generatedQuizByNoteId
    ) {
        List<QuizItem> sectionQuestions = section.questionReferences().stream()
                .map(questionReference -> buildExportableQuestion(
                        notesById.get(questionReference.noteId()),
                        generatedQuizByNoteId.get(questionReference.noteId()),
                        questionReference.questionIndex()
                ))
                .toList();
        List<String> sectionSubjects = section.questionReferences().stream()
                .map(ExportQuestionReference::noteId)
                .distinct()
                .map(notesById::get)
                .map(note -> note == null ? null : note.getSubject())
                .filter(Objects::nonNull)
                .toList();
        return new QuizDocxExportService.ExportableSection(
                section.title(),
                sectionSubjects,
                sectionQuestions
        );
    }

    private QuizItem buildExportableQuestion(NoteEntity note, GeneratedQuizEntity generatedQuiz, int questionIndex) {
        if (note == null) {
            throw GeneratedQuizBatchExportValidationException.unknownNote();
        }
        if (generatedQuiz == null || generatedQuiz.getQuestions() == null || generatedQuiz.getQuestions().isEmpty()) {
            throw GeneratedQuizBatchExportValidationException.noteWithoutGeneratedQuiz();
        }
        if (questionIndex < 0 || questionIndex >= generatedQuiz.getQuestions().size()) {
            throw GeneratedQuizBatchExportValidationException.invalidQuestionSelection();
        }
        return generatedQuiz.getQuestions().get(questionIndex);
    }

    private int parseQuestionIndex(Integer questionIndex) {
        if (questionIndex == null || questionIndex < 0) {
            throw GeneratedQuizBatchExportValidationException.invalidQuestionSelection();
        }
        return questionIndex;
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

    private record ExportSectionRequest(
            String title,
            List<ExportQuestionReference> questionReferences
    ) {
    }

    private record ExportQuestionReference(
            UUID noteId,
            int questionIndex
    ) {
    }
}
