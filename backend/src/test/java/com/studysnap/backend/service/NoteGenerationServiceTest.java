package com.studysnap.backend.service;

import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteGenerationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LlmStudyPackService llmStudyPackService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private NoteGenerationUsageProtectionService noteGenerationUsageProtectionService;

    @Mock
    private ContentModerationService contentModerationService;

    private NoteGenerationService noteGenerationService;

    @BeforeEach
    void setUp() {
        noteGenerationService = new NoteGenerationService(
                userRepository,
                subscriptionService,
                noteGenerationUsageProtectionService,
                llmStudyPackService,
                contentModerationService
        );
    }

    @Test
    void generateFromTopic_usesProfileContextAndReturnsGeneratedContent() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Senior High-STEM");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(llmStudyPackService.generateNoteFromTopic(
                org.mockito.ArgumentMatchers.eq("Newton's Laws of Motion"),
                org.mockito.ArgumentMatchers.any(StudyPackGenerationContext.class)
        )).thenReturn("Generated note content");

        GenerateNoteFromTopicResponse response = noteGenerationService.generateFromTopic(
                new GenerateNoteFromTopicRequest("  Newton's Laws of Motion  "),
                userId
        );

        ArgumentCaptor<StudyPackGenerationContext> contextCaptor = ArgumentCaptor.forClass(StudyPackGenerationContext.class);
        verify(noteGenerationUsageProtectionService).assertQuotaAvailable(userId, PlanType.FREE);
        verify(llmStudyPackService).generateNoteFromTopic(org.mockito.ArgumentMatchers.eq("Newton's Laws of Motion"), contextCaptor.capture());
        verify(noteGenerationUsageProtectionService).recordUsage(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any());
        assertThat(response).isEqualTo(new GenerateNoteFromTopicResponse("Generated note content"));
        assertThat(contextCaptor.getValue().learnerLevel()).isEqualTo(LearnerLevel.COLLEGE);
        assertThat(contextCaptor.getValue().courseProgram()).isEqualTo("Senior High – STEM");
        assertThat(contextCaptor.getValue().subject()).isNull();
        assertThat(contextCaptor.getValue().tags()).isEmpty();
    }
}
