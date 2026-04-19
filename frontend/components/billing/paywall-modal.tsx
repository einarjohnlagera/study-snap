"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { trackAnalyticsEvent, type ProfileType } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { PLAN_BILLING_PATH } from "@/lib/plans";

export type PaywallModalVariant =
  | "adaptive-practice"
  | "board-exam-mode"
  | "difficulty-selection"
  | "challenge-quiz-limit"
  | "study-pack-limit"
  | "ocr-limit";

type PaywallModalProps = {
  isOpen: boolean;
  variant: PaywallModalVariant;
  onClose: () => void;
  source: string;
};

type PaywallConfig = {
  title: string;
  message: string;
  dismissLabel: string;
  feature: string;
};

function resolveQuizLimitMessage(profileType: ProfileType | null | undefined) {
  if (profileType === "BOARD_EXAM") {
    return "You’ve used all your quizzes for this month. Upgrade to Premium to continue practicing and access Board Exam mode.";
  }
  if (profileType === "TEACHER") {
    return "You’ve used all your quiz generations for this month. Upgrade to Premium to generate more quizzes and export materials for your class.";
  }
  return "You’ve used all your quizzes for this month. Upgrade to Premium to continue practicing and unlock Adaptive Practice.";
}

const PAYWALL_CONTENT: Record<PaywallModalVariant, PaywallConfig> = {
  "adaptive-practice": {
    title: "Adaptive Practice is a Premium feature",
    message:
      "Adaptive Practice focuses on your weak concepts and helps you improve faster. Upgrade to Premium to unlock personalized practice and deeper review.",
    dismissLabel: "Maybe Later",
    feature: "adaptive",
  },
  "board-exam-mode": {
    title: "Board Exam Mode is a Premium feature",
    message:
      "Board Exam Mode gives you a stricter exam-style session for focused prep. Upgrade to Premium to unlock board-style practice.",
    dismissLabel: "Maybe Later",
    feature: "board_exam",
  },
  "difficulty-selection": {
    title: "Difficulty Selection is a Premium feature",
    message:
      "Choose your quiz difficulty and challenge yourself. Great for exam preparation and mastering difficult topics.",
    dismissLabel: "Maybe Later",
    feature: "difficulty",
  },
  "challenge-quiz-limit": {
    title: "You’ve reached your quiz limit",
    message: "",
    dismissLabel: "Maybe Later",
    feature: "quiz_limit",
  },
  "study-pack-limit": {
    title: "You’ve reached your study pack limit",
    message:
      "You can still review your existing notes and quizzes. Upgrade to Premium to generate more Study Packs.",
    dismissLabel: "Maybe Later",
    feature: "study_pack_limit",
  },
  "ocr-limit": {
    title: "OCR limit reached",
    message:
      "You’ve reached your image-to-text limit for this month. You can still create notes manually or upload files. Upgrade to Premium for higher OCR limits.",
    dismissLabel: "Maybe Later",
    feature: "ocr_limit",
  },
};

export function PaywallModal({
  isOpen,
  variant,
  onClose,
  source,
}: Readonly<PaywallModalProps>) {
  const router = useRouter();
  const pathname = usePathname();
  const hasTrackedOpenRef = useRef(false);
  const [verifyEmailModalOpen, setVerifyEmailModalOpen] = useState(false);
  const authUser = getAuthUser();
  const config = useMemo(() => {
    if (variant !== "challenge-quiz-limit") {
      return PAYWALL_CONTENT[variant];
    }
    return {
      ...PAYWALL_CONTENT[variant],
      message: resolveQuizLimitMessage(authUser?.profileType),
    };
  }, [authUser?.profileType, variant]);

  useEffect(() => {
    if (!isOpen) {
      hasTrackedOpenRef.current = false;
      setVerifyEmailModalOpen(false);
      return;
    }
    if (hasTrackedOpenRef.current) {
      return;
    }
    hasTrackedOpenRef.current = true;
    void trackAnalyticsEvent({
      eventType: "PAYWALL_VIEWED",
      metadata: {
        source,
        feature: config.feature,
        path: pathname,
        variant,
      },
    });
  }, [config.feature, isOpen, onClose, pathname, source, variant]);

  const handleDismiss = () => {
    void trackAnalyticsEvent({
      eventType: "PAYWALL_DISMISSED",
      metadata: {
        source,
        feature: config.feature,
        path: pathname,
        variant,
      },
    });
    onClose();
  };

  const handleUpgrade = () => {
    if (authUser && !authUser.emailVerifiedAt) {
      setVerifyEmailModalOpen(true);
      return;
    }
    void trackAnalyticsEvent({
      eventType: "UPGRADE_CLICKED",
      metadata: {
        source,
        feature: config.feature,
        path: pathname,
        variant,
        target: PLAN_BILLING_PATH,
      },
    });
    onClose();
    router.push(PLAN_BILLING_PATH);
  };

  return (
    <>
      <AppModal
        isOpen={isOpen}
        title={config.title}
        description={config.message}
        onClose={handleDismiss}
        panelClassName="max-w-[460px]"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={handleDismiss}>
              {config.dismissLabel}
            </Button>
            <Button type="button" onClick={handleUpgrade}>
              Upgrade to Premium
            </Button>
          </div>
        )}
      />
      <VerifyEmailRequiredModal
        isOpen={verifyEmailModalOpen}
        onClose={() => setVerifyEmailModalOpen(false)}
      />
    </>
  );
}
