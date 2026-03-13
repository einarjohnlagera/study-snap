"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { getMe, logout, type MeResponse } from "@/lib/api";
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

function formatPlan(value: MeResponse["planType"]): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

function formatStatus(value: MeResponse["status"]): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

function ProfileLoading() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="h-16 w-16 animate-pulse rounded-full bg-foreground/10" />
      <div className="h-6 w-48 animate-pulse rounded bg-foreground/10" />
      <div className="h-4 w-64 animate-pulse rounded bg-foreground/10" />
    </Card>
  );
}

export default function ProfilePage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [profile, setProfile] = useState<MeResponse | null>(null);
  const [signingOut, setSigningOut] = useState(false);

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
      const message = err instanceof Error ? err.message : "Could not load profile.";
      setError(message);
      setProfile(null);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  const resolvedDisplayName = useMemo(() => {
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

  const avatarLetter = useMemo(() => {
    const fallback = resolvedDisplayName || profile?.email || "U";
    return fallback.charAt(0).toUpperCase();
  }, [profile?.email, resolvedDisplayName]);

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
        <ProfileLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not load profile</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <Button type="button" className="w-full sm:w-auto" onClick={() => void loadProfile()}>
            Retry
          </Button>
        </Card>
      ) : profile ? (
        <div className="space-y-6">
          <Card className="space-y-4 p-4 sm:p-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
              <div className="inline-flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xl font-semibold text-white dark:bg-blue-500">
                {avatarLetter}
              </div>
              <div className="min-w-0 space-y-1">
                <h1 className="truncate text-2xl font-semibold tracking-tight">{resolvedDisplayName}</h1>
                <p className="truncate text-sm text-foreground/75">{profile.email}</p>
              </div>
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Account Information</h2>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-md border border-border bg-background p-3">
                <p className="text-xs uppercase tracking-wide text-foreground/60">Profile Type</p>
                <p className="mt-1 font-medium">{formatProfileType(profile.profileType)}</p>
              </div>
              <div className="rounded-md border border-border bg-background p-3">
                <p className="text-xs uppercase tracking-wide text-foreground/60">Plan</p>
                <p className="mt-1 font-medium">{formatPlan(profile.planType)}</p>
              </div>
              <div className="rounded-md border border-border bg-background p-3">
                <p className="text-xs uppercase tracking-wide text-foreground/60">Member Since</p>
                <p className="mt-1 font-medium text-foreground/75">Not available yet</p>
              </div>
              <div className="rounded-md border border-border bg-background p-3">
                <p className="text-xs uppercase tracking-wide text-foreground/60">Email Verified</p>
                <p className="mt-1 font-medium">
                  {profile.emailVerifiedAt ? new Date(profile.emailVerifiedAt).toLocaleString() : "Not verified"}
                </p>
              </div>
              <div className="rounded-md border border-border bg-background p-3 sm:col-span-2">
                <p className="text-xs uppercase tracking-wide text-foreground/60">Account Status</p>
                <p className="mt-1 font-medium">{formatStatus(profile.status)}</p>
              </div>
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Actions</h2>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button type="button" variant="outline" className="w-full sm:w-auto" disabled>
                Edit Profile (Coming Soon)
              </Button>
              <Button type="button" variant="outline" className="w-full sm:w-auto" disabled>
                Manage Plan (Coming Soon)
              </Button>
              <Button type="button" className="w-full sm:w-auto" onClick={() => void handleSignOut()} disabled={signingOut}>
                {signingOut ? "Signing out..." : "Sign Out"}
              </Button>
            </div>
          </Card>
        </div>
      ) : null}
    </main>
  );
}
