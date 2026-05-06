"use client";

import { FormEvent, Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";
import { PublicFooter } from "@/components/public/public-footer";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { getMyPlan, login, signup, trackAnalyticsEvent } from "@/lib/api";
import {
  getAuthUser,
  LOGIN_REASON_AUTH_REQUIRED,
  LOGIN_REASON_LOGGED_OUT,
  LOGIN_REASON_QUERY_KEY,
  LOGIN_REASON_SESSION_EXPIRED,
  type AuthUser,
  resolvePostLoginDestination,
  setAuthUser,
} from "@/lib/auth";

type Mode = "login" | "signup";

function resolveModeFromLocation(): Mode {
  if (globalThis.window === undefined) {
    return "login";
  }
  const params = new URLSearchParams(globalThis.location.search);
  return params.get("mode") === "signup" ? "signup" : "login";
}

function AuthPageContent() {
  const router = useRouter();
  const startRouteProgress = useRouteProgress();
  const searchParams = useSearchParams();
  const [authenticatedUser, setAuthenticatedUser] = useState<AuthUser | null>(() => getAuthUser());
  const [mode, setMode] = useState<Mode>(resolveModeFromLocation);
  const [firstName, setFirstName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [keepSignedIn, setKeepSignedIn] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hasTrackedSignupStartRef = useRef(false);
  const searchKey = searchParams.toString();
  const searchMode = searchParams.get("mode");
  const loginReason = searchParams.get(LOGIN_REASON_QUERY_KEY);
  const authNotice = useMemo(() => {
    switch (loginReason) {
      case LOGIN_REASON_SESSION_EXPIRED:
        return {
          message: "Your session expired. Please log in again.",
          className:
            "rounded-md border border-amber-300/60 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-700/60 dark:bg-amber-950/40 dark:text-amber-200",
        };
      case LOGIN_REASON_LOGGED_OUT:
        return null;
      case LOGIN_REASON_AUTH_REQUIRED:
        return {
          message: "Please log in to continue.",
          className:
            "rounded-md border border-border bg-muted/50 px-3 py-2 text-sm text-foreground/80",
        };
      default:
        return null;
    }
  }, [loginReason]);

  useEffect(() => {
    setMode(searchMode === "signup" ? "signup" : "login");
  }, [searchKey, searchMode]);

  useEffect(() => {
    const syncAuthenticatedUser = () => {
      setAuthenticatedUser(getAuthUser());
    };

    globalThis.addEventListener("studysnap-auth-change", syncAuthenticatedUser);
    globalThis.addEventListener("storage", syncAuthenticatedUser);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuthenticatedUser);
      globalThis.removeEventListener("storage", syncAuthenticatedUser);
    };
  }, []);

  useEffect(() => {
    if (!authenticatedUser) {
      return;
    }

    startRouteProgress();
    router.replace(resolvePostLoginDestination(authenticatedUser));
  }, [authenticatedUser, router, startRouteProgress]);

  useEffect(() => {
    if (mode !== "signup" || hasTrackedSignupStartRef.current) {
      return;
    }
    hasTrackedSignupStartRef.current = true;
    void trackAnalyticsEvent({
      eventType: "SIGNUP_STARTED",
      metadata: {
        source: "auth_page",
      },
    });
  }, [mode]);

  const canSubmit = useMemo(() => {
    if (mode === "signup") {
      return firstName.trim().length > 0 && email.trim().length > 0 && password.trim().length >= 8;
    }
    return email.trim().length > 0 && password.trim().length > 0;
  }, [email, firstName, mode, password]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit || loading) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const authUser =
        mode === "signup"
          ? await signup({
              firstName,
              email,
              password,
              displayName: displayName.trim().length > 0 ? displayName : undefined,
            })
          : await login({ email, password, keepSignedIn });
      setAuthUser(authUser);
      const nextAuthUser = {
        ...authUser,
        planSummary: await getMyPlan().catch(() => null),
      };
      setAuthUser(nextAuthUser);
      setAuthenticatedUser(nextAuthUser);
      startRouteProgress();
      router.replace(resolvePostLoginDestination(nextAuthUser));
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not continue. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  if (authenticatedUser) {
    return null;
  }

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-6 sm:px-6 sm:py-10">
      <Card className="space-y-6 p-4 sm:p-6">
        <div className="flex justify-center">
          <BrandFullLogo width={192} height={40} priority />
        </div>
        <div className="space-y-2">
          <CardTitle>{mode === "login" ? "Log in to NoteLib" : "Create your NoteLib account"}</CardTitle>
          <CardDescription>
            {mode === "login"
              ? "Continue to your study workspace."
              : "Sign up to generate and save Study Packs."}
          </CardDescription>
          {authNotice ? (
            <p className={authNotice.className}>
              {authNotice.message}
            </p>
          ) : null}
        </div>

        <div className="grid grid-cols-2 gap-2">
          <Button
            type="button"
            variant={mode === "login" ? "default" : "outline"}
            className="w-full"
            onClick={() => setMode("login")}
          >
            Log in
          </Button>
          <Button
            type="button"
            variant={mode === "signup" ? "default" : "outline"}
            className="w-full"
            onClick={() => setMode("signup")}
          >
            Sign up
          </Button>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          {mode === "signup" ? (
            <>
              <label className="block space-y-2">
                <span className="text-sm font-medium">First Name</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  autoComplete="given-name"
                  required
                />
              </label>
              <label className="block space-y-2">
                <span className="text-sm font-medium">Display Name (optional)</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder={firstName.trim() || "How your name appears"}
                  autoComplete="nickname"
                />
              </label>
            </>
          ) : null}

          <label className="block space-y-2">
            <span className="text-sm font-medium">{mode === "login" ? "Email or username" : "Email"}</span>
            <input
              type={mode === "login" ? "text" : "email"}
              className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete={mode === "login" ? "username" : "email"}
              required
            />
          </label>

          <label className="block space-y-2">
            <span className="text-sm font-medium">Password</span>
            <input
              type="password"
              className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === "signup" ? "new-password" : "current-password"}
              required
            />
          </label>
          {mode === "login" ? (
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                checked={keepSignedIn}
                onChange={(e) => setKeepSignedIn(e.target.checked)}
              />
              Keep me signed in for 30 days
            </label>
          ) : null}

          {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

          <Button
            type="submit"
            className="w-full sm:w-auto"
            disabled={!canSubmit}
            loading={loading}
            loadingText={mode === "login" ? "Logging in..." : "Creating account..."}
          >
            {mode === "login" ? "Log in" : "Create account"}
          </Button>
        </form>
      </Card>
      <PublicFooter className="mt-6" />
    </div>
  );
}

export default function AuthPage() {
  return (
    <Suspense fallback={null}>
      <AuthPageContent />
    </Suspense>
  );
}
