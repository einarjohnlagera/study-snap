import type { LinkedLearnerResponse } from "@/lib/api";

/**
 * ONE vocabulary for why a learning connection is not active, shared by the Learning Connections
 * page and the Dashboard card.
 *
 * ⚠️ Since v0.90.0 a relationship row is created only at ACCEPTANCE, so `PENDING` no longer means
 * "waiting for someone to accept". It means the invitation WAS accepted and guardian consent is
 * outstanding, or an active connection was paused by a birth-year correction. Telling a supporter
 * their invitation still needs accepting is therefore false on the common path — and it is exactly
 * the copy that shipped.
 *
 * ⚠️ Rows written before the V122 migration DO carry the old meaning, with nothing marking them.
 * That is what `UNRESOLVED` is for: neutral copy that is true either way, rather than a confident
 * claim that would be wrong for one of the two populations.
 */
export type LinkedLearnerPendingReason =
  | "BIRTH_YEAR_REQUIRED"
  | "GUARDIAN_CONSENT_REQUIRED"
  | "GUARDIAN_CONSENT_RECORDED"
  | "UNRESOLVED";

export function resolvePendingReason(link: LinkedLearnerResponse): LinkedLearnerPendingReason {
  if (link.birthYearRequired) return "BIRTH_YEAR_REQUIRED";
  if (link.guardianConsentRequired && !link.guardianConsentRecorded) return "GUARDIAN_CONSENT_REQUIRED";
  if (link.guardianConsentRequired && link.guardianConsentRecorded) return "GUARDIAN_CONSENT_RECORDED";
  return "UNRESOLVED";
}

export const LINKED_LEARNER_STATUS_COPY = {
  active: "Learning connection active",
  waitingForLearnerBirthYear: "Waiting for the learner to record their birth year.",
  pausedForConsentLearnerView: "This connection is paused until the supporter records guardian consent.",
  pausedForConsentSupporterView:
    "Your progress access is paused because guardian consent is required. Record consent above to unblock the connection.",
  consentRecorded: "Guardian consent has been recorded.",
} as const;

/** Headline plus optional detail for a supporter-side card. */
export function describeSupportedLearnerStatus(link: LinkedLearnerResponse): {
  headline: string;
  detail: string | null;
} {
  if (link.status === "ACCEPTED") {
    return { headline: LINKED_LEARNER_STATUS_COPY.active, detail: null };
  }
  switch (resolvePendingReason(link)) {
    case "BIRTH_YEAR_REQUIRED":
      return {
        headline: "Waiting on the learner's birth year",
        detail: LINKED_LEARNER_STATUS_COPY.waitingForLearnerBirthYear,
      };
    case "GUARDIAN_CONSENT_REQUIRED":
      return {
        headline: "Paused — guardian consent required",
        detail: LINKED_LEARNER_STATUS_COPY.pausedForConsentSupporterView,
      };
    case "GUARDIAN_CONSENT_RECORDED":
      return {
        headline: "Guardian consent recorded",
        detail: "Progress becomes available once the connection finishes activating.",
      };
    default:
      // ⚠️ Neutral on purpose. This is the only branch a legacy pre-V122 row can reach, and it
      // must not assert either meaning of PENDING.
      return {
        headline: "Connection not active yet",
        detail: "Open Learning Connections to see what this connection is waiting on.",
      };
  }
}
