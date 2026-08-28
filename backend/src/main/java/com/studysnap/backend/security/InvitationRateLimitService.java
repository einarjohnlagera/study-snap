package com.studysnap.backend.security;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.TooManyLinkedLearnerInvitationsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Meters linked-learner invitations. Email invitations send mail from NoteLib's signed domain;
 * shareable links are distributed by their creator but still initiate third-party contact.
 *
 * <p>⚠️ Email-keying removed the account lookup that used to bound this implicitly: an invite to an
 * address with no account is now a real, deliverable send rather than a no-op. That is the point of
 * the feature, and it is also why the endpoint needs an explicit meter.
 *
 * <p>⚠️ TWO KEYS, deliberately. A volume cap alone still permits sending to the SAME victim address
 * up to the limit, repeatedly, because re-posting an address re-sends the invitation. The
 * per-address key is what makes that case bounded.
 *
 * <p>⚠️ Neither key may depend on whether the address has an account. Branching on account state is
 * exactly the oracle {@code V122} closed, so this must stay a pure function of caller and address.
 */
@Service
public class InvitationRateLimitService {
    private static final long BUCKET_PURGE_INTERVAL_MS = 3_600_000L;

    private final StudySnapProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InvitationRateLimitService(StudySnapProperties properties) {
        this.properties = properties;
    }

    public void assertInviteAllowed(UUID inviterUserId, String normalizedEmail) {
        assertInviteAllowed(inviterUserId, normalizedEmail, OffsetDateTime.now());
    }

    void assertInviteAllowed(UUID inviterUserId, String normalizedEmail, OffsetDateTime now) {
        StudySnapProperties.LinkedLearners config = properties.getLinkedLearners();
        // Per-address first: it is the tighter bound, so a targeted flood is refused before it can
        // consume the caller's volume allowance and mask itself as ordinary usage.
        consume("address:" + inviterUserId + ":" + normalizedEmail,
                config.getInvitesPerAddressPerWindow(), window(config), now);
        consume("inviter:" + inviterUserId, config.getInvitesPerWindow(), window(config), now);
    }

    /**
     * Link invitations have no address key. Their independent creator bucket is deliberate: link
     * creation can still produce third-party contact when the creator distributes the URL, while
     * consuming this bucket must not change or dilute the email-invitation limits above.
     */
    public void assertLinkCreationAllowed(UUID creatorUserId) {
        assertLinkCreationAllowed(creatorUserId, OffsetDateTime.now());
    }

    void assertLinkCreationAllowed(UUID creatorUserId, OffsetDateTime now) {
        StudySnapProperties.LinkedLearners config = properties.getLinkedLearners();
        consume("link-creator:" + creatorUserId,
                config.getInvitationLinksPerWindow(), window(config), now);
    }

    private Duration window(StudySnapProperties.LinkedLearners config) {
        return Duration.ofHours(config.getInviteRateLimitWindowHours());
    }

    private void consume(String bucketKey, int limit, Duration window, OffsetDateTime now) {
        Bucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> new Bucket(now, 0, window));
        synchronized (bucket) {
            if (bucket.windowStart.plus(window).isBefore(now)) {
                bucket.windowStart = now;
                bucket.count = 0;
            }
            bucket.window = window;
            if (bucket.count >= limit) {
                throw new TooManyLinkedLearnerInvitationsException();
            }
            bucket.count++;
        }
    }

    // Keys include a caller-supplied address, so without eviction an invite flood grows the map
    // without bound. Mirrors AuthRateLimitService, on a longer interval to match the longer window.
    @Scheduled(fixedDelay = BUCKET_PURGE_INTERVAL_MS)
    void purgeExpiredBuckets() {
        purgeExpiredBuckets(OffsetDateTime.now());
    }

    void purgeExpiredBuckets(OffsetDateTime now) {
        buckets.values().removeIf(bucket -> {
            synchronized (bucket) {
                return bucket.windowStart.plus(bucket.window).isBefore(now);
            }
        });
    }

    int trackedBucketCount() {
        return buckets.size();
    }

    private static final class Bucket {
        private OffsetDateTime windowStart;
        private int count;
        private Duration window;

        private Bucket(OffsetDateTime windowStart, int count, Duration window) {
            this.windowStart = windowStart;
            this.count = count;
            this.window = window;
        }
    }
}
