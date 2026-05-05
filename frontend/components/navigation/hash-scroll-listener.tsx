"use client";

import { useCallback, useEffect } from "react";

type HashScrollListenerProps = Readonly<{
  allowedIds: readonly string[];
}>;

export function HashScrollListener({ allowedIds }: HashScrollListenerProps) {
  const scrollToRequestedHash = useCallback(() => {
    if (globalThis.window === undefined) {
      return;
    }
    const hash = globalThis.location.hash.slice(1);
    if (!hash || !allowedIds.includes(hash)) {
      return;
    }
    const section = globalThis.document.getElementById(hash);
    if (!section || typeof section.scrollIntoView !== "function") {
      return;
    }
    globalThis.requestAnimationFrame(() => {
      section.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }, [allowedIds]);

  useEffect(() => {
    const timeoutId = globalThis.setTimeout(() => {
      scrollToRequestedHash();
    }, 0);
    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [scrollToRequestedHash]);

  useEffect(() => {
    const handleHashChange = () => {
      scrollToRequestedHash();
    };
    globalThis.addEventListener("hashchange", handleHashChange);
    return () => {
      globalThis.removeEventListener("hashchange", handleHashChange);
    };
  }, [scrollToRequestedHash]);

  return null;
}
