package com.studysnap.backend.service;

import com.studysnap.backend.dto.PublicShareResponse;
import com.studysnap.backend.dto.ShareLinkResponse;
import com.studysnap.backend.dto.ShareRemixResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.ShareLinkEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.repository.ShareLinkRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ShareService {
    private static final int SHARE_TOKEN_BYTES = 18;
    private static final int MAX_TOKEN_GENERATION_ATTEMPTS = 10;
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final ShareLinkRepository shareLinkRepository;
    private final StudyPackRepository studyPackRepository;
    private final ActivityTrackingService activityTrackingService;

    public ShareLinkResponse createShareLink(String studyPackId, UUID ownerUserId) {
        UUID id;
        try {
            id = UUID.fromString(studyPackId);
        } catch (IllegalArgumentException ex) {
            throw new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND);
        }

        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserIdForUpdate(id, ownerUserId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        String existingToken = normalizeToken(studyPack.getShareToken());
        if (existingToken != null) {
            return buildShareLinkResponse(existingToken);
        }

        String token = generateUniqueToken();
        studyPack.setShareToken(token);
        studyPack.setUpdatedAt(OffsetDateTime.now());
        studyPackRepository.save(studyPack);
        return buildShareLinkResponse(token);
    }

    @Transactional(readOnly = true)
    public PublicShareResponse getPublicShare(String token) {
        StudyPackEntity studyPack = resolveSharedStudyPack(token);
        return new PublicShareResponse(
                studyPack.getId().toString(),
                studyPack.getTitle(),
                studyPack.getSummary(),
                studyPack.getKeyConcepts(),
                studyPack.getQuiz()
        );
    }

    public ShareRemixResponse remixSharedStudyPack(String token, UUID ownerUserId) {
        StudyPackEntity source = resolveSharedStudyPack(token);
        StudyPackEntity remixed = new StudyPackEntity();
        remixed.setId(UUID.randomUUID());
        remixed.setOwnerUserId(ownerUserId);
        remixed.setAnonId(null);
        remixed.setInputType(source.getInputType());
        remixed.setTitle(source.getTitle());
        remixed.setSummary(source.getSummary());
        remixed.setSubject(source.getSubject());
        remixed.setKeyConcepts(copyKeyConcepts(source.getKeyConcepts()));
        remixed.setQuiz(copyQuiz(source.getQuiz()));
        remixed.setOcrConfidence(source.getOcrConfidence());
        remixed.setModelTier(source.getModelTier());
        remixed.setModelUsed(source.getModelUsed());
        remixed.setInputTokens(source.getInputTokens());
        remixed.setOutputTokens(source.getOutputTokens());
        remixed.setCachedInputTokens(source.getCachedInputTokens());
        remixed.setEstimatedCost(source.getEstimatedCost());
        remixed.setStatus(source.getStatus() == null ? StudyPackStatus.DONE : source.getStatus());
        remixed.setErrorCode(null);
        remixed.setCreatedAt(OffsetDateTime.now());
        remixed.setUpdatedAt(OffsetDateTime.now());
        remixed.setShareToken(null);
        remixed.setTags(copyTags(source.getTags()));

        StudyPackEntity saved = studyPackRepository.save(remixed);
        activityTrackingService.recordActivity(ownerUserId, ActivityType.CREATED_STUDY_PACK, saved.getId());
        return new ShareRemixResponse(saved.getId().toString());
    }

    private StudyPackEntity resolveSharedStudyPack(String token) {
        String normalizedToken = normalizeToken(token);
        if (normalizedToken == null) {
            throw new AppException("SHARE_NOT_FOUND", "Share link not found.", HttpStatus.NOT_FOUND);
        }

        return studyPackRepository.findByShareToken(normalizedToken)
                .orElseGet(() -> shareLinkRepository.findById(normalizedToken)
                        .filter(share -> Boolean.TRUE.equals(share.getIsPublic()))
                        .map(ShareLinkEntity::getStudyPack)
                        .orElseThrow(() -> new AppException(
                                "SHARE_NOT_FOUND",
                                "Share link not found.",
                                HttpStatus.NOT_FOUND
                        )));
    }

    private ShareLinkResponse buildShareLinkResponse(String token) {
        return new ShareLinkResponse(token, "/p/" + token);
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String generateUniqueToken() {
        for (int attempt = 0; attempt < MAX_TOKEN_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generateTokenCandidate();
            if (!studyPackRepository.existsByShareToken(candidate) && shareLinkRepository.findById(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new AppException(
                "SHARE_TOKEN_GENERATION_FAILED",
                "Could not generate a share token. Please try again.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private String generateTokenCandidate() {
        byte[] random = new byte[SHARE_TOKEN_BYTES];
        TOKEN_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private List<String> copyKeyConcepts(List<String> keyConcepts) {
        if (keyConcepts == null || keyConcepts.isEmpty()) {
            return List.of();
        }
        return List.copyOf(keyConcepts);
    }

    private List<QuizItem> copyQuiz(List<QuizItem> quiz) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }
        return quiz.stream()
                .map(item -> new QuizItem(
                        item.question(),
                        item.choices() == null ? List.of() : List.copyOf(item.choices()),
                        item.answer(),
                        item.concept(),
                        item.explanation()
                ))
                .toList();
    }

    private String[] copyTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return new String[0];
        }
        return Arrays.copyOf(tags, tags.length);
    }
}

