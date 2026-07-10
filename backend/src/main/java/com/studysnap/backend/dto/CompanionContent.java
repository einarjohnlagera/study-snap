package com.studysnap.backend.dto;

import java.util.List;

public record CompanionContent(
        String overview,
        String studyStrategy,
        String commonMistakes,
        String resources,
        List<CompanionFaqItem> faq,
        List<CompanionMentorTip> mentorTips
) {
}
