package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyPackGenerationContextResolverTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;

    @Test
    void resolve_populatesNoteAuthoringAxesAndPrefersDomainContext() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setCourseProgram("Senior High – STEM");
        note.setDomainContext(DomainContext.ENGINEERING_SCIENCES);
        note.setLearnerLevel(LearnerLevel.SENIOR_HIGH);
        note.setSubject("Physics");
        note.setTags(new String[]{"electricity"});

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.COLLEGE);
        assertThat(context.courseProgram()).isEqualTo("Senior High – STEM");
        assertThat(context.subject()).isEqualTo("Physics");
        assertThat(context.tags()).containsExactly("electricity");
        assertThat(context.domainContext()).isEqualTo(DomainContext.ENGINEERING_SCIENCES);
        assertThat(context.noteLearnerLevel()).isEqualTo(LearnerLevel.SENIOR_HIGH);
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context))
                .isEqualTo("Engineering Sciences");
        assertThat(StudyPackGenerationContextResolver.effectiveCurriculumLevel(context))
                .isEqualTo(LearnerLevel.SENIOR_HIGH);
    }

    @Test
    void resolve_usesNoteProgramAsAuthoringDomainWhenDomainContextIsMissing() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.PROFESSIONAL);
        user.setCourseProgram("Education");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setCourseProgram("Science");
        note.setSubject("Biology");

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.PROFESSIONAL);
        assertThat(context.courseProgram()).isEqualTo("Science");
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context)).isEqualTo("Science");
        assertThat(StudyPackGenerationContextResolver.effectiveCurriculumLevel(context))
                .isEqualTo(LearnerLevel.PROFESSIONAL);
    }

    @Test
    void resolve_fallsBackToProfileCourseProgramWhenNoteCourseProgramIsMissing() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.PROFESSIONAL);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setCourseProgram("   ");
        note.setSubject("Computing");
        note.setTags(new String[]{"algorithms"});

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.courseProgram()).isEqualTo("Software Engineering");
        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context))
                .isEqualTo("Software Engineering");
    }

    @Test
    void resolveStudyPack_prefersSourceNoteCourseProgramOverProfileCourseProgram() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setCourseProgram("Senior High STEM");
        note.setDomainContext(DomainContext.GENERAL_EDUCATION);
        note.setLearnerLevel(LearnerLevel.SENIOR_HIGH);
        note.setSubject("Physics");
        note.setTags(new String[]{"ohms-law"});
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));

        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setNoteId(noteId);
        studyPack.setSubject("Fallback Physics");
        studyPack.setTags(new String[]{"fallback"});

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository);

        StudyPackGenerationContext context = resolver.resolveForStudyPack(userId, studyPack);

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.COLLEGE);
        assertThat(context.courseProgram()).isEqualTo("Senior High STEM");
        assertThat(context.subject()).isEqualTo("Physics");
        assertThat(context.tags()).containsExactly("ohms-law");
        assertThat(context.domainContext()).isEqualTo(DomainContext.GENERAL_EDUCATION);
        assertThat(context.noteLearnerLevel()).isEqualTo(LearnerLevel.SENIOR_HIGH);
    }

    @Test
    void resolveStudyPack_fallsBackToProfileCourseProgramWhenSourceNoteIsMissing() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.PROFESSIONAL);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.empty());

        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setNoteId(noteId);
        studyPack.setSubject("Computing");
        studyPack.setTags(new String[]{"algorithms"});

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository);

        StudyPackGenerationContext context = resolver.resolveForStudyPack(userId, studyPack);

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.PROFESSIONAL);
        assertThat(context.courseProgram()).isEqualTo("Software Engineering");
        assertThat(context.subject()).isEqualTo("Computing");
        assertThat(context.tags()).containsExactly("algorithms");
        assertThat(context.domainContext()).isNull();
        assertThat(context.noteLearnerLevel()).isNull();
    }

    @Test
    void resolveForBulkGeneration_usesProfileLearnerLevelForPoolPrewarm() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.BOARD_EXAM_REVIEW);
        user.setCourseProgram("Profile Course");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository);

        StudyPackGenerationContext context = resolver.resolveForBulkGeneration(
                userId,
                "Nursing",
                "Maternal Health",
                DomainContext.NURSING,
                LearnerLevel.BOARD_EXAM_REVIEW
        );

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW);
        assertThat(context.courseProgram()).isEqualTo("Nursing");
        assertThat(context.subject()).isEqualTo("Maternal Health");
        assertThat(context.domainContext()).isEqualTo(DomainContext.NURSING);
        assertThat(context.noteLearnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW);
    }

    @Test
    void resolveForBulkGeneration_allowsMissingProfileLearnerLevel() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCourseProgram("Nursing");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository);

        StudyPackGenerationContext context = resolver.resolveForBulkGeneration(
                userId,
                "Nursing",
                "Maternal Health",
                null,
                null
        );

        assertThat(context.learnerLevel()).isNull();
        assertThat(context.domainContext()).isNull();
        assertThat(context.noteLearnerLevel()).isNull();
        assertThat(StudyPackGenerationContextResolver.effectiveCurriculumLevel(context))
                .isEqualTo(LearnerLevel.COLLEGE);
    }

    @Test
    void resolve_allowsNoAuthoringDomainAndDefaultsCurriculumToCollege() {
        UUID userId = UUID.randomUUID();
        NoteEntity note = new NoteEntity();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(StudyPackGenerationContextResolver.effectiveAuthoringDomain(context)).isNull();
        assertThat(StudyPackGenerationContextResolver.effectiveCurriculumLevel(context))
                .isEqualTo(LearnerLevel.COLLEGE);
    }
}
