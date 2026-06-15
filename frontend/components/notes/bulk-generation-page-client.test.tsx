import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { BulkGenerationPageClient } from "./bulk-generation-page-client";
import { bulkGenerateNotes, getMe, listCoursePrograms, listSubjects } from "@/lib/api";

const replaceMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAdminUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ id: "admin-1", role: "ADMIN", profileType: null }),
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
  listSubjects: jest.fn(),
  listCoursePrograms: jest.fn(),
  getMe: jest.fn(),
}));

async function fillAdminForm(titleValues: string[]) {
  fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Maternal Health" } });
  fireEvent.change(screen.getByLabelText(/^Course \/ Program/), { target: { value: "Nursing" } });
  // Target Audience defaults to "Student" and Learner Level to "College" for admin.
  titleValues.forEach((value, index) => {
    if (index > 0) {
      fireEvent.click(screen.getByRole("button", { name: "+ Add title" }));
    }
    fireEvent.change(screen.getByLabelText(new RegExp(`^Title ${index + 1}$`)), { target: { value } });
  });
  fireEvent.click(screen.getByRole("switch", { name: /public/i }));
}

describe("BulkGenerationPageClient", () => {
  beforeEach(() => {
    replaceMock.mockReset();
    (bulkGenerateNotes as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockResolvedValue([]);
    (listCoursePrograms as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "", learnerLevel: "COLLEGE" });
  });

  it("shows the admin metadata fields", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    expect(screen.getByLabelText(/^Subject/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Course \/ Program/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Target Audience/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Learner Level/)).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /public/i })).toBeInTheDocument();
  });

  it("submits the resolved payload and shows the queue acknowledgment", async () => {
    (bulkGenerateNotes as jest.Mock).mockResolvedValueOnce({
      acceptedTitles: 2,
      queuedTitles: 2,
      subjectCount: 1,
      rejectedTitles: 0,
    });
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    await fillAdminForm(["Prenatal Care", "Stages of Labor"]);

    fireEvent.click(screen.getByRole("button", { name: "Queue 2 notes" }));

    await waitFor(() => {
      expect(bulkGenerateNotes).toHaveBeenCalledWith({
        subject: "Maternal Health",
        titles: ["Prenatal Care", "Stages of Labor"],
        makePublic: true,
        courseProgram: "Nursing",
        targetProfileType: "STUDENT",
        learnerLevel: "COLLEGE",
      });
    });
    expect(await screen.findByRole("heading", { name: "Bulk generation queued" })).toBeInTheDocument();
    expect(screen.getByText(/Queued 2 notes for Maternal Health/)).toBeInTheDocument();
  });

  it("preserves form state when submission fails", async () => {
    (bulkGenerateNotes as jest.Mock).mockRejectedValueOnce(new Error("Connection lost."));
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    await fillAdminForm(["Prenatal Care"]);

    fireEvent.click(screen.getByRole("button", { name: "Queue 1 note" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Connection lost.");
    expect(screen.getByLabelText(/^Subject/)).toHaveValue("Maternal Health");
    expect(screen.getByLabelText(/^Title 1$/)).toHaveValue("Prenatal Care");
  });

  it("blocks submission with no titles", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Maternal Health" } });
    fireEvent.change(screen.getByLabelText(/^Course \/ Program/), { target: { value: "Nursing" } });

    fireEvent.click(screen.getByRole("button", { name: "Queue 0 notes" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Add at least one title");
    expect(bulkGenerateNotes).not.toHaveBeenCalled();
  });
});
