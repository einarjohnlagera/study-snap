package com.studysnap.backend.service;

import com.studysnap.backend.entity.FeedbackEntity;
import com.studysnap.backend.entity.FeedbackImageEntity;
import com.studysnap.backend.entity.FeedbackStatus;
import com.studysnap.backend.repository.FeedbackImageRepository;
import com.studysnap.backend.repository.FeedbackRepository;
import com.studysnap.backend.testutil.SqlCaptureStatementInspector;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
@Transactional
class AdminFeedbackImageReadPathIntegrationTest {
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-07-20T08:35:00Z");

    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private FeedbackImageRepository feedbackImageRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists feedback (
                    id uuid primary key,
                    user_id uuid not null,
                    email varchar(255) not null,
                    message text not null,
                    page_url text,
                    status varchar(32) not null,
                    created_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists feedback_image (
                    feedback_id uuid primary key,
                    content_type varchar(32) not null,
                    size_bytes integer not null,
                    image_bytes bytea not null,
                    created_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("delete from feedback_image");
        jdbcTemplate.execute("delete from feedback");
        SqlCaptureStatementInspector.clear();
    }

    @Test
    void recentFeedbackReadsImageExistenceWithoutSelectingImageBytes() {
        FeedbackEntity feedback = saveFeedback(UUID.randomUUID());
        FeedbackImageEntity image = new FeedbackImageEntity();
        image.setFeedbackId(feedback.getId());
        image.setContentType("image/png");
        image.setSizeBytes(8);
        image.setImageBytes(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        image.setCreatedAt(BASE_TIME);
        feedbackImageRepository.saveAndFlush(image);
        entityManager.clear();
        SqlCaptureStatementInspector.clear();

        List<FeedbackEntity> recentFeedback = feedbackRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5));
        List<UUID> imageFeedbackIds = feedbackImageRepository.findExistingFeedbackIds(
                recentFeedback.stream().map(FeedbackEntity::getId).toList()
        );

        assertThat(imageFeedbackIds).containsExactly(feedback.getId());
        List<String> selects = SqlCaptureStatementInspector.statements().stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.startsWith("select"))
                .toList();
        assertThat(selects).noneMatch(sql -> sql.contains("image_bytes"));
        assertThat(selects).anyMatch(sql -> sql.contains("feedback_image") && sql.contains("feedback_id"));
    }

    private FeedbackEntity saveFeedback(UUID userId) {
        FeedbackEntity feedback = new FeedbackEntity();
        feedback.setId(UUID.randomUUID());
        feedback.setUserId(userId);
        feedback.setEmail(userId + "@example.com");
        feedback.setMessage("The dashboard layout breaks on my phone.");
        feedback.setPageUrl("https://www.notelib.app/dashboard");
        feedback.setStatus(FeedbackStatus.NEW);
        feedback.setCreatedAt(BASE_TIME);
        return feedbackRepository.saveAndFlush(feedback);
    }
}
