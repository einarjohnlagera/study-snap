"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import {
  completeOnboardingProfileType,
  getMe,
  type MeResponse,
  type ProfileType,
  updateUserProfile,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { buildPublicProfilePath } from "@/lib/public-note-path";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";

type IdentityForm = {
  firstName: string;
  lastName: string;
  displayName: string;
  email: string;
};

const PROFILE_TYPE_OPTIONS: Array<{ value: ProfileType; label: string }> = [
  { value: "STUDENT", label: "Student" },
  { value: "BOARD_EXAM", label: "Board Exam" },
  { value: "TEACHER", label: "Teacher" },
  { value: "PARENT", label: "Parent" },
  { value: "PROFESSIONAL", label: "Professional" },
];

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
    displayName: "",
    email: "",
  });
  const [savingIdentity, setSavingIdentity] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [selectedProfileType, setSelectedProfileType] = useState<ProfileType | "">("");
  const [shareMessage, setShareMessage] = useState<string | null>(null);

  const loadProfile = useCallback(async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      redirectToLoginWithCurrentDestination(router);
      return;
    }

    setLoading(true);
    setError(null);
    setSaveMessage(null);
    setShareMessage(null);
    try {
      const me = await getMe();
      setProfile(me);
      setIdentityForm({
        firstName: me.firstName ?? "",
        lastName: me.lastName ?? "",
        displayName: me.displayName ?? "",
        email: me.pendingEmail ?? me.email ?? "",
      });
      setSelectedProfileType(me.profileType ?? "");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load profile.";
      setError(message);
      setProfile(null);
      setSelectedProfileType("");
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
    setShareMessage(null);
    setIdentityForm((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const handleSaveProfile = async () => {
    if (savingIdentity) {
      return;
    }
    setSavingIdentity(true);
    setSaveMessage(null);
    setShareMessage(null);
    try {
      const updatedIdentity = await updateUserProfile({
        firstName: identityForm.firstName.trim(),
        lastName: identityForm.lastName.trim(),
        displayName: identityForm.displayName.trim(),
        email: identityForm.email.trim(),
      });
      let updatedProfile = updatedIdentity;
      if (selectedProfileType && selectedProfileType !== updatedIdentity.profileType) {
        updatedProfile = await completeOnboardingProfileType({ profileType: selectedProfileType });
      }

      setProfile(updatedProfile);
      setIdentityForm({
        firstName: updatedProfile.firstName ?? "",
        lastName: updatedProfile.lastName ?? "",
        displayName: updatedProfile.displayName ?? "",
        email: updatedProfile.pendingEmail ?? updatedProfile.email,
      });
      setSelectedProfileType(updatedProfile.profileType ?? selectedProfileType);
      setSaveMessage(
        updatedProfile.pendingEmail
          ? "Please verify your new email address before it replaces your current email."
          : "Profile updated successfully.",
      );
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update profile.";
      setSaveMessage(message);
    } finally {
      setSavingIdentity(false);
    }
  };

  const handleSharePublicProfile = async () => {
    if (!profile?.id) {
      return;
    }
    try {
      const shareUrl = new URL(buildPublicProfilePath(profile.id), window.location.origin).toString();
      await navigator.clipboard.writeText(shareUrl);
      setShareMessage("Public profile link copied.");
    } catch {
      setShareMessage("Could not copy public profile link.");
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
          <PageHeader
            eyebrow="PROFILE"
            title="Profile"
            description="Manage your identity and access your public profile."
          />

          <Card className="space-y-4 p-4 sm:p-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
              <div className="inline-flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xl font-semibold text-white dark:bg-blue-500">
                {avatarLetter}
              </div>
              <div className="min-w-0 space-y-1">
                <h1 className="truncate text-2xl font-semibold tracking-tight">{resolvedDisplayName}</h1>
                <p className="truncate text-sm text-foreground/75">{profile.email}</p>
                {profile.pendingEmail ? (
                  <p className="truncate text-xs text-foreground/60">
                    Pending email change: {profile.pendingEmail}
                  </p>
                ) : null}
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
                <span className="text-sm font-medium">Display Name</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={identityForm.displayName}
                  onChange={(event) => handleIdentityFieldChange("displayName", event.target.value)}
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
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Profile Type</h2>
            <div className="space-y-3 rounded-md border border-border bg-background p-3">
              <label className="block space-y-2">
                <span className="text-xs uppercase tracking-wide text-foreground/60">Current Profile Type</span>
                <select
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={selectedProfileType}
                  onChange={(event) => {
                    setSaveMessage(null);
                    setShareMessage(null);
                    setSelectedProfileType(event.target.value as ProfileType);
                  }}
                >
                  {PROFILE_TYPE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Public Profile</h2>
            <p className="text-sm text-foreground/75">
              View or share the public page that showcases your display name and public notes.
            </p>
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleSaveProfile()}
                disabled={savingIdentity || !selectedProfileType}
              >
                {savingIdentity ? "Saving..." : "Save Profile"}
              </Button>
              <Link href={buildPublicProfilePath(profile.id)} className="w-full sm:w-auto">
                <Button type="button" variant="outline" className="w-full sm:w-auto">
                  View Public Profile
                </Button>
              </Link>
              <Button
                type="button"
                variant="outline"
                className="w-full sm:w-auto"
                onClick={() => void handleSharePublicProfile()}
              >
                Share Public Profile
              </Button>
            </div>
            {saveMessage ? (
              <p className="text-xs text-foreground/60">{saveMessage}</p>
            ) : null}
            {shareMessage ? (
              <p className="text-xs text-foreground/60">{shareMessage}</p>
            ) : null}
          </Card>
        </div>
      ) : null}
    </main>
  );
}
