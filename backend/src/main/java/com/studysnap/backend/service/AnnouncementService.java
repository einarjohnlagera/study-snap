package com.studysnap.backend.service;

import com.studysnap.backend.dto.AnnouncementPublishResponse;
import com.studysnap.backend.dto.AnnouncementResponse;
import com.studysnap.backend.dto.UpsertAnnouncementRequest;
import com.studysnap.backend.entity.AnnouncementAudience;
import com.studysnap.backend.entity.AnnouncementEntity;
import com.studysnap.backend.entity.AnnouncementStatus;
import com.studysnap.backend.entity.NotificationType;
import com.studysnap.backend.exception.AnnouncementNotEditableException;
import com.studysnap.backend.exception.AnnouncementNotEndableException;
import com.studysnap.backend.exception.AnnouncementNotFoundException;
import com.studysnap.backend.exception.AnnouncementNotPublishableException;
import com.studysnap.backend.exception.InvalidAnnouncementRequestException;
import com.studysnap.backend.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Admin "What's New": a Draft → Published → Ended lifecycle whose only delivery mechanism is the
 * existing {@link NotificationService}.
 *
 * <p>⚠️ ONE INBOX, ONE TABLE, ONE READ. There is no second delivery path and no lazy eligibility
 * evaluation — an announcement fans out into {@code notifications} rows, so the inbox stays a single
 * indexed read.
 *
 * <p>⚠️ TITLE, BODY AND CTA ARE COPIED AT FAN-OUT AND NEVER RE-READ. {@code announcement_id} on a
 * notification row is PROVENANCE AND LIFECYCLE ASSOCIATION, not live content inheritance, so delivered
 * rows stay historically coherent even after the announcement ends.
 *
 * <p>⚠️ NO SCHEDULER. Draft → Published is manual, and expiry is decided on read (see
 * {@code NotificationRepository.findVisibleInbox}) rather than by a sweep, so an ended or expired
 * announcement stops presenting immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {
    /**
     * ⚠️ A "BATCH" HERE IS A CHUNK OF THE RECIPIENT LIST, NOT A BULK INSERT. Idempotency comes from the
     * unique {@code (recipient_user_id, dedup_key)} index, which requires one insert per recipient: a
     * bulk insert would fail the whole chunk on the first duplicate. Chunking exists so a long fan-out
     * reports progress and so one bad row cannot abandon the rest.
     */
    private static final int FAN_OUT_CHUNK_SIZE = 500;

    private static final String AUDIENCE_FIELD = "audience";

    private final AnnouncementRepository announcementRepository;
    private final NotificationService notificationService;
    private final AnnouncementAudienceResolver audienceResolver;

    public List<AnnouncementResponse> list() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(announcement -> toResponse(announcement, now))
                .toList();
    }

    public AnnouncementResponse create(UpsertAnnouncementRequest request, UUID createdByUserId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AnnouncementEntity announcement = new AnnouncementEntity();
        announcement.setId(UUID.randomUUID());
        announcement.setStatus(AnnouncementStatus.DRAFT);
        announcement.setCreatedByUserId(createdByUserId);
        announcement.setCreatedAt(now);
        applyContent(announcement, request, now);
        return toResponse(announcementRepository.saveAndFlush(announcement), now);
    }

    /**
     * ⚠️ EDITS ARE PERMITTED ONLY WHILE {@code DRAFT}, AND AN ATTEMPT ON ANYTHING ELSE IS REFUSED —
     * never silently ignored. Delivered rows carry a copy of the text, so a tolerated edit would leave
     * the definition and the copies disagreeing with no way to tell which is real.
     */
    public AnnouncementResponse update(UUID announcementId, UpsertAnnouncementRequest request) {
        AnnouncementEntity announcement = findOrThrow(announcementId);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
            throw new AnnouncementNotEditableException(announcement.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        applyContent(announcement, request, now);
        return toResponse(announcementRepository.saveAndFlush(announcement), now);
    }

    /**
     * Publishes a draft and fans it out, or RE-RUNS the fan-out for one that is already published.
     *
     * <p>⚠️ RE-PUBLISH IS A RETRY FOR EVERYONE WHO ALREADY HAS IT — AND A FIRST SEND FOR ANYONE WHO HAS
     * JOINED THE AUDIENCE SINCE. This javadoc previously said "a retry, not a second send" flatly, and a
     * {@code v0.130.0} pressure test showed that is only half true: {@link #fanOut(AnnouncementEntity)}
     * re-resolves the audience AT CALL TIME, so a user who signed up, changed profile type or upgraded
     * plan between the two publishes is in the second resolution and receives the announcement. Existing
     * recipients are protected by the unique index and get nothing new; the effect is a TOP-UP.
     *
     * <p>That is a defensible behaviour — an admin pressing Publish again generally does want current
     * readers reached — but it is a real difference and must not be restated as pure retry.
     *
     * <p>⚠️ THE RETRY HALF IS DELIBERATE. Fan-out is one committed
     * insert per recipient with no ambient transaction, so a few thousand recipients is a few thousand
     * round trips inside one admin HTTP request — long enough to outrun a gateway timeout. The status
     * transition commits BEFORE fan-out starts, and the unique index makes a re-run insert zero
     * duplicates, so a timed-out publish is recoverable by pressing Publish again.
     *
     * <p>⚠️ {@code published_at} IS STAMPED ONCE and never re-stamped on a retry. ⚠️ An ENDED
     * announcement is refused: to say it again, publish a replacement.
     *
     * <p>⚠️ NOT {@code @Transactional}, AND THAT IS LOAD-BEARING. {@link NotificationService#deliver}
     * relies on catching {@code DataIntegrityViolationException} from the unique index; under an
     * ambient transaction that violation would mark the whole transaction rollback-only and take the
     * entire fan-out down with it.
     */
    public AnnouncementPublishResponse publish(UUID announcementId) {
        AnnouncementEntity announcement = findOrThrow(announcementId);
        if (announcement.getStatus() == AnnouncementStatus.ENDED) {
            throw new AnnouncementNotPublishableException();
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (announcement.getStatus() == AnnouncementStatus.DRAFT) {
            announcement.setStatus(AnnouncementStatus.PUBLISHED);
            announcement.setPublishedAt(now);
            announcement.setUpdatedAt(now);
            announcement = announcementRepository.saveAndFlush(announcement);
        }

        AnnouncementFanOutResult result = fanOut(announcement);
        return new AnnouncementPublishResponse(
                toResponse(announcement, now),
                result.recipientCount(),
                result.delivered(),
                result.skipped()
        );
    }

    /**
     * ⚠️ ENDING TAKES EFFECT IMMEDIATELY BECAUSE THE INBOX DECIDES ON READ. Delivered rows are NOT
     * deleted and NOT rolled back — a user who saw it cannot un-see it — they simply stop presenting.
     */
    public AnnouncementResponse end(UUID announcementId) {
        AnnouncementEntity announcement = findOrThrow(announcementId);
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw new AnnouncementNotEndableException();
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        announcement.setStatus(AnnouncementStatus.ENDED);
        announcement.setUpdatedAt(now);
        return toResponse(announcementRepository.saveAndFlush(announcement), now);
    }

    public AnnouncementFanOutResult fanOut(AnnouncementEntity announcement) {
        return fanOut(announcement, audienceResolver.resolve(announcement));
    }

    /**
     * ⚠️ IDEMPOTENCY IS THE UNIQUE INDEX, NOT A PRE-CHECK. There is deliberately no {@code existsBy}
     * before the insert: two concurrent fan-outs could both pass one and still duplicate. Every
     * recipient's row carries {@code dedup_key = "ANNOUNCEMENT:<announcementId>"}, built by the single
     * helper {@link NotificationService#dedupKey}, so re-running this inserts zero new rows.
     *
     * <p>⚠️ RESILIENT TO PARTIAL FAILURE. One recipient's failed insert is counted and stepped over;
     * it never abandons the rest, and a retry picks up exactly the recipients that were missed.
     *
     * <p>⚠️ An audience resolving to zero users is a legitimate outcome, not an error.
     */
    public AnnouncementFanOutResult fanOut(AnnouncementEntity announcement, List<UUID> recipientUserIds) {
        int delivered = 0;
        int skipped = 0;
        for (int chunkStart = 0; chunkStart < recipientUserIds.size(); chunkStart += FAN_OUT_CHUNK_SIZE) {
            int chunkEnd = Math.min(chunkStart + FAN_OUT_CHUNK_SIZE, recipientUserIds.size());
            for (UUID recipientUserId : recipientUserIds.subList(chunkStart, chunkEnd)) {
                if (deliverOne(announcement, recipientUserId)) {
                    delivered++;
                } else {
                    skipped++;
                }
            }
            log.info(
                    "announcement.fan_out.chunk announcementId={} progress={}/{} delivered={} skipped={}",
                    announcement.getId(), chunkEnd, recipientUserIds.size(), delivered, skipped
            );
        }
        log.info(
                "announcement.fan_out announcementId={} recipients={} delivered={} skipped={}",
                announcement.getId(), recipientUserIds.size(), delivered, skipped
        );
        return new AnnouncementFanOutResult(recipientUserIds.size(), delivered, skipped);
    }

    private boolean deliverOne(AnnouncementEntity announcement, UUID recipientUserId) {
        try {
            notificationService.deliver(new NotificationService.NotificationDelivery(
                    recipientUserId,
                    NotificationType.ANNOUNCEMENT,
                    // ⚠️ The announcement id is the dedup ENTITY id as well as the provenance column.
                    // Passing anything else here (a constant, or null) would collapse every announcement
                    // onto one dedup key and silently deliver only the first one, forever.
                    announcement.getId().toString(),
                    announcement.getTitle(),
                    announcement.getBody(),
                    announcement.getCtaLabel(),
                    announcement.getCtaPath(),
                    announcement.getId()
            ));
            return true;
        } catch (RuntimeException failedDelivery) {
            log.warn(
                    "announcement.fan_out.failed announcementId={} recipientUserId={} message={}",
                    announcement.getId(), recipientUserId, failedDelivery.getMessage()
            );
            return false;
        }
    }

    private void applyContent(AnnouncementEntity announcement, UpsertAnnouncementRequest request, OffsetDateTime now) {
        announcement.setTitle(request.title().trim());
        announcement.setBody(request.body().trim());
        announcement.setCtaPath(AnnouncementCtaPathValidator.validate(request.ctaPath()));
        announcement.setCtaLabel(normalizeCtaLabel(request.ctaLabel(), announcement.getCtaPath()));
        AnnouncementAudience audience = parseAudience(request.audience());
        announcement.setAudience(audience);
        announcement.setAudienceValue(audienceResolver.normalizeAudienceValue(audience, request.audienceValue()));
        announcement.setExpiresAt(request.expiresAt());
        announcement.setUpdatedAt(now);
    }

    /**
     * ⚠️ BOTH OR NEITHER. A path with no label renders an unlabelled link; a label with no path renders
     * a dead one. Either half alone is a defect the admin cannot see from the form.
     */
    private String normalizeCtaLabel(String rawCtaLabel, String ctaPath) {
        String ctaLabel = rawCtaLabel == null || rawCtaLabel.isBlank() ? null : rawCtaLabel.trim();
        if (ctaLabel == null && ctaPath == null) {
            return null;
        }
        if (ctaLabel == null) {
            throw new InvalidAnnouncementRequestException("ctaLabel", "is required when ctaPath is set.");
        }
        if (ctaPath == null) {
            throw new InvalidAnnouncementRequestException(
                    AnnouncementCtaPathValidator.FIELD,
                    "is required when ctaLabel is set."
            );
        }
        return ctaLabel;
    }

    private AnnouncementAudience parseAudience(String rawAudience) {
        if (rawAudience == null || rawAudience.isBlank()) {
            throw new InvalidAnnouncementRequestException(AUDIENCE_FIELD, "is required.");
        }
        try {
            return AnnouncementAudience.valueOf(rawAudience.trim());
        } catch (IllegalArgumentException unknownAudience) {
            throw new InvalidAnnouncementRequestException(AUDIENCE_FIELD, "is not a known audience.");
        }
    }

    private AnnouncementEntity findOrThrow(UUID announcementId) {
        return announcementRepository.findById(announcementId).orElseThrow(AnnouncementNotFoundException::new);
    }

    /**
     * ⚠️ {@code expired} AND {@code editable} ARE DERIVED ON READ, never stored. An expiry that had to
     * be written by a job would be wrong for as long as the job had not run.
     */
    private AnnouncementResponse toResponse(AnnouncementEntity announcement, OffsetDateTime now) {
        boolean expired = announcement.getExpiresAt() != null && !announcement.getExpiresAt().isAfter(now);
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getCtaLabel(),
                announcement.getCtaPath(),
                announcement.getAudience().name(),
                announcement.getAudienceValue(),
                announcement.getStatus().name(),
                announcement.getPublishedAt(),
                announcement.getExpiresAt(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt(),
                announcement.getStatus() == AnnouncementStatus.DRAFT,
                expired
        );
    }

    /**
     * @param recipientCount how many user ids the audience resolved to
     * @param delivered      recipients whose inbox now holds the row, including those it already held —
     *                       a duplicate delivery is a successful no-op, not a failure
     * @param skipped        recipients whose insert failed; a retry picks up exactly these
     */
    public record AnnouncementFanOutResult(int recipientCount, int delivered, int skipped) {
    }
}
