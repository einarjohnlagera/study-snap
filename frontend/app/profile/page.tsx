"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import {
  completeOnboardingProfileType,
  getMe,
  type LearnerLevel,
  type MeResponse,
  type ProfileType,
  updateUserProfile,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { COURSE_PROGRAM_SUGGESTIONS, LEARNER_LEVEL_OPTIONS } from "@/lib/learning-profile";
import { buildPublicProfilePath } from "@/lib/public-note-path";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";

type IdentityForm = {
  firstName: string;
  lastName: string;
  displayName: string;
  email: string;
};

type LearningProfileForm = {
  learnerLevel: LearnerLevel | "";
  courseProgram: string;
  bio: string;
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
  const [learningProfileForm, setLearningProfileForm] = useState<LearningProfileForm>({
    learnerLevel: "",
    courseProgram: "",
    bio: "",
  });
  const [savingIdentity, setSavingIdentity] = useState(false);
  const [identityMessage, setIdentityMessage] = useState<string | null>(null);
  const [savingProfileType, setSavingProfileType] = useState(false);
  const [profileTypeMessage, setProfileTypeMessage] = useState<string | null>(null);
  const [selectedProfileType, setSelectedProfileType] = useState<ProfileType | "">("");
  const [savingLearningProfile, setSavingLearningProfile] = useState(false);
  const [learningProfileMessage, setLearningProfileMessage] = useState<string | null>(null);

  const loadProfile = useCallback(async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      redirectToLoginWithCurrentDestination(router);
      return;
    }

    setLoading(true);
    setError(null);
    setIdentityMessage(null);
    setProfileTypeMessage(null);
    setLearningProfileMessage(null);
    try {
      const me = await getMe();
      setProfile(me);
      setIdentityForm({
        firstName: me.firstName ?? "",
        lastName: me.lastName ?? "",
        displayName: me.displayName ?? "",
        email: me.pendingEmail ?? me.email ?? "",
      });
      setLearningProfileForm({
        learnerLevel: me.learnerLevel ?? "",
        courseProgram: me.courseProgram ?? "",
        bio: me.bio ?? "",
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
    setIdentityMessage(null);
    setIdentityForm((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const handleLearningProfileFieldChange = (
    field: keyof LearningProfileForm,
    value: string,
  ) => {
    setLearningProfileMessage(null);
    setLearningProfileForm((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const buildProfileUpdateRequest = () => ({
    firstName: identityForm.firstName.trim(),
    lastName: identityForm.lastName.trim(),
    displayName: identityForm.displayName.trim(),
    bio: learningProfileForm.bio.trim(),
    learnerLevel: learningProfileForm.learnerLevel || null,
    courseProgram: learningProfileForm.courseProgram.trim(),
    email: identityForm.email.trim(),
  });

  const handleSaveIdentity = async () => {
    if (savingIdentity) {
      return;
    }
    setSavingIdentity(true);
    setIdentityMessage(null);
    try {
      const updatedIdentity = await updateUserProfile(buildProfileUpdateRequest());

      setProfile(updatedIdentity);
      setIdentityForm({
        firstName: updatedIdentity.firstName ?? "",
        lastName: updatedIdentity.lastName ?? "",
        displayName: updatedIdentity.displayName ?? "",
        email: updatedIdentity.pendingEmail ?? updatedIdentity.email,
      });
      setLearningProfileForm({
        learnerLevel: updatedIdentity.learnerLevel ?? "",
        courseProgram: updatedIdentity.courseProgram ?? "",
        bio: updatedIdentity.bio ?? "",
      });
      setSelectedProfileType(updatedIdentity.profileType ?? selectedProfileType);
      setIdentityMessage(
        updatedIdentity.pendingEmail
          ? "Please verify your new email address before it replaces your current email."
          : "Identity updated successfully.",
      );
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update profile.";
      setIdentityMessage(message);
    } finally {
      setSavingIdentity(false);
    }
  };

  const handleSaveLearningProfile = async () => {
    if (savingLearningProfile) {
      return;
    }
    setSavingLearningProfile(true);
    setLearningProfileMessage(null);
    try {
      const updatedProfile = await updateUserProfile(buildProfileUpdateRequest());
      setProfile(updatedProfile);
      setIdentityForm({
        firstName: updatedProfile.firstName ?? "",
        lastName: updatedProfile.lastName ?? "",
        displayName: updatedProfile.displayName ?? "",
        email: updatedProfile.pendingEmail ?? updatedProfile.email,
      });
      setLearningProfileForm({
        learnerLevel: updatedProfile.learnerLevel ?? "",
        courseProgram: updatedProfile.courseProgram ?? "",
        bio: updatedProfile.bio ?? "",
      });
      setSelectedProfileType(updatedProfile.profileType ?? selectedProfileType);
      setLearningProfileMessage("Learning profile updated successfully.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update learning profile.";
      setLearningProfileMessage(message);
    } finally {
      setSavingLearningProfile(false);
    }
  };

  const handleSaveProfileType = async () => {
    if (savingProfileType || !selectedProfileType || !profile) {
      return;
    }
    if (selectedProfileType === profile.profileType) {
      setProfileTypeMessage("Profile type is already up to date.");
      return;
    }

    setSavingProfileType(true);
    setProfileTypeMessage(null);
    try {
      const updatedProfile = await completeOnboardingProfileType({ profileType: selectedProfileType });
      setProfile(updatedProfile);
      setSelectedProfileType(updatedProfile.profileType ?? selectedProfileType);
      setProfileTypeMessage("Profile type updated successfully.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update profile type.";
      setProfileTypeMessage(message);
    } finally {
      setSavingProfileType(false);
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
          <ResponsiveActionButton type="button" className="w-full sm:w-auto" onClick={() => void loadProfile()} action="retry" label="Retry" />
        </Card>
      ) : profile ? (
        <div className="space-y-6">
          <PageHeader
            eyebrow="PROFILE"
            title="Profile"
            description="Manage your identity and access your public profile."
          />

          <Card className="space-y-4 p-4 sm:p-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex min-w-0 items-center gap-4">
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
              <ResponsiveActionLink
                href={buildPublicProfilePath(profile.id)}
                action="open"
                label="View Public Page"
                variant="outline"
                className="w-full sm:w-auto"
              />
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
            <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
              {identityMessage ? <p className="text-xs text-foreground/60">{identityMessage}</p> : <div />}
              <ResponsiveActionButton
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleSaveIdentity()}
                disabled={savingIdentity}
                action="save"
                label={savingIdentity ? "Saving..." : "Save Identity"}
              />
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
                    setProfileTypeMessage(null);
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
            <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
              {profileTypeMessage ? (
                <p className="text-xs text-foreground/60">{profileTypeMessage}</p>
              ) : (
                <p className="text-xs text-foreground/60">
                  Profile Type shapes dashboard emphasis and study guidance only.
                </p>
              )}
              <ResponsiveActionButton
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleSaveProfileType()}
                disabled={savingProfileType || !selectedProfileType}
                action="save"
                label={savingProfileType ? "Saving..." : "Save Profile Type"}
              />
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Learning Profile</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block space-y-2">
                <span className="text-sm font-medium">Learner Level</span>
                <select
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={learningProfileForm.learnerLevel}
                  onChange={(event) => handleLearningProfileFieldChange("learnerLevel", event.target.value)}
                >
                  <option value="">Select learner level</option>
                  {LEARNER_LEVEL_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block space-y-2">
                <span className="text-sm font-medium">Course / Program</span>
                <input
                  list="course-program-suggestions"
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={learningProfileForm.courseProgram}
                  onChange={(event) => handleLearningProfileFieldChange("courseProgram", event.target.value.slice(0, 120))}
                />
                <datalist id="course-program-suggestions">
                  {COURSE_PROGRAM_SUGGESTIONS.map((option) => (
                    <option key={option} value={option} />
                  ))}
                </datalist>
              </label>
              <label className="block space-y-2 sm:col-span-2">
                <span className="text-sm font-medium">Bio</span>
                <textarea
                  className="min-h-24 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                  aria-label="Bio"
                  value={learningProfileForm.bio}
                  onChange={(event) => handleLearningProfileFieldChange("bio", event.target.value.slice(0, 200))}
                  maxLength={200}
                />
                <p className="text-xs text-foreground/60">{learningProfileForm.bio.length}/200 characters</p>
              </label>
            </div>
            <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
              {learningProfileMessage ? (
                <p className="text-xs text-foreground/60">{learningProfileMessage}</p>
              ) : (
                <p className="text-xs text-foreground/60">
                  Learner level helps NoteLib adjust quiz difficulty and recommendations.
                </p>
              )}
              <ResponsiveActionButton
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleSaveLearningProfile()}
                disabled={savingLearningProfile}
                action="save"
                label={savingLearningProfile ? "Saving..." : "Save Learning Profile"}
              />
            </div>
          </Card>
        </div>
      ) : null}
    </main>
  );
}
