package com.studysnap.backend.dto;

import java.util.List;

public record ProgressReportResponse(List<SubjectProgressEntry> subjects) {
}
