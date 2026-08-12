package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "official_study_plan_wishlist")
@Getter
@Setter
@NoArgsConstructor
public class OfficialStudyPlanWishlistEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_program", nullable = false, length = 120)
    private String courseProgram;

    @Column(name = "normalized_course_program", nullable = false, length = 120)
    private String normalizedCourseProgram;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
