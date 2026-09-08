package com.studysnap.backend.repository;

import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionFaqItem;
import com.studysnap.backend.dto.CompanionMentorTip;
import com.studysnap.backend.dto.CompanionMentorTipAction;
import com.studysnap.backend.dto.CompanionMentorTipSurfacingCondition;
import com.studysnap.backend.dto.CompanionMentorTipSurfacingConditionType;
import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.NoteCollectionEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NoteCollectionRepositoryTest {

    @Autowired
    private NoteCollectionRepository noteCollectionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists note_collections (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    title varchar(150) not null,
                    description text,
                    visibility varchar(16) not null,
                    course_program varchar(120),
                    learner_level varchar(50),
                    estimated_study_hours integer,
                    target_completion_date date,
                    companion json,
                    companion_structure_snapshot json,
                    source_plan_id uuid,
                    source_title_at_sync varchar(150),
                    source_parent_id_at_sync uuid,
                    source_position_at_sync integer,
                    source_synced_at timestamp with time zone,
                    parent_collection_id uuid,
                    sibling_position integer,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("alter table note_collections add column if not exists learner_level varchar(50)");
        jdbcTemplate.execute("alter table note_collections add column if not exists published_at timestamp with time zone");
        jdbcTemplate.execute("alter table note_collections add column if not exists last_update_published_at timestamp with time zone");
        jdbcTemplate.execute("delete from note_collections");
    }

    @Test
    void companionJson_roundTripsMentorTipsThroughJsonColumn() {
        UUID collectionId = UUID.randomUUID();
        UUID tipId = UUID.randomUUID();
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(collectionId);
        collection.setOwnerUserId(UUID.randomUUID());
        collection.setTitle("Board Prep");
        collection.setVisibility(CollectionVisibility.PRIVATE);
        collection.setCompanion(new CompanionContent(
                "Overview",
                "Strategy",
                "Mistakes",
                null,
                List.of(new CompanionFaqItem("Question?", "Answer.")),
                List.of(new CompanionMentorTip(
                        tipId,
                        "Review the weak spots",
                        "Use one focused block for due concepts before opening new material.",
                        CompanionMentorTipAction.REVIEW_DUE_CONCEPTS,
                        new CompanionMentorTipSurfacingCondition(
                                CompanionMentorTipSurfacingConditionType.DAYS_BEFORE_TARGET_DATE,
                                14
                        )
                ))
        ));
        collection.setCreatedAt(Instant.now());
        collection.setUpdatedAt(Instant.now());

        noteCollectionRepository.saveAndFlush(collection);
        entityManager.clear();

        NoteCollectionEntity reloaded = noteCollectionRepository.findById(collectionId).orElseThrow();

        assertThat(reloaded.getCompanion()).isNotNull();
        assertThat(reloaded.getCompanion().mentorTips()).hasSize(1);
        CompanionMentorTip mentorTip = reloaded.getCompanion().mentorTips().getFirst();
        assertThat(mentorTip.id()).isEqualTo(tipId);
        assertThat(mentorTip.linkedAction()).isEqualTo(CompanionMentorTipAction.REVIEW_DUE_CONCEPTS);
        assertThat(mentorTip.surfacingCondition().type())
                .isEqualTo(CompanionMentorTipSurfacingConditionType.DAYS_BEFORE_TARGET_DATE);
        assertThat(mentorTip.surfacingCondition().threshold()).isEqualTo(14);
    }

    /**
     * The counted row is an adopter copy, whose stamp deliberately has no meaning. The source has
     * an actual unpublished child as well, so adding a published-at predicate to the adoption query
     * makes this fail instead of passing against an all-backfilled fixture.
     */
    @Test
    void adoptionCountIgnoresPublicationStampsOnAdopterRows() {
        UUID curatorId = UUID.randomUUID();
        UUID learnerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        NoteCollectionEntity source = collection(curatorId, "Official source", now);
        source.setVisibility(CollectionVisibility.PUBLIC);
        source.setPublishedAt(now);
        source = noteCollectionRepository.saveAndFlush(source);

        NoteCollectionEntity unpublishedSourceAddition = collection(curatorId, "Working subject", now);
        unpublishedSourceAddition.setParentCollectionId(source.getId());
        unpublishedSourceAddition.setPublishedAt(null);
        noteCollectionRepository.saveAndFlush(unpublishedSourceAddition);

        NoteCollectionEntity adopter = collection(learnerId, "Learner copy", now);
        adopter.setSourcePlanId(source.getId());
        adopter.setPublishedAt(null);
        noteCollectionRepository.saveAndFlush(adopter);
        entityManager.clear();

        assertThat(noteCollectionRepository.countAdoptionsByCollectionIds(List.of(source.getId())))
                .singleElement()
                .extracting(NoteCollectionAdoptionCountProjection::getCollectionId,
                        NoteCollectionAdoptionCountProjection::getAdoptionCount)
                .containsExactly(source.getId(), 1L);
    }

    private NoteCollectionEntity collection(UUID ownerId, String title, Instant now) {
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(UUID.randomUUID());
        collection.setOwnerUserId(ownerId);
        collection.setTitle(title);
        collection.setVisibility(CollectionVisibility.PRIVATE);
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);
        return collection;
    }
}
