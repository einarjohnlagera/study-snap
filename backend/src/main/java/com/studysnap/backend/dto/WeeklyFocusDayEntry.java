package com.studysnap.backend.dto;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public record WeeklyFocusDayEntry(
        DayOfWeek dayOfWeek,
        List<UUID> collectionIds
) {
}
