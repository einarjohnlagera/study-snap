"use client";

export type ThemePreference = "SYSTEM" | "LIGHT" | "DARK";
export type ThemeMode = "system" | "light" | "dark";

export function themePreferenceToMode(themePreference: ThemePreference | null | undefined): ThemeMode {
  switch (themePreference) {
    case "LIGHT":
      return "light";
    case "DARK":
      return "dark";
    default:
      return "system";
  }
}

export function themeModeToPreference(themeMode: ThemeMode): ThemePreference {
  switch (themeMode) {
    case "light":
      return "LIGHT";
    case "dark":
      return "DARK";
    default:
      return "SYSTEM";
  }
}
