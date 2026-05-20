"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

type AppShellTitleContextValue = {
  titleOverride: string | null;
  setTitleOverride: (title: string | null) => void;
};

const AppShellTitleContext = createContext<AppShellTitleContextValue>({
  titleOverride: null,
  setTitleOverride: () => {},
});

export function AppShellTitleProvider({ children }: { children: React.ReactNode }) {
  const [titleOverride, setTitleOverrideState] = useState<string | null>(null);

  const setTitleOverride = useCallback((title: string | null) => {
    setTitleOverrideState(title);
  }, []);

  const value = useMemo(
    () => ({ titleOverride, setTitleOverride }),
    [titleOverride, setTitleOverride],
  );

  return <AppShellTitleContext.Provider value={value}>{children}</AppShellTitleContext.Provider>;
}

export function useAppShellTitleContext(): AppShellTitleContextValue {
  return useContext(AppShellTitleContext);
}

export function useAppShellTitleOverride(title: string | null): void {
  const { setTitleOverride } = useAppShellTitleContext();

  useEffect(() => {
    setTitleOverride(title);
    return () => {
      setTitleOverride(null);
    };
  }, [title, setTitleOverride]);
}
