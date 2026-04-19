"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { isEmailNotVerifiedError, joinPremiumWaitlist, trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

type PremiumWaitlistModalProps = {
  isOpen: boolean;
  onClose: () => void;
  source: string;
  feature?: string | null;
  trackPaywallView?: boolean;
};

const PREMIUM_FEATURES = [
  "Quiz",
  "Adaptive Practice",
  "Weak Concept Training",
  "Higher monthly limits",
];

export function PremiumWaitlistModal({
  isOpen,
  onClose,
  source,
  feature = null,
  trackPaywallView = false,
}: Readonly<PremiumWaitlistModalProps>) {
  const pathname = usePathname();
  const [joining, setJoining] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [verifyEmailModalOpen, setVerifyEmailModalOpen] = useState(false);
  const hasTrackedOpenRef = useRef(false);

  useEffect(() => {
    if (!isOpen) {
      hasTrackedOpenRef.current = false;
      setJoining(false);
      setSuccessMessage(null);
      setErrorMessage(null);
      setVerifyEmailModalOpen(false);
      return;
    }
    if (!trackPaywallView || hasTrackedOpenRef.current) {
      return;
    }
    hasTrackedOpenRef.current = true;
    void trackAnalyticsEvent({
      eventType: "PAYWALL_VIEWED",
      metadata: {
        source,
        feature,
        path: pathname,
      },
    });
  }, [feature, isOpen, pathname, source, trackPaywallView]);

  const handleJoinWaitlist = async () => {
    const authUser = getAuthUser();
    if (authUser && !authUser.emailVerifiedAt) {
      setVerifyEmailModalOpen(true);
      return;
    }
    setJoining(true);
    setErrorMessage(null);
    try {
      void trackAnalyticsEvent({
        eventType: "UPGRADE_CLICKED",
        metadata: {
          source,
          feature,
          path: pathname,
          target: "premium_waitlist",
        },
      });
      const response = await joinPremiumWaitlist();
      setSuccessMessage(response.message);
    } catch (err) {
      if (isEmailNotVerifiedError(err)) {
        setVerifyEmailModalOpen(true);
      } else {
        setErrorMessage(err instanceof Error ? err.message : "Could not join the waitlist right now. Please try again.");
      }
    } finally {
      setJoining(false);
    }
  };

  return (
    <>
      <AppModal
        isOpen={isOpen}
        title="Premium is coming soon"
        onClose={onClose}
        panelClassName="max-w-[460px]"
        actions={successMessage ? (
          <div className="flex justify-end">
            <Button type="button" onClick={onClose}>
              Close
            </Button>
          </div>
        ) : (
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={onClose} disabled={joining}>
              Maybe Later
            </Button>
            <Button
              type="button"
              onClick={() => void handleJoinWaitlist()}
              loading={joining}
              loadingText="Joining..."
            >
              Join Waitlist
            </Button>
          </div>
        )}
      >
        <div className="space-y-4 text-sm leading-relaxed text-foreground/85">
          <p>
            Premium will include:
          </p>
          <ul className="list-disc space-y-1 pl-5">
            {PREMIUM_FEATURES.map((featureItem) => (
              <li key={featureItem}>{featureItem}</li>
            ))}
          </ul>
          <p>
            We&apos;re currently enabling payments. Join the waitlist and we&apos;ll notify you when Premium launches.
          </p>
          {successMessage ? (
            <div className="rounded-lg border border-blue-500/20 bg-blue-500/10 p-3 text-sm text-foreground">
              {successMessage}
            </div>
          ) : null}
          {errorMessage ? (
            <div className="rounded-lg border border-red-500/30 bg-red-50/70 p-3 text-sm text-red-700 dark:bg-red-950/20 dark:text-red-300">
              {errorMessage}
            </div>
          ) : null}
        </div>
      </AppModal>
      <VerifyEmailRequiredModal
        isOpen={verifyEmailModalOpen}
        onClose={() => setVerifyEmailModalOpen(false)}
      />
    </>
  );
}
