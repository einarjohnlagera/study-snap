"use client";

import { THEME_OPTIONS } from "@/lib/theme-preferences";
import { useThemePreference } from "@/components/use-theme-preference";

function getThemeHelperText(selectedTheme: "system" | "light" | "dark", effectiveTheme: "light" | "dark") {
  if (selectedTheme === "system") {
    return `System follows your device setting and updates automatically. Currently using ${effectiveTheme === "dark" ? "Dark" : "Light"}.`;
  }

  return selectedTheme === "dark"
    ? "Dark mode is active across the app."
    : "Light mode is active across the app.";
}

export function ThemeSelector() {
  const {
    mounted,
    selectedTheme,
    effectiveTheme,
    savingThemePreference,
    applyThemePreference,
  } = useThemePreference();

  const helperText = getThemeHelperText(selectedTheme, effectiveTheme);

  return (
    <div className="space-y-3">
      <div className="inline-flex w-full flex-wrap gap-2 rounded-xl border border-border bg-muted/20 p-2 sm:w-auto">
        {THEME_OPTIONS.map((option) => {
          const isSelected = mounted && selectedTheme === option.mode;

          return (
            <button
              key={option.mode}
              type="button"
              className={`motion-lift min-w-[5.5rem] rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                isSelected
                  ? "bg-blue-600 text-white shadow-sm dark:bg-blue-500"
                  : "bg-background text-foreground/80 hover:bg-highlight hover:text-foreground active:bg-highlight-strong"
              }`}
              aria-pressed={isSelected}
              aria-label={`Use ${option.label} theme`}
              onClick={() => void applyThemePreference(option.mode)}
              disabled={savingThemePreference}
            >
              {option.label}
            </button>
          );
        })}
      </div>
      <p className="text-xs text-foreground/60">
        {savingThemePreference ? "Saving theme preference..." : helperText}
      </p>
    </div>
  );
}
