"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ThemeToggle } from "@/components/theme-toggle";
import { getMe, type MeResponse } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

function formatProfileType(value: MeResponse["profileType"]): string {
  if (!value) {
    return "Not set";
  }
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
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

  const displayName = useMemo(() => {
    if (!profile) {
      return "";
    }
    if (profile.displayName?.trim()) {
      return profile.displayName.trim();
    }
    if (profile.firstName?.trim()) {
      return profile.firstName.trim();
    }
    return profile.email;
  }, [profile]);

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
            <h1 className="text-2xl font-semibold sm:text-3xl">Account & Preferences</h1>
            <p className="text-sm text-foreground/75">
              Manage your profile details and application preferences.
            </p>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Account</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block space-y-2">
                <span className="text-sm font-medium">Display Name</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={displayName}
                  disabled
                />
              </label>
              <label className="block space-y-2">
                <span className="text-sm font-medium">Profile Type</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={formatProfileType(profile.profileType)}
                  disabled
                />
              </label>
            </div>
            <p className="text-xs text-foreground/60">
              Profile editing will be available soon.
            </p>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Preferences</h2>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-medium">Theme</p>
                <p className="text-xs text-foreground/60">Switch between light and dark mode.</p>
              </div>
              <ThemeToggle />
            </div>
            <div className="flex items-start justify-between gap-3 rounded-md border border-border bg-background p-3">
              <div>
                <p className="text-sm font-medium">Study reminders</p>
                <p className="text-xs text-foreground/60">Coming soon</p>
              </div>
              <input type="checkbox" disabled />
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Security</h2>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button type="button" variant="outline" className="w-full sm:w-auto" disabled>
                Change Password (Coming Soon)
              </Button>
              <Button type="button" variant="outline" className="w-full sm:w-auto" disabled>
                Manage Sessions (Coming Soon)
              </Button>
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Notifications</h2>
            <div className="flex items-start justify-between gap-3 rounded-md border border-border bg-background p-3">
              <div>
                <p className="text-sm font-medium">Product updates</p>
                <p className="text-xs text-foreground/60">Coming soon</p>
              </div>
              <input type="checkbox" disabled />
            </div>
            <div className="flex items-start justify-between gap-3 rounded-md border border-border bg-background p-3">
              <div>
                <p className="text-sm font-medium">Study reminders</p>
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
