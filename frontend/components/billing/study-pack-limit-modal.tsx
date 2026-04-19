"use client";

import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { PLAN_BILLING_PATH } from "@/lib/plans";

type StudyPackLimitModalProps = {
  isOpen: boolean;
  planType: "FREE" | "PREMIUM";
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
  const title = isFreePlan ? "Free Plan Limit Reached" : "Monthly Limit Reached";
  const description = isFreePlan
    ? `You’ve reached your Study Pack limit for this month on the Free plan.\n\nYou can still create and save notes, and generate more Study Packs again on ${resetDateLabel}.\n\nIf you want to continue practicing now, Premium gives you more Study Packs, Quizzes, and Adaptive Practice for weak topics.`
    : `You’ve used all your Study Packs for this month.\n\nYour limit resets on ${resetDateLabel}.\n\nIf you need more Study Packs now, you can upgrade your plan or get additional Study Packs.`;

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
                Upgrade to Premium
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
                Upgrade Plan
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
