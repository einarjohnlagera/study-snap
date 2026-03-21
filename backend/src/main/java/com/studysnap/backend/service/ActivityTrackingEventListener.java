package com.studysnap.backend.service;

import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.event.ActivityTrackingRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActivityTrackingEventListener {
    private static final Logger log = LoggerFactory.getLogger(ActivityTrackingEventListener.class);
    private static final Set<ActivityType> MEANINGFUL_STUDY_ACTIVITIES = EnumSet.of(
            ActivityType.CREATED_STUDY_PACK,
            ActivityType.COMPLETED_QUICK_REVIEW,
            ActivityType.COMPLETED_ADAPTIVE_QUIZ
    );

    private final ActivityEventRepository activityEventRepository;
    private final StudyPackRepository studyPackRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onActivityRequested(ActivityTrackingRequestedEvent event) {
        if (event == null || event.userId() == null || event.activityType() == null) {
            return;
        }

        try {
            OffsetDateTime eventTimestamp = OffsetDateTime.now();
            UserActivityEventEntity activityEvent = new UserActivityEventEntity();
            activityEvent.setId(UUID.randomUUID());
            activityEvent.setUserId(event.userId());
            activityEvent.setStudyPackId(event.studyPackId());
            activityEvent.setNoteId(resolveNoteId(event.studyPackId()));
            activityEvent.setActivityType(event.activityType());
            activityEvent.setCreatedAt(eventTimestamp);
            activityEventRepository.save(activityEvent);

            updateStudyStreak(event.userId(), event.activityType(), eventTimestamp);
        } catch (Exception ex) {
            log.warn(
                    "activity_tracking_failed userId={} activityType={} studyPackId={}",
                    event.userId(),
                    event.activityType(),
                    event.studyPackId(),
                    ex
            );
        }
    }

    private UUID resolveNoteId(UUID studyPackId) {
        if (studyPackId == null) {
            return null;
        }
        return studyPackRepository.findById(studyPackId)
                .map(studyPack -> studyPack.getNoteId())
                .orElse(null);
    }

    private void updateStudyStreak(UUID userId, ActivityType activityType, OffsetDateTime eventTimestamp) {
        if (!MEANINGFUL_STUDY_ACTIVITIES.contains(activityType)) {
            return;
        }

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        LocalDate studyDate = eventTimestamp.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        LocalDate lastStudyDate = user.getLastStudyDate();
        int currentStreak = user.getCurrentStreak() == null ? 0 : user.getCurrentStreak();
        int longestStreak = user.getLongestStreak() == null ? 0 : user.getLongestStreak();

        if (lastStudyDate != null && lastStudyDate.isEqual(studyDate)) {
            return;
        }

        if (lastStudyDate != null && lastStudyDate.plusDays(1).isEqual(studyDate)) {
            currentStreak = Math.max(1, currentStreak) + 1;
        } else {
            currentStreak = 1;
        }

        longestStreak = Math.max(longestStreak, currentStreak);
        user.setCurrentStreak(currentStreak);
        user.setLongestStreak(longestStreak);
        user.setLastStudyDate(studyDate);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }
}
