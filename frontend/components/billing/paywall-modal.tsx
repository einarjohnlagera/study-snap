"use client";

import { PremiumWaitlistModal } from "@/components/billing/premium-waitlist-modal";

export type PaywallModalVariant =
  | "challenge-quiz"
  | "adaptive-practice"
  | "study-pack-limit";

type PaywallModalProps = {
  isOpen: boolean;
  variant: PaywallModalVariant;
  onClose: () => void;
};

export function PaywallModal({
  isOpen,
  variant,
  onClose,
}: PaywallModalProps) {
  return (
    <PremiumWaitlistModal
      isOpen={isOpen}
      onClose={onClose}
      source="paywall_modal"
      feature={variant}
      trackPaywallView
    />
  );
}
