package com.studysnap.backend.dto;

import java.util.List;

public record GenerateCompanionRequest(
        List<CompanionSection> sections
) {
}
