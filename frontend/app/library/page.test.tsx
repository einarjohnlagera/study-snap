import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LibraryPage from "./page";
import { reorderSelectedNoteIdsByDrag } from "./exam-builder-order";
import {
  getQuickReviewPerformanceSummary,
  listNotes,
  listSubjects,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const pushMock = jest.fn();
const routerMock = {
  push: pushMock,
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  exportCombinedGeneratedQuizDocx: jest.fn(),
  listNotes: jest.fn(),
  listSubjects: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

describe("Library page", () => {
  beforeEach(() => {
    pushMock.mockReset();
    (getAuthUser as jest.Mock).mockReturnValue(null);
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry", "Pharmacy"]);
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-42",
        title: "Cell Respiration",
        courseProgram: "Nursing",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells", "energy", "mitochondria"],
        contentPreview: "ATP production in mitochondria...",
        summaryPreview: "Mitochondria convert glucose into usable ATP energy.",
        visibility: "PRIVATE",
        studyPackId: null,
        studyPackStatus: "DRAFT",
        quizCount: null,
        generatedQuizId: null,
        generatedQuizQuestionCount: null,
        createdAt: "2026-03-20T10:00:00Z",
        updatedAt: "2026-03-21T10:00:00Z",
      },
      {
        id: "note-99",
        title: "Zygote Review",
        courseProgram: "Chemistry",
        learnerLevel: "COLLEGE",
        subject: "Chemistry",
        tags: ["review", "exam", "cells"],
        contentPreview: "Generated chemistry review preview...",
        summaryPreview: "Generated chemistry summary preview.",
        visibility: "PUBLIC",
        studyPackId: "pack-99",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        generatedQuizId: "generated-99",
        generatedQuizQuestionCount: 10,
        createdAt: "2026-03-21T10:00:00Z",
        updatedAt: "2026-03-22T10:00:00Z",
      },
      {
        id: "note-77",
        title: "Dosage Calculations",
        courseProgram: "Pharmacy",
        learnerLevel: "COLLEGE",
        subject: null,
        tags: ["math", "medication", "review"],
        contentPreview: "Medication dosage formulas and unit conversions...",
        summaryPreview: "Practice dosage conversion steps for common prescriptions.",
        visibility: "PRIVATE",
        studyPackId: "pack-77",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 4,
        generatedQuizId: "generated-77",
        generatedQuizQuestionCount: 10,
        createdAt: "2026-03-18T10:00:00Z",
        updatedAt: "2026-03-23T10:00:00Z",
      },
    ]);
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockResolvedValue({
      lastReviewedAt: null,
    });
  });

  it("opens note detail when a card is clicked and does not render card action menus", async () => {
    render(<LibraryPage />);

    expect(await screen.findByRole("heading", { name: "Library" })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Create Note" })).toBeInTheDocument();
    expect(screen.getByText("Nursing")).toBeInTheDocument();
    expect(listSubjects).toHaveBeenCalledWith("mine");
    expect(screen.queryByRole("button", { name: "Open note actions" })).not.toBeInTheDocument();

    const title = await screen.findByText("Cell Respiration");
    const card = title.closest("[role='link']");
    expect(card).not.toBeNull();

    fireEvent.click(card as HTMLElement);

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/notes/note-42?from=library");
    });
  });

  it("reorders selected notes by drag target order", () => {
    expect(reorderSelectedNoteIdsByDrag(
      ["note-99", "note-77", "note-42"],
      "note-77",
      "note-99",
    )).toEqual(["note-77", "note-99", "note-42"]);
  });

  it("hides teacher exam actions for student profiles", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    expect(screen.queryByRole("button", { name: "Select" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create Exam" })).not.toBeInTheDocument();
  });

  it("hides Quiz Ready filter and badges for student profiles", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    expect(screen.queryByRole("button", { name: "Quiz Ready" })).not.toBeInTheDocument();
    expect(screen.queryByText("Quiz Ready")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Study Pack Ready" })).toBeInTheDocument();
  });

  it("hides Quiz Ready filter and badges for board taker profiles", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "board-1",
      role: "USER",
      profileType: "BOARD_EXAM",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    expect(screen.queryByRole("button", { name: "Quiz Ready" })).not.toBeInTheDocument();
    expect(screen.queryByText("Quiz Ready")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Study Pack Ready" })).toBeInTheDocument();
  });

  it("filters notes by horizontal subject chips", async () => {
    render(<LibraryPage />);

    expect(await screen.findByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Biology" }));

    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
  });

  it("filters notes by readiness chips", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Quiz Ready" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Study Pack Ready" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
  });

  it("shows Quiz Ready filter and badges for teacher profiles", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    expect(screen.getByRole("button", { name: "Quiz Ready" })).toBeInTheDocument();
    expect(screen.getAllByText("Quiz Ready").length).toBeGreaterThanOrEqual(2);
  });

  it("clears hidden Quiz Ready filter after switching away from teacher profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    const { rerender } = render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Quiz Ready" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();

    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    rerender(<LibraryPage />);

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "Quiz Ready" })).not.toBeInTheDocument();
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    });
  });

  it("filters subjects from the searchable subject selector", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getAllByRole("button", { name: "+ More" })[0]);
    expect(screen.getByRole("heading", { name: "Select subject" })).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("Search subjects..."), {
      target: { value: "pha" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "Pharmacy" }).at(-1) as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
  });

  it("searches library notes by title and tags in real time", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "review" },
    });

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
  });

  it("filters notes by a single selected tag", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getAllByRole("button", { name: "+ More" })[1]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "energy" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "energy" }).at(-1) as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
  });

  it("uses OR logic for multiple tags from the same note", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getAllByRole("button", { name: "+ More" })[1]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "math" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "math" }).at(-1) as HTMLElement);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "med" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "medication" }).at(-1) as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
  });

  it("uses OR logic for multiple tags from different notes", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getAllByRole("button", { name: "+ More" })[1]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "energy" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "energy" }).at(-1) as HTMLElement);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "exam" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "exam" }).at(-1) as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
  });

  it("combines subject and tag filters while keeping subject restrictive", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Biology" }));
    fireEvent.click(screen.getAllByRole("button", { name: "+ More" })[1]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "cells" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "cells" }).at(-1) as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
  });

  it("combines search and tag filters while keeping search restrictive", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "dosage" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "+ More" })[1]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "review" }).at(-1) as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
  });

  it("searches tags inside the selector and supports quick deselect from selected tags", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getAllByRole("button", { name: "+ More" })[1]);
    expect(screen.getByRole("heading", { name: "Select tags" })).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "mito" },
    });
    fireEvent.click(screen.getByRole("button", { name: "mitochondria" }));
    expect(screen.getByText("Selected tags")).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "mitochondria" })[0]);
    expect(screen.queryByText("Selected tags")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "mitochondria" }));
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.getByRole("button", { name: "mitochondria" })).toBeInTheDocument();
    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
  });

  it("shows the empty filtered state and clears filters back to results", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Biology" }));
    fireEvent.click(screen.getAllByRole("button", { name: "+ More" })[1]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "review" }).at(-1) as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.getByText("No notes match these filters")).toBeInTheDocument();
    expect(screen.getByText("Try adjusting your filters")).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "Clear filters" })[0]);

    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
  });

  it("sorts notes from the shared sort sheet", async () => {
    const { container } = render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Title (Z-A)" }));

    const cardTitles = Array.from(container.querySelectorAll("h3")).map((element) => element.textContent);
    expect(cardTitles.slice(0, 3)).toEqual(["Zygote Review", "Dosage Calculations", "Cell Respiration"]);
  });

  it("shows the derived subject fallback when a note has no explicit subject", async () => {
    render(<LibraryPage />);

    await screen.findByText("Dosage Calculations");

    expect(screen.getByRole("button", { name: "Pharmacy" })).toBeInTheDocument();
    expect(screen.getAllByText("Pharmacy")).not.toHaveLength(0);
  });

  it("shows create-note and demo actions when the library is empty", async () => {
    (listNotes as jest.Mock).mockResolvedValueOnce([]);

    render(<LibraryPage />);

    expect(await screen.findByText("Your note library is empty")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Your First Note" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Try Demo" })).toBeInTheDocument();
  });

  it("enables teacher selection mode only for quiz-ready notes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Select" }));

    const disabledCheckbox = screen.getByLabelText("Select Cell Respiration for exam export");
    expect(disabledCheckbox).toBeDisabled();
    expect(screen.getByText("Generate a quiz first to include this note in an exam.")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Select Zygote Review for exam export"));

    expect(screen.getByText("1 note selected")).toBeInTheDocument();
  });

  it("routes teacher selections into the dedicated exam builder page", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Select" }));
    fireEvent.click(screen.getByLabelText("Select Zygote Review for exam export"));
    fireEvent.click(screen.getByLabelText("Select Dosage Calculations for exam export"));
    fireEvent.click(screen.getByRole("button", { name: "Create Exam" }));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/library/exam-builder?notes=note-99%2Cnote-77");
    });
  });
});
