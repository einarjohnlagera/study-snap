package com.studysnap.backend.service;

import com.studysnap.backend.dto.LinkedLearnerProgressResponse;
import com.studysnap.backend.dto.MasterySnapshotResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerProgressNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinkedLearnerProgressService {
    private final LinkedLearnerReadAuthorizationService authorizationService;
    private final AuthService authService;
    private final DashboardService dashboardService;
    private final ProgressReportService progressReportService;
    private final NoteCollectionService noteCollectionService;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public LinkedLearnerProgressResponse getProgress(UUID callerUserId, UUID relationshipId) {
        // ⚠️ Defence in depth on the product's ONLY cross-user read. Every path that grants an
        // ACCEPTED relationship is gated on a verified email, so this is redundant TODAY — it is
        // here so that a future grant path which loses its gate does not silently open the read
        // too. The cost is genuinely zero rather than merely small: emailVerifiedAt is monotonic
        // (no call site ever clears it, and an address change re-stamps it only after the new
        // address is confirmed), so no verified supporter can lose access by holding a stale flag.
        authService.requireEmailVerified(callerUserId);
        UUID learnerUserId = authorizationService.requireAcceptedLearnerId(callerUserId, relationshipId);
        UserEntity learner = userRepository.findById(learnerUserId)
                .orElseThrow(LinkedLearnerProgressNotFoundException::new);

        MasterySnapshotResponse quizPerformance = dashboardService.getMasterySnapshot(learnerUserId);
        ProgressReportResponse progressReport = progressReportService.getProgressReport(
                learnerUserId,
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        List<NoteCollectionSummaryResponse> collections = noteCollectionService.list(learnerUserId);

        LinkedLearnerProgressResponse.ReadinessCounts readiness = aggregateReadiness(progressReport.subjects());
        LinkedLearnerProgressResponse.CollectionProgressCounts collectionProgress =
                aggregateCollectionProgress(collections);
        // ⚠️ Progress-shaped only: study days and both streak fields are activity-derived. Even a
        // boolean inferred from them would disclose activity without an ACTIVITY grant. Their
        // former partial use in hasActivity is now deliberately removed, closing the v0.93.0 LOW
        // finding completely rather than merely omitting the engagement object from serialization.
        boolean hasActivity = readiness.totalConcepts() > 0
                || quizPerformance.studyPacksReviewed() > 0
                || collectionProgress.practicedItems() > 0;

        LinkedLearnerProgressResponse response = new LinkedLearnerProgressResponse(
                relationshipId,
                resolveDisplayName(learner),
                quizPerformance,
                readiness,
                collectionProgress,
                hasActivity
        );
        trackAnalytics(callerUserId, relationshipId);
        return response;
    }

    private void trackAnalytics(UUID callerUserId, UUID relationshipId) {
        try {
            analyticsService.trackEvent(
                    callerUserId,
                    AnalyticsEventType.CONNECTION_PROGRESS_VIEWED,
                    relationshipId,
                    Map.of()
            );
        } catch (RuntimeException analyticsFault) {
            // Analytics must never turn an authorized progress read into a failed response.
            log.warn(
                    "action=track_progress_view_analytics outcome=failed userId={} relationshipId={}",
                    callerUserId,
                    relationshipId,
                    analyticsFault
            );
        }
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
