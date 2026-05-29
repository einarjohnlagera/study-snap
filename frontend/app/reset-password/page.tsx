"use client";

import { FormEvent, Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { PublicFooter } from "@/components/public/public-footer";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { resetPassword } from "@/lib/api";
import { getAuthUser, LOGIN_REASON_PASSWORD_RESET, LOGIN_REASON_QUERY_KEY } from "@/lib/auth";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";

type PageState = "form" | "invalid";

function ResetPasswordContent() {
  const router = useRouter();
  const startRouteProgress = useRouteProgress();
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";

  useEffect(() => {
    if (getAuthUser()) {
      router.replace("/dashboard");
    }
  }, [router]);

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pageState, setPageState] = useState<PageState>(token ? "form" : "invalid");

  const passwordsMatch = newPassword === confirmPassword;
  const canSubmit = newPassword.length >= 8 && confirmPassword.length > 0 && passwordsMatch && !loading;

  const validationMessage = useMemo(() => {
    if (confirmPassword.length > 0 && !passwordsMatch) {
      return "Passwords do not match.";
    }
    return null;
  }, [confirmPassword, passwordsMatch]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      await resetPassword(token, newPassword);
      startRouteProgress();
      router.replace(`/auth?${LOGIN_REASON_QUERY_KEY}=${LOGIN_REASON_PASSWORD_RESET}`);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not reset password. Please try again.";
      if (message.toLowerCase().includes("invalid") || message.toLowerCase().includes("expired")) {
        setPageState("invalid");
      } else {
        setError(message);
      }
    } finally {
      setLoading(false);
    }
  };

  if (pageState === "invalid") {
    return (
      <div className="mx-auto w-full max-w-xl px-4 py-6 sm:px-6 sm:py-10">
        <Card className="space-y-4 p-4 sm:p-6">
          <div className="flex justify-center">
            <BrandFullLogo width={192} height={40} priority />
          </div>
          <div className="space-y-1">
            <CardTitle>Link expired or invalid</CardTitle>
            <CardDescription>
              This password reset link has expired or has already been used.
            </CardDescription>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link href="/forgot-password" className={buttonVariants({ variant: "default" })}>
              Request a new link
            </Link>
            <Link href="/auth" className={buttonVariants({ variant: "outline" })}>
              Back to log in
            </Link>
          </div>
        </Card>
        <PublicFooter variant="compact" className="mt-6" />
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-6 sm:px-6 sm:py-10">
      <Card className="space-y-6 p-4 sm:p-6">
        <div className="flex justify-center">
          <BrandFullLogo width={192} height={40} priority />
        </div>
        <div className="space-y-1">
          <CardTitle>Set a new password</CardTitle>
          <CardDescription>Your new password must be at least 8 characters.</CardDescription>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <label className="block space-y-2">
            <span className="text-sm font-medium">New password</span>
            <input
              type="password"
              className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
              required
              minLength={8}
            />
          </label>
          <label className="block space-y-2">
            <span className="text-sm font-medium">Confirm new password</span>
            <input
              type="password"
              className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
              required
            />
          </label>

          {validationMessage ? (
            <p className="text-sm text-red-600 dark:text-red-400">{validationMessage}</p>
          ) : null}
          {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

          <Button
            type="submit"
            className="w-full sm:w-auto"
            disabled={!canSubmit}
            loading={loading}
            loadingText="Resetting password..."
          >
            Reset password
          </Button>
        </form>
      </Card>
      <PublicFooter variant="compact" className="mt-6" />
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ResetPasswordContent />
    </Suspense>
  );
}
