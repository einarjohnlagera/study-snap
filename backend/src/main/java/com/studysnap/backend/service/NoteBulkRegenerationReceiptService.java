package com.studysnap.backend.service;

import com.studysnap.backend.dto.NoteBulkRegenerationItemReceiptResponse;
import com.studysnap.backend.dto.NoteBulkRegenerationReceiptResponse;
import com.studysnap.backend.entity.NoteBulkRegenerationItemEntity;
import com.studysnap.backend.entity.NoteBulkRegenerationItemState;
import com.studysnap.backend.exception.NoteBulkRegenerationBatchNotFoundException;
import com.studysnap.backend.repository.NoteBulkRegenerationItemRepository;
import com.studysnap.backend.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lifetime of the per-item bulk regeneration record.
 *
 * <p>⚠️ MIRRORS {@code BulkGenerationResultService.deleteExpiredReceipts} — same 24 h TTL, same hourly
 * :45 job. The record is a receipt, not audit history, and its TTL is deliberately not extended.
 *
 * <p>⚠️ UNLIKE that receipt, reading it is NOT consume-once. A regeneration batch runs far past the
 * five minutes the bulk-generation client poller tolerates, so the curator may navigate away and come
 * back; a read that deleted the row would destroy the very thing they came back for.
 */
@Service
@RequiredArgsConstructor
public class NoteBulkRegenerationReceiptService {
    private static final int RECEIPT_TTL_HOURS = 24;
    /**
     * How long a batch may go untouched while still claiming unresolved items before the receipt
     * reports it indeterminate. Generously above the per-item ceiling (two LLM calls plus the
     * throttle) so a slow-but-live batch is never mislabelled.
     */
    private static final int STALE_AFTER_MINUTES = 30;

    private final NoteBulkRegenerationItemRepository repository;
    private final NoteRepository noteRepository;

    /**
     * ⚠️ OWNER-SCOPED AT THE QUERY, not filtered afterwards, so a batch id belonging to someone else
     * is indistinguishable from one that never existed. Titles are resolved through an owner-scoped
     * lookup for the same reason.
     */
    @Transactional(readOnly = true)
    public NoteBulkRegenerationReceiptResponse getReceipt(UUID batchId, UUID ownerUserId) {
        List<NoteBulkRegenerationItemEntity> rows =
                repository.findByBatchIdAndOwnerUserIdOrderByBatchCreatedAtAsc(batchId, ownerUserId);
        if (rows.isEmpty()) {
            throw new NoteBulkRegenerationBatchNotFoundException();
        }

        Map<UUID, String> titlesByNoteId = noteRepository
                .findByOwnerUserIdAndIdIn(ownerUserId, rows.stream().map(NoteBulkRegenerationItemEntity::getNoteId).toList())
                .stream()
                .collect(Collectors.toMap(note -> note.getId(), note -> note.getTitle(), (first, second) -> first));

        Map<NoteBulkRegenerationItemState, Integer> counts = new EnumMap<>(NoteBulkRegenerationItemState.class);
        for (NoteBulkRegenerationItemState state : NoteBulkRegenerationItemState.values()) {
            counts.put(state, 0);
        }
        OffsetDateTime lastTouchedAt = null;
        for (NoteBulkRegenerationItemEntity row : rows) {
            counts.merge(row.getState(), 1, Integer::sum);
            if (lastTouchedAt == null || row.getUpdatedAt().isAfter(lastTouchedAt)) {
                lastTouchedAt = row.getUpdatedAt();
            }
        }

        int pending = counts.get(NoteBulkRegenerationItemState.PENDING)
                + counts.get(NoteBulkRegenerationItemState.RUNNING);
        // ⚠️ DERIVED, never a stored terminal flag -- a driver killed mid-batch writes no end marker.
        boolean finished = pending == 0;
        boolean stale = !finished
                && lastTouchedAt != null
                && lastTouchedAt.isBefore(OffsetDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(STALE_AFTER_MINUTES));

        // ⚠️ FAILED only. A REGENERATED item must never be retried -- that would spend quota and
        // replace good content -- and a BLOCKED item stays blocked until its condition changes, so the
        // curator fixes the cause and re-selects deliberately.
        List<UUID> retryableNoteIds = rows.stream()
                .filter(row -> row.getState() == NoteBulkRegenerationItemState.FAILED)
                .map(NoteBulkRegenerationItemEntity::getNoteId)
                .toList();

        List<NoteBulkRegenerationItemReceiptResponse> items = rows.stream()
                .map(row -> new NoteBulkRegenerationItemReceiptResponse(
                        row.getNoteId(),
                        titlesByNoteId.get(row.getNoteId()),
                        row.getState().name(),
                        row.getReasonCode(),
                        row.getReason(),
                        Boolean.TRUE.equals(row.getShareLinkDeactivated())
                ))
                .toList();

        return new NoteBulkRegenerationReceiptResponse(
                batchId,
                rows.getFirst().getScope().name(),
                rows.size(),
                counts.get(NoteBulkRegenerationItemState.REGENERATED),
                counts.get(NoteBulkRegenerationItemState.BLOCKED),
                counts.get(NoteBulkRegenerationItemState.FAILED),
                counts.get(NoteBulkRegenerationItemState.NOT_RUN),
                pending,
                finished,
                stale,
                retryableNoteIds,
                items
        );
    }

    /**
     * ⚠️ Expires on the BATCH's clock, so every item of a batch disappears together. Sweeping on each
     * row's own {@code updated_at} would delete a long batch's early items while its late ones
     * survived, leaving a receipt with holes in it.
     */
    @Transactional
    public long deleteExpiredItems(OffsetDateTime now) {
        return repository.deleteByBatchCreatedAtBefore(now.minusHours(RECEIPT_TTL_HOURS));
    }
}
