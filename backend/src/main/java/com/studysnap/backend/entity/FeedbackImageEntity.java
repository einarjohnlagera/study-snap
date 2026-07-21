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
@Table(name = "feedback_image")
@Getter
@Setter
@NoArgsConstructor
public class FeedbackImageEntity {
    @Id
    @Column(name = "feedback_id")
    private UUID feedbackId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "image_bytes", nullable = false, columnDefinition = "bytea")
    private byte[] imageBytes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
