"use client";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/page-header";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { SuggestionCombobox } from "@/components/ui/suggestion-combobox";
import { AppModal } from "@/components/ui/app-modal";
import { ToastMessage } from "@/components/ui/toast-message";
import {
  completeOnboardingProfileType,
  getMe,
  listCoursePrograms,
  type LearnerLevel,
  type MeResponse,
  type ProfileType,
  updateUserProfile,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import {
  COURSE_PROGRAM_SUGGESTIONS,
  LEARNER_LEVEL_OPTIONS,
  mergeCourseProgramSuggestions,
} from "@/lib/learning-profile";
import {
  DISABLED_PROFILE_TYPES,
  getProfileTypeSwitchContent,
  isActiveProfileTypeForSwitch,
} from "@/lib/profile-mode";
import { BackLink } from "@/components/ui/back-link";
import { buildPublicProfilePath } from "@/lib/public-note-path";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";
import { ProfileNotePerformance } from "@/components/profile/profile-note-performance";

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

type LearningProfileErrors = {
  learnerLevel?: string;
  courseProgram?: string;
};

type ProfileTypeOption = {
  value: ProfileType;
  label: string;
  description: string;
  disabled?: boolean;
};

const PROFILE_TYPE_OPTIONS: ProfileTypeOption[] = [
  {
    value: "STUDENT",
    label: "Student",
    description: "Active learning, quiz tracking, and study recall.",
  },
  {
    value: "BOARD_EXAM",
    label: "Board Taker",
    description: "Exam prep focus with performance tracking and weak-concept review.",
  },
  {
    value: "TEACHER",
    label: "Teacher",
    description: "Create and export quiz materials from your notes.",
  },
  {
    value: "PARENT",
    label: "Parent",
    description: "Student support and learning oversight.",
    disabled: true,
  },
  {
    value: "PROFESSIONAL",
    label: "Professional",
    description: "Interview prep and applied knowledge review.",
    disabled: true,
  },
];

function ProfileLoading() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <Skeleton className="h-16 w-16 rounded-full" />
      <Skeleton className="h-6 w-48" />
      <Skeleton className="h-4 w-64" />
    </Card>
  );
}

type ProfileTypeSwitchModalProps = {
  pendingProfileType: ProfileType | null;
  saving: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

function ProfileTypeSwitchModal({
  pendingProfileType,
  saving,
  onCancel,
  onConfirm,
}: ProfileTypeSwitchModalProps) {
  if (!pendingProfileType || !isActiveProfileTypeForSwitch(pendingProfileType)) {
    return null;
  }
  const content = getProfileTypeSwitchContent(pendingProfileType);
  return (
    <AppModal
      isOpen={true}
      title={content.title}
      onClose={onCancel}
      actions={(
        <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Button
            type="button"
            variant="outline"
            className="w-full sm:w-auto"
            onClick={onCancel}
          >
            Cancel
          </Button>
          <Button
            type="button"
            className="w-full sm:w-auto"
            onClick={onConfirm}
            loading={saving}
            loadingText="Switching..."
          >
            Switch
          </Button>
        </div>
      )}
    >
      <div className="space-y-3">
        {content.body.map((line) => (
          <p key={line} className="text-sm text-foreground/80">{line}</p>
        ))}
        <p className="text-xs text-foreground/60">{content.note}</p>
      </div>
    </AppModal>
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
  const [pendingProfileType, setPendingProfileType] = useState<ProfileType | null>(null);
  const [profileTypeSwitchToast, setProfileTypeSwitchToast] = useState<string | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [savingLearningProfile, setSavingLearningProfile] = useState(false);
  const [learningProfileMessage, setLearningProfileMessage] = useState<string | null>(null);
  const [learningProfileErrors, setLearningProfileErrors] = useState<LearningProfileErrors>({});
  const [courseProgramSuggestions, setCourseProgramSuggestions] = useState<string[]>([]);

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
    setLearningProfileErrors({});
    try {
      const [meResult, courseProgramsResult] = await Promise.allSettled([
        getMe(),
        listCoursePrograms("mine"),
      ]);
      if (meResult.status !== "fulfilled") {
        throw meResult.reason;
      }
      const me = meResult.value;
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
      setCourseProgramSuggestions(courseProgramsResult.status === "fulfilled" ? courseProgramsResult.value : []);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load profile.";
      setError(message);
      setProfile(null);
      setSelectedProfileType("");
      setCourseProgramSuggestions([]);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  useEffect(() => {
    const ref = toastTimerRef;
    return () => {
      if (ref.current) {
        clearTimeout(ref.current);
      }
    };
  }, []);

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

  const availableCourseProgramSuggestions = useMemo(
    () => mergeCourseProgramSuggestions(
      COURSE_PROGRAM_SUGGESTIONS,
      courseProgramSuggestions,
      [learningProfileForm.courseProgram],
      [profile?.courseProgram],
    ),
    [courseProgramSuggestions, learningProfileForm.courseProgram, profile?.courseProgram],
  );

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
    setLearningProfileErrors((current) => ({
      ...current,
      ...(field === "learnerLevel" ? { learnerLevel: undefined } : {}),
      ...(field === "courseProgram" ? { courseProgram: undefined } : {}),
    }));
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

  const validateLearningProfileForm = () => {
    const nextErrors: LearningProfileErrors = {};

    if (!learningProfileForm.learnerLevel) {
      nextErrors.learnerLevel = "Please select your learner level.";
    }
    if (!learningProfileForm.courseProgram.trim()) {
      nextErrors.courseProgram = "Please select or enter your course / program.";
    }

    setLearningProfileErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

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
    if (!validateLearningProfileForm()) {
      setLearningProfileMessage(null);
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
      setLearningProfileErrors({});
      setLearningProfileMessage("Learning profile updated successfully.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update learning profile.";
      setLearningProfileMessage(message);
    } finally {
      setSavingLearningProfile(false);
    }
  };

  const handleSaveProfileType = () => {
    if (savingProfileType || !selectedProfileType || !profile) {
      return;
    }
    if (selectedProfileType === profile.profileType) {
      setProfileTypeMessage("Profile type is already up to date.");
      return;
    }
    if (!isActiveProfileTypeForSwitch(selectedProfileType)) {
      return;
    }
    // Show the confirmation modal before committing the switch.
    setPendingProfileType(selectedProfileType);
    setProfileTypeMessage(null);
  };

  const handleConfirmProfileTypeSwitch = async () => {
    if (!pendingProfileType || !isActiveProfileTypeForSwitch(pendingProfileType)) {
      return;
    }
    setSavingProfileType(true);
    const targetType = pendingProfileType;
    setPendingProfileType(null);
    try {
      const updatedProfile = await completeOnboardingProfileType({ profileType: targetType });
      setProfile(updatedProfile);
      setSelectedProfileType(updatedProfile.profileType ?? targetType);

      // Show a mode-specific success toast.
      const content = getProfileTypeSwitchContent(targetType);
      if (toastTimerRef.current) {
        clearTimeout(toastTimerRef.current);
      }
      setProfileTypeSwitchToast(content.toast);
      toastTimerRef.current = setTimeout(() => {
        setProfileTypeSwitchToast(null);
      }, 4000);
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
          <BackLink href={buildPublicProfilePath(profile.id)} label="Profile" />
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
                loading={savingIdentity}
                loadingText="Saving..."
                action="save"
                label="Save Identity"
              />
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Profile Type</h2>
            <div className="space-y-2">
              {PROFILE_TYPE_OPTIONS.map((option) => {
                const isDisabled = option.disabled === true || (DISABLED_PROFILE_TYPES as readonly string[]).includes(option.value);
                const isSelected = selectedProfileType === option.value;
                if (isDisabled) {
                  return (
                    <div
                      key={option.value}
                      className="flex cursor-not-allowed items-start gap-3 rounded-xl border border-border bg-muted/10 px-4 py-3 opacity-50"
                      aria-disabled="true"
                    >
                      <div className="mt-1 h-4 w-4 shrink-0 rounded-full border-2 border-border" />
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-sm font-medium text-foreground">{option.label}</span>
                          <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-foreground/60">
                            Coming Soon
                          </span>
                        </div>
                        <p className="text-xs text-foreground/60">{option.description}</p>
                      </div>
                    </div>
                  );
                }
                return (
                  <button
                    key={option.value}
                    type="button"
                    className={`flex w-full items-start gap-3 rounded-xl border px-4 py-3 text-left transition-colors hover:bg-highlight ${
                      isSelected
                        ? "border-primary bg-primary/5 dark:bg-primary/10"
                        : "border-border bg-background"
                    }`}
                    onClick={() => {
                      setProfileTypeMessage(null);
                      setSelectedProfileType(option.value);
                    }}
                    aria-pressed={isSelected}
                  >
                    <div
                      className={`mt-1 h-4 w-4 shrink-0 rounded-full border-2 transition-colors ${
                        isSelected ? "border-primary bg-primary" : "border-border"
                      }`}
                    />
                    <div className="min-w-0 flex-1">
                      <span className="text-sm font-medium text-foreground">{option.label}</span>
                      <p className="text-xs text-foreground/60">{option.description}</p>
                    </div>
                  </button>
                );
              })}
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
                onClick={handleSaveProfileType}
                disabled={savingProfileType || !selectedProfileType}
                loading={savingProfileType}
                loadingText="Saving..."
                action="save"
                label="Save Profile Type"
              />
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Learning Profile</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block space-y-2">
                <span className="text-sm font-medium">Learner Level</span>
                <SuggestionCombobox
                  id="profile-learner-level"
                  value={learningProfileForm.learnerLevel}
                  options={LEARNER_LEVEL_OPTIONS}
                  ariaLabel="Learner Level"
                  onChange={(value) =>
                    handleLearningProfileFieldChange("learnerLevel", value as LearnerLevel | "")
                  }
                  placeholder="Choose learner level"
                  helperText="Choose the option that best matches your current study stage."
                  allowCustom={false}
                  toggleLabel="Toggle learner level suggestions"
                />
                {learningProfileErrors.learnerLevel ? (
                  <p className="text-xs text-red-600 dark:text-red-400">{learningProfileErrors.learnerLevel}</p>
                ) : null}
              </label>
              <label className="block space-y-2">
                <span className="text-sm font-medium">Course / Program</span>
                <CourseProgramCombobox
                  id="profile-course-program"
                  value={learningProfileForm.courseProgram}
                  suggestions={availableCourseProgramSuggestions}
                  learnerLevel={learningProfileForm.learnerLevel}
                  ariaLabel="Course / Program"
                  onChange={(value) =>
                    handleLearningProfileFieldChange("courseProgram", value)
                  }
                  errorText={learningProfileErrors.courseProgram ?? null}
                />
                <p className="text-xs text-foreground/60">Used to tailor content and quiz recommendations to your field.</p>
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
                loading={savingLearningProfile}
                loadingText="Saving..."
                action="save"
                label="Save Learning Profile"
              />
            </div>
          </Card>

          <ProfileNotePerformance />
        </div>
      ) : null}

      {/* Profile type switch confirmation modal */}
      <ProfileTypeSwitchModal
        pendingProfileType={pendingProfileType}
        saving={savingProfileType}
        onCancel={() => setPendingProfileType(null)}
        onConfirm={() => { void handleConfirmProfileTypeSwitch(); }}
      />

      {profileTypeSwitchToast ? (
        <ToastMessage message={profileTypeSwitchToast} tone="success" />
      ) : null}
    </main>
  );
}
