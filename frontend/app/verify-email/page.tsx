"use client";

import Link from "next/link";
import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  ApiRequestError,
  copyNoteOnSignup,
  getMe,
  getMyPlan,
  requestEmailVerification,
  trackAnalyticsEvent,
  verifyEmailToken,
} from "@/lib/api";
import { buildLoginPath, getAuthUser, resolveAuthenticatedHome, setAuthUser, type AuthUser } from "@/lib/auth";
import {
  buildCopiedNotePath,
  clearCopyIntentCookie,
  getCopyIntentCookie,
} from "@/lib/public-note-copy";
import { ToastMessage } from "@/components/ui/toast-message";

function VerifyEmailPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");

  const [authUser, setAuthUserState] = useState<AuthUser | null>(null);
  const [verifiedMe, setVerifiedMe] = useState<Awaited<ReturnType<typeof getMe>> | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastTone, setToastTone] = useState<"success" | "error" | "info">("info");

  useEffect(() => {
    const existingAuthUser = getAuthUser();
    setAuthUserState(existingAuthUser);
  }, []);

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

  const handleVerifiedCopyIntent = useCallback(async (verifiedAuthUser: AuthUser): Promise<boolean> => {
    const copyIntentNoteId = getCopyIntentCookie();
    if (!copyIntentNoteId) {
      return false;
    }

    try {
      const copied = await copyNoteOnSignup(copyIntentNoteId);
      clearCopyIntentCookie();
      void trackAnalyticsEvent({
        eventType: "COPY_ON_SIGNUP_COMPLETED",
        entityId: copied.noteId,
        metadata: {
          source: "email_verification",
          publicNoteId: copyIntentNoteId,
        },
      });
      router.replace(buildCopiedNotePath(copied.noteId, "quick-review"));
      return true;
    } catch {
      clearCopyIntentCookie();
      router.replace(resolveAuthenticatedHome(verifiedAuthUser));
      return true;
    }
  }, [router]);

  useEffect(() => {
    const existingAuthUser = getAuthUser();
    setAuthUserState(existingAuthUser);

    if (!token) {
      if (existingAuthUser?.emailVerifiedAt) {
        router.replace(resolveAuthenticatedHome(existingAuthUser));
      }
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    setMessage(null);

    void verifyEmailToken(token)
      .then(async (response) => {
        if (cancelled) {
          return;
        }
        setMessage(response.message);
        setToastTone("success");
        setToastMessage(response.message);
        const activeAuthUser = getAuthUser();
        if (!activeAuthUser) {
          return;
        }

        try {
          const [me, planSummary] = await Promise.all([
            getMe(),
            getMyPlan().catch(() => null),
          ]);
          setVerifiedMe(me);
          const nextAuthUser: AuthUser = {
            ...activeAuthUser,
            email: me.email,
            displayName: me.displayName,
            profileType: me.profileType,
            emailVerifiedAt: me.emailVerifiedAt,
            onboardingCompletedAt: me.onboardingCompletedAt,
            productOnboardingCompletedAt: me.productOnboardingCompletedAt,
            planSummary,
          };
          setAuthUser(nextAuthUser);
          setAuthUserState(nextAuthUser);
          await handleVerifiedCopyIntent(nextAuthUser);
        } catch {
          setVerifiedMe(null);
          const fallbackAuthUser: AuthUser = {
            ...activeAuthUser,
            emailVerifiedAt: new Date().toISOString(),
          };
          setAuthUser(fallbackAuthUser);
          setAuthUserState(fallbackAuthUser);
          await handleVerifiedCopyIntent(fallbackAuthUser);
        }
      })
      .catch((verificationError) => {
        if (cancelled) {
          return;
        }
        setError(
          verificationError instanceof Error
            ? verificationError.message
            : "Could not verify email.",
        );
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [handleVerifiedCopyIntent, router, token]);

  const handleResend = async () => {
    if (!authUser || authUser.emailVerifiedAt || loading) {
      return;
    }
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      await requestEmailVerification();
      setToastTone("success");
      setToastMessage("Verification email sent. Check your inbox or spam folder.");
    } catch (resendError) {
      if (resendError instanceof ApiRequestError && (resendError.code === "VERIFICATION_EMAIL_COOLDOWN" || resendError.status === 429)) {
        setToastTone("info");
        setToastMessage("You can resend again in a moment.");
      } else {
        setError(resendError instanceof Error ? resendError.message : "Could not send verification email.");
      }
    } finally {
      setLoading(false);
    }
  };

  const continueHref = useMemo(() => {
    if (!authUser) {
      return "/login";
    }
    return resolveAuthenticatedHome(authUser);
  }, [authUser]);

  const loginToVerifyHref = useMemo(
    () =>
      buildLoginPath({
        redirectTo: "/verify-email",
      }),
    [],
  );
  const showFirstStudyWelcome = Boolean(
    token
    && !loading
    && authUser?.emailVerifiedAt
    && verifiedMe?.studyPackCount === 0,
  );

  return (
    <div className="mx-auto w-full max-w-xl px-6 py-10">
      <Card className="space-y-5">
        <div className="space-y-2">
          <CardTitle>{showFirstStudyWelcome ? "Welcome to NoteLib!" : "Verify your email"}</CardTitle>
          <CardDescription>
            {showFirstStudyWelcome
              ? "Let’s create your first Study Pack."
              : "Email verification is required to unlock all features."}
          </CardDescription>
        </div>

        {showFirstStudyWelcome ? (
          <p className="text-sm text-foreground/80">
            Your email is verified. Create your first note to generate a Study Pack and start practicing right away.
          </p>
        ) : token ? (
          <p className="text-sm text-foreground/80">
            {loading ? "Verifying your email..." : "Verification request processed."}
          </p>
        ) : (
          <>
            <p className="text-sm text-foreground/80">
              Check your inbox and open your verification link. You can resend the email if needed.
            </p>
            <p className="text-sm text-foreground/50">
              Don&apos;t see it? Check your Spam or Promotions folder.
            </p>
          </>
        )}

        {message ? <p className="text-sm text-emerald-600 dark:text-emerald-400">{message}</p> : null}
        {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

        <div className="flex flex-wrap gap-2">
          {authUser && !authUser.emailVerifiedAt ? (
            <Button type="button" variant="outline" onClick={() => void handleResend()} disabled={loading}>
              {loading ? "Sending..." : "Resend verification email"}
            </Button>
          ) : null}

          {showFirstStudyWelcome ? (
            <>
              <Link href="/notes/new" className={buttonVariants({ variant: "default" })}>
                Create First Note
              </Link>
              <Link href="/dashboard" className={buttonVariants({ variant: "outline" })}>
                Go to Dashboard
              </Link>
            </>
          ) : token && !loading ? (
            <Link href={continueHref} className={buttonVariants({ variant: "default" })}>
              Continue
            </Link>
          ) : null}

          {!authUser ? (
            <Link href={loginToVerifyHref} className={buttonVariants({ variant: "outline" })}>
              Log in to resend email
            </Link>
          ) : null}
        </div>
      </Card>
      {toastMessage ? <ToastMessage message={toastMessage} tone={toastTone} /> : null}
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense
      fallback={(
        <div className="mx-auto w-full max-w-xl px-6 py-10">
          <Card className="space-y-5">
            <div className="space-y-2">
              <CardTitle>Verify your email</CardTitle>
              <CardDescription>Preparing verification page...</CardDescription>
            </div>
          </Card>
        </div>
      )}
    >
      <VerifyEmailPageContent />
    </Suspense>
  );
}
