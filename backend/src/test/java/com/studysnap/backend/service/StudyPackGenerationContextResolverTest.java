package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.UserEntity;
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

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository);

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

        StudyPackGenerationContextResolver resolver = new StudyPackGenerationContextResolver(userRepository);

        StudyPackGenerationContext context = resolver.resolve(userId, note);

        assertThat(context.courseProgram()).isEqualTo("Software Engineering");
    }
}
