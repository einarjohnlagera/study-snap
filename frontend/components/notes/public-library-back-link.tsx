"use client";

import { useEffect, useState } from "react";
import { BackLink } from "@/components/ui/back-link";
import { getAuthUser } from "@/lib/auth";
import { PUBLIC_LIBRARY_PATH, PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY } from "@/lib/public-library-url";

export function PublicLibraryBackLink() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [returnHref] = useState(() => {
    const saved = globalThis.sessionStorage?.getItem(PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY);
    const isPublicLibraryPath = saved === PUBLIC_LIBRARY_PATH
      || saved?.startsWith(`${PUBLIC_LIBRARY_PATH}?`)
      || saved?.startsWith(`${PUBLIC_LIBRARY_PATH}/`);
    const isExplorePath = saved === "/explore" || saved?.startsWith("/explore?");
    return saved && (isPublicLibraryPath || isExplorePath)
      ? saved
      : PUBLIC_LIBRARY_PATH;
  });

  useEffect(() => {
    const syncAuth = () => {
      setIsAuthenticated(Boolean(getAuthUser()));
    };

    syncAuth();
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuth);
    };
  }, []);

  if (!isAuthenticated) {
    return null;
  }

  return <BackLink href={returnHref} label={returnHref.startsWith("/explore") ? "Explore" : "Public Library"} />;
}
