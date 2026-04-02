package com.studysnap.backend.dto;

import com.studysnap.backend.entity.ThemePreference;
import jakarta.validation.constraints.NotNull;

public record UpdateThemePreferenceRequest(
        @NotNull(message = "Theme preference is required.")
        ThemePreference themePreference
) {
}
