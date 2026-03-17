"use client";

import Link from "next/link";
import Image from "next/image";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Menu, X } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { logout, getMe } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Navbar } from "@/components/navbar";

type AppShellProps = {
  children: React.ReactNode;
};

type ShellUser = {
  displayName: string | null;
  firstName: string | null;
  email: string | null;
};

function isAuthenticatedRoute(pathname: string): boolean {
  return (
    pathname.startsWith("/dashboard")
    || pathname.startsWith("/library")
    || pathname.startsWith("/profile")
    || pathname.startsWith("/settings")
    || pathname === "/study"
    || pathname.startsWith("/study/")
    || pathname.startsWith("/study-packs")
  );
}

function shouldUseAuthenticatedShell(pathname: string, hasAuthUser: boolean): boolean {
  if (pathname.startsWith("/p/")) {
    return hasAuthUser;
  }
  if (pathname.startsWith("/demo")) {
    return hasAuthUser;
  }
  return isAuthenticatedRoute(pathname);
}

function getPageTitle(pathname: string): string {
  if (pathname.startsWith("/p/")) {
    return "Shared Study Pack";
  }
  if (pathname.startsWith("/dashboard")) {
    return "Dashboard";
  }
  if (pathname.startsWith("/library")) {
    return "Study Library";
  }
  if (pathname.startsWith("/profile")) {
    return "Profile";
  }
  if (pathname.startsWith("/settings")) {
    return "Settings";
  }
  if (pathname === "/demo") {
    return "Demo";
  }
  if (pathname === "/study" || pathname.startsWith("/study/")) {
    return "Create Study Pack";
  }
  if (pathname.includes("/quick-review")) {
    return "Quick Review";
  }
  if (pathname.includes("/adaptive-practice")) {
    return "Adaptive Practice";
  }
  if (pathname.includes("/challenge-quiz")) {
    return "Challenge Quiz";
  }
  if (pathname.startsWith("/study-packs")) {
    return "Study Pack";
  }
  return "NoteLib";
}

type NavLinkItem = {
  href: string;
  label: string;
};

const MAIN_NAV: NavLinkItem[] = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/library", label: "Library" },
];

const SECONDARY_NAV: NavLinkItem[] = [
  { href: "/profile", label: "Profile" },
  { href: "/settings", label: "Settings" },
];

function NavLinks({
  pathname,
  onNavigate,
}: {
  pathname: string;
  onNavigate?: () => void;
}) {
  const renderLink = (item: NavLinkItem) => {
    const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
    return (
      <Link
        key={item.href}
        href={item.href}
        onClick={onNavigate}
        className={`block w-full rounded-md px-3 py-2.5 text-left text-sm font-medium transition-colors ${
          active
            ? "bg-blue-600/15 text-blue-700 dark:bg-blue-500/20 dark:text-blue-200"
            : "text-foreground/80 hover:bg-muted/70 hover:text-foreground"
        }`}
      >
        {item.label}
      </Link>
    );
  };

  return (
    <>
      <div className="space-y-1.5">
        <p className="px-3 text-xs font-semibold uppercase tracking-wide text-foreground/50">Main</p>
        {MAIN_NAV.map(renderLink)}
      </div>
      <div className="space-y-1.5">
        <p className="px-3 text-xs font-semibold uppercase tracking-wide text-foreground/50">Account</p>
        {SECONDARY_NAV.map(renderLink)}
      </div>
    </>
  );
}

export function AppShell({ children }: AppShellProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [avatarMenuOpen, setAvatarMenuOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [hasAuthUser, setHasAuthUser] = useState(false);
  const [user, setUser] = useState<ShellUser>({
    displayName: null,
    firstName: null,
    email: null,
  });
  const avatarMenuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const syncAuthState = () => {
      setHasAuthUser(Boolean(getAuthUser()));
    };

    syncAuthState();
    window.addEventListener("studysnap-auth-change", syncAuthState);
    window.addEventListener("storage", syncAuthState);
    return () => {
      window.removeEventListener("studysnap-auth-change", syncAuthState);
      window.removeEventListener("storage", syncAuthState);
    };
  }, []);

  const shouldUseShell = useMemo(() => {
    if (!pathname) {
      return false;
    }
    return shouldUseAuthenticatedShell(pathname, hasAuthUser);
  }, [hasAuthUser, pathname]);

  useEffect(() => {
    if (!shouldUseShell) {
      return;
    }
    const authUser = getAuthUser();
    setUser({
      displayName: authUser?.displayName ?? null,
      firstName: null,
      email: authUser?.email ?? null,
    });
  }, [shouldUseShell, pathname]);

  useEffect(() => {
    if (!shouldUseShell) {
      return;
    }
    let mounted = true;
    void getMe()
      .then((me) => {
        if (!mounted) {
          return;
        }
        setUser({
          displayName: me.displayName?.trim() || null,
          firstName: me.firstName?.trim() || null,
          email: me.email,
        });
      })
      .catch(() => {
        // Keep local auth fallback data if profile fetch fails.
      });
    return () => {
      mounted = false;
    };
  }, [shouldUseShell, pathname]);

  useEffect(() => {
    setDrawerOpen(false);
    setAvatarMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!avatarMenuOpen) {
      return;
    }
    const handleOutsideClick = (event: MouseEvent) => {
      if (!avatarMenuRef.current) {
        return;
      }
      if (!avatarMenuRef.current.contains(event.target as Node)) {
        setAvatarMenuOpen(false);
      }
    };
    window.addEventListener("mousedown", handleOutsideClick);
    return () => {
      window.removeEventListener("mousedown", handleOutsideClick);
    };
  }, [avatarMenuOpen]);

  const handleSignOut = useCallback(async () => {
    setSigningOut(true);
    try {
      await logout();
      setAvatarMenuOpen(false);
      setDrawerOpen(false);
      router.push("/auth");
      router.refresh();
    } finally {
      setSigningOut(false);
    }
  }, [router]);

  const avatarSeed = useMemo(() => {
    return user.displayName || user.firstName || user.email || "U";
  }, [user.displayName, user.email, user.firstName]);

  const avatarLetter = avatarSeed.charAt(0).toUpperCase();
  const pageTitle = getPageTitle(pathname || "");

  if (!shouldUseShell) {
    return (
      <div className="min-h-screen bg-background text-foreground">
        <Navbar />
        <main>{children}</main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background text-foreground md:grid md:grid-cols-[256px_1fr]">
      <aside className="hidden border-r border-border bg-background md:sticky md:top-0 md:flex md:h-screen md:w-64 md:flex-col">
        <div className="flex h-16 items-center gap-3 border-b border-border px-4">
          <span className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border">
            <Image src="/notelib-logo-icon.svg" alt="NoteLib logo" width={20} height={20} priority />
          </span>
          <span className="text-sm font-semibold">NoteLib</span>
        </div>
        <nav className="flex-1 space-y-6 p-4">
          <NavLinks pathname={pathname || ""} />
        </nav>
      </aside>

      <div className="min-h-screen">
        <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-border bg-background/95 px-4 backdrop-blur sm:px-6">
          <div className="flex items-center gap-3">
            <button
              type="button"
              className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border md:hidden"
              onClick={() => setDrawerOpen(true)}
              aria-label="Open navigation menu"
            >
              <Menu className="h-4 w-4" />
            </button>
            <h1 className="text-base font-semibold sm:text-lg">{pageTitle}</h1>
          </div>

          <div className="flex items-center gap-3">
            <ThemeToggle />
            <div className="relative" ref={avatarMenuRef}>
              <button
                type="button"
                onClick={() => setAvatarMenuOpen((open) => !open)}
                className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-blue-600 text-sm font-semibold text-white dark:bg-blue-500"
                aria-label="Open user menu"
              >
                {avatarLetter}
              </button>
              {avatarMenuOpen ? (
                <div className="absolute right-0 top-11 w-44 rounded-md border border-border bg-background p-1 shadow-sm">
                  <Link
                    href="/profile"
                    className="block rounded px-3 py-2 text-sm text-foreground/85 hover:bg-muted/70 hover:text-foreground"
                  >
                    Profile
                  </Link>
                  <Link
                    href="/settings"
                    className="block rounded px-3 py-2 text-sm text-foreground/85 hover:bg-muted/70 hover:text-foreground"
                  >
                    Settings
                  </Link>
                  <button
                    type="button"
                    onClick={() => void handleSignOut()}
                    className="block w-full rounded px-3 py-2 text-left text-sm text-foreground/85 hover:bg-muted/70 hover:text-foreground"
                    disabled={signingOut}
                  >
                    {signingOut ? "Signing out..." : "Sign Out"}
                  </button>
                </div>
              ) : null}
            </div>
          </div>
        </header>

        <main>{children}</main>
      </div>

      {drawerOpen ? (
        <>
          <button
            type="button"
            className="fixed inset-0 z-30 bg-black/40 md:hidden"
            onClick={() => setDrawerOpen(false)}
            aria-label="Close navigation overlay"
          />
          <aside className="fixed inset-y-0 left-0 z-40 flex w-72 flex-col border-r border-border bg-background md:hidden">
            <div className="flex h-16 items-center justify-between border-b border-border px-4">
              <div className="flex items-center gap-3">
                <span className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border">
                  <Image src="/notelib-logo-icon.svg" alt="NoteLib logo" width={20} height={20} />
                </span>
                <span className="text-sm font-semibold">NoteLib</span>
              </div>
              <button
                type="button"
                className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border"
                onClick={() => setDrawerOpen(false)}
                aria-label="Close navigation menu"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <nav className="flex-1 space-y-6 p-4">
              <NavLinks pathname={pathname || ""} onNavigate={() => setDrawerOpen(false)} />
            </nav>
            <div className="border-t border-border p-4">
              <Button
                type="button"
                variant="outline"
                className="w-full"
                onClick={() => void handleSignOut()}
                disabled={signingOut}
              >
                {signingOut ? "Signing out..." : "Sign Out"}
              </Button>
            </div>
          </aside>
        </>
      ) : null}
    </div>
  );
}
