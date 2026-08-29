import type { LinkedLearnerResponse } from "@/lib/api";
import {
  describeSupportedLearnerStatus,
  resolvePendingReason,
} from "@/lib/linked-learner-status";

const baseLink: LinkedLearnerResponse = {
  id: "link-1",
  callerRole: "SUPPORTER",
  initiatedBy: "LEARNER",
  incomingInvitation: false,
  counterpartyDisplayName: "Pat Learner",
  counterpartyEmail: null,
  status: "PENDING",
  createdAt: "2026-08-29T10:00:00Z",
  acceptedAt: null,
  revokedAt: null,
  birthYearRequired: false,
  guardianConsentRequired: false,
  guardianConsentRecorded: false,
  activitySharedByMe: false,
  activitySharedWithMe: false,
  progressSharedByMe: false,
  progressSharedWithMe: false,
};

it("tells the link creator that they are the one who must confirm", () => {
  // v0.94.0 link redemption creates PENDING with the redeemer as initiator, so for these rows
  // PENDING really does mean "waiting for someone to accept" — and that someone is the caller.
  const redeemed = { ...baseLink, incomingInvitation: true };

  expect(resolvePendingReason(redeemed)).toBe("AWAITING_CALLER_CONFIRMATION");

  const status = describeSupportedLearnerStatus(redeemed);
  expect(status.headline).toBe("Waiting for you to confirm");
  expect(status.detail).toMatch(/confirm the request/i);
  // Pin the CLASS: this row must never fall back to copy that names no actor.
  expect(status.headline).not.toMatch(/not active yet/i);
});

it("names the nearer blocker before asking the caller to confirm", () => {
  // A caller sent to a confirm button that cannot complete yet is worse than no prompt at all.
  const blocked = { ...baseLink, incomingInvitation: true, birthYearRequired: true };
  expect(resolvePendingReason(blocked)).toBe("BIRTH_YEAR_REQUIRED");

  const consentBlocked = {
    ...baseLink,
    incomingInvitation: true,
    guardianConsentRequired: true,
  };
  expect(resolvePendingReason(consentBlocked)).toBe("GUARDIAN_CONSENT_REQUIRED");
});

it("stays neutral for a pending row the caller is not the invited party on", () => {
  // A legacy pre-V122 row really may still be awaiting acceptance, so this branch must not
  // assert either meaning of PENDING.
  expect(resolvePendingReason(baseLink)).toBe("UNRESOLVED");
  expect(describeSupportedLearnerStatus(baseLink).headline).toBe("Connection not active yet");
});

it("reports an active connection without a pending reason", () => {
  const accepted = { ...baseLink, status: "ACCEPTED" as const };
  expect(describeSupportedLearnerStatus(accepted).detail).toBeNull();
});
