package com.studysnap.backend.service;

import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.exception.InvalidLongExamSourceException;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ⚠️ THIS CLASS EXISTS BECAUSE ITS ABSENCE WAS A REAL, PROVEN HOLE.
 *
 * <p>A pre-signoff cold agent deleted the ownership check in {@link PlanSourcedExamVerifier} and ran the
 * ENTIRE backend suite: 1894 tests, zero failures. Both exam service tests mock this collaborator, so
 * every plan test stubbed a hand-built member set and none of them exercised the check that decides
 * whether a caller may claim a plan at all.
 *
 * <p>The hole was created by a refactor, and that is the part worth remembering: the same mutant was run
 * and killed while this logic was still inlined in {@code LongExamService}. Extracting it to a
 * collaborator — the right call, since a second inlined copy is what `v0.100.0` spent a release undoing —
 * silently moved it behind a mock and deleted coverage that had already been verified. **Re-run a mutant
 * after extracting the code it covers; the pre-extraction kill does not carry.**
 */
@ExtendWith(MockitoExtension.class)
class PlanSourcedExamVerifierTest {

    @Mock
    private NoteCollectionRepository noteCollectionRepository;
    @Mock
    private NoteCollectionItemRepository noteCollectionItemRepository;
    @InjectMocks
    private PlanSourcedExamVerifier verifier;

    @Test
    void rejectsACollectionTheCallerDoesNotOwn() {
        // The whole point of the class. A collection id arrives in a request body, so an unowned one must
        // be refused before its membership can relax anybody's same-subject rule.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(noteCollectionRepository.findByIdAndOwnerUserId(collectionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.resolvePlanMemberNoteIds(
                collectionId.toString(),
                userId,
                InvalidLongExamSourceException::new
        )).isInstanceOf(InvalidLongExamSourceException.class);

        verify(noteCollectionItemRepository, never()).findByCollectionIdOrderByPositionAsc(any());
    }

    @Test
    void returnsMemberNoteIdsForACollectionTheCallerOwns() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        when(noteCollectionRepository.findByIdAndOwnerUserId(collectionId, userId))
                .thenReturn(Optional.of(new NoteCollectionEntity()));
        when(noteCollectionItemRepository.findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(List.of(
                        buildItem(collectionId, firstNoteId),
                        buildItem(collectionId, secondNoteId)
                ));

        Set<UUID> members = verifier.resolvePlanMemberNoteIds(
                collectionId.toString(),
                userId,
                InvalidLongExamSourceException::new
        );

        assertThat(members).containsExactly(firstNoteId, secondNoteId);
    }

    @Test
    void treatsAnAbsentClaimAsNoPlanWithoutTouchingAnyRepository() {
        // A caller claiming nothing must not be charged a lookup, and must not be told anything about
        // whether some collection exists.
        UUID userId = UUID.randomUUID();

        assertThat(verifier.resolvePlanMemberNoteIds(null, userId, InvalidLongExamSourceException::new)).isEmpty();
        assertThat(verifier.resolvePlanMemberNoteIds("", userId, InvalidLongExamSourceException::new)).isEmpty();
        assertThat(verifier.resolvePlanMemberNoteIds("   ", userId, InvalidLongExamSourceException::new)).isEmpty();

        verify(noteCollectionRepository, never()).findByIdAndOwnerUserId(any(), any());
        verify(noteCollectionItemRepository, never()).findByCollectionIdOrderByPositionAsc(any());
    }

    @Test
    void rejectsAMalformedCollectionIdWithTheCallersOwnErrorContract() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> verifier.resolvePlanMemberNoteIds(
                "not-a-uuid",
                userId,
                InvalidLongExamSourceException::new
        )).isInstanceOf(InvalidLongExamSourceException.class);

        verify(noteCollectionRepository, never()).findByIdAndOwnerUserId(any(), any());
    }

    @Test
    void returnsAnEmptySetForAnOwnedButEmptyCollection() {
        // ⚠️ An owned-but-empty plan must NOT read as "plan scope with no members" — callers treat an
        // empty set as "no plan claimed", which keeps the strict same-subject rule rather than relaxing it.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(noteCollectionRepository.findByIdAndOwnerUserId(collectionId, userId))
                .thenReturn(Optional.of(new NoteCollectionEntity()));
        when(noteCollectionItemRepository.findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(List.of());

        assertThat(verifier.resolvePlanMemberNoteIds(
                collectionId.toString(),
                userId,
                InvalidLongExamSourceException::new
        )).isEmpty();
    }

    private NoteCollectionItemEntity buildItem(UUID collectionId, UUID noteId) {
        NoteCollectionItemEntity item = new NoteCollectionItemEntity();
        item.setId(UUID.randomUUID());
        item.setCollectionId(collectionId);
        item.setNoteId(noteId);
        item.setPosition(0);
        return item;
    }
}
