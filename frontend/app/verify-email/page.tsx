"use client";

import Link from "next/link";
import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { ApiRequestError, getMe, requestEmailVerification, verifyEmailToken } from "@/lib/api";
import { buildLoginPath, getAuthUser, setAuthUser, type AuthUser } from "@/lib/auth";
import { ToastMessage } from "@/components/ui/toast-message";

function VerifyEmailPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");

  const [authUser, setAuthUserState] = useState<AuthUser | null>(null);
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
    const timeout = window.setTimeout(() => {
      setToastMessage(null);
    }, 3200);
    return () => {
      window.clearTimeout(timeout);
    };
  }, [toastMessage]);

  useEffect(() => {
    const existingAuthUser = getAuthUser();
    setAuthUserState(existingAuthUser);

    if (!token) {
      if (existingAuthUser?.emailVerifiedAt) {
        router.replace(existingAuthUser.profileType ? "/dashboard" : "/onboarding");
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
        setToastMessage("Your email has been verified. You can now generate Study Packs.");
        const activeAuthUser = getAuthUser();
        if (!activeAuthUser) {
          return;
        }

        try {
          const me = await getMe();
          const nextAuthUser: AuthUser = {
            ...activeAuthUser,
            displayName: me.displayName,
            profileType: me.profileType,
            emailVerifiedAt: me.emailVerifiedAt,
          };
          setAuthUser(nextAuthUser);
          setAuthUserState(nextAuthUser);
        } catch {
          const fallbackAuthUser: AuthUser = {
            ...activeAuthUser,
            emailVerifiedAt: new Date().toISOString(),
          };
          setAuthUser(fallbackAuthUser);
          setAuthUserState(fallbackAuthUser);
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
  }, [router, token]);

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
      setToastMessage("Verification email sent. Check your inbox.");
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
    return authUser.profileType ? "/dashboard" : "/onboarding";
  }, [authUser]);

  const loginToVerifyHref = useMemo(
    () =>
      buildLoginPath({
        redirectTo: "/verify-email",
      }),
    [],
  );

  return (
    <div className="mx-auto w-full max-w-xl px-6 py-10">
      <Card className="space-y-5">
        <div className="space-y-2">
          <CardTitle>Verify your email</CardTitle>
          <CardDescription>
            Email verification is required before generating Study Packs.
          </CardDescription>
        </div>

        {token ? (
          <p className="text-sm text-foreground/80">
            {loading ? "Verifying your email..." : "Verification request processed."}
          </p>
        ) : (
          <p className="text-sm text-foreground/80">
            Check your inbox and open your verification link. You can resend the email if needed.
          </p>
        )}

        {message ? <p className="text-sm text-emerald-600 dark:text-emerald-400">{message}</p> : null}
        {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

        <div className="flex flex-wrap gap-2">
          {authUser && !authUser.emailVerifiedAt ? (
            <Button type="button" variant="outline" onClick={() => void handleResend()} disabled={loading}>
              {loading ? "Sending..." : "Resend verification email"}
            </Button>
          ) : null}

          {token && !loading ? (
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
