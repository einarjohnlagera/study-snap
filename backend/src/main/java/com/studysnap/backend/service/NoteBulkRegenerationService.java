package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerationFailureReason;
import com.studysnap.backend.dto.BulkRegenerateNotesRequest;
import com.studysnap.backend.dto.BulkRegenerateNotesResponse;
import com.studysnap.backend.entity.NoteBulkRegenerationItemEntity;
import com.studysnap.backend.entity.NoteBulkRegenerationItemState;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteRegenerationScope;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.exception.BulkNoteRegenerationQuotaExceededException;
import com.studysnap.backend.exception.InvalidBulkRegenerationRequestException;
import com.studysnap.backend.exception.MonthlyNoteGenerationLimitReachedException;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.NoteBulkRegenerationBatchNotFoundException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.repository.NoteBulkRegenerationItemRepository;
import com.studysnap.backend.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drives a curator's bulk regeneration batch: sequential, continue-on-failure, and truthful about
 * every item.
 *
 * <p>⚠️ THIS IS AN ORCHESTRATOR, NOT A SECOND GENERATION PATH. Each item calls the single-Note
 * primitive that already exists, so the atomic commit, both meters, the share-link deactivation and
 * every post-commit side effect are the ones v0.118.0 shipped and verified.
 *
 * <p>⚠️ IT DELIBERATELY DOES NOT REUSE {@code NoteBulkGenerationService.processItem}, for three reasons
 * that are all live in that code today:
 * <ol>
 *   <li>it always passes a non-null {@code preservedSubject}, which routes into
 *       {@code applyBulkGeneratedMetadataToNote} and UNCONDITIONALLY overwrites the note's title and
 *       tags — on a curator-authored canonical Note that is the single most destructive thing this
 *       feature could do, so this driver passes {@code null} and the destructive branch stays
 *       unreachable;</li>
 *   <li>it resolves ONE generation context for the whole batch and passes it as an override, which
 *       makes the primitive skip per-note resolution — structurally wrong here, where each Note must
 *       resolve its own Subject, Domain Context, Depth and program, so this driver passes
 *       {@code null} there too;</li>
 *   <li>it swallows a failed generation start and still reports the item created.</li>
 * </ol>
 *
 * <p>⚠️ AND IT DOES NOT REPRODUCE THAT SERVICE'S OUTER-CATCH DEFECT.
 * {@code NoteBulkGenerationService.processBatch} clears its partial lists and marks EVERY topic failed
 * whenever anything escapes the loop, while {@code createdCount} keeps its partial value — so an
 * interrupted batch reports completed items as failed, and the curator's only remedy is to regenerate
 * them again, spending quota and replacing good content. Here each item's row is written AS IT
 * RESOLVES and nothing ever rewrites a resolved row. There is deliberately no terminal {@code finally}
 * that touches item state; adding one would recreate the defect exactly.
 *
 * <p>⚠️ NOT {@code @Transactional}, and that is load-bearing rather than an omission. The driver waits
 * on each item by re-reading {@code notes.status}; inside a transaction JPA's identity map would keep
 * handing back the note as it was when first loaded, so the wait would spin to its timeout and every
 * item would look hung. It also must not hold a JDBC connection while an item's two LLM calls run.
 *
 * <p>⚠️ NOTHING SWEEPS A LOST BATCH, BY DECISION. A driver thread killed mid-item never reaches the end
 * of its loop, so that item's row stays {@code RUNNING} and any untouched items stay {@code PENDING}.
 * Those rows expire under the same 24 h TTL as the existing bulk receipt. The asymmetry is worth
 * stating: {@code GenerationRecoveryService} sweeps a note stuck in {@code GENERATING} for more than
 * 120 minutes, so the NOTE self-heals while its batch row does not, and a reader must render a
 * {@code RUNNING} row older than the TTL as indeterminate rather than as in-flight. A partial sweeper
 * would have to guess whether an unreported item completed, and guessing "completed" is precisely the
 * lie this table exists to prevent.
 *
 * <p>⚠️ NO CANCELLATION. Already-dispatched LLM work cannot be stopped, so a control implying it can
 * would be a lie.
 */
@Service
public class NoteBulkRegenerationService {
    private static final Logger log = LoggerFactory.getLogger(NoteBulkRegenerationService.class);
    private static final int MIN_MAX_NOTES = 1;
    private static final int MIN_THROTTLE_DELAY_MS = 0;
    private static final int MAX_THROTTLE_DELAY_MS = 5_000;
    private static final int MIN_POLL_INTERVAL_MS = 10;
    private static final int MAX_POLL_INTERVAL_MS = 5_000;
    private static final long MIN_ITEM_TIMEOUT_MS = 1_000L;
    private static final String EMPTY_BATCH_MESSAGE = "Select at least one note to regenerate.";
    private static final String MAX_NOTES_MESSAGE_TEMPLATE = "You can regenerate up to %d notes at once.";
    private static final String SCOPE_METADATA_KEY = "scope";
    private static final String REQUESTED_COUNT_METADATA_KEY = "requestedCount";
    private static final String METERED_METADATA_KEY = "metered";
    private static final String NOTHING_TO_RETRY_MESSAGE =
            "Nothing in that batch failed, so there is nothing to retry.";
    private static final String QUOTA_BLOCKED_CODE = "NOTE_GENERATION_LIMIT_REACHED";
    /** Thrown as a BARE AppException by StudyPackService.assertMonthlyStudyPackQuotaAvailable. */
    private static final String MONTHLY_STUDY_PACK_LIMIT_CODE = "MONTHLY_STUDY_PACK_LIMIT_REACHED";
    private static final String QUOTA_BLOCKED_MESSAGE =
            "You have reached your note generation limit for this billing cycle.";
    private static final String GENERATION_FAILED_CODE = "NOTE_REGENERATION_FAILED";
    private static final String GENERATION_FAILED_MESSAGE =
            "Regeneration did not complete for this note. Nothing was changed and nothing was charged.";

    private final NoteRepository noteRepository;
    private final NoteBulkRegenerationItemRepository itemRepository;
    private final NoteRegenerationReadinessService readinessService;
    private final NoteRegenerationConsequenceService consequenceService;
    private final StudyPackService studyPackService;
    private final MePlanService mePlanService;
    private final OnboardingGuardService onboardingGuardService;
    private final BulkGenerationFailureReasonNormalizer failureReasonNormalizer;
    private final NoteBulkRegenerationTaskDispatcher taskDispatcher;
    private final AnalyticsService analyticsService;
    private final BulkRegenerationAccessGuard accessGuard;
    private final int maxNotes;
    private final int throttleDelayMs;
    private final int pollIntervalMs;
    private final long itemTimeoutMs;

    public NoteBulkRegenerationService(
            NoteRepository noteRepository,
            NoteBulkRegenerationItemRepository itemRepository,
            NoteRegenerationReadinessService readinessService,
            NoteRegenerationConsequenceService consequenceService,
            StudyPackService studyPackService,
            MePlanService mePlanService,
            OnboardingGuardService onboardingGuardService,
            BulkGenerationFailureReasonNormalizer failureReasonNormalizer,
            NoteBulkRegenerationTaskDispatcher taskDispatcher,
            BulkRegenerationAccessGuard accessGuard,
            AnalyticsService analyticsService,
            // ⚠️ ITS OWN CONFIG KEY, deliberately not note.bulk-generation.max-topics, so tuning the
            // regeneration cap never moves the bulk GENERATION cap. Both default to 50.
            @Value("${note.bulk-regeneration.max-notes:50}") int maxNotes,
            // Shared pacing value, same default as bulk generation. Not a "fix" target: it is a
            // deliberate throttle between items, not latency.
            @Value("${note.bulk-regeneration.throttle-delay-ms:500}") int throttleDelayMs,
            @Value("${note.bulk-regeneration.poll-interval-ms:500}") int pollIntervalMs,
            @Value("${note.bulk-regeneration.item-timeout-ms:900000}") long itemTimeoutMs
    ) {
        this.noteRepository = noteRepository;
        this.itemRepository = itemRepository;
        this.readinessService = readinessService;
        this.consequenceService = consequenceService;
        this.studyPackService = studyPackService;
        this.mePlanService = mePlanService;
        this.onboardingGuardService = onboardingGuardService;
        this.failureReasonNormalizer = failureReasonNormalizer;
        this.taskDispatcher = taskDispatcher;
        this.accessGuard = accessGuard;
        this.analyticsService = analyticsService;
        this.maxNotes = Math.clamp(maxNotes, MIN_MAX_NOTES, Integer.MAX_VALUE);
        this.throttleDelayMs = Math.clamp(throttleDelayMs, MIN_THROTTLE_DELAY_MS, MAX_THROTTLE_DELAY_MS);
        this.pollIntervalMs = Math.clamp(pollIntervalMs, MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS);
        this.itemTimeoutMs = Math.clamp(itemTimeoutMs, MIN_ITEM_TIMEOUT_MS, Long.MAX_VALUE);
    }

    public int getMaxNotes() {
        return maxNotes;
    }

    /**
     * ⚠️ The 422 fires HERE, before a single item is dispatched, carrying how many notes to remove.
     * Letting the batch run until quota ran out would leave the curator a half-rebuilt Review Set with
     * no way to tell which half.
     */
    /**
     * Re-runs the items of a previous batch that FAILED, as a NEW batch.
     *
     * <p>⚠️ THE SERVER DERIVES THE SET; THE CLIENT NEVER SENDS IT. Retry spends real units on a paid
     * account, so "only the failed ones" has to be a server guarantee rather than a client convention —
     * a client sending the wrong ids would re-run REGENERATED notes, spending quota and replacing good
     * content with a second generation nobody asked for.
     *
     * <p>⚠️ REGENERATED IS NEVER RETRIED, and neither is BLOCKED: a blocked item stays blocked until
     * its condition changes (quota resets, a Domain Context is set), so re-running it blindly would
     * fail again for the same reason. The curator fixes the cause and re-selects deliberately.
     *
     * <p>⚠️ A NEW BATCH ID IS MINTED, AND THAT IS LOAD-BEARING RATHER THAN COSMETIC.
     * {@code writeItem} is find-then-save against the unique {@code (batch_id, note_id)}, which is safe
     * only while ONE driver owns a batch. Retrying INTO the original batch would give one pair two
     * writers whenever a timed-out RUNNING worker is still alive, and the loser's constraint violation
     * is swallowed. A fresh batch id makes that collision impossible by construction.
     *
     * <p>⚠️ It routes through {@link #queueBatch}, so a retry re-runs the curator gate, the
     * pre-dispatch 422 and every per-item readiness guard. A retry can no more overspend than the
     * original could.
     */
    public BulkRegenerateNotesResponse retryFailedItems(
            UUID batchId,
            UUID ownerUserId,
            boolean enforceLimits
    ) {
        List<NoteBulkRegenerationItemEntity> rows =
                itemRepository.findByBatchIdAndOwnerUserIdOrderByBatchCreatedAtAsc(batchId, ownerUserId);
        if (rows.isEmpty()) {
            // Same 404 contract as the receipt: unknown, someone else's and expired are indistinguishable.
            throw new NoteBulkRegenerationBatchNotFoundException();
        }

        List<UUID> failedNoteIds = rows.stream()
                .filter(row -> row.getState() == NoteBulkRegenerationItemState.FAILED)
                .map(NoteBulkRegenerationItemEntity::getNoteId)
                .toList();
        if (failedNoteIds.isEmpty()) {
            throw new InvalidBulkRegenerationRequestException(NOTHING_TO_RETRY_MESSAGE);
        }

        // The original batch's scope, never a caller-supplied one: retrying a Study-Pack-only batch as
        // combined would replace note content the curator never asked to replace.
        NoteRegenerationScope scope = rows.getFirst().getScope();
        return queueBatch(
                new BulkRegenerateNotesRequest(failedNoteIds, scope.name()), ownerUserId, enforceLimits);
    }

    public BulkRegenerateNotesResponse queueBatch(
            BulkRegenerateNotesRequest request,
            UUID ownerUserId,
            boolean enforceLimits
    ) {
        onboardingGuardService.assertProfileComplete(ownerUserId);
        // ⚠️ BEFORE any request parsing or note lookup, so a non-curator learns nothing about the
        // selection they submitted.
        accessGuard.assertCurator(ownerUserId);
        if (request == null) {
            throw new InvalidBulkRegenerationRequestException(EMPTY_BATCH_MESSAGE);
        }
        NoteRegenerationScope scope = NoteRegenerationScope.parseOrDefault(request.scope());
        List<UUID> noteIds = normalizeNoteIds(request.noteIds());

        rejectIfNoteGenerationQuotaExceeded(noteIds, ownerUserId, scope, enforceLimits);

        UUID batchId = UUID.randomUUID();
        OffsetDateTime batchCreatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        for (UUID noteId : noteIds) {
            writeItem(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                    NoteBulkRegenerationItemState.PENDING, null, null, false);
        }
        // ⚠️ batchCreatedAt is CARRIED into the driver, never re-read. Re-reading it from the first
        // PENDING row would fall back to `now()` whenever that read came back empty (a swallowed
        // write above, say), and the driver would then stamp its rows with a different batch clock
        // than the PENDING ones -- splitting one batch across two TTLs and producing exactly the
        // half-swept receipt the batch clock exists to prevent.
        // ⚠️ THE ONLY DURABLE RECORD THAT A BATCH EVER RAN. note_bulk_regeneration_item is a RECEIPT with
        // a 24 h TTL, so it cannot answer a dated question, and nothing else distinguishes a
        // bulk-regenerated note from a single-note one -- both mutate notes.updated_at in place. Without
        // this event the checkpoints on curator adoption and on TEACHER allowance adequacy would be
        // decorative, which the signoff gate forbids.
        //
        // ⚠️ `metered` IS THE FIELD THE MONEY DECISION IS READ THROUGH: owner decision 1 admitted TEACHER
        // curators against the plan's recommendation, and this is what makes "is their allowance enough
        // for a real batch?" answerable rather than a guess.
        //
        // ⚠️ Wrapped so analytics can NEVER fail the batch -- the v0.101.0 rule. A dropped event costs a
        // measurement; a thrown one would cost the curator their regeneration.
        try {
            analyticsService.trackEvent(
                    ownerUserId,
                    AnalyticsEventType.BULK_REGENERATION_STARTED,
                    batchId,
                    Map.of(
                            SCOPE_METADATA_KEY, scope.name(),
                            REQUESTED_COUNT_METADATA_KEY, noteIds.size(),
                            METERED_METADATA_KEY, enforceLimits
                    )
            );
        } catch (RuntimeException analyticsFailure) {
            log.warn("action=bulk_regenerate_batch outcome=analytics_failed batchId={}", batchId,
                    analyticsFailure);
        }
        taskDispatcher.execute(
                () -> processBatch(batchId, noteIds, ownerUserId, scope, enforceLimits, batchCreatedAt));
        return new BulkRegenerateNotesResponse(batchId, scope.name(), noteIds.size());
    }

    /**
     * How many note-generation units this selection would really spend, counting only the items that
     * would actually be DISPATCHED.
     *
     * <p>⚠️ THE DENOMINATOR IS THE DISPATCHABLE SET, NOT THE SELECTION. A blocked or not-eligible Note
     * never reaches generation and spends nothing, so charging the raw selection against remaining
     * units would refuse a batch that fits.
     *
     * <p>⚠️ AND IT IS SCOPE-AWARE. {@code startAsyncGenerationFromNote} asserts and charges only the
     * Study Pack meter, so a Study-Pack-only batch costs ZERO note-generation units and this hard
     * rejection must never fire for it.
     */
    public int countNoteGenerationUnitsRequired(
            List<UUID> noteIds,
            UUID ownerUserId,
            NoteRegenerationScope scope
    ) {
        int perItem = readinessService.noteGenerationUnitsPerItem(scope);
        if (perItem == 0) {
            return 0;
        }
        return (int) noteIds.stream()
                .filter(noteId -> readinessService.evaluate(noteId, ownerUserId, scope).isReady())
                .count() * perItem;
    }

    private void rejectIfNoteGenerationQuotaExceeded(
            List<UUID> noteIds,
            UUID ownerUserId,
            NoteRegenerationScope scope,
            boolean enforceLimits
    ) {
        // ⚠️ Mirrors NoteBulkGenerationService exactly: the ADMIN bypass is `enforceLimits == false`
        // and is NOT widened. A TEACHER curator is metered normally under block-and-reduce.
        if (!enforceLimits) {
            return;
        }
        int required = countNoteGenerationUnitsRequired(noteIds, ownerUserId, scope);
        if (required == 0) {
            return;
        }
        int remaining = mePlanService.getNoteGenerationsRemaining(ownerUserId);
        if (required > remaining) {
            throw new BulkNoteRegenerationQuotaExceededException(remaining, required);
        }
    }

    /**
     * Whether this item can no longer be paid for. Only the note-generation meter is consulted: a
     * Study-Pack-only item never spends a topic note unit, so an exhausted note-generation allowance
     * must not block it.
     *
     * <p>⚠️ An ADMIN batch ({@code enforceLimits == false}) is never quota-blocked, matching the bypass
     * bulk generation already applies. The bypass is NOT widened by this method.
     */
    private boolean isNoteGenerationQuotaExhausted(
            UUID ownerUserId,
            NoteRegenerationScope scope,
            boolean enforceLimits
    ) {
        if (!enforceLimits || scope != NoteRegenerationScope.NOTE_AND_STUDY_PACK) {
            return false;
        }
        return mePlanService.getNoteGenerationsRemaining(ownerUserId) <= 0;
    }

    /**
     * Both monthly meters a regeneration can exhaust, recognised by CODE because they do not share a
     * type. The AI rate limit is deliberately NOT here: it resets within the minute, so retrying really
     * is the remedy and FAILED/retryable is the honest classification for it.
     */
    private static boolean isQuotaExhaustion(AppException exception) {
        String code = exception.getCode();
        return QUOTA_BLOCKED_CODE.equals(code) || MONTHLY_STUDY_PACK_LIMIT_CODE.equals(code);
    }

    private NoteBulkRegenerationItemState recordFailure(
            UUID batchId,
            UUID ownerUserId,
            UUID noteId,
            NoteRegenerationScope scope,
            OffsetDateTime batchCreatedAt,
            RuntimeException exception
    ) {
        BulkGenerationFailureReason reason = normalizeFailureReason(noteId, exception);
        log.warn(
                "action=bulk_regenerate_note outcome=failed_before_dispatch batchId={} noteId={}"
                        + " ownerUserId={}",
                batchId, noteId, ownerUserId, exception
        );
        return record(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                NoteBulkRegenerationItemState.FAILED, reason.code(), reason.reason(), false);
    }

    private List<UUID> normalizeNoteIds(List<UUID> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new InvalidBulkRegenerationRequestException(EMPTY_BATCH_MESSAGE);
        }
        // Duplicates are collapsed rather than rejected: a selection built from two overlapping filters
        // legitimately contains one note twice, and regenerating it twice would spend two units and
        // replace good content with a second generation nobody asked for.
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID noteId : requested) {
            if (noteId != null) {
                unique.add(noteId);
            }
        }
        if (unique.isEmpty()) {
            throw new InvalidBulkRegenerationRequestException(EMPTY_BATCH_MESSAGE);
        }
        if (unique.size() > maxNotes) {
            throw new InvalidBulkRegenerationRequestException(MAX_NOTES_MESSAGE_TEMPLATE.formatted(maxNotes));
        }
        return List.copyOf(unique);
    }

    void processBatch(
            UUID batchId,
            List<UUID> noteIds,
            UUID ownerUserId,
            NoteRegenerationScope scope,
            boolean enforceLimits,
            OffsetDateTime batchCreatedAt
    ) {
        int regenerated = 0;
        int blocked = 0;
        int failed = 0;
        for (int index = 0; index < noteIds.size(); index++) {
            UUID noteId = noteIds.get(index);
            try {
                NoteBulkRegenerationItemState state =
                        processItem(batchId, noteId, ownerUserId, scope, enforceLimits, batchCreatedAt);
                switch (state) {
                    case REGENERATED -> regenerated++;
                    case BLOCKED -> blocked++;
                    case FAILED -> failed++;
                    default -> { /* NOT_RUN and a timed-out RUNNING are read from the receipt. */ }
                }
                throttleBeforeNext(index, noteIds.size());
            } catch (BatchInterruptedException interrupted) {
                // ⚠️ THE ONE INTERRUPTION PATH, AND THE ONE PLACE THE OUTER-CATCH DEFECT COULD RETURN.
                // The batch STOPS and NOTHING already recorded is touched: a deploy mid-batch (main
                // auto-deploys on merge) leaves every resolved item exactly as it resolved. Rewriting
                // them here — which is what NoteBulkGenerationService's outer catch does — would tell
                // the curator to regenerate notes that already succeeded, spending quota and replacing
                // good content.
                // ⚠️ The waiting sleep and the throttling sleep funnel through the SAME exception on
                // purpose. They were two branches once, and a mutation planted on one of them survived
                // because the guard only exercised the other.
                Thread.currentThread().interrupt();
                log.warn(
                        "action=bulk_regenerate_batch outcome=interrupted batchId={} resolvedItems={}"
                                + " remainingItems={} ownerUserId={}",
                        batchId, index, noteIds.size() - index, ownerUserId
                );
                return;
            }
        }
        log.info(
                "action=bulk_regenerate_batch outcome=completed batchId={} requested={} regenerated={}"
                        + " blocked={} failed={} ownerUserId={}",
                batchId, noteIds.size(), regenerated, blocked, failed, ownerUserId
        );
    }

    /**
     * ⚠️ THE PER-NOTE GUARDS RE-RUN HERE, AT THE MOMENT THE ITEM STARTS. The preflight snapshot is
     * never trusted: between preflight and this Note's turn a single-Note regeneration can have put it
     * into {@code GENERATING}, an edit can have cleared its Domain Context, or it can have been
     * deleted. A now-blocked Note takes {@code BLOCKED} with its reason — never silently skipped, and
     * never counted as {@code REGENERATED}.
     */
    private NoteBulkRegenerationItemState processItem(
            UUID batchId,
            UUID noteId,
            UUID ownerUserId,
            NoteRegenerationScope scope,
            boolean enforceLimits,
            OffsetDateTime batchCreatedAt
    ) {
        writeItem(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                NoteBulkRegenerationItemState.RUNNING, null, null, false);

        NoteRegenerationReadinessService.Verdict verdict =
                readinessService.evaluate(noteId, ownerUserId, scope);
        switch (verdict.readiness()) {
            case NOT_ELIGIBLE -> {
                return record(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                        NoteBulkRegenerationItemState.NOT_RUN, verdict.reasonCode(), verdict.reason(), false);
            }
            case BLOCKED -> {
                return record(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                        NoteBulkRegenerationItemState.BLOCKED, verdict.reasonCode(), verdict.reason(), false);
            }
            default -> { /* READY — fall through to dispatch. */ }
        }

        // Captured BEFORE dispatch: the deactivation happens inside the primitive's commit transaction
        // and is not separately observable from out here.
        boolean hadLiveShareLink = consequenceService.hasLiveShareLink(ownerUserId, noteId, scope);

        // ⚠️ NO PER-ITEM QUOTA PRE-CHECK HERE, AND THAT IS DELIBERATE. One was written and removed: the
        // primitive's own synchronous assertQuotaAvailable already throws
        // MonthlyNoteGenerationLimitReachedException on the CALLING thread, which the catch below
        // already records as BLOCKED with the same code -- so a pre-check changed nothing observable
        // while costing an extra quota read per item. Verified by mutation: with the pre-check
        // disabled, quotaExhaustedDuringABatchIsReportedAsQuotaRatherThanAsABareFailure still passed.
        try {
            dispatchItem(noteId, ownerUserId, scope, enforceLimits);
        } catch (NoteNotFoundException notFound) {
            return record(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                    NoteBulkRegenerationItemState.NOT_RUN,
                    notFound.getCode(), notFound.getMessage(), false);
        } catch (AppException appException) {
            // ⚠️ CLASSIFIED BY CODE, NOT BY EXCEPTION TYPE, AND THAT IS THE WHOLE POINT. The two meters a
            // combined regeneration spends throw DIFFERENT SHAPES for the same condition: the
            // note-generation meter throws the typed MonthlyNoteGenerationLimitReachedException, while
            // assertMonthlyStudyPackQuotaAvailable throws a BARE AppException carrying
            // MONTHLY_STUDY_PACK_LIMIT_REACHED. A catch on the typed exception alone therefore caught one
            // meter and let its sibling fall through to the generic branch below as FAILED — which the
            // receipt then offers as RETRYABLE, inviting the curator into a retry that cannot succeed
            // until the cycle resets. Found by the v0.119.0 pressure test; the earlier fix swept the diff
            // rather than the surface.
            if (isQuotaExhaustion(appException)) {
                // BLOCKED, not FAILED: a condition the curator resolves by waiting or upgrading, and
                // retry must not re-run it blindly. Mirrors the existing receipt's quotaBlockedTopics.
                return record(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                        NoteBulkRegenerationItemState.BLOCKED,
                        appException.getCode(), appException.getMessage(), false);
            }
            return recordFailure(batchId, ownerUserId, noteId, scope, batchCreatedAt, appException);
        } catch (RuntimeException exception) {
            return recordFailure(batchId, ownerUserId, noteId, scope, batchCreatedAt, exception);
        }

        // ⚠️ THE VERDICT COMES FROM PERSISTED notes.status, NOT FROM "the call did not throw".
        // generateStudyPackFromExistingNoteAsync catches Exception, marks the note FAILED and returns
        // normally, so an exception-based verdict would report every async failure as a success — the
        // exact defect NoteBulkGenerationService.processItem carries today.
        NoteStatus finalStatus = awaitTerminalStatus(noteId, ownerUserId);
        if (finalStatus == null) {
            return record(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                    NoteBulkRegenerationItemState.NOT_RUN,
                    NoteRegenerationReadinessService.NOT_ELIGIBLE_CODE,
                    NoteRegenerationReadinessService.NOT_ELIGIBLE_MESSAGE, false);
        }
        if (finalStatus == NoteStatus.GENERATED) {
            return record(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                    NoteBulkRegenerationItemState.REGENERATED, null, null, hadLiveShareLink);
        }
        if (finalStatus == NoteStatus.FAILED) {
            // ⚠️ THE CATCH ABOVE CANNOT SEE A QUOTA REJECTION THAT ARRIVES ON THE GENERATION THREAD.
            // generateFromTopic asserts quota a SECOND time inside the async worker, and
            // generateStudyPackFromExistingNoteAsync catches Exception and marks the note FAILED, so
            // the exception never reaches this class. Reported in production as a bare "generation
            // failed" with no reason recorded anywhere. If the meter is exhausted now, that is
            // overwhelmingly what happened, and saying so is strictly better than a generic failure
            // whose only remedy looks like "try again" -- which would spend a unit the curator has not
            // got. Still a narrowing, not a proof: see the finding doc for why the exact reason cannot
            // be persisted without a column this release may not add.
            if (isNoteGenerationQuotaExhausted(ownerUserId, scope, enforceLimits)) {
                return record(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                        NoteBulkRegenerationItemState.BLOCKED,
                        QUOTA_BLOCKED_CODE, QUOTA_BLOCKED_MESSAGE, false);
            }
            return record(batchId, ownerUserId, noteId, scope, batchCreatedAt,
                    NoteBulkRegenerationItemState.FAILED, GENERATION_FAILED_CODE, GENERATION_FAILED_MESSAGE,
                    false);
        }
        // Still GENERATING at the timeout. The row stays RUNNING, which is the honest answer: the item
        // may yet succeed on the generation pool, and claiming either outcome would be a guess.
        log.warn(
                "action=bulk_regenerate_note outcome=still_generating_at_timeout batchId={} noteId={}"
                        + " ownerUserId={}",
                batchId, noteId, ownerUserId
        );
        return NoteBulkRegenerationItemState.RUNNING;
    }

    private void dispatchItem(
            UUID noteId,
            UUID ownerUserId,
            NoteRegenerationScope scope,
            boolean enforceLimits
    ) {
        if (scope == NoteRegenerationScope.NOTE_AND_STUDY_PACK) {
            studyPackService.startAsyncNoteAndStudyPackRegeneration(
                    noteId.toString(), ownerUserId, enforceLimits);
            return;
        }
        // ⚠️ BOTH TRAILING NULLS ARE LOAD-BEARING AND MUST STAY NULL.
        // preservedSubject = null keeps applyBulkGeneratedMetadataToNote — which unconditionally
        // overwrites title and tags — unreachable, protecting curator-authored canonical titles.
        // generationContextOverride = null forces the primitive to resolve THIS note's own context
        // rather than one batch-wide context.
        studyPackService.startAsyncGenerationFromNote(
                noteId.toString(), ownerUserId, false, enforceLimits, null, null);
    }

    /**
     * Waits for the dispatched item to leave {@code GENERATING}, re-reading persisted status.
     *
     * <p>⚠️ Reads outside any transaction of this class's own, so each poll sees committed state. The
     * status check runs BEFORE the first sleep, so a synchronous dispatcher (tests) returns
     * immediately rather than paying an interval.
     *
     * @return the terminal status, {@code null} if the note is gone, or {@code GENERATING} on timeout.
     */
    private NoteStatus awaitTerminalStatus(UUID noteId, UUID ownerUserId) {
        long deadline = System.currentTimeMillis() + itemTimeoutMs;
        while (true) {
            NoteStatus status = noteRepository.findByIdAndOwnerUserId(noteId, ownerUserId)
                    .map(NoteEntity::getStatus)
                    .orElse(null);
            if (status != NoteStatus.GENERATING) {
                return status;
            }
            if (System.currentTimeMillis() >= deadline) {
                return NoteStatus.GENERATING;
            }
            sleepOrInterrupt(pollIntervalMs);
        }
    }

    /**
     * ⚠️ PACING, NOT LATENCY. The 500 ms gap between items is shared with bulk generation and is not a
     * thing to "fix".
     */
    private void throttleBeforeNext(int index, int totalItems) {
        if (throttleDelayMs == 0 || index >= totalItems - 1) {
            return;
        }
        sleepOrInterrupt(throttleDelayMs);
    }

    private void sleepOrInterrupt(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            throw new BatchInterruptedException(exception);
        }
    }

    private BulkGenerationFailureReason normalizeFailureReason(UUID noteId, RuntimeException exception) {
        // ⚠️ Keyed by NOTE ID rather than topic string. The existing normalizer keys on the topic, which
        // collides whenever a batch contains two identically-named items; a note id cannot.
        String key = noteId.toString();
        try {
            BulkGenerationFailureReason normalized = failureReasonNormalizer.normalize(key, exception);
            return normalized == null
                    ? BulkGenerationFailureReasonNormalizer.unexpected(key, exception)
                    : normalized;
        } catch (RuntimeException normalizationException) {
            return BulkGenerationFailureReasonNormalizer.unexpected(key, exception);
        }
    }

    private NoteBulkRegenerationItemState record(
            UUID batchId,
            UUID ownerUserId,
            UUID noteId,
            NoteRegenerationScope scope,
            OffsetDateTime batchCreatedAt,
            NoteBulkRegenerationItemState state,
            String reasonCode,
            String reason,
            boolean shareLinkDeactivated
    ) {
        writeItem(batchId, ownerUserId, noteId, scope, batchCreatedAt, state, reasonCode, reason,
                shareLinkDeactivated);
        return state;
    }

    /**
     * ⚠️ Written as the item resolves, each in its own transaction, so a killed driver leaves every
     * already-resolved row committed. A persistence failure here is logged and never propagated: the
     * Note and its Study Pack are already committed, and letting a receipt write abort the batch would
     * be strictly worse than an item that reads as unknown.
     *
     * <p>⚠️ KNOWN LIMITATION FOR WHOEVER BUILDS RETRY (slice B5). This is find-then-save with no lock,
     * against a table carrying {@code uq_note_bulk_regeneration_item_batch_note}. In B1 that is safe by
     * construction — one driver thread owns a batch and each {@code (batch, note)} pair is written only
     * by it — but a retry that re-runs a batch's items while a timed-out {@code RUNNING} item's worker
     * is still alive would give one pair two writers, and the loser's constraint violation would be
     * swallowed here, losing that item's real state. Retry must either mint a NEW batch id or make this
     * write conditional; do not inherit this method's concurrency assumptions unexamined.
     */
    private void writeItem(
            UUID batchId,
            UUID ownerUserId,
            UUID noteId,
            NoteRegenerationScope scope,
            OffsetDateTime batchCreatedAt,
            NoteBulkRegenerationItemState state,
            String reasonCode,
            String reason,
            boolean shareLinkDeactivated
    ) {
        try {
            NoteBulkRegenerationItemEntity entity = itemRepository
                    .findByBatchIdAndNoteId(batchId, noteId)
                    .orElseGet(NoteBulkRegenerationItemEntity::new);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
                entity.setBatchId(batchId);
                entity.setOwnerUserId(ownerUserId);
                entity.setNoteId(noteId);
                entity.setScope(scope);
                entity.setBatchCreatedAt(batchCreatedAt);
            }
            entity.setState(state);
            entity.setReasonCode(reasonCode);
            entity.setReason(reason);
            entity.setShareLinkDeactivated(shareLinkDeactivated);
            entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            itemRepository.save(entity);
        } catch (RuntimeException exception) {
            log.warn(
                    "action=bulk_regenerate_item outcome=failed_to_record batchId={} noteId={} state={}",
                    batchId, noteId, state, exception
            );
        }
    }

    public List<NoteBulkRegenerationItemEntity> listBatchItems(UUID batchId, UUID ownerUserId) {
        return new ArrayList<>(
                itemRepository.findByBatchIdAndOwnerUserIdOrderByBatchCreatedAtAsc(batchId, ownerUserId));
    }

    /** Signals that the driver thread was interrupted, e.g. by a deploy. Never leaves this class. */
    private static final class BatchInterruptedException extends RuntimeException {
        private BatchInterruptedException(InterruptedException cause) {
            super(cause);
        }
    }
}
