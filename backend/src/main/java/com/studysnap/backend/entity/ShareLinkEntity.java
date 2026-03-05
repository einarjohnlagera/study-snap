package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "share_links")
@Getter
@Setter
@NoArgsConstructor
public class ShareLinkEntity {

	@Id
	private String token;

	@ManyToOne(optional = false)
	@JoinColumn(name = "review_id", nullable = false)
	private ReviewEntity review;

	@Column(name = "is_public", nullable = false)
	private Boolean isPublic;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "expires_at")
	private OffsetDateTime expiresAt;

	@Column(name = "view_count", nullable = false)
	private Integer viewCount;
}
