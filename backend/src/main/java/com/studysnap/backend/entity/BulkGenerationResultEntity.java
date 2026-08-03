package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bulk_generation_result")
@Getter
@Setter
@NoArgsConstructor
public class BulkGenerationResultEntity {
    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false)
    private String subject;

    @Column(name = "course_program")
    private String courseProgram;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_context", length = 64)
    private DomainContext domainContext;

    @Enumerated(EnumType.STRING)
    @Column(name = "learner_level", length = 32)
    private LearnerLevel learnerLevel;

    @Column(name = "target_profile_type", nullable = false)
    private String targetProfileType;

    @Column(name = "make_public", nullable = false)
    private Boolean makePublic;

    @Column(name = "requested_count", nullable = false)
    private Integer requestedCount;

    @Column(name = "created_count", nullable = false)
    private Integer createdCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failed_topics", nullable = false, columnDefinition = "jsonb")
    private List<String> failedTopics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quota_blocked_topics", nullable = false, columnDefinition = "jsonb")
    private List<String> quotaBlockedTopics;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
