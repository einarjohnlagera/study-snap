package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.studysnap.backend.repository.ChallengeQuizQuestionBankOwnerProjection;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.repository.NoteOwnerVisibilityProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackOwnerProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import java.util.ArrayList;
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
    void copyTemplateQuestions_usesOfficialTemplateAcrossLearnerLevelsAndTagsTheAdopterNoteLevel() {
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

        com.studysnap.backend.service.model.StudyPackGenerationContext adopterContext =
                new com.studysnap.backend.service.model.StudyPackGenerationContext(
                        LearnerLevel.COLLEGE, null, null, List.of(), null, LearnerLevel.SENIOR_HIGH
                );
        List<QuizItem> copied = service().copyTemplateQuestions(
                adopterId,
                adopterStudyPackId,
                StudyPackGenerationContextResolver.effectiveCurriculumLevel(adopterContext),
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
        assertThat(copiedRow.getLearnerLevel()).isEqualTo(LearnerLevel.SENIOR_HIGH.name());
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
                        LearnerLevel.COLLEGE,
                        "Nursing",
                        "Nursing",
                        List.of(),
                        null,
                        LearnerLevel.BOARD_EXAM_REVIEW
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
    void queueSeedIfEligible_treatsTheOfficialEmailAccountAsOfficialEvenWithoutAdminRole() {
        UUID officialId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        NoteEntity officialNote = note(noteId, officialId, NoteVisibility.PUBLIC);
        StudyPackEntity officialStudyPack = studyPack(studyPackId, noteId, officialId);
        officialStudyPack.setTitle("Official pack");
        officialStudyPack.setSummary("Summary");
        officialStudyPack.setKeyConcepts(List.of("Concept"));
        UserEntity officialAuthorByEmail = new UserEntity();
        officialAuthorByEmail.setId(officialId);
        officialAuthorByEmail.setRole(UserRole.USER);
        officialAuthorByEmail.setEmail("einar.lagera@gmail.com");
        com.studysnap.backend.service.model.StudyPackGenerationContext context =
                new com.studysnap.backend.service.model.StudyPackGenerationContext(
                        LearnerLevel.BOARD_EXAM_REVIEW, "Nursing", "Nursing", List.of()
                );

        when(userRepository.findById(officialId)).thenReturn(Optional.of(officialAuthorByEmail));
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
        UUID studyPackId = UUID.randomUUID();
        stubBackfillCatalog(
                List.of(new NoteOwnerVisibilityProjection(noteId, officialId, NoteVisibility.PUBLIC)),
                List.of(new StudyPackOwnerProjection(studyPackId, noteId, officialId)),
                List.of(officialAuthor(officialId)),
                List.of(new ChallengeQuizQuestionBankOwnerProjection(officialId, studyPackId))
        );

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
        UUID studyPackId = UUID.randomUUID();
        stubBackfillCatalog(
                List.of(new NoteOwnerVisibilityProjection(noteId, officialId, NoteVisibility.PUBLIC)),
                List.of(new StudyPackOwnerProjection(studyPackId, noteId, officialId)),
                List.of(officialAuthor(officialId)),
                List.of()
        );
        doThrow(new RejectedExecutionException("queue full")).when(llmParallelTaskExecutor).execute(any());

        var response = service().queueBackfill();

        assertThat(response.queued()).isZero();
        assertThat(response.skipped()).isZero();
        assertThat(response.rejected()).isEqualTo(1);
        verifyNoInteractions(quizGenerationService);
    }

    /**
     * ⚠️ THE QUERY-COUNT GUARD, AND IT NEEDS {@code never()} RATHER THAN {@code times(1)}. Asserting
     * only that the batch lookups fired once passes under a "both run" implementation — the shape
     * this repo has already paid for twice — and asserting only that the counts come out right
     * passes under the N+1 by construction, because the N+1 returns the SAME counts.
     *
     * <p>⚠️ AND N MUST BE > 1. With a single note, one-per-note and one-for-all are the same number.
     */
    @Test
    void queueBackfill_resolvesAuthorsAndSeededPacksInBatchesRatherThanOncePerNote() {
        UUID officialId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        UUID firstStudyPackId = UUID.randomUUID();
        UUID secondStudyPackId = UUID.randomUUID();
        stubBackfillCatalog(
                List.of(
                        new NoteOwnerVisibilityProjection(firstNoteId, officialId, NoteVisibility.PUBLIC),
                        new NoteOwnerVisibilityProjection(secondNoteId, officialId, NoteVisibility.PUBLIC)
                ),
                List.of(
                        new StudyPackOwnerProjection(firstStudyPackId, firstNoteId, officialId),
                        new StudyPackOwnerProjection(secondStudyPackId, secondNoteId, officialId)
                ),
                List.of(officialAuthor(officialId)),
                List.of()
        );

        var response = service().queueBackfill();

        assertThat(response.queued()).isEqualTo(2);
        verify(userRepository, never()).findById(any());
        verify(questionBankRepository, never()).existsByUserIdAndStudyPackId(any(), any());
        verify(userRepository, times(1)).findAllById(any());
        verify(questionBankRepository, times(1)).findOwnerStudyPackPairsByStudyPackIdIn(any());
        verify(noteRepository, times(1))
                .findOwnerVisibilityProjectionsByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC);
        verify(studyPackRepository, times(1)).findOwnerProjectionsByNoteIdIn(any());
        verify(noteRepository, never()).findByVisibilityOrderByUpdatedAtDesc(any());
        verify(studyPackRepository, never()).findByNoteIdIn(any());
    }

    /**
     * ⚠️ THE COUNTS MUST MEAN WHAT THEY MEANT BEFORE. The catalog holds one of each shape the old
     * loop distinguished — eligible, already-seeded, a non-Official author, and a note with no pack
     * — so a batch implementation that quietly drops a category (an inner join instead of a lookup,
     * say) changes {@code skipped} and this fails.
     */
    @Test
    void queueBackfill_countsEveryIneligibleShapeAsSkippedExactlyAsTheOldLoopDid() {
        UUID officialId = UUID.randomUUID();
        UUID learnerId = UUID.randomUUID();
        UUID eligibleNoteId = UUID.randomUUID();
        UUID seededNoteId = UUID.randomUUID();
        UUID learnerNoteId = UUID.randomUUID();
        UUID packlessNoteId = UUID.randomUUID();
        UUID eligibleStudyPackId = UUID.randomUUID();
        UUID seededStudyPackId = UUID.randomUUID();
        UUID learnerStudyPackId = UUID.randomUUID();
        stubBackfillCatalog(
                List.of(
                        new NoteOwnerVisibilityProjection(eligibleNoteId, officialId, NoteVisibility.PUBLIC),
                        new NoteOwnerVisibilityProjection(seededNoteId, officialId, NoteVisibility.PUBLIC),
                        new NoteOwnerVisibilityProjection(learnerNoteId, learnerId, NoteVisibility.PUBLIC),
                        new NoteOwnerVisibilityProjection(packlessNoteId, officialId, NoteVisibility.PUBLIC)
                ),
                List.of(
                        new StudyPackOwnerProjection(eligibleStudyPackId, eligibleNoteId, officialId),
                        new StudyPackOwnerProjection(seededStudyPackId, seededNoteId, officialId),
                        new StudyPackOwnerProjection(learnerStudyPackId, learnerNoteId, learnerId)
                ),
                List.of(officialAuthor(officialId), learnerAuthor(learnerId)),
                List.of(new ChallengeQuizQuestionBankOwnerProjection(officialId, seededStudyPackId))
        );

        var response = service().queueBackfill();

        assertThat(response.queued()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(3);
        assertThat(response.rejected()).isZero();
        verify(llmParallelTaskExecutor, times(1)).execute(any());
    }

    /**
     * ⚠️ THE QUEUE ORDER IS PART OF THE CONTRACT. The backfill dispatches in {@code updated_at desc}
     * order, so it decides which packs seed first when the executor queue fills — and a batched
     * rewrite that grouped by owner or by pack id would reorder it invisibly. The counts would be
     * identical, which is why this is a separate assertion rather than a stronger one above.
     */
    @Test
    void queueBackfill_dispatchesInTheOrderTheCatalogQueryReturned() {
        // ⚠️ DETERMINISTIC, DESCENDING IDS AND N=3 — NOT DECORATION. With N=2 and random UUIDs this
        // fixture survived the very reorder its name forbids: a sort by pack id preserved the fixture
        // order roughly half the time, so the guard was sound in principle and a coin flip in practice.
        // The catalog is returned in DESCENDING id order (the query's own `updated_at desc`), so any
        // ascending sort — by note id or pack id — produces a different order every run.
        UUID officialId = UUID.randomUUID();
        UUID newestNoteId = UUID.fromString("cccccccc-0000-4000-8000-000000000003");
        UUID middleNoteId = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
        UUID oldestNoteId = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
        stubBackfillCatalog(
                List.of(
                        new NoteOwnerVisibilityProjection(newestNoteId, officialId, NoteVisibility.PUBLIC),
                        new NoteOwnerVisibilityProjection(middleNoteId, officialId, NoteVisibility.PUBLIC),
                        new NoteOwnerVisibilityProjection(oldestNoteId, officialId, NoteVisibility.PUBLIC)
                ),
                List.of(
                        new StudyPackOwnerProjection(
                                UUID.fromString("cccccccc-1111-4000-8000-000000000003"), newestNoteId, officialId),
                        new StudyPackOwnerProjection(
                                UUID.fromString("bbbbbbbb-1111-4000-8000-000000000002"), middleNoteId, officialId),
                        new StudyPackOwnerProjection(
                                UUID.fromString("aaaaaaaa-1111-4000-8000-000000000001"), oldestNoteId, officialId)
                ),
                List.of(officialAuthor(officialId)),
                List.of()
        );
        List<Runnable> dispatched = new ArrayList<>();
        doAnswer(invocation -> {
            dispatched.add(invocation.getArgument(0));
            return null;
        }).when(llmParallelTaskExecutor).execute(any());

        service(immediateTransactions(), llmParallelTaskExecutor).queueBackfill();
        dispatched.forEach(Runnable::run);

        // Each seed task re-reads its own note; the unstubbed lookup returns empty and the task bails,
        // which is all this needs -- the ORDER of those reads is the queue order.
        ArgumentCaptor<UUID> seededNoteIds = ArgumentCaptor.forClass(UUID.class);
        verify(noteRepository, times(3)).findById(seededNoteIds.capture());
        assertThat(seededNoteIds.getAllValues()).containsExactly(newestNoteId, middleNoteId, oldestNoteId);
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

    private void stubBackfillCatalog(
            List<NoteOwnerVisibilityProjection> publicNotes,
            List<StudyPackOwnerProjection> studyPacks,
            List<UserEntity> owners,
            List<ChallengeQuizQuestionBankOwnerProjection> alreadySeeded
    ) {
        when(noteRepository.findOwnerVisibilityProjectionsByVisibilityOrderByUpdatedAtDesc(NoteVisibility.PUBLIC))
                .thenReturn(publicNotes);
        when(studyPackRepository.findOwnerProjectionsByNoteIdIn(any())).thenReturn(studyPacks);
        when(userRepository.findAllById(any())).thenReturn(owners);
        when(questionBankRepository.findOwnerStudyPackPairsByStudyPackIdIn(any())).thenReturn(alreadySeeded);
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

    private UserEntity learnerAuthor(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setRole(UserRole.USER);
        user.setEmail("learner-" + id + "@example.test");
        return user;
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
