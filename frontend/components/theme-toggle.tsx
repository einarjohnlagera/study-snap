"use client";

import { Moon, Sun } from "lucide-react";
import { useThemePreference } from "@/components/use-theme-preference";

export function ThemeToggle() {
  const {
    mounted,
    effectiveTheme,
    applyThemePreference,
  } = useThemePreference();

  if (!mounted) {
    return (
      <button
        type="button"
        className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border cursor-pointer"
        aria-label="Toggle theme"
      >
        <Sun className="h-4 w-4" />
      </button>
    );
  }

  const isDark = effectiveTheme === "dark";

  return (
    <button
      type="button"
      onClick={() => void applyThemePreference(isDark ? "light" : "dark")}
      className="inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-lg border border-border hover:bg-muted/40"
      aria-label="Toggle theme"
    >
      {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </button>
  );
}
