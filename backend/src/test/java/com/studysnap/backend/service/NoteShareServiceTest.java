package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.SharedNoteResponse;
import com.studysnap.backend.dto.SharedStudyPackResponse;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteShareEntity;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.InvalidNoteShareRequestException;
import com.studysnap.backend.exception.SharedNoteNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteShareRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteShareServiceTest {
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GRANTEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NOTE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID RELATIONSHIP_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock private NoteShareRepository noteShareRepository;
    @Mock private NoteRepository noteRepository;
    @Mock private StudyPackRepository studyPackRepository;
    @Mock private LinkedLearnerRelationshipRepository relationshipRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuthService authService;
    @Mock private OnboardingGuardService onboardingGuardService;
    @Mock private AnalyticsService analyticsService;

    private NoteShareService service;

    @BeforeEach
    void setUp() {
        service = new NoteShareService(
                noteShareRepository,
                noteRepository,
                studyPackRepository,
                relationshipRepository,
                userRepository,
                authService,
                onboardingGuardService,
                analyticsService
        );
    }

    @Test
    void listSharesHidesARecipientWhoseRelationshipIsNoLongerAccepted() {
        // ⚠️ The listing must agree with PUT about what a valid share is. It filtered on revoked_at
        // alone, so a connection that lapsed to PENDING (a v0.89.1 birth-year correction) or REVOKED
        // stayed listed — and round-tripping that list through PUT was then rejected. The recipient's
        // own reads were already denied, so this over-reported rather than leaked.
        when(noteRepository.findByIdAndOwnerUserId(NOTE_ID, OWNER_ID)).thenReturn(Optional.of(note()));
        when(noteShareRepository.findLiveAcceptedByNoteIdOrderByCreatedAtAsc(NOTE_ID))
                .thenReturn(List.of());

        assertThat(service.listShares(OWNER_ID, NOTE_ID.toString())).isEmpty();
        // The unfiltered lookup must not be what the owner-facing listing reads.
        verify(noteShareRepository, never()).findByNoteIdAndRevokedAtIsNullOrderByCreatedAtAsc(NOTE_ID);
    }

    @Test
    void listSharesReturnsARecipientWhoseRelationshipIsStillAccepted() {
        when(noteRepository.findByIdAndOwnerUserId(NOTE_ID, OWNER_ID)).thenReturn(Optional.of(note()));
        when(noteShareRepository.findLiveAcceptedByNoteIdOrderByCreatedAtAsc(NOTE_ID))
                .thenReturn(List.of(share()));
        when(userRepository.findAllById(Set.of(GRANTEE_ID)))
                .thenReturn(List.of(user(GRANTEE_ID, "Maria Santos", "maria@example.com")));

        assertThat(service.listShares(OWNER_ID, NOTE_ID.toString())).hasSize(1);
    }

    @Test
    void replaceSharesIsIdempotentForTheSameDesiredState() {
        NoteEntity note = note();
        NoteShareEntity share = share();
        LinkedLearnerRelationshipEntity relationship = relationship(LinkedLearnerStatus.ACCEPTED);
        UserEntity grantee = user(GRANTEE_ID, "Maria Santos", "maria@example.com");
        when(noteRepository.findByIdAndOwnerUserId(NOTE_ID, OWNER_ID)).thenReturn(Optional.of(note));
        when(relationshipRepository.findAllById(Set.of(RELATIONSHIP_ID))).thenReturn(List.of(relationship));
        // The diff source stays unfiltered on purpose — it must see the TRUE live set so a lapsed row
        // can still be revoked. The RESPONSE is relationship-aware, hence both stubs.
        when(noteShareRepository.findByNoteIdAndRevokedAtIsNullOrderByCreatedAtAsc(NOTE_ID))
                .thenReturn(List.of(share));
        when(noteShareRepository.findLiveAcceptedByNoteIdOrderByCreatedAtAsc(NOTE_ID))
                .thenReturn(List.of(share));
        when(userRepository.findAllById(Set.of(GRANTEE_ID))).thenReturn(List.of(grantee));

        var result = service.replaceShares(OWNER_ID, NOTE_ID.toString(), List.of(RELATIONSHIP_ID));

        assertThat(result).hasSize(1);
        verify(noteShareRepository, never()).saveAll(any());
        verify(noteShareRepository, never()).revokeLiveShares(any(), any(), any());
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void replaceSharesRejectsTheWholeRequestWhenOneRelationshipIsInvalid() {
        UUID invalidRelationshipId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(noteRepository.findByIdAndOwnerUserId(NOTE_ID, OWNER_ID)).thenReturn(Optional.of(note()));
        when(relationshipRepository.findAllById(Set.of(RELATIONSHIP_ID, invalidRelationshipId)))
                .thenReturn(List.of(relationship(LinkedLearnerStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.replaceShares(
                OWNER_ID,
                NOTE_ID.toString(),
                List.of(RELATIONSHIP_ID, invalidRelationshipId)
        )).isInstanceOf(InvalidNoteShareRequestException.class);

        verify(noteShareRepository, never()).saveAll(any());
        verify(noteShareRepository, never()).revokeLiveShares(any(), any(), any());
    }

    @Test
    void relationshipLeavingAcceptedCutsTheNextRecipientRead() {
        NoteShareEntity share = share();
        LinkedLearnerRelationshipEntity relationship = relationship(LinkedLearnerStatus.ACCEPTED);
        when(noteShareRepository.findFirstByNoteIdAndGranteeUserIdAndRevokedAtIsNull(NOTE_ID, GRANTEE_ID))
                .thenReturn(Optional.of(share));
        when(relationshipRepository.findById(RELATIONSHIP_ID)).thenReturn(Optional.of(relationship));
        when(noteRepository.findById(NOTE_ID)).thenReturn(Optional.of(note()));
        when(studyPackRepository.findByNoteId(NOTE_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "Owner", "owner@example.com")));

        assertThat(service.getSharedNote(GRANTEE_ID, NOTE_ID.toString()).id()).isEqualTo(NOTE_ID.toString());
        relationship.setStatus(LinkedLearnerStatus.PENDING);

        assertThatThrownBy(() -> service.getSharedNote(GRANTEE_ID, NOTE_ID.toString()))
                .isInstanceOf(SharedNoteNotFoundException.class)
                .hasMessage("This note is no longer shared with you.");
        relationship.setStatus(LinkedLearnerStatus.REVOKED);

        assertThatThrownBy(() -> service.getSharedNote(GRANTEE_ID, NOTE_ID.toString()))
                .isInstanceOf(SharedNoteNotFoundException.class)
                .hasMessage("This note is no longer shared with you.");
    }

    @Test
    void recipientDtosExposeOnlyTheRatifiedMaterialKeys() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        SharedNoteResponse noteResponse = new SharedNoteResponse(
                NOTE_ID.toString(), "Title", "Content", "Subject", "Program", "COLLEGE",
                List.of("tag"), "GENERATED", "Owner", OffsetDateTime.parse("2026-08-27T00:00:00Z"),
                null, true
        );
        SharedStudyPackResponse studyPackResponse = new SharedStudyPackResponse(
                UUID.randomUUID().toString(), NOTE_ID.toString(), "Title", "Summary", "Full notes",
                List.of("Concept"), List.<QuizItem>of(), "Owner"
        );

        Set<String> noteKeys = new HashSet<>();
        mapper.valueToTree(noteResponse).fieldNames().forEachRemaining(noteKeys::add);
        Set<String> studyPackKeys = new HashSet<>();
        mapper.valueToTree(studyPackResponse).fieldNames().forEachRemaining(studyPackKeys::add);

        assertThat(noteKeys).containsExactlyInAnyOrder(
                        "id", "title", "content", "subject", "courseProgram", "learnerLevel", "tags",
                        "status", "ownerDisplayName", "sharedAt", "studyPackId", "canCopy"
                );
        assertThat(studyPackKeys).containsExactlyInAnyOrder(
                        "id", "noteId", "title", "summary", "fullNotes", "keyConcepts", "quiz",
                        "ownerDisplayName"
                );
    }

    private NoteEntity note() {
        NoteEntity note = new NoteEntity();
        note.setId(NOTE_ID);
        note.setOwnerUserId(OWNER_ID);
        note.setTitle("Shared note");
        note.setContent("Private content");
        note.setTags(new String[0]);
        note.setStatus(NoteStatus.DRAFT);
        note.setVisibility(NoteVisibility.PRIVATE);
        return note;
    }

    private NoteShareEntity share() {
        NoteShareEntity share = new NoteShareEntity();
        share.setId(UUID.randomUUID());
        share.setNoteId(NOTE_ID);
        share.setOwnerUserId(OWNER_ID);
        share.setGranteeUserId(GRANTEE_ID);
        share.setRelationshipId(RELATIONSHIP_ID);
        share.setCreatedAt(OffsetDateTime.parse("2026-08-27T00:00:00Z"));
        return share;
    }

    private LinkedLearnerRelationshipEntity relationship(LinkedLearnerStatus status) {
        LinkedLearnerRelationshipEntity relationship = new LinkedLearnerRelationshipEntity();
        relationship.setId(RELATIONSHIP_ID);
        relationship.setSupporterUserId(OWNER_ID);
        relationship.setLearnerUserId(GRANTEE_ID);
        relationship.setStatus(status);
        return relationship;
    }

    private UserEntity user(UUID id, String displayName, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setDisplayName(displayName);
        user.setEmail(email);
        return user;
    }
}
