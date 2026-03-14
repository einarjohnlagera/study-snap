package com.studysnap.backend.dto;

import java.util.List;

public record StudyPackListPageResponse(
        List<StudyPackListItemResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
