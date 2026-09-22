package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CampaignClosedException extends AppException {
    public CampaignClosedException() {
        super("CAMPAIGN_CLOSED", "This feedback campaign has ended.", HttpStatus.CONFLICT);
    }
}
