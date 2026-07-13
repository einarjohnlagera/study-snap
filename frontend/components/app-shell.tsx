"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Menu, X } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { ApiRequestError, getMe, getMyPlan, logout, requestEmailVerification } from "@/lib/api";
import {
  buildLoginPath,
  getAuthUser,
  LOGIN_REASON_LOGGED_OUT,
  needsOnboarding,
  resolveAuthenticatedHome,
  setAuthUser,
} from "@/lib/auth";
import { ThemeToggle } from "@/components/theme-toggle";
import { BrandMonogram } from "@/components/branding/brand-assets";
import { SendFeedbackWidget } from "@/components/feedback/send-feedback-widget";
import { ResponsiveActionButton, ResponsiveActionContent } from "@/components/ui/action-button";
import { ToastMessage } from "@/components/ui/toast-message";
import { Navbar } from "@/components/navbar";
import { useAppShellTitleContext } from "@/components/app-shell-title-context";
import { useExamFocusContext } from "@/components/exam-mode/exam-focus-context";
import { getCollectionLabels } from "@/lib/collection-labels";
import { hasPendingLightweightProfileCompletion } from "@/lib/onboarding-v2";
import { buildPublicCreatorOrProfilePath } from "@/lib/public-note-path";
import { cn } from "@/lib/utils";
import type { ProfileType } from "@/lib/api";

type AppShellProps = {
  children: React.ReactNode;
};

type ShellUser = {
  id: string | null;
  displayName: string | null;
  username: string | null;
  firstName: string | null;
  email: string | null;
  emailVerifiedAt: string | null;
  onboardingCompletedAt: string | null;
  role: "USER" | "ADMIN" | null;
  profileType: ProfileType | null;
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
    || (pathname.startsWith("/collections") && pathname !== "/collections/published")
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
  if (pathname.startsWith("/public/library/")) {
    return "Public Library";
  }
  if (pathname === "/public/library") {
    return "Public Library";
  }
  if (pathname.startsWith("/library")) {
    return "Library";
  }
  if (pathname.startsWith("/collections")) {
    return getCollectionLabels(getAuthUser()?.profileType).plural;
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
  if (pathname.includes("/long-exam")) {
    return "Long Exam";
  }
  if (pathname.includes("/interview-practice")) {
    return "Interview Practice";
  }
  if (pathname === "/notes/new") {
    return "New Note";
  }
  if (/^\/notes\/[^/]+\/edit$/.test(pathname)) {
    return "Edit Note";
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
  action: "admin" | "campaigns" | "collections" | "dashboard" | "help" | "library" | "profile" | "progress" | "publicLibrary" | "settings";
};

const MAIN_NAV: NavLinkItem[] = [
  { href: "/dashboard", label: "Dashboard", action: "dashboard" },
  { href: "/library", label: "Library", action: "library" },
  { href: "/collections", label: "Collections", action: "collections" },
  { href: "/progress", label: "Progress", action: "progress" },
  { href: "/public/library", label: "Public Library", action: "publicLibrary" },
];


function NavLinks({
  pathname,
  mainNav,
  secondaryNav,
  onNavigate,
}: Readonly<{
  pathname: string;
  mainNav: NavLinkItem[];
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
            ? "bg-highlight-strong text-blue-700 dark:text-blue-200"
            : "text-foreground/80 hover:bg-highlight hover:text-foreground active:bg-highlight-strong"
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
        {mainNav.map(renderLink)}
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
    id: null,
    displayName: null,
    username: null,
    firstName: null,
    email: null,
    emailVerifiedAt: null,
    onboardingCompletedAt: null,
    role: null,
    profileType: null,
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
          id: null,
          displayName: null,
          username: null,
          firstName: null,
          email: null,
          emailVerifiedAt: null,
          onboardingCompletedAt: null,
          role: null,
          profileType: null,
        });
        return;
      }
      setUser((previous) => ({
        id: authUser.id ?? previous.id,
        displayName: authUser.displayName ?? previous.displayName,
        username: authUser.username ?? previous.username,
        firstName: previous.firstName,
        email: authUser.email ?? previous.email,
        emailVerifiedAt: authUser.emailVerifiedAt,
        onboardingCompletedAt: authUser.onboardingCompletedAt ?? previous.onboardingCompletedAt,
        role: authUser.role,
        profileType: authUser.profileType ?? previous.profileType,
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
    if (
      needsOnboarding(authUser)
      && !hasPendingLightweightProfileCompletion(authUser.id)
      && isProtectedAppRoute(pathname)
      && pathname !== "/onboarding"
    ) {
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
      id: authUser?.id ?? null,
      displayName: authUser?.displayName ?? null,
      username: authUser?.username ?? null,
      firstName: null,
      email: authUser?.email ?? null,
      emailVerifiedAt: authUser?.emailVerifiedAt ?? null,
      onboardingCompletedAt: authUser?.onboardingCompletedAt ?? null,
      role: authUser?.role ?? null,
      profileType: authUser?.profileType ?? null,
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
          id: me.id,
          displayName: me.displayName?.trim() || null,
          username: me.username ?? null,
          firstName: me.firstName?.trim() || null,
          email: me.email,
          emailVerifiedAt: me.emailVerifiedAt,
          onboardingCompletedAt: me.onboardingCompletedAt,
          role: me.role,
          profileType: me.profileType,
        });
        const authUser = getAuthUser();
        if (authUser) {
          setAuthUser({
            ...authUser,
            displayName: me.displayName?.trim() || authUser.displayName,
            username: me.username ?? null,
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

  const secondaryNav = useMemo<NavLinkItem[]>(() => {
    const nav: NavLinkItem[] = [
      { href: "/profile", label: "Profile", action: "profile" },
      { href: "/settings", label: "Settings", action: "settings" },
      { href: "/help", label: "Help", action: "help" },
    ];
    if (user.role === "ADMIN") {
      nav.push({ href: "/admin", label: "Admin", action: "admin" as const });
      nav.push({ href: "/admin/campaigns", label: "Campaigns", action: "campaigns" as const });
    }
    return nav;
  }, [user.role]);

  const mainNav = useMemo<NavLinkItem[]>(() => {
    const labels = getCollectionLabels(user.profileType);
    return MAIN_NAV.map((item) => (
      item.href === "/collections" ? { ...item, label: labels.navLabel } : item
    ));
  }, [user.profileType]);

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
      router.replace(
        buildLoginPath({
          reason: LOGIN_REASON_LOGGED_OUT,
        }),
      );
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
  const { titleOverride } = useAppShellTitleContext();
  const pageTitle = titleOverride ?? getPageTitle(pathname || "");
  const showVerificationBanner = shouldUseShell && hasAuthUser && !user.emailVerifiedAt;

  const { isExamFocusActive } = useExamFocusContext();

  if (!shouldUseShell) {
    return (
      <div className="min-h-screen bg-background text-foreground">
        <Navbar />
        <main>{children}</main>
      </div>
    );
  }

  return (
    <div
      className={cn(
        "min-h-screen bg-background text-foreground",
        !isExamFocusActive && "md:grid md:grid-cols-[256px_1fr]",
      )}
    >
      {!isExamFocusActive ? (
        <aside className="hidden border-r border-border bg-background md:sticky md:top-0 md:flex md:h-screen md:w-64 md:flex-col">
          <div className="flex h-16 items-center gap-3 border-b border-border px-4">
            <span className="inline-flex h-9 w-9 items-center justify-center overflow-hidden rounded-xl border border-border/70 bg-white shadow-sm">
              <BrandMonogram size={36} className="h-9 w-9" priority />
            </span>
            <span className="text-sm font-semibold">NoteLib</span>
          </div>
          <nav className="flex-1 space-y-6 p-4">
            <NavLinks pathname={pathname || ""} mainNav={mainNav} secondaryNav={secondaryNav} />
          </nav>
        </aside>
      ) : null}

      <div className="min-h-screen">
        {!isExamFocusActive ? (
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
            <SendFeedbackWidget
              variant="icon"
              triggerLabel="Send Feedback"
              iconButtonClassName="border-border/80 bg-background/80"
            />
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
                <div className="motion-dropdown-panel absolute right-0 top-11 w-52 rounded-md border border-border bg-background p-1 shadow-sm">
                  {user.id ? (
                    <Link
                      href={buildPublicCreatorOrProfilePath({ userId: user.id, username: user.username })}
                      className="motion-lift block rounded px-3 py-2 text-sm text-foreground/85 transition-colors hover:bg-highlight hover:text-foreground active:bg-highlight-strong"
                    >
                      <span className="inline-flex items-center gap-2">
                        <ResponsiveActionContent action="profile" label="My Profile" showTextOnMobile />
                      </span>
                    </Link>
                  ) : null}
                  <Link
                    href="/settings"
                    className="motion-lift block rounded px-3 py-2 text-sm text-foreground/85 transition-colors hover:bg-highlight hover:text-foreground active:bg-highlight-strong"
                  >
                    <span className="inline-flex items-center gap-2">
                      <ResponsiveActionContent action="settings" label="Settings" showTextOnMobile />
                    </span>
                  </Link>
                  <Link
                    href="/help"
                    className="motion-lift block rounded px-3 py-2 text-sm text-foreground/85 transition-colors hover:bg-highlight hover:text-foreground active:bg-highlight-strong"
                  >
                    <span className="inline-flex items-center gap-2">
                      <ResponsiveActionContent action="help" label="Help" showTextOnMobile />
                    </span>
                  </Link>
                  <button
                    type="button"
                    onClick={() => void handleSignOut()}
                    className="motion-lift block w-full rounded px-3 py-2 text-left text-sm text-foreground/85 transition-colors hover:bg-highlight hover:text-foreground active:bg-highlight-strong"
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
        ) : null}

        {!isExamFocusActive && showVerificationBanner ? (
          <div className="border-b border-amber-300/50 bg-amber-50/70 px-4 py-3 dark:border-amber-700/50 dark:bg-amber-950/20 sm:px-6">
            <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
              <div className="space-y-1">
                <p className="text-sm text-amber-900 dark:text-amber-200">
                  Verify your email to unlock all features. You can write and save notes in the meantime.
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

      {!isExamFocusActive && drawerOpen ? (
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
                <span className="inline-flex h-9 w-9 items-center justify-center overflow-hidden rounded-xl border border-border/70 bg-white shadow-sm">
                  <BrandMonogram size={36} className="h-9 w-9" />
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
                <NavLinks
                  pathname={pathname || ""}
                  mainNav={mainNav}
                  secondaryNav={secondaryNav}
                  onNavigate={() => setDrawerOpen(false)}
                />
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

      {!isExamFocusActive && toastMessage ? <ToastMessage message={toastMessage} tone={toastTone} /> : null}
    </div>
  );
}
