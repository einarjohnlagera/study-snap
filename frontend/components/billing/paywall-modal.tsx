"use client";

import { useEffect, useMemo, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { trackAnalyticsEvent } from "@/lib/api";
import { PLAN_BILLING_PATH } from "@/lib/plans";

export type PaywallModalVariant =
  | "adaptive-practice"
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

const DISMISSAL_STORAGE_KEY = "notelib-paywall-dismissals";
const SESSION_STORAGE_KEY = "notelib-paywall-session-id";

const PAYWALL_CONTENT: Record<PaywallModalVariant, PaywallConfig> = {
  "adaptive-practice": {
    title: "Adaptive Practice is a Premium feature",
    message:
      "Adaptive Practice focuses on your weak concepts and helps you improve faster. Upgrade to Premium to unlock personalized practice and deeper review.",
    dismissLabel: "Maybe Later",
    feature: "adaptive",
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
    message:
      "You’ve used all your Challenge Quizzes for this month. Upgrade to Premium for more quizzes and Adaptive Practice to focus on weak areas.",
    dismissLabel: "OK",
    feature: "quiz_limit",
  },
  "study-pack-limit": {
    title: "You’ve reached your study pack limit",
    message:
      "You can still review your existing notes and quizzes. Upgrade to Premium to generate more Study Packs.",
    dismissLabel: "OK",
    feature: "study_pack_limit",
  },
  "ocr-limit": {
    title: "OCR limit reached",
    message:
      "You’ve reached your image-to-text limit for this month. You can still create notes manually or upload files. Upgrade to Premium for higher OCR limits.",
    dismissLabel: "OK",
    feature: "ocr_limit",
  },
};

function getCurrentSessionId(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  const existing = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
  if (existing) {
    return existing;
  }
  const next = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  window.sessionStorage.setItem(SESSION_STORAGE_KEY, next);
  return next;
}

function readDismissals(): Record<string, string> {
  if (typeof window === "undefined") {
    return {};
  }
  try {
    const value = window.localStorage.getItem(DISMISSAL_STORAGE_KEY);
    if (!value) {
      return {};
    }
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" ? parsed as Record<string, string> : {};
  } catch {
    return {};
  }
}

function hasDismissedInCurrentSession(variant: PaywallModalVariant): boolean {
  const sessionId = getCurrentSessionId();
  if (!sessionId) {
    return false;
  }
  return readDismissals()[variant] === sessionId;
}

function storeDismissal(variant: PaywallModalVariant) {
  if (typeof window === "undefined") {
    return;
  }
  const sessionId = getCurrentSessionId();
  if (!sessionId) {
    return;
  }
  const next = {
    ...readDismissals(),
    [variant]: sessionId,
  };
  window.localStorage.setItem(DISMISSAL_STORAGE_KEY, JSON.stringify(next));
}

export function PaywallModal({
  isOpen,
  variant,
  onClose,
  source,
}: PaywallModalProps) {
  const router = useRouter();
  const pathname = usePathname();
  const hasTrackedOpenRef = useRef(false);
  const config = useMemo(() => PAYWALL_CONTENT[variant], [variant]);

  useEffect(() => {
    if (!isOpen) {
      hasTrackedOpenRef.current = false;
      return;
    }
    if (hasDismissedInCurrentSession(variant)) {
      onClose();
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
    storeDismissal(variant);
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
    <AppModal
      isOpen={isOpen && !hasDismissedInCurrentSession(variant)}
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
  );
}
