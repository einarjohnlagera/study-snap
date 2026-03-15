"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { getMe, type MeResponse } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";

type IdentityForm = {
  firstName: string;
  lastName: string;
  email: string;
};

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
  const [identityForm, setIdentityForm] = useState<IdentityForm>({
    firstName: "",
    lastName: "",
    email: "",
  });
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  const loadProfile = useCallback(async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      redirectToLoginWithCurrentDestination(router);
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
    setSaveMessage(null);
    try {
      const me = await getMe();
      setProfile(me);
      setIdentityForm({
        firstName: me.firstName ?? "",
        lastName: me.lastName ?? "",
        email: me.email ?? "",
      });
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

  const handleIdentityFieldChange = (field: keyof IdentityForm, value: string) => {
    setSaveMessage(null);
    setIdentityForm((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const handleSaveIdentity = () => {
    // Placeholder flow until profile update endpoint is available.
    setSaveMessage("Profile updates are not connected yet. Changes are local for this session.");
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
          <PageHeader
            eyebrow="PROFILE"
            title="Profile"
            description="Manage your personal information and profile type."
          />

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
            <h2 className="text-lg font-semibold sm:text-xl">Identity</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block space-y-2">
                <span className="text-sm font-medium">First Name</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={identityForm.firstName}
                  onChange={(event) => handleIdentityFieldChange("firstName", event.target.value)}
                />
              </label>
              <label className="block space-y-2">
                <span className="text-sm font-medium">Last Name</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={identityForm.lastName}
                  onChange={(event) => handleIdentityFieldChange("lastName", event.target.value)}
                />
              </label>
              <label className="block space-y-2 sm:col-span-2">
                <span className="text-sm font-medium">Email</span>
                <input
                  type="email"
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={identityForm.email}
                  onChange={(event) => handleIdentityFieldChange("email", event.target.value)}
                />
              </label>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <Button type="button" className="w-full sm:w-auto" onClick={handleSaveIdentity}>
                Save Identity
              </Button>
              {saveMessage ? (
                <p className="text-xs text-foreground/60">{saveMessage}</p>
              ) : null}
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Profile Type</h2>
            <div className="rounded-md border border-border bg-background p-3">
              <p className="text-xs uppercase tracking-wide text-foreground/60">Current Profile Type</p>
              <p className="mt-1 font-medium">{formatProfileType(profile.profileType)}</p>
            </div>
          </Card>
        </div>
      ) : null}
    </main>
  );
}
