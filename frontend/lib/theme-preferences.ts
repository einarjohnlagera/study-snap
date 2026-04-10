"use client";

export type ThemePreference = "SYSTEM" | "LIGHT" | "DARK";
export type ThemeMode = "system" | "light" | "dark";

export const THEME_OPTIONS: ReadonlyArray<{
  mode: ThemeMode;
  label: string;
}> = [
  { mode: "light", label: "Light" },
  { mode: "dark", label: "Dark" },
  { mode: "system", label: "System" },
];

export const THEME_CLASS_NAME_BY_MODE = {
  light: "theme-light",
  dark: "theme-dark",
} as const;

export const THEME_TRANSITION_DURATION_MS = 220;

export function normalizeThemeMode(themeMode: string | null | undefined): ThemeMode {
  if (themeMode === "light" || themeMode === "dark") {
    return themeMode;
  }
  return "system";
}

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
