"use client";

import { useEffect } from "react";
import { API_BASE_URL } from "@/lib/api";

export function ServiceWorkerRegistration() {
  useEffect(() => {
    if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) {
      return;
    }
    const swUrl = `/sw.js?apiBase=${encodeURIComponent(API_BASE_URL)}`;
    void navigator.serviceWorker.register(swUrl, { scope: "/" }).catch(() => undefined);
  }, []);

  return null;
}
