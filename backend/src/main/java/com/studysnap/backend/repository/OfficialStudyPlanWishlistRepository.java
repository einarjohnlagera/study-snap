package com.studysnap.backend.repository;

import com.studysnap.backend.entity.OfficialStudyPlanWishlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OfficialStudyPlanWishlistRepository
        extends JpaRepository<OfficialStudyPlanWishlistEntity, UUID> {
    boolean existsByUserIdAndNormalizedCourseProgram(UUID userId, String normalizedCourseProgram);

    @Modifying
    @Query(value = """
            insert into official_study_plan_wishlist (
                id, user_id, course_program, normalized_course_program, created_at
            ) values (
                :id, :userId, :courseProgram, :normalizedCourseProgram, :createdAt
            ) on conflict (user_id, normalized_course_program) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("courseProgram") String courseProgram,
            @Param("normalizedCourseProgram") String normalizedCourseProgram,
            @Param("createdAt") OffsetDateTime createdAt
    );

    @Query(value = """
            select min(w.course_program) as courseProgram,
                   count(*) as requestCount,
                   count(distinct w.user_id) as distinctLearners
            from official_study_plan_wishlist w
            group by w.normalized_course_program
            order by count(*) desc, w.normalized_course_program asc
            """, nativeQuery = true)
    List<OfficialStudyPlanDemandProjection> findProgramDemand();
}
