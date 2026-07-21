"use client";

import { useEffect } from "react";
import { trackAnalyticsEvent, type AnalyticsEventType } from "@/lib/api";
import { bucketReferrerSource, type ReferrerSource } from "@/lib/referrer-source";

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
    let referrerSource: ReferrerSource = "direct";
    try {
      if (globalThis.document !== undefined) {
        referrerSource = bucketReferrerSource(globalThis.document.referrer);
      }
    } catch {
      referrerSource = "direct";
    }
    void trackAnalyticsEvent({
      eventType,
      entityId,
      metadata: { ...metadata, referrerSource },
    });
  }, [entityId, eventType, metadata]);

  return null;
}
