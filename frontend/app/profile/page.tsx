"use client";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { GoogleAuthButton } from "@/components/auth/google-auth-button";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/page-header";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { SuggestionCombobox } from "@/components/ui/suggestion-combobox";
import { AppModal } from "@/components/ui/app-modal";
import { ToastMessage } from "@/components/ui/toast-message";
import {
  completeOnboardingProfileType,
  connectGoogle,
  getMe,
  getSignInMethods,
  listCoursePrograms,
  type SignInMethodsResponse,
  type LearnerLevel,
  type MeResponse,
  type ProfileType,
  updateExamDate,
  updateUserProfile,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import {
  COURSE_PROGRAM_SUGGESTIONS,
  LEARNER_LEVEL_OPTIONS,
  getGroupedLearnerLevels,
  mergeCourseProgramSuggestions,
} from "@/lib/learning-profile";
import {
  DISABLED_PROFILE_TYPES,
  getProfileTypeSwitchContent,
  isActiveProfileTypeForSwitch,
} from "@/lib/profile-mode";
import { BackLink } from "@/components/ui/back-link";
import { buildPublicCreatorOrProfilePath } from "@/lib/public-note-path";
import { PROFILE_LEARNING_PROFILE_SECTION_ID, PROFILE_TOP_PERFORMANCE_SECTION_ID } from "@/lib/profile-sections";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";
import { getSelectionCardClassName } from "@/lib/clickable-card";
import { ProfileNotePerformance } from "@/components/profile/profile-note-performance";

type IdentityForm = {
  firstName: string;
  lastName: string;
  displayName: string;
  username: string;
  email: string;
};

type LearningProfileForm = {
  learnerLevel: LearnerLevel | "";
  courseProgram: string;
  bio: string;
};

type TeachingInfoForm = {
  schoolName: string;
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
    description: "Active learning with quiz tracking, weak-concept practice, and study recall.",
  },
  {
    value: "BOARD_EXAM",
    label: "Exam Reviewer",
    description: "For board, licensure, and civil service exam prep — with performance tracking and targeted review.",
  },
  {
    value: "TEACHER",
    label: "Teacher",
    description: "Generate, preview, and export quiz materials from your notes.",
  },
  {
    value: "PARENT",
    label: "Parent",
    description: "Support and monitor your child's learning progress.",
    disabled: true,
  },
  {
    value: "PROFESSIONAL",
    label: "Professional",
    description: "Scenario-based practice and knowledge review for certifications and career growth.",
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

function formatExamCountdown(examDate: string | null): string | null {
  if (!examDate) {
    return null;
  }
  const exam = new Date(`${examDate}T00:00:00`);
  if (Number.isNaN(exam.getTime())) {
    return null;
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diffMs = exam.getTime() - today.getTime();
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  if (diffDays < 0) {
    return "Your exam date has passed. Keep practicing to stay sharp.";
  }
  if (diffDays === 0) {
    return "Your exam is today. Focus on a final round of review.";
  }
  if (diffDays === 1) {
    return "You have 1 day until your exam.";
  }
  return `You have ${diffDays} days until your exam.`;
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
}: Readonly<ProfileTypeSwitchModalProps>) {
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
  const searchParams = useSearchParams();
  const fromDashboard = searchParams.get("from") === "dashboard";
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [profile, setProfile] = useState<MeResponse | null>(null);
  const [identityForm, setIdentityForm] = useState<IdentityForm>({
    firstName: "",
    lastName: "",
    displayName: "",
    username: "",
    email: "",
  });
  const [learningProfileForm, setLearningProfileForm] = useState<LearningProfileForm>({
    learnerLevel: "",
    courseProgram: "",
    bio: "",
  });
  const [teachingInfoForm, setTeachingInfoForm] = useState<TeachingInfoForm>({
    schoolName: "",
  });
  const [savingIdentity, setSavingIdentity] = useState(false);
  const [identityMessage, setIdentityMessage] = useState<string | null>(null);
  const [savingProfileType, setSavingProfileType] = useState(false);
  const [profileTypeMessage, setProfileTypeMessage] = useState<string | null>(null);
  const [selectedProfileType, setSelectedProfileType] = useState<ProfileType | "">("");
  const [pendingProfileType, setPendingProfileType] = useState<ProfileType | null>(null);
  const [profileTypeSwitchToast, setProfileTypeSwitchToast] = useState<string | null>(null);
  const [examDateInput, setExamDateInput] = useState("");
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [savingLearningProfile, setSavingLearningProfile] = useState(false);
  const [learningProfileMessage, setLearningProfileMessage] = useState<string | null>(null);
  const [learningProfileErrors, setLearningProfileErrors] = useState<LearningProfileErrors>({});
  const [courseProgramSuggestions, setCourseProgramSuggestions] = useState<string[]>([]);
  const [signInMethods, setSignInMethods] = useState<SignInMethodsResponse | null>(null);
  const [signInMethodsMessage, setSignInMethodsMessage] = useState<string | null>(null);
  const [connectingGoogle, setConnectingGoogle] = useState(false);

  const scrollToRequestedSection = useCallback(() => {
    if (globalThis.window === undefined) {
      return;
    }
    const knownSectionIds = [PROFILE_TOP_PERFORMANCE_SECTION_ID, PROFILE_LEARNING_PROFILE_SECTION_ID];
    const hash = globalThis.location.hash.slice(1);
    if (!knownSectionIds.includes(hash)) {
      return;
    }
    const section = globalThis.document.getElementById(hash);
    if (!section) {
      return;
    }
    globalThis.requestAnimationFrame(() => {
      section.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }, []);

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
    setSignInMethodsMessage(null);
    setLearningProfileErrors({});
    try {
      const [meResult, courseProgramsResult, signInMethodsResult] = await Promise.allSettled([
        getMe(),
        listCoursePrograms("mine"),
        getSignInMethods(),
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
        username: me.username ?? "",
        email: me.pendingEmail ?? me.email ?? "",
      });
      setLearningProfileForm({
        learnerLevel: me.learnerLevel ?? "",
        courseProgram: me.courseProgram ?? "",
        bio: me.bio ?? "",
      });
      setTeachingInfoForm({
        schoolName: me.schoolName ?? "",
      });
      setSelectedProfileType(me.profileType ?? "");
      setExamDateInput(me.examDate ?? "");
      setCourseProgramSuggestions(courseProgramsResult.status === "fulfilled" ? courseProgramsResult.value : []);
      setSignInMethods(signInMethodsResult.status === "fulfilled" ? signInMethodsResult.value : null);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load profile.";
      setError(message);
      setProfile(null);
      setSelectedProfileType("");
      setExamDateInput("");
      setCourseProgramSuggestions([]);
      setSignInMethods(null);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  useEffect(() => {
    if (loading) {
      return;
    }
    const timeoutId = globalThis.setTimeout(() => {
      scrollToRequestedSection();
    }, 0);
    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [loading, scrollToRequestedSection]);

  useEffect(() => {
    const handleHashChange = () => {
      scrollToRequestedSection();
    };
    globalThis.addEventListener("hashchange", handleHashChange);
    return () => {
      globalThis.removeEventListener("hashchange", handleHashChange);
    };
  }, [scrollToRequestedSection]);

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

  const groupedLearnerLevels = useMemo(
    () => {
      const grouped = getGroupedLearnerLevels(profile?.profileType);
      return [
        { label: grouped.recommendedGroupLabel, options: grouped.recommended },
        { label: "Other options", options: grouped.other },
      ];
    },
    [profile?.profileType],
  );

  const availableCourseProgramSuggestions = useMemo(
    () => mergeCourseProgramSuggestions(
      COURSE_PROGRAM_SUGGESTIONS,
      courseProgramSuggestions,
      [learningProfileForm.courseProgram],
      [profile?.courseProgram],
    ),
    [courseProgramSuggestions, learningProfileForm.courseProgram, profile?.courseProgram],
  );

  const examCountdown = useMemo(
    () => formatExamCountdown(profile?.examDate ?? null),
    [profile?.examDate],
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
    username: identityForm.username.trim(),
    bio: learningProfileForm.bio.trim(),
    learnerLevel: learningProfileForm.learnerLevel || null,
    courseProgram: learningProfileForm.courseProgram.trim(),
    schoolName: teachingInfoForm.schoolName.trim(),
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
        username: updatedIdentity.username ?? "",
        email: updatedIdentity.pendingEmail ?? updatedIdentity.email,
      });
      setLearningProfileForm({
        learnerLevel: updatedIdentity.learnerLevel ?? "",
        courseProgram: updatedIdentity.courseProgram ?? "",
        bio: updatedIdentity.bio ?? "",
      });
      setTeachingInfoForm({
        schoolName: updatedIdentity.schoolName ?? "",
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
        username: updatedProfile.username ?? "",
        email: updatedProfile.pendingEmail ?? updatedProfile.email,
      });
      setLearningProfileForm({
        learnerLevel: updatedProfile.learnerLevel ?? "",
        courseProgram: updatedProfile.courseProgram ?? "",
        bio: updatedProfile.bio ?? "",
      });
      setTeachingInfoForm({
        schoolName: updatedProfile.schoolName ?? "",
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


  const handleSaveProfileType = async () => {
    if (savingProfileType || !selectedProfileType || !profile) {
      return;
    }
    const profileTypeChanged = selectedProfileType !== profile.profileType;
    const examDateChanged = selectedProfileType === "BOARD_EXAM" && examDateInput !== (profile.examDate ?? "");
    const schoolNameChanged = selectedProfileType === "TEACHER" && teachingInfoForm.schoolName.trim() !== (profile.schoolName ?? "");
    if (!profileTypeChanged && !examDateChanged && !schoolNameChanged) {
      setProfileTypeMessage("Profile type is already up to date.");
      return;
    }
    if (profileTypeChanged) {
      if (!isActiveProfileTypeForSwitch(selectedProfileType)) {
        return;
      }
      // Show the confirmation modal before committing the switch.
      setPendingProfileType(selectedProfileType);
      setProfileTypeMessage(null);
      return;
    }
    // Only a secondary field changed (profile type unchanged).
    setSavingProfileType(true);
    setProfileTypeMessage(null);
    try {
      if (examDateChanged) {
        const updatedProfile = await updateExamDate(examDateInput || null);
        setProfile(updatedProfile);
        setExamDateInput(updatedProfile.examDate ?? "");
        setProfileTypeMessage(updatedProfile.examDate ? "Exam date updated successfully." : "Exam date cleared.");
      } else {
        // Only school name changed (TEACHER profile type unchanged).
        const updatedProfile = await updateUserProfile(buildProfileUpdateRequest());
        setProfile(updatedProfile);
        setTeachingInfoForm({ schoolName: updatedProfile.schoolName ?? "" });
        setProfileTypeMessage(updatedProfile.schoolName?.trim() ? "School name updated." : "School name cleared.");
      }
    } catch (err) {
      if (examDateChanged) {
        setExamDateInput(profile.examDate ?? "");
      } else {
        setTeachingInfoForm({ schoolName: profile.schoolName ?? "" });
      }
      setProfileTypeMessage(err instanceof Error ? err.message : "Could not save.");
    } finally {
      setSavingProfileType(false);
    }
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

      // Also save exam date if switching to BOARD_EXAM with a pending change.
      let finalProfile = updatedProfile;
      if (targetType === "BOARD_EXAM" && examDateInput !== (updatedProfile.examDate ?? "")) {
        finalProfile = await updateExamDate(examDateInput || null);
      }
      // Also save school name if switching to TEACHER with a pending value.
      if (targetType === "TEACHER" && teachingInfoForm.schoolName.trim() !== (updatedProfile.schoolName ?? "")) {
        finalProfile = await updateUserProfile(buildProfileUpdateRequest());
      }

      setProfile(finalProfile);
      setSelectedProfileType(finalProfile.profileType ?? targetType);
      setExamDateInput(finalProfile.examDate ?? "");
      setTeachingInfoForm({ schoolName: finalProfile.schoolName ?? "" });

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


  const handleConnectGoogle = useCallback(async (code: string) => {
    if (connectingGoogle) {
      return;
    }
    setConnectingGoogle(true);
    setSignInMethodsMessage(null);
    try {
      const updated = await connectGoogle({ code });
      setSignInMethods(updated);
      setSignInMethodsMessage("Google connected successfully.");
    } catch (err) {
      setSignInMethodsMessage(err instanceof Error ? err.message : "Could not connect Google.");
    } finally {
      setConnectingGoogle(false);
    }
  }, [connectingGoogle]);

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
          <BackLink
            href={fromDashboard ? "/dashboard" : buildPublicCreatorOrProfilePath({ userId: profile.id, username: profile.username })}
            label={fromDashboard ? "Dashboard" : "Profile"}
          />
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
                href={buildPublicCreatorOrProfilePath({ userId: profile.id, username: profile.username })}
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
                <span className="text-sm font-medium">Username</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={identityForm.username}
                  onChange={(event) => handleIdentityFieldChange("username", event.target.value)}
                  autoComplete="username"
                />
                <p className="text-xs text-foreground/60">
                  Your username is used for public attribution and profile links.
                </p>
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
            <div className="space-y-1">
              <h2 className="text-lg font-semibold sm:text-xl">Sign-in Methods</h2>
              <p className="text-sm text-foreground/70">
                See which login methods are connected to your NoteLib account.
              </p>
            </div>
            <div className="space-y-3 rounded-xl border border-border bg-background/60 p-4">
              <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-sm font-medium">Account email</p>
                  <p className="text-sm text-foreground/70">{signInMethods?.email ?? profile.email}</p>
                </div>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="rounded-lg border border-border p-3">
                  <p className="text-sm font-medium">Email and password</p>
                  <p className="text-sm text-foreground/70">
                    {signInMethods?.passwordEnabled ? "Enabled" : "Not set up"}
                  </p>
                </div>
                <div className="space-y-3 rounded-lg border border-border p-3">
                  <div>
                    <p className="text-sm font-medium">Google</p>
                    <p className="text-sm text-foreground/70">
                      {signInMethods?.googleConnected
                        ? `Connected${signInMethods.googleEmail ? `: ${signInMethods.googleEmail}` : ""}`
                        : "Not connected"}
                    </p>
                  </div>
                  {!signInMethods?.googleConnected ? (
                    <GoogleAuthButton
                      label="Connect Google"
                      loadingLabel="Connecting Google..."
                      disabled={connectingGoogle}
                      onCredential={handleConnectGoogle}
                      onError={setSignInMethodsMessage}
                    />
                  ) : null}
                </div>
              </div>
              {signInMethodsMessage ? <p className="text-xs text-foreground/60">{signInMethodsMessage}</p> : null}
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
                    className={getSelectionCardClassName({
                      selected: isSelected,
                      className: "flex w-full items-start gap-3 px-4 py-3",
                    })}
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
            {selectedProfileType === "BOARD_EXAM" ? (
              <div className="space-y-3 border-t border-border pt-4">
                <div className="space-y-1">
                  <h3 className="text-sm font-semibold text-foreground">Exam Date</h3>
                  <p className="text-xs text-foreground/60">
                    Used for your dashboard countdown. Clear the field to remove the countdown.
                  </p>
                </div>
                <input
                  type="date"
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={examDateInput}
                  onChange={(event) => {
                    setExamDateInput(event.target.value);
                    setProfileTypeMessage(null);
                  }}
                  aria-label="Exam Date"
                />
                {examCountdown ? <p className="text-xs text-foreground/60">{examCountdown}</p> : null}
              </div>
            ) : null}
            {selectedProfileType === "TEACHER" ? (
              <div className="space-y-3 border-t border-border pt-4">
                <div className="space-y-1">
                  <h3 className="text-sm font-semibold text-foreground">School Name</h3>
                  <p className="text-xs text-foreground/60">
                    Shown in the header of every exported DOCX. Optional.
                  </p>
                </div>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  aria-label="School Name"
                  placeholder="e.g. Mapúa University"
                  value={teachingInfoForm.schoolName}
                  maxLength={120}
                  onChange={(event) => {
                    setProfileTypeMessage(null);
                    setTeachingInfoForm({ schoolName: event.target.value.slice(0, 120) });
                  }}
                />
              </div>
            ) : null}
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
                loading={savingProfileType}
                loadingText="Saving..."
                action="save"
                label="Save Profile Type"
              />
            </div>
          </Card>


          <Card id={PROFILE_LEARNING_PROFILE_SECTION_ID} className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Learning Profile</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block space-y-2">
                <span className="text-sm font-medium">Learner Level</span>
                <SuggestionCombobox
                  id="profile-learner-level"
                  value={learningProfileForm.learnerLevel}
                  options={LEARNER_LEVEL_OPTIONS}
                  groupedOptions={groupedLearnerLevels}
                  ariaLabel="Learner Level"
                  onChange={(value) =>
                    handleLearningProfileFieldChange("learnerLevel", value as LearnerLevel | "")
                  }
                  placeholder="Choose learner level"
                  helperText="Quiz questions and explanations will better match your learning stage."
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
                <p className="text-xs text-foreground/60">Provides domain context so examples and questions stay relevant to your field.</p>
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
                  Quiz questions and explanations will better match your learning stage.
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
