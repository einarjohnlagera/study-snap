"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

type ExamFocusContextValue = {
  isExamFocusActive: boolean;
  setExamFocusActive: (active: boolean) => void;
};

const ExamFocusContext = createContext<ExamFocusContextValue>({
  isExamFocusActive: false,
  setExamFocusActive: () => {},
});

export function ExamFocusProvider({ children }: { children: React.ReactNode }) {
  const [isExamFocusActive, setIsExamFocusActive] = useState(false);

  const setExamFocusActive = useCallback((active: boolean) => {
    setIsExamFocusActive(active);
  }, []);

  const value = useMemo(
    () => ({ isExamFocusActive, setExamFocusActive }),
    [isExamFocusActive, setExamFocusActive],
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
