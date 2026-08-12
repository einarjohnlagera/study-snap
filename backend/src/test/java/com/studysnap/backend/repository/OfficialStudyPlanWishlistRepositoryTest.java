package com.studysnap.backend.repository;

import com.studysnap.backend.entity.OfficialStudyPlanWishlistEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OfficialStudyPlanWishlistRepositoryTest {
    private static final String NURSING = "Nursing";
    private static final String NORMALIZED_NURSING = "nursing";
    private static final String ACCOUNTANCY = "Accountancy";

    @Autowired
    private OfficialStudyPlanWishlistRepository wishlistRepository;

    @Test
    void findProgramDemand_groupsByNormalizedProgramAndOrdersByRequestCount() {
        UUID firstLearner = UUID.randomUUID();
        save(firstLearner, NURSING, NORMALIZED_NURSING);
        save(UUID.randomUUID(), NORMALIZED_NURSING, NORMALIZED_NURSING);
        save(UUID.randomUUID(), " " + NURSING + " ", NORMALIZED_NURSING);
        save(firstLearner, ACCOUNTANCY, "accountancy");

        List<OfficialStudyPlanDemandProjection> demand = wishlistRepository.findProgramDemand();

        assertThat(demand).hasSize(2);
        assertThat(demand.getFirst().getCourseProgram()).isEqualToIgnoringCase(NORMALIZED_NURSING);
        assertThat(demand.getFirst().getRequestCount()).isEqualTo(3);
        assertThat(demand.getLast().getCourseProgram()).isEqualTo(ACCOUNTANCY);
        assertThat(demand.getLast().getRequestCount()).isEqualTo(1);
    }

    private void save(UUID userId, String courseProgram, String normalizedCourseProgram) {
        OfficialStudyPlanWishlistEntity entity = new OfficialStudyPlanWishlistEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setCourseProgram(courseProgram.trim());
        entity.setNormalizedCourseProgram(normalizedCourseProgram);
        entity.setCreatedAt(OffsetDateTime.now());
        wishlistRepository.save(entity);
    }
}
