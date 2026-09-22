package com.studysnap.backend.service;

import com.studysnap.backend.dto.CampaignFeedbackStatusResponse;
import com.studysnap.backend.dto.SubmitCampaignFeedbackRequest;
import com.studysnap.backend.entity.CampaignFeedbackResponseEntity;
import com.studysnap.backend.entity.PrimaryBlocker;
import com.studysnap.backend.exception.CampaignClosedException;
import com.studysnap.backend.exception.InvalidCampaignFeedbackRequestException;
import com.studysnap.backend.repository.CampaignFeedbackResponseRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignFeedbackService {
    private static final String CAMPAIGN_ID = "STUDY_FRICTION_2026_09";
    private static final int SHORT_TEXT_MAX_LENGTH = 200;
    private static final int FREE_TEXT_MAX_LENGTH = 2000;

    private final CampaignFeedbackResponseRepository repository;
    private final TransactionOperations campaignFeedbackTransactionOperations;

    @Value("${notelib.campaign.study-friction-2026-09.closes-at}")
    private String closesAtRaw;
    private Instant closesAt;

    @PostConstruct
    void init() {
        closesAt = Instant.parse(closesAtRaw);
    }

    public CampaignFeedbackStatusResponse getStatus(UUID userId) {
        boolean submitted = repository.findByUserIdAndCampaignId(userId, CAMPAIGN_ID).isPresent();
        return new CampaignFeedbackStatusResponse(submitted, isOpen());
    }

    public void submit(UUID userId, SubmitCampaignFeedbackRequest request) {
        validate(request);

        // A prior response wins over the close boundary, including on a retry after the campaign closes.
        if (repository.findByUserIdAndCampaignId(userId, CAMPAIGN_ID).isPresent()) {
            return;
        }
        if (!isOpen()) {
            throw new CampaignClosedException();
        }

        try {
            campaignFeedbackTransactionOperations.execute(status -> {
                CampaignFeedbackResponseEntity entity = buildEntity(userId, request);
                // Flush here so a concurrent duplicate is translated and caught outside this transaction.
                return repository.saveAndFlush(entity);
            });
        } catch (DataIntegrityViolationException raceLost) {
            // The unique (user_id, campaign_id) index makes the concurrent winner authoritative.
        }
    }

    private boolean isOpen() {
        return Instant.now().isBefore(closesAt);
    }

    private void validate(SubmitCampaignFeedbackRequest request) {
        if (request == null) {
            throw new InvalidCampaignFeedbackRequestException("Feedback is required.");
        }
        List<PrimaryBlocker> blockers = request.primaryBlockers() == null
                ? List.of()
                : request.primaryBlockers();
        boolean hasFreeText = request.freeText() != null && !request.freeText().isBlank();
        if (blockers.isEmpty() && !hasFreeText) {
            throw new InvalidCampaignFeedbackRequestException(
                    "Choose at least one primaryBlocker or enter freeText."
            );
        }
        if (hasValues(request.quizIssues()) && !blockers.contains(PrimaryBlocker.QUIZ_QUALITY)) {
            throw new InvalidCampaignFeedbackRequestException("quizIssues requires QUIZ_QUALITY in primaryBlockers.");
        }
        if (request.planIssue() != null && !blockers.contains(PrimaryBlocker.PRICING)) {
            throw new InvalidCampaignFeedbackRequestException("planIssue requires PRICING in primaryBlockers.");
        }
        if (request.missingFeatureText() != null && !blockers.contains(PrimaryBlocker.MISSING_FEATURE)) {
            throw new InvalidCampaignFeedbackRequestException(
                    "missingFeatureText requires MISSING_FEATURE in primaryBlockers."
            );
        }
        if (request.contentSubjectText() != null && !blockers.contains(PrimaryBlocker.CANT_FIND_CONTENT)) {
            throw new InvalidCampaignFeedbackRequestException(
                    "contentSubjectText requires CANT_FIND_CONTENT in primaryBlockers."
            );
        }
        validateLength("missingFeatureText", request.missingFeatureText(), SHORT_TEXT_MAX_LENGTH);
        validateLength("contentSubjectText", request.contentSubjectText(), SHORT_TEXT_MAX_LENGTH);
        validateLength("freeText", request.freeText(), FREE_TEXT_MAX_LENGTH);
    }

    private CampaignFeedbackResponseEntity buildEntity(UUID userId, SubmitCampaignFeedbackRequest request) {
        CampaignFeedbackResponseEntity entity = new CampaignFeedbackResponseEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setCampaignId(CAMPAIGN_ID);
        entity.setPrimaryBlockers(toNames(request.primaryBlockers()));
        entity.setQuizIssues(toNames(request.quizIssues()));
        entity.setPlanIssue(request.planIssue() == null ? null : request.planIssue().name());
        entity.setMissingFeatureText(request.missingFeatureText());
        entity.setContentSubjectText(request.contentSubjectText());
        entity.setFreeText(request.freeText());
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    private String[] toNames(List<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return new String[0];
        }
        return values.stream().map(Enum::name).toArray(String[]::new);
    }

    private boolean hasValues(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private void validateLength(String field, String value, int maximum) {
        if (value != null && value.length() > maximum) {
            throw new InvalidCampaignFeedbackRequestException(field + " must be " + maximum + " characters or fewer.");
        }
    }
}
