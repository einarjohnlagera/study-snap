package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminAnalyticsSummaryResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.event.AnalyticsTrackingRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AnalyticsService(
            AnalyticsEventRepository analyticsEventRepository,
            UserRepository userRepository,
            NoteRepository noteRepository,
            StudyPackRepository studyPackRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.studyPackRepository = studyPackRepository;
        this.eventPublisher = eventPublisher;
    }

    public void trackEvent(UUID userId, AnalyticsEventType eventType, UUID entityId, Map<String, Object> metadata) {
        if (eventType == null) {
            return;
        }

        Map<String, Object> safeMetadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
        try {
            eventPublisher.publishEvent(new AnalyticsTrackingRequestedEvent(userId, eventType, entityId, safeMetadata));
        } catch (Exception ex) {
            log.warn(
                    "analytics_event_publish_failed userId={} eventType={} entityId={}",
                    userId,
                    eventType,
                    entityId,
                    ex
            );
        }
    }

    public AdminAnalyticsSummaryResponse getSummary() {
        return new AdminAnalyticsSummaryResponse(
                analyticsEventRepository.countByEventType(AnalyticsEventType.LANDING_PAGE_VIEWED),
                analyticsEventRepository.countByEventType(AnalyticsEventType.LANDING_CTA_CLICKED),
                analyticsEventRepository.countByEventType(AnalyticsEventType.PUBLIC_NOTE_VIEWED),
                analyticsEventRepository.countByEventType(AnalyticsEventType.PUBLIC_NOTE_COPY_CLICKED),
                analyticsEventRepository.countByEventType(AnalyticsEventType.SIGNUP_STARTED),
                analyticsEventRepository.countByEventType(AnalyticsEventType.SIGNUP_COMPLETED),
                analyticsEventRepository.countByEventType(AnalyticsEventType.EMAIL_VERIFIED),
                userRepository.count(),
                noteRepository.count(),
                studyPackRepository.count(),
                analyticsEventRepository.countByEventType(AnalyticsEventType.CHALLENGE_QUIZ_STARTED),
                analyticsEventRepository.countByEventType(AnalyticsEventType.ADAPTIVE_PRACTICE_STARTED),
                analyticsEventRepository.countByEventType(AnalyticsEventType.SUBSCRIPTION_STARTED)
        );
    }
}
