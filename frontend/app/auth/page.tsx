"use client";

import { FormEvent, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { GoogleAuthButton } from "@/components/auth/google-auth-button";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";
import { PublicFooter } from "@/components/public/public-footer";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  ACCOUNT_PENDING_DELETION_CODE,
  ApiRequestError,
  copyNoteOnSignup,
  getMyPlan,
  login,
  loginWithGoogle,
  reactivateAccount,
  signup,
  trackAnalyticsEvent,
} from "@/lib/api";
import {
  getAuthUser,
  LOGIN_REASON_AUTH_REQUIRED,
  LOGIN_REASON_LOGGED_OUT,
  LOGIN_REASON_PASSWORD_RESET,
  LOGIN_REASON_QUERY_KEY,
  LOGIN_REASON_SESSION_EXPIRED,
  type AuthUser,
  resolvePostLoginDestination,
  setAuthUser,
} from "@/lib/auth";
import {
  buildCopiedNotePath,
  clearCopyIntentCookie,
  getCopyIntentCookie,
  PUBLIC_NOTE_COPY_AUTH_INTENT,
} from "@/lib/public-note-copy";
import { setPendingLightweightProfileCompletion } from "@/lib/onboarding-v2";
import { setExamIntentCookie } from "@/lib/exam-intent";
import { getExamHubConfig } from "@/lib/exam-hub-config";
import { suggestEmailCorrection } from "@/lib/email-typo-suggestion";

type Mode = "login" | "signup";
type PendingDeletionCredential = { kind: "password" } | { kind: "google"; code: string };

const EXAM_AUTH_INTENT = "exam";

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
  const [reactivatingAccount, setReactivatingAccount] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendingDeletionCredential, setPendingDeletionCredential] = useState<PendingDeletionCredential | null>(null);
  const emailSuggestion = useMemo(
    () => (mode === "signup" ? suggestEmailCorrection(email) : null),
    [email, mode],
  );
  const hasTrackedSignupStartRef = useRef(false);
  const suppressAuthenticatedRedirectRef = useRef(false);
  const searchKey = searchParams.toString();
  const searchMode = searchParams.get("mode");
  const loginReason = searchParams.get(LOGIN_REASON_QUERY_KEY);
  const authDescription = useMemo(() => {
    if (mode === "login") {
      return "Continue to your study workspace.";
    }
    return searchParams.get("intent") === PUBLIC_NOTE_COPY_AUTH_INTENT
      ? "Sign up to save this note to your library."
      : "Sign up to generate and save Study Packs.";
  }, [mode, searchParams]);
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
      case LOGIN_REASON_PASSWORD_RESET:
        return {
          message: "Your password has been reset. Log in with your new password.",
          className:
            "rounded-md border border-green-300/60 bg-green-50 px-3 py-2 text-sm text-green-800 dark:border-green-700/60 dark:bg-green-950/40 dark:text-green-200",
        };
      default:
        return null;
    }
  }, [loginReason]);

  useEffect(() => {
    setMode(searchMode === "signup" ? "signup" : "login");
  }, [searchKey, searchMode]);

  useEffect(() => {
    if (searchParams.get("intent") !== EXAM_AUTH_INTENT) {
      return;
    }
    const examSlug = searchParams.get("exam");
    if (!examSlug || !getExamHubConfig(examSlug)) {
      return;
    }
    setExamIntentCookie(examSlug);
  }, [searchKey, searchParams]);

  useEffect(() => {
    const syncAuthenticatedUser = () => {
      if (suppressAuthenticatedRedirectRef.current) {
        return;
      }
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
    if (suppressAuthenticatedRedirectRef.current) {
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

  const hydrateAuthUser = useCallback(async (authUser: AuthUser) => {
    setAuthUser(authUser);
    const nextAuthUser = {
      ...authUser,
      planSummary: await getMyPlan().catch(() => null),
    };
    setAuthUser(nextAuthUser);
    return nextAuthUser;
  }, []);

  const completeAuth = useCallback(async (authUser: AuthUser, options?: { clearCopyIntent?: boolean }) => {
    const nextAuthUser = await hydrateAuthUser(authUser);
    if (options?.clearCopyIntent) {
      clearCopyIntentCookie();
    }
    setAuthenticatedUser(nextAuthUser);
    startRouteProgress();
    router.replace(resolvePostLoginDestination(nextAuthUser));
    router.refresh();
  }, [hydrateAuthUser, router, startRouteProgress]);

  const handlePendingDeletionError = useCallback((err: unknown, credential: PendingDeletionCredential) => {
    if (err instanceof ApiRequestError && err.code === ACCOUNT_PENDING_DELETION_CODE) {
      setPendingDeletionCredential(credential);
      setError("This account is scheduled for deletion. Reactivate to keep it.");
      return true;
    }
    return false;
  }, []);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit || loading) {
      return;
    }

    setLoading(true);
    setError(null);
    setPendingDeletionCredential(null);
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
      await completeAuth(authUser, { clearCopyIntent: mode === "login" });
    } catch (err) {
      if (mode === "login" && handlePendingDeletionError(err, { kind: "password" })) {
        return;
      }
      setError(err instanceof Error ? err.message : "Could not continue. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleCredential = useCallback(async (code: string) => {
    if (loading) {
      return;
    }
    setLoading(true);
    setError(null);
    setPendingDeletionCredential(null);
    try {
      suppressAuthenticatedRedirectRef.current = true;
      const authUser = await loginWithGoogle({ code, keepSignedIn });
      const nextAuthUser = await hydrateAuthUser(authUser);
      const copyIntentNoteId = getCopyIntentCookie();
      const isNewGoogleSignup = nextAuthUser.onboardingCompletedAt === null;
      if (isNewGoogleSignup && copyIntentNoteId) {
        try {
          const copied = await copyNoteOnSignup(copyIntentNoteId);
          clearCopyIntentCookie();
          setPendingLightweightProfileCompletion(nextAuthUser.id);
          void trackAnalyticsEvent({
            eventType: "COPY_ON_SIGNUP_COMPLETED",
            entityId: copied.noteId,
            metadata: {
              source: "google_signup",
              publicNoteId: copyIntentNoteId,
            },
          });
          startRouteProgress();
          router.replace(buildCopiedNotePath(copied.noteId, "quick-review", { skipGenerate: true }));
          router.refresh();
          return;
        } catch {
          clearCopyIntentCookie();
        }
      } else if (!isNewGoogleSignup) {
        clearCopyIntentCookie();
      }
      suppressAuthenticatedRedirectRef.current = false;
      setAuthenticatedUser(nextAuthUser);
      startRouteProgress();
      router.replace(resolvePostLoginDestination(nextAuthUser));
      router.refresh();
    } catch (err) {
      suppressAuthenticatedRedirectRef.current = false;
      if (handlePendingDeletionError(err, { kind: "google", code })) {
        return;
      }
      setError(err instanceof Error ? err.message : "Could not continue with Google. Please try again.");
    } finally {
      setLoading(false);
    }
  }, [handlePendingDeletionError, hydrateAuthUser, keepSignedIn, loading, router, startRouteProgress]);

  const handleReactivateAccount = async () => {
    if (!pendingDeletionCredential || reactivatingAccount) {
      return;
    }

    setReactivatingAccount(true);
    setError(null);
    try {
      const authUser = pendingDeletionCredential.kind === "google"
        ? await reactivateAccount({ googleCode: pendingDeletionCredential.code, keepSignedIn })
        : await reactivateAccount({ email, password, keepSignedIn });
      setPendingDeletionCredential(null);
      await completeAuth(authUser, { clearCopyIntent: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not reactivate account. Please try again.");
    } finally {
      setReactivatingAccount(false);
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
          <CardDescription>{authDescription}</CardDescription>
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
            onClick={() => {
              setMode("login");
              setPendingDeletionCredential(null);
            }}
          >
            Log in
          </Button>
          <Button
            type="button"
            variant={mode === "signup" ? "default" : "outline"}
            className="w-full"
            onClick={() => {
              setMode("signup");
              setPendingDeletionCredential(null);
            }}
          >
            Sign up
          </Button>
        </div>

        <div className="space-y-3">
          <GoogleAuthButton
            label="Continue with Google"
            loadingLabel="Continuing with Google..."
            disabled={loading}
            onCredential={handleGoogleCredential}
            onError={setError}
          />
          <div className="flex items-center gap-3 text-xs uppercase tracking-wide text-foreground/45">
            <span className="h-px flex-1 bg-border" />
            <span>or</span>
            <span className="h-px flex-1 bg-border" />
          </div>
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
            {emailSuggestion ? (
              <p className="text-xs text-foreground/70">
                Did you mean{" "}
                <button
                  type="button"
                  className="font-medium text-blue-600 underline underline-offset-2 hover:text-blue-700 dark:text-blue-400"
                  onClick={() => setEmail(emailSuggestion)}
                >
                  {emailSuggestion}
                </button>
                ?
              </p>
            ) : null}
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
            <div className="space-y-2">
              <label className="flex items-start gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={keepSignedIn}
                  onChange={(e) => setKeepSignedIn(e.target.checked)}
                />
                Keep me signed in for 30 days
              </label>
              <div>
                <Link
                  href="/forgot-password"
                  className="text-sm text-foreground/60 underline underline-offset-2 hover:text-foreground"
                >
                  Forgot password?
                </Link>
              </div>
            </div>
          ) : null}

          {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
          {mode === "login" && pendingDeletionCredential ? (
            <div className="space-y-3 rounded-md border border-amber-300/60 bg-amber-50 px-3 py-3 text-sm text-amber-900 dark:border-amber-700/60 dark:bg-amber-950/40 dark:text-amber-100">
              <p>This account is scheduled for deletion. Reactivate within 30 days to keep your notes and study history.</p>
              <Button
                type="button"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={() => void handleReactivateAccount()}
                loading={reactivatingAccount}
                loadingText="Reactivating..."
              >
                Reactivate account
              </Button>
            </div>
          ) : null}

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
      <PublicFooter variant="compact" className="mt-6" />
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
