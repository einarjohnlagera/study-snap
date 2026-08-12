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
    String LANDING_PAGE_VIEWED_EVENT_TYPE = "LANDING_PAGE_VIEWED";
    String EXAM_HUB_VIEWED_EVENT_TYPE = "EXAM_HUB_VIEWED";
    String PUBLISHED_PLANS_VIEWED_EVENT_TYPE = "PUBLISHED_PLANS_VIEWED";
    String EXAM_HUB_CTA_CLICKED_EVENT_TYPE = "EXAM_HUB_CTA_CLICKED";
    String ONBOARDING_STEP_VIEWED_EVENT_TYPE = "ONBOARDING_V2_STEP_VIEWED";
    // Retention activation = first STUDY_PACK_GENERATED. Week-2 return = any analytics event in (firstPack + 7d, firstPack + 14d].
    // Eligible users have a first pack at least 14 days before the report time so the week-2 window is complete.
    String RETENTION_WINDOW_START_DAYS = "7";
    String RETENTION_WINDOW_END_DAYS = "14";
    String WIDE_RETENTION_MATURITY_DAYS = "30";
    String EARLY_RETENTION_WINDOW_START_DAYS = "1";
    String WIDE_RETENTION_WINDOW_END_DAYS = "30";
    int RETENTION_COHORT_WEEK_LIMIT = 8;
    String REFERRER_SOURCE_JSON_PATH = "referrerSource";
    String GOOGLE_REFERRER_SOURCE = "google";
    String DIRECT_REFERRER_SOURCE = "direct";
    // PostgreSQL's jsonb_extract_path_text(metadata_json, 'referrerSource') is equivalent to
    // metadata_json ->> 'referrerSource', while remaining executable in the H2 integration suite.
    String REFERRER_SOURCE_EXPRESSION = "jsonb_extract_path_text(ae.metadata_json, '" + REFERRER_SOURCE_JSON_PATH + "')";
    String ONBOARDING_STEP_NAME_JSON_PATH = "step_name";
    String ONBOARDING_STEP_NAME_EXPRESSION = "jsonb_extract_path_text(ae.metadata_json, '"
            + ONBOARDING_STEP_NAME_JSON_PATH + "')";
    String ONBOARDING_STEP_USER_COUNTS_QUERY = "SELECT " + ONBOARDING_STEP_NAME_EXPRESSION + " AS stepName,\n"
            + "       COUNT(DISTINCT ae.user_id) AS userCount\n"
            + "FROM analytics_events ae\n"
            + "WHERE ae.event_type = '" + ONBOARDING_STEP_VIEWED_EVENT_TYPE + "'\n"
            + "GROUP BY " + ONBOARDING_STEP_NAME_EXPRESSION;
    String ONBOARDING_COMPLETION_QUERY = "SELECT COUNT(*) AS totalSignups,\n"
            + "       COUNT(onboarding_completed_at) AS onboardingCompletedUsers\n"
            + "FROM users";
    String ORGANIC_LANDINGS_QUERY = "SELECT CAST(DATE_TRUNC('week', ae.created_at) AS DATE) AS weekStart,\n"
            + "       ae.event_type AS eventType,\n"
            + "       COALESCE(" + REFERRER_SOURCE_EXPRESSION + ", '" + DIRECT_REFERRER_SOURCE + "') AS referrerSource,\n"
            + "       COUNT(*) AS totalCount\n"
            + "FROM analytics_events ae\n"
            + "WHERE ae.event_type IN ('" + LANDING_PAGE_VIEWED_EVENT_TYPE + "', '"
            + EXAM_HUB_VIEWED_EVENT_TYPE + "', '" + PUBLISHED_PLANS_VIEWED_EVENT_TYPE + "')\n"
            + "  AND ae.created_at >= CAST(:since AS TIMESTAMP WITH TIME ZONE)\n"
            + "GROUP BY CAST(DATE_TRUNC('week', ae.created_at) AS DATE), ae.event_type, "
            + "COALESCE(" + REFERRER_SOURCE_EXPRESSION + ", '" + DIRECT_REFERRER_SOURCE + "')\n"
            + "ORDER BY weekStart DESC, eventType ASC, referrerSource ASC";
    String EXAM_HUB_ORGANIC_CLICK_THROUGH_QUERY = "SELECT COALESCE(SUM(CASE\n"
            + "           WHEN ae.event_type = '" + EXAM_HUB_VIEWED_EVENT_TYPE + "'\n"
            + "            AND " + REFERRER_SOURCE_EXPRESSION + " = '" + GOOGLE_REFERRER_SOURCE + "'\n"
            + "           THEN 1 ELSE 0 END), 0) AS googleExamHubViews,\n"
            + "       COALESCE(SUM(CASE\n"
            + "           WHEN ae.event_type = '" + EXAM_HUB_CTA_CLICKED_EVENT_TYPE + "'\n"
            + "           THEN 1 ELSE 0 END), 0) AS examHubCtaClicks\n"
            + "FROM analytics_events ae\n"
            + "WHERE ae.event_type IN ('" + EXAM_HUB_VIEWED_EVENT_TYPE + "', '"
            + EXAM_HUB_CTA_CLICKED_EVENT_TYPE + "')\n"
            + "  AND ae.created_at >= CAST(:since AS TIMESTAMP WITH TIME ZONE)";
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
                AND quiz.created_at <= fp.first_generated_at + INTERVAL '7' DAY
            )
            """;
    String VALUE_LOOP_CLOSURE_SINCE_QUERY = """
            SELECT COUNT(DISTINCT fp.user_id)
            FROM (
                SELECT ae.user_id, MIN(ae.created_at) AS first_generated_at
                FROM analytics_events ae
                WHERE ae.event_type = '""" + STUDY_PACK_GENERATED_EVENT_TYPE + "'\n" + """
                  AND ae.created_at >= :since
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
                AND quiz.created_at <= fp.first_generated_at + INTERVAL '7' DAY
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
    String RETENTION_RETURNED_AFTER_PREFIX = """
            EXISTS (
                SELECT 1 FROM analytics_events activity
                WHERE activity.user_id = fp.user_id
                  AND activity.created_at > fp.first_pack_at + INTERVAL '""";
    String RETENTION_RETURNED_AFTER_SUFFIX = "' DAY\n)";
    String RETENTION_RETURNED_BETWEEN_END_PREFIX = """
            ' DAY
                  AND activity.created_at <= fp.first_pack_at + INTERVAL '""";
    String RETENTION_RETURNED_BETWEEN_SUFFIX = RETENTION_RETURNED_AFTER_SUFFIX;
    String RETENTION_RETURNED_AFTER_DAY_7_EXISTS = RETENTION_RETURNED_AFTER_PREFIX
            + RETENTION_WINDOW_START_DAYS + RETENTION_RETURNED_AFTER_SUFFIX;
    String RETENTION_RETURNED_DAYS_2_TO_30_EXISTS = RETENTION_RETURNED_AFTER_PREFIX
            + EARLY_RETENTION_WINDOW_START_DAYS + RETENTION_RETURNED_BETWEEN_END_PREFIX
            + WIDE_RETENTION_WINDOW_END_DAYS + RETENTION_RETURNED_BETWEEN_SUFFIX;
    String RETENTION_RETURNED_AFTER_DAY_1_EXISTS = RETENTION_RETURNED_AFTER_PREFIX
            + EARLY_RETENTION_WINDOW_START_DAYS + RETENTION_RETURNED_AFTER_SUFFIX;
    String WIDE_RETENTION_ELIGIBILITY = "fp.first_pack_at <= CAST(:now AS TIMESTAMP WITH TIME ZONE) - INTERVAL '"
            + WIDE_RETENTION_MATURITY_DAYS + "' DAY";
    String WIDE_RETENTION_COUNT_QUERY_PREFIX = FIRST_PACK_CTE
            + "SELECT COUNT(*)\nFROM first_packs fp\nWHERE ";
    String ELIGIBLE_ACTIVATED_USERS_FOR_WIDE_RETENTION_QUERY = WIDE_RETENTION_COUNT_QUERY_PREFIX
            + WIDE_RETENTION_ELIGIBILITY + "\n";
    String RETURNED_AFTER_DAY_7_USERS_QUERY = WIDE_RETENTION_COUNT_QUERY_PREFIX
            + WIDE_RETENTION_ELIGIBILITY + "\n  AND " + RETENTION_RETURNED_AFTER_DAY_7_EXISTS + "\n";
    String RETURNED_DAYS_2_TO_30_USERS_QUERY = WIDE_RETENTION_COUNT_QUERY_PREFIX
            + WIDE_RETENTION_ELIGIBILITY + "\n  AND " + RETENTION_RETURNED_DAYS_2_TO_30_EXISTS + "\n";
    String RETURNED_AFTER_DAY_1_USERS_QUERY = WIDE_RETENTION_COUNT_QUERY_PREFIX
            + WIDE_RETENTION_ELIGIBILITY + "\n  AND " + RETENTION_RETURNED_AFTER_DAY_1_EXISTS + "\n";
    String WEEKLY_RETENTION_COHORTS_QUERY = FIRST_PACK_CTE
            + "SELECT CAST(DATE_TRUNC('week', fp.first_pack_at) AS DATE) AS weekStart,\n"
            + "       COUNT(*) AS cohortSize,\n"
            + "       SUM(CASE WHEN " + RETENTION_RETURNED_EXISTS
            + " THEN 1 ELSE 0 END) AS returnedCount,\n"
            + "       SUM(CASE WHEN " + RETENTION_RETURNED_AFTER_DAY_7_EXISTS
            + " THEN 1 ELSE 0 END) AS returnedAfterDay7Count\n"
            + "FROM first_packs fp\n"
            + "WHERE fp.first_pack_at <= CAST(:now AS TIMESTAMP WITH TIME ZONE) - INTERVAL '"
            + RETENTION_WINDOW_END_DAYS + "' DAY\n"
            + "GROUP BY CAST(DATE_TRUNC('week', fp.first_pack_at) AS DATE)\n"
            + "ORDER BY weekStart DESC\n"
            + "LIMIT " + RETENTION_COHORT_WEEK_LIMIT + "\n";

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
            where e.eventType = :eventType
              and e.createdAt >= :since
            """)
    long countDistinctUsersByEventTypeSince(
            @Param("eventType") AnalyticsEventType eventType,
            @Param("since") OffsetDateTime since
    );

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
            select count(distinct e.userId)
            from AnalyticsEventEntity e
            where e.eventType = :subscriptionStarted
              and e.createdAt >= :since
              and exists (
                  select 1
                  from AnalyticsEventEntity paywall
                  where paywall.userId = e.userId
                    and paywall.eventType = :paywallViewed
                    and paywall.createdAt >= :since
                    and paywall.createdAt <= e.createdAt
              )
            """)
    long countUsersUpgradedAfterPaywallSince(
            @Param("paywallViewed") AnalyticsEventType paywallViewed,
            @Param("subscriptionStarted") AnalyticsEventType subscriptionStarted,
            @Param("since") OffsetDateTime since
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

    @Query("""
            select count(distinct later.userId)
            from AnalyticsEventEntity later
            where later.eventType = :laterEventType
              and later.createdAt >= :since
              and exists (
                  select 1
                  from AnalyticsEventEntity earlier
                  where earlier.userId = later.userId
                    and earlier.eventType = :earlierEventType
                    and earlier.createdAt >= :since
                    and earlier.createdAt <= later.createdAt
              )
            """)
    long countDistinctUsersWithEventAfterEventSince(
            @Param("earlierEventType") AnalyticsEventType earlierEventType,
            @Param("laterEventType") AnalyticsEventType laterEventType,
            @Param("since") OffsetDateTime since
    );

    @Query(value = VALUE_LOOP_CLOSURE_QUERY, nativeQuery = true)
    long countUsersStartedQuizWithin7DaysOfFirstGeneratedPack();

    @Query(value = VALUE_LOOP_CLOSURE_SINCE_QUERY, nativeQuery = true)
    long countUsersStartedQuizWithin7DaysOfFirstGeneratedPackSince(@Param("since") OffsetDateTime since);

    @Query(value = ELIGIBLE_ACTIVATED_USERS_QUERY, nativeQuery = true)
    long countEligibleActivatedUsersForWeek2Retention(@Param("now") OffsetDateTime now);

    @Query(value = RETURNED_WEEK_2_USERS_QUERY, nativeQuery = true)
    long countReturnedWeek2Users(@Param("now") OffsetDateTime now);

    @Query(value = ELIGIBLE_ACTIVATED_USERS_FOR_WIDE_RETENTION_QUERY, nativeQuery = true)
    long countEligibleActivatedUsersForWideRetention(@Param("now") OffsetDateTime now);

    @Query(value = RETURNED_AFTER_DAY_7_USERS_QUERY, nativeQuery = true)
    long countReturnedAfterDay7Users(@Param("now") OffsetDateTime now);

    @Query(value = RETURNED_DAYS_2_TO_30_USERS_QUERY, nativeQuery = true)
    long countReturnedDays2To30Users(@Param("now") OffsetDateTime now);

    @Query(value = RETURNED_AFTER_DAY_1_USERS_QUERY, nativeQuery = true)
    long countReturnedAfterDay1Users(@Param("now") OffsetDateTime now);

    @Query(value = WEEKLY_RETENTION_COHORTS_QUERY, nativeQuery = true)
    List<WeeklyRetentionCohortProjection> findWeeklyRetentionCohorts(@Param("now") OffsetDateTime now);

    @Query(value = ORGANIC_LANDINGS_QUERY, nativeQuery = true)
    List<OrganicLandingProjection> findOrganicLandingsSince(@Param("since") OffsetDateTime since);

    @Query(value = EXAM_HUB_ORGANIC_CLICK_THROUGH_QUERY, nativeQuery = true)
    ExamHubOrganicClickThroughProjection findExamHubOrganicClickThroughSince(@Param("since") OffsetDateTime since);

    @Query(value = ONBOARDING_STEP_USER_COUNTS_QUERY, nativeQuery = true)
    List<OnboardingStepUserCountProjection> findOnboardingStepUserCounts();

    @Query(value = ONBOARDING_COMPLETION_QUERY, nativeQuery = true)
    OnboardingCompletionProjection findOnboardingCompletion();

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
