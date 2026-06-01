package com.studysnap.backend.repository;

import com.studysnap.backend.dto.AdminPublicNoteMetricItemResponse;
import com.studysnap.backend.entity.AnalyticsEventEntity;
import com.studysnap.backend.entity.AnalyticsEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(value = VALUE_LOOP_CLOSURE_QUERY, nativeQuery = true)
    long countUsersStartedQuizWithin7DaysOfFirstGeneratedPack();

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
