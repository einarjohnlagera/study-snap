package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCampaignFeedbackRequestException extends AppException {
    public InvalidCampaignFeedbackRequestException(String message) {
        super("INVALID_CAMPAIGN_FEEDBACK", message, HttpStatus.BAD_REQUEST);
    }
}
