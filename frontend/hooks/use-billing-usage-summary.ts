"use client";

import { useCallback, useEffect, useState } from "react";
import { getBillingUsageSummary, type BillingUsageSummaryResponse } from "@/lib/api";

export function useBillingUsageSummary(enabled = true) {
  const [usageSummary, setUsageSummary] = useState<BillingUsageSummaryResponse | null>(null);
  const [usageLoaded, setUsageLoaded] = useState(false);

  const loadUsageSummary = useCallback(async () => {
    if (!enabled) {
      setUsageSummary(null);
      setUsageLoaded(true);
      return null;
    }

    try {
      const nextUsageSummary = await getBillingUsageSummary();
      setUsageSummary(nextUsageSummary);
      return nextUsageSummary;
    } catch {
      return null;
    } finally {
      setUsageLoaded(true);
    }
  }, [enabled]);

  useEffect(() => {
    void loadUsageSummary();
  }, [loadUsageSummary]);

  return {
    usageSummary,
    usageLoaded,
    refreshUsageSummary: loadUsageSummary,
  };
}
