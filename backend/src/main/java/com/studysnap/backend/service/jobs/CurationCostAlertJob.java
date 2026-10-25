package com.studysnap.backend.service.jobs;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.NotificationType;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CurationCostAlertJob {
    private static final BigDecimal TOKENS_PER_MILLION = new BigDecimal("1000000");
    private static final String DEDUP_PREFIX = "curation-cost-alert:";
    private static final String TITLE = "Official Study Pack cost alert";
    private static final String SCOPE = "This estimate covers Study Pack generation only. "
            + "It excludes note generation from topics, template seeding, summary regeneration and quiz repair. "
            + "Check the OpenAI billing dashboard for total spend.";

    private final StudyPackRepository studyPackRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final StudySnapProperties properties;

    @Scheduled(cron = "${studysnap.curation-cost-alert.schedule-cron:0 30 * * * *}", zone = "UTC")
    public void run() {
        runAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    void runAt(OffsetDateTime now) {
        if (!properties.getCurationCostAlert().isEnabled()) {
            return;
        }
        try {
            YearMonth month = YearMonth.from(now.withOffsetSameInstant(ZoneOffset.UTC));
            OffsetDateTime monthStart = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            BigDecimal estimate = estimate(studyPackRepository.sumAdminGenerationTokensByModel(
                    monthStart, monthStart.plusMonths(1)));
            BigDecimal threshold = properties.getCurationCostAlert().getThresholdUsd();
            log.info("Tracked Official Study Pack generation estimate for {}: USD {} (threshold USD {})",
                    month, estimate, threshold);
            if (estimate.compareTo(threshold) < 0) {
                return;
            }

            String dedupKey = DEDUP_PREFIX + month + ":" + threshold.setScale(2, RoundingMode.HALF_UP);
            String body = "Estimated tracked Official Study Pack generation cost this month has reached $"
                    + estimate.setScale(2, RoundingMode.HALF_UP) + " (threshold $"
                    + threshold.setScale(2, RoundingMode.HALF_UP) + "). " + SCOPE;
            for (var adminId : userRepository.findAdminUserIds()) {
                try {
                    notificationService.deliver(new NotificationService.NotificationDelivery(
                            adminId, NotificationType.CURATION_COST_ALERT, dedupKey,
                            TITLE, body, null, null, null), dedupKey);
                } catch (RuntimeException deliveryFailure) {
                    log.error("Could not deliver curation cost alert to ADMIN {}", adminId, deliveryFailure);
                }
            }
        } catch (RuntimeException failure) {
            log.error("Could not estimate tracked Official Study Pack generation cost", failure);
        }
    }

    BigDecimal estimate(Iterable<StudyPackRepository.MonthlyCurationTokens> rows) {
        Map<String, StudySnapProperties.ModelPrice> prices = properties.getCurationCostAlert().getModelPrices();
        BigDecimal total = BigDecimal.ZERO;
        for (var row : rows) {
            String model = row.getModelUsed();
            var match = prices.entrySet().stream()
                    .filter(entry -> model != null && model.startsWith(entry.getKey()))
                    .max(Comparator.comparingInt(entry -> entry.getKey().length()));
            StudySnapProperties.ModelPrice price;
            if (match.isPresent()) {
                price = match.get().getValue();
            } else {
                log.warn("Unknown Study Pack model {} in curation cost estimate; using highest configured rates", model);
                price = new StudySnapProperties.ModelPrice();
                price.setInput(prices.values().stream().map(StudySnapProperties.ModelPrice::getInput)
                        .max(BigDecimal::compareTo).orElseThrow());
                price.setCachedInput(prices.values().stream()
                        .map(p -> p.getCachedInput() == null ? p.getInput() : p.getCachedInput())
                        .max(BigDecimal::compareTo).orElseThrow());
                price.setOutput(prices.values().stream().map(StudySnapProperties.ModelPrice::getOutput)
                        .max(BigDecimal::compareTo).orElseThrow());
            }
            BigDecimal cached = BigDecimal.valueOf(row.getCachedInputTokens());
            BigDecimal uncached = BigDecimal.valueOf(row.getInputTokens()).subtract(cached).max(BigDecimal.ZERO);
            total = total.add(uncached.multiply(price.getInput())
                    .add(cached.multiply(price.getCachedInput() == null ? price.getInput() : price.getCachedInput()))
                    .add(BigDecimal.valueOf(row.getOutputTokens()).multiply(price.getOutput()))
                    .divide(TOKENS_PER_MILLION, 8, RoundingMode.HALF_UP));
        }
        return total;
    }
}
