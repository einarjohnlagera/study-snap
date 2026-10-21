package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studysnap.backend.config.StudySnapProperties;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialChallengeQuizTemplateResolverTest {
    @Mock private NoteRepository noteRepository;
    @Mock private StudyPackRepository studyPackRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChallengeQuizQuestionBankRepository questionBankRepository;

    @Test
    void copiedOfficialTemplateOwnStatementPairIsEligibleForOrdinaryAndRedoClaims() {
        UUID ownerId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        UUID sourcePackId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        NoteEntity note = note(noteId, ownerId, NoteVisibility.PUBLIC, sourceNoteId);
        stubPackAndNote(packId, note, ownerId);
        stubSource(sourcePackId, sourceNoteId, sourceOwnerId);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(author(ownerId, UserRole.ADMIN)));

        ChallengeQuizQuestionBankEntity ownRow = statementPair("Own Official statement", "INCORRECT");
        ChallengeQuizQuestionBankEntity sourceRow = statementPair("Source Official statement", "INCORRECT");
        when(questionBankRepository.findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(ownerId, packId))
                .thenReturn(List.of(ownRow));
        when(questionBankRepository.findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(sourceOwnerId, sourcePackId))
                .thenReturn(List.of(sourceRow));
        when(questionBankRepository.findClaimableForUpdate(ownerId, packId, LearnerLevel.COLLEGE.name(), packId))
                .thenReturn(List.of(ownRow));
        when(questionBankRepository.findIncorrectClaimable(ownerId, packId, LearnerLevel.COLLEGE.name(), "INCORRECT"))
                .thenReturn(List.of(ownRow));
        when(questionBankRepository.findIncorrectClaimableForUpdate(ownerId, packId, LearnerLevel.COLLEGE.name(), "INCORRECT"))
                .thenReturn(List.of(ownRow));

        OfficialChallengeQuizTemplateResolver resolver = resolver();
        StudySnapProperties properties = new StudySnapProperties();
        properties.getChallengeQuiz().setStatementPairLegacyCutoff(OffsetDateTime.parse("2026-10-21T00:00:00Z"));
        ChallengeQuizQuestionBankService bankService = new ChallengeQuizQuestionBankService(
                questionBankRepository, resolver, properties);

        assertThat(resolver.templateQuestionKeys(packId))
                .containsExactlyInAnyOrder(ownRow.getQuestionKey(), sourceRow.getQuestionKey());
        assertThat(bankService.claimEligibleQuestions(ownerId, packId, LearnerLevel.COLLEGE, packId, Set.of(), 1))
                .containsExactly(ownRow.getQuestion());
        // The ordinary session has completed and released its claim before Redo Missed starts.
        ownRow.setClaimedSessionId(null);
        assertThat(bankService.countEligibleIncorrectQuestions(ownerId, packId, LearnerLevel.COLLEGE)).isEqualTo(1);
        assertThat(bankService.claimIncorrectQuestions(ownerId, packId, LearnerLevel.COLLEGE,
                UUID.randomUUID(), 1, 1)).containsExactly(ownRow.getQuestion());
    }

    @Test
    void learnerCopyUsesOnlyItsOfficialSourceKeys() {
        UUID learnerId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        UUID sourcePackId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        stubPackAndNote(packId, note(UUID.randomUUID(), learnerId, NoteVisibility.PUBLIC, sourceNoteId), learnerId);
        stubSource(sourcePackId, sourceNoteId, sourceOwnerId);
        when(userRepository.findById(learnerId)).thenReturn(Optional.of(author(learnerId, UserRole.USER)));
        ChallengeQuizQuestionBankEntity sourceRow = statementPair("Source statement", "INCORRECT");
        when(questionBankRepository.findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(sourceOwnerId, sourcePackId))
                .thenReturn(List.of(sourceRow));

        assertThat(resolver().templateQuestionKeys(packId)).containsExactly(sourceRow.getQuestionKey());
        verify(questionBankRepository, never())
                .findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(learnerId, packId);
    }

    @Test
    void templateCopyingStillResolvesOnlyTheAdoptedSource() {
        UUID ownerId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        UUID sourceOwnerId = UUID.randomUUID();
        UUID sourcePackId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        stubPackAndNote(packId, note(UUID.randomUUID(), ownerId, NoteVisibility.PUBLIC, sourceNoteId), ownerId);
        stubSource(sourcePackId, sourceNoteId, sourceOwnerId);

        assertThat(resolver().resolveTemplate(packId)).contains(
                new OfficialChallengeQuizTemplateService.OfficialTemplateKey(sourceOwnerId, sourcePackId));
        verify(userRepository, never()).findById(ownerId);
        verify(questionBankRepository, never())
                .findByUserIdAndStudyPackIdOrderByGeneratedAtAsc(ownerId, packId);
    }

    private OfficialChallengeQuizTemplateResolver resolver() {
        return new OfficialChallengeQuizTemplateResolver(
                noteRepository, studyPackRepository, userRepository, questionBankRepository);
    }

    private void stubPackAndNote(UUID packId, NoteEntity note, UUID ownerId) {
        StudyPackEntity pack = new StudyPackEntity();
        pack.setId(packId);
        pack.setNoteId(note.getId());
        pack.setOwnerUserId(ownerId);
        when(studyPackRepository.findById(packId)).thenReturn(Optional.of(pack));
        when(noteRepository.findById(note.getId())).thenReturn(Optional.of(note));
    }

    private void stubSource(UUID packId, UUID noteId, UUID ownerId) {
        NoteEntity source = note(noteId, ownerId, NoteVisibility.PUBLIC, null);
        StudyPackEntity sourcePack = new StudyPackEntity();
        sourcePack.setId(packId);
        sourcePack.setNoteId(noteId);
        sourcePack.setOwnerUserId(ownerId);
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(source));
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.of(sourcePack));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(author(ownerId, UserRole.ADMIN)));
    }

    private static NoteEntity note(UUID noteId, UUID ownerId, NoteVisibility visibility, UUID copiedFromNoteId) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerId);
        note.setVisibility(visibility);
        note.setCopiedFromNoteId(copiedFromNoteId);
        return note;
    }

    private static UserEntity author(UUID userId, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRole(role);
        return user;
    }

    private static ChallengeQuizQuestionBankEntity statementPair(String label, String outcome) {
        ChallengeQuizQuestionBankEntity row = new ChallengeQuizQuestionBankEntity();
        row.setQuestionKey(label);
        row.setQuestion(new QuizItem("Statement 1: " + label + ". Statement 2: Another claim.",
                List.of("Both", "Only statement 1", "Only statement 2", "Neither"), 0,
                "Concept", "Explanation"));
        row.setLastKnownOutcome(outcome);
        row.setGeneratedAt(OffsetDateTime.parse("2026-10-20T00:00:00Z"));
        return row;
    }
}
