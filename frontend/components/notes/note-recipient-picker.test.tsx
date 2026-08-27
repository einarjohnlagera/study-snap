import { fireEvent, render, screen } from "@testing-library/react";
import { NoteRecipientPicker } from "./note-recipient-picker";
import { getLinkedLearners } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  getLinkedLearners: jest.fn(),
}));

const connection = (id: string, status: "PENDING" | "ACCEPTED" | "REVOKED", name: string) => ({
  id,
  callerRole: "SUPPORTER" as const,
  initiatedBy: "SUPPORTER" as const,
  incomingInvitation: false,
  counterpartyDisplayName: name,
  counterpartyEmail: `${name.toLowerCase().replace(" ", ".")}@example.com`,
  status,
  createdAt: "2026-08-27T00:00:00Z",
  acceptedAt: status === "ACCEPTED" ? "2026-08-27T00:00:00Z" : null,
  revokedAt: status === "REVOKED" ? "2026-08-27T00:00:00Z" : null,
  birthYearRequired: false,
  guardianConsentRequired: false,
  guardianConsentRecorded: false,
});

describe("NoteRecipientPicker", () => {
  beforeEach(() => {
    (getLinkedLearners as jest.Mock).mockReset();
  });

  it("shows accepted connections only and never selects them by default", async () => {
    (getLinkedLearners as jest.Mock).mockResolvedValue([
      connection("accepted", "ACCEPTED", "Maria Santos"),
      connection("pending", "PENDING", "Pending Person"),
    ]);

    render(<NoteRecipientPicker selectedRelationshipIds={[]} onChange={jest.fn()} />);

    expect(await screen.findByText("Maria Santos")).toBeInTheDocument();
    expect(screen.queryByText("Pending Person")).not.toBeInTheDocument();
    expect(screen.getByRole("checkbox")).not.toBeChecked();
    expect(screen.queryByText(/select all/i)).not.toBeInTheDocument();
  });

  it("drops stale non-accepted relationship ids from the next desired state", async () => {
    (getLinkedLearners as jest.Mock).mockResolvedValue([
      connection("accepted", "ACCEPTED", "Maria Santos"),
    ]);
    const onChange = jest.fn();

    render(
      <NoteRecipientPicker
        selectedRelationshipIds={["stale-revoked"]}
        onChange={onChange}
      />,
    );
    fireEvent.click(await screen.findByRole("checkbox"));

    expect(onChange).toHaveBeenCalledWith(["accepted"]);
  });
});
