package com.studysnap.backend.dto;

import java.util.List;

public record NotesLibraryIdsResponse(List<String> noteIds, long totalMatching, boolean truncated) {
}
