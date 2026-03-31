"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import { PublicFooter } from "@/components/public/public-footer";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { getMyPlan, login, signup, trackAnalyticsEvent } from "@/lib/api";
import {
  getAuthUser,
  LOGIN_REASON_QUERY_KEY,
  LOGIN_REASON_SESSION_EXPIRED,
  type AuthUser,
  resolveAuthenticatedHome,
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

export default function AuthPage() {
  const router = useRouter();
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
  const [showSessionExpiredMessage, setShowSessionExpiredMessage] = useState(() => {
    if (globalThis.window === undefined) {
      return false;
    }
    const params = new URLSearchParams(globalThis.location.search);
    return params.get(LOGIN_REASON_QUERY_KEY) === LOGIN_REASON_SESSION_EXPIRED;
  });

  useEffect(() => {
    const params = new URLSearchParams(globalThis.location.search);
    setMode(params.get("mode") === "signup" ? "signup" : "login");
    setShowSessionExpiredMessage(params.get(LOGIN_REASON_QUERY_KEY) === LOGIN_REASON_SESSION_EXPIRED);
  }, []);

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

    router.replace(resolveAuthenticatedHome(authenticatedUser));
  }, [authenticatedUser, router]);

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
      router.replace(resolveAuthenticatedHome(nextAuthUser));
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
          <Image
            src="/notelib-logo-full-light.svg"
            alt="NoteLib"
            width={192}
            height={40}
            priority
            className="dark:hidden"
          />
          <Image
            src="/notelib-logo-full-dark.svg"
            alt="NoteLib"
            width={192}
            height={40}
            className="hidden dark:block"
          />
        </div>
        <div className="space-y-2">
          <CardTitle>{mode === "login" ? "Log in to NoteLib" : "Create your NoteLib account"}</CardTitle>
          <CardDescription>
            {mode === "login"
              ? "Continue to your study workspace."
              : "Sign up to generate and save Study Packs."}
          </CardDescription>
          {showSessionExpiredMessage ? (
            <p className="rounded-md border border-amber-300/60 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-700/60 dark:bg-amber-950/40 dark:text-amber-200">
              Your session has expired. Please log in again.
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
            <span className="text-sm font-medium">Email</span>
            <input
              type="email"
              className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
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

          <Button type="submit" className="w-full sm:w-auto" disabled={!canSubmit || loading}>
            {loading ? "Please wait..." : mode === "login" ? "Log in" : "Create account"}
          </Button>
        </form>
      </Card>
      <PublicFooter className="mt-6" />
    </div>
  );
}
