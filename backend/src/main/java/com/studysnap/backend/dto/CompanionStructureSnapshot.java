package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record CompanionStructureSnapshot(
        int memberCount,
        List<UUID> memberIds
) {
}
