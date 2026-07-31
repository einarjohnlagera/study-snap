"use client";

import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { PlanPicker } from "@/components/collections/plan-picker";
import { buildExamAuthPath } from "@/components/exam-hub/exam-hub-cta";
import { PublicStudyPlanCard } from "@/components/study-plan/public-study-plan-card";
import { getAccessToken, getAuthUser } from "@/lib/auth";
import { API_BASE_URL, type NoteCollectionSummary } from "@/lib/api";
import type { ExamHubConfig } from "@/lib/exam-hub-config";
import { setExamIntentCookie } from "@/lib/exam-intent";

// Deliberately not `listCollections()`: that helper routes through `fetchWithAuth`, which
// hard-redirects to `/login` via `handleUnauthorizedSession` on any 401 (e.g. a stale
// `localStorage` auth user whose refresh token has expired). That's the right behavior on an
// authenticated page, but this section lives on the anonymous, SEO-relevant Exam Hub page,
// whose contract is to never navigate a visitor away on a lookup failure. This raw fetch fails
// open (including on a 401) instead.
async function fetchOwnedCollectionsWithoutRedirect(): Promise<NoteCollectionSummary[]> {
  const token = getAccessToken();
  if (!token) {
    return [];
  }
  const response = await fetch(`${API_BASE_URL}/collections`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    return [];
  }
  return response.json() as Promise<NoteCollectionSummary[]>;
}

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
  const [personalCollections, setPersonalCollections] = useState<NoteCollectionSummary[]>([]);

  // Same-page-load fetch as `/collections/published`'s adoption resolution, but this card's
  // `plans` prop is already available synchronously (server-rendered), so there is a brief
  // window where an already-adopted visitor sees "Start" before this settles to "Continue".
  // Accepted for this patch: the adopt call is idempotent, so a click during that window
  // re-triggers rather than duplicates. Fails open to `[]` so a lookup failure never blocks
  // this anonymous, SEO-relevant hub page from rendering its adopt/preview CTA.
  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    let cancelled = false;
    fetchOwnedCollectionsWithoutRedirect()
      .then((collections) => {
        if (!cancelled) {
          setPersonalCollections(collections);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPersonalCollections([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);
  const adoptedCollection = useMemo(
    () => (isAuthenticated
      ? personalCollections.find((collection) => collection.sourcePlanId === selectedPlan?.id) ?? null
      : null),
    [isAuthenticated, personalCollections, selectedPlan],
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
          adoptedCollection={adoptedCollection}
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
