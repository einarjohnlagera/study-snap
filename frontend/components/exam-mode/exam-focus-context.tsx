"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

type ExamFocusContextValue = {
  isExamFocusActive: boolean;
  setExamFocusActive: (active: boolean) => void;
  isBottomViewportClaimed: boolean;
  setBottomViewportClaimed: (active: boolean) => void;
};

const ExamFocusContext = createContext<ExamFocusContextValue>({
  isExamFocusActive: false,
  setExamFocusActive: () => {},
  isBottomViewportClaimed: false,
  setBottomViewportClaimed: () => {},
});

export function ExamFocusProvider({ children }: { children: React.ReactNode }) {
  const [isExamFocusActive, setIsExamFocusActive] = useState(false);
  const [isBottomViewportClaimed, setIsBottomViewportClaimed] = useState(false);

  const setExamFocusActive = useCallback((active: boolean) => {
    setIsExamFocusActive(active);
  }, []);

  const setBottomViewportClaimed = useCallback((active: boolean) => {
    setIsBottomViewportClaimed(active);
  }, []);

  const value = useMemo(
    () => ({
      isExamFocusActive,
      setExamFocusActive,
      isBottomViewportClaimed,
      setBottomViewportClaimed,
    }),
    [isBottomViewportClaimed, isExamFocusActive, setBottomViewportClaimed, setExamFocusActive],
  );

  return <ExamFocusContext.Provider value={value}>{children}</ExamFocusContext.Provider>;
}

export function useExamFocusContext(): ExamFocusContextValue {
  return useContext(ExamFocusContext);
}

export function useExamFocusMode(active: boolean): void {
  const { setExamFocusActive } = useExamFocusContext();

  useEffect(() => {
    if (!active) {
      return;
    }
    setExamFocusActive(true);
    return () => {
      setExamFocusActive(false);
    };
  }, [active, setExamFocusActive]);
}

/**
 * Lets an active assessment own the mobile viewport bottom without changing
 * the exam-focus behavior that controls the rest of the app chrome.
 */
export function useBottomViewportClaim(active: boolean): void {
  const { setBottomViewportClaimed } = useExamFocusContext();

  useEffect(() => {
    if (!active) {
      return;
    }
    setBottomViewportClaimed(true);
    return () => {
      setBottomViewportClaimed(false);
    };
  }, [active, setBottomViewportClaimed]);
}
