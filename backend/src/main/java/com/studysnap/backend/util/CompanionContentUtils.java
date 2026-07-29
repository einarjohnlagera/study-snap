package com.studysnap.backend.util;

import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionFaqItem;
import com.studysnap.backend.dto.CompanionMentorTip;

public final class CompanionContentUtils {
    private CompanionContentUtils() {
    }

    public static boolean hasRenderableContent(CompanionContent companion) {
        if (companion == null) {
            return false;
        }
        return hasText(companion.overview())
                || hasText(companion.studyStrategy())
                || hasText(companion.commonMistakes())
                || hasText(companion.resources())
                || companion.faq() != null && companion.faq().stream().anyMatch(CompanionContentUtils::hasRenderableFaq)
                || companion.mentorTips() != null && companion.mentorTips().stream().anyMatch(CompanionContentUtils::hasRenderableTip);
    }

    private static boolean hasRenderableFaq(CompanionFaqItem item) {
        return item != null && (hasText(item.question()) || hasText(item.answer()));
    }

    private static boolean hasRenderableTip(CompanionMentorTip tip) {
        return tip != null && (hasText(tip.title()) || hasText(tip.body()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
