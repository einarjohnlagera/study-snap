"use client";

import { useEffect, useState } from "react";
import { BackLink } from "@/components/ui/back-link";
import { getAuthUser } from "@/lib/auth";

export function PublicLibraryBackLink() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    const syncAuth = () => {
      setIsAuthenticated(Boolean(getAuthUser()));
    };

    syncAuth();
    window.addEventListener("studysnap-auth-change", syncAuth);
    return () => {
      window.removeEventListener("studysnap-auth-change", syncAuth);
    };
  }, []);

  if (!isAuthenticated) {
    return null;
  }

  return <BackLink href="/library/public" label="Public Library" />;
}
