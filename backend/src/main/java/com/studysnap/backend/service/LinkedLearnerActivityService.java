package com.studysnap.backend.service;

import com.studysnap.backend.dto.LinkedLearnerActivityResponse;
import com.studysnap.backend.dto.StudyEngagementResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinkedLearnerActivityService {
    private final LinkedLearnerGrantAuthorizationService authorizationService;
    private final DashboardService dashboardService;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public LinkedLearnerActivityResponse getActivity(UUID callerUserId, UUID relationshipId) {
        UUID fromUserId = authorizationService.requireGrant(
                callerUserId, relationshipId, LinkedLearnerGrantScope.ACTIVITY);
        UserEntity owner = userRepository.findById(fromUserId)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        StudyEngagementResponse engagement = dashboardService.getStudyEngagement(fromUserId);
        LinkedLearnerActivityResponse response = new LinkedLearnerActivityResponse(
                resolveDisplayName(owner),
                engagement.engagementMode(),
                engagement.currentStreak(),
                engagement.longestStreak(),
                engagement.studyDaysThisWeek()
        );
        trackAnalytics(callerUserId, relationshipId);
        return response;
    }

    private void trackAnalytics(UUID callerUserId, UUID relationshipId) {
        try {
            analyticsService.trackEvent(
                    callerUserId,
                    AnalyticsEventType.CONNECTION_ACTIVITY_VIEWED,
                    relationshipId,
                    Map.of()
            );
        } catch (RuntimeException analyticsFault) {
            // Analytics must never turn an authorized momentum read into a failed response.
            // ⚠️ Defence in depth: AnalyticsService.trackEvent already swallows and logs internally, so
            // this cannot fire today. It logs rather than ignoring for the same reason as the grant path —
            // a view event lost without a trace makes the granted-to-viewed funnel silently wrong.
            log.warn(
                    "action=track_activity_view_analytics outcome=failed userId={} relationshipId={}",
                    callerUserId,
                    relationshipId,
                    analyticsFault
            );
        }
    }

    private String resolveDisplayName(UserEntity user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }
}
