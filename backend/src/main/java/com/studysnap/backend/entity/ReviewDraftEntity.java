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
@Table(name = "review_drafts")
@Getter
@Setter
@NoArgsConstructor
public class ReviewDraftEntity {

	@Id
	private UUID id;

	@Column(name = "owner_user_id")
	private String ownerUserId;

	@Column(name = "anon_id")
	private String anonId;

	@Column(name = "extracted_text", nullable = false)
	private String extractedText;

	@Column(name = "ocr_confidence", nullable = false)
	private Double ocrConfidence;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;
}
