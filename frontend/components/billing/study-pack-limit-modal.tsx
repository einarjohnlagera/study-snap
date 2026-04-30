"use client";

import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import type { PlanType } from "@/lib/api";
import { PLAN_BILLING_PATH } from "@/lib/plans";

type StudyPackLimitModalProps = {
  isOpen: boolean;
  planType: PlanType;
  resetDateLabel: string;
  onClose: () => void;
};

export function StudyPackLimitModal({
  isOpen,
  planType,
  resetDateLabel,
  onClose,
}: Readonly<StudyPackLimitModalProps>) {
  const router = useRouter();

  const handleNavigate = (href: string) => {
    onClose();
    router.push(href);
  };

  if (!isOpen) {
    return null;
  }

  const isFreePlan = planType === "FREE";
  const isPlusPlan = planType === "PLUS";
  const title = isFreePlan
    ? "You’ve reached your study pack limit"
    : isPlusPlan
      ? "You’ve reached your study pack limit for Plus"
      : "You’ve reached your study pack limit for this month";
  const description = isFreePlan
    ? `Upgrade to Plus or Pro to create more study packs and continue turning your notes into summaries, key concepts, and quizzes.\n\nYou can still create and save notes. Your limit resets on ${resetDateLabel}.`
    : isPlusPlan
      ? `Upgrade to Pro for higher study-pack limits, or wait until ${resetDateLabel} for your next reset.\n\nYou can still review your existing notes and quizzes while you wait.`
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
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:justify-end">
          {isFreePlan ? (
            <>
              <Button type="button" onClick={() => handleNavigate("/pricing")}>
                Upgrade to Plus
              </Button>
              <Button type="button" variant="outline" onClick={onClose}>
                Maybe Later
              </Button>
              <Button type="button" variant="outline" onClick={() => handleNavigate(PLAN_BILLING_PATH)}>
                View My Plan
              </Button>
            </>
          ) : (
            <>
              <Button type="button" onClick={() => handleNavigate("/pricing")}>
                {isPlusPlan ? "Upgrade to Pro" : "View Plans"}
              </Button>
              <Button type="button" variant="outline" onClick={() => handleNavigate(PLAN_BILLING_PATH)}>
                Get More Study Packs
              </Button>
              <Button type="button" variant="outline" onClick={onClose}>
                Maybe Later
              </Button>
            </>
          )}
        </div>
      )}
    />
  );
}
