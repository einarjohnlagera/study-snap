package com.studysnap.backend.service;

import com.studysnap.backend.dto.LinkedLearnerActivityResponse;
import com.studysnap.backend.dto.StudyEngagementResponse;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkedLearnerActivityService {
    private final LinkedLearnerGrantAuthorizationService authorizationService;
    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public LinkedLearnerActivityResponse getActivity(UUID callerUserId, UUID relationshipId) {
        UUID fromUserId = authorizationService.requireGrant(
                callerUserId, relationshipId, LinkedLearnerGrantScope.ACTIVITY);
        UserEntity owner = userRepository.findById(fromUserId)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        StudyEngagementResponse engagement = dashboardService.getStudyEngagement(fromUserId);
        return new LinkedLearnerActivityResponse(
                resolveDisplayName(owner),
                engagement.engagementMode(),
                engagement.currentStreak(),
                engagement.longestStreak(),
                engagement.studyDaysThisWeek()
        );
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
