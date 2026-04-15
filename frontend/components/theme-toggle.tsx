"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { Monitor, Moon, Smartphone, Sun } from "lucide-react";
import { useThemePreference } from "@/components/use-theme-preference";
import {
  getThemeModeLabel,
  THEME_OPTIONS,
  type ThemeMode,
} from "@/lib/theme-preferences";
import { cn } from "@/lib/utils";

function getThemeIcon(themeMode: ThemeMode, isMobileVariant: boolean): ReactNode {
  switch (themeMode) {
    case "dark":
      return <Moon className="h-3.5 w-3.5" aria-hidden="true" />;
    case "light":
      return <Sun className="h-3.5 w-3.5" aria-hidden="true" />;
    default:
      return isMobileVariant
        ? <Smartphone className="h-3.5 w-3.5" aria-hidden="true" />
        : <Monitor className="h-3.5 w-3.5" aria-hidden="true" />;
  }
}

function DesktopThemeButton({
  mode,
  isSelected,
  disabled,
  onSelect,
}: Readonly<{
  mode: ThemeMode;
  isSelected: boolean;
  disabled: boolean;
  onSelect: (mode: ThemeMode) => void;
}>) {
  const label = getThemeModeLabel(mode);

  return (
    <button
      type="button"
      aria-label={`Use ${label} theme`}
      aria-pressed={isSelected}
      disabled={disabled}
      title={label}
      className={cn(
        "inline-flex h-7 w-7 items-center justify-center rounded-full transition-colors",
        isSelected
          ? "bg-blue-600 text-white shadow-sm dark:bg-blue-500"
          : "text-foreground/70 hover:bg-highlight hover:text-foreground",
      )}
      onClick={() => onSelect(mode)}
    >
      {getThemeIcon(mode, false)}
    </button>
  );
}

function MobileThemeButton({
  mode,
  isSelected,
  disabled,
  expanded,
  onSelect,
}: Readonly<{
  mode: ThemeMode;
  isSelected: boolean;
  disabled: boolean;
  expanded: boolean;
  onSelect: (mode: ThemeMode) => void;
}>) {
  const label = getThemeModeLabel(mode);

  return (
    <button
      type="button"
      aria-label={`Use ${label} theme`}
      aria-pressed={isSelected}
      disabled={disabled}
      tabIndex={expanded ? 0 : -1}
      title={label}
      className={cn(
        "inline-flex h-8 w-8 items-center justify-center rounded-lg transition-colors",
        isSelected
          ? "bg-blue-600 text-white shadow-sm dark:bg-blue-500"
          : "text-foreground/80 hover:bg-highlight hover:text-foreground",
      )}
      onClick={() => onSelect(mode)}
    >
      <span
        className={cn(
          "inline-flex h-6 w-6 items-center justify-center rounded-full border",
          isSelected
            ? "border-white/30 bg-white/10"
            : "border-border bg-background text-foreground/75",
        )}
      >
        {getThemeIcon(mode, true)}
      </span>
    </button>
  );
}

export function ThemeToggle() {
  const {
    mounted,
    selectedTheme,
    savingThemePreference,
    applyThemePreference,
  } = useThemePreference();
  const [mobileExpanded, setMobileExpanded] = useState(false);
  const mobileControlRef = useRef<HTMLDivElement | null>(null);

  const currentTheme = mounted ? selectedTheme : "system";
  const themeLabel = getThemeModeLabel(currentTheme);
  const tooltipLabel = `Theme: ${themeLabel}`;
  const controlsDisabled = !mounted || savingThemePreference;

  useEffect(() => {
    if (!mobileExpanded) {
      return;
    }

    const handlePointerDown = (event: MouseEvent) => {
      if (!mobileControlRef.current?.contains(event.target as Node)) {
        setMobileExpanded(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMobileExpanded(false);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [mobileExpanded]);

  const handleSelectTheme = async (mode: ThemeMode) => {
    await applyThemePreference(mode);
    setMobileExpanded(false);
  };

  return (
    <>
      <div
        className="hidden items-center gap-0.5 rounded-full border border-border bg-background/80 p-1 shadow-sm backdrop-blur md:inline-flex"
        role="group"
        aria-label="Theme"
      >
        {THEME_OPTIONS.map((option) => (
          <DesktopThemeButton
            key={option.mode}
            mode={option.mode}
            isSelected={currentTheme === option.mode}
            disabled={controlsDisabled}
            onSelect={(mode) => {
              void handleSelectTheme(mode);
            }}
          />
        ))}
      </div>

      <div ref={mobileControlRef} className="relative md:hidden">
        <button
          type="button"
          className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border bg-background/85 transition-colors hover:bg-highlight"
          aria-label={tooltipLabel}
          aria-expanded={mobileExpanded}
          title={tooltipLabel}
          onClick={() => setMobileExpanded((expanded) => !expanded)}
          disabled={controlsDisabled}
        >
          {getThemeIcon(currentTheme, true)}
        </button>

        <div
          aria-hidden={!mobileExpanded}
          className={cn(
            "absolute right-0 top-[calc(100%+0.375rem)] z-40 rounded-xl border border-border bg-background/95 p-1.5 shadow-lg backdrop-blur transition-[opacity,transform] duration-200 ease-out",
            mobileExpanded
              ? "translate-y-0 opacity-100"
              : "pointer-events-none -translate-y-1 opacity-0",
          )}
        >
          <div className="flex items-center gap-1">
            {THEME_OPTIONS.map((option) => (
              <MobileThemeButton
                key={option.mode}
                mode={option.mode}
                isSelected={currentTheme === option.mode}
                disabled={controlsDisabled}
                expanded={mobileExpanded}
                onSelect={(mode) => {
                  void handleSelectTheme(mode);
                }}
              />
            ))}
          </div>
        </div>
      </div>
    </>
  );
}
