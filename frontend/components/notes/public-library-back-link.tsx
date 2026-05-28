"use client";

import { useEffect, useState } from "react";
import { BackLink } from "@/components/ui/back-link";
import { getAuthUser } from "@/lib/auth";

const PUBLIC_LIBRARY_RETURN_KEY = "notelib_public_library_return_url";
const PUBLIC_LIBRARY_DEFAULT_HREF = "/public/library";

export function PublicLibraryBackLink() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [returnHref] = useState(() => {
    const saved = globalThis.sessionStorage?.getItem(PUBLIC_LIBRARY_RETURN_KEY);
    return saved && saved.startsWith("/public/library") ? saved : PUBLIC_LIBRARY_DEFAULT_HREF;
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

  return <BackLink href={returnHref} label="Public Library" />;
}
