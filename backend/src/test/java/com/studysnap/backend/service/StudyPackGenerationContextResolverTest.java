package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
    void resolve_prefersNoteCourseProgramOverProfileCourseProgram() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NoteEntity note = new NoteEntity();
        note.setCourseProgram("Senior High – STEM");
        note.setSubject("Physics");
        note.setTags(new String[]{"electricity"});

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository, noteRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.learnerLevel()).isEqualTo(LearnerLevel.COLLEGE);
        assertThat(context.courseProgram()).isEqualTo("Senior High – STEM");
        assertThat(context.subject()).isEqualTo("Physics");
        assertThat(context.tags()).containsExactly("electricity");
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
    }
}
