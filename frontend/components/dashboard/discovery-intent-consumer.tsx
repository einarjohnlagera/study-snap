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

    // Cleared BEFORE the await, and that ordering is the whole one-shot guarantee: document.cookie
    // writes are synchronous, so a StrictMode double-invoke or a remount re-reads null and returns
    // at the check above. Moving either clear after the await produces a genuine double adoption.
    clearDiscoveryIntentCookie();

    // Not a mounted-flag: StrictMode's synthetic unmount is indistinguishable from a real one, so
    // a mounted-flag suppresses the first invocation's legitimate navigation while the second
    // invocation finds an already-cleared cookie and returns — the adoption lands server-side and
    // the visitor is never taken to it. Comparing the path instead answers the question actually
    // being asked: is the visitor still where they were when this started?
    const startPath = globalThis.location?.pathname;
    const stillHere = () => globalThis.location?.pathname === startPath;

    const resumeAdoption = async () => {
      try {
        if (intent.planType === "goal") {
          const result = await adoptGoal(intent.planId);
          if (!stillHere()) {
            return;
          }
          // Exam intent is discarded only once the adoption actually succeeded. Clearing it up
          // front over-applied the rule: if the plan turns out to be gone there is no competing
          // action to suppress, and the visitor loses an exam prompt they were entitled to.
          clearExamIntentCookie();
          setStudyPlanSkippedNotice(result.goalCollectionId, result.skippedSubjectCount);
          setJustAdoptedNotice(result.goalCollectionId);
          router.replace(`/collections/${result.goalCollectionId}`);
          return;
        }
        const result = await adoptStudyPlan(intent.planId);
        if (!stillHere()) {
          return;
        }
        clearExamIntentCookie();
        setStudyPlanSkippedNotice(result.collectionId, result.skippedCount);
        router.replace(`/collections/${result.collectionId}`);
      } catch {
        if (!stillHere()) {
          return;
        }
        // Deliberately a catch-all: the visitor has already left the card that would have shown the
        // real error, so there is nowhere to surface it. The notice therefore says the adoption
        // could not be completed and that the plan MAY be gone, rather than asserting a cause it
        // cannot know — a network failure and an unpublished plan land here identically.
        router.replace(buildDiscoveryIntentFallbackPath(intent.returnPath));
      }
    };

    // Without the stillHere() checks above, a resolved adoption yanks the visitor out of whatever
    // they navigated to — losing an in-progress note draft — because they see a fully interactive
    // Dashboard while a bulk multi-subject copy runs invisibly behind it. The adoption still
    // completes server-side; only the navigation is suppressed.
    void resumeAdoption();
  }, [router]);

  return null;
}
