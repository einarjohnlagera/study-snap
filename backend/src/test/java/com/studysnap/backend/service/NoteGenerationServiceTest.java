package com.studysnap.backend.service;

import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.exception.CourseProgramSelectionRequiredException;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;

import java.time.OffsetDateTime;
import com.studysnap.backend.exception.ProfileSetupRequiredException;
import com.studysnap.backend.exception.InvalidDomainContextException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteGenerationServiceTest {
    private static final String ENGINEERING_ALGEBRA_TOPIC = "Engineering Algebra";

    @Mock
    private UserRepository userRepository;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private CourseProgramCatalogRepository courseProgramCatalogRepository;
    @Mock
    private NoteCourseProgramRepository noteCourseProgramRepository;

    @Mock
    private LlmStudyPackService llmStudyPackService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private NoteGenerationUsageProtectionService noteGenerationUsageProtectionService;

    @Mock
    private ContentModerationService contentModerationService;

    @Mock
    private OnboardingGuardService onboardingGuardService;

    private NoteGenerationService noteGenerationService;

    @BeforeEach
    void setUp() {
        noteGenerationService = new NoteGenerationService(
                userRepository,
                subscriptionService,
                noteGenerationUsageProtectionService,
                llmStudyPackService,
                contentModerationService,
                onboardingGuardService,
                new StudyPackGenerationContextResolver(
                        userRepository,
                        noteRepository,
                        noteCourseProgramRepository,
                        courseProgramCatalogRepository
                ),
                courseProgramCatalogRepository
        );
    }

    @Test
    void generateFromTopic_rejectsMissingProfileTypeBeforeQuotaOrGeneration() {
        UUID userId = UUID.randomUUID();
        ProfileSetupRequiredException exception = new ProfileSetupRequiredException();
        doThrow(exception).when(onboardingGuardService).assertProfileComplete(userId);
        GenerateNoteFromTopicRequest request = new GenerateNoteFromTopicRequest("Ohm's Law", null, null);

        assertThatThrownBy(() -> noteGenerationService.generateFromTopic(request, userId))
                .isSameAs(exception);

        verify(noteGenerationUsageProtectionService, org.mockito.Mockito.never()).assertQuotaAvailable(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
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
                new GenerateNoteFromTopicRequest("  Newton's Laws of Motion  ", null, null),
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
        assertThat(contextCaptor.getValue().domainContext()).isNull();
        assertThat(contextCaptor.getValue().noteLearnerLevel()).isNull();
    }

    @Test
    void generateFromTopic_usesValidatedDomainContext() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Civil Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(llmStudyPackService.generateNoteFromTopic(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(StudyPackGenerationContext.class)
        )).thenReturn("Generated note content");

        noteGenerationService.generateFromTopic(
                new GenerateNoteFromTopicRequest(
                        ENGINEERING_ALGEBRA_TOPIC, "Civil Engineering", "engineering_mathematics"
                ),
                userId
        );

        ArgumentCaptor<StudyPackGenerationContext> contextCaptor =
                ArgumentCaptor.forClass(StudyPackGenerationContext.class);
        verify(llmStudyPackService).generateNoteFromTopic(
                org.mockito.ArgumentMatchers.any(), contextCaptor.capture()
        );
        assertThat(contextCaptor.getValue().domainContext())
                .isEqualTo(DomainContext.ENGINEERING_MATHEMATICS);
    }

    @Test
    void generateFromTopic_rejectsUnknownDomainContext() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        GenerateNoteFromTopicRequest request = new GenerateNoteFromTopicRequest(
                ENGINEERING_ALGEBRA_TOPIC, null, "engineering_math"
        );

        assertThatThrownBy(() -> noteGenerationService.generateFromTopic(request, userId))
                .isInstanceOf(InvalidDomainContextException.class)
                .hasMessageContaining("domainContext");
    }

    @Test
    void generateFromTopic_requestCourseProgramOverridesProfile() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(llmStudyPackService.generateNoteFromTopic(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(StudyPackGenerationContext.class)
        )).thenReturn("Generated note content");

        noteGenerationService.generateFromTopic(
                new GenerateNoteFromTopicRequest("Ohm's Law", "Civil Engineering", null),
                userId
        );

        ArgumentCaptor<StudyPackGenerationContext> contextCaptor = ArgumentCaptor.forClass(StudyPackGenerationContext.class);
        verify(llmStudyPackService).generateNoteFromTopic(org.mockito.ArgumentMatchers.any(), contextCaptor.capture());
        assertThat(contextCaptor.getValue().courseProgram()).isEqualTo("Civil Engineering");
    }

    @Test
    void generateFromTopic_acceptsRequestCourseProgramWhenProfileHasNone() {
        // Onboarding's first generation runs before the profile course/program is persisted, so the
        // request is the only source. Every other course/program test here gives the user a profile
        // value, which is why none of them caught the learner branch throwing on this exact shape and
        // making onboarding a dead end for every new user (finding B0).
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(llmStudyPackService.generateNoteFromTopic(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(StudyPackGenerationContext.class)
        )).thenReturn("Generated note content");

        noteGenerationService.generateFromTopic(
                new GenerateNoteFromTopicRequest("Newton's Laws of Motion", "AWS Certification", null),
                userId
        );

        ArgumentCaptor<StudyPackGenerationContext> contextCaptor = ArgumentCaptor.forClass(StudyPackGenerationContext.class);
        verify(llmStudyPackService).generateNoteFromTopic(org.mockito.ArgumentMatchers.any(), contextCaptor.capture());
        assertThat(contextCaptor.getValue().courseProgram()).isEqualTo("AWS Certification");
    }

    @Test
    void generateFromTopic_treatsAMidOnboardingAdminAsALearnerRatherThanACurator() {
        // Nobody curates during onboarding. An ADMIN reaching this mid-onboarding used to take the
        // curator branch, which ignores courseProgramText and demands courseProgramIds that no
        // onboarding screen can supply -- so onboarding threw COURSE_PROGRAM_SELECTION_REQUIRED and was
        // uncompletable for every admin account. There was no curator-branch test here at all, which is
        // why the wider change did not surface it.
        UUID userId = UUID.randomUUID();
        UserEntity admin = new UserEntity();
        admin.setId(userId);
        admin.setRole(UserRole.ADMIN);
        admin.setLearnerLevel(LearnerLevel.COLLEGE);
        admin.setCourseProgram(null);
        admin.setOnboardingCompletedAt(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(llmStudyPackService.generateNoteFromTopic(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(StudyPackGenerationContext.class)
        )).thenReturn("Generated note content");

        noteGenerationService.generateFromTopic(
                new GenerateNoteFromTopicRequest("Newton's Laws", "Accountancy", null),
                userId
        );

        ArgumentCaptor<StudyPackGenerationContext> contextCaptor = ArgumentCaptor.forClass(StudyPackGenerationContext.class);
        verify(llmStudyPackService).generateNoteFromTopic(org.mockito.ArgumentMatchers.any(), contextCaptor.capture());
        assertThat(contextCaptor.getValue().courseProgram()).isEqualTo("Accountancy");
    }

    @Test
    void generateFromTopic_stillRequiresCatalogProgramsForAnOnboardedAdmin() {
        // The guard above must be scoped to onboarding only -- a fully onboarded admin is a curator and
        // still authors through the catalog. Without this, the fix would silently demote every curator.
        UUID userId = UUID.randomUUID();
        UserEntity admin = new UserEntity();
        admin.setId(userId);
        admin.setRole(UserRole.ADMIN);
        admin.setLearnerLevel(LearnerLevel.COLLEGE);
        admin.setOnboardingCompletedAt(OffsetDateTime.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        GenerateNoteFromTopicRequest request =
                new GenerateNoteFromTopicRequest("Newton's Laws", "Accountancy", null);

        assertThatThrownBy(() -> noteGenerationService.generateFromTopic(request, userId))
                .isInstanceOf(CourseProgramSelectionRequiredException.class);
    }

    @Test
    void generateFromTopic_fallsBackToProfileCourseProgramWhenRequestCourseProgramIsBlank() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.PROFESSIONAL);
        user.setCourseProgram("Software Engineering");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(llmStudyPackService.generateNoteFromTopic(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(StudyPackGenerationContext.class)
        )).thenReturn("Generated note content");

        noteGenerationService.generateFromTopic(
                new GenerateNoteFromTopicRequest("Design Patterns", "   ", null),
                userId
        );

        ArgumentCaptor<StudyPackGenerationContext> contextCaptor = ArgumentCaptor.forClass(StudyPackGenerationContext.class);
        verify(llmStudyPackService).generateNoteFromTopic(org.mockito.ArgumentMatchers.any(), contextCaptor.capture());
        assertThat(contextCaptor.getValue().courseProgram()).isEqualTo("Software Engineering");
    }
}
