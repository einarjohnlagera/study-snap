"use client";

import { useEffect } from "react";
import { useTheme } from "next-themes";
import { getAuthUser } from "@/lib/auth";
import { themePreferenceToMode } from "@/lib/theme-preferences";

export function ThemePreferenceSync() {
  const { setTheme } = useTheme();

  useEffect(() => {
    const syncThemePreference = () => {
      const authUser = getAuthUser();
      if (!authUser?.themePreference) {
        return;
      }
      setTheme(themePreferenceToMode(authUser.themePreference));
    };

    syncThemePreference();
    globalThis.addEventListener("studysnap-auth-change", syncThemePreference);
    globalThis.addEventListener("storage", syncThemePreference);

    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncThemePreference);
      globalThis.removeEventListener("storage", syncThemePreference);
    };
  }, [setTheme]);

  return null;
}
