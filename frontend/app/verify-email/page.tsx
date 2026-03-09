"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { confirmEmailVerification, requestEmailVerification } from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";

export default function VerifyEmailPage() {
  const router = useRouter();
  const [authUserId, setAuthUserId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser) {
      router.replace("/auth");
      return;
    }
    if (authUser.emailVerifiedAt) {
      router.replace(authUser.profileType ? "/dashboard" : "/onboarding");
      return;
    }
    setAuthUserId(authUser.id);
  }, [router]);

  const canSubmit = useMemo(() => authUserId !== null && !loading, [authUserId, loading]);

  const handleResend = async () => {
    if (!canSubmit) {
      return;
    }
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const response = await requestEmailVerification();
      setMessage(response.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send verification email.");
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = async () => {
    if (!canSubmit || !authUserId) {
      return;
    }
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const me = await confirmEmailVerification({ token: authUserId });
      const authUser = getAuthUser();
      if (authUser) {
        setAuthUser({
          ...authUser,
          emailVerifiedAt: me.emailVerifiedAt,
          profileType: me.profileType,
          displayName: me.displayName,
        });
      }
      router.push(me.profileType ? "/dashboard" : "/onboarding");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not verify email.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-xl px-6 py-10">
      <Card className="space-y-5">
        <div className="space-y-2">
          <CardTitle>Verify your email</CardTitle>
          <CardDescription>
            Check your inbox and verify your email before generating a Study Pack.
          </CardDescription>
        </div>
        <p className="text-sm text-foreground/70">
          Placeholder flow in local/dev: use the confirm button below while tokenized verification is being integrated.
        </p>
        {message ? <p className="text-sm text-emerald-600 dark:text-emerald-400">{message}</p> : null}
        {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" onClick={() => void handleResend()} disabled={!canSubmit}>
            Resend verification email
          </Button>
          <Button type="button" onClick={() => void handleConfirm()} disabled={!canSubmit}>
            I have verified my email
          </Button>
        </div>
      </Card>
    </div>
  );
}
