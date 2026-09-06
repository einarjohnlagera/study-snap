package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteRegenerationPreflightItemResponse;
import com.studysnap.backend.dto.NoteRegenerationPreflightRequest;
import com.studysnap.backend.dto.NoteRegenerationPreflightResponse;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteRegenerationScope;
import com.studysnap.backend.exception.InvalidBulkRegenerationRequestException;
import com.studysnap.backend.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * B2 — what the curator is told BEFORE committing a bulk regeneration.
 *
 * <p>⚠️ THIS IS DISCLOSURE; THE DRIVER IS ENFORCEMENT. Both read
 * {@link NoteRegenerationReadinessService} and {@link NoteRegenerationConsequenceService}, so the two
 * cannot describe the same Note differently. Reimplementing any of these checks here — or on the
 * client — would drift the moment either side changed.
 *
 * <p>⚠️ THE PREFLIGHT VERDICT IS A SNAPSHOT AND IS NOT AUTHORITATIVE. The driver re-runs the same
 * guards at each item's start; this endpoint exists so the curator can pre-empt a rejection, not so the
 * driver can trust a stale answer.
 *
 * <p>⚠️ Deterministic states only. There is no "review recommended", no metadata-quality score and no
 * classifier — a Note with a NULL Domain Context and one joined program is fully generation-ready, so
 * flagging it would mean judging metadata quality.
 */
@Service
@RequiredArgsConstructor
public class NoteRegenerationPreflightService {
    private static final String EMPTY_SELECTION_MESSAGE = "Select at least one note to regenerate.";

    private final NoteRepository noteRepository;
    private final NoteRegenerationReadinessService readinessService;
    private final NoteRegenerationConsequenceService consequenceService;
    private final NoteBulkRegenerationService bulkRegenerationService;
    private final MePlanService mePlanService;
    private final OnboardingGuardService onboardingGuardService;
    private final BulkRegenerationAccessGuard accessGuard;

    @Transactional(readOnly = true)
    public NoteRegenerationPreflightResponse preflight(
            NoteRegenerationPreflightRequest request,
            UUID ownerUserId,
            boolean enforceLimits
    ) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        // ⚠️ Same curator gate as the batch itself — the disclosure surface must not be wider than
        // the capability it discloses.
        accessGuard.assertCurator(ownerUserId);
        if (request == null || request.noteIds() == null || request.noteIds().isEmpty()) {
            throw new InvalidBulkRegenerationRequestException(EMPTY_SELECTION_MESSAGE);
        }
        NoteRegenerationScope scope = NoteRegenerationScope.parseOrDefault(request.scope());

        Set<UUID> requestedIds = new LinkedHashSet<>();
        for (UUID noteId : request.noteIds()) {
            if (noteId != null) {
                requestedIds.add(noteId);
            }
        }
        if (requestedIds.isEmpty()) {
            throw new InvalidBulkRegenerationRequestException(EMPTY_SELECTION_MESSAGE);
        }

        List<UUID> orderedIds = List.copyOf(requestedIds);
        Map<UUID, NoteEntity> ownedNotes = new LinkedHashMap<>();
        for (NoteEntity note : noteRepository.findByOwnerUserIdAndIdIn(ownerUserId, orderedIds)) {
            ownedNotes.put(note.getId(), note);
        }

        List<NoteRegenerationPreflightItemResponse> items = new ArrayList<>();
        List<UUID> readyIds = new ArrayList<>();
        int blocked = 0;
        int notEligible = 0;
        for (UUID noteId : orderedIds) {
            NoteEntity note = ownedNotes.get(noteId);
            NoteRegenerationReadinessService.Verdict verdict = note == null
                    ? readinessService.evaluate(noteId, ownerUserId, scope)
                    : readinessService.evaluate(note, ownerUserId, scope);
            items.add(new NoteRegenerationPreflightItemResponse(
                    noteId,
                    note == null ? null : note.getTitle(),
                    verdict.readiness().name(),
                    verdict.reasonCode(),
                    verdict.reason()
            ));
            switch (verdict.readiness()) {
                case READY -> readyIds.add(noteId);
                case BLOCKED -> blocked++;
                default -> notEligible++;
            }
        }

        // ⚠️ Consequence counts are over the READY set only. A blocked note is never regenerated, so
        // counting its public visibility or its live share link would overstate what the curator is
        // about to do — and overstating a destructive consequence is not the safe direction, it is a
        // different lie.
        List<NoteEntity> readyNotes = readyIds.stream().map(ownedNotes::get).toList();
        int publicNotesAffected = consequenceService.countPublicNotes(readyNotes);
        int sharedQuizzesToDeactivate =
                consequenceService.countSharedQuizzesToDeactivate(ownerUserId, readyIds, scope);

        int perItemNoteUnits = readinessService.noteGenerationUnitsPerItem(scope);
        int noteUnitsRequired = readyIds.size() * perItemNoteUnits;
        // Every scope spends exactly one Study Pack unit per dispatched item.
        int studyPackUnitsRequired = readyIds.size();
        // ⚠️ An ADMIN batch bypasses quota entirely, so reporting a "remaining" number for it would be
        // meaningless. Zero required is the honest disclosure: nothing is metered.
        int noteUnitsRemaining = enforceLimits ? mePlanService.getNoteGenerationsRemaining(ownerUserId) : 0;
        int studyPackUnitsRemaining =
                enforceLimits ? mePlanService.getStudyPackGenerationsRemaining(ownerUserId) : 0;

        boolean quotaExceeded = enforceLimits && noteUnitsRequired > noteUnitsRemaining;
        int itemsToRemove = quotaExceeded ? noteUnitsRequired - noteUnitsRemaining : 0;

        return new NoteRegenerationPreflightResponse(
                scope.name(),
                orderedIds.size(),
                readyIds.size(),
                blocked,
                notEligible,
                publicNotesAffected,
                sharedQuizzesToDeactivate,
                enforceLimits ? noteUnitsRequired : 0,
                noteUnitsRemaining,
                enforceLimits ? studyPackUnitsRequired : 0,
                studyPackUnitsRemaining,
                quotaExceeded,
                itemsToRemove,
                bulkRegenerationService.getMaxNotes(),
                List.copyOf(items)
        );
    }
}
