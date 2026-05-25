package com.studysnap.backend.dto;

import java.util.List;

public record PublicQuizItem(
        String question,
        List<String> choices,
        String concept
) {
}
