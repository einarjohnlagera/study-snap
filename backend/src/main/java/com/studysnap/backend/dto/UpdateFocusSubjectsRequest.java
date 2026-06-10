package com.studysnap.backend.dto;

import java.util.List;

public record UpdateFocusSubjectsRequest(
        List<String> subjects
) {
}
