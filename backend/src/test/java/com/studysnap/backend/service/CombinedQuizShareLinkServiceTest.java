package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.CombinedQuizSection;
import com.studysnap.backend.dto.PublicSharedQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.QuizShareLinkResponse;
import com.studysnap.backend.dto.SharedQuizResultsResponse;
import com.studysnap.backend.entity.CombinedQuizEntity;
import com.studysnap.backend.entity.QuizShareLinkEntity;
import com.studysnap.backend.exception.QuizShareLinkNotFoundException;
import com.studysnap.backend.repository.CombinedQuizRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuizShareLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CombinedQuizShareLinkServiceTest {
    @Mock private QuizShareLinkRepository linkRepository;
    @Mock private GeneratedQuizRepository generatedQuizRepository;
    @Mock private CombinedQuizRepository combinedQuizRepository;
    @Mock private NoteRepository noteRepository;
    @Mock private OnboardingGuardService onboardingGuardService;
    @Mock private QuizShareLimitService limitService;
    @Mock private UserUsageService userUsageService;
    @Mock private AuthService authService;

    private QuizShareLinkService service;

    @BeforeEach
    void setUp() {
        service = new QuizShareLinkService(linkRepository, generatedQuizRepository, combinedQuizRepository,
                noteRepository, onboardingGuardService, limitService, userUsageService, authService, new StudySnapProperties());
    }

    @Test
    void combinedQuizIsFlatPublicPayloadWithoutAnswerKeyAndMultiSelectGradesExactly() {
        UUID quizId = UUID.randomUUID();
        QuizShareLinkEntity link = link(quizId, true);
        CombinedQuizEntity quiz = combinedQuiz(quizId, List.of(
                new CombinedQuizSection("First note", List.of(new QuizItem(
                        "Select both", List.of("A", "B", "C"), 0, "Concept", "Explanation", null,
                        "MULTI_SELECT", null, null, List.of(0, 2)
                ))),
                new CombinedQuizSection("Second note", List.of(new QuizItem("Second", List.of("A", "B"), 1, "Other", "Hidden")))
        ));
        when(linkRepository.findByToken("token")).thenReturn(Optional.of(link));
        when(combinedQuizRepository.findById(quizId)).thenReturn(Optional.of(quiz));

        PublicSharedQuizResponse publicQuiz = service.getActivePublicQuiz("token");
        SharedQuizResultsResponse results = service.getSharedQuizResults(
                "token", Arrays.asList(null, 1), Arrays.asList(List.of(2, 0), null));

        assertThat(publicQuiz.noteTitle()).isEqualTo("Whole unit");
        assertThat(publicQuiz.questions()).hasSize(2).extracting(question -> question.question())
                .containsExactly("Select both", "Second");
        assertThat(publicQuiz.questions().getFirst().questionFormat()).isEqualTo("MULTI_SELECT");
        assertThat(publicQuiz.questions().getFirst().getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("correctIndex", "correctIndices", "explanation");
        assertThat(results.score()).isEqualTo(2);
        assertThat(results.items().getFirst().correctIndices()).containsExactly(0, 2);
    }

    /** Killing test for returning any existing link instead of only an active link. */
    /**
     * ⚠️ LOAD ON REFRESH. Without a read endpoint the only way back to an existing link is to POST, and on a
     * link the owner has toggled OFF that POST does not return it -- the idempotent early return requires an
     * ACTIVE link -- so it mints a NEW link and spends share-link quota on a page refresh.
     */
    /**
     * ⚠️ The combined arc had NO inactive-link coverage: every fixture built an ACTIVE link, so deleting
     * the {@code isActive} guard in {@code findActiveLink} passed the whole suite. If that guard were ever
     * lost, "Turn sharing off" would become inert and a revoked public quiz would stay live —
     * {@code shareable-quiz-links.md} promises a 404 and nothing executed it for this path.
     */
    @Test
    void takingACombinedQuizThroughAnInactiveLinkIsNotFound() {
        UUID quizId = UUID.randomUUID();
        QuizShareLinkEntity inactive = link(quizId, false);
        when(linkRepository.findByToken(anyString())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getActivePublicQuiz(inactive.getToken()))
                .isInstanceOf(QuizShareLinkNotFoundException.class);
        assertThatThrownBy(() -> service.getSharedQuizResults(inactive.getToken(), List.of(), null))
                .isInstanceOf(QuizShareLinkNotFoundException.class);

        // The guard must short-circuit BEFORE the snapshot is read, or an inactive link still discloses it.
        verify(combinedQuizRepository, never()).findById(any(UUID.class));
    }

    @Test
    void combinedShareLinkIsReadableWithoutCreatingOrSpendingQuota() {
        UUID quizId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        QuizShareLinkEntity existing = link(quizId, false);
        when(linkRepository.findFirstByCombinedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(quizId, owner))
                .thenReturn(Optional.of(existing));

        QuizShareLinkResponse response = service.getCombinedQuizShareLink(quizId, owner);

        verify(onboardingGuardService).assertProfileComplete(owner);
        // An INACTIVE link is still readable -- that is the whole point; the owner has to see it to re-enable it.
        assertThat(response.isActive()).isFalse();
        assertThat(response.token()).isEqualTo(existing.getToken());
        verify(limitService, never()).assertShareLinkQuotaNotExceeded(any(UUID.class));
        verify(userUsageService, never()).incrementQuizShareLinkCreated(any(UUID.class), any(OffsetDateTime.class));
        verify(linkRepository, never()).save(any(QuizShareLinkEntity.class));
    }

    @Test
    void combinedShareLinkReadIsNotFoundWhenNoLinkExists() {
        UUID quizId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        when(linkRepository.findFirstByCombinedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(quizId, owner))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCombinedQuizShareLink(quizId, owner))
                .isInstanceOf(QuizShareLinkNotFoundException.class);
    }

    @Test
    void combinedShareLinkReusesOnlyAnActiveLinkAndKeepsTheGuardAndUsageOrder() {
        UUID owner = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        CombinedQuizEntity quiz = combinedQuiz(quizId, List.of());
        QuizShareLinkEntity inactive = link(quizId, false);
        when(combinedQuizRepository.findByIdAndOwnerUserId(quizId, owner)).thenReturn(Optional.of(quiz));
        when(linkRepository.findFirstByCombinedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(quizId, owner))
                .thenReturn(Optional.of(inactive));
        when(linkRepository.findByToken(anyString())).thenReturn(Optional.empty());
        when(linkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QuizShareLinkResponse response = service.createCombinedQuizShareLink(quizId, owner);

        assertThat(response.token()).hasSize(16);
        var order = inOrder(authService, onboardingGuardService);
        order.verify(authService).requireEmailVerified(owner);
        order.verify(onboardingGuardService).assertProfileComplete(owner);
        verify(limitService).assertShareLinkQuotaNotExceeded(owner);
        verify(userUsageService).incrementQuizShareLinkCreated(eq(owner), any(OffsetDateTime.class));
        verify(linkRepository).save(any(QuizShareLinkEntity.class));
    }

    @Test
    void combinedShareLinkReturnsAnExistingActiveLinkWithoutQuotaOrUsage() {
        UUID owner = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        CombinedQuizEntity quiz = combinedQuiz(quizId, List.of());
        QuizShareLinkEntity active = link(quizId, true);
        when(combinedQuizRepository.findByIdAndOwnerUserId(quizId, owner)).thenReturn(Optional.of(quiz));
        when(linkRepository.findFirstByCombinedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(quizId, owner))
                .thenReturn(Optional.of(active));

        QuizShareLinkResponse response = service.createCombinedQuizShareLink(quizId, owner);

        assertThat(response.token()).isEqualTo("token");
        verify(limitService, never()).assertShareLinkQuotaNotExceeded(any());
        verify(userUsageService, never()).incrementQuizShareLinkCreated(any(), any());
        verify(linkRepository, never()).save(any());
    }

    private static CombinedQuizEntity combinedQuiz(UUID id, List<CombinedQuizSection> sections) {
        CombinedQuizEntity quiz = new CombinedQuizEntity();
        quiz.setId(id);
        quiz.setTitle("Whole unit");
        quiz.setSections(sections);
        return quiz;
    }

    private static QuizShareLinkEntity link(UUID combinedQuizId, boolean active) {
        QuizShareLinkEntity link = new QuizShareLinkEntity();
        link.setId(UUID.randomUUID());
        link.setCombinedQuizId(combinedQuizId);
        link.setToken("token");
        link.setActive(active);
        link.setCreatedAt(OffsetDateTime.now());
        return link;
    }
}
