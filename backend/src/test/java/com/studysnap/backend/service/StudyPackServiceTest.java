package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackDraftRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.OcrRateLimitService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyPackServiceTest {

    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private StudyPackDraftRepository studyPackDraftRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private OcrService ocrService;
    @Mock
    private LlmStudyPackService llmStudyPackService;
    @Mock
    private ActivityTrackingService activityTrackingService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private BillingUsagePeriodService billingUsagePeriodService;
    @Mock
    private OcrRateLimitService ocrRateLimitService;

    private StudyPackService studyPackService;

    @BeforeEach
    void setUp() {
        studyPackService = new StudyPackService(
                studyPackRepository,
                studyPackDraftRepository,
                noteRepository,
                ocrService,
                llmStudyPackService,
                new StudySnapProperties(),
                activityTrackingService,
                analyticsService,
                subscriptionService,
                userUsageService,
                billingUsagePeriodService,
                ocrRateLimitService
        );
        lenient().when(studyPackRepository.save(any(StudyPackEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createFromText_withDraftNote_marksNoteGenerated_andConsumesGenerationCredit() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity draftNote = buildDraftNote(noteId, userId, "draft note content");
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "Generated title",
                "Generated summary",
                "Biology",
                List.of("cells", "review"),
                List.of("Cell membrane", "Mitochondria"),
                List.of(new QuizItem(
                        "What powers the cell?",
                        List.of("Nucleus", "Mitochondria", "Ribosome", "Golgi body"),
                        "Mitochondria",
                        "Cell biology",
                        "Mitochondria generate ATP."
                )),
                "gpt-4.1-mini",
                100,
                220,
                0,
                new BigDecimal("0.0100")
        );

        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(draftNote));
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(llmStudyPackService.generateStudyPack("draft note content")).thenReturn(generated);

        StudyPackResponse response = studyPackService.createFromText(
                new CreateStudyPackRequest(null, noteId.toString()),
                userId
        );

        ArgumentCaptor<StudyPackEntity> studyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(studyPackCaptor.capture());
        StudyPackEntity savedStudyPack = studyPackCaptor.getValue();
        assertThat(savedStudyPack.getNoteId()).isEqualTo(noteId);
        assertThat(savedStudyPack.getSummary()).isEqualTo("Generated summary");
        assertThat(savedStudyPack.getKeyConcepts()).containsExactly("Cell membrane", "Mitochondria");
        assertThat(savedStudyPack.getQuiz()).hasSize(1);

        verify(noteRepository).save(draftNote);
        assertThat(draftNote.getStatus()).isEqualTo(NoteStatus.GENERATED);

        verify(userUsageService).incrementStudyPackGeneration(eq(userId), any(OffsetDateTime.class));
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.STUDY_PACK_GENERATED), eq(savedStudyPack.getId()), any());
        assertThat(response.noteId()).isEqualTo(noteId.toString());
        assertThat(response.summary()).isEqualTo("Generated summary");
        assertThat(response.keyConcepts()).containsExactly("Cell membrane", "Mitochondria");
        assertThat(response.quiz()).hasSize(1);
    }

    @Test
    void createFromText_rejectsGenerationWhenNoteAlreadyGenerated() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        NoteEntity generatedNote = buildDraftNote(noteId, userId, "already generated note");
        generatedNote.setStatus(NoteStatus.GENERATED);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(generatedNote));

        assertThatThrownBy(() -> studyPackService.createFromText(
                new CreateStudyPackRequest(null, noteId.toString()),
                userId
        ))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("NOTE_ALREADY_GENERATED");

        verify(studyPackRepository, never()).save(any(StudyPackEntity.class));
        verify(userUsageService, never()).incrementStudyPackGeneration(any(UUID.class), any(OffsetDateTime.class));
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    private NoteEntity buildDraftNote(UUID noteId, UUID ownerUserId, String content) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerUserId);
        note.setTitle("Draft title");
        note.setSubject("Subject");
        note.setTags(new String[]{"draft"});
        note.setContent(content);
        note.setStatus(NoteStatus.DRAFT);
        note.setVisibility(NoteVisibility.PRIVATE);
        note.setCreatedAt(OffsetDateTime.now().minusHours(2));
        note.setUpdatedAt(OffsetDateTime.now().minusHours(1));
        note.setCopiedFromPublic(Boolean.FALSE);
        return note;
    }
}
