"use client";

import { useEffect } from "react";
import { trackAnalyticsEvent, type AnalyticsEventType } from "@/lib/api";

type AnalyticsPageViewTrackerProps = {
  eventType: AnalyticsEventType;
  entityId?: string | null;
  metadata?: Record<string, unknown>;
};

export function AnalyticsPageViewTracker({
  eventType,
  entityId = null,
  metadata,
}: AnalyticsPageViewTrackerProps) {
  useEffect(() => {
    void trackAnalyticsEvent({ eventType, entityId, metadata });
  }, [entityId, eventType, metadata]);

  return null;
}
