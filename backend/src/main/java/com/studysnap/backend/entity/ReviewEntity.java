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
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class ReviewEntity {

	@Id
	private UUID id;

	@Column(name = "owner_user_id")
	private String ownerUserId;

	@Column(name = "anon_id")
	private String anonId;

	@Enumerated(EnumType.STRING)
	@Column(name = "input_type", nullable = false)
	private InputType inputType;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String summary;

	@Column(name = "key_concepts", nullable = false, columnDefinition = "jsonb")
	private String keyConcepts;

	@Column(nullable = false, columnDefinition = "jsonb")
	private String quiz;

	@Column(name = "ocr_confidence")
	private Double ocrConfidence;

	@Enumerated(EnumType.STRING)
	@Column(name = "model_tier", nullable = false)
	private ModelTier modelTier;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReviewStatus status;

	@Column(name = "error_code")
	private String errorCode;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
