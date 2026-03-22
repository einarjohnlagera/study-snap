package com.studysnap.backend.service.jobs;

import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.UserUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingUsageResetJob {
    private final UserRepository userRepository;
    private final UserUsageService userUsageService;

    @Scheduled(cron = "0 15 1 * * *")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> userIds = userRepository.findAllUserIds();
        for (UUID userId : userIds) {
            userUsageService.ensureUsagePeriod(userId, now);
        }
        log.info("billing.usage.reset ensured usage period rows for {} users", userIds.size());
    }
}
