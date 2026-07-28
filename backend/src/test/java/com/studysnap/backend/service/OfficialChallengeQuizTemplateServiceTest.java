package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.TransactionException;

@ExtendWith(MockitoExtension.class)
class OfficialChallengeQuizTemplateServiceTest {
    @Mock
    private ChallengeQuizQuestionBankRepository questionBankRepository;
    @Mock
    private ChallengeQuizQuestionBankService questionBankService;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private QuizGenerationService quizGenerationService;
    @Mock
    private StudyPackGenerationContextResolver generationContextResolver;
    @Mock
    private TransactionOperations transactionOperations;
    @Mock
    private AsyncTaskExecutor llmParallelTaskExecutor;

    @Test
    void copyTemplateQuestions_usesOfficialTemplateAcrossLearnerLevelsAndTagsTheCallerRows() {
        UUID adopterId = UUID.randomUUID();
        UUID adopterStudyPackId = UUID.randomUUID();
        UUID adopterNoteId = UUID.randomUUID();
        UUID officialId = UUID.randomUUID();
        UUID officialStudyPackId = UUID.randomUUID();
        UUID officialNoteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        NoteEntity adoptedNote = note(adopterNoteId, adopterId, NoteVisibility.PRIVATE);
        adoptedNote.setCopiedFromNoteId(officialNoteId);
        NoteEntity officialNote = note(officialNoteId, officialId, NoteVisibility.PUBLIC);
        StudyPackEntity adopterStudyPack = studyPack(adopterStudyPackId, adopterNoteId, adopterId);
        StudyPackEntity officialStudyPack = studyPack(officialStudyPackId, officialNoteId, officialId);
        UserEntity officialAuthor = officialAuthor(officialId);
        ChallengeQuizQuestionBankEntity source = bankedQuestion("Official question");

        when(studyPackRepository.findById(adopterStudyPackId)).thenReturn(Optional.of(adopterStudyPack));
        when(noteRepository.findById(adopterNoteId)).thenReturn(Optional.of(adoptedNote));
        when(noteRepository.findById(officialNoteId)).thenReturn(Optional.of(officialNote));
        when(userRepository.findById(officialId)).thenReturn(Optional.of(officialAuthor));
        when(studyPackRepository.findByNoteId(officialNoteId)).thenReturn(Optional.of(officialStudyPack));
        when(questionBankRepository.findQuestionKeysByUserIdAndStudyPackId(adopterId, adopterStudyPackId))
                .thenReturn(List.of());
        when(questionBankRepository.findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(officialId, officialStudyPackId))
                .thenReturn(List.of(source));

        List<QuizItem> copied = service().copyTemplateQuestions(
                adopterId,
                adopterStudyPackId,
                LearnerLevel.COLLEGE,
                sessionId,
                Set.of(),
                5
        );

        assertThat(copied).containsExactly(source.getQuestion());
        ArgumentCaptor<List<ChallengeQuizQuestionBankEntity>> saved = ArgumentCaptor.forClass(List.class);
        verify(questionBankRepository).saveAll(saved.capture());
        ChallengeQuizQuestionBankEntity copiedRow = saved.getValue().getFirst();
        assertThat(copiedRow.getUserId()).isEqualTo(adopterId);
        assertThat(copiedRow.getStudyPackId()).isEqualTo(adopterStudyPackId);
        assertThat(copiedRow.getLearnerLevel()).isEqualTo(LearnerLevel.COLLEGE.name());
        assertThat(copiedRow.getQuestion()).isEqualTo(source.getQuestion());
        assertThat(copiedRow.getClaimedSessionId()).isEqualTo(sessionId);
        assertThat(copiedRow.getLastKnownOutcome()).isEqualTo("UNANSWERED");
        verify(quizGenerationService, org.mockito.Mockito.never())
                .generateChallengeQuiz(any(), any(), any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void copyTemplateQuestions_doesNotUseAnUnpublishedImmediateSource() {
        UUID adopterStudyPackId = UUID.randomUUID();
        UUID adopterNoteId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        StudyPackEntity adopterStudyPack = studyPack(adopterStudyPackId, adopterNoteId, UUID.randomUUID());
        NoteEntity adoptedNote = note(adopterNoteId, adopterStudyPack.getOwnerUserId(), NoteVisibility.PRIVATE);
        adoptedNote.setCopiedFromNoteId(sourceNoteId);
        NoteEntity unpublishedSource = note(sourceNoteId, UUID.randomUUID(), NoteVisibility.PRIVATE);

        when(studyPackRepository.findById(adopterStudyPackId)).thenReturn(Optional.of(adopterStudyPack));
        when(noteRepository.findById(adopterNoteId)).thenReturn(Optional.of(adoptedNote));
        when(noteRepository.findById(sourceNoteId)).thenReturn(Optional.of(unpublishedSource));

        List<QuizItem> copied = service().copyTemplateQuestions(
                adopterStudyPack.getOwnerUserId(),
                adopterStudyPackId,
                LearnerLevel.COLLEGE,
                UUID.randomUUID(),
                Set.of(),
                5
        );

        assertThat(copied).isEmpty();
        verifyNoInteractions(questionBankRepository, quizGenerationService);
    }

    @Test
    void queueSeedIfEligible_generatesOneOfficialTemplateAfterTheWritePath() {
        UUID officialId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        NoteEntity officialNote = note(noteId, officialId, NoteVisibility.PUBLIC);
        StudyPackEntity officialStudyPack = studyPack(studyPackId, noteId, officialId);
        officialStudyPack.setTitle("Official pack");
        officialStudyPack.setSummary("Summary");
        officialStudyPack.setKeyConcepts(List.of("Concept"));
        UserEntity officialAuthor = officialAuthor(officialId);
        com.studysnap.backend.service.model.StudyPackGenerationContext context =
                new com.studysnap.backend.service.model.StudyPackGenerationContext(
                        LearnerLevel.BOARD_EXAM_REVIEW, "Nursing", "Nursing", List.of()
                );

        when(userRepository.findById(officialId)).thenReturn(Optional.of(officialAuthor));
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(officialNote));
        when(studyPackRepository.findById(studyPackId)).thenReturn(Optional.of(officialStudyPack));
        when(generationContextResolver.resolveForStudyPack(officialId, officialStudyPack)).thenReturn(context);
        when(quizGenerationService.generateChallengeQuiz(
                eq("Official pack"), eq("Summary"), eq(List.of("Concept")), eq(List.of()), eq(20), eq("medium"), eq(context)
        )).thenReturn(GeneratedChallengeQuizContent.withoutUsage(List.of(
                new QuizItem("Official question", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")
        )));

        AsyncTaskExecutor directExecutor = Runnable::run;
        service(immediateTransactions(), directExecutor)
                .queueSeedIfEligible(officialNote, officialStudyPack);

        verify(questionBankService).persistGeneratedQuestions(
                officialId, studyPackId, null, LearnerLevel.BOARD_EXAM_REVIEW,
                List.of(new QuizItem("Official question", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"))
        );
    }

    @Test
    void queueBackfill_isIdempotentForAnAlreadySeededOfficialStudyPack() {
        UUID officialId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity officialNote = note(noteId, officialId, NoteVisibility.PUBLIC);
        StudyPackEntity officialStudyPack = studyPack(UUID.randomUUID(), noteId, officialId);
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)).thenReturn(List.of(officialNote));
        when(studyPackRepository.findByNoteIdIn(List.of(noteId))).thenReturn(List.of(officialStudyPack));
        when(userRepository.findById(officialId)).thenReturn(Optional.of(officialAuthor(officialId)));
        when(questionBankRepository.existsByUserIdAndStudyPackId(officialId, officialStudyPack.getId())).thenReturn(true);

        var first = service().queueBackfill();
        var second = service().queueBackfill();

        assertThat(first.queued()).isZero();
        assertThat(first.skipped()).isEqualTo(1);
        assertThat(first.rejected()).isZero();
        assertThat(second).isEqualTo(first);
        verifyNoInteractions(llmParallelTaskExecutor);
    }

    @Test
    void queueBackfill_reportsRejectedInsteadOfFailingWhenTheSeedExecutorQueueIsFull() {
        UUID officialId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity officialNote = note(noteId, officialId, NoteVisibility.PUBLIC);
        StudyPackEntity officialStudyPack = studyPack(UUID.randomUUID(), noteId, officialId);
        when(noteRepository.findByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC)).thenReturn(List.of(officialNote));
        when(studyPackRepository.findByNoteIdIn(List.of(noteId))).thenReturn(List.of(officialStudyPack));
        when(userRepository.findById(officialId)).thenReturn(Optional.of(officialAuthor(officialId)));
        when(questionBankRepository.existsByUserIdAndStudyPackId(officialId, officialStudyPack.getId())).thenReturn(false);
        doThrow(new RejectedExecutionException("queue full")).when(llmParallelTaskExecutor).execute(any());

        var response = service().queueBackfill();

        assertThat(response.queued()).isZero();
        assertThat(response.skipped()).isZero();
        assertThat(response.rejected()).isEqualTo(1);
        verifyNoInteractions(quizGenerationService);
    }

    @Test
    void queueSeedIfEligible_doesNotFailTheWritePathWhenTheSeedExecutorQueueIsFull() {
        UUID officialId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        NoteEntity officialNote = note(noteId, officialId, NoteVisibility.PUBLIC);
        StudyPackEntity officialStudyPack = studyPack(studyPackId, noteId, officialId);
        when(userRepository.findById(officialId)).thenReturn(Optional.of(officialAuthor(officialId)));
        when(questionBankRepository.existsByUserIdAndStudyPackId(officialId, studyPackId)).thenReturn(false);
        doThrow(new RejectedExecutionException("queue full")).when(llmParallelTaskExecutor).execute(any());

        OfficialChallengeQuizTemplateService service = service();

        assertThatCode(() -> service.queueSeedIfEligible(officialNote, officialStudyPack)).doesNotThrowAnyException();
        verifyNoInteractions(quizGenerationService);
    }

    private OfficialChallengeQuizTemplateService service() {
        return service(transactionOperations, llmParallelTaskExecutor);
    }

    private OfficialChallengeQuizTemplateService service(
            TransactionOperations transactionOperations,
            AsyncTaskExecutor taskExecutor
    ) {
        return new OfficialChallengeQuizTemplateService(
                questionBankRepository,
                questionBankService,
                noteRepository,
                studyPackRepository,
                userRepository,
                quizGenerationService,
                generationContextResolver,
                transactionOperations,
                taskExecutor
        );
    }

    private TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
    }

    private NoteEntity note(UUID id, UUID ownerId, NoteVisibility visibility) {
        NoteEntity note = new NoteEntity();
        note.setId(id);
        note.setOwnerUserId(ownerId);
        note.setVisibility(visibility);
        return note;
    }

    private StudyPackEntity studyPack(UUID id, UUID noteId, UUID ownerId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(id);
        studyPack.setNoteId(noteId);
        studyPack.setOwnerUserId(ownerId);
        return studyPack;
    }

    private UserEntity officialAuthor(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private ChallengeQuizQuestionBankEntity bankedQuestion(String text) {
        ChallengeQuizQuestionBankEntity entry = new ChallengeQuizQuestionBankEntity();
        entry.setQuestionKey(text.toLowerCase());
        entry.setQuestion(new QuizItem(text, List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"));
        return entry;
    }
}
