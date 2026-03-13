"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { getMe, logout, type MeResponse } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

function formatPlan(planType: MeResponse["planType"]): string {
  return planType.charAt(0) + planType.slice(1).toLowerCase();
}

function SettingsLoading() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="h-6 w-48 animate-pulse rounded bg-foreground/10" />
      <div className="h-4 w-64 animate-pulse rounded bg-foreground/10" />
      <div className="h-10 w-full animate-pulse rounded bg-foreground/10" />
    </Card>
  );
}

export default function SettingsPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [profile, setProfile] = useState<MeResponse | null>(null);
  const [signingOut, setSigningOut] = useState(false);
  const [planMessage, setPlanMessage] = useState<string | null>(null);

  const loadProfile = useCallback(async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      router.replace("/auth");
      return;
    }
    if (!authUser.emailVerifiedAt) {
      router.replace("/verify-email");
      return;
    }
    if (!authUser.profileType) {
      router.replace("/onboarding");
      return;
    }

    setLoading(true);
    setError(null);
    setPlanMessage(null);
    try {
      const me = await getMe();
      setProfile(me);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load settings.";
      setError(message);
      setProfile(null);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  const handleUpgradeClick = () => {
    setPlanMessage("Premium upgrade flow is coming soon.");
  };

  const handleSignOut = async () => {
    setSigningOut(true);
    try {
      await logout();
      router.push("/auth");
      router.refresh();
    } finally {
      setSigningOut(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      {loading ? (
        <SettingsLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not load settings</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <Button type="button" className="w-full sm:w-auto" onClick={() => void loadProfile()}>
            Retry
          </Button>
        </Card>
      ) : profile ? (
        <div className="space-y-6">
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Settings
            </p>
            <h1 className="text-2xl font-semibold sm:text-3xl">Configuration</h1>
            <p className="text-sm text-foreground/75">
              Manage account settings, plan details, and upcoming preferences.
            </p>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Account</h2>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <Button type="button" className="w-full sm:w-auto" onClick={() => void handleSignOut()} disabled={signingOut}>
                {signingOut ? "Signing out..." : "Sign Out"}
              </Button>
              <Button type="button" variant="outline" className="w-full sm:w-auto" disabled>
                Delete Account (Coming Soon)
              </Button>
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Plan</h2>
            <div className="rounded-md border border-border bg-background p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Current Plan</p>
              <p className="mt-2 text-lg font-semibold">{formatPlan(profile.planType)}</p>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <Button type="button" className="w-full sm:w-auto" onClick={handleUpgradeClick}>
                Upgrade to Premium
              </Button>
              {planMessage ? (
                <p className="text-xs text-foreground/60">{planMessage}</p>
              ) : null}
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Preferences</h2>
            <div className="flex items-start justify-between gap-3 rounded-md border border-border bg-background p-3">
              <div>
                <p className="text-sm font-medium">Study reminders</p>
                <p className="text-xs text-foreground/60">Coming soon</p>
              </div>
              <input type="checkbox" disabled />
            </div>
            <div className="flex items-start justify-between gap-3 rounded-md border border-border bg-background p-3">
              <div>
                <p className="text-sm font-medium">Theme</p>
                <p className="text-xs text-foreground/60">Coming soon</p>
              </div>
              <input type="checkbox" disabled />
            </div>
          </Card>
        </div>
      ) : null}
    </main>
  );
}
