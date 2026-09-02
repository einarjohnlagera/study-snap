package com.studysnap.backend.service;

import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Turns a caller-supplied {@code sourceCollectionId} into the set of note ids a plan actually contains.
 *
 * <p>⚠️ THE COLLECTION ID IS A CLAIM, NEVER A PERMISSION. Both multi-note exam paths relax the
 * same-subject rule for notes a plan contains, so a caller who could assert plan scope without it being
 * checked would be switching a validation rule off by naming any id. Ownership is re-verified here, and
 * callers must additionally confirm that each individual source — including the primary — is in the
 * returned set. A caller that treats a non-empty result as blanket permission has reintroduced the bug.
 *
 * <p>It lives in one place on purpose. `v0.100.0` spent a release consolidating four inlined copies of a
 * single authorization predicate; a second copy of this one would be the same mistake with a shorter
 * fuse, because the two exam services already drifted apart on the rule it guards.
 */
@Service
@RequiredArgsConstructor
public class PlanSourcedExamVerifier {
    private final NoteCollectionRepository noteCollectionRepository;
    private final NoteCollectionItemRepository noteCollectionItemRepository;

    /**
     * @param sourceCollectionIdRaw the claimed plan, or null/blank when the caller claims none
     * @param onInvalid             thrown for an unparseable id or a collection this user does not own —
     *                              each exam path has its own source-error contract, so the exception is
     *                              supplied rather than chosen here
     * @return note ids the plan contains, or an empty set when no plan was claimed
     */
    public Set<UUID> resolvePlanMemberNoteIds(
            String sourceCollectionIdRaw,
            UUID userId,
            Supplier<? extends AppException> onInvalid
    ) {
        if (sourceCollectionIdRaw == null || sourceCollectionIdRaw.isBlank()) {
            return Set.of();
        }
        UUID sourceCollectionId = UuidParsingUtils.parseUuidOrThrow(sourceCollectionIdRaw, onInvalid);
        if (noteCollectionRepository.findByIdAndOwnerUserId(sourceCollectionId, userId).isEmpty()) {
            throw onInvalid.get();
        }
        return resolvePlanMembers(sourceCollectionIdRaw, userId, onInvalid).stream()
                .map(PlanExamMember::noteId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Resolves the verified plan members without discarding the section label or plan position.
     *
     * <p>The verifier still owns only membership authorization. Callers decide separately whether a
     * member has a ready Study Pack, so this shared predicate does not grow a generation concern.
     */
    public List<PlanExamMember> resolvePlanMembers(
            String sourceCollectionIdRaw,
            UUID userId,
            Supplier<? extends AppException> onInvalid
    ) {
        if (sourceCollectionIdRaw == null || sourceCollectionIdRaw.isBlank()) {
            return List.of();
        }
        UUID sourceCollectionId = UuidParsingUtils.parseUuidOrThrow(sourceCollectionIdRaw, onInvalid);
        if (noteCollectionRepository.findByIdAndOwnerUserId(sourceCollectionId, userId).isEmpty()) {
            throw onInvalid.get();
        }
        return noteCollectionItemRepository.findByCollectionIdOrderByPositionAsc(sourceCollectionId).stream()
                .map(item -> new PlanExamMember(item.getNoteId(), item.getLabel(), item.getPosition()))
                .toList();
    }

    public record PlanExamMember(UUID noteId, String label, int position) {
    }
}
