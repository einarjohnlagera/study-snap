"use client";

import { useMemo, useState, useSyncExternalStore } from "react";
import { PlanPicker } from "@/components/collections/plan-picker";
import { buildExamAuthPath } from "@/components/exam-hub/exam-hub-cta";
import { PublicStudyPlanCard } from "@/components/study-plan/public-study-plan-card";
import { getAuthUser } from "@/lib/auth";
import type { NoteCollectionSummary } from "@/lib/api";
import type { ExamHubConfig } from "@/lib/exam-hub-config";
import { setExamIntentCookie } from "@/lib/exam-intent";

type ExamHubOfficialReviewSetsProps = {
  exam: ExamHubConfig;
  plans: NoteCollectionSummary[];
};

function subscribeToAuthChanges(onStoreChange: () => void) {
  if (globalThis.window === undefined) {
    return () => {};
  }
  globalThis.addEventListener("studysnap-auth-change", onStoreChange);
  globalThis.addEventListener("storage", onStoreChange);
  return () => {
    globalThis.removeEventListener("studysnap-auth-change", onStoreChange);
    globalThis.removeEventListener("storage", onStoreChange);
  };
}

function getAuthSnapshot() {
  return getAuthUser() !== null;
}

function getServerAuthSnapshot() {
  return false;
}

export function ExamHubOfficialReviewSets({
  exam,
  plans,
}: Readonly<ExamHubOfficialReviewSetsProps>) {
  const isAuthenticated = useSyncExternalStore(
    subscribeToAuthChanges,
    getAuthSnapshot,
    getServerAuthSnapshot,
  );
  const authUser = isAuthenticated ? getAuthUser() : null;
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(plans[0]?.id ?? null);
  const effectiveSelectedPlanId = plans.some((plan) => plan.id === selectedPlanId)
    ? selectedPlanId
    : plans[0]?.id ?? null;
  const selectedPlan = useMemo(
    () => plans.find((plan) => plan.id === effectiveSelectedPlanId) ?? null,
    [effectiveSelectedPlanId, plans],
  );

  if (!selectedPlan) {
    return null;
  }

  const examPath = `/exam/${exam.slug}`;
  const exactMatchLabels = Array.from(new Set(
    plans
      .map((plan) => plan.courseProgram?.trim())
      .filter((courseProgram): courseProgram is string => Boolean(courseProgram)),
  ));

  return (
    <section className="space-y-4" aria-labelledby="official-review-sets-heading">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
          Curated path
        </p>
        <h2 id="official-review-sets-heading" className="text-xl font-semibold tracking-tight">
          Official Review Sets for {exam.shortName}
        </h2>
        <p className="text-sm text-foreground/65">
          Matched exactly by Course / Program
          {exactMatchLabels.length > 0 ? `: ${exactMatchLabels.join(", ")}` : ""}. Preview a set before adding a
          private, editable copy to your workspace.
        </p>
      </div>

      {plans.length > 1 ? (
        <PlanPicker
          id="exam-hub-review-set-picker"
          label="Official Review Set"
          description="Choose which exact course/program match to preview."
          collections={plans}
          selectedCollectionId={selectedPlan.id}
          collectionsState="ready"
          includeParentCollections
          showEmptyOption={false}
          onChange={setSelectedPlanId}
        />
      ) : null}

      <div className="max-w-xl">
        <PublicStudyPlanCard
          plan={selectedPlan}
          adoptedCollection={null}
          profileType={authUser?.profileType ?? null}
          canAdopt={authUser !== null}
          discoverySource="exam_hub"
          discoveryMetadata={{ slug: exam.slug }}
          onSignedOutAdopt={() => setExamIntentCookie(exam.slug)}
          signedOutHref={buildExamAuthPath(exam.slug, examPath)}
        />
      </div>
    </section>
  );
}
