package com.studysnap.backend.service;

import com.studysnap.backend.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkOperationNotificationService {
    static final int TOPIC_TEXT_BUDGET = 850;
    static final String CTA_LABEL = "Open Library";
    static final String CTA_PATH = "/library";

    private final NotificationService notificationService;

    /**
     * Bulk generation sends the learner away from the result, unlike single-note generation whose
     * detail page polls every three seconds. Only failures need a durable signal: createdCount records
     * notes, not completed Study Packs, so a success notification could claim readiness that is false.
     */
    public void bulkGenerationIncomplete(
            UUID recipientUserId,
            UUID resultId,
            List<String> failedTopics,
            List<String> quotaBlockedTopics
    ) {
        try {
            if (failedTopics.isEmpty() && quotaBlockedTopics.isEmpty()) {
                return;
            }
            GenerationCopy copy = generationCopy(failedTopics, quotaBlockedTopics);
            deliver(recipientUserId, resultId, NotificationType.BULK_GENERATION_INCOMPLETE, copy.title(), copy.body());
        } catch (RuntimeException failedDelivery) {
            logFailure(recipientUserId, resultId, NotificationType.BULK_GENERATION_INCOMPLETE, failedDelivery);
        }
    }

    /** Bulk regeneration also sends the learner away, so a normally completed run gets one signal. */
    public void bulkRegenerationComplete(UUID recipientUserId, UUID batchId, int requested, int regenerated) {
        try {
            GenerationCopy copy;
            if (regenerated == requested) {
                copy = requested == 1
                        ? new GenerationCopy("Your Study Pack has been updated", "Open your Library to see it.")
                        : new GenerationCopy(regenerated + " Study Packs have been updated", "Open your Library to see them.");
            } else if (regenerated > 0) {
                String title = regenerated == 1
                        ? "1 Study Pack has been updated"
                        : regenerated + " Study Packs have been updated";
                copy = new GenerationCopy(
                        title,
                        "Some Study Packs weren't updated. Your existing Study Packs are unchanged and still work."
                );
            } else {
                copy = new GenerationCopy(
                        "We couldn't update your Study Packs",
                        "Your existing Study Packs are unchanged and still work."
                );
            }
            deliver(recipientUserId, batchId, NotificationType.BULK_REGENERATION_COMPLETE, copy.title(), copy.body());
        } catch (RuntimeException failedDelivery) {
            logFailure(recipientUserId, batchId, NotificationType.BULK_REGENERATION_COMPLETE, failedDelivery);
        }
    }

    private GenerationCopy generationCopy(List<String> failedTopics, List<String> quotaBlockedTopics) {
        List<TopicEntry> topics = new ArrayList<>(failedTopics.size() + quotaBlockedTopics.size());
        if (!failedTopics.isEmpty() && !quotaBlockedTopics.isEmpty()) {
            // Give each copy group one topic before filling the shared budget. Otherwise a long failed
            // list could leave the quota sentence empty even though quota-blocked topics exist.
            topics.add(new TopicEntry(failedTopics.getFirst(), TopicGroup.FAILED));
            topics.add(new TopicEntry(quotaBlockedTopics.getFirst(), TopicGroup.QUOTA_BLOCKED));
            failedTopics.stream().skip(1).forEach(topic -> topics.add(new TopicEntry(topic, TopicGroup.FAILED)));
            quotaBlockedTopics.stream().skip(1)
                    .forEach(topic -> topics.add(new TopicEntry(topic, TopicGroup.QUOTA_BLOCKED)));
        } else {
            failedTopics.forEach(topic -> topics.add(new TopicEntry(topic, TopicGroup.FAILED)));
            quotaBlockedTopics.forEach(topic -> topics.add(new TopicEntry(topic, TopicGroup.QUOTA_BLOCKED)));
        }
        TruncatedTopics truncated = truncate(topics);

        if (quotaBlockedTopics.isEmpty()) {
            int count = failedTopics.size();
            return new GenerationCopy(
                    count == 1 ? "1 topic couldn't be generated" : count + " topics couldn't be generated",
                    "Couldn't be generated: " + truncated.failedText() + truncated.suffix()
            );
        }
        if (failedTopics.isEmpty()) {
            int count = quotaBlockedTopics.size();
            return new GenerationCopy(
                    count == 1 ? "1 topic needs more monthly capacity" : count + " topics need more monthly capacity",
                    "Not created because your monthly limit was reached: "
                            + truncated.quotaBlockedText() + truncated.suffix()
            );
        }
        return new GenerationCopy(
                "Some topics couldn't be generated",
                "Couldn't be generated: " + truncated.failedText()
                        + ". Not created because your monthly limit was reached: " + truncated.quotaBlockedText()
                        + truncated.suffix() + "."
        );
    }

    private TruncatedTopics truncate(List<TopicEntry> topics) {
        List<String> failed = new ArrayList<>();
        List<String> quotaBlocked = new ArrayList<>();
        int used = 0;
        int omitted = 0;
        for (TopicEntry entry : topics) {
            List<String> destination = entry.group() == TopicGroup.FAILED ? failed : quotaBlocked;
            int separatorLength = destination.isEmpty() ? 0 : 2;
            int available = TOPIC_TEXT_BUDGET - used - separatorLength;
            if (available <= 0) {
                omitted++;
                continue;
            }
            String topic = entry.topic();
            if (topic.length() > available) {
                if (failed.isEmpty() && quotaBlocked.isEmpty()) {
                    String shortened = available == 1 ? "…" : topic.substring(0, available - 1) + "…";
                    destination.add(shortened);
                    used += shortened.length();
                } else {
                    omitted++;
                }
                continue;
            }
            destination.add(topic);
            used += separatorLength + topic.length();
        }
        return new TruncatedTopics(
                String.join(", ", failed),
                String.join(", ", quotaBlocked),
                omitted == 0 ? "" : " and " + omitted + " more"
        );
    }

    private void deliver(UUID recipientUserId, UUID operationId, NotificationType type, String title, String body) {
        notificationService.deliver(new NotificationService.NotificationDelivery(
                recipientUserId,
                type,
                operationId.toString(),
                title,
                body,
                CTA_LABEL,
                CTA_PATH,
                null
        ));
    }

    private void logFailure(UUID recipientUserId, UUID operationId, NotificationType type, RuntimeException failure) {
        log.warn(
                "action=bulk_operation_notification outcome=failed operationId={} recipientUserId={} type={}",
                operationId,
                recipientUserId,
                type,
                failure
        );
    }

    private enum TopicGroup { FAILED, QUOTA_BLOCKED }

    private record TopicEntry(String topic, TopicGroup group) { }

    private record TruncatedTopics(String failedText, String quotaBlockedText, String suffix) { }

    private record GenerationCopy(String title, String body) { }
}
