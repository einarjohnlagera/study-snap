package com.studysnap.backend.dto;

/**
 * @param announcement  the announcement after the transition
 * @param recipientCount how many user ids the audience resolved to
 * @param queued          recipients accepted for background delivery; zero when dispatch was rejected
 */
public record AnnouncementPublishResponse(
        AnnouncementResponse announcement,
        int recipientCount,
        int queued
) {
}
