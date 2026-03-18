package com.studysnap.backend.service;

public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody
) {
}
