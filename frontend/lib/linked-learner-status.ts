import type { LinkedLearnerResponse } from "@/lib/api";

/**
 * ONE vocabulary for why a learning connection is not active, shared by the Learning Connections
 * page and the Dashboard card.
 *
 * ⚠️ Since v0.90.0 an EMAIL invitation creates a relationship row only at ACCEPTANCE, so for that
 * path `PENDING` does not mean "waiting for someone to accept". It means the invitation WAS
 * accepted and guardian consent is outstanding, or an active connection was paused by a birth-year
 * correction. Telling that supporter their invitation still needs accepting is false — and it is
 * exactly the copy that shipped.
 *
 * ⚠️ v0.94.0 MADE THAT PREMISE PARTLY FALSE AGAIN, which is why the branch below exists. Redeeming
 * a shareable invitation link creates a `PENDING` row immediately, with the REDEEMER as initiator
 * and the link's creator as the party who must confirm. For those rows `PENDING` means exactly
 * "waiting for someone to accept" — and that someone is the caller. Falling through to `UNRESOLVED`
 * told a creator whose link had just been redeemed only that the connection was "not active yet",
 * never that they were the one holding it up. `incomingInvitation` is the server's own
 * `PENDING && caller is the invited party`, so it identifies that case exactly.
 *
 * ⚠️ Rows written before the V122 migration DO carry the old meaning, with nothing marking them.
 * `UNRESOLVED` gives them neutral copy that is true either way. **It is not the ONLY branch such a
 * row can reach** — a legacy row whose learner never declared a year matches `BIRTH_YEAR_REQUIRED`
 * first. That copy is still accurate rather than misleading, because a legacy acceptance does
 * require the year before it can complete; it simply names the nearer of two blockers.
 */
export type LinkedLearnerPendingReason =
  | "BIRTH_YEAR_REQUIRED"
  | "GUARDIAN_CONSENT_REQUIRED"
  | "GUARDIAN_CONSENT_RECORDED"
  | "AWAITING_CALLER_CONFIRMATION"
  | "UNRESOLVED";

export function resolvePendingReason(link: LinkedLearnerResponse): LinkedLearnerPendingReason {
  if (link.birthYearRequired) return "BIRTH_YEAR_REQUIRED";
  if (link.guardianConsentRequired && !link.guardianConsentRecorded) return "GUARDIAN_CONSENT_REQUIRED";
  if (link.guardianConsentRequired && link.guardianConsentRecorded) return "GUARDIAN_CONSENT_RECORDED";
  // ⚠️ AFTER the blocker branches on purpose. A birth year or an outstanding consent is the NEARER
  // thing to resolve, and naming confirmation first would send the caller to a button that cannot
  // complete yet.
  if (link.incomingInvitation) return "AWAITING_CALLER_CONFIRMATION";
  return "UNRESOLVED";
}

export const LINKED_LEARNER_STATUS_COPY = {
  active: "Learning connection active",
  waitingForLearnerBirthYear: "Waiting for the learner to record their birth year.",
  pausedForConsentLearnerView: "This connection is paused until the supporter records guardian consent.",
  // ⚠️ These describe the connection's STATUS and must not assert anything about access.
  // Two reasons, both learned the hard way. (1) Since v0.93.0 an ACCEPTED relationship no longer
  // implies progress access — it needs a live learner-issued PROGRESS grant — so any copy promising
  // that activation or consent "unblocks progress" is false. The earlier wording survived two
  // releases because this file holds prose rather than status literals, so v0.93.0's own sweep for
  // `"ACCEPTED"` never reached it. (2) The DTO zeroes `*SharedWithMe` on a non-ACCEPTED row, so the
  // frontend genuinely CANNOT tell "granted, now paused" from "never granted" — claiming either
  // would be a guess rendered as fact.
  pausedForConsentSupporterView:
    "This connection is paused until you record guardian consent.",
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
        // ⚠️ Says only that the connection is finishing. It must NOT promise progress: since
        // v0.93.0 that requires a separate grant the learner alone can give.
        detail: "This connection is finishing activation.",
      };
    case "AWAITING_CALLER_CONFIRMATION":
      return {
        headline: "Waiting for you to confirm",
        detail: "Someone opened your invitation link. Confirm the request to activate this connection.",
      };
    default:
      // ⚠️ Neutral on purpose: this branch must not assert either meaning of PENDING, because a
      // legacy pre-V122 row with no blocker really is still awaiting acceptance.
      return {
        headline: "Connection not active yet",
        detail: "Open Learning Connections to see what this connection is waiting on.",
      };
  }
}
