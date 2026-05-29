"use client";

import { FormEvent, Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { PublicFooter } from "@/components/public/public-footer";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { forgotPassword } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

function ForgotPasswordContent() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    if (getAuthUser()) {
      router.replace("/dashboard");
    }
  }, [router]);

  const canSubmit = email.trim().length > 0 && !loading;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      await forgotPassword(email.trim());
      setSubmitted(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send reset email. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-6 sm:px-6 sm:py-10">
      <Card className="space-y-6 p-4 sm:p-6">
        <div className="flex justify-center">
          <BrandFullLogo width={192} height={40} priority />
        </div>

        {submitted ? (
          <div className="space-y-4">
            <div>
              <CardTitle>Check your email</CardTitle>
              <CardDescription className="mt-1">
                If an account exists for <strong>{email}</strong>, you will receive a password reset link shortly.
              </CardDescription>
            </div>
            <p className="text-sm text-foreground/70">
              Didn&apos;t receive the email? Check your spam folder, or{" "}
              <button
                type="button"
                className="underline underline-offset-2 hover:text-foreground"
                onClick={() => setSubmitted(false)}
              >
                try again
              </button>
              .
            </p>
            <Link
              href="/auth"
              className="block text-sm text-foreground/70 underline underline-offset-2 hover:text-foreground"
            >
              Back to log in
            </Link>
          </div>
        ) : (
          <>
            <div className="space-y-1">
              <CardTitle>Forgot your password?</CardTitle>
              <CardDescription>
                Enter your email and we&apos;ll send you a link to reset your password.
              </CardDescription>
            </div>

            <form className="space-y-4" onSubmit={handleSubmit}>
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

              {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

              <Button
                type="submit"
                className="w-full sm:w-auto"
                disabled={!canSubmit}
                loading={loading}
                loadingText="Sending link..."
              >
                Send reset link
              </Button>
            </form>

            <Link
              href="/auth"
              className="block text-sm text-foreground/70 underline underline-offset-2 hover:text-foreground"
            >
              Back to log in
            </Link>
          </>
        )}
      </Card>
      <PublicFooter variant="compact" className="mt-6" />
    </div>
  );
}

export default function ForgotPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ForgotPasswordContent />
    </Suspense>
  );
}
