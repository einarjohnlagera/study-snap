package com.studysnap.backend.dto;

import java.util.List;

/** Aggregate labels for the existing Learning Focus sentence, independent of hydrated cards. */
public record PublicProfileFocusResponse(List<LabelCount> coursePrograms, List<LabelCount> subjects) {
    public record LabelCount(String label, long count) {
    }
}
