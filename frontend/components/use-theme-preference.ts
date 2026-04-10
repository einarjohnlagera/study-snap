"use client";

import { useState, useSyncExternalStore } from "react";
import { useTheme } from "next-themes";
import { updateThemePreference } from "@/lib/api";
import { getAuthUser, patchAuthUser } from "@/lib/auth";
import {
  normalizeThemeMode,
  themeModeToPreference,
  type ThemeMode,
} from "@/lib/theme-preferences";

function subscribeNoop() {
  return () => undefined;
}

function getClientSnapshot() {
  return true;
}

function getServerSnapshot() {
  return false;
}

export function useThemePreference() {
  const { theme, resolvedTheme, systemTheme, setTheme } = useTheme();
  const [savingThemePreference, setSavingThemePreference] = useState(false);
  const mounted = useSyncExternalStore(subscribeNoop, getClientSnapshot, getServerSnapshot);
  const selectedTheme = normalizeThemeMode(theme);
  const effectiveTheme: "light" | "dark" =
    normalizeThemeMode(resolvedTheme ?? systemTheme ?? "light") === "dark" ? "dark" : "light";

  const applyThemePreference = async (nextTheme: ThemeMode) => {
    if (savingThemePreference || selectedTheme === nextTheme) {
      return;
    }

    setTheme(nextTheme);

    const authUser = getAuthUser();
    if (!authUser) {
      return;
    }

    const nextThemePreference = themeModeToPreference(nextTheme);
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

  return {
    mounted,
    selectedTheme,
    effectiveTheme,
    savingThemePreference,
    applyThemePreference,
  };
}
