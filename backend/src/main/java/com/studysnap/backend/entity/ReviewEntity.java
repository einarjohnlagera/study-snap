package com.studysnap.backend.entity;

import com.studysnap.backend.dto.QuizItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "key_concepts", nullable = false, columnDefinition = "jsonb")
	private List<String> keyConcepts;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<QuizItem> quiz;

	@Column(name = "ocr_confidence")
	private Double ocrConfidence;

	@Enumerated(EnumType.STRING)
	@Column(name = "model_tier", nullable = false)
	private ModelTier modelTier;

	@Column(name = "model_used", nullable = false, length = 64)
	private String modelUsed;

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	@Column(name = "cached_input_tokens")
	private Integer cachedInputTokens;

	@Column(name = "estimated_cost", precision = 12, scale = 6)
	private BigDecimal estimatedCost;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReviewStatus status;

	@Column(name = "error_code")
	private String errorCode;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
