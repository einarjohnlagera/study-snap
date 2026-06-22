package com.studysnap.backend.repository;

import com.studysnap.backend.dto.AdminPublicNoteMetricItemResponse;
import com.studysnap.backend.entity.AnalyticsEventEntity;
import com.studysnap.backend.entity.AnalyticsEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEventEntity, UUID> {
    String STUDY_PACK_GENERATED_EVENT_TYPE = "STUDY_PACK_GENERATED";
    String QUICK_REVIEW_STARTED_EVENT_TYPE = "QUICK_REVIEW_STARTED";
    String CHALLENGE_QUIZ_STARTED_EVENT_TYPE = "CHALLENGE_QUIZ_STARTED";
    String ADAPTIVE_PRACTICE_STARTED_EVENT_TYPE = "ADAPTIVE_PRACTICE_STARTED";
    String BOARD_EXAM_STARTED_EVENT_TYPE = "BOARD_EXAM_STARTED";
    String LONG_EXAM_STARTED_EVENT_TYPE = "LONG_EXAM_STARTED";
    String INTERVIEW_PRACTICE_STARTED_EVENT_TYPE = "INTERVIEW_PRACTICE_STARTED";
    // Retention activation = first STUDY_PACK_GENERATED. Week-2 return = any analytics event in (firstPack + 7d, firstPack + 14d].
    // Eligible users have a first pack at least 14 days before the report time so the week-2 window is complete.
    String RETENTION_WINDOW_START_DAYS = "7";
    String RETENTION_WINDOW_END_DAYS = "14";
    int RETENTION_COHORT_WEEK_LIMIT = 8;
    String VALUE_LOOP_CLOSURE_QUERY = """
            SELECT COUNT(DISTINCT fp.user_id)
            FROM (
                SELECT ae.user_id, MIN(ae.created_at) AS first_generated_at
                FROM analytics_events ae
                WHERE ae.event_type = '""" + STUDY_PACK_GENERATED_EVENT_TYPE + "'\n" + """
                GROUP BY ae.user_id
            ) fp
            WHERE EXISTS (
                SELECT 1 FROM analytics_events quiz
                WHERE quiz.user_id = fp.user_id
                AND quiz.event_type IN (
                    '""" + QUICK_REVIEW_STARTED_EVENT_TYPE + "',\n" + """
                    '""" + CHALLENGE_QUIZ_STARTED_EVENT_TYPE + "',\n" + """
                    '""" + ADAPTIVE_PRACTICE_STARTED_EVENT_TYPE + "',\n" + """
                    '""" + BOARD_EXAM_STARTED_EVENT_TYPE + "',\n" + """
                    '""" + LONG_EXAM_STARTED_EVENT_TYPE + "',\n" + """
                    '""" + INTERVIEW_PRACTICE_STARTED_EVENT_TYPE + "'\n" + """
                )
                AND quiz.created_at >= fp.first_generated_at
                AND quiz.created_at <= fp.first_generated_at + INTERVAL '7 days'
            )
            """;
    String FIRST_PACK_CTE = """
            WITH first_packs AS (
                SELECT ae.user_id, MIN(ae.created_at) AS first_pack_at
                FROM analytics_events ae
                WHERE ae.event_type = '""" + STUDY_PACK_GENERATED_EVENT_TYPE + "'\n" + """
                  AND ae.user_id IS NOT NULL
                GROUP BY ae.user_id
            )
            """;
    String RETENTION_RETURNED_EXISTS = """
            EXISTS (
                SELECT 1 FROM analytics_events activity
                WHERE activity.user_id = fp.user_id
                  AND activity.created_at > fp.first_pack_at + INTERVAL '""" + RETENTION_WINDOW_START_DAYS + "' DAY\n" + """
                  AND activity.created_at <= fp.first_pack_at + INTERVAL '""" + RETENTION_WINDOW_END_DAYS + "' DAY\n" + """
            )
            """;
    String ELIGIBLE_ACTIVATED_USERS_QUERY = FIRST_PACK_CTE + """
            SELECT COUNT(*)
            FROM first_packs fp
            WHERE fp.first_pack_at <= CAST(:now AS TIMESTAMP WITH TIME ZONE) - INTERVAL '""" + RETENTION_WINDOW_END_DAYS + "' DAY\n" + """
            """;
    String RETURNED_WEEK_2_USERS_QUERY = FIRST_PACK_CTE + """
            SELECT COUNT(*)
            FROM first_packs fp
            WHERE fp.first_pack_at <= CAST(:now AS TIMESTAMP WITH TIME ZONE) - INTERVAL '""" + RETENTION_WINDOW_END_DAYS + "' DAY\n" + """
            """ + "  AND " + RETENTION_RETURNED_EXISTS + "\n";
    String WEEKLY_RETENTION_COHORTS_QUERY = FIRST_PACK_CTE + """
            SELECT CAST(DATE_TRUNC('week', fp.first_pack_at) AS DATE) AS weekStart,
                   COUNT(*) AS cohortSize,
                   SUM(CASE WHEN """ + " " + RETENTION_RETURNED_EXISTS + " THEN 1 ELSE 0 END) AS returnedCount\n" + """
            FROM first_packs fp
            WHERE fp.first_pack_at <= CAST(:now AS TIMESTAMP WITH TIME ZONE) - INTERVAL '""" + RETENTION_WINDOW_END_DAYS + "' DAY\n" + """
            GROUP BY CAST(DATE_TRUNC('week', fp.first_pack_at) AS DATE)
            ORDER BY weekStart DESC
            """ + "LIMIT " + RETENTION_COHORT_WEEK_LIMIT + "\n";

    long countByEventType(AnalyticsEventType eventType);

    @Query("""
            select count(distinct e.userId)
            from AnalyticsEventEntity e
            where e.eventType = :eventType
            """)
    long countDistinctUsersByEventType(@Param("eventType") AnalyticsEventType eventType);

    @Query("""
            select count(distinct e.userId)
            from AnalyticsEventEntity e
            where e.eventType = :subscriptionStarted
              and exists (
                  select 1
                  from AnalyticsEventEntity paywall
                  where paywall.userId = e.userId
                    and paywall.eventType = :paywallViewed
                    and paywall.createdAt <= e.createdAt
              )
            """)
    long countUsersUpgradedAfterPaywall(
            @Param("paywallViewed") AnalyticsEventType paywallViewed,
            @Param("subscriptionStarted") AnalyticsEventType subscriptionStarted
    );

    @Query("""
            select count(distinct later.userId)
            from AnalyticsEventEntity later
            where later.eventType = :laterEventType
              and exists (
                  select 1
                  from AnalyticsEventEntity earlier
                  where earlier.userId = later.userId
                    and earlier.eventType = :earlierEventType
                    and earlier.createdAt <= later.createdAt
              )
            """)
    long countDistinctUsersWithEventAfterEvent(
            @Param("earlierEventType") AnalyticsEventType earlierEventType,
            @Param("laterEventType") AnalyticsEventType laterEventType
    );

    @Query(value = VALUE_LOOP_CLOSURE_QUERY, nativeQuery = true)
    long countUsersStartedQuizWithin7DaysOfFirstGeneratedPack();

    @Query(value = ELIGIBLE_ACTIVATED_USERS_QUERY, nativeQuery = true)
    long countEligibleActivatedUsersForWeek2Retention(@Param("now") OffsetDateTime now);

    @Query(value = RETURNED_WEEK_2_USERS_QUERY, nativeQuery = true)
    long countReturnedWeek2Users(@Param("now") OffsetDateTime now);

    @Query(value = WEEKLY_RETENTION_COHORTS_QUERY, nativeQuery = true)
    List<WeeklyRetentionCohortProjection> findWeeklyRetentionCohorts(@Param("now") OffsetDateTime now);

    List<AnalyticsEventEntity> findByEventTypeOrderByCreatedAtDesc(AnalyticsEventType eventType, Pageable pageable);

    @Query("""
            select new com.studysnap.backend.dto.AdminPublicNoteMetricItemResponse(
                n.id,
                n.title,
                n.subject,
                count(e.id)
            )
            from AnalyticsEventEntity e
            join NoteEntity n on n.id = e.entityId
            where e.eventType = :eventType
              and n.visibility = com.studysnap.backend.entity.NoteVisibility.PUBLIC
            group by n.id, n.title, n.subject
            order by count(e.id) desc, max(e.createdAt) desc
            """)
    List<AdminPublicNoteMetricItemResponse> findTopPublicNotesByEventType(
            @Param("eventType") AnalyticsEventType eventType,
            Pageable pageable
    );

    @Query("""
            select n.id as noteId, count(e.id) as totalCount
            from AnalyticsEventEntity e
            join NoteEntity n on n.id = e.entityId
            where e.eventType = :eventType
              and n.id in :noteIds
              and n.visibility = com.studysnap.backend.entity.NoteVisibility.PUBLIC
            group by n.id
            """)
    List<PublicNoteEventCountProjection> countPublicNoteEventsByTypeAndNoteIds(
            @Param("eventType") AnalyticsEventType eventType,
            @Param("noteIds") List<UUID> noteIds
    );

    long countByEventTypeAndEntityId(AnalyticsEventType eventType, UUID entityId);
}
