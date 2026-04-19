"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { usePathname, useSearchParams } from "next/navigation";

const INITIAL_PROGRESS = 0;
const START_PROGRESS = 18;
const MID_PROGRESS = 56;
const LATE_PROGRESS = 78;
const COMPLETE_PROGRESS = 100;
const COMPLETE_HIDE_DELAY_MS = 180;
const MID_PROGRESS_DELAY_MS = 90;
const LATE_PROGRESS_DELAY_MS = 420;
const STALL_RESET_DELAY_MS = 8000;

const RouteProgressContext = createContext<() => void>(() => {});

function isTrackableInternalLink(anchor: HTMLAnchorElement) {
  const rawHref = anchor.getAttribute("href");
  if (!rawHref || rawHref.startsWith("#") || rawHref.startsWith("mailto:") || rawHref.startsWith("tel:")) {
    return false;
  }
  if (anchor.target && anchor.target !== "_self") {
    return false;
  }
  if (anchor.hasAttribute("download")) {
    return false;
  }

  const destination = new URL(anchor.href, globalThis.location.href);
  const current = new URL(globalThis.location.href);
  if (destination.origin !== current.origin) {
    return false;
  }

  return destination.href !== current.href;
}

export function RouteProgressProvider({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const routeKey = `${pathname}?${searchParams.toString()}`;
  const [visible, setVisible] = useState(false);
  const [progress, setProgress] = useState(INITIAL_PROGRESS);
  const hasMountedRef = useRef(false);
  const stallResetTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const stageTimersRef = useRef<ReturnType<typeof setTimeout>[]>([]);

  const clearTimers = useCallback(() => {
    if (stallResetTimerRef.current) {
      clearTimeout(stallResetTimerRef.current);
      stallResetTimerRef.current = null;
    }
    stageTimersRef.current.forEach((timerId) => clearTimeout(timerId));
    stageTimersRef.current = [];
  }, []);

  const finishProgress = useCallback(() => {
    clearTimers();
    setProgress(COMPLETE_PROGRESS);
    const finishTimer = setTimeout(() => {
      setVisible(false);
      setProgress(INITIAL_PROGRESS);
    }, COMPLETE_HIDE_DELAY_MS);
    stageTimersRef.current = [finishTimer];
  }, [clearTimers]);

  const startProgress = useCallback(() => {
    clearTimers();
    setVisible(true);
    setProgress((current) => (Math.max(current, START_PROGRESS)));
    stageTimersRef.current = [
      setTimeout(() => setProgress((current) => (Math.max(current, MID_PROGRESS))), MID_PROGRESS_DELAY_MS),
      setTimeout(() => setProgress((current) => (Math.max(current, LATE_PROGRESS))), LATE_PROGRESS_DELAY_MS),
    ];
    stallResetTimerRef.current = setTimeout(() => {
      setVisible(false);
      setProgress(INITIAL_PROGRESS);
    }, STALL_RESET_DELAY_MS);
  }, [clearTimers]);

  useEffect(() => {
    if (!hasMountedRef.current) {
      hasMountedRef.current = true;
      return;
    }
    if (!visible) {
      return;
    }
    finishProgress();
  }, [finishProgress, routeKey, visible]);

  useEffect(() => {
    const handleClickCapture = (event: MouseEvent) => {
      if (
        event.defaultPrevented
        || event.button !== 0
        || event.metaKey
        || event.ctrlKey
        || event.shiftKey
        || event.altKey
      ) {
        return;
      }
      const target = event.target as HTMLElement | null;
      const anchor = target?.closest("a");
      if (!anchor || !(anchor instanceof HTMLAnchorElement) || !isTrackableInternalLink(anchor)) {
        return;
      }
      startProgress();
    };

    globalThis.document.addEventListener("click", handleClickCapture, true);
    return () => {
      globalThis.document.removeEventListener("click", handleClickCapture, true);
    };
  }, [startProgress]);

  useEffect(() => clearTimers, [clearTimers]);

  const contextValue = useMemo(() => startProgress, [startProgress]);

  return (
    <RouteProgressContext.Provider value={contextValue}>
      <div
        aria-hidden="true"
        className={`pointer-events-none fixed inset-x-0 top-0 z-100 h-0.5 origin-left bg-blue-600 shadow-[0_0_12px_rgba(37,99,235,0.45)] transition-opacity duration-150 dark:bg-blue-400 ${
          visible ? "opacity-100" : "opacity-0"
        }`}
        style={{ transform: `scaleX(${progress / 100})` }}
      />
      {children}
    </RouteProgressContext.Provider>
  );
}

export function useRouteProgress() {
  return useContext(RouteProgressContext);
}
