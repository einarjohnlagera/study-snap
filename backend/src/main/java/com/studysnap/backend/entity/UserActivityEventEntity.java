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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_activity_events")
@Getter
@Setter
@NoArgsConstructor
public class UserActivityEventEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "study_pack_id")
    private UUID studyPackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 64)
    private ActivityType activityType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
