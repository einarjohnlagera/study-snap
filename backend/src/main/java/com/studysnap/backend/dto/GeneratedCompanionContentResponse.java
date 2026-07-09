package com.studysnap.backend.dto;

import java.util.List;

public record GeneratedCompanionContentResponse(
        String overview,
        String studyStrategy,
        String commonMistakes,
        List<CompanionFaqItem> faq
) {
    public static GeneratedCompanionContentResponse from(CompanionContent content) {
        if (content == null) {
            return new GeneratedCompanionContentResponse(null, null, null, List.of());
        }
        return new GeneratedCompanionContentResponse(
                content.overview(),
                content.studyStrategy(),
                content.commonMistakes(),
                content.faq() == null ? List.of() : content.faq()
        );
    }
}
