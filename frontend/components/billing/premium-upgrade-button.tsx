"use client";

import { useState, type ComponentProps } from "react";
import { usePathname, useRouter } from "next/navigation";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { createPremiumCheckoutSession, isEmailNotVerifiedError, trackAnalyticsEvent, type BillingCycle } from "@/lib/api";
import { getAuthUser, getCurrentPathWithQuery, getSafeRedirectPath } from "@/lib/auth";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";

type PremiumUpgradeButtonProps = {
  label: string;
  source: string;
  billingCycle?: BillingCycle;
  feature?: string | null;
  variant?: ComponentProps<typeof Button>["variant"];
  size?: ComponentProps<typeof Button>["size"];
  className?: string;
};

export function PremiumUpgradeButton({
  label,
  source,
  billingCycle,
  feature = null,
  variant = "default",
  size = "default",
  className,
}: Readonly<PremiumUpgradeButtonProps>) {
  const pathname = usePathname();
  const router = useRouter();
  const [startingCheckout, setStartingCheckout] = useState(false);
  const [verifyEmailModalOpen, setVerifyEmailModalOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleUpgrade = async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      router.push("/signup");
      return;
    }
    if (!authUser.emailVerifiedAt) {
      setVerifyEmailModalOpen(true);
      return;
    }

    setStartingCheckout(true);
    setErrorMessage(null);
    try {
      void trackAnalyticsEvent({
        eventType: "UPGRADE_CLICKED",
        metadata: {
          source,
          feature,
          path: pathname,
          target: "xendit_checkout",
        },
      });
      const response = await createPremiumCheckoutSession({
        billingCycle: billingCycle ?? null,
        returnUrl: getSafeRedirectPath(getCurrentPathWithQuery()),
      });
      redirectToCheckoutUrl(response.checkoutUrl);
    } catch (error) {
      if (isEmailNotVerifiedError(error)) {
        setVerifyEmailModalOpen(true);
      } else {
        setErrorMessage(error instanceof Error ? error.message : "Could not start Premium checkout. Please try again.");
      }
    } finally {
      setStartingCheckout(false);
    }
  };

  return (
    <>
      <Button
        type="button"
        variant={variant}
        size={size}
        className={className}
        onClick={() => void handleUpgrade()}
        loading={startingCheckout}
        loadingText="Redirecting..."
      >
        {label}
      </Button>
      <AppModal
        isOpen={Boolean(errorMessage)}
        title="Could not start checkout"
        description={errorMessage ?? undefined}
        onClose={() => setErrorMessage(null)}
        panelClassName="max-w-[420px]"
        actions={(
          <div className="flex justify-end">
            <Button type="button" onClick={() => setErrorMessage(null)}>
              Close
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
