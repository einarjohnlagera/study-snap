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
    long countByEventType(AnalyticsEventType eventType);

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
}
