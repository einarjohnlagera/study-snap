import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LinkedLearnersPage from "./page";
import {
  acceptLinkedLearner,
  acceptLinkedLearnerInvitation,
  listLinkedLearnerInvitations,
  correctLinkedLearnerBirthYear,
  getLinkedLearners,
  inviteLinkedLearner,
  recordLinkedLearnerGuardianConsent,
  previewLinkedLearnerBirthYearCorrection,
  revokeLinkedLearner,
  type LinkedLearnerResponse,
} from "@/lib/api";

jest.mock("@/lib/api", () => ({
  acceptLinkedLearner: jest.fn(),
  correctLinkedLearnerBirthYear: jest.fn(),
  getLinkedLearners: jest.fn(),
  inviteLinkedLearner: jest.fn(),
  recordLinkedLearnerBirthYear: jest.fn(),
  recordLinkedLearnerGuardianConsent: jest.fn(),
  previewLinkedLearnerBirthYearCorrection: jest.fn(),
  revokeLinkedLearner: jest.fn(),
  listLinkedLearnerInvitations: jest.fn(),
  acceptLinkedLearnerInvitation: jest.fn(),
  revokeLinkedLearnerInvitation: jest.fn(),
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
  birthYearRequired: false,
  guardianConsentRequired: false,
  guardianConsentRecorded: false,
};

beforeEach(() => {
  (listLinkedLearnerInvitations as jest.Mock).mockResolvedValue([]);
  jest.clearAllMocks();
  jest.mocked(getLinkedLearners).mockResolvedValue([]);
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

it("shows consent only when required and blocks recording until attested", async () => {
  const minorLink: LinkedLearnerResponse = {
    ...baseLink,
    callerRole: "SUPPORTER",
    initiatedBy: "LEARNER",
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

it("offers progress only to the supporter on an accepted connection", async () => {
  jest.mocked(getLinkedLearners).mockResolvedValue([
    { ...baseLink, id: "accepted-support", callerRole: "SUPPORTER", status: "ACCEPTED", incomingInvitation: false },
    { ...baseLink, id: "pending-support", callerRole: "SUPPORTER", status: "PENDING", incomingInvitation: false },
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
  expect(screen.getByText("Your progress access is paused because guardian consent is required. Record consent above to unblock the connection.")).toBeInTheDocument();
});

it("shows an incoming invitation and accepts it through the invitation endpoint", async () => {
  // ⚠️ Invitations are email-keyed and separate from connections: a row exists whether or not the
  // address had an account, which is what closed the account-existence oracle. Accepting one is the
  // ONLY path that creates a relationship.
  (getLinkedLearners as jest.Mock).mockResolvedValue([]);
  (listLinkedLearnerInvitations as jest.Mock).mockResolvedValue([{
    id: "inv-1", incoming: true, inviterRole: "SUPPORTER",
    invitedEmail: "me@example.com", inviterName: "Aunt May", createdAt: "2026-08-20T00:00:00Z",
  }]);
  (acceptLinkedLearnerInvitation as jest.Mock).mockResolvedValue({
    ...baseLink, status: "ACCEPTED", guardianConsentRequired: false, guardianConsentRecorded: false,
  });

  render(<LinkedLearnersPage />);

  expect(await screen.findByText(/Aunt May invited you/i)).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Accept" }));

  await waitFor(() => expect(acceptLinkedLearnerInvitation).toHaveBeenCalledWith("inv-1", null, false));
});

it("does not disclose a name on an outgoing invitation", async () => {
  // The inviter typed the address and must learn nothing further from it — echoing back a resolved
  // display name is what made the list a name-harvesting oracle.
  (getLinkedLearners as jest.Mock).mockResolvedValue([]);
  (listLinkedLearnerInvitations as jest.Mock).mockResolvedValue([{
    id: "inv-2", incoming: false, inviterRole: "SUPPORTER",
    invitedEmail: "someone@example.com", inviterName: null, createdAt: "2026-08-20T00:00:00Z",
  }]);

  render(<LinkedLearnersPage />);

  expect(await screen.findByText(/You invited someone@example.com/i)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Withdraw" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Accept" })).not.toBeInTheDocument();
});
