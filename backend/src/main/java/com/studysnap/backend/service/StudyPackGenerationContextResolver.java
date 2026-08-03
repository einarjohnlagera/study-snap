package com.studysnap.backend.service;

import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyPackGenerationContextResolver {
    private static final LearnerLevel DEFAULT_LEARNER_LEVEL = LearnerLevel.COLLEGE;

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;

    public StudyPackGenerationContext resolve(UUID ownerUserId, NoteEntity note) {
        List<String> tags = note == null || note.getTags() == null
                ? List.of()
                : Arrays.asList(note.getTags());

        return userRepository.findById(ownerUserId)
                .map(user -> new StudyPackGenerationContext(
                        user.getLearnerLevel(),
                        note == null ? user.getCourseProgram() : firstNonBlank(note.getCourseProgram(), user.getCourseProgram()),
                        note == null ? null : note.getSubject(),
                        tags,
                        note == null ? null : note.getDomainContext(),
                        note == null ? null : note.getLearnerLevel()
                ))
                .orElseGet(() -> new StudyPackGenerationContext(
                        null,
                        note == null ? null : note.getCourseProgram(),
                        note == null ? null : note.getSubject(),
                        tags,
                        note == null ? null : note.getDomainContext(),
                        note == null ? null : note.getLearnerLevel()
                ));
    }

    public StudyPackGenerationContext resolveForStudyPack(UUID ownerUserId, StudyPackEntity studyPack) {
        Optional<NoteEntity> sourceNote = findSourceNote(ownerUserId, studyPack);
        if (sourceNote.isPresent()) {
            return resolve(ownerUserId, sourceNote.get());
        }

        List<String> tags = studyPack == null || studyPack.getTags() == null
                ? List.of()
                : Arrays.asList(studyPack.getTags());
        return userRepository.findById(ownerUserId)
                .map(user -> buildFromStudyPackFallback(user, studyPack, tags))
                .orElseGet(() -> new StudyPackGenerationContext(
                        null,
                        null,
                        studyPack == null ? null : studyPack.getSubject(),
                        tags,
                        null,
                        null
                ));
    }

    public StudyPackGenerationContext resolveForBulkGeneration(
            UUID ownerUserId,
            String courseProgram,
            String subject
    ) {
        return userRepository.findById(ownerUserId)
                .map(user -> new StudyPackGenerationContext(
                        user.getLearnerLevel(),
                        CourseProgramNormalizationUtils.normalizeForStorage(
                                firstNonBlank(courseProgram, user.getCourseProgram())
                        ),
                        subject,
                        List.of(),
                        null,
                        null
                ))
                .orElseGet(() -> new StudyPackGenerationContext(
                        null,
                        CourseProgramNormalizationUtils.normalizeForStorage(courseProgram),
                        subject,
                        List.of(),
                        null,
                        null
                ));
    }

    private Optional<NoteEntity> findSourceNote(UUID ownerUserId, StudyPackEntity studyPack) {
        if (studyPack == null || studyPack.getNoteId() == null) {
            return Optional.empty();
        }
        return noteRepository.findByIdAndOwnerUserId(studyPack.getNoteId(), ownerUserId);
    }

    private StudyPackGenerationContext buildFromStudyPackFallback(
            UserEntity user,
            StudyPackEntity studyPack,
            List<String> tags
    ) {
        return new StudyPackGenerationContext(
                user.getLearnerLevel(),
                user.getCourseProgram(),
                studyPack == null ? null : studyPack.getSubject(),
                tags,
                null,
                null
        );
    }

    public static String effectiveAuthoringDomain(StudyPackGenerationContext context) {
        if (context == null) {
            return null;
        }
        if (context.domainContext() != null) {
            return context.domainContext().getLabel();
        }
        return firstNonBlank(context.courseProgram(), null);
    }

    public static LearnerLevel effectiveCurriculumLevel(StudyPackGenerationContext context) {
        if (context == null) {
            return DEFAULT_LEARNER_LEVEL;
        }
        if (context.noteLearnerLevel() != null) {
            return context.noteLearnerLevel();
        }
        return context.learnerLevel() == null ? DEFAULT_LEARNER_LEVEL : context.learnerLevel();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
