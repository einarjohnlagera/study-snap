package com.studysnap.backend.service;

import com.studysnap.backend.dto.PublicShareResponse;
import com.studysnap.backend.dto.ShareLinkResponse;
import com.studysnap.backend.entity.ReviewEntity;
import com.studysnap.backend.entity.ShareLinkEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.ReviewRepository;
import com.studysnap.backend.repository.ShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ShareService {
	private final ShareLinkRepository shareLinkRepository;
	private final ReviewRepository reviewRepository;

	public ShareLinkResponse createShareLink(String reviewId) {
		UUID id;
		try {
			id = UUID.fromString(reviewId);
		} catch (IllegalArgumentException ex) {
			throw new AppException("REVIEW_NOT_FOUND", "Review not found.", HttpStatus.NOT_FOUND);
		}

		ReviewEntity review = reviewRepository.findById(id)
				.orElseThrow(() -> new AppException("REVIEW_NOT_FOUND", "Review not found.", HttpStatus.NOT_FOUND));

		ShareLinkEntity share = new ShareLinkEntity();
		String token = generateToken();
		share.setToken(token);
		share.setReview(review);
		share.setIsPublic(true);
		share.setCreatedAt(OffsetDateTime.now());
		share.setViewCount(0);
		shareLinkRepository.save(share);

		return new ShareLinkResponse(token, "/share/" + token);
	}

	@Transactional(readOnly = true)
	public PublicShareResponse getPublicShare(String token) {
		ShareLinkEntity share = shareLinkRepository.findById(token)
				.orElseThrow(() -> new AppException("SHARE_NOT_FOUND", "Share link not found.", HttpStatus.NOT_FOUND));

		if (!Boolean.TRUE.equals(share.getIsPublic())) {
			throw new AppException("SHARE_NOT_FOUND", "Share link not found.", HttpStatus.NOT_FOUND);
		}

		ReviewEntity review = share.getReview();
		return new PublicShareResponse(
				review.getId().toString(),
				review.getTitle(),
				review.getSummary(),
				review.getKeyConcepts(),
				review.getQuiz()
		);
	}

	private String generateToken() {
		byte[] random = UUID.randomUUID().toString().getBytes();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(random).substring(0, 22);
	}
}
