import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import LinkedLearnersPage from "./page";
import {
  acceptLinkedLearner,
  acceptLinkedLearnerInvitation,
  ApiRequestError,
  createLinkedLearnerInvitationLink,
  getLinkedLearnerActivity,
  listLinkedLearnerInvitations,
  listLinkedLearnerInvitationLinks,
  correctLinkedLearnerBirthYear,
  getLinkedLearners,
  inviteLinkedLearner,
  recordLinkedLearnerGuardianConsent,
  previewLinkedLearnerBirthYearCorrection,
  revokeLinkedLearnerInvitation,
  revokeLinkedLearnerInvitationLink,
  revokeLinkedLearner,
  setLinkedLearnerActivityGrant,
  setLinkedLearnerProgressGrant,
  type LinkedLearnerInvitationResponse,
  type LinkedLearnerResponse,
} from "@/lib/api";

const replace = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: jest.fn(), refresh: jest.fn() }),
}));

jest.mock("@/lib/api", () => ({
  acceptLinkedLearner: jest.fn(),
  createLinkedLearnerInvitationLink: jest.fn(),
  correctLinkedLearnerBirthYear: jest.fn(),
  getLinkedLearners: jest.fn(),
  inviteLinkedLearner: jest.fn(),
  recordLinkedLearnerBirthYear: jest.fn(),
  recordLinkedLearnerGuardianConsent: jest.fn(),
  previewLinkedLearnerBirthYearCorrection: jest.fn(),
  revokeLinkedLearner: jest.fn(),
  listLinkedLearnerInvitations: jest.fn(),
  listLinkedLearnerInvitationLinks: jest.fn(),
  acceptLinkedLearnerInvitation: jest.fn(),
  revokeLinkedLearnerInvitation: jest.fn(),
  revokeLinkedLearnerInvitationLink: jest.fn(),
  setLinkedLearnerActivityGrant: jest.fn(),
  setLinkedLearnerProgressGrant: jest.fn(),
  getLinkedLearnerActivity: jest.fn(),
  ApiRequestError: class ApiRequestError extends Error {
    status: number;
    constructor(message: string, options: { status: number }) {
      super(message);
      this.status = options.status;
    }
  },
}));

const baseLink: LinkedLearnerResponse = {
  id: "link-1",
  callerRole: "LEARNER",
  initiatedBy: "SUPPORTER",
  incomingInvitation: true,
  counterpartyDisplayName: "Pat Supporter",
  counterpartyEmail: "pat@example.com",
  status: "PENDING",
  createdAt: "2026-08-19T10:00:00Z",
  acceptedAt: null,
  revokedAt: null,
  expiresAt: null,
  birthYearRequired: false,
  guardianConsentRequired: false,
  guardianConsentRecorded: false,
  activitySharedByMe: false,
  activitySharedWithMe: false,
  progressSharedByMe: false,
  progressSharedWithMe: false,
};

const invitationLink = {
  id: "invite-link-1",
  token: "AbCdEf0123456789GhIjKl",
  url: "https://notelib.test/linked-learners/invite/AbCdEf0123456789GhIjKl",
  creatorRole: "SUPPORTER" as const,
  createdAt: "2026-08-28T01:00:00Z",
  expiresAt: "2026-09-27T01:00:00Z",
};

beforeEach(() => {
  (listLinkedLearnerInvitations as jest.Mock).mockResolvedValue([]);
  (listLinkedLearnerInvitationLinks as jest.Mock).mockResolvedValue([]);
  jest.clearAllMocks();
  jest.mocked(getLinkedLearners).mockResolvedValue([]);
});

it("shows a pending request's deadline so requests cannot die in silence", async () => {
  // ⚠️ v0.97.0 added a hard 30-day request deadline. Without a surface for it this repeats the
  // defect v0.94.0 item 3 fixed for invitations — sharpest on the consent path, where a supporter
  // who must record guardian consent would otherwise never learn the window exists.
  jest.mocked(getLinkedLearners).mockResolvedValue([
    { ...baseLink, status: "PENDING", expiresAt: "2026-09-28T00:00:00Z" },
  ]);

  render(<LinkedLearnersPage />);

  expect(await screen.findByText(/Expires .*if it is not confirmed/)).toBeInTheDocument();
});

it("shows no deadline once a request is accepted, because null means not on the clock", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([
    { ...baseLink, status: "ACCEPTED", acceptedAt: "2026-08-20T10:00:00Z", expiresAt: null },
  ]);

  render(<LinkedLearnersPage />);

  await screen.findByText(/Accepted/);
  expect(screen.queryByText(/if it is not confirmed/)).not.toBeInTheDocument();
});

it("routes a mid-onboarding caller to onboarding instead of a dead error", async () => {
  // ⚠️ v0.98.0 item 1 requires finished onboarding to ACCEPT. This page's guard keys on
  // needsOnboarding(), which treats two cohorts as onboarded while the server column is null, so
  // this 403 is reachable by real accounts. Without the redirect they read "Could not accept the
  // invitation" with no idea what to do.
  jest.mocked(listLinkedLearnerInvitations).mockResolvedValue([{
    id: "inv-onboarding", incoming: true, inviterRole: "SUPPORTER",
    invitedEmail: "me@example.com", inviterName: "Aunt May", createdAt: "2026-08-20T00:00:00Z",
    expiresAt: "2026-09-19T00:00:00Z", expired: false,
  } as unknown as LinkedLearnerInvitationResponse]);
  jest.mocked(acceptLinkedLearnerInvitation).mockRejectedValue(
    Object.assign(new Error("Finish setting up your account before connecting with someone."), {
      action: "COMPLETE_ONBOARDING",
      status: 403,
    }));

  render(<LinkedLearnersPage />);
  fireEvent.click(await screen.findByRole("button", { name: "Accept" }));

  await waitFor(() => expect(replace).toHaveBeenCalledWith("/onboarding"));
});

it("loads live invitation links on refresh", async () => {
  jest.mocked(listLinkedLearnerInvitationLinks).mockResolvedValue([invitationLink]);

  render(<LinkedLearnersPage />);

  expect(await screen.findByDisplayValue(invitationLink.url)).toBeInTheDocument();
  expect(listLinkedLearnerInvitationLinks).toHaveBeenCalledTimes(1);
});

it("creates a single-use invitation link", async () => {
  jest.mocked(createLinkedLearnerInvitationLink).mockResolvedValue(invitationLink);
  jest.mocked(listLinkedLearnerInvitationLinks)
    .mockResolvedValueOnce([])
    .mockResolvedValueOnce([invitationLink]);
  render(<LinkedLearnersPage />);
  await screen.findByText("No live invitation links.");

  fireEvent.click(screen.getByRole("button", { name: "Create invitation link" }));

  await waitFor(() => expect(createLinkedLearnerInvitationLink)
    .toHaveBeenCalledWith("SUPPORTER", null));
  expect(await screen.findByDisplayValue(invitationLink.url)).toBeInTheDocument();
  expect(listLinkedLearnerInvitationLinks).toHaveBeenCalledTimes(2);
});

it("refetches live invitation links after revocation", async () => {
  jest.mocked(listLinkedLearnerInvitationLinks)
    .mockResolvedValueOnce([invitationLink])
    .mockResolvedValueOnce([]);
  jest.mocked(revokeLinkedLearnerInvitationLink).mockResolvedValue({ message: "Revoked" });
  render(<LinkedLearnersPage />);
  await screen.findByDisplayValue(invitationLink.url);

  fireEvent.click(screen.getByRole("button", { name: "Revoke link" }));

  await waitFor(() => expect(listLinkedLearnerInvitationLinks).toHaveBeenCalledTimes(2));
  expect(screen.queryByDisplayValue(invitationLink.url)).not.toBeInTheDocument();
  expect(screen.getByText("Invitation link revoked.")).toBeInTheDocument();
});

it("refetches live invitation links on focus and removes the listener on unmount", async () => {
  jest.mocked(listLinkedLearnerInvitationLinks)
    .mockResolvedValueOnce([invitationLink])
    .mockResolvedValueOnce([]);
  const { unmount } = render(<LinkedLearnersPage />);
  await screen.findByDisplayValue(invitationLink.url);

  act(() => globalThis.dispatchEvent(new Event("focus")));

  await waitFor(() => expect(listLinkedLearnerInvitationLinks).toHaveBeenCalledTimes(2));
  expect(await screen.findByText("No live invitation links.")).toBeInTheDocument();

  unmount();
  act(() => globalThis.dispatchEvent(new Event("focus")));
  expect(listLinkedLearnerInvitationLinks).toHaveBeenCalledTimes(2);
});

it("keeps the last loaded links visible when a focus refresh fails", async () => {
  jest.mocked(listLinkedLearnerInvitationLinks)
    .mockResolvedValueOnce([invitationLink])
    .mockRejectedValueOnce(new Error("Network unavailable."));
  render(<LinkedLearnersPage />);
  await screen.findByDisplayValue(invitationLink.url);

  act(() => globalThis.dispatchEvent(new Event("focus")));

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Network unavailable. Showing the last loaded list.",
  );
  expect(screen.getByDisplayValue(invitationLink.url)).toBeInTheDocument();
});

it("clears a stale links list when a foreground refetch fails", async () => {
  jest.mocked(listLinkedLearnerInvitationLinks)
    .mockResolvedValueOnce([invitationLink])
    .mockRejectedValueOnce(new Error("Refresh failed"));
  jest.mocked(createLinkedLearnerInvitationLink).mockResolvedValue(invitationLink);
  render(<LinkedLearnersPage />);
  await screen.findByDisplayValue(invitationLink.url);

  fireEvent.click(screen.getByRole("button", { name: "Create invitation link" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("Refresh failed");
  expect(screen.queryByDisplayValue(invitationLink.url)).not.toBeInTheDocument();
});

it("treats a revoke 404 as an already-completed outcome and refetches", async () => {
  jest.mocked(listLinkedLearnerInvitationLinks)
    .mockResolvedValueOnce([invitationLink])
    .mockResolvedValueOnce([]);
  jest.mocked(revokeLinkedLearnerInvitationLink).mockRejectedValue(
    new ApiRequestError("Not found", { status: 404 }),
  );
  render(<LinkedLearnersPage />);
  await screen.findByDisplayValue(invitationLink.url);

  fireEvent.click(screen.getByRole("button", { name: "Revoke link" }));

  expect(await screen.findByText("Invitation link was already gone.")).toBeInTheDocument();
  expect(listLinkedLearnerInvitationLinks).toHaveBeenCalledTimes(2);
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.queryByDisplayValue(invitationLink.url)).not.toBeInTheDocument();
});

it("keeps a live link visible when revocation fails", async () => {
  jest.mocked(listLinkedLearnerInvitationLinks).mockResolvedValue([invitationLink]);
  jest.mocked(revokeLinkedLearnerInvitationLink).mockRejectedValue(new Error("Network unavailable"));
  render(<LinkedLearnersPage />);
  await screen.findByDisplayValue(invitationLink.url);

  fireEvent.click(screen.getByRole("button", { name: "Revoke link" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("Network unavailable");
  expect(screen.getByDisplayValue(invitationLink.url)).toBeInTheDocument();
});

it("invites in either direction", async () => {
  jest.mocked(inviteLinkedLearner).mockResolvedValue({ message: "Invitation response" });
  render(<LinkedLearnersPage />);
  await screen.findByText("No invitations or connections yet.");

  fireEvent.click(screen.getByRole("button", { name: /they will support me/i }));
  fireEvent.change(screen.getByLabelText("Their email"), { target: { value: "mentor@example.com" } });
  fireEvent.click(screen.getByRole("button", { name: "Send invitation" }));

  await waitFor(() => expect(inviteLinkedLearner).toHaveBeenCalledWith("mentor@example.com", "LEARNER", null));

  fireEvent.click(screen.getByRole("button", { name: /i will support them/i }));
  fireEvent.change(screen.getByLabelText("Their email"), { target: { value: "learner@example.com" } });
  fireEvent.click(screen.getByRole("button", { name: "Send invitation" }));

  await waitFor(() => expect(inviteLinkedLearner).toHaveBeenCalledWith("learner@example.com", "SUPPORTER", null));
});

it("accepts an incoming invitation", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([baseLink]);
  jest.mocked(acceptLinkedLearner).mockResolvedValue({
    ...baseLink,
    status: "ACCEPTED",
    incomingInvitation: false,
    acceptedAt: "2026-08-19T11:00:00Z",
  });
  render(<LinkedLearnersPage />);

  fireEvent.click(await screen.findByRole("button", { name: "Accept invitation" }));

  await waitFor(() => expect(acceptLinkedLearner).toHaveBeenCalledWith("link-1", null, false));
  expect(await screen.findByText("accepted")).toBeInTheDocument();
});

it("loads a link-redeemed provisional minor as confirmable and completes guardian consent", async () => {
  const minorLink: LinkedLearnerResponse = {
    ...baseLink,
    callerRole: "SUPPORTER",
    initiatedBy: "LEARNER",
    birthYearRequired: false,
    guardianConsentRequired: true,
  };
  jest.mocked(getLinkedLearners).mockResolvedValue([minorLink]);
  jest.mocked(recordLinkedLearnerGuardianConsent).mockResolvedValue({
    ...minorLink,
    guardianConsentRecorded: true,
  });
  render(<LinkedLearnersPage />);

  expect(await screen.findByText(/consent wording placeholder pending counsel/i)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Accept invitation" })).toBeDisabled();
  expect(acceptLinkedLearner).not.toHaveBeenCalled();

  fireEvent.click(screen.getByRole("checkbox", { name: "Confirm guardian consent attestation" }));
  fireEvent.click(screen.getByRole("button", { name: "Accept invitation" }));
  await waitFor(() => expect(acceptLinkedLearner).toHaveBeenCalledWith("link-1", null, true));
});

it("rolls an optimistic revoke back when the API fails", async () => {
  const acceptedLink = { ...baseLink, status: "ACCEPTED" as const, incomingInvitation: false };
  jest.mocked(getLinkedLearners).mockResolvedValue([acceptedLink]);
  jest.mocked(revokeLinkedLearner).mockRejectedValue(new Error("Network failed"));
  render(<LinkedLearnersPage />);

  fireEvent.click(await screen.findByRole("button", { name: "Revoke" }));
  expect(screen.getByText("revoked")).toBeInTheDocument();

  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Network failed"));
  expect(screen.getByText("accepted")).toBeInTheDocument();
});

it("offers progress only when the counterparty has granted progress access", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([
    { ...baseLink, id: "accepted-support", callerRole: "SUPPORTER", status: "ACCEPTED", incomingInvitation: false, progressSharedWithMe: true },
    { ...baseLink, id: "accepted-without-grant", callerRole: "SUPPORTER", status: "ACCEPTED", incomingInvitation: false },
    { ...baseLink, id: "pending-support", callerRole: "SUPPORTER", status: "PENDING", incomingInvitation: false, progressSharedWithMe: false },
    { ...baseLink, id: "accepted-learning", callerRole: "LEARNER", status: "ACCEPTED", incomingInvitation: false },
  ]);

  render(<LinkedLearnersPage />);

  expect(await screen.findByRole("link", { name: "View progress" })).toHaveAttribute(
    "href",
    "/linked-learners/accepted-support/progress",
  );
  expect(screen.getAllByText("pending")).toHaveLength(1);
  expect(screen.getAllByRole("link", { name: "View progress" })).toHaveLength(1);
});

it("renders an expired request distinctly after loading it from the server", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...baseLink,
    status: "EXPIRED",
    incomingInvitation: false,
    // ⚠️ NON-NULL on purpose, and this is the realistic shape: markExpiredIfPending and
    // markRevokedIfLive deliberately do not clear expires_at, so a production terminal row carries
    // a past deadline. A null fixture here made the PENDING gate untestable — the render could have
    // been gated on `link.expiresAt` alone and every test still passed, showing an expired card
    // that also said "Expires <past date> if it is not confirmed".
    expiresAt: "2026-08-01T00:00:00Z",
  }]);

  render(<LinkedLearnersPage />);

  expect(await screen.findByText(/connection request expired/i)).toBeInTheDocument();
  expect(screen.getByText(/new invitation/i)).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Revoke" })).not.toBeInTheDocument();
  // ⚠️ A terminal row carries a non-null expires_at in production, so the deadline line must be
  // gated on PENDING and not merely on the field being present. Without this, gating on
  // `link.expiresAt` alone passes while an expired card also reads "Expires <past date>".
  expect(screen.queryByText(/if it is not confirmed/)).not.toBeInTheDocument();
  expect(getLinkedLearners).toHaveBeenCalledTimes(1);
});

it("keeps an unavailable sharing switch reachable instead of removing it from the tab order", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...baseLink,
    callerRole: "LEARNER",
    status: "PENDING",
    incomingInvitation: false,
    activitySharedByMe: false,
    progressSharedByMe: false,
  }]);

  render(<LinkedLearnersPage />);

  const toggle = await screen.findByRole("switch", { name: /share my study activity/i });
  // ⚠️ A native `disabled` button is removed from the tab order entirely, so a keyboard or
  // screen-reader user cannot reach the switch, hear that it exists, or learn why it is
  // unavailable. aria-disabled keeps it discoverable and announced while still refusing the action.
  expect(toggle).not.toBeDisabled();
  expect(toggle).toHaveAttribute("aria-disabled", "true");
  toggle.focus();
  expect(toggle).toHaveFocus();

  fireEvent.click(toggle);
  expect(setLinkedLearnerActivityGrant).not.toHaveBeenCalled();
});

it("renders activity in both directions and progress control only for the learner", async () => {
  const accepted = {
    ...baseLink,
    status: "ACCEPTED" as const,
    incomingInvitation: false,
  };
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...accepted,
    callerRole: "LEARNER",
    counterpartyDisplayName: "Alex",
    activitySharedByMe: false,
    activitySharedWithMe: true,
    progressSharedByMe: true,
  }]);
  const first = render(<LinkedLearnersPage />);

  expect(await screen.findByRole("switch", { name: "Share my study activity with Alex" })).not.toBeChecked();
  expect(screen.getByText("Alex shares their study activity with you")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "View momentum" })).toBeInTheDocument();
  expect(screen.getByRole("switch", { name: "Share my study progress with Alex" })).toBeChecked();

  first.unmount();
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...accepted,
    callerRole: "SUPPORTER",
    counterpartyDisplayName: "Blair",
    activitySharedByMe: true,
    activitySharedWithMe: false,
    progressSharedWithMe: true,
  }]);
  render(<LinkedLearnersPage />);

  expect(await screen.findByRole("switch", { name: "Share my study activity with Blair" })).toBeChecked();
  expect(screen.getByText("Blair does not share their study activity with you")).toBeInTheDocument();
  expect(screen.queryByText("Blair's study activity is visible through shared progress")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "View momentum" })).not.toBeInTheDocument();
  expect(screen.queryByRole("switch", { name: /share my study progress/i })).not.toBeInTheDocument();
  expect(screen.getByText("Blair shares their study progress with you")).toBeInTheDocument();
});

it("keeps the server-confirmed activity toggle state when the write fails", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...baseLink,
    status: "ACCEPTED",
    incomingInvitation: false,
    activitySharedByMe: false,
  }]);
  jest.mocked(setLinkedLearnerActivityGrant).mockRejectedValue(new Error("Network failed"));
  render(<LinkedLearnersPage />);

  const toggle = await screen.findByRole("switch", { name: /share my study activity/i });
  fireEvent.click(toggle);

  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Network failed"));
  expect(toggle).not.toBeChecked();
});

it("keeps the server-confirmed progress toggle state when the write fails", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...baseLink,
    callerRole: "LEARNER",
    status: "ACCEPTED",
    incomingInvitation: false,
    progressSharedByMe: false,
  }]);
  jest.mocked(setLinkedLearnerProgressGrant).mockRejectedValue(new Error("Network failed"));
  render(<LinkedLearnersPage />);

  const toggle = await screen.findByRole("switch", { name: /share my study progress/i });
  fireEvent.click(toggle);

  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Network failed"));
  expect(toggle).not.toBeChecked();
});

it("lets a learner withdraw live grants while a paused connection explains the pause", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...baseLink,
    callerRole: "LEARNER",
    status: "PENDING",
    incomingInvitation: false,
    activitySharedByMe: true,
    progressSharedByMe: true,
  }]);
  jest.mocked(setLinkedLearnerProgressGrant).mockResolvedValue({ granted: false });
  render(<LinkedLearnersPage />);

  // Pin the CLASS, not the string: nothing on a pending connection may promise that sharing
  // resumes, because a link-redeemed PENDING was never accepted and has nothing to resume.
  expect(await screen.findByText(/pending, so nothing is being shared right now/i)).toBeInTheDocument();
  expect(screen.queryByText(/resume/i)).not.toBeInTheDocument();
  const progressToggle = screen.getByRole("switch", { name: /share my study progress/i });
  expect(progressToggle).toBeEnabled();
  fireEvent.click(progressToggle);

  await waitFor(() => expect(setLinkedLearnerProgressGrant).toHaveBeenCalledWith("link-1", false));
  expect(screen.queryByRole("link", { name: "View progress" })).not.toBeInTheDocument();
});

it("renders zero momentum honestly instead of hiding it", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...baseLink,
    status: "ACCEPTED",
    incomingInvitation: false,
    activitySharedWithMe: true,
  }]);
  jest.mocked(getLinkedLearnerActivity).mockResolvedValue({
    displayName: "Pat Supporter",
    engagementMode: "FOCUSED",
    currentStreak: 0,
    longestStreak: 0,
    studyDaysThisWeek: 0,
  });
  render(<LinkedLearnersPage />);

  fireEvent.click(await screen.findByRole("button", { name: "View momentum" }));
  expect(await screen.findByText("No meaningful study activity recorded yet.")).toBeInTheDocument();
  expect(screen.getByText("Focused")).toBeInTheDocument();
});

it("refetches momentum on every expand so revoked data is never re-rendered from memory", async () => {
  // ⚠️ The privacy defect this pins: access is re-derived server-side on every request, which is what
  // makes a revoke cut immediately. Serving a cached payload on re-expand defeats that CLIENT-side —
  // collapse, the owner revokes, re-expand, and withdrawn momentum renders with no request issued, so
  // no 403 arrives and the access-ended path never runs.
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...baseLink,
    status: "ACCEPTED",
    incomingInvitation: false,
    activitySharedWithMe: true,
  }]);
  jest.mocked(getLinkedLearnerActivity).mockResolvedValue({
    displayName: "Pat Supporter",
    engagementMode: "CONSISTENCY",
    currentStreak: 7,
    longestStreak: 9,
    studyDaysThisWeek: 4,
  });
  render(<LinkedLearnersPage />);

  fireEvent.click(await screen.findByRole("button", { name: "View momentum" }));
  expect(await screen.findByText("7 days")).toBeInTheDocument();
  expect(getLinkedLearnerActivity).toHaveBeenCalledTimes(1);

  // Collapse: the payload must not survive.
  fireEvent.click(screen.getByRole("button", { name: "Hide momentum" }));
  expect(screen.queryByText("7 days")).not.toBeInTheDocument();

  // Re-expand must hit the server again rather than re-rendering what it already had.
  fireEvent.click(screen.getByRole("button", { name: "View momentum" }));
  await waitFor(() => expect(getLinkedLearnerActivity).toHaveBeenCalledTimes(2));
});

it("does not strand an open momentum panel when the grant is withdrawn", async () => {
  // The control that closes the panel lives inside the activitySharedWithMe branch, so a refresh
  // flipping the grant false must take the panel with it — not leave it open and undismissable.
  jest.mocked(getLinkedLearners)
    .mockResolvedValueOnce([{
      ...baseLink, status: "ACCEPTED", incomingInvitation: false, activitySharedWithMe: true,
    }])
    .mockResolvedValue([{
      ...baseLink, status: "ACCEPTED", incomingInvitation: false, activitySharedWithMe: false,
    }]);
  jest.mocked(getLinkedLearnerActivity).mockResolvedValue({
    displayName: "Pat Supporter",
    engagementMode: "CONSISTENCY",
    currentStreak: 7,
    longestStreak: 9,
    studyDaysThisWeek: 4,
  });
  render(<LinkedLearnersPage />);

  fireEvent.click(await screen.findByRole("button", { name: "View momentum" }));
  expect(await screen.findByText("7 days")).toBeInTheDocument();

  // A 403 on a later read is the access-ended path; it must clear the panel.
  jest.mocked(getLinkedLearnerActivity).mockRejectedValue(
    Object.assign(new Error("gone"), { status: 403, name: "ApiRequestError" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Hide momentum" }));
  fireEvent.click(screen.getByRole("button", { name: "View momentum" }));

  await waitFor(() => expect(screen.queryByText("7 days")).not.toBeInTheDocument());
});

it("shows momentum load failures inline and retries them", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([{
    ...baseLink,
    status: "ACCEPTED",
    incomingInvitation: false,
    activitySharedWithMe: true,
  }]);
  jest.mocked(getLinkedLearnerActivity)
    .mockRejectedValueOnce(new Error("Temporary failure"))
    .mockResolvedValueOnce({
      displayName: "Pat Supporter",
      engagementMode: "CONSISTENCY",
      currentStreak: 2,
      longestStreak: 5,
      studyDaysThisWeek: 3,
    });
  render(<LinkedLearnersPage />);

  fireEvent.click(await screen.findByRole("button", { name: "View momentum" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Temporary failure");
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));

  expect(await screen.findByText("2 days")).toBeInTheDocument();
  expect(getLinkedLearnerActivity).toHaveBeenCalledTimes(2);
});

it("collapses momentum and refreshes connection state when access ends", async () => {
  const accepted = {
    ...baseLink,
    status: "ACCEPTED" as const,
    incomingInvitation: false,
    activitySharedWithMe: true,
  };
  jest.mocked(getLinkedLearners)
    .mockResolvedValueOnce([accepted])
    .mockResolvedValueOnce([{ ...accepted, activitySharedWithMe: false }]);
  jest.mocked(getLinkedLearnerActivity).mockRejectedValue(
    new ApiRequestError("Access ended", { status: 404 }),
  );
  render(<LinkedLearnersPage />);

  fireEvent.click(await screen.findByRole("button", { name: "View momentum" }));

  await waitFor(() => expect(getLinkedLearners).toHaveBeenCalledTimes(2));
  expect(screen.queryByLabelText(/momentum/i)).not.toBeInTheDocument();
  expect(screen.getByText(/does not share their study activity with you/i)).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
});

it("offers birth year correction only on the learner's own connection surface", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([
    { ...baseLink, callerRole: "SUPPORTER", birthYearRequired: false },
  ]);
  const { unmount } = render(<LinkedLearnersPage />);

  await screen.findByText("Your invitations and connections");
  expect(screen.queryByRole("heading", { name: "Correct your birth year" })).not.toBeInTheDocument();

  unmount();
  jest.mocked(getLinkedLearners).mockResolvedValue([
    { ...baseLink, callerRole: "LEARNER", birthYearRequired: false },
  ]);
  render(<LinkedLearnersPage />);
  await waitFor(() => expect(screen.getByRole("heading", { name: "Correct your birth year" })).toBeInTheDocument());
});

it("warns with the affected connection count before a downward correction", async () => {
  const learnerLink = { ...baseLink, callerRole: "LEARNER" as const, birthYearRequired: false };
  jest.mocked(getLinkedLearners).mockResolvedValue([learnerLink]);
  jest.mocked(previewLinkedLearnerBirthYearCorrection).mockResolvedValue({ affectedConnectionCount: 2 });
  jest.mocked(correctLinkedLearnerBirthYear).mockResolvedValue([
    { ...learnerLink, status: "PENDING", guardianConsentRequired: true },
  ]);
  render(<LinkedLearnersPage />);

  fireEvent.change(await screen.findByLabelText("Corrected birth year"), { target: { value: "2015" } });
  fireEvent.click(screen.getByRole("button", { name: "Review correction" }));

  expect(await screen.findByText("2 connection(s) will pause until a guardian confirms.")).toBeInTheDocument();
  expect(correctLinkedLearnerBirthYear).not.toHaveBeenCalled();

  fireEvent.click(screen.getByRole("button", { name: "Apply correction" }));
  await waitFor(() => expect(correctLinkedLearnerBirthYear).toHaveBeenCalledWith(2015));
});

it("explains paused guardian-consent connections to both sides", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([
    {
      ...baseLink,
      id: "learner-paused",
      callerRole: "LEARNER",
      incomingInvitation: false,
      guardianConsentRequired: true,
    },
    {
      ...baseLink,
      id: "supporter-paused",
      callerRole: "SUPPORTER",
      incomingInvitation: false,
      guardianConsentRequired: true,
    },
  ]);
  render(<LinkedLearnersPage />);

  expect(await screen.findByText("This connection is paused until the supporter records guardian consent.")).toBeInTheDocument();
  expect(screen.getByText("This connection is paused until you record guardian consent.")).toBeInTheDocument();
  // ⚠️ Pins the v0.94.0 correction: the paused copy describes STATUS only. Since v0.93.0 an ACCEPTED
  // relationship does not imply progress access, so no status string may promise it.
  expect(screen.queryByText(/progress access is paused/i)).not.toBeInTheDocument();
  expect(screen.queryByText(/unblock the connection/i)).not.toBeInTheDocument();
});

it("shows an incoming invitation and accepts it through the invitation endpoint", async () => {
  // ⚠️ Invitations are email-keyed and separate from connections: a row exists whether or not the
  // address had an account, which is what closed the account-existence oracle. Accepting one is the
  // ONLY path that creates a relationship.
  (getLinkedLearners as jest.Mock).mockResolvedValue([]);
  (listLinkedLearnerInvitations as jest.Mock).mockResolvedValue([{
    id: "inv-1", incoming: true, inviterRole: "SUPPORTER",
    invitedEmail: "me@example.com", inviterName: "Aunt May", createdAt: "2026-08-20T00:00:00Z",
    expiresAt: "2026-09-19T00:00:00Z", expired: false,
  }]);
  (acceptLinkedLearnerInvitation as jest.Mock).mockResolvedValue({
    ...baseLink, status: "ACCEPTED", guardianConsentRequired: false, guardianConsentRecorded: false,
  });

  render(<LinkedLearnersPage />);

  expect(await screen.findByText(/Aunt May invited you/i)).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Accept" }));

  await waitFor(() => expect(acceptLinkedLearnerInvitation).toHaveBeenCalledWith("inv-1", null, false));
});

it("renders no name on an outgoing invitation even when the API supplies one", async () => {
  // The inviter typed the address and must learn nothing further from it — echoing back a resolved
  // display name is what made the list a name-harvesting oracle.
  //
  // ⚠️ THIS TEST USED TO MOCK `inviterName: null` AND ASSERT THE EMAIL RENDERED, which proved
  // nothing: it mocked the very absence it claimed to verify, and would have passed unchanged if
  // the component rendered every name it was handed. Supplying a name here makes the assertion
  // real — the OUTGOING row must not display it regardless of what the response carries. The
  // server-side guarantee that no name is sent is covered separately in LinkedLearnerServiceTest.
  (getLinkedLearners as jest.Mock).mockResolvedValue([]);
  (listLinkedLearnerInvitations as jest.Mock).mockResolvedValue([{
    id: "inv-2", incoming: false, inviterRole: "SUPPORTER",
    invitedEmail: "someone@example.com", inviterName: "Should Never Render", createdAt: "2026-08-20T00:00:00Z",
    expiresAt: "2026-09-19T00:00:00Z", expired: false,
  }]);

  render(<LinkedLearnersPage />);

  expect(await screen.findByText(/You invited someone@example.com/i)).toBeInTheDocument();
  expect(screen.queryByText(/Should Never Render/)).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Withdraw" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Accept" })).not.toBeInTheDocument();
});

it("offers birth-year correction after a learner-initiated invite, before anyone has accepted", async () => {
  // ⚠️ REGRESSION GUARD. invite() writes the write-once account-global birth year and creates NO
  // relationship row, so gating this card on `links` alone hid it for the whole life of an
  // unaccepted invitation — while RELEASES.md and the feature doc both name the learner-only
  // correction path as the mitigation for exactly that write. Service permitted it; UI hid it.
  (getLinkedLearners as jest.Mock).mockResolvedValue([]);
  (listLinkedLearnerInvitations as jest.Mock).mockResolvedValue([{
    id: "inv-9", incoming: false, inviterRole: "LEARNER",
    invitedEmail: "supporter@example.com", inviterName: null, createdAt: "2026-08-26T00:00:00Z",
    expiresAt: "2026-09-25T00:00:00Z", expired: false,
  }]);

  render(<LinkedLearnersPage />);

  expect(await screen.findByRole("heading", { name: "Correct your birth year" })).toBeInTheDocument();
});

it("shows the expiry clock on a live outgoing invitation", async () => {
  jest.mocked(listLinkedLearnerInvitations).mockResolvedValue([{
    id: "inv-live",
    incoming: false,
    inviterRole: "SUPPORTER",
    invitedEmail: "learner@example.com",
    inviterName: null,
    createdAt: "2026-08-20T00:00:00Z",
    expiresAt: "2026-09-19T00:00:00Z",
    expired: false,
  }]);

  render(<LinkedLearnersPage />);

  expect(await screen.findByText(/^Expires /)).toBeInTheDocument();
  expect(listLinkedLearnerInvitations).toHaveBeenCalledTimes(1);
  expect(screen.getByText("Pending")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Invite again" })).not.toBeInTheDocument();
});

it("renders an expired invitation distinctly and pre-fills address and role for invite again", async () => {
  jest.mocked(listLinkedLearnerInvitations).mockResolvedValue([{
    id: "inv-expired",
    incoming: false,
    inviterRole: "LEARNER",
    invitedEmail: "mentor@example.com",
    inviterName: null,
    createdAt: "2026-07-01T00:00:00Z",
    expiresAt: "2026-08-01T00:00:00Z",
    expired: true,
  }]);
  jest.mocked(inviteLinkedLearner).mockResolvedValue({ message: "Invitation sent." });

  render(<LinkedLearnersPage />);

  const expiredBadge = await screen.findByText("Expired");
  expect(expiredBadge.closest("li")).toHaveClass("border-amber-300");
  expect(screen.getByText(/Invite them again to send a new email/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Invite again" }));

  expect(screen.getByLabelText("Their email")).toHaveValue("mentor@example.com");
  expect(screen.getByRole("button", { name: /they will support me/i })).toHaveAttribute("aria-pressed", "true");

  fireEvent.click(screen.getByRole("button", { name: "Send invitation" }));
  await waitFor(() => expect(inviteLinkedLearner)
    .toHaveBeenCalledWith("mentor@example.com", "LEARNER", null));
});

it("keeps an expired invitation listed when invite again is rate-limited", async () => {
  jest.mocked(listLinkedLearnerInvitations).mockResolvedValue([{
    id: "inv-rate-limited",
    incoming: false,
    inviterRole: "SUPPORTER",
    invitedEmail: "learner@example.com",
    inviterName: null,
    createdAt: "2026-07-01T00:00:00Z",
    expiresAt: "2026-08-01T00:00:00Z",
    expired: true,
  }]);
  jest.mocked(inviteLinkedLearner).mockRejectedValue(new Error("Too many invitations. Try again later."));

  render(<LinkedLearnersPage />);
  fireEvent.click(await screen.findByRole("button", { name: "Invite again" }));
  fireEvent.click(screen.getByRole("button", { name: "Send invitation" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("Too many invitations. Try again later.");
  expect(screen.getByText(/You invited learner@example.com/)).toBeInTheDocument();
});

it("removes an expired invitation through the existing revoke action", async () => {
  jest.mocked(listLinkedLearnerInvitations)
    .mockResolvedValueOnce([{
      id: "inv-expired-revoke",
      incoming: false,
      inviterRole: "SUPPORTER",
      invitedEmail: "learner@example.com",
      inviterName: null,
      createdAt: "2026-07-01T00:00:00Z",
      expiresAt: "2026-08-01T00:00:00Z",
      expired: true,
    }])
    .mockResolvedValueOnce([]);
  jest.mocked(revokeLinkedLearnerInvitation).mockResolvedValue({ message: "Invitation updated." });

  render(<LinkedLearnersPage />);
  fireEvent.click(await screen.findByRole("button", { name: "Withdraw" }));

  await waitFor(() => expect(revokeLinkedLearnerInvitation).toHaveBeenCalledWith("inv-expired-revoke"));
  await waitFor(() => expect(screen.queryByText(/You invited learner@example.com/)).not.toBeInTheDocument());
});

it("reports the server's withdrawal outcome and refreshes the connection list too", async () => {
  jest.mocked(listLinkedLearnerInvitations)
    .mockResolvedValueOnce([{
      id: "inv-accepted-race",
      incoming: false,
      inviterRole: "SUPPORTER",
      invitedEmail: "learner@example.com",
      inviterName: null,
      createdAt: "2026-08-01T00:00:00Z",
      expiresAt: "2026-09-01T00:00:00Z",
      expired: false,
    }])
    .mockResolvedValueOnce([]);
  // ⚠️ The counterparty may have accepted between render and click, in which case a relationship
  // now exists and "Invitation withdrawn." is a claim the client never verified.
  jest.mocked(revokeLinkedLearnerInvitation).mockResolvedValue({
    message: "That invitation was already accepted.",
  });

  render(<LinkedLearnersPage />);
  fireEvent.click(await screen.findByRole("button", { name: "Withdraw" }));

  await waitFor(() => expect(screen.getByRole("status"))
    .toHaveTextContent("That invitation was already accepted."));
  expect(screen.queryByText("Invitation withdrawn.")).not.toBeInTheDocument();
  // Withdrawing only reloaded invitations, so a connection created in that window stayed invisible.
  await waitFor(() => expect(getLinkedLearners).toHaveBeenCalledTimes(2));
});

it("puts invitation-link feedback inside the link card, not two cards below it", async () => {
  jest.mocked(createLinkedLearnerInvitationLink).mockResolvedValue({
    id: "new-link",
    token: "AbCdEf0123456789GhIjKl",
    url: "https://notelib.test/linked-learners/invite/AbCdEf0123456789GhIjKl",
    creatorRole: "SUPPORTER",
    createdAt: "2026-08-29T10:00:00Z",
    expiresAt: "2026-09-28T10:00:00Z",
  });

  render(<LinkedLearnersPage />);
  fireEvent.click(await screen.findByRole("button", { name: "Create invitation link" }));

  const feedback = await screen.findByText("Invitation link created. It can be used once.");
  // ⚠️ Feedback rendered two cards below the action reads as no feedback at all. Assert the
  // PROPERTY that was wrong — document position — rather than a container the markup may reshape:
  // the message must appear before the next card begins, not after it.
  const nextCard = screen.getByRole("heading", { name: "Send an invitation" });
  const feedbackPrecedesNextCard =
    feedback.compareDocumentPosition(nextCard) & Node.DOCUMENT_POSITION_FOLLOWING;
  expect(feedbackPrecedesNextCard).toBeTruthy();
});

it("surfaces an invitation list load failure instead of rendering it as an empty list", async () => {
  jest.mocked(listLinkedLearnerInvitations).mockRejectedValue(new Error("Invitation list unavailable"));

  render(<LinkedLearnersPage />);

  expect(await screen.findByRole("alert")).toHaveTextContent("Invitation list unavailable");
  expect(screen.queryByRole("heading", { name: "Pending invitations" })).not.toBeInTheDocument();
});

it("keeps letters out of the birth-year field and validates the year in the form, not in a browser bubble", async () => {
  // ⚠️ The field accepted "fasdf" and relied on the browser's native `required` bubble for feedback —
  // which named no field, matched nothing else in the product, and appeared beside a value the input
  // should never have held. The form now owns its validation (`noValidate`) and reports it inline.
  render(<LinkedLearnersPage />);
  await screen.findByText("No invitations or connections yet.");

  fireEvent.click(screen.getByRole("button", { name: /they will support me/i }));
  const birthYear = screen.getByLabelText(/Your birth year/i) as HTMLInputElement;

  fireEvent.change(birthYear, { target: { value: "fasdf" } });
  expect(birthYear.value).toBe("");

  fireEvent.change(birthYear, { target: { value: "20a1b1x" } });
  expect(birthYear.value).toBe("2011");

  fireEvent.change(screen.getByLabelText("Their email"), { target: { value: "mentor@example.com" } });
  fireEvent.change(birthYear, { target: { value: "1800" } });
  fireEvent.click(screen.getByRole("button", { name: "Send invitation" }));

  expect(await screen.findByRole("alert")).toHaveTextContent(/Enter a year between 1900 and/);
  expect(inviteLinkedLearner).not.toHaveBeenCalled();
});

it("reports a missing email inline instead of submitting", async () => {
  render(<LinkedLearnersPage />);
  await screen.findByText("No invitations or connections yet.");

  fireEvent.click(screen.getByRole("button", { name: "Send invitation" }));

  expect(await screen.findByRole("alert")).toHaveTextContent(/Enter the email address/);
  expect(inviteLinkedLearner).not.toHaveBeenCalled();
});

it("steps the birth year only within the range the server accepts, and never seeds a value", async () => {
  // ⚠️ The steppers must stay inert while the field is empty. Stepping up from blank would have to start
  // somewhere, and any starting year is a declaration the person did not make — the same reason this field
  // has no default. birth_year is account-global and effectively write-once.
  render(<LinkedLearnersPage />);
  await screen.findByText("No invitations or connections yet.");
  fireEvent.click(screen.getByRole("button", { name: /they will support me/i }));

  const birthYear = screen.getByLabelText(/Your birth year/i) as HTMLInputElement;
  const up = screen.getByRole("button", { name: "Increase birth year" });
  const down = screen.getByRole("button", { name: "Decrease birth year" });

  expect(up).toBeDisabled();
  expect(down).toBeDisabled();
  fireEvent.click(up);
  expect(birthYear.value).toBe("");

  fireEvent.change(birthYear, { target: { value: "2011" } });
  fireEvent.click(up);
  expect(birthYear.value).toBe("2012");
  fireEvent.click(down);
  expect(birthYear.value).toBe("2011");

  // Clamps at the lower bound rather than stepping below what the server accepts.
  fireEvent.change(birthYear, { target: { value: "1900" } });
  fireEvent.click(down);
  expect(birthYear.value).toBe("1900");

  // Clamps at the current year rather than stepping into the future.
  const currentYear = new Date().getFullYear();
  fireEvent.change(birthYear, { target: { value: String(currentYear) } });
  fireEvent.click(up);
  expect(birthYear.value).toBe(String(currentYear));
});

it("flags an impossible year as it is typed, not only on submit", async () => {
  render(<LinkedLearnersPage />);
  await screen.findByText("No invitations or connections yet.");
  fireEvent.click(screen.getByRole("button", { name: /they will support me/i }));

  const birthYear = screen.getByLabelText(/Your birth year/i);
  fireEvent.change(birthYear, { target: { value: "12" } });
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();

  fireEvent.change(birthYear, { target: { value: "1234" } });
  expect(await screen.findByRole("alert")).toHaveTextContent(/Enter a year between 1900 and/);

  fireEvent.change(birthYear, { target: { value: "2011" } });
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});
