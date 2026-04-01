"use client";

import Link from "next/link";
import Image from "next/image";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Menu, X } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { ApiRequestError, getMe, getMyPlan, logout, requestEmailVerification } from "@/lib/api";
import { getAuthUser, needsOnboarding, resolveAuthenticatedHome, setAuthUser } from "@/lib/auth";
import { ThemeToggle } from "@/components/theme-toggle";
import { SendFeedbackWidget } from "@/components/feedback/send-feedback-widget";
import { ResponsiveActionButton, ResponsiveActionContent } from "@/components/ui/action-button";
import { ToastMessage } from "@/components/ui/toast-message";
import { Navbar } from "@/components/navbar";

type AppShellProps = {
  children: React.ReactNode;
};

type ShellUser = {
  displayName: string | null;
  firstName: string | null;
  email: string | null;
  emailVerifiedAt: string | null;
  onboardingCompletedAt: string | null;
  role: "USER" | "ADMIN" | null;
};

function isMarketingPublicRoute(pathname: string): boolean {
  return (
    pathname === "/"
    || pathname === "/learn"
    || pathname.startsWith("/learn/")
    || pathname === "/pricing"
    || pathname === "/privacy"
    || pathname === "/terms"
  );
}

function isAuthRoute(pathname: string): boolean {
  return pathname === "/auth" || pathname === "/login" || pathname === "/signup";
}

function shouldUseAuthenticatedShell(hasAuthUser: boolean, pathname: string): boolean {
  return hasAuthUser && !isMarketingPublicRoute(pathname) && !isAuthRoute(pathname);
}

function isProtectedAppRoute(pathname: string): boolean {
  return (
    pathname.startsWith("/dashboard")
    || pathname.startsWith("/library")
    || pathname.startsWith("/notes")
    || pathname.startsWith("/profile")
    || pathname.startsWith("/settings")
    || pathname.startsWith("/study")
    || pathname.startsWith("/study-packs")
    || pathname.startsWith("/admin")
  );
}

function getPageTitle(pathname: string): string {
  if (pathname.startsWith("/p/")) {
    return "Shared Study Pack";
  }
  if (pathname.startsWith("/dashboard")) {
    return "Dashboard";
  }
  if (pathname.startsWith("/library/public")) {
    return "Public Library";
  }
  if (pathname.startsWith("/public/library/")) {
    return "Public Library";
  }
  if (pathname.startsWith("/library")) {
    return "Library";
  }
  if (pathname.startsWith("/public/notes/")) {
    return "Public Note";
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
  if (pathname === "/notes/new") {
    return "New Note";
  }
  if (pathname.startsWith("/notes/")) {
    return "Note";
  }
  if (pathname.startsWith("/notes")) {
    return "Notes";
  }
  if (pathname.startsWith("/profile")) {
    return "Profile";
  }
  if (pathname.startsWith("/settings")) {
    return "Settings";
  }
  if (pathname.startsWith("/onboarding")) {
    return "Onboarding";
  }
  if (pathname.startsWith("/pricing")) {
    return "Pricing";
  }
  if (pathname.startsWith("/admin")) {
    return "Admin";
  }
  if (pathname.startsWith("/verify-email")) {
    return "Verify Email";
  }
  if (pathname === "/demo") {
    return "Demo";
  }
  if (pathname === "/study" || pathname.startsWith("/study/")) {
    return "New Note";
  }
  if (pathname.startsWith("/study-packs")) {
    return "Note";
  }
  return "NoteLib";
}

type NavLinkItem = {
  href: string;
  label: string;
  action: "admin" | "dashboard" | "library" | "profile" | "publicLibrary" | "settings";
};

const MAIN_NAV: NavLinkItem[] = [
  { href: "/dashboard", label: "Dashboard", action: "dashboard" },
  { href: "/library", label: "Library", action: "library" },
  { href: "/library/public", label: "Public Library", action: "publicLibrary" },
];

const SECONDARY_NAV: NavLinkItem[] = [
  { href: "/profile", label: "Profile", action: "profile" },
  { href: "/settings", label: "Settings", action: "settings" },
];

function NavLinks({
  pathname,
  secondaryNav,
  onNavigate,
}: Readonly<{
  pathname: string;
  secondaryNav: NavLinkItem[];
  onNavigate?: () => void;
}>) {
  const renderLink = (item: NavLinkItem) => {
    const active = item.href === "/library"
      ? pathname === item.href
      : pathname === item.href || pathname.startsWith(`${item.href}/`);
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
        <span className="inline-flex items-center gap-2">
          <ResponsiveActionContent action={item.action} label={item.label} showTextOnMobile />
        </span>
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
        {secondaryNav.map(renderLink)}
      </div>
    </>
  );
}

export function AppShell({ children }: Readonly<AppShellProps>) {
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
    emailVerifiedAt: null,
    onboardingCompletedAt: null,
    role: null,
  });
  const [resendingVerification, setResendingVerification] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastTone, setToastTone] = useState<"success" | "error" | "info">("info");
  const avatarMenuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const syncAuthState = () => {
      const authUser = getAuthUser();
      setHasAuthUser(Boolean(authUser));
      if (!authUser) {
        setUser({
          displayName: null,
          firstName: null,
          email: null,
          emailVerifiedAt: null,
          onboardingCompletedAt: null,
          role: null,
        });
        return;
      }
      setUser((previous) => ({
        displayName: authUser.displayName ?? previous.displayName,
        firstName: previous.firstName,
        email: authUser.email ?? previous.email,
        emailVerifiedAt: authUser.emailVerifiedAt,
        onboardingCompletedAt: authUser.onboardingCompletedAt ?? previous.onboardingCompletedAt,
        role: authUser.role,
      }));
    };

    syncAuthState();
    globalThis.addEventListener("studysnap-auth-change", syncAuthState);
    globalThis.addEventListener("storage", syncAuthState);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuthState);
      globalThis.removeEventListener("storage", syncAuthState);
    };
  }, []);

  const shouldUseShell = useMemo(() => {
    return shouldUseAuthenticatedShell(hasAuthUser, pathname || "");
  }, [hasAuthUser, pathname]);

  useEffect(() => {
    if (!hasAuthUser || pathname !== "/" || isMarketingPublicRoute(pathname)) {
      return;
    }
    router.replace("/dashboard");
  }, [hasAuthUser, pathname, router]);

  useEffect(() => {
    if (!hasAuthUser || !isAuthRoute(pathname)) {
      return;
    }

    const authUser = getAuthUser();
    if (!authUser) {
      return;
    }

    router.replace(resolveAuthenticatedHome(authUser));
  }, [hasAuthUser, pathname, router]);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser) {
      return;
    }
    if (needsOnboarding(authUser) && isProtectedAppRoute(pathname) && pathname !== "/onboarding") {
      router.replace("/onboarding");
      return;
    }
    if (authUser.onboardingCompletedAt && pathname === "/onboarding") {
      router.replace("/dashboard");
    }
  }, [pathname, router]);

  useEffect(() => {
    if (!shouldUseShell) {
      return;
    }
    const authUser = getAuthUser();
    setUser({
      displayName: authUser?.displayName ?? null,
      firstName: null,
      email: authUser?.email ?? null,
      emailVerifiedAt: authUser?.emailVerifiedAt ?? null,
      onboardingCompletedAt: authUser?.onboardingCompletedAt ?? null,
      role: authUser?.role ?? null,
    });
  }, [shouldUseShell, pathname]);

  useEffect(() => {
    if (!shouldUseShell) {
      return;
    }
    let mounted = true;
    void Promise.all([
      getMe(),
      getMyPlan().catch(() => null),
    ])
      .then(([me, planSummary]) => {
        if (!mounted) {
          return;
        }
        setUser({
          displayName: me.displayName?.trim() || null,
          firstName: me.firstName?.trim() || null,
          email: me.email,
          emailVerifiedAt: me.emailVerifiedAt,
          onboardingCompletedAt: me.onboardingCompletedAt,
          role: me.role,
        });
        const authUser = getAuthUser();
        if (authUser) {
          setAuthUser({
            ...authUser,
            displayName: me.displayName?.trim() || authUser.displayName,
            profileType: me.profileType,
            emailVerifiedAt: me.emailVerifiedAt,
            onboardingCompletedAt: me.onboardingCompletedAt,
            productOnboardingCompletedAt: me.productOnboardingCompletedAt,
            planSummary,
          });
        }
      })
      .catch(() => {
        // Keep local auth fallback data if profile fetch fails.
      });
    return () => {
      mounted = false;
    };
  }, [shouldUseShell, pathname]);

  const secondaryNav = useMemo(() => {
    return user.role === "ADMIN"
      ? [...SECONDARY_NAV, { href: "/admin", label: "Admin", action: "admin" }]
      : SECONDARY_NAV;
  }, [user.role]);

  useEffect(() => {
    setDrawerOpen(false);
    setAvatarMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!toastMessage) {
      return;
    }
    const timeout = globalThis.setTimeout(() => {
      setToastMessage(null);
    }, 3200);
    return () => {
      globalThis.clearTimeout(timeout);
    };
  }, [toastMessage]);

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
    globalThis.addEventListener("mousedown", handleOutsideClick);
    return () => {
      globalThis.removeEventListener("mousedown", handleOutsideClick);
    };
  }, [avatarMenuOpen]);

  const handleSignOut = useCallback(async () => {
    setSigningOut(true);
    try {
      await logout();
      setAvatarMenuOpen(false);
      setDrawerOpen(false);
      router.replace("/auth");
      router.refresh();
    } finally {
      setSigningOut(false);
    }
  }, [router]);

  const handleResendVerification = useCallback(async () => {
    if (resendingVerification) {
      return;
    }
    setResendingVerification(true);
    try {
      await requestEmailVerification();
      setToastTone("success");
      setToastMessage("Verification email sent. Check your inbox.");
    } catch (error) {
      if (error instanceof ApiRequestError && (error.code === "VERIFICATION_EMAIL_COOLDOWN" || error.status === 429)) {
        setToastTone("info");
        setToastMessage("You can resend again in a moment.");
      } else {
        setToastTone("error");
        setToastMessage("Could not send verification email. Please try again.");
      }
    } finally {
      setResendingVerification(false);
    }
  }, [resendingVerification]);

  const avatarSeed = useMemo(() => {
    return user.displayName || user.firstName || user.email || "U";
  }, [user.displayName, user.email, user.firstName]);

  const avatarLetter = avatarSeed.charAt(0).toUpperCase();
  const pageTitle = getPageTitle(pathname || "");
  const showVerificationBanner = shouldUseShell && hasAuthUser && !user.emailVerifiedAt;

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
          <NavLinks pathname={pathname || ""} secondaryNav={secondaryNav} />
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
                    <span className="inline-flex items-center gap-2">
                      <ResponsiveActionContent action="profile" label="Profile" showTextOnMobile />
                    </span>
                  </Link>
                  <Link
                    href="/settings"
                    className="block rounded px-3 py-2 text-sm text-foreground/85 hover:bg-muted/70 hover:text-foreground"
                  >
                    <span className="inline-flex items-center gap-2">
                      <ResponsiveActionContent action="settings" label="Settings" showTextOnMobile />
                    </span>
                  </Link>
                  <button
                    type="button"
                    onClick={() => void handleSignOut()}
                    className="block w-full rounded px-3 py-2 text-left text-sm text-foreground/85 hover:bg-muted/70 hover:text-foreground"
                    disabled={signingOut}
                  >
                    <span className="inline-flex items-center gap-2">
                      <ResponsiveActionContent action="signOut" label={signingOut ? "Signing out..." : "Sign Out"} showTextOnMobile />
                    </span>
                  </button>
                </div>
              ) : null}
            </div>
          </div>
        </header>

        {showVerificationBanner ? (
          <div className="border-b border-amber-300/50 bg-amber-50/70 px-4 py-3 dark:border-amber-700/50 dark:bg-amber-950/20 sm:px-6">
            <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
              <div className="space-y-1">
                <p className="text-sm text-amber-900 dark:text-amber-200">
                  Verify your email to generate Study Packs, use OCR, and publish notes.
                </p>
              </div>
              <ResponsiveActionButton
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  void handleResendVerification();
                }}
                disabled={resendingVerification}
                action="retry"
                label={resendingVerification ? "Sending..." : "Resend verification email"}
              />
            </div>
          </div>
        ) : null}

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
              <NavLinks pathname={pathname || ""} secondaryNav={secondaryNav} onNavigate={() => setDrawerOpen(false)} />
            </nav>
            <div className="border-t border-border p-4">
              <ResponsiveActionButton
                type="button"
                variant="outline"
                className="w-full"
                onClick={() => void handleSignOut()}
                disabled={signingOut}
                action="signOut"
                label={signingOut ? "Signing out..." : "Sign Out"}
                showTextOnMobile
              />
            </div>
          </aside>
        </>
      ) : null}

      {toastMessage ? <ToastMessage message={toastMessage} tone={toastTone} /> : null}
      <SendFeedbackWidget />
    </div>
  );
}
