"use client";

import { useCallback, useEffect, useState } from "react";
import { getBillingPricing, type BillingPricingResponse } from "@/lib/api";

export function useBillingPricing(enabled = true) {
  const [billingPricing, setBillingPricing] = useState<BillingPricingResponse | null>(null);
  const [pricingLoaded, setPricingLoaded] = useState(false);

  const loadBillingPricing = useCallback(async () => {
    if (!enabled) {
      setBillingPricing(null);
      setPricingLoaded(true);
      return null;
    }

    try {
      const nextPricing = await getBillingPricing();
      setBillingPricing(nextPricing);
      return nextPricing;
    } catch {
      setBillingPricing(null);
      return null;
    } finally {
      setPricingLoaded(true);
    }
  }, [enabled]);

  useEffect(() => {
    void loadBillingPricing();
  }, [loadBillingPricing]);

  return {
    billingPricing,
    pricingLoaded,
    refreshBillingPricing: loadBillingPricing,
  };
}
