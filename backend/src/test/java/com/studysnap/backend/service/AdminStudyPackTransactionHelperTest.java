package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStudyPackTransactionHelperTest {
    private static final String ORIGINAL_SUMMARY = "Original summary";
    private static final String ORIGINAL_CONCEPT = "Original concept";

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LlmStudyPackService llmStudyPackService;
    @Mock
    private RegenerationProgressTracker progressTracker;

    private AdminStudyPackTransactionHelper transactionHelper;

    @BeforeEach
    void setUp() {
        transactionHelper = new AdminStudyPackTransactionHelper(
                noteRepository,
                studyPackRepository,
                quickReviewSessionRepository,
                userRepository,
                llmStudyPackService,
                progressTracker
        );
    }

    @Test
    void regenerateOnePack_handlesNoteNotFound() {
        StudyPackEntity pack = new StudyPackEntity();
        pack.setId(UUID.randomUUID());
        pack.setNoteId(UUID.randomUUID());
        when(studyPackRepository.findById(pack.getId())).thenReturn(Optional.of(pack));
        when(noteRepository.findById(pack.getNoteId())).thenReturn(Optional.empty());

        assertThatCode(() -> transactionHelper.regenerateOnePack(pack))
                .doesNotThrowAnyException();

        verify(llmStudyPackService, never()).regenerateSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(studyPackRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repairMalformedQuiz_regeneratesQuizOnly() {
        UUID noteId = UUID.randomUUID();
        StudyPackEntity pack = buildPack(noteId, malformedQuiz());
        pack.setSummary(ORIGINAL_SUMMARY);
        pack.setKeyConcepts(List.of(ORIGINAL_CONCEPT));
        NoteEntity note = buildNote(noteId, pack.getOwnerUserId());
        List<QuizItem> regeneratedQuiz = List.of(mcqQuizItem("Which mechanism best explains the effect?"));
        GeneratedStudyPackContent generated = new GeneratedStudyPackContent(
                "Generated title",
                "Generated summary",
                "Generated subject",
                List.of("generated"),
                List.of("Generated concept"),
                regeneratedQuiz,
                "model",
                10,
                20,
                0,
                BigDecimal.ONE
        );
        when(studyPackRepository.findById(pack.getId())).thenReturn(Optional.of(pack));
        when(quickReviewSessionRepository.existsByStudyPackIdAndSessionModeAndStatus(
                pack.getId(),
                QuickReviewSessionMode.QUICK_REVIEW,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(false);
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note));
        when(userRepository.findById(pack.getOwnerUserId())).thenReturn(Optional.empty());
        when(llmStudyPackService.generateStudyPack(eq(note.getContent()), any())).thenReturn(generated);
        when(studyPackRepository.save(same(pack))).thenReturn(pack);

        transactionHelper.repairMalformedQuiz(pack);

        assertThat(pack.getQuiz()).isEqualTo(regeneratedQuiz);
        assertThat(pack.getSummary()).isEqualTo(ORIGINAL_SUMMARY);
        assertThat(pack.getKeyConcepts()).containsExactly(ORIGINAL_CONCEPT);
        verify(studyPackRepository).save(pack);
        verify(progressTracker).recordSuccess();
    }

    @Test
    void repairMalformedQuiz_skipsCleanPackIdempotently() {
        StudyPackEntity pack = buildPack(UUID.randomUUID(), List.of(mcqQuizItem("What is the best answer?")));
        when(studyPackRepository.findById(pack.getId())).thenReturn(Optional.of(pack));

        transactionHelper.repairMalformedQuiz(pack);

        verify(llmStudyPackService, never()).generateStudyPack(any(), any());
        verify(studyPackRepository, never()).save(any());
        verify(progressTracker).recordSuccess();
    }

    @Test
    void repairMalformedQuiz_skipsPackWithActiveQuickReviewSession() {
        StudyPackEntity pack = buildPack(UUID.randomUUID(), malformedQuiz());
        when(studyPackRepository.findById(pack.getId())).thenReturn(Optional.of(pack));
        when(quickReviewSessionRepository.existsByStudyPackIdAndSessionModeAndStatus(
                pack.getId(),
                QuickReviewSessionMode.QUICK_REVIEW,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(true);

        transactionHelper.repairMalformedQuiz(pack);

        verify(llmStudyPackService, never()).generateStudyPack(any(), any());
        verify(studyPackRepository, never()).save(any());
        verify(progressTracker).recordSuccess();
    }

    @Test
    void repairMalformedQuiz_recordsFailureWhenRegenerationFails() {
        UUID noteId = UUID.randomUUID();
        StudyPackEntity pack = buildPack(noteId, malformedQuiz());
        NoteEntity note = buildNote(noteId, pack.getOwnerUserId());
        when(studyPackRepository.findById(pack.getId())).thenReturn(Optional.of(pack));
        when(quickReviewSessionRepository.existsByStudyPackIdAndSessionModeAndStatus(
                pack.getId(),
                QuickReviewSessionMode.QUICK_REVIEW,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(false);
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note));
        when(userRepository.findById(pack.getOwnerUserId())).thenReturn(Optional.empty());
        when(llmStudyPackService.generateStudyPack(eq(note.getContent()), any()))
                .thenThrow(new IllegalStateException("LLM failed"));

        assertThatCode(() -> transactionHelper.repairMalformedQuiz(pack))
                .doesNotThrowAnyException();

        verify(studyPackRepository, never()).save(any());
        verify(progressTracker).recordFailure();
    }

    private StudyPackEntity buildPack(UUID noteId, List<QuizItem> quiz) {
        StudyPackEntity pack = new StudyPackEntity();
        pack.setId(UUID.randomUUID());
        pack.setOwnerUserId(UUID.randomUUID());
        pack.setNoteId(noteId);
        pack.setSummary("Summary");
        pack.setKeyConcepts(List.of("Concept"));
        pack.setQuiz(quiz);
        return pack;
    }

    private NoteEntity buildNote(UUID noteId, UUID ownerUserId) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerUserId);
        note.setContent("Source note content");
        note.setCourseProgram("BS Biology");
        note.setSubject("Biology");
        note.setTags(new String[]{"cell"});
        return note;
    }

    private List<QuizItem> malformedQuiz() {
        return List.of(new QuizItem(
                "Statement 1: Cells use ATP. Statement 2: ATP stores energy. Which is correct?",
                List.of("True", "False"),
                0,
                "ATP",
                "Both statements are correct.",
                null,
                "TRUE_FALSE",
                null,
                null
        ));
    }

    private QuizItem mcqQuizItem(String question) {
        return new QuizItem(
                question,
                List.of("A", "B", "C", "D"),
                0,
                "Concept",
                "Explanation",
                null,
                "MCQ",
                null,
                null
        );
    }
}
