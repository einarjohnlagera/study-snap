"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { adoptGoal, adoptStudyPlan } from "@/lib/api";
import {
  buildDiscoveryIntentFallbackPath,
  clearDiscoveryIntentCookie,
  getDiscoveryIntentCookie,
} from "@/lib/discovery-intent";
import { clearExamIntentCookie } from "@/lib/exam-intent";
import { setJustAdoptedNotice } from "@/lib/just-adopted-notice";
import { setStudyPlanSkippedNotice } from "@/lib/study-plan-skipped-notice";

export function DiscoveryIntentConsumer() {
  const router = useRouter();

  useEffect(() => {
    const intent = getDiscoveryIntentCookie();
    if (!intent) {
      return;
    }

    // An explicit plan-adopt action is more recent and specific than an exam suggestion.
    // Clear both before the request so remounts can never replay the adoption.
    clearDiscoveryIntentCookie();
    clearExamIntentCookie();

    const resumeAdoption = async () => {
      try {
        if (intent.planType === "goal") {
          const result = await adoptGoal(intent.planId);
          setStudyPlanSkippedNotice(result.goalCollectionId, result.skippedSubjectCount);
          setJustAdoptedNotice(result.goalCollectionId);
          router.replace(`/collections/${result.goalCollectionId}`);
          return;
        }
        const result = await adoptStudyPlan(intent.planId);
        setStudyPlanSkippedNotice(result.collectionId, result.skippedCount);
        router.replace(`/collections/${result.collectionId}`);
      } catch {
        // Deliberately a catch-all: the visitor has already left the card that would have shown the
        // real error, so there is nowhere to surface it. The notice therefore says the adoption
        // could not be completed and that the plan MAY be gone, rather than asserting a cause it
        // cannot know — a network failure and an unpublished plan land here identically.
        router.replace(buildDiscoveryIntentFallbackPath(intent.returnPath));
      }
    };

    void resumeAdoption();
  }, [router]);

  return null;
}
