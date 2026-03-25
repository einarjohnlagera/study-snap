"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { PremiumWaitlistModal } from "@/components/billing/premium-waitlist-modal";

type PremiumWaitlistButtonProps = {
  label: string;
  source: string;
  feature?: string | null;
  variant?: React.ComponentProps<typeof Button>["variant"];
  size?: React.ComponentProps<typeof Button>["size"];
  className?: string;
};

export function PremiumWaitlistButton({
  label,
  source,
  feature = null,
  variant = "default",
  size = "default",
  className,
}: PremiumWaitlistButtonProps) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <Button
        type="button"
        variant={variant}
        size={size}
        className={className}
        onClick={() => setIsOpen(true)}
      >
        {label}
      </Button>
      <PremiumWaitlistModal
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        source={source}
        feature={feature}
      />
    </>
  );
}
