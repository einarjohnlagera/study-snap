"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Image from "next/image";
import { Share, X } from "lucide-react";
import { Button } from "@/components/ui/button";

type BeforeInstallPromptOutcome = {
  outcome: "accepted" | "dismissed";
  platform: string;
};

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<BeforeInstallPromptOutcome>;
};

type StandaloneNavigator = Navigator & {
  standalone?: boolean;
};

type NudgeVariant = "android" | "ios";

const DISMISSED_STORAGE_KEY = "pwa-nudge-dismissed";
const VISIT_COUNT_STORAGE_KEY = "pwa-nudge-visit-count";
const SESSION_COUNTED_STORAGE_KEY = "pwa-nudge-session-counted";
const MOBILE_BREAKPOINT_PX = 768;

function isStandaloneMode() {
  return globalThis.matchMedia("(display-mode: standalone)").matches
    || Boolean((navigator as StandaloneNavigator).standalone);
}

function isMobileViewport() {
  return navigator.maxTouchPoints > 0 && globalThis.innerWidth < MOBILE_BREAKPOINT_PX;
}

function isIosSafariCandidate() {
  return /iPhone|iPad/.test(navigator.userAgent);
}

function getVisitCount() {
  const rawValue = globalThis.localStorage.getItem(VISIT_COUNT_STORAGE_KEY);
  const parsedValue = Number.parseInt(rawValue ?? "0", 10);
  return Number.isFinite(parsedValue) ? parsedValue : 0;
}

function incrementVisitCountOncePerSession() {
  if (globalThis.sessionStorage.getItem(SESSION_COUNTED_STORAGE_KEY) === "true") {
    return getVisitCount();
  }

  const nextVisitCount = getVisitCount() + 1;
  globalThis.localStorage.setItem(VISIT_COUNT_STORAGE_KEY, String(nextVisitCount));
  globalThis.sessionStorage.setItem(SESSION_COUNTED_STORAGE_KEY, "true");
  return nextVisitCount;
}

export function AddToHomeScreenNudge() {
  const deferredPromptRef = useRef<BeforeInstallPromptEvent | null>(null);
  const [variant, setVariant] = useState<NudgeVariant | null>(null);

  const dismiss = useCallback(() => {
    globalThis.localStorage.setItem(DISMISSED_STORAGE_KEY, "true");
    deferredPromptRef.current = null;
    setVariant(null);
  }, []);

  const shouldAllowNudge = useCallback(() => {
    if (globalThis.localStorage.getItem(DISMISSED_STORAGE_KEY) === "true") {
      return false;
    }
    if (!isMobileViewport() || isStandaloneMode()) {
      return false;
    }
    return getVisitCount() >= 2;
  }, []);

  useEffect(() => {
    const visitCount = incrementVisitCountOncePerSession();
    let iosPromptTimer: ReturnType<typeof globalThis.setTimeout> | null = null;
    if (
      visitCount >= 2
      && shouldAllowNudge()
      && isIosSafariCandidate()
    ) {
      iosPromptTimer = globalThis.setTimeout(() => setVariant("ios"), 0);
    }

    const handleBeforeInstallPrompt = (event: Event) => {
      event.preventDefault();
      deferredPromptRef.current = event as BeforeInstallPromptEvent;
      if (shouldAllowNudge()) {
        setVariant("android");
      }
    };

    globalThis.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
    return () => {
      if (iosPromptTimer !== null) {
        globalThis.clearTimeout(iosPromptTimer);
      }
      globalThis.removeEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
    };
  }, [shouldAllowNudge]);

  const handleInstall = async () => {
    const promptEvent = deferredPromptRef.current;
    if (!promptEvent) {
      return;
    }

    await promptEvent.prompt();
    await promptEvent.userChoice;
    globalThis.localStorage.setItem(DISMISSED_STORAGE_KEY, "true");
    deferredPromptRef.current = null;
    setVariant(null);
  };

  if (!variant) {
    return null;
  }

  const isAndroidPrompt = variant === "android";

  return (
    <div className="fixed inset-x-3 bottom-[calc(env(safe-area-inset-bottom)+0.75rem)] z-50 mx-auto flex max-w-md items-center gap-3 rounded-xl border border-border bg-background/95 p-3 text-foreground shadow-lg backdrop-blur sm:hidden">
      <Image
        src="/notelib-logo-monogram.png"
        alt=""
        width={36}
        height={36}
        className="h-9 w-9 shrink-0 rounded-lg"
        aria-hidden="true"
      />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium">Add NoteLib to your phone</p>
        <p className="mt-0.5 text-xs text-foreground/65">
          {isAndroidPrompt
            ? "Install it for quick access from your home screen."
            : "Tap Share, then Add to Home Screen."}
        </p>
      </div>
      {isAndroidPrompt ? (
        <Button type="button" size="sm" onClick={() => void handleInstall()}>
          Install App
        </Button>
      ) : (
        <Button type="button" size="sm" variant="outline" onClick={dismiss}>
          <Share className="h-4 w-4" aria-hidden="true" />
          Got It
        </Button>
      )}
      <button
        type="button"
        className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-foreground/60 hover:bg-muted/50 hover:text-foreground"
        aria-label="Dismiss install prompt"
        onClick={dismiss}
      >
        <X className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}
