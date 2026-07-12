"use client";

import { useEffect, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { trackAnalyticsEvent, type PlanType } from "@/lib/api";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

type StudyPackLimitModalProps = {
  isOpen: boolean;
  planType: PlanType;
  resetDateLabel: string;
  onClose: () => void;
  analyticsSource: string;
};

const UPGRADE_PATH = "/settings?section=plans";
const STUDY_PACK_LIMIT_FEATURE = "study_pack_limit";
const SETTINGS_PLAN_BILLING_TARGET = "settings_plan_billing";

function resolveAppPlan(planType: PlanType): AppPlanType {
  if (planType === "PLUS" || planType === "PRO") {
    return planType;
  }
  return "FREE";
}

export function StudyPackLimitModal({
  isOpen,
  planType,
  resetDateLabel,
  onClose,
  analyticsSource,
}: Readonly<StudyPackLimitModalProps>) {
  const router = useRouter();
  const pathname = usePathname();
  const hasTrackedOpenRef = useRef(false);
  const appPlan = resolveAppPlan(planType);
  const ctas = getUpgradeCtas(appPlan, "study-pack-limit");
  const isFreePlan = appPlan === "FREE";
  const isPlusPlan = appPlan === "PLUS";

  useEffect(() => {
    if (!isOpen) {
      hasTrackedOpenRef.current = false;
      return;
    }
    if (hasTrackedOpenRef.current) {
      return;
    }
    hasTrackedOpenRef.current = true;
    void trackAnalyticsEvent({
      eventType: "PAYWALL_VIEWED",
      metadata: {
        source: analyticsSource,
        feature: STUDY_PACK_LIMIT_FEATURE,
        path: pathname,
        currentPlan: planType,
      },
    });
  }, [analyticsSource, isOpen, pathname, planType]);

  const handleNavigate = (href: string) => {
    void trackAnalyticsEvent({
      eventType: "UPGRADE_CLICKED",
      metadata: {
        source: analyticsSource,
        feature: STUDY_PACK_LIMIT_FEATURE,
        path: pathname,
        currentPlan: planType,
        target: SETTINGS_PLAN_BILLING_TARGET,
      },
    });
    onClose();
    router.push(href);
  };

  if (!isOpen) {
    return null;
  }

  const title = isFreePlan
    ? "You’ve reached your study pack limit"
    : isPlusPlan
      ? "You’ve reached your study pack limit for Plus"
      : "You’ve reached your study pack limit for this month";
  const description = isFreePlan
    ? `Upgrade for more Study Packs and keep turning notes into summaries, key concepts, and quizzes.\n\nYou can still create and save notes. Your limit resets on ${resetDateLabel}.`
    : isPlusPlan
      ? `Upgrade for higher study-pack limits, or wait until ${resetDateLabel} for your next reset.\n\nYou can still review your existing notes and quizzes while you wait.`
      : `Your study pack limit resets on ${resetDateLabel}.\n\nYou can still review your existing notes and quizzes while you wait.`;

  return (
    <AppModal
      isOpen={isOpen}
      title={title}
      description={description}
      descriptionClassName="whitespace-pre-line"
      onClose={onClose}
      panelClassName="max-w-[520px]"
      actions={(
        <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
          {ctas.primary ? (
            <>
              <Button type="button" variant="ghost" onClick={onClose}>
                Maybe Later
              </Button>
              <Button type="button" onClick={() => handleNavigate(UPGRADE_PATH)}>
                {ctas.primary.label}
              </Button>
            </>
          ) : (
            <Button type="button" onClick={onClose}>
              Got It
            </Button>
          )}
        </div>
      )}
    />
  );
}
