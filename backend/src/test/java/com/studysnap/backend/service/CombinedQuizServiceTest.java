package com.studysnap.backend.service;

import com.studysnap.backend.dto.CombinedQuizResponse;
import com.studysnap.backend.dto.CreateCombinedQuizRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.CombinedQuizValidationException;
import com.studysnap.backend.exception.GeneratedQuizBatchExportValidationException;
import com.studysnap.backend.repository.CombinedQuizRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CombinedQuizServiceTest {
    @Mock private CombinedQuizRepository combinedQuizRepository;
    @Mock private NoteRepository noteRepository;
    @Mock private GeneratedQuizRepository generatedQuizRepository;
    @Mock private StudyPackRepository studyPackRepository;
    @Mock private AuthService authService;
    @Mock private OnboardingGuardService onboardingGuardService;

    private CombinedQuizService service;

    @BeforeEach
    void setUp() {
        service = new CombinedQuizService(combinedQuizRepository, noteRepository, generatedQuizRepository,
                studyPackRepository, authService, onboardingGuardService);
    }

    @Test
    void assemble_snapshotsOrderedSectionsCopiedTitlesAndTrustedProvenanceCopies() {
        UUID owner = UUID.randomUUID();
        UUID firstNote = UUID.randomUUID();
        UUID secondNote = UUID.randomUUID();
        UUID firstPack = UUID.randomUUID();
        UUID secondPack = UUID.randomUUID();
        NoteEntity first = note(firstNote, owner, "Cell structure");
        NoteEntity second = note(secondNote, owner, "Cell division");
        GeneratedQuizEntity firstQuiz = quiz(firstNote, item("First", "A. B. Smith"), item("Skipped", "B"));
        GeneratedQuizEntity secondQuiz = quiz(secondNote, item("Second", "C"));
        when(noteRepository.findByOwnerUserIdAndIdIn(owner, List.of(firstNote, secondNote))).thenReturn(List.of(first, second));
        when(generatedQuizRepository.findByOwnerUserIdAndNoteIdIn(owner, List.of(firstNote, secondNote)))
                .thenReturn(List.of(firstQuiz, secondQuiz));
        when(studyPackRepository.findByNoteIdIn(List.of(firstNote, secondNote)))
                .thenReturn(List.of(pack(firstNote, firstPack), pack(secondNote, secondPack)));
        when(combinedQuizRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CombinedQuizResponse response = service.assemble(new CreateCombinedQuizRequest("Unit one", List.of(
                new CreateCombinedQuizRequest.Section(firstNote, List.of(0)),
                new CreateCombinedQuizRequest.Section(secondNote, List.of(0))
        )), owner);

        assertThat(response.sections()).extracting(section -> section.title())
                .containsExactly("Cell structure", "Cell division");
        assertThat(response.sections().getFirst().questions()).extracting(QuizItem::question)
                .containsExactly("First");
        assertThat(response.sections().getFirst().questions().getFirst().choices()).containsExactly("B. Smith", "Other");
        assertThat(response.sections().getFirst().questions().getFirst().sourceStudyPackId())
                .isEqualTo(firstPack.toString());
        assertThat(response.sections().get(1).questions().getFirst().sourceStudyPackId())
                .isEqualTo(secondPack.toString());

        // A later source-quiz regeneration replaces its rows; the snapshot still owns the trusted copy.
        firstQuiz.setQuestions(List.of(item("Replacement", "D")));
        assertThat(response.sections().getFirst().questions()).extracting(QuizItem::question).containsExactly("First");
    }

    /** Killing test for dropping the ownership count match: the unowned note exists but is omitted by the query. */
    @Test
    void assemble_rejectsAnExistingButUnownedNoteThroughTheCountMatchGate() {
        UUID owner = UUID.randomUUID();
        UUID ownedNote = UUID.randomUUID();
        UUID unownedNote = UUID.randomUUID();
        when(noteRepository.findByOwnerUserIdAndIdIn(owner, List.of(ownedNote, unownedNote)))
                .thenReturn(List.of(note(ownedNote, owner, "Owned")));

        assertThatThrownBy(() -> service.assemble(new CreateCombinedQuizRequest("Unit", List.of(
                new CreateCombinedQuizRequest.Section(ownedNote, List.of(0)),
                new CreateCombinedQuizRequest.Section(unownedNote, List.of(0))
        )), owner)).isInstanceOf(GeneratedQuizBatchExportValidationException.class)
                .hasMessage("One or more selected notes could not be exported.");

        verify(combinedQuizRepository, never()).save(any());
    }

    /** Killing test for replacing cap rejection with truncation: no row may be written. */
    @Test
    void assemble_rejectsAnOverCapSelectionWithoutPersisting() {
        UUID noteId = UUID.randomUUID();
        List<Integer> tooManyQuestions = java.util.stream.IntStream.rangeClosed(0, CombinedQuizService.MAX_TOTAL_QUESTIONS)
                .boxed().toList();

        assertThatThrownBy(() -> service.assemble(new CreateCombinedQuizRequest("Unit", List.of(
                new CreateCombinedQuizRequest.Section(noteId, tooManyQuestions)
        )), UUID.randomUUID())).isInstanceOf(CombinedQuizValidationException.class);

        verify(combinedQuizRepository, never()).save(any());
        verify(noteRepository, never()).findByOwnerUserIdAndIdIn(any(), any());
    }

    private static NoteEntity note(UUID id, UUID owner, String title) {
        NoteEntity note = new NoteEntity();
        note.setId(id);
        note.setOwnerUserId(owner);
        note.setTitle(title);
        return note;
    }

    private static StudyPackEntity pack(UUID noteId, UUID id) {
        StudyPackEntity pack = new StudyPackEntity();
        pack.setId(id);
        pack.setNoteId(noteId);
        return pack;
    }

    private static GeneratedQuizEntity quiz(UUID noteId, QuizItem... items) {
        GeneratedQuizEntity quiz = new GeneratedQuizEntity();
        quiz.setId(UUID.randomUUID());
        quiz.setNoteId(noteId);
        quiz.setQuestions(List.of(items));
        return quiz;
    }

    private static QuizItem item(String question, String firstChoice) {
        return new QuizItem(question, List.of(firstChoice, "Other"), 0, "Concept", "Explanation");
    }
}
