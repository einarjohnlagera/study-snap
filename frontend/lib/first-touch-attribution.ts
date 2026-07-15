import type { FirstTouchAttribution } from "@/lib/api";

const FIRST_TOUCH_ATTRIBUTION_STORAGE_KEY = "notelib-first-touch-attribution";
const UTM_VALUE_MAX_LENGTH = 255;
const REFERRER_MAX_LENGTH = 2048;
const UTM_QUERY_PARAMS = [
  ["utm_source", "utmSource"],
  ["utm_medium", "utmMedium"],
  ["utm_campaign", "utmCampaign"],
  ["utm_content", "utmContent"],
  ["utm_term", "utmTerm"],
] as const;

type AttributionKey = keyof FirstTouchAttribution;

function normalizeValue(value: string | null | undefined, maxLength = UTM_VALUE_MAX_LENGTH): string | undefined {
  const normalized = value?.trim();
  return normalized ? normalized.slice(0, maxLength) : undefined;
}

function toAttribution(value: unknown): FirstTouchAttribution {
  if (!value || typeof value !== "object") {
    return {};
  }

  const stored = value as Record<string, unknown>;
  return {
    utmSource: typeof stored.utmSource === "string" ? normalizeValue(stored.utmSource) : undefined,
    utmMedium: typeof stored.utmMedium === "string" ? normalizeValue(stored.utmMedium) : undefined,
    utmCampaign: typeof stored.utmCampaign === "string" ? normalizeValue(stored.utmCampaign) : undefined,
    utmContent: typeof stored.utmContent === "string" ? normalizeValue(stored.utmContent) : undefined,
    utmTerm: typeof stored.utmTerm === "string" ? normalizeValue(stored.utmTerm) : undefined,
    referrer: typeof stored.referrer === "string" ? normalizeValue(stored.referrer, REFERRER_MAX_LENGTH) : undefined,
  };
}

export function captureFirstTouchAttribution(): void {
  if (typeof globalThis.window === "undefined") {
    return;
  }

  try {
    if (globalThis.sessionStorage.getItem(FIRST_TOUCH_ATTRIBUTION_STORAGE_KEY) !== null) {
      return;
    }

    const queryParams = new URLSearchParams(globalThis.location.search);
    const attribution: FirstTouchAttribution = {
      referrer: normalizeValue(globalThis.document.referrer, REFERRER_MAX_LENGTH),
    };
    for (const [queryParam, attributionKey] of UTM_QUERY_PARAMS) {
      const value = normalizeValue(queryParams.get(queryParam));
      if (value) {
        attribution[attributionKey as AttributionKey] = value;
      }
    }
    globalThis.sessionStorage.setItem(FIRST_TOUCH_ATTRIBUTION_STORAGE_KEY, JSON.stringify(attribution));
  } catch {
    // Attribution collection is optional and must not affect navigation or signup.
  }
}

export function getFirstTouchAttribution(): FirstTouchAttribution {
  if (typeof globalThis.window === "undefined") {
    return {};
  }

  try {
    const stored = globalThis.sessionStorage.getItem(FIRST_TOUCH_ATTRIBUTION_STORAGE_KEY);
    return stored ? toAttribution(JSON.parse(stored)) : {};
  } catch {
    return {};
  }
}
