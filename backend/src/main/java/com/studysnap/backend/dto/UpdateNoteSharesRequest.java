package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateNoteSharesRequest(
        @NotNull List<UUID> relationshipIds
) {
}
