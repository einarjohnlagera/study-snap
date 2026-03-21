package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.dto.QuickReviewStudyTipRequest;
import com.studysnap.backend.dto.QuickReviewStudyTipResponse;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuickReviewStudyTipService {
    private static final Logger log = LoggerFactory.getLogger(QuickReviewStudyTipService.class);

    private final StudySnapProperties properties;
    private final StudyPackRepository studyPackRepository;
    private final LlmStudyPackService llmStudyPackService;
    private final AuthService authService;

    public QuickReviewStudyTipResponse generateStudyTip(
            String studyPackIdRaw,
            UUID userId,
            QuickReviewStudyTipRequest request
    ) {
        authService.requireEmailVerified(userId);
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );

        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        if (!properties.getQuickReview().getStudyTip().isEnabled()) {
            return new QuickReviewStudyTipResponse(null);
        }

        if (request == null || request.incorrectQuestions() == null || request.incorrectQuestions().isEmpty()) {
            return new QuickReviewStudyTipResponse(null);
        }

        int minIncorrectCount = Math.max(1, properties.getQuickReview().getStudyTip().getMinIncorrectCount());
        if (request.incorrectQuestions().size() < minIncorrectCount) {
            return new QuickReviewStudyTipResponse(null);
        }

        int maxQuestions = Math.max(1, properties.getQuickReview().getStudyTip().getMaxQuestions());
        List<String> incorrectQuestionSummaries = request.incorrectQuestions().stream()
                .filter(this::hasUsableContent)
                .limit(maxQuestions)
                .map(this::buildIncorrectQuestionSummary)
                .toList();
        if (incorrectQuestionSummaries.isEmpty()) {
            return new QuickReviewStudyTipResponse(null);
        }

        try {
            String tip = llmStudyPackService.generateQuickReviewStudyTip(incorrectQuestionSummaries);
            return new QuickReviewStudyTipResponse(tip);
        } catch (Exception ex) {
            log.warn(
                    "quick_review_study_tip_generation_failed userId={} studyPackId={} errorCode={} message={}",
                    userId,
                    studyPackId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            return new QuickReviewStudyTipResponse(null);
        }
    }

    private String buildIncorrectQuestionSummary(QuickReviewIncorrectQuestionRequest item) {
        return "Question: " + item.question().trim()
                + " | Correct answer: " + item.correctAnswer().trim()
                + " | Explanation: " + item.explanation().trim();
    }

    private boolean hasUsableContent(QuickReviewIncorrectQuestionRequest item) {
        if (item == null) {
            return false;
        }
        return Stream.of(item.question(), item.correctAnswer(), item.explanation())
                .allMatch(value -> value != null && !value.trim().isEmpty());
    }
}
