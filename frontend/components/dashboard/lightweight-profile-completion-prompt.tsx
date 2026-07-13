"use client";

import { useMemo, useState } from "react";
import { CourseProgramCombobox } from "@/components/metadata/course-program-combobox";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { getSelectionCardClassName } from "@/lib/clickable-card";
import {
  completeOnboarding,
  updateLearningProfileContext,
  type LearnerLevel,
  type MeResponse,
  type ProfileType,
} from "@/lib/api";
import {
  COURSE_PROGRAM_SUGGESTIONS,
  getDefaultLearnerLevel,
  getGroupedLearnerLevels,
} from "@/lib/learning-profile";
import {
  ONBOARDING_PROFILE_OPTIONS,
  type OnboardingProfileType,
} from "@/lib/onboarding-v2";

type LightweightProfileCompletionPromptProps = {
  initialProfileType: ProfileType | null;
  initialLearnerLevel: LearnerLevel | null;
  initialCourseProgram: string | null;
  initialExamDate: string | null;
  onDismiss: () => void;
  onComplete: (me: MeResponse) => void;
};

function toOnboardingProfileType(profileType: ProfileType | null): OnboardingProfileType | null {
  return profileType === "STUDENT" || profileType === "BOARD_EXAM" || profileType === "TEACHER" || profileType === "PROFESSIONAL"
    ? profileType
    : null;
}

export function LightweightProfileCompletionPrompt({
  initialProfileType,
  initialLearnerLevel,
  initialCourseProgram,
  initialExamDate,
  onDismiss,
  onComplete,
}: Readonly<LightweightProfileCompletionPromptProps>) {
  const [profileType, setProfileType] = useState<OnboardingProfileType | null>(() => toOnboardingProfileType(initialProfileType));
  const [learnerLevel, setLearnerLevel] = useState<LearnerLevel | null>(initialLearnerLevel);
  const [courseProgram, setCourseProgram] = useState(initialCourseProgram ?? "");
  const [examDate, setExamDate] = useState(initialExamDate ?? "");
  const [learningProfileSaved, setLearningProfileSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const groupedLearnerLevels = useMemo(() => getGroupedLearnerLevels(profileType), [profileType]);

  const selectProfileType = (nextProfileType: OnboardingProfileType) => {
    setProfileType(nextProfileType);
    setLearnerLevel((current) => current ?? getDefaultLearnerLevel(nextProfileType));
    if (nextProfileType !== "BOARD_EXAM") {
      setExamDate("");
    }
    setError(null);
  };

  const handleSubmit = async () => {
    if (submitting) {
      return;
    }
    if (!profileType) {
      setError("Please select your profile type.");
      return;
    }
    if (!learnerLevel) {
      setError("Please select your learner level.");
      return;
    }
    if (!courseProgram.trim()) {
      setError("Please select your course / program.");
      return;
    }

    setSubmitting(true);
    setError(null);
    let savedLearningProfile = learningProfileSaved;
    try {
      if (!savedLearningProfile) {
        await updateLearningProfileContext(learnerLevel, courseProgram);
        setLearningProfileSaved(true);
        savedLearningProfile = true;
      }
      const me = await completeOnboarding({
        profileType,
        examDate: profileType === "BOARD_EXAM" && examDate ? examDate : null,
      });
      onComplete(me);
    } catch (submissionError) {
      setError(
        savedLearningProfile
          ? "Your study preferences are saved. Please retry confirming your profile type."
          : submissionError instanceof Error
            ? submissionError.message
            : "Could not save your profile details. Please try again.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card className="motion-fade-enter space-y-4 p-4 sm:p-5">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1.5">
          <h2 className="text-lg font-semibold sm:text-xl">Finish setting up your study profile</h2>
          <p className="text-sm text-foreground/75">
            A few details help keep future quizzes and recommendations matched to your study path.
          </p>
        </div>
        <Button type="button" variant="ghost" size="sm" onClick={onDismiss} aria-label="Dismiss profile completion prompt">
          Dismiss
        </Button>
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        {ONBOARDING_PROFILE_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            className={getSelectionCardClassName({
              selected: profileType === option.value,
              className: "p-3",
            })}
            onClick={() => selectProfileType(option.value)}
            aria-pressed={profileType === option.value}
            aria-label={option.label}
            disabled={submitting}
          >
            <span className="block text-left">
              <span className="block text-sm font-semibold text-foreground">{option.icon} {option.label}</span>
              <span className="mt-0.5 block text-xs text-foreground/70">{option.description}</span>
            </span>
          </button>
        ))}
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <label className="space-y-2">
          <span className="text-sm font-medium text-foreground">Learner Level *</span>
          <select
            value={learnerLevel ?? ""}
            onChange={(event) => setLearnerLevel(event.target.value ? event.target.value as LearnerLevel : null)}
            className="h-11 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
            aria-label="Learner Level"
            disabled={submitting || learningProfileSaved}
          >
            <option value="">Choose your learner level</option>
            <optgroup label={groupedLearnerLevels.recommendedGroupLabel}>
              {groupedLearnerLevels.recommended.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </optgroup>
            {groupedLearnerLevels.other.length > 0 ? (
              <optgroup label="Other options">
                {groupedLearnerLevels.other.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </optgroup>
            ) : null}
          </select>
        </label>

        <div className="space-y-2">
          <span className="text-sm font-medium text-foreground">Course / Program *</span>
          <CourseProgramCombobox
            id="dashboard-lightweight-course-program"
            value={courseProgram}
            suggestions={COURSE_PROGRAM_SUGGESTIONS}
            onChange={setCourseProgram}
            learnerLevel={learnerLevel}
            ariaLabel="Course / Program"
            context="onboarding"
            allowCustom={false}
            disabled={submitting || learningProfileSaved}
            inlineDropdown
          />
        </div>
      </div>

      {profileType === "BOARD_EXAM" ? (
        <label className="block space-y-2">
          <span className="text-sm font-medium text-foreground">When is your exam? (optional)</span>
          <input
            type="date"
            value={examDate}
            onChange={(event) => setExamDate(event.target.value)}
            className="min-h-11 w-full rounded-xl border border-border bg-background px-4 py-2.5 text-sm text-foreground outline-none transition-colors focus:border-blue-500"
            aria-label="When is your exam? (optional)"
            disabled={submitting}
          />
        </label>
      ) : null}

      {learningProfileSaved ? (
        <p className="text-xs text-emerald-700 dark:text-emerald-300">
          Your study preferences are saved. Confirm your profile type to finish setup.
        </p>
      ) : null}
      {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

      <div className="flex flex-col gap-2 sm:flex-row">
        <Button type="button" onClick={() => void handleSubmit()} loading={submitting} loadingText="Saving..." disabled={submitting}>
          {learningProfileSaved ? "Confirm Profile Type" : "Save Study Profile"}
        </Button>
      </div>
    </Card>
  );
}
