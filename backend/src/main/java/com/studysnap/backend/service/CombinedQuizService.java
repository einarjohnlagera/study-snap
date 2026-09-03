package com.studysnap.backend.service;

import com.studysnap.backend.dto.CombinedQuizResponse;
import com.studysnap.backend.dto.CombinedQuizSection;
import com.studysnap.backend.dto.CreateCombinedQuizRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.CombinedQuizEntity;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.CombinedQuizNotFoundException;
import com.studysnap.backend.exception.CombinedQuizValidationException;
import com.studysnap.backend.exception.GeneratedQuizBatchExportValidationException;
import com.studysnap.backend.repository.CombinedQuizRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
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

/** Creates and reads immutable, note-independent combined-quiz snapshots. */
@Service
@RequiredArgsConstructor
public class CombinedQuizService {
    /** Request-safety bounds, deliberately independent of plan pricing. */
    public static final int MAX_SECTIONS = 20;
    public static final int MAX_SOURCE_NOTES = 20;
    public static final int MAX_TOTAL_QUESTIONS = 100;

    private final CombinedQuizRepository combinedQuizRepository;
    private final NoteRepository noteRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final StudyPackRepository studyPackRepository;
    private final AuthService authService;
    private final OnboardingGuardService onboardingGuardService;

    @Transactional
    public CombinedQuizResponse assemble(CreateCombinedQuizRequest request, UUID ownerUserId) {
        authService.requireEmailVerified(ownerUserId);
        onboardingGuardService.assertProfileComplete(ownerUserId);
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw CombinedQuizValidationException.invalidTitle();
        }
        List<CreateCombinedQuizRequest.Section> sections = request.sections() == null ? List.of() : request.sections();
        if (sections.isEmpty()) {
            throw GeneratedQuizBatchExportValidationException.emptySelection();
        }
        if (sections.size() > MAX_SECTIONS || requestedQuestionCount(sections) > MAX_TOTAL_QUESTIONS) {
            throw CombinedQuizValidationException.selectionTooLarge();
        }

        List<UUID> noteIds = sections.stream()
                .filter(Objects::nonNull)
                .map(CreateCombinedQuizRequest.Section::noteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (noteIds.isEmpty()) {
            throw GeneratedQuizBatchExportValidationException.emptySelection();
        }
        if (noteIds.size() > MAX_SOURCE_NOTES) {
            throw CombinedQuizValidationException.selectionTooLarge();
        }

        // This count match is the ownership authorization boundary. Do not reduce it to a per-note lookup:
        // that would disclose whether an omitted note exists under a different owner.
        Map<UUID, NoteEntity> notesById = new LinkedHashMap<>();
        for (NoteEntity note : noteRepository.findByOwnerUserIdAndIdIn(ownerUserId, noteIds)) {
            notesById.put(note.getId(), note);
        }
        if (notesById.size() != noteIds.size()) {
            throw GeneratedQuizBatchExportValidationException.unknownNote();
        }

        Map<UUID, GeneratedQuizEntity> generatedQuizByNoteId = new LinkedHashMap<>();
        for (GeneratedQuizEntity quiz : generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(ownerUserId, noteIds)) {
            generatedQuizByNoteId.put(quiz.getNoteId(), quiz);
        }
        Map<UUID, StudyPackEntity> studyPackByNoteId = new LinkedHashMap<>();
        for (StudyPackEntity pack : studyPackRepository.findByNoteIdIn(noteIds)) {
            studyPackByNoteId.put(pack.getNoteId(), pack);
        }

        List<CombinedQuizSection> snapshotSections = sections.stream()
                .filter(Objects::nonNull)
                .map(section -> copySection(section, notesById, generatedQuizByNoteId, studyPackByNoteId))
                .filter(section -> !section.questions().isEmpty())
                .toList();
        if (snapshotSections.isEmpty()) {
            throw GeneratedQuizBatchExportValidationException.emptySelection();
        }

        CombinedQuizEntity entity = new CombinedQuizEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setTitle(request.title().trim());
        entity.setSections(snapshotSections);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toResponse(combinedQuizRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public CombinedQuizResponse getById(UUID combinedQuizId, UUID ownerUserId) {
        return combinedQuizRepository.findByIdAndOwnerUserId(combinedQuizId, ownerUserId)
                .map(this::toResponse)
                .orElseThrow(CombinedQuizNotFoundException::new);
    }

    private CombinedQuizSection copySection(
            CreateCombinedQuizRequest.Section requestedSection,
            Map<UUID, NoteEntity> notesById,
            Map<UUID, GeneratedQuizEntity> generatedQuizByNoteId,
            Map<UUID, StudyPackEntity> studyPackByNoteId
    ) {
        if (requestedSection.noteId() == null || requestedSection.questionIndexes() == null) {
            return new CombinedQuizSection("", List.of());
        }
        GeneratedQuizEntity generatedQuiz = generatedQuizByNoteId.get(requestedSection.noteId());
        if (generatedQuiz == null || generatedQuiz.getQuestions() == null) {
            return new CombinedQuizSection(notesById.get(requestedSection.noteId()).getTitle(), List.of());
        }
        StudyPackEntity pack = studyPackByNoteId.get(requestedSection.noteId());
        String sourceStudyPackId = pack == null ? null : pack.getId().toString();
        List<QuizItem> copiedQuestions = requestedSection.questionIndexes().stream()
                .filter(Objects::nonNull)
                .filter(index -> index >= 0 && index < generatedQuiz.getQuestions().size())
                // Trusted copy only: the public QuizItem constructor re-sanitizes choices and corrupts initials.
                .map(index -> generatedQuiz.getQuestions().get(index).withSourceStudyPackId(sourceStudyPackId))
                .toList();
        return new CombinedQuizSection(notesById.get(requestedSection.noteId()).getTitle(), copiedQuestions);
    }

    private int requestedQuestionCount(List<CreateCombinedQuizRequest.Section> sections) {
        return sections.stream()
                .filter(Objects::nonNull)
                .map(CreateCombinedQuizRequest.Section::questionIndexes)
                .filter(Objects::nonNull)
                .mapToInt(List::size)
                .sum();
    }

    private CombinedQuizResponse toResponse(CombinedQuizEntity entity) {
        return new CombinedQuizResponse(entity.getId(), entity.getTitle(), entity.getSections(), entity.getCreatedAt());
    }
}
