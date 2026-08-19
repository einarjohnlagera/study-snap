package com.studysnap.backend.service;

import com.studysnap.backend.dto.LinkedLearnerProgressResponse;
import com.studysnap.backend.dto.MasterySnapshotResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.dto.StudyEngagementResponse;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerProgressNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkedLearnerProgressService {
    private final LinkedLearnerReadAuthorizationService authorizationService;
    private final DashboardService dashboardService;
    private final ProgressReportService progressReportService;
    private final NoteCollectionService noteCollectionService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public LinkedLearnerProgressResponse getProgress(UUID callerUserId, UUID relationshipId) {
        UUID learnerUserId = authorizationService.requireAcceptedLearnerId(callerUserId, relationshipId);
        UserEntity learner = userRepository.findById(learnerUserId)
                .orElseThrow(LinkedLearnerProgressNotFoundException::new);

        MasterySnapshotResponse quizPerformance = dashboardService.getMasterySnapshot(learnerUserId);
        StudyEngagementResponse engagement = dashboardService.getStudyEngagement(learnerUserId);
        ProgressReportResponse progressReport = progressReportService.getProgressReport(
                learnerUserId,
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        List<NoteCollectionSummaryResponse> collections = noteCollectionService.list(learnerUserId);

        LinkedLearnerProgressResponse.ReadinessCounts readiness = aggregateReadiness(progressReport.subjects());
        LinkedLearnerProgressResponse.CollectionProgressCounts collectionProgress =
                aggregateCollectionProgress(collections);
        boolean hasActivity = readiness.totalConcepts() > 0
                || quizPerformance.studyPacksReviewed() > 0
                || engagement.studyDaysThisWeek() > 0
                || collectionProgress.practicedItems() > 0;

        return new LinkedLearnerProgressResponse(
                relationshipId,
                resolveDisplayName(learner),
                quizPerformance,
                engagement,
                readiness,
                collectionProgress,
                hasActivity
        );
    }

    private LinkedLearnerProgressResponse.ReadinessCounts aggregateReadiness(
            List<SubjectProgressEntry> subjects
    ) {
        int totalConcepts = subjects.stream().mapToInt(SubjectProgressEntry::totalConcepts).sum();
        int masteredConcepts = subjects.stream().mapToInt(SubjectProgressEntry::masteredConcepts).sum();
        int dueConcepts = subjects.stream().mapToInt(SubjectProgressEntry::dueConcepts).sum();
        int notStartedConcepts = subjects.stream().mapToInt(SubjectProgressEntry::notPracticedConcepts).sum();
        int readinessPercentage = totalConcepts == 0
                ? 0
                : (int) Math.round(100.0 * masteredConcepts / totalConcepts);
        return new LinkedLearnerProgressResponse.ReadinessCounts(
                totalConcepts,
                masteredConcepts,
                dueConcepts,
                notStartedConcepts,
                readinessPercentage
        );
    }

    private LinkedLearnerProgressResponse.CollectionProgressCounts aggregateCollectionProgress(
            List<NoteCollectionSummaryResponse> collections
    ) {
        return new LinkedLearnerProgressResponse.CollectionProgressCounts(
                collections.size(),
                collections.stream().mapToInt(NoteCollectionSummaryResponse::itemCount).sum(),
                collections.stream().mapToInt(NoteCollectionSummaryResponse::readyCount).sum(),
                collections.stream().mapToInt(NoteCollectionSummaryResponse::notesPracticed).sum()
        );
    }

    private String resolveDisplayName(UserEntity learner) {
        if (learner.getDisplayName() != null && !learner.getDisplayName().isBlank()) {
            return learner.getDisplayName().trim();
        }
        String fullName = ((learner.getFirstName() == null ? "" : learner.getFirstName()) + " "
                + (learner.getLastName() == null ? "" : learner.getLastName())).trim();
        return fullName.isBlank() ? learner.getEmail() : fullName;
    }
}
