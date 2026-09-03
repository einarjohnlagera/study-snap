package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.PublicSharedQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.QuizShareLinkResponse;
import com.studysnap.backend.dto.SharedQuizResultsRequest;
import com.studysnap.backend.dto.SharedQuizResultsResponse;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuizShareLinkEntity;
import com.studysnap.backend.exception.InvalidSharedQuizAnswersException;
import com.studysnap.backend.exception.QuizShareLinkLimitExceededException;
import com.studysnap.backend.exception.QuizShareLinkNotAllowedException;
import com.studysnap.backend.exception.QuizShareLinkNotFoundException;
import com.studysnap.backend.repository.CombinedQuizRepository;
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
import java.util.Collections;
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
    private static final String MULTI_SELECT_FORMAT = "MULTI_SELECT";
    private static final List<String> CHOICES = List.of("A", "B", "C", "D");

    @Mock
    private QuizShareLinkRepository quizShareLinkRepository;
    @Mock
    private GeneratedQuizRepository generatedQuizRepository;
    @Mock
    private CombinedQuizRepository combinedQuizRepository;
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
                combinedQuizRepository,
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
    void getActivePublicQuizReturnsTheQuestionsWithoutTheirAnswers() {
        UUID noteId = UUID.randomUUID();
        stubActiveQuizWithNote(noteId, "Cell Structure", List.of(
                new QuizItem("Question?", CHOICES, 1, "Concept", "Because B")
        ));

        PublicSharedQuizResponse response = quizShareLinkService.getActivePublicQuiz(TEST_TOKEN);

        assertThat(response.noteTitle()).isEqualTo("Cell Structure");
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().getFirst().choices()).containsExactly("A", "B", "C", "D");
    }

    /**
     * ⚠️ THE ANSWER-KEY GUARD, AND IT ASSERTS ON THE SERIALIZED PAYLOAD ON PURPOSE. A field-level assertion
     * has no reachable subject — {@code PublicQuizItem} simply has no answer field to interrogate — so it
     * would pass no matter what a later change added to that record. Serializing gives the negative
     * assertion something that exists either way: the moment anyone puts {@code correctIndices},
     * {@code correctIndex} or {@code explanation} back on the pre-answer payload, this reds. Adding
     * {@code correctIndices} is a live path precisely because "PublicQuizItem drops correctIndices" is how
     * the v0.110.0 MULTI_SELECT defect is written up.
     */
    @Test
    void getActivePublicQuizNeverDisclosesTheAnswerKeyToTheRecipient() throws Exception {
        UUID noteId = UUID.randomUUID();
        stubActiveQuizWithNote(noteId, "Cell Structure", List.of(multiSelectQuestion()));

        PublicSharedQuizResponse response = quizShareLinkService.getActivePublicQuiz(TEST_TOKEN);
        String payload = new ObjectMapper().writeValueAsString(response);

        assertThat(payload)
                .contains("questionFormat")
                .doesNotContain("correctIndices")
                .doesNotContain("correctIndex")
                .doesNotContain("explanation")
                .doesNotContain("Because A and C");
    }

    /**
     * ⚠️ THE DISCRIMINATING CASE. {@code QuizItem.correctIndex()} falls back to
     * {@code correctIndices.getFirst()} for MULTI_SELECT, so the replaced {@code answer == correctIndex}
     * comparison marked "picked only choice 0" CORRECT and "picked 0 and 2" WRONG — exactly inverted.
     * A fixture asserting only the happy path would pass under both implementations for the second
     * question, so the partial-credit assertions below are what actually pin the fix.
     */
    @Test
    void sharedQuizGradesMultiSelectAsAnExactSet() {
        SharedQuizResultsResponse allCorrect = gradeMultiSelectQuiz(List.of(0, 2));

        assertThat(allCorrect.score()).isEqualTo(1);
        assertThat(allCorrect.items().getFirst().correct()).isTrue();
        assertThat(allCorrect.items().getFirst().correctIndices()).containsExactly(0, 2);
    }

    @Test
    void sharedQuizScoresZeroWhenOnlyTheFirstCorrectChoiceIsSelected() {
        // Choice 0 IS correctIndices.getFirst(), so this submission is the one the defect rewarded with
        // full marks. Under exact-set grading a partial answer is wrong.
        SharedQuizResultsResponse firstOnly = gradeMultiSelectQuiz(List.of(0));

        assertThat(firstOnly.score()).isZero();
        assertThat(firstOnly.items().getFirst().correct()).isFalse();
    }

    @Test
    void sharedQuizScoresZeroWhenOnlyTheSecondCorrectChoiceIsSelected() {
        SharedQuizResultsResponse secondOnly = gradeMultiSelectQuiz(List.of(2));

        assertThat(secondOnly.score()).isZero();
        assertThat(secondOnly.items().getFirst().correct()).isFalse();
    }

    @Test
    void sharedQuizIgnoresOutOfRangeAndDuplicateMultiSelectIndexes() {
        SharedQuizResultsResponse response = gradeMultiSelectQuiz(List.of(2, 0, 2, 9, -1));

        assertThat(response.score()).isEqualTo(1);
        assertThat(response.items().getFirst().correct()).isTrue();
    }

    @Test
    void sharedQuizStillGradesSingleChoiceQuestions() {
        UUID generatedQuizId = UUID.randomUUID();
        stubActiveQuiz(generatedQuizId, List.of(
                new QuizItem("Single?", CHOICES, 1, "Concept", "Because B")
        ));

        SharedQuizResultsResponse correct = quizShareLinkService.getSharedQuizResults(
                TEST_TOKEN,
                Collections.singletonList(1),
                null
        );
        SharedQuizResultsResponse wrong = quizShareLinkService.getSharedQuizResults(
                TEST_TOKEN,
                Collections.singletonList(3),
                null
        );

        assertThat(correct.score()).isEqualTo(1);
        assertThat(correct.items().getFirst().correctIndex()).isEqualTo(1);
        // Only MULTI_SELECT discloses a set, so the review screen's "non-empty means use these" rule
        // cannot mis-highlight a single-choice question.
        assertThat(correct.items().getFirst().correctIndices()).isEmpty();
        assertThat(wrong.score()).isZero();
    }

    @Test
    void sharedQuizRejectsMultiAnswersOfTheWrongLength() {
        UUID generatedQuizId = UUID.randomUUID();
        stubActiveQuiz(generatedQuizId, List.of(
                new QuizItem("Single?", CHOICES, 1, "Concept", "Because B")
        ));
        List<Integer> answers = Collections.singletonList(1);
        List<List<Integer>> multiAnswers = List.of(List.of(0), List.of(1));

        assertThatThrownBy(() -> quizShareLinkService.getSharedQuizResults(TEST_TOKEN, answers, multiAnswers))
                .isInstanceOf(InvalidSharedQuizAnswersException.class);
    }

    @Test
    void sharedQuizAcceptsSubmissionsFromThePreFixBundle() {
        // A recipient mid-quiz on the old bundle sends no multiAnswers at all. That must still grade and
        // return, not 500 -- they simply cannot be right about a question they could not fully answer.
        UUID generatedQuizId = UUID.randomUUID();
        stubActiveQuiz(generatedQuizId, List.of(
                new QuizItem("Single?", CHOICES, 1, "Concept", "Because B"),
                multiSelectQuestion()
        ));

        SharedQuizResultsResponse response = quizShareLinkService.getSharedQuizResults(
                TEST_TOKEN,
                List.of(1, 0),
                null
        );

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.score()).isEqualTo(1);
        assertThat(response.items().get(1).correct()).isFalse();
    }

    @Test
    void sharedQuizAnswersDeserializeWithNullEntriesForMultiSelectPositions() throws Exception {
        // The wire contract the frontend depends on: a MULTI_SELECT position has no single index, so it
        // sends null there and the selections in multiAnswers.
        SharedQuizResultsRequest request = new ObjectMapper().readValue(
                "{\"answers\":[0,null,2],\"multiAnswers\":[null,[0,2],null]}",
                SharedQuizResultsRequest.class
        );

        assertThat(request.answers()).containsExactly(0, null, 2);
        assertThat(request.multiAnswers()).containsExactly(null, List.of(0, 2), null);
    }

    @Test
    void getActivePublicQuizCarriesQuestionFormatSoTheRecipientCanAnswerMultiSelect() {
        stubActiveQuizWithNote(UUID.randomUUID(), "Cell Structure", List.of(multiSelectQuestion()));

        PublicSharedQuizResponse response = quizShareLinkService.getActivePublicQuiz(TEST_TOKEN);

        assertThat(response.questions().getFirst().questionFormat()).isEqualTo(MULTI_SELECT_FORMAT);
    }

    private SharedQuizResultsResponse gradeMultiSelectQuiz(List<Integer> selectedIndices) {
        UUID generatedQuizId = UUID.randomUUID();
        stubActiveQuiz(generatedQuizId, List.of(multiSelectQuestion()));
        return quizShareLinkService.getSharedQuizResults(
                TEST_TOKEN,
                Collections.singletonList(null),
                List.of(selectedIndices)
        );
    }

    private void stubActiveQuizWithNote(UUID noteId, String noteTitle, List<QuizItem> questions) {
        UUID generatedQuizId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        QuizShareLinkEntity link = buildLink(generatedQuizId, ownerUserId, TEST_TOKEN, true);
        GeneratedQuizEntity generatedQuiz = buildGeneratedQuiz(generatedQuizId, ownerUserId, noteId);
        generatedQuiz.setQuestions(questions);
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setTitle(noteTitle);
        when(quizShareLinkRepository.findByToken(TEST_TOKEN)).thenReturn(Optional.of(link));
        when(generatedQuizRepository.findById(generatedQuizId)).thenReturn(Optional.of(generatedQuiz));
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note));
    }

    private void stubActiveQuiz(UUID generatedQuizId, List<QuizItem> questions) {
        UUID ownerUserId = UUID.randomUUID();
        QuizShareLinkEntity link = buildLink(generatedQuizId, ownerUserId, TEST_TOKEN, true);
        GeneratedQuizEntity generatedQuiz = buildGeneratedQuiz(generatedQuizId, ownerUserId, UUID.randomUUID());
        generatedQuiz.setQuestions(questions);
        when(quizShareLinkRepository.findByToken(TEST_TOKEN)).thenReturn(Optional.of(link));
        when(generatedQuizRepository.findById(generatedQuizId)).thenReturn(Optional.of(generatedQuiz));
    }

    /** Correct answers are {@code [0, 2]}; {@code correctIndex()} therefore resolves to 0. */
    private QuizItem multiSelectQuestion() {
        return new QuizItem(
                "Which apply?",
                CHOICES,
                null,
                "Concept",
                "Because A and C",
                null,
                MULTI_SELECT_FORMAT,
                null,
                null,
                List.of(0, 2)
        );
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
