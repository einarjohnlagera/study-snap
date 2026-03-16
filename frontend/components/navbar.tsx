"use client";

import Link from "next/link";
import Image from "next/image";
import { ThemeToggle } from "./theme-toggle";
import { useEffect, useState } from "react";
import { getAuthUser } from "@/lib/auth";
import { usePathname, useRouter } from "next/navigation";
import { Button } from "./ui/button";
import { logout } from "@/lib/api";

export function Navbar() {
  const router = useRouter();
  const pathname = usePathname();
  const [showDashboard, setShowDashboard] = useState(false);
  const [showAuthLinks, setShowAuthLinks] = useState(true);
  const [displayName, setDisplayName] = useState<string | null>(null);

  useEffect(() => {
    const authUser = getAuthUser();
    setShowDashboard(Boolean(authUser));
    setShowAuthLinks(!authUser);
    setDisplayName(authUser?.displayName ?? null);
  }, [pathname]);

  const handleLogout = async () => {
    await logout();
    setShowDashboard(false);
    setShowAuthLinks(true);
    setDisplayName(null);
    router.push("/");
    router.refresh();
  };

  return (
    <header className="border-b border-border bg-background/95 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-5xl items-center justify-between px-6">
        <Link href="/" className="flex items-center gap-3">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-border">
            <Image
              src="/note-lib-logo-icon.svg"
              alt="NoteLib logo"
              width={20}
              height={20}
              priority
            />
          </span>
          <span className="text-sm font-semibold sm:text-base">NoteLib</span>
        </Link>
        <div className="flex items-center gap-4">
          {showDashboard ? (
            <>
              <Link
                href="/dashboard"
                className="text-sm text-foreground/80 transition-colors hover:text-foreground"
              >
                Dashboard
              </Link>
              <Link
                href="/library"
                className="text-sm text-foreground/80 transition-colors hover:text-foreground"
              >
                Library
              </Link>
              <Link
                href="/profile"
                className="text-sm text-foreground/80 transition-colors hover:text-foreground"
              >
                Profile
              </Link>
              <Link
                href="/settings"
                className="text-sm text-foreground/80 transition-colors hover:text-foreground"
              >
                Settings
              </Link>
            </>
          ) : null}
          {displayName ? (
            <span className="text-sm text-foreground/80">Hi, {displayName}</span>
          ) : null}
          {showAuthLinks ? (
            <Link
              href="/auth"
              className="text-sm text-foreground/80 transition-colors hover:text-foreground"
            >
              Log in
            </Link>
          ) : (
            <Button type="button" variant="outline" size="sm" onClick={() => void handleLogout()}>
              Log out
            </Button>
          )}
          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
