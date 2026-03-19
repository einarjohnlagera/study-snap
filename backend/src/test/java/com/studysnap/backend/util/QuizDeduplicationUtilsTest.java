package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QuizDeduplicationUtilsTest {

    @Test
    void normalizeQuestion_removesPunctuationAndNormalizesCase() {
        assertThat(QuizDeduplicationUtils.normalizeQuestion("  Photosynthesis?! Basics  "))
                .isEqualTo("photosynthesis basics");
    }

    @Test
    void uniqueQuestions_filtersDuplicatesAndDisallowedQuestions() {
        List<QuizItem> generated = List.of(
                new QuizItem("What is ATP?", List.of("A", "B", "C", "D"), "A", null, "x"),
                new QuizItem("What is ATP?!", List.of("A1", "B1", "C1", "D1"), "A1", null, "x"),
                new QuizItem("Define chlorophyll.", List.of("A2", "B2", "C2", "D2"), "A2", null, "x")
        );

        Set<String> disallowed = QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(
                List.of("Define chlorophyll")
        );

        List<QuizItem> unique = QuizDeduplicationUtils.uniqueQuestions(generated, disallowed);

        assertThat(unique).hasSize(1);
        assertThat(unique.getFirst().question()).isEqualTo("What is ATP?");
    }
}
