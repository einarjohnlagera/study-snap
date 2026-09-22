package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaign_feedback_responses")
@Getter
@Setter
@NoArgsConstructor
public class CampaignFeedbackResponseEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "campaign_id", nullable = false, length = 64)
    private String campaignId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "primary_blockers", nullable = false, columnDefinition = "text[]")
    private String[] primaryBlockers = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "quiz_issues", columnDefinition = "text[]")
    private String[] quizIssues = new String[0];

    @Column(name = "plan_issue", length = 64)
    private String planIssue;

    @Column(name = "missing_feature_text", length = 200)
    private String missingFeatureText;

    @Column(name = "content_subject_text", length = 200)
    private String contentSubjectText;

    @Column(name = "free_text", columnDefinition = "text")
    private String freeText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
