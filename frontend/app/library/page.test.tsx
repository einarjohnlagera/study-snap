import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LibraryPage from "./page";
import { reorderSelectedNoteIdsByDrag } from "./exam-builder-order";
import {
  createSavedLibraryFilter,
  deleteSavedLibraryFilter,
  getNoteStats,
  getSavedLibraryFilters,
  listNotes,
  listSubjects,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};
let currentSearch = "";

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  useSearchParams: () => new URLSearchParams(currentSearch),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  createSavedLibraryFilter: jest.fn(),
  deleteSavedLibraryFilter: jest.fn(),
  exportCombinedGeneratedQuizDocx: jest.fn(),
  getNoteStats: jest.fn(),
  getSavedLibraryFilters: jest.fn(),
  listNotes: jest.fn(),
  listSubjects: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

async function openMoreFilters() {
  fireEvent.click(screen.getByRole("button", { name: "Open more filters" }));
  await screen.findByRole("heading", { name: "More Filters" });
}

function selectSubjectFilter(subject: string) {
  fireEvent.focus(screen.getAllByPlaceholderText("All")[0]);
  fireEvent.mouseDown(screen.getAllByRole("button", { name: subject })[0]);
}

async function openTagSelectorFromFilters() {
  fireEvent.click(screen.getByRole("button", { name: "Browse all" }));
  await screen.findByRole("heading", { name: "Select tags" });
}

describe("Library page", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    currentSearch = "";
    (getAuthUser as jest.Mock).mockReturnValue(null);
    (getNoteStats as jest.Mock).mockResolvedValue({
      topSubjects: [],
      otherSubjectsCount: 0,
      totalNotes: 3,
    });
    (getSavedLibraryFilters as jest.Mock).mockResolvedValue([]);
    (createSavedLibraryFilter as jest.Mock).mockResolvedValue({
      id: "saved-filter-1",
      name: "Review Notes",
      filterState: { search: "review" },
      createdAt: "2026-03-24T00:00:00Z",
    });
    (deleteSavedLibraryFilter as jest.Mock).mockResolvedValue(undefined);
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry", "Pharmacy"]);
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-42",
        title: "Cell Respiration",
        courseProgram: "Nursing",
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
  });

  it("opens note detail when a card is clicked and does not render card action menus", async () => {
    render(<LibraryPage />);

    expect(await screen.findByRole("heading", { name: "Library" })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Create Note" })).toBeInTheDocument();
    expect(screen.getByText("3 notes")).toBeInTheDocument();
    expect(screen.getByText("Nursing")).toBeInTheDocument();
    expect(listSubjects).toHaveBeenCalledWith("mine");
    expect(screen.queryByRole("button", { name: "Open note actions" })).not.toBeInTheDocument();

    const title = await screen.findByText("Cell Respiration");
    const card = title.closest("[role='link']");
    expect(card).not.toBeNull();

    fireEvent.click(card as HTMLElement);

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/notes/note-42?from=library&ref=%2Flibrary");
    });
  });

  it("renders subject stats when the library has enough notes across multiple subjects", async () => {
    (getNoteStats as jest.Mock).mockResolvedValue({
      topSubjects: [
        { subject: "Biology", count: 3 },
        { subject: "Chemistry", count: 2 },
      ],
      otherSubjectsCount: 0,
      totalNotes: 5,
    });

    render(<LibraryPage />);

    expect(await screen.findByRole("button", { name: "Biology 3" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Chemistry 2" })).toBeInTheDocument();
  });

  it("does not render subject stats below the total note threshold", async () => {
    (getNoteStats as jest.Mock).mockResolvedValue({
      topSubjects: [
        { subject: "Biology", count: 2 },
        { subject: "Chemistry", count: 2 },
      ],
      otherSubjectsCount: 0,
      totalNotes: 4,
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    expect(screen.queryByRole("button", { name: "Biology 2" })).not.toBeInTheDocument();
  });

  it("does not render subject stats when only one subject exists", async () => {
    (getNoteStats as jest.Mock).mockResolvedValue({
      topSubjects: [
        { subject: "Biology", count: 5 },
      ],
      otherSubjectsCount: 0,
      totalNotes: 5,
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    expect(screen.queryByRole("button", { name: "Biology 5" })).not.toBeInTheDocument();
  });

  it("shows an Other chip for subjects beyond the top subjects", async () => {
    (getNoteStats as jest.Mock).mockResolvedValue({
      topSubjects: [
        { subject: "Biology", count: 4 },
        { subject: "Chemistry", count: 3 },
      ],
      otherSubjectsCount: 2,
      totalNotes: 9,
    });

    render(<LibraryPage />);

    expect(await screen.findByText("Other 2")).toBeInTheDocument();
  });

  it("applies the subject URL filter when a subject stats chip is clicked", async () => {
    (getNoteStats as jest.Mock).mockResolvedValue({
      topSubjects: [
        { subject: "Biology", count: 3 },
        { subject: "Chemistry", count: 2 },
      ],
      otherSubjectsCount: 0,
      totalNotes: 5,
    });

    render(<LibraryPage />);

    const biologyChip = await screen.findByRole("button", { name: "Biology 3" });
    replaceMock.mockClear();
    fireEvent.click(biologyChip);

    expect(replaceMock).toHaveBeenCalledWith("/library?subject=Biology", { scroll: false });
  });

  it("hides subject stats when a subject filter is already active", async () => {
    currentSearch = "?subject=Biology";
    (getNoteStats as jest.Mock).mockResolvedValue({
      topSubjects: [
        { subject: "Biology", count: 3 },
        { subject: "Chemistry", count: 2 },
      ],
      otherSubjectsCount: 0,
      totalNotes: 5,
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    expect(screen.queryByRole("button", { name: "Biology 3" })).not.toBeInTheDocument();
  });

  it("suppresses subject stats when loading stats fails", async () => {
    const warnSpy = jest.spyOn(console, "warn").mockImplementation(() => undefined);
    (getNoteStats as jest.Mock).mockRejectedValue(new Error("stats failed"));

    try {
      render(<LibraryPage />);

      await screen.findByText("Cell Respiration");
      expect(screen.queryByRole("button", { name: "Biology 3" })).not.toBeInTheDocument();
      expect(warnSpy).toHaveBeenCalledWith("Could not load note stats.", expect.any(Error));
    } finally {
      warnSpy.mockRestore();
    }
  });

  it("uses the all-mode session timestamp from the note list for the reviewed label", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "challenge-only-note",
        title: "Challenge Only Review",
        courseProgram: "Nursing",
        subject: "Biology",
        tags: [],
        contentPreview: "Challenge content",
        summaryPreview: "Challenge summary",
        visibility: "PRIVATE",
        studyPackId: "pack-challenge",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 4,
        generatedQuizId: null,
        generatedQuizQuestionCount: null,
        createdAt: "2026-03-20T10:00:00Z",
        updatedAt: "2026-03-21T10:00:00Z",
        lastSessionCompletedAt: new Date().toISOString(),
      },
    ]);

    render(<LibraryPage />);

    await screen.findByText("Challenge Only Review");
    expect(screen.getByText("Last reviewed today")).toBeInTheDocument();
    expect(screen.queryByText("Not reviewed yet")).not.toBeInTheDocument();
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
    await openMoreFilters();

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
    await openMoreFilters();

    expect(screen.queryByRole("button", { name: "Quiz Ready" })).not.toBeInTheDocument();
    expect(screen.queryByText("Quiz Ready")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Study Pack Ready" })).toBeInTheDocument();
  });

  it("filters notes by subject from the more filters modal", async () => {
    render(<LibraryPage />);

    expect(await screen.findByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();

    await openMoreFilters();
    selectSubjectFilter("Biology");

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
    await openMoreFilters();

    expect(screen.getByRole("button", { name: "Draft" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Draft" }));

    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Quiz Ready" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.getByText("2 of 3 notes")).toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Study Pack Ready" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
  });

  it("shows draft empty state copy when no draft notes match", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-ready",
        title: "Ready Note",
        courseProgram: "Nursing",
        subject: "Biology",
        tags: ["review"],
        contentPreview: "Ready content",
        summaryPreview: "Ready summary",
        visibility: "PRIVATE",
        studyPackId: "pack-ready",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        generatedQuizId: null,
        generatedQuizQuestionCount: null,
        createdAt: "2026-03-20T10:00:00Z",
        updatedAt: "2026-03-21T10:00:00Z",
      },
    ]);

    render(<LibraryPage />);

    await screen.findByText("Ready Note");
    await openMoreFilters();
    fireEvent.click(screen.getByRole("button", { name: "Draft" }));

    expect(screen.queryByText("Ready Note")).not.toBeInTheDocument();
    expect(screen.getByText("No draft notes")).toBeInTheDocument();
    expect(screen.getByText("No draft notes — you've generated Study Packs for everything in your library.")).toBeInTheDocument();
  });

  it("shows Quiz Ready filter and badges for teacher profiles", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await openMoreFilters();

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
    await openMoreFilters();
    fireEvent.click(screen.getByRole("button", { name: "Quiz Ready" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();

    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    rerender(<LibraryPage />);

    await waitFor(() => {
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    });
    await openMoreFilters();
    expect(screen.queryByRole("button", { name: "Quiz Ready" })).not.toBeInTheDocument();
  });

  it("filters subjects from the searchable subject field", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await openMoreFilters();

    fireEvent.focus(screen.getAllByPlaceholderText("All")[0]);
    fireEvent.change(screen.getAllByPlaceholderText("Search subjects...")[0], {
      target: { value: "pha" },
    });
    fireEvent.mouseDown(screen.getAllByRole("button", { name: "Pharmacy" })[0]);

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

  it("shows the save filter action only when a filter is active", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "Loading filters..." })).not.toBeInTheDocument();
    });

    expect(screen.queryByRole("button", { name: "Save filter" })).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "review" },
    });

    expect(screen.getByRole("button", { name: "Save filter" })).toBeInTheDocument();
  });

  it("keeps the save filter dialog open and shows an inline error for a blank name", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save filter" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByText("Name is required")).toBeInTheDocument();
    expect(createSavedLibraryFilter).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "Save filter" })).toBeInTheDocument();
  });

  it("saves the active filter state and adds the saved filter to the picker", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save filter" }));
    fireEvent.change(screen.getByLabelText("Filter name"), {
      target: { value: "Review Notes" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(createSavedLibraryFilter).toHaveBeenCalledWith("Review Notes", { search: "review" });
    });
    expect(await screen.findByText("Filter saved")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Saved filters" }));
    expect(screen.getByRole("button", { name: "Review Notes" })).toBeInTheDocument();
  });

  it("applies a saved filter by replacing the current library URL params", async () => {
    (getSavedLibraryFilters as jest.Mock).mockResolvedValue([
      {
        id: "saved-filter-2",
        name: "Pharmacy Ready",
        filterState: {
          courseProgram: "Pharmacy",
          tags: ["math"],
          status: "STUDY_PACK_READY",
          sort: "TITLE_ASC",
        },
        createdAt: "2026-03-24T00:00:00Z",
      },
    ]);

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "cell" },
    });

    fireEvent.click(await screen.findByRole("button", { name: "Saved filters" }));
    fireEvent.click(screen.getByRole("button", { name: "Pharmacy Ready" }));

    expect(replaceMock).toHaveBeenCalledWith(
      "/library?cp=Pharmacy&tags=math&status=study_pack_ready&sort=title_asc",
      { scroll: false },
    );
    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
  });

  it("deletes a saved filter from the picker", async () => {
    (getSavedLibraryFilters as jest.Mock).mockResolvedValue([
      {
        id: "saved-filter-3",
        name: "Chemistry Review",
        filterState: { subject: "Chemistry" },
        createdAt: "2026-03-24T00:00:00Z",
      },
    ]);

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(await screen.findByRole("button", { name: "Saved filters" }));
    expect(screen.getByRole("button", { name: "Chemistry Review" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Delete saved filter Chemistry Review" }));

    await waitFor(() => {
      expect(deleteSavedLibraryFilter).toHaveBeenCalledWith("saved-filter-3");
    });
    expect(screen.queryByRole("button", { name: "Chemistry Review" })).not.toBeInTheDocument();
    expect(screen.getByText("Filter deleted")).toBeInTheDocument();
  });

  it("silently hides saved filters when loading them fails", async () => {
    (getSavedLibraryFilters as jest.Mock).mockRejectedValue(new Error("network"));

    render(<LibraryPage />);

    expect(await screen.findByText("Cell Respiration")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "Loading filters..." })).not.toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: "Saved filters" })).not.toBeInTheDocument();
  });

  it("filters notes by a single selected tag", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "energy" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "energy" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
  });

  it("uses OR logic for multiple tags from the same note", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "math" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "math" })[0]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "med" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "medication" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
  });

  it("uses OR logic for multiple tags from different notes", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "energy" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "energy" })[0]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "exam" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "exam" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
  });

  it("combines subject and tag filters while keeping subject restrictive", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    selectSubjectFilter("Biology");
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "cells" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "cells" })[0]);
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
    await openMoreFilters();
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "review" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
  });

  it("searches tags inside the selector and supports quick deselect from selected tags", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    await openTagSelectorFromFilters();

    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "mito" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "mitochondria" })[0]);
    expect(screen.getByText("Selected tags")).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "mitochondria" })[0]);
    expect(screen.queryByText("Selected tags")).not.toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "mitochondria" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.getAllByRole("button", { name: "mitochondria" })[0]).toBeInTheDocument();
    expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
    expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
  });

  it("shows the empty filtered state and clears filters back to results", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    selectSubjectFilter("Biology");
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "review" })[0]);
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

    await openMoreFilters();
    fireEvent.focus(screen.getAllByPlaceholderText("All")[0]);
    expect(screen.getAllByRole("button", { name: "Pharmacy" })[0]).toBeInTheDocument();
    expect(screen.getAllByText("Pharmacy")).not.toHaveLength(0);
  });

  it("shows create-note and demo actions when the library is empty", async () => {
    (listNotes as jest.Mock).mockResolvedValueOnce([]);

    render(<LibraryPage />);

    expect(await screen.findByText("Your note library is empty")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Your First Note" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Try Demo" })).toBeInTheDocument();
  });

  it("shows teacher-framed empty library copy without the demo action", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });
    (listNotes as jest.Mock).mockResolvedValueOnce([]);

    render(<LibraryPage />);

    expect(await screen.findByText("Your note library is empty")).toBeInTheDocument();
    expect(
      screen.getByText("Create a note, generate a quiz, then export it as DOCX for your class. Build multi-note exams with Exam Builder."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Your First Note" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Try Demo" })).not.toBeInTheDocument();
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
