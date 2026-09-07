package com.studysnap.backend.service.jobs;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationCleanupJob {
    private final NotificationService notificationService;
    private final StudySnapProperties properties;

    @Scheduled(cron = "${studysnap.notifications.cleanup-cron:0 15 * * * *}")
    public void run() {
        int deletedCount = notificationService.deleteExpired(
                OffsetDateTime.now(ZoneOffset.UTC),
                properties.getNotifications().getRetentionDays()
        );
        log.info("notification.cleanup deleted {} read or dismissed notifications", deletedCount);
    }
}
