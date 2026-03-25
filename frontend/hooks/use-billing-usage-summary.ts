"use client";

import { useCallback, useEffect, useState } from "react";
import { getMyPlan, type MePlanResponse } from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";

export function useBillingUsageSummary(enabled = true) {
  const [usageSummary, setUsageSummary] = useState<MePlanResponse | null>(() => getAuthUser()?.planSummary ?? null);
  const [usageLoaded, setUsageLoaded] = useState(false);

  const loadUsageSummary = useCallback(async () => {
    if (!enabled) {
      setUsageSummary(null);
      setUsageLoaded(true);
      return null;
    }

    try {
      const nextUsageSummary = await getMyPlan();
      setUsageSummary(nextUsageSummary);
      const authUser = getAuthUser();
      if (authUser) {
        setAuthUser({
          ...authUser,
          planSummary: nextUsageSummary,
        });
      }
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
