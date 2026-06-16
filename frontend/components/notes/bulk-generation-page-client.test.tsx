import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { BulkGenerationPageClient } from "./bulk-generation-page-client";
import { bulkGenerateNotes, getMe, listCoursePrograms, listSubjects } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { consumeBulkQueuedFlash } from "@/lib/bulk-generation-flash";

const replaceMock = jest.fn();
const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock, push: pushMock }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAdminUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
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

async function fillAdminForm(topicValues: string[]) {
  fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Maternal Health" } });
  fireEvent.change(screen.getByLabelText(/^Course \/ Program/), { target: { value: "Nursing" } });
  // Target Audience defaults to "Student" for admin.
  topicValues.forEach((value, index) => {
    if (index > 0) {
      fireEvent.click(screen.getByRole("button", { name: "+ Add topic" }));
    }
    fireEvent.change(screen.getByLabelText(new RegExp(`^Topic ${index + 1}$`)), { target: { value } });
  });
  fireEvent.click(screen.getByRole("switch", { name: /public/i }));
}

describe("BulkGenerationPageClient", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    replaceMock.mockReset();
    pushMock.mockReset();
    globalThis.sessionStorage?.clear();
    (bulkGenerateNotes as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockResolvedValue([]);
    (listCoursePrograms as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "" });
    (getAuthUser as jest.Mock).mockReturnValue({ id: "admin-1", role: "ADMIN", profileType: null });
  });

  it("shows the admin metadata fields without learner level", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    expect(screen.getByLabelText(/^Subject/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Course \/ Program/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Target Audience/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Learner Level/)).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /public/i })).toBeInTheDocument();
    expect(screen.getByTestId("bulk-metadata-grid")).toHaveClass("sm:grid-cols-2");
  });

  it("keeps the compact grid profile-aware for teacher and non-teacher views", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "teacher-1", role: "USER", profileType: "TEACHER" });
    const { unmount } = render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    expect(screen.getByLabelText(/^Course \/ Program/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Target Audience/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Learner Level/)).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /public/i })).toBeInTheDocument();

    unmount();
    (getAuthUser as jest.Mock).mockReturnValue({ id: "student-1", role: "USER", profileType: "STUDENT" });
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalledTimes(2));

    expect(screen.getByLabelText(/^Subject/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Course \/ Program/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Target Audience/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Learner Level/)).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /public/i })).toBeInTheDocument();
  });

  it("submits the resolved payload, flashes the queued count, and redirects to Library", async () => {
    (bulkGenerateNotes as jest.Mock).mockResolvedValueOnce({
      acceptedTopics: 2,
      queuedTopics: 2,
      rejectedTopics: 0,
    });
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    await fillAdminForm(["Prenatal Care", "Stages of Labor"]);

    fireEvent.click(screen.getByRole("button", { name: "Queue 2 notes" }));

    await waitFor(() => {
      expect(bulkGenerateNotes).toHaveBeenCalledWith({
        subject: "Maternal Health",
        topics: ["Prenatal Care", "Stages of Labor"],
        makePublic: true,
        courseProgram: "Nursing",
        targetProfileType: "STUDENT",
      });
    });
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/library"));
    expect(consumeBulkQueuedFlash()).toBe(2);
  });

  it("splits a multi-line paste into separate topic rows and strips list markers", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    fireEvent.paste(screen.getByLabelText(/^Topic 1$/), {
      clipboardData: {
        getData: () => "* Adjusting Entries and the Accrual Basis of Accounting\n* Cash Management and Cash Equivalents in Financial Reporting",
      },
    });

    await waitFor(() => {
      expect(screen.getByLabelText(/^Topic 1$/)).toHaveValue(
        "Adjusting Entries and the Accrual Basis of Accounting",
      );
    });
    expect(screen.getByLabelText(/^Topic 2$/)).toHaveValue(
      "Cash Management and Cash Equivalents in Financial Reporting",
    );
  });

  it("preserves form state when submission fails", async () => {
    (bulkGenerateNotes as jest.Mock).mockRejectedValueOnce(new Error("Connection lost."));
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    await fillAdminForm(["Prenatal Care"]);

    fireEvent.click(screen.getByRole("button", { name: "Queue 1 note" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Connection lost.");
    expect(screen.getByLabelText(/^Subject/)).toHaveValue("Maternal Health");
    expect(screen.getByLabelText(/^Topic 1$/)).toHaveValue("Prenatal Care");
  });

  it("adds and removes topic rows", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());

    fireEvent.click(screen.getByRole("button", { name: "+ Add topic" }));

    expect(screen.getByLabelText(/^Topic 1$/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Topic 2$/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Remove topic 2" }));

    expect(screen.queryByLabelText(/^Topic 2$/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove topic 1" })).toBeDisabled();
  });

  it("blocks submission with no topics", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Maternal Health" } });
    fireEvent.change(screen.getByLabelText(/^Course \/ Program/), { target: { value: "Nursing" } });

    fireEvent.click(screen.getByRole("button", { name: "Queue 0 notes" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Add at least one topic");
    expect(bulkGenerateNotes).not.toHaveBeenCalled();
  });

  it("blocks submission over the topic cap", async () => {
    render(<BulkGenerationPageClient />);
    await waitFor(() => expect(getMe).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText(/^Subject/), { target: { value: "Maternal Health" } });
    fireEvent.change(screen.getByLabelText(/^Course \/ Program/), { target: { value: "Nursing" } });
    fireEvent.change(screen.getByLabelText(/^Topic 1$/), { target: { value: "Topic 1" } });

    for (let index = 2; index <= 51; index += 1) {
      fireEvent.click(screen.getByRole("button", { name: "+ Add topic" }));
      fireEvent.change(screen.getByLabelText(new RegExp(`^Topic ${index}$`)), {
        target: { value: `Topic ${index}` },
      });
    }
    fireEvent.click(screen.getByRole("button", { name: "Queue 51 notes" }));

    expect(screen.getByRole("alert")).toHaveTextContent("up to 50 topics at once");
    expect(bulkGenerateNotes).not.toHaveBeenCalled();
  });
});
