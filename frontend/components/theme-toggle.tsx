"use client";

import { Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { useState, useSyncExternalStore } from "react";
import { updateThemePreference } from "@/lib/api";
import { getAuthUser, patchAuthUser } from "@/lib/auth";
import { themeModeToPreference } from "@/lib/theme-preferences";

export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  const [savingThemePreference, setSavingThemePreference] = useState(false);
  const mounted = useSyncExternalStore(
    () => () => undefined,
    () => true,
    () => false,
  );

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

  const isDark = resolvedTheme === "dark";

  const handleToggleTheme = async () => {
    const nextTheme = isDark ? "light" : "dark";
    const nextThemePreference = themeModeToPreference(nextTheme);

    setTheme(nextTheme);

    const authUser = getAuthUser();
    if (!authUser || savingThemePreference) {
      return;
    }

    patchAuthUser({ themePreference: nextThemePreference });
    setSavingThemePreference(true);
    try {
      await updateThemePreference({ themePreference: nextThemePreference });
    } catch {
      // Theme changes should remain instant even if preference sync fails.
    } finally {
      setSavingThemePreference(false);
    }
  };

  return (
    <button
      type="button"
      onClick={() => void handleToggleTheme()}
      className="inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-lg border border-border hover:bg-muted/40"
      aria-label="Toggle theme"
    >
      {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </button>
  );
}
