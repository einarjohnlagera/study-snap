package com.studysnap.backend.service;

import java.util.Map;

public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody,
        Map<String, String> headers
) {
    public EmailMessage(String to, String subject, String htmlBody, String textBody) {
        this(to, subject, htmlBody, textBody, Map.of());
    }

    public EmailMessage {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
