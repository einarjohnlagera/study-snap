"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getAuthUser } from "@/lib/auth";

type PublicLibraryBackLinkProps = {
  className?: string;
};

export function PublicLibraryBackLink({ className }: PublicLibraryBackLinkProps) {
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

  return (
    <Link href="/library/public" className={className}>
      Back to Public Library
    </Link>
  );
}
