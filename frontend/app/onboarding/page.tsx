"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { completeOnboarding, getMe, type EngagementMode, type ProfileType } from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";

type OnboardingProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER";
type ReminderPreset = "OFF" | "LIGHT" | "GUIDED";
type OnboardingStep = "profile" | "learning" | "reminders" | "exam-date";

const PROFILE_TYPE_OPTIONS: Array<{
  value: OnboardingProfileType;
  label: string;
  description: string;
}> = [
  {
    value: "STUDENT",
    label: "Student",
    description: "I want to review my notes and prepare for quizzes.",
  },
  {
    value: "BOARD_EXAM",
    label: "Board Exam",
    description: "I am preparing for a board or licensure exam.",
  },
  {
    value: "TEACHER",
    label: "Teacher",
    description: "I want to create quizzes and review materials.",
  },
];

const LEARNING_STYLE_OPTIONS: Array<{
  value: EngagementMode;
  label: string;
  description: string;
}> = [
  {
    value: "FOCUSED",
    label: "Focused",
    description: "Use NoteLib when you need it. No streaks or pressure.",
  },
  {
    value: "CONSISTENCY",
    label: "Consistency",
    description: "Light encouragement to study regularly.",
  },
  {
    value: "STREAK",
    label: "Streak",
    description: "Track consecutive study days.",
  },
];

const REMINDER_OPTIONS: Array<{
  value: ReminderPreset;
  label: string;
  description: string;
}> = [
  {
    value: "OFF",
    label: "No reminders for now",
    description: "Keep things quiet while you settle in.",
  },
  {
    value: "LIGHT",
    label: "Light reminders",
    description: "A gentle nudge if you have not studied for a while.",
  },
  {
    value: "GUIDED",
    label: "Stay on track",
    description: "Get inactivity reminders and prompts to revisit weak topics.",
  },
];

function getReminderPreset(
  inactivityRemindersEnabled: boolean,
  weakConceptRemindersEnabled: boolean,
): ReminderPreset {
  if (weakConceptRemindersEnabled) {
    return "GUIDED";
  }
  if (inactivityRemindersEnabled) {
    return "LIGHT";
  }
  return "OFF";
}

function getReminderValues(reminderPreset: ReminderPreset) {
  if (reminderPreset === "GUIDED") {
    return {
      inactivityRemindersEnabled: true,
      weakConceptRemindersEnabled: true,
    };
  }
  if (reminderPreset === "LIGHT") {
    return {
      inactivityRemindersEnabled: true,
      weakConceptRemindersEnabled: false,
    };
  }
  return {
    inactivityRemindersEnabled: false,
    weakConceptRemindersEnabled: false,
  };
}

function isOnboardingProfileType(value: ProfileType | null): value is OnboardingProfileType {
  return value === "STUDENT" || value === "BOARD_EXAM" || value === "TEACHER";
}

export default function OnboardingPage() {
  const router = useRouter();
  const [profileType, setProfileType] = useState<OnboardingProfileType | null>(null);
  const [engagementMode, setEngagementMode] = useState<EngagementMode>("FOCUSED");
  const [reminderPreset, setReminderPreset] = useState<ReminderPreset>("LIGHT");
  const [examDate, setExamDate] = useState("");
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const steps = useMemo<OnboardingStep[]>(() => {
    return profileType === "BOARD_EXAM"
      ? ["profile", "learning", "reminders", "exam-date"]
      : ["profile", "learning", "reminders"];
  }, [profileType]);

  useEffect(() => {
    if (currentStepIndex > steps.length - 1) {
      setCurrentStepIndex(Math.max(0, steps.length - 1));
    }
  }, [currentStepIndex, steps.length]);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser) {
      redirectToLoginWithCurrentDestination(router);
      return;
    }
    if (!authUser.emailVerifiedAt) {
      router.replace("/verify-email");
      return;
    }
    if (authUser.onboardingCompletedAt) {
      router.replace("/dashboard");
      return;
    }

    let cancelled = false;
    void getMe()
      .then((me) => {
        if (cancelled) {
          return;
        }
        if (me.onboardingCompletedAt) {
          router.replace("/dashboard");
          return;
        }
        setProfileType(isOnboardingProfileType(me.profileType) ? me.profileType : null);
        setEngagementMode(me.engagementMode);
        setReminderPreset(getReminderPreset(me.inactivityRemindersEnabled, me.weakConceptRemindersEnabled));
        setExamDate(me.examDate ?? "");
      })
      .catch((requestError) => {
        if (cancelled) {
          return;
        }
        setError(requestError instanceof Error ? requestError.message : "Could not load onboarding.");
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [router]);

  const currentStep = steps[currentStepIndex] ?? "profile";
  const isLastStep = currentStepIndex === steps.length - 1;
  const isCurrentStepValid = useMemo(() => {
    if (currentStep === "profile") {
      return profileType !== null;
    }
    if (currentStep === "exam-date") {
      return examDate.trim().length > 0;
    }
    return true;
  }, [currentStep, examDate, profileType]);

  const handleNext = async () => {
    if (loading || saving || !isCurrentStepValid) {
      return;
    }
    if (!isLastStep) {
      setError(null);
      setCurrentStepIndex((current) => current + 1);
      return;
    }
    if (!profileType) {
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const reminderValues = getReminderValues(reminderPreset);
      const me = await completeOnboarding({
        profileType,
        engagementMode,
        inactivityRemindersEnabled: reminderValues.inactivityRemindersEnabled,
        weakConceptRemindersEnabled: reminderValues.weakConceptRemindersEnabled,
        examDate: profileType === "BOARD_EXAM" ? examDate : null,
      });
      const authUser = getAuthUser();
      if (authUser) {
        setAuthUser({
          ...authUser,
          displayName: me.displayName,
          profileType: me.profileType,
          emailVerifiedAt: me.emailVerifiedAt,
          onboardingCompletedAt: me.onboardingCompletedAt,
          productOnboardingCompletedAt: me.productOnboardingCompletedAt,
        });
      }
      router.push("/dashboard");
      router.refresh();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Could not complete onboarding.");
    } finally {
      setSaving(false);
    }
  };

  const handleBack = () => {
    if (currentStepIndex === 0 || saving) {
      return;
    }
    setError(null);
    setCurrentStepIndex((current) => Math.max(0, current - 1));
  };

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-8 sm:px-6 sm:py-12">
      <Card className="space-y-6 p-5 sm:p-7">
        <div className="space-y-2">
          <CardTitle>Let&apos;s set up NoteLib for you.</CardTitle>
          <CardDescription>
            A few quick choices, then you&apos;re ready to study.
          </CardDescription>
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs font-medium uppercase tracking-[0.2em] text-foreground/60">
            <span>Setup</span>
            <span>
              Step {Math.min(currentStepIndex + 1, steps.length)} of {steps.length}
            </span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-primary transition-all"
              style={{ width: `${((currentStepIndex + 1) / steps.length) * 100}%` }}
            />
          </div>
        </div>

        {loading ? (
          <p className="text-sm text-foreground/70">Loading setup...</p>
        ) : (
          <div className="space-y-6">
            {currentStep === "profile" ? (
              <section className="space-y-3">
                <div className="space-y-1">
                  <h2 className="text-lg font-semibold">What will you use NoteLib for?</h2>
                  <p className="text-sm text-foreground/70">Choose the goal that fits how you plan to study.</p>
                </div>
                <div className="space-y-3">
                  {PROFILE_TYPE_OPTIONS.map((option) => (
                    <label
                      key={option.value}
                      className="flex cursor-pointer items-start gap-3 rounded-xl border border-border bg-background px-4 py-3"
                    >
                      <input
                        aria-label={option.label}
                        type="radio"
                        name="profileType"
                        value={option.value}
                        checked={profileType === option.value}
                        onChange={() => setProfileType(option.value)}
                      />
                      <span className="space-y-1">
                        <span className="block text-sm font-medium">{option.label}</span>
                        <span className="block text-xs text-foreground/60">{option.description}</span>
                      </span>
                    </label>
                  ))}
                </div>
              </section>
            ) : null}

            {currentStep === "learning" ? (
              <section className="space-y-3">
                <div className="space-y-1">
                  <h2 className="text-lg font-semibold">Learning Style</h2>
                  <p className="text-sm text-foreground/70">Pick the level of study encouragement you want.</p>
                </div>
                <div className="space-y-3">
                  {LEARNING_STYLE_OPTIONS.map((option) => (
                    <label
                      key={option.value}
                      className="flex cursor-pointer items-start gap-3 rounded-xl border border-border bg-background px-4 py-3"
                    >
                      <input
                        aria-label={option.label}
                        type="radio"
                        name="engagementMode"
                        value={option.value}
                        checked={engagementMode === option.value}
                        onChange={() => setEngagementMode(option.value)}
                      />
                      <span className="space-y-1">
                        <span className="block text-sm font-medium">{option.label}</span>
                        <span className="block text-xs text-foreground/60">{option.description}</span>
                      </span>
                    </label>
                  ))}
                </div>
              </section>
            ) : null}

            {currentStep === "reminders" ? (
              <section className="space-y-3">
                <div className="space-y-1">
                  <h2 className="text-lg font-semibold">Study Reminder Frequency</h2>
                  <p className="text-sm text-foreground/70">
                    Choose how much follow-up you want while you build your study routine.
                  </p>
                </div>
                <div className="space-y-3">
                  {REMINDER_OPTIONS.map((option) => (
                    <label
                      key={option.value}
                      className="flex cursor-pointer items-start gap-3 rounded-xl border border-border bg-background px-4 py-3"
                    >
                      <input
                        aria-label={option.label}
                        type="radio"
                        name="reminderPreset"
                        value={option.value}
                        checked={reminderPreset === option.value}
                        onChange={() => setReminderPreset(option.value)}
                      />
                      <span className="space-y-1">
                        <span className="block text-sm font-medium">{option.label}</span>
                        <span className="block text-xs text-foreground/60">{option.description}</span>
                      </span>
                    </label>
                  ))}
                </div>
              </section>
            ) : null}

            {currentStep === "exam-date" ? (
              <section className="space-y-3">
                <div className="space-y-1">
                  <h2 className="text-lg font-semibold">When is your exam?</h2>
                  <p className="text-sm text-foreground/70">We&apos;ll use this later for planning and reminders.</p>
                </div>
                <label className="block space-y-2">
                  <span className="text-sm font-medium text-foreground">Select date</span>
                  <input
                    type="date"
                    value={examDate}
                    onChange={(event) => setExamDate(event.target.value)}
                    className="h-11 w-full rounded-xl border border-border bg-background px-3 text-sm text-foreground"
                  />
                </label>
              </section>
            ) : null}
          </div>
        )}

        {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button
            type="button"
            variant="outline"
            onClick={handleBack}
            disabled={loading || saving || currentStepIndex === 0}
            className="w-full sm:w-auto"
          >
            Back
          </Button>
          <Button
            type="button"
            onClick={() => void handleNext()}
            disabled={loading || saving || !isCurrentStepValid}
            className="w-full sm:w-auto"
          >
            {saving ? "Saving..." : isLastStep ? "Finish setup" : "Continue"}
          </Button>
        </div>
      </Card>
    </div>
  );
}
