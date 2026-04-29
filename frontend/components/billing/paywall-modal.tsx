"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { createPremiumCheckoutSession, isEmailNotVerifiedError, trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser, getCurrentPathWithQuery, getSafeRedirectPath } from "@/lib/auth";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";
import {
  type FreePaywallContent,
  type PaywallAction,
  resolveFreePaywallContent,
} from "@/lib/paywall-content";

export type PaywallModalVariant =
  | "adaptive-practice"
  | "board-exam-mode"
  | "difficulty-selection"
  | "challenge-quiz-limit"
  | "quiz-generation-limit"
  | "study-pack-limit"
  | "ocr-limit"
  | "note-generation-limit";

type PaywallModalProps = {
  isOpen: boolean;
  variant: PaywallModalVariant;
  onClose: () => void;
  source: string;
  returnUrl?: string | null;
  resolveReturnUrl?: () => Promise<string | null> | string | null;
};

/** Variants with no PaywallAction mapping keep their own inline content. */
type LegacyPaywallConfig = {
  title: string;
  message: string;
  dismissLabel: string;
  feature: string;
};

const LEGACY_PAYWALL_CONTENT: Partial<Record<PaywallModalVariant, LegacyPaywallConfig>> = {
  "difficulty-selection": {
    title: "Difficulty Selection is a Premium feature",
    message:
      "Choose your quiz difficulty and challenge yourself. Great for exam preparation and mastering difficult topics.",
    dismissLabel: "Maybe Later",
    feature: "difficulty",
  },
  "ocr-limit": {
    title: "OCR limit reached",
    message:
      "You've reached your image-to-text limit for this month. You can still create notes manually or upload files. Upgrade to Premium for higher OCR limits.",
    dismissLabel: "Maybe Later",
    feature: "ocr_limit",
  },
};

function resolvePaywallAction(variant: PaywallModalVariant): PaywallAction | null {
  switch (variant) {
    case "study-pack-limit": return "STUDY_PACK";
    case "challenge-quiz-limit": return "QUIZ";
    case "quiz-generation-limit": return "QUIZ_GENERATION";
    case "board-exam-mode": return "BOARD_EXAM";
    case "adaptive-practice": return "ADAPTIVE_PRACTICE";
    case "note-generation-limit": return "NOTE_GENERATION";
    default: return null;
  }
}

function toModalConfig(content: FreePaywallContent): LegacyPaywallConfig {
  return {
    title: content.title,
    message: content.body,
    dismissLabel: content.dismissLabel,
    feature: content.feature,
  };
}

export function PaywallModal({
  isOpen,
  variant,
  onClose,
  source,
  returnUrl = null,
  resolveReturnUrl,
}: Readonly<PaywallModalProps>) {
  const router = useRouter();
  const pathname = usePathname();
  const hasTrackedOpenRef = useRef(false);
  const [verifyEmailModalOpen, setVerifyEmailModalOpen] = useState(false);
  const [startingCheckout, setStartingCheckout] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const authUser = getAuthUser();

  const config = useMemo((): LegacyPaywallConfig => {
    const action = resolvePaywallAction(variant);
    if (action) {
      return toModalConfig(resolveFreePaywallContent(action, authUser?.profileType));
    }
    return LEGACY_PAYWALL_CONTENT[variant] ?? {
      title: "Premium feature",
      message: "Upgrade to Premium to access this feature.",
      dismissLabel: "Maybe Later",
      feature: variant,
    };
  }, [authUser?.profileType, variant]);

  useEffect(() => {
    if (!isOpen) {
      hasTrackedOpenRef.current = false;
      setVerifyEmailModalOpen(false);
      setStartingCheckout(false);
      setCheckoutError(null);
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

  const handleUpgrade = async () => {
    if (authUser && !authUser.emailVerifiedAt) {
      setVerifyEmailModalOpen(true);
      return;
    }
    if (!authUser) {
      onClose();
      router.push("/signup");
      return;
    }

    setStartingCheckout(true);
    setCheckoutError(null);
    try {
      const resolvedReturnUrl = getSafeRedirectPath(
        (await resolveReturnUrl?.()) ?? returnUrl ?? getCurrentPathWithQuery(),
      );
      void trackAnalyticsEvent({
        eventType: "UPGRADE_CLICKED",
        metadata: {
          source,
          feature: config.feature,
          path: pathname,
          variant,
          target: "xendit_checkout",
        },
      });
      const response = await createPremiumCheckoutSession({
        returnUrl: resolvedReturnUrl,
      });
      onClose();
      redirectToCheckoutUrl(response.checkoutUrl);
    } catch (error) {
      if (isEmailNotVerifiedError(error)) {
        setVerifyEmailModalOpen(true);
      } else {
        setCheckoutError(error instanceof Error ? error.message : "Could not start Premium checkout. Please try again.");
      }
    } finally {
      setStartingCheckout(false);
    }
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
            <Button
              type="button"
              onClick={() => void handleUpgrade()}
              loading={startingCheckout}
              loadingText="Redirecting..."
            >
              Upgrade to Premium
            </Button>
          </div>
        )}
      >
        {checkoutError ? (
          <div className="rounded-lg border border-red-500/30 bg-red-50/70 p-3 text-sm text-red-700 dark:bg-red-950/20 dark:text-red-300">
            {checkoutError}
          </div>
        ) : null}
      </AppModal>
      <VerifyEmailRequiredModal
        isOpen={verifyEmailModalOpen}
        onClose={() => setVerifyEmailModalOpen(false)}
      />
    </>
  );
}
