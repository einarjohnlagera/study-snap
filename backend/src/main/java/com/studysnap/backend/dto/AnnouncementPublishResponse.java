package com.studysnap.backend.dto;

/**
 * @param announcement  the announcement after the transition
 * @param recipientCount how many user ids the audience resolved to
 * @param delivered      recipients whose inbox now holds the row — INCLUDING those it already held,
 *                       because a duplicate delivery is a successful no-op, not a failure
 * @param skipped        recipients whose insert failed and who therefore did NOT receive it; a retry
 *                       of the same publish will pick them up and insert zero duplicates for the rest
 */
public record AnnouncementPublishResponse(
        AnnouncementResponse announcement,
        int recipientCount,
        int delivered,
        int skipped
) {
}
