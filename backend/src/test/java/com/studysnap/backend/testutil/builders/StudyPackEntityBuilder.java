package com.studysnap.backend.testutil.builders;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
@With
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class StudyPackEntityBuilder {
    private final UUID id;
    private final UUID ownerUserId;
    private final String title;
    private final String summary;
    private final List<QuizItem> quiz;
    private final InputType inputType;
    private final ModelTier modelTier;
    private final String modelUsed;
    private final StudyPackStatus status;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final String[] tags;

    public static StudyPackEntityBuilder aStudyPack() {
        OffsetDateTime now = OffsetDateTime.now().minusDays(1);
        return new StudyPackEntityBuilder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Study Pack",
                "Summary",
                List.of(new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Concept 1", "Explanation")),
                InputType.TEXT,
                ModelTier.FREE,
                "gpt-4.1-mini",
                StudyPackStatus.DONE,
                now,
                now,
                new String[]{"test"}
        );
    }

    public StudyPackEntityBuilder withQuizCount(int quizCount) {
        List<QuizItem> items = new ArrayList<>();
        for (int index = 0; index < quizCount; index++) {
            items.add(new QuizItem(
                    "Q" + (index + 1),
                    List.of("A", "B", "C", "D"),
                    "A",
                    "Concept " + (index + 1),
                    "Explanation"
            ));
        }
        return withQuiz(items);
    }

    public StudyPackEntity build() {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(id);
        studyPack.setOwnerUserId(ownerUserId);
        studyPack.setInputType(inputType);
        studyPack.setTitle(title);
        studyPack.setSummary(summary);
        studyPack.setQuiz(quiz);
        studyPack.setModelTier(modelTier);
        studyPack.setModelUsed(modelUsed);
        studyPack.setStatus(status);
        studyPack.setCreatedAt(createdAt);
        studyPack.setUpdatedAt(updatedAt);
        studyPack.setTags(tags);
        return studyPack;
    }
}
