package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.PublicSharedQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.QuizShareLinkResponse;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuizShareLinkEntity;
import com.studysnap.backend.exception.QuizShareLinkLimitExceededException;
import com.studysnap.backend.exception.QuizShareLinkNotAllowedException;
import com.studysnap.backend.exception.QuizShareLinkNotFoundException;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuizShareLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizShareLinkServiceTest {
    private static final String TEST_TOKEN = "abc123";

    @Mock
    private QuizShareLinkRepository quizShareLinkRepository;
    @Mock
    private GeneratedQuizRepository generatedQuizRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private QuizShareLimitService quizShareLimitService;
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private AuthService authService;
    @Mock
    private OnboardingGuardService onboardingGuardService;

    private QuizShareLinkService quizShareLinkService;

    @BeforeEach
    void setUp() {
        quizShareLinkService = new QuizShareLinkService(
                quizShareLinkRepository,
                generatedQuizRepository,
                noteRepository,
                onboardingGuardService,
                quizShareLimitService,
                userUsageService,
                authService,
                new StudySnapProperties()
        );
    }

    @Test
    void nonTeacherCanCreateShareLinkAndRecordUsage() {
        UUID ownerUserId = UUID.randomUUID();
        UUID generatedQuizId = UUID.randomUUID();
        GeneratedQuizEntity generatedQuiz = buildGeneratedQuiz(generatedQuizId, ownerUserId, UUID.randomUUID());
        when(generatedQuizRepository.findByIdAndOwnerUserId(generatedQuizId, ownerUserId)).thenReturn(Optional.of(generatedQuiz));
        when(quizShareLinkRepository.findFirstByGeneratedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(generatedQuizId, ownerUserId))
                .thenReturn(Optional.empty());
        when(quizShareLinkRepository.findByToken(anyString())).thenReturn(Optional.empty());
        when(quizShareLinkRepository.save(any(QuizShareLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuizShareLinkResponse response = quizShareLinkService.createShareLink(generatedQuizId, ownerUserId);

        var orderedGuards = inOrder(authService, onboardingGuardService);
        orderedGuards.verify(authService).requireEmailVerified(ownerUserId);
        orderedGuards.verify(onboardingGuardService).assertProfileComplete(ownerUserId);
        verify(quizShareLimitService).assertShareLinkQuotaNotExceeded(ownerUserId);
        verify(userUsageService).incrementQuizShareLinkCreated(eq(ownerUserId), any(OffsetDateTime.class));
        assertThat(response.token()).hasSize(16);
        assertThat(response.shareUrl()).endsWith("/quiz/" + response.token());
        assertThat(response.isActive()).isTrue();

        ArgumentCaptor<QuizShareLinkEntity> captor = ArgumentCaptor.forClass(QuizShareLinkEntity.class);
        verify(quizShareLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getGeneratedQuizId()).isEqualTo(generatedQuizId);
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(ownerUserId);
    }

    @Test
    void createShareLinkBlocksWhenQuotaExceeded() {
        UUID ownerUserId = UUID.randomUUID();
        UUID generatedQuizId = UUID.randomUUID();
        GeneratedQuizEntity generatedQuiz = buildGeneratedQuiz(generatedQuizId, ownerUserId, UUID.randomUUID());
        when(generatedQuizRepository.findByIdAndOwnerUserId(generatedQuizId, ownerUserId)).thenReturn(Optional.of(generatedQuiz));
        when(quizShareLinkRepository.findFirstByGeneratedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(generatedQuizId, ownerUserId))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(QuizShareLinkLimitExceededException.forPlan(PlanType.FREE))
                .when(quizShareLimitService)
                .assertShareLinkQuotaNotExceeded(ownerUserId);

        assertThatThrownBy(() -> quizShareLinkService.createShareLink(generatedQuizId, ownerUserId))
                .isInstanceOf(QuizShareLinkLimitExceededException.class);

        verify(quizShareLinkRepository, never()).save(any(QuizShareLinkEntity.class));
        verify(userUsageService, never()).incrementQuizShareLinkCreated(eq(ownerUserId), any(OffsetDateTime.class));
    }

    @Test
    void createShareLinkReturnsExistingLinkWithoutConsumingQuota() {
        UUID ownerUserId = UUID.randomUUID();
        UUID generatedQuizId = UUID.randomUUID();
        QuizShareLinkEntity existing = buildLink(generatedQuizId, ownerUserId, TEST_TOKEN, true);
        GeneratedQuizEntity generatedQuiz = buildGeneratedQuiz(generatedQuizId, ownerUserId, UUID.randomUUID());
        when(generatedQuizRepository.findByIdAndOwnerUserId(generatedQuizId, ownerUserId)).thenReturn(Optional.of(generatedQuiz));
        when(quizShareLinkRepository.findFirstByGeneratedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(generatedQuizId, ownerUserId))
                .thenReturn(Optional.of(existing));

        QuizShareLinkResponse response = quizShareLinkService.createShareLink(generatedQuizId, ownerUserId);

        assertThat(response.token()).isEqualTo(TEST_TOKEN);
        verify(quizShareLimitService, never()).assertShareLinkQuotaNotExceeded(ownerUserId);
        verify(quizShareLinkRepository, never()).save(any(QuizShareLinkEntity.class));
        verify(userUsageService, never()).incrementQuizShareLinkCreated(eq(ownerUserId), any(OffsetDateTime.class));
    }

    @Test
    void getActivePublicQuizRejectsInactiveLink() {
        QuizShareLinkEntity link = buildLink(UUID.randomUUID(), UUID.randomUUID(), TEST_TOKEN, false);
        when(quizShareLinkRepository.findByToken(TEST_TOKEN)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> quizShareLinkService.getActivePublicQuiz(TEST_TOKEN))
                .isInstanceOf(QuizShareLinkNotFoundException.class);
    }

    @Test
    void toggleShareLinkRejectsNonOwner() {
        UUID callerUserId = UUID.randomUUID();
        QuizShareLinkEntity link = buildLink(UUID.randomUUID(), UUID.randomUUID(), TEST_TOKEN, true);
        when(quizShareLinkRepository.findByToken(TEST_TOKEN)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> quizShareLinkService.toggleShareLink(TEST_TOKEN, callerUserId))
                .isInstanceOf(QuizShareLinkNotAllowedException.class);
    }

    @Test
    void nonTeacherCanFetchOwnShareLink() {
        UUID ownerUserId = UUID.randomUUID();
        UUID generatedQuizId = UUID.randomUUID();
        QuizShareLinkEntity link = buildLink(generatedQuizId, ownerUserId, TEST_TOKEN, true);
        when(quizShareLinkRepository.findFirstByGeneratedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(
                generatedQuizId,
                ownerUserId
        )).thenReturn(Optional.of(link));

        QuizShareLinkResponse response = quizShareLinkService.getShareLinkByQuizId(generatedQuizId, ownerUserId);

        verify(onboardingGuardService).assertProfileComplete(ownerUserId);
        assertThat(response.token()).isEqualTo(TEST_TOKEN);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void nonTeacherCanToggleOwnShareLink() {
        UUID ownerUserId = UUID.randomUUID();
        QuizShareLinkEntity link = buildLink(UUID.randomUUID(), ownerUserId, TEST_TOKEN, true);
        when(quizShareLinkRepository.findByToken(TEST_TOKEN)).thenReturn(Optional.of(link));
        when(quizShareLinkRepository.save(link)).thenReturn(link);

        QuizShareLinkResponse response = quizShareLinkService.toggleShareLink(TEST_TOKEN, ownerUserId);

        var orderedGuards = inOrder(authService, onboardingGuardService);
        orderedGuards.verify(authService).requireEmailVerified(ownerUserId);
        orderedGuards.verify(onboardingGuardService).assertProfileComplete(ownerUserId);
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void createShareLinkStillRequiresEmailVerification() {
        UUID ownerUserId = UUID.randomUUID();
        UUID generatedQuizId = UUID.randomUUID();
        IllegalStateException emailVerificationFailure = new IllegalStateException("email verification required");
        doThrow(emailVerificationFailure).when(authService).requireEmailVerified(ownerUserId);

        assertThatThrownBy(() -> quizShareLinkService.createShareLink(generatedQuizId, ownerUserId))
                .isSameAs(emailVerificationFailure);

        verify(onboardingGuardService, never()).assertProfileComplete(any(UUID.class));
        verify(generatedQuizRepository, never()).findByIdAndOwnerUserId(any(UUID.class), any(UUID.class));
    }

    @Test
    void toggleShareLinkStillRequiresEmailVerification() {
        UUID ownerUserId = UUID.randomUUID();
        IllegalStateException emailVerificationFailure = new IllegalStateException("email verification required");
        doThrow(emailVerificationFailure).when(authService).requireEmailVerified(ownerUserId);

        assertThatThrownBy(() -> quizShareLinkService.toggleShareLink(TEST_TOKEN, ownerUserId))
                .isSameAs(emailVerificationFailure);

        verify(onboardingGuardService, never()).assertProfileComplete(any(UUID.class));
        verify(quizShareLinkRepository, never()).findByToken(anyString());
    }

    @Test
    void getActivePublicQuizStripsAnswers() {
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID generatedQuizId = UUID.randomUUID();
        QuizShareLinkEntity link = buildLink(generatedQuizId, ownerUserId, TEST_TOKEN, true);
        GeneratedQuizEntity generatedQuiz = buildGeneratedQuiz(generatedQuizId, ownerUserId, noteId);
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setTitle("Cell Structure");
        when(quizShareLinkRepository.findByToken(TEST_TOKEN)).thenReturn(Optional.of(link));
        when(generatedQuizRepository.findById(generatedQuizId)).thenReturn(Optional.of(generatedQuiz));
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note));

        PublicSharedQuizResponse response = quizShareLinkService.getActivePublicQuiz(TEST_TOKEN);

        assertThat(response.noteTitle()).isEqualTo("Cell Structure");
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().getFirst().choices()).containsExactly("A", "B", "C", "D");
    }

    private GeneratedQuizEntity buildGeneratedQuiz(UUID id, UUID ownerUserId, UUID noteId) {
        GeneratedQuizEntity generatedQuiz = new GeneratedQuizEntity();
        generatedQuiz.setId(id);
        generatedQuiz.setOwnerUserId(ownerUserId);
        generatedQuiz.setNoteId(noteId);
        generatedQuiz.setGeneratedAt(OffsetDateTime.now());
        generatedQuiz.setUpdatedAt(OffsetDateTime.now());
        generatedQuiz.setQuestions(List.of(new QuizItem("Question?", List.of("A", "B", "C", "D"), 1, "Concept", "Because B")));
        return generatedQuiz;
    }

    private QuizShareLinkEntity buildLink(UUID generatedQuizId, UUID ownerUserId, String token, boolean active) {
        QuizShareLinkEntity link = new QuizShareLinkEntity();
        link.setId(UUID.randomUUID());
        link.setGeneratedQuizId(generatedQuizId);
        link.setOwnerUserId(ownerUserId);
        link.setToken(token);
        link.setActive(active);
        link.setCreatedAt(OffsetDateTime.now());
        return link;
    }

}
