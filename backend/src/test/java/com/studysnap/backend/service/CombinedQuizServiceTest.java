package com.studysnap.backend.service;

import com.studysnap.backend.dto.CombinedQuizResponse;
import com.studysnap.backend.dto.CombinedQuizSummaryResponse;
import com.studysnap.backend.dto.CreateCombinedQuizRequest;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.CombinedQuizValidationException;
import com.studysnap.backend.entity.CombinedQuizEntity;
import com.studysnap.backend.exception.CombinedQuizNotFoundException;
import com.studysnap.backend.exception.GeneratedQuizBatchExportValidationException;
import com.studysnap.backend.dto.CombinedQuizSection;
import com.studysnap.backend.repository.CombinedQuizRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuizShareLinkRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.entity.QuizShareLinkEntity;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class CombinedQuizServiceTest {
    @Mock private CombinedQuizRepository combinedQuizRepository;
    @Mock private NoteRepository noteRepository;
    @Mock private GeneratedQuizRepository generatedQuizRepository;
    @Mock private StudyPackRepository studyPackRepository;
    @Mock private QuizShareLinkRepository quizShareLinkRepository;
    @Mock private AuthService authService;
    @Mock private OnboardingGuardService onboardingGuardService;

    private CombinedQuizService service;

    @BeforeEach
    void setUp() {
        service = new CombinedQuizService(combinedQuizRepository, noteRepository, generatedQuizRepository,
                studyPackRepository, quizShareLinkRepository, authService, onboardingGuardService);
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
        // ⚠️ THIS PROVES THE COPY IS TRUSTED IN MEMORY ONLY, AND MUST NOT BE READ AS AN END-TO-END
        // GUARANTEE. The repository is mocked, so nothing here crosses the storage boundary. Round-tripping
        // this value through JSONB strips a SECOND label, because QuizItem's @JsonCreator routes into the
        // sanitizing constructor and QuizValidationUtils.sanitizeChoiceTexts is not idempotent:
        // "B. Smith" is read back as "Smith". That is a live, product-wide defect recorded as a Known
        // limitation in RELEASES.md -- it is NOT closed by the trusted-copy helpers, which only prevent a
        // THIRD strip within a single in-memory copy.
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
    /**
     * ⚠️ FINDING 3 — the ownership scope on the read had NO coverage: swapping
     * {@code findByIdAndOwnerUserId} for {@code findById} passed every test in the suite, and a full answer
     * key (correctIndex, correctIndices, explanation for up to 100 questions) sits behind that one
     * predicate.
     */
    @Test
    void getById_refusesACombinedQuizOwnedBySomeoneElse() {
        UUID combinedQuizId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        when(combinedQuizRepository.findByIdAndOwnerUserId(combinedQuizId, caller)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(combinedQuizId, caller))
                .isInstanceOf(CombinedQuizNotFoundException.class);

        // The owner-scoped query is the gate; an unscoped lookup must never be the one consulted.
        verify(combinedQuizRepository, never()).findById(any(UUID.class));
    }

    @Test
    void getById_returnsTheOwnersOwnCombinedQuiz() {
        UUID combinedQuizId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        CombinedQuizEntity entity = new CombinedQuizEntity();
        entity.setId(combinedQuizId);
        entity.setOwnerUserId(owner);
        entity.setTitle("Unit review");
        entity.setSections(List.of(new CombinedQuizSection("First note", List.of())));
        entity.setCreatedAt(OffsetDateTime.now());
        when(combinedQuizRepository.findByIdAndOwnerUserId(combinedQuizId, owner)).thenReturn(Optional.of(entity));

        assertThat(service.getById(combinedQuizId, owner).title()).isEqualTo("Unit review");
    }

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

    /** Killing test for removing either the owner predicate or the defensive owner check in the list path. */
    @Test
    void list_returnsOnlyCallerOwnedQuizzesNewestFirstAndUsesTheBoundedOwnerQuery() {
        UUID owner = UUID.randomUUID();
        CombinedQuizEntity newest = combinedQuiz(UUID.randomUUID(), owner, "Newest", "2026-09-04T12:00:00Z", List.of());
        CombinedQuizEntity anotherOwnersQuiz = combinedQuiz(UUID.randomUUID(), UUID.randomUUID(), "Not mine", "2026-09-04T11:00:00Z", List.of());
        CombinedQuizEntity oldest = combinedQuiz(UUID.randomUUID(), owner, "Oldest", "2026-09-04T10:00:00Z", List.of());
        when(combinedQuizRepository.findByOwnerUserIdOrderByCreatedAtDesc(eq(owner), any(Pageable.class)))
                .thenReturn(List.of(newest, anotherOwnersQuiz, oldest));
        when(quizShareLinkRepository.findByCombinedQuizIdInAndOwnerUserId(any(), eq(owner))).thenReturn(List.of());

        List<CombinedQuizSummaryResponse> summaries = service.list(owner);

        assertThat(summaries).extracting(CombinedQuizSummaryResponse::id)
                .containsExactly(newest.getId(), oldest.getId());
        assertThat(summaries).extracting(CombinedQuizSummaryResponse::title).doesNotContain("Not mine");
        org.mockito.ArgumentCaptor<Pageable> page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(combinedQuizRepository).findByOwnerUserIdOrderByCreatedAtDesc(eq(owner), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(CombinedQuizService.MAX_LIST_RESULTS);
        // The repository method owns `created_at DESC`, matching V132's index; Pageable only owns the cap.
        assertThat(page.getValue().getSort().isUnsorted()).isTrue();
        org.mockito.InOrder guardsThenRead = inOrder(authService, onboardingGuardService, combinedQuizRepository);
        guardsThenRead.verify(authService).requireEmailVerified(owner);
        guardsThenRead.verify(onboardingGuardService).assertProfileComplete(owner);
        guardsThenRead.verify(combinedQuizRepository).findByOwnerUserIdOrderByCreatedAtDesc(eq(owner), any(Pageable.class));
    }

    /** Killing test for taking the first batch link instead of collapsing a quiz's links to its newest row. */
    @Test
    void list_countsStoredSectionsAndQuestionsAndUsesTheLatestShareLinkState() {
        UUID owner = UUID.randomUUID();
        CombinedQuizEntity repeatedSourceSections = combinedQuiz(
                UUID.randomUUID(), owner, "Repeated source snapshot", "2026-09-04T12:00:00Z",
                List.of(
                        new CombinedQuizSection("One source note", List.of(storedItem("One"), storedItem("Two"))),
                        new CombinedQuizSection("One source note", List.of(storedItem("Three")))
                )
        );
        CombinedQuizEntity sharingOff = combinedQuiz(UUID.randomUUID(), owner, "Off", "2026-09-04T11:00:00Z", List.of());
        CombinedQuizEntity noLink = combinedQuiz(UUID.randomUUID(), owner, "No link", "2026-09-04T10:00:00Z", List.of());
        when(combinedQuizRepository.findByOwnerUserIdOrderByCreatedAtDesc(eq(owner), any(Pageable.class)))
                .thenReturn(List.of(repeatedSourceSections, sharingOff, noLink));
        when(quizShareLinkRepository.findByCombinedQuizIdInAndOwnerUserId(any(), eq(owner))).thenReturn(List.of(
                shareLink(repeatedSourceSections.getId(), owner, false, "2026-09-04T09:00:00Z"),
                shareLink(repeatedSourceSections.getId(), owner, true, "2026-09-04T10:00:00Z"),
                shareLink(sharingOff.getId(), owner, false, "2026-09-04T10:00:00Z")
        ));

        List<CombinedQuizSummaryResponse> summaries = service.list(owner);

        assertThat(summaries).extracting(CombinedQuizSummaryResponse::sharing).containsExactly(
                CombinedQuizSummaryResponse.Sharing.SHARING_ON,
                CombinedQuizSummaryResponse.Sharing.SHARING_OFF,
                CombinedQuizSummaryResponse.Sharing.NO_LINK
        );
        assertThat(summaries.getFirst().sectionCount()).isEqualTo(2);
        assertThat(summaries.getFirst().questionCount()).isEqualTo(3);
    }

    /** Killing test for replacing the one batch link read with a per-quiz lookup. */
    @Test
    void list_readsShareLinksOnceForMultipleQuizzes() {
        UUID owner = UUID.randomUUID();
        when(combinedQuizRepository.findByOwnerUserIdOrderByCreatedAtDesc(eq(owner), any(Pageable.class))).thenReturn(List.of(
                combinedQuiz(UUID.randomUUID(), owner, "One", "2026-09-04T12:00:00Z", List.of()),
                combinedQuiz(UUID.randomUUID(), owner, "Two", "2026-09-04T11:00:00Z", List.of())
        ));
        when(quizShareLinkRepository.findByCombinedQuizIdInAndOwnerUserId(any(), eq(owner))).thenReturn(List.of());

        service.list(owner);

        verify(quizShareLinkRepository).findByCombinedQuizIdInAndOwnerUserId(any(), eq(owner));
        verify(quizShareLinkRepository, never()).findFirstByCombinedQuizIdAndOwnerUserIdOrderByCreatedAtDesc(any(), any());
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

    private static CombinedQuizEntity combinedQuiz(
            UUID id,
            UUID owner,
            String title,
            String createdAt,
            List<CombinedQuizSection> sections
    ) {
        CombinedQuizEntity quiz = new CombinedQuizEntity();
        quiz.setId(id);
        quiz.setOwnerUserId(owner);
        quiz.setTitle(title);
        quiz.setCreatedAt(OffsetDateTime.parse(createdAt).withOffsetSameInstant(ZoneOffset.UTC));
        quiz.setSections(sections);
        return quiz;
    }

    private static QuizShareLinkEntity shareLink(UUID combinedQuizId, UUID owner, boolean active, String createdAt) {
        QuizShareLinkEntity link = new QuizShareLinkEntity();
        link.setId(UUID.randomUUID());
        link.setCombinedQuizId(combinedQuizId);
        link.setOwnerUserId(owner);
        link.setActive(active);
        link.setCreatedAt(OffsetDateTime.parse(createdAt).withOffsetSameInstant(ZoneOffset.UTC));
        return link;
    }

    private static QuizItem storedItem(String question) {
        return QuizItem.fromStoredComponents(
                question, List.of("Choice"), 0, "Concept", "Explanation", null,
                "MCQ", null, null, null, null, null, null, null, null
        );
    }
}
