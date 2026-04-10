"use client";

import { Monitor, Moon, Sun } from "lucide-react";
import { useThemePreference } from "@/components/use-theme-preference";
import {
  getThemeModeLabel,
  THEME_TOGGLE_CYCLE,
  type ThemeMode,
} from "@/lib/theme-preferences";

function getNextThemeMode(currentTheme: ThemeMode): ThemeMode {
  const currentIndex = THEME_TOGGLE_CYCLE.indexOf(currentTheme);
  const nextIndex = currentIndex === -1 ? 0 : (currentIndex + 1) % THEME_TOGGLE_CYCLE.length;
  return THEME_TOGGLE_CYCLE[nextIndex];
}

function getThemeIcon(themeMode: ThemeMode) {
  switch (themeMode) {
    case "dark":
      return <Moon className="h-4 w-4" />;
    case "light":
      return <Sun className="h-4 w-4" />;
    default:
      return <Monitor className="h-4 w-4" />;
  }
}

export function ThemeToggle() {
  const {
    mounted,
    selectedTheme,
    applyThemePreference,
  } = useThemePreference();

  const currentTheme = mounted ? selectedTheme : "system";
  const themeLabel = getThemeModeLabel(currentTheme);
  const tooltipLabel = `Theme: ${themeLabel}`;

  if (!mounted) {
    return (
      <button
        type="button"
        className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border cursor-pointer"
        aria-label={tooltipLabel}
        title={tooltipLabel}
      >
        {getThemeIcon(currentTheme)}
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={() => void applyThemePreference(getNextThemeMode(currentTheme))}
      className="inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-lg border border-border hover:bg-muted/40"
      aria-label={tooltipLabel}
      title={tooltipLabel}
    >
      {getThemeIcon(currentTheme)}
    </button>
  );
}
