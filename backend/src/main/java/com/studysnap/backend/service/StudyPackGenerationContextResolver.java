package com.studysnap.backend.service;

import com.studysnap.backend.dto.ApplicableProgramResponse;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.MultiProgramDomainContextRequiredException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
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
    private final NoteCourseProgramRepository noteCourseProgramRepository;
    private final CourseProgramCatalogRepository courseProgramCatalogRepository;

    public void assertGenerationReady(NoteEntity note) {
        if (note != null
                && note.getDomainContext() == null
                && noteCourseProgramRepository.findIdsByNoteId(note.getId()).size() > 1) {
            throw new MultiProgramDomainContextRequiredException();
        }
    }

    public StudyPackGenerationContext resolve(UUID ownerUserId, NoteEntity note) {
        List<String> tags = note == null || note.getTags() == null
                ? List.of()
                : Arrays.asList(note.getTags());

        return userRepository.findById(ownerUserId)
                .map(user -> new StudyPackGenerationContext(
                        user.getLearnerLevel(),
                        resolveCourseProgram(note, user),
                        note == null ? null : note.getSubject(),
                        tags,
                        note == null ? null : note.getDomainContext(),
                        note == null ? null : note.getLearnerLevel()
                ))
                .orElseGet(() -> new StudyPackGenerationContext(
                        null,
                        resolveCourseProgram(note, null),
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
            List<UUID> courseProgramIds,
            String courseProgramText,
            String subject,
            DomainContext domainContext,
            LearnerLevel noteLearnerLevel
    ) {
        return userRepository.findById(ownerUserId)
                .map(user -> new StudyPackGenerationContext(
                        user.getLearnerLevel(),
                        resolveBulkCourseProgram(courseProgramIds, courseProgramText, user),
                        subject,
                        List.of(),
                        domainContext,
                        noteLearnerLevel
                ))
                .orElseGet(() -> new StudyPackGenerationContext(
                        null,
                        resolveBulkCourseProgram(courseProgramIds, courseProgramText, null),
                        subject,
                        List.of(),
                        domainContext,
                        noteLearnerLevel
                ));
    }

    public StudyPackGenerationContext resolveForBulkGeneration(
            UUID ownerUserId,
            String courseProgramText,
            String subject,
            DomainContext domainContext,
            LearnerLevel noteLearnerLevel
    ) {
        return resolveForBulkGeneration(ownerUserId, List.of(), courseProgramText, subject, domainContext, noteLearnerLevel);
    }

    private Optional<NoteEntity> findSourceNote(UUID ownerUserId, StudyPackEntity studyPack) {
        if (studyPack == null || studyPack.getNoteId() == null) {
            return Optional.empty();
        }
        return noteRepository.findByIdAndOwnerUserId(studyPack.getNoteId(), ownerUserId);
    }

    private String resolveCourseProgram(NoteEntity note, UserEntity user) {
        String profileCourseProgram = user != null && !CuratorAuthoringPredicate.isCurator(user)
                ? user.getCourseProgram()
                : null;
        if (note == null) {
            return CourseProgramNormalizationUtils.normalizeForStorage(profileCourseProgram);
        }
        List<String> joinedPrograms = noteCourseProgramRepository.findByNoteId(note.getId()).stream()
                .map(ApplicableProgramResponse::name)
                .toList();
        // Exactly one catalog program may reach a prompt as the domain; a list may not (ADR-001).
        // Everything else falls through to the personal-notes string, which is a permanent path and
        // not a legacy branch -- it is how every learner-authored note resolves its domain.
        if (joinedPrograms.size() == 1) {
            return joinedPrograms.getFirst();
        }
        return CourseProgramNormalizationUtils.normalizeForStorage(firstNonBlank(note.getCourseProgram(), profileCourseProgram));
    }

    private String resolveBulkCourseProgram(
            List<UUID> courseProgramIds,
            String courseProgramText,
            UserEntity user
    ) {
        List<UUID> ids = courseProgramIds == null ? List.of() : courseProgramIds;
        if (ids.size() == 1) {
            List<String> names = courseProgramCatalogRepository.findNamesByIds(ids);
            if (names.size() == 1) {
                return names.getFirst();
            }
        }
        String profileCourseProgram = user != null && !CuratorAuthoringPredicate.isCurator(user)
                ? user.getCourseProgram()
                : null;
        return CourseProgramNormalizationUtils.normalizeForStorage(firstNonBlank(courseProgramText, profileCourseProgram));
    }

    private StudyPackGenerationContext buildFromStudyPackFallback(
            UserEntity user,
            StudyPackEntity studyPack,
            List<String> tags
    ) {
        // ⚠️ Guarded for the same reason as resolveCourseProgram, not left to construction. This branch is
        // currently unreachable with a curator whose profile could leak -- study_packs.note_id is NOT NULL
        // with an FK, so a miss needs pack-owner != note-owner -- but "unreachable today" is not the rule
        // the other two sites follow, and a reader cannot tell an omission from a decision.
        return new StudyPackGenerationContext(
                user.getLearnerLevel(),
                CuratorAuthoringPredicate.isCurator(user) ? null : user.getCourseProgram(),
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
