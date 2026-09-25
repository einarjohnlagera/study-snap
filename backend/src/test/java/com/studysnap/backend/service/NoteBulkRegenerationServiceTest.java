package com.studysnap.backend.service;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteRegenerationScope;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.repository.NoteBulkRegenerationItemRepository;
import com.studysnap.backend.repository.NoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteBulkRegenerationServiceTest {
    @Mock private NoteRepository noteRepository;
    @Mock private NoteBulkRegenerationItemRepository itemRepository;
    @Mock private NoteRegenerationReadinessService readinessService;
    @Mock private NoteRegenerationConsequenceService consequenceService;
    @Mock private StudyPackService studyPackService;
    @Mock private MePlanService mePlanService;
    @Mock private OnboardingGuardService onboardingGuardService;
    @Mock private BulkGenerationFailureReasonNormalizer failureReasonNormalizer;
    @Mock private NoteBulkRegenerationTaskDispatcher taskDispatcher;
    @Mock private BulkRegenerationAccessGuard accessGuard;
    @Mock private AnalyticsService analyticsService;
    @Mock private BulkOperationNotificationService notificationService;

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void processBatch_notifiesOnceWithRequestedAndRegeneratedCountsAfterNormalCompletion() {
        UUID batchId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity generated = new NoteEntity();
        generated.setStatus(NoteStatus.GENERATED);
        when(itemRepository.findByBatchIdAndNoteId(batchId, noteId)).thenReturn(Optional.empty());
        when(readinessService.evaluate(noteId, ownerUserId, NoteRegenerationScope.STUDY_PACK))
                .thenReturn(new NoteRegenerationReadinessService.Verdict(
                        NoteRegenerationReadinessService.NoteRegenerationReadiness.READY, null, null));
        when(consequenceService.hasLiveShareLink(
                ownerUserId, noteId, NoteRegenerationScope.STUDY_PACK)).thenReturn(false);
        when(noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)).thenReturn(Optional.of(generated));
        NoteBulkRegenerationService service = service(0);

        service.processBatch(
                batchId,
                List.of(noteId),
                ownerUserId,
                NoteRegenerationScope.STUDY_PACK,
                false,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        verify(notificationService).bulkRegenerationComplete(ownerUserId, batchId, 1, 1);
    }

    @Test
    void processBatch_reportsTheRegeneratedCountAndNotTheRequestedOneWhenOnlySomeNotesWereUpdated() {
        UUID batchId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID updatedNoteId = UUID.randomUUID();
        UUID skippedNoteId = UUID.randomUUID();
        NoteEntity generated = new NoteEntity();
        generated.setStatus(NoteStatus.GENERATED);
        when(itemRepository.findByBatchIdAndNoteId(batchId, updatedNoteId)).thenReturn(Optional.empty());
        when(itemRepository.findByBatchIdAndNoteId(batchId, skippedNoteId)).thenReturn(Optional.empty());
        when(readinessService.evaluate(updatedNoteId, ownerUserId, NoteRegenerationScope.STUDY_PACK))
                .thenReturn(new NoteRegenerationReadinessService.Verdict(
                        NoteRegenerationReadinessService.NoteRegenerationReadiness.READY, null, null));
        when(readinessService.evaluate(skippedNoteId, ownerUserId, NoteRegenerationScope.STUDY_PACK))
                .thenReturn(new NoteRegenerationReadinessService.Verdict(
                        NoteRegenerationReadinessService.NoteRegenerationReadiness.NOT_ELIGIBLE,
                        "NOTE_NOT_FOUND",
                        "Missing"));
        when(consequenceService.hasLiveShareLink(
                ownerUserId, updatedNoteId, NoteRegenerationScope.STUDY_PACK)).thenReturn(false);
        when(noteRepository.findByIdAndOwnerUserId(updatedNoteId, ownerUserId)).thenReturn(Optional.of(generated));
        NoteBulkRegenerationService service = service(0);

        service.processBatch(
                batchId,
                List.of(updatedNoteId, skippedNoteId),
                ownerUserId,
                NoteRegenerationScope.STUDY_PACK,
                false,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        verify(notificationService).bulkRegenerationComplete(ownerUserId, batchId, 2, 1);
    }

    @Test
    void processBatch_interruptionSendsNoNotification() {
        UUID batchId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        when(itemRepository.findByBatchIdAndNoteId(batchId, firstNoteId)).thenReturn(Optional.empty());
        when(readinessService.evaluate(firstNoteId, ownerUserId, NoteRegenerationScope.STUDY_PACK))
                .thenReturn(new NoteRegenerationReadinessService.Verdict(
                        NoteRegenerationReadinessService.NoteRegenerationReadiness.NOT_ELIGIBLE,
                        "NOTE_NOT_FOUND",
                        "Missing"));
        Thread.currentThread().interrupt();
        NoteBulkRegenerationService service = service(10);

        service.processBatch(
                batchId,
                List.of(firstNoteId, secondNoteId),
                ownerUserId,
                NoteRegenerationScope.STUDY_PACK,
                false,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        verify(notificationService, never()).bulkRegenerationComplete(any(), any(), anyInt(), anyInt());
    }

    private NoteBulkRegenerationService service(int throttleDelayMs) {
        return new NoteBulkRegenerationService(
                noteRepository,
                itemRepository,
                readinessService,
                consequenceService,
                studyPackService,
                mePlanService,
                onboardingGuardService,
                failureReasonNormalizer,
                taskDispatcher,
                accessGuard,
                analyticsService,
                notificationService,
                50,
                throttleDelayMs,
                10,
                1_000
        );
    }
}
