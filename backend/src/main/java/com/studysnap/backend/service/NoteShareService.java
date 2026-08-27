package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteShareResponse;
import com.studysnap.backend.dto.SharedNoteListItemResponse;
import com.studysnap.backend.dto.SharedNoteResponse;
import com.studysnap.backend.dto.SharedNotesPageResponse;
import com.studysnap.backend.dto.SharedStudyPackResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteShareEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.InvalidLibraryQueryException;
import com.studysnap.backend.exception.InvalidNoteShareRequestException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.SharedNoteNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteShareRepository;
import com.studysnap.backend.repository.SharedNoteListProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.util.CreatedAtIdCursorUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteShareService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String CURSOR_PARAMETER = "shared-with-me cursor";
    private static final String RECIPIENT_COUNT_METADATA = "recipientCount";
    private static final String STUDY_PACK_READY_METADATA = "studyPackReady";
    private static final String DAYS_SINCE_SHARED_METADATA = "daysSinceShared";

    private final NoteShareRepository noteShareRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final LinkedLearnerRelationshipRepository relationshipRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final OnboardingGuardService onboardingGuardService;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public List<NoteShareResponse> listShares(UUID callerUserId, String noteIdRaw) {
        requireEligibleCaller(callerUserId);
        UUID noteId = parseNoteId(noteIdRaw);
        requireOwnedNote(noteId, callerUserId);
        return toShareResponses(noteShareRepository.findByNoteIdAndRevokedAtIsNullOrderByCreatedAtAsc(noteId));
    }

    @Transactional
    public List<NoteShareResponse> replaceShares(
            UUID callerUserId,
            String noteIdRaw,
            List<UUID> requestedRelationshipIds
    ) {
        requireEligibleCaller(callerUserId);
        UUID noteId = parseNoteId(noteIdRaw);
        NoteEntity note = requireOwnedNote(noteId, callerUserId);
        Set<UUID> desiredRelationshipIds = new LinkedHashSet<>(requestedRelationshipIds);
        Map<UUID, LinkedLearnerRelationshipEntity> desiredRelationships = relationshipRepository
                .findAllById(desiredRelationshipIds)
                .stream()
                .collect(Collectors.toMap(LinkedLearnerRelationshipEntity::getId, Function.identity()));
        if (desiredRelationships.size() != desiredRelationshipIds.size()
                || desiredRelationships.values().stream().anyMatch(
                        relationship -> !isAcceptedPartyRelationship(relationship, callerUserId))) {
            throw new InvalidNoteShareRequestException();
        }

        List<NoteShareEntity> liveShares = noteShareRepository
                .findByNoteIdAndRevokedAtIsNullOrderByCreatedAtAsc(noteId);
        Set<UUID> currentRelationshipIds = liveShares.stream()
                .map(NoteShareEntity::getRelationshipId)
                .collect(Collectors.toSet());
        List<UUID> relationshipIdsToRevoke = currentRelationshipIds.stream()
                .filter(relationshipId -> !desiredRelationshipIds.contains(relationshipId))
                .toList();
        // ⚠️ Collapse by GRANTEE, not by relationship id. Two people can hold a live relationship in BOTH
        // directions — the live-row index on linked_learner_relationships is directional — so the caller's
        // own connection list can contain two rows resolving to the same person. Adding both would insert
        // two live rows for (note, grantee) and violate ux_note_shares_live, turning a legitimate request
        // into a 500 with no way for the owner to tell the two identical-looking entries apart.
        Set<UUID> granteesAlreadyLive = liveShares.stream()
                .map(NoteShareEntity::getGranteeUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<LinkedLearnerRelationshipEntity> relationshipsToAdd = desiredRelationshipIds.stream()
                .filter(relationshipId -> !currentRelationshipIds.contains(relationshipId))
                .map(desiredRelationships::get)
                .filter(relationship -> granteesAlreadyLive.add(resolveGranteeUserId(callerUserId, relationship)))
                .toList();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int revokedCount = relationshipIdsToRevoke.isEmpty()
                ? 0
                : noteShareRepository.revokeLiveShares(noteId, relationshipIdsToRevoke, now);
        if (!relationshipsToAdd.isEmpty()) {
            List<NoteShareEntity> additions = relationshipsToAdd.stream()
                    .map(relationship -> newShare(note, callerUserId, relationship, now))
                    .toList();
            noteShareRepository.saveAll(additions);
        }

        if (!relationshipsToAdd.isEmpty()) {
            analyticsService.trackEvent(
                    callerUserId,
                    AnalyticsEventType.NOTE_SHARED_WITH_CONNECTION,
                    noteId,
                    Map.of(
                            RECIPIENT_COUNT_METADATA, relationshipsToAdd.size(),
                            STUDY_PACK_READY_METADATA, isStudyPackReady(noteId)
                    )
            );
        }
        if (revokedCount > 0) {
            analyticsService.trackEvent(
                    callerUserId,
                    AnalyticsEventType.NOTE_SHARE_REVOKED,
                    noteId,
                    Map.of(RECIPIENT_COUNT_METADATA, revokedCount)
            );
        }
        return toShareResponses(noteShareRepository.findByNoteIdAndRevokedAtIsNullOrderByCreatedAtAsc(noteId));
    }

    @Transactional(readOnly = true)
    public SharedNotesPageResponse listSharedWithMe(
            UUID callerUserId,
            Integer requestedPageSize,
            String cursor
    ) {
        requireEligibleCaller(callerUserId);
        int pageSize = requestedPageSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.clamp(requestedPageSize, 1, MAX_PAGE_SIZE);
        CreatedAtIdCursorUtils.CursorToken cursorToken = decodeCursor(cursor);
        List<SharedNoteListProjection> fetched = noteShareRepository.findSharedWithMe(
                callerUserId,
                cursorToken == null ? null : cursorToken.createdAt(),
                cursorToken == null ? null : cursorToken.id(),
                PageRequest.of(0, pageSize + 1)
        );
        boolean hasMore = fetched.size() > pageSize;
        List<SharedNoteListProjection> page = hasMore ? fetched.subList(0, pageSize) : fetched;
        List<SharedNoteListItemResponse> items = page.stream().map(this::toListItemResponse).toList();
        String nextCursor = hasMore && !page.isEmpty()
                ? CreatedAtIdCursorUtils.encode(page.getLast().getSharedAt(), page.getLast().getShareId())
                : null;
        return new SharedNotesPageResponse(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public SharedNoteResponse getSharedNote(UUID callerUserId, String noteIdRaw) {
        requireEligibleCaller(callerUserId);
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(noteIdRaw, SharedNoteNotFoundException::new);
        NoteShareEntity share = requireAuthorizedShare(noteId, callerUserId);
        NoteEntity note = noteRepository.findById(noteId).orElseThrow(SharedNoteNotFoundException::new);
        if (!note.getOwnerUserId().equals(share.getOwnerUserId())) {
            throw new SharedNoteNotFoundException();
        }
        StudyPackEntity studyPack = studyPackRepository.findByNoteId(noteId)
                .filter(pack -> pack.getStatus() == StudyPackStatus.DONE)
                .orElse(null);
        analyticsService.trackEvent(
                callerUserId,
                AnalyticsEventType.SHARED_NOTE_OPENED,
                noteId,
                Map.of(DAYS_SINCE_SHARED_METADATA, daysSince(share.getCreatedAt()))
        );
        return new SharedNoteResponse(
                note.getId().toString(),
                note.getTitle(),
                note.getContent(),
                note.getSubject(),
                note.getCourseProgram(),
                note.getLearnerLevel() == null ? null : note.getLearnerLevel().name(),
                note.getTags() == null ? List.of() : Arrays.asList(note.getTags()),
                note.getStatus().name(),
                resolveDisplayName(requireUser(note.getOwnerUserId())),
                share.getCreatedAt(),
                studyPack == null ? null : studyPack.getId().toString(),
                true
        );
    }

    @Transactional(readOnly = true)
    public SharedStudyPackResponse getSharedStudyPack(UUID callerUserId, String studyPackIdRaw) {
        requireEligibleCaller(callerUserId);
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                SharedNoteNotFoundException::new
        );
        NoteShareEntity share = noteShareRepository.findLiveByStudyPackIdAndGranteeUserId(studyPackId, callerUserId)
                .orElseThrow(SharedNoteNotFoundException::new);
        requireAcceptedRelationship(share, callerUserId);
        StudyPackEntity studyPack = studyPackRepository.findById(studyPackId)
                .filter(pack -> pack.getStatus() == StudyPackStatus.DONE)
                .orElseThrow(SharedNoteNotFoundException::new);
        NoteEntity note = noteRepository.findById(share.getNoteId())
                .orElseThrow(SharedNoteNotFoundException::new);
        if (!share.getNoteId().equals(studyPack.getNoteId())
                || !share.getOwnerUserId().equals(studyPack.getOwnerUserId())
                || !share.getOwnerUserId().equals(note.getOwnerUserId())) {
            throw new SharedNoteNotFoundException();
        }
        analyticsService.trackEvent(
                callerUserId,
                AnalyticsEventType.SHARED_STUDY_PACK_OPENED,
                studyPackId,
                Map.of()
        );
        return new SharedStudyPackResponse(
                studyPack.getId().toString(),
                studyPack.getNoteId().toString(),
                studyPack.getTitle(),
                studyPack.getSummary(),
                note.getContent(),
                studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                studyPack.getQuiz() == null ? List.of() : studyPack.getQuiz(),
                resolveDisplayName(requireUser(share.getOwnerUserId()))
        );
    }

    @Transactional(readOnly = true)
    public void requireSharedNoteAccess(UUID callerUserId, UUID noteId) {
        requireEligibleCaller(callerUserId);
        requireAuthorizedShare(noteId, callerUserId);
    }

    @Transactional(readOnly = true)
    public void requireSharedStudyPackAccess(UUID callerUserId, UUID studyPackId) {
        requireEligibleCaller(callerUserId);
        NoteShareEntity share = noteShareRepository.findLiveByStudyPackIdAndGranteeUserId(studyPackId, callerUserId)
                .orElseThrow(SharedNoteNotFoundException::new);
        requireAcceptedRelationship(share, callerUserId);
    }

    private void requireEligibleCaller(UUID callerUserId) {
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
    }

    private UUID parseNoteId(String noteIdRaw) {
        return UuidParsingUtils.parseUuidOrThrow(noteIdRaw, NoteNotFoundException::new);
    }

    private NoteEntity requireOwnedNote(UUID noteId, UUID ownerUserId) {
        return noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(NoteNotFoundException::new);
    }

    private NoteShareEntity requireAuthorizedShare(UUID noteId, UUID callerUserId) {
        NoteShareEntity share = noteShareRepository
                .findFirstByNoteIdAndGranteeUserIdAndRevokedAtIsNull(noteId, callerUserId)
                .orElseThrow(SharedNoteNotFoundException::new);
        requireAcceptedRelationship(share, callerUserId);
        return share;
    }

    private void requireAcceptedRelationship(NoteShareEntity share, UUID callerUserId) {
        LinkedLearnerRelationshipEntity relationship = relationshipRepository.findById(share.getRelationshipId())
                .orElseThrow(SharedNoteNotFoundException::new);
        if (relationship.getStatus() != LinkedLearnerStatus.ACCEPTED
                || !share.getGranteeUserId().equals(callerUserId)
                || !isRelationshipBetween(relationship, share.getOwnerUserId(), share.getGranteeUserId())) {
            throw new SharedNoteNotFoundException();
        }
    }

    private boolean isAcceptedPartyRelationship(
            LinkedLearnerRelationshipEntity relationship,
            UUID callerUserId
    ) {
        return relationship.getStatus() == LinkedLearnerStatus.ACCEPTED
                && (callerUserId.equals(relationship.getSupporterUserId())
                    || callerUserId.equals(relationship.getLearnerUserId()));
    }

    private boolean isRelationshipBetween(
            LinkedLearnerRelationshipEntity relationship,
            UUID firstUserId,
            UUID secondUserId
    ) {
        return (firstUserId.equals(relationship.getSupporterUserId())
                && secondUserId.equals(relationship.getLearnerUserId()))
                || (firstUserId.equals(relationship.getLearnerUserId())
                && secondUserId.equals(relationship.getSupporterUserId()));
    }

    private static UUID resolveGranteeUserId(UUID ownerUserId, LinkedLearnerRelationshipEntity relationship) {
        return ownerUserId.equals(relationship.getSupporterUserId())
                ? relationship.getLearnerUserId()
                : relationship.getSupporterUserId();
    }

    private NoteShareEntity newShare(
            NoteEntity note,
            UUID ownerUserId,
            LinkedLearnerRelationshipEntity relationship,
            OffsetDateTime createdAt
    ) {
        UUID granteeUserId = resolveGranteeUserId(ownerUserId, relationship);
        NoteShareEntity share = new NoteShareEntity();
        share.setId(UUID.randomUUID());
        share.setNoteId(note.getId());
        share.setOwnerUserId(ownerUserId);
        share.setGranteeUserId(granteeUserId);
        share.setRelationshipId(relationship.getId());
        share.setCreatedAt(createdAt);
        return share;
    }

    private List<NoteShareResponse> toShareResponses(List<NoteShareEntity> shares) {
        Map<UUID, UserEntity> usersById = userRepository.findAllById(
                        shares.stream().map(NoteShareEntity::getGranteeUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        List<NoteShareResponse> responses = new ArrayList<>();
        for (NoteShareEntity share : shares) {
            UserEntity grantee = usersById.get(share.getGranteeUserId());
            if (grantee != null) {
                responses.add(new NoteShareResponse(
                        share.getRelationshipId(),
                        resolveDisplayName(grantee),
                        grantee.getEmail(),
                        share.getCreatedAt()
                ));
            }
        }
        return responses;
    }

    private SharedNoteListItemResponse toListItemResponse(SharedNoteListProjection projection) {
        return new SharedNoteListItemResponse(
                projection.getNoteId().toString(),
                projection.getTitle(),
                projection.getSubject(),
                resolveDisplayName(
                        projection.getOwnerDisplayName(),
                        projection.getOwnerFirstName(),
                        projection.getOwnerLastName(),
                        projection.getOwnerEmail()
                ),
                StudyPackStatus.DONE.name().equals(projection.getStudyPackStatus()),
                projection.getSharedAt()
        );
    }

    private boolean isStudyPackReady(UUID noteId) {
        return studyPackRepository.findByNoteId(noteId)
                .map(pack -> pack.getStatus() == StudyPackStatus.DONE)
                .orElse(false);
    }

    private CreatedAtIdCursorUtils.CursorToken decodeCursor(String cursor) {
        try {
            return CreatedAtIdCursorUtils.decode(cursor);
        } catch (RuntimeException exception) {
            throw new InvalidLibraryQueryException(CURSOR_PARAMETER);
        }
    }

    private long daysSince(OffsetDateTime sharedAt) {
        return Math.max(0, ChronoUnit.DAYS.between(sharedAt, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(SharedNoteNotFoundException::new);
    }

    private String resolveDisplayName(UserEntity user) {
        return resolveDisplayName(user.getDisplayName(), user.getFirstName(), user.getLastName(), user.getEmail());
    }

    private String resolveDisplayName(String displayName, String firstName, String lastName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        String fullName = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).trim();
        return fullName.isBlank() ? email : fullName;
    }
}
