package com.studysnap.backend.dto;

import java.util.List;

public record NotesLibraryFilterOptionsResponse(
        List<FacetCount> subjects,
        List<FacetCount> coursePrograms,
        List<FacetCount> tags
) {
}
