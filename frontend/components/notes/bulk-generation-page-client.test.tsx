import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { BulkGenerationPageClient } from "./bulk-generation-page-client";
import { bulkGenerateNotes } from "@/lib/api";

const replaceMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAdminUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error {
    status: number;

    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  bulkGenerateNotes: jest.fn(),
}));

const samplePaste = `Subject: Maternal Health
Prenatal Care
Stages of Labor

Subject: Community Health
Epidemiology Basics`;

function fillValidForm() {
  fireEvent.change(screen.getByLabelText("Course / Program"), { target: { value: "Nursing" } });
  fireEvent.change(screen.getByLabelText("Subject-grouped titles"), { target: { value: samplePaste } });
  fireEvent.click(screen.getByRole("checkbox", { name: /Public/ }));
}

describe("BulkGenerationPageClient", () => {
  beforeEach(() => {
    replaceMock.mockReset();
    (bulkGenerateNotes as jest.Mock).mockReset();
  });

  it("preserves form state when submission fails", async () => {
    (bulkGenerateNotes as jest.Mock).mockRejectedValueOnce(new Error("Connection lost."));
    render(<BulkGenerationPageClient />);
    fillValidForm();

    fireEvent.click(screen.getByRole("button", { name: "Queue 3 notes" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Connection lost.");
    expect(screen.getByLabelText("Course / Program")).toHaveValue("Nursing");
    expect(screen.getByLabelText("Subject-grouped titles")).toHaveValue(samplePaste);
    expect(screen.getByRole("checkbox", { name: /Public/ })).toBeChecked();
  });

  it("submits parsed groups and shows the queue acknowledgment", async () => {
    (bulkGenerateNotes as jest.Mock).mockResolvedValueOnce({
      acceptedTitles: 3,
      queuedTitles: 3,
      subjectCount: 2,
      rejectedTitles: 0,
    });
    render(<BulkGenerationPageClient />);
    fillValidForm();

    fireEvent.click(screen.getByRole("button", { name: "Queue 3 notes" }));

    await waitFor(() => {
      expect(bulkGenerateNotes).toHaveBeenCalledWith({
        courseProgram: "Nursing",
        targetAudience: "BOARD_EXAM_REVIEW",
        makePublic: true,
        groups: [
          { subject: "Maternal Health", titles: ["Prenatal Care", "Stages of Labor"] },
          { subject: "Community Health", titles: ["Epidemiology Basics"] },
        ],
      });
    });
    expect(await screen.findByRole("heading", { name: "Bulk generation queued" })).toBeInTheDocument();
    expect(screen.getByText(/Queued 3 notes across 2 subjects/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to Library" })).toHaveAttribute("href", "/library");
  });

  it("blocks empty and over-cap submissions inline", () => {
    render(<BulkGenerationPageClient />);

    fireEvent.click(screen.getByRole("button", { name: "Queue 0 notes" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Add at least one title");

    const titles = Array.from({ length: 51 }, (_, index) => `Title ${index + 1}`).join("\n");
    fireEvent.change(screen.getByLabelText("Course / Program"), { target: { value: "Nursing" } });
    fireEvent.change(screen.getByLabelText("Subject-grouped titles"), {
      target: { value: `Subject: Nursing\n${titles}` },
    });
    fireEvent.click(screen.getByRole("button", { name: "Queue 51 notes" }));

    expect(screen.getAllByText("You can bulk generate up to 50 titles at once.").length).toBeGreaterThan(0);
    expect(bulkGenerateNotes).not.toHaveBeenCalled();
  });
});
