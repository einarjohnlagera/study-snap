import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LinkedLearnersPage from "./page";
import {
  acceptLinkedLearner,
  getLinkedLearners,
  inviteLinkedLearner,
  recordLinkedLearnerGuardianConsent,
  revokeLinkedLearner,
  type LinkedLearnerResponse,
} from "@/lib/api";

jest.mock("@/lib/api", () => ({
  acceptLinkedLearner: jest.fn(),
  getLinkedLearners: jest.fn(),
  inviteLinkedLearner: jest.fn(),
  recordLinkedLearnerBirthYear: jest.fn(),
  recordLinkedLearnerGuardianConsent: jest.fn(),
  revokeLinkedLearner: jest.fn(),
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

  await waitFor(() => expect(inviteLinkedLearner).toHaveBeenCalledWith("mentor@example.com", "LEARNER"));

  fireEvent.click(screen.getByRole("button", { name: /i will support them/i }));
  fireEvent.change(screen.getByLabelText("Their email"), { target: { value: "learner@example.com" } });
  fireEvent.click(screen.getByRole("button", { name: "Send invitation" }));

  await waitFor(() => expect(inviteLinkedLearner).toHaveBeenCalledWith("learner@example.com", "SUPPORTER"));
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
