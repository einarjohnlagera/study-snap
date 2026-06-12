import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LibraryPage from "./page";
import { reorderSelectedNoteIdsByDrag } from "./exam-builder-order";
import {
  addCollectionItems,
  createCollection,
  createSavedLibraryFilter,
  deleteSavedLibraryFilter,
  getSavedLibraryFilters,
  listCollections,
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
  addCollectionItems: jest.fn(),
  createCollection: jest.fn(),
  createSavedLibraryFilter: jest.fn(),
  deleteSavedLibraryFilter: jest.fn(),
  exportCombinedGeneratedQuizDocx: jest.fn(),
  getSavedLibraryFilters: jest.fn(),
  listCollections: jest.fn(),
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

function applyTopModal() {
  const applyButtons = screen.getAllByRole("button", { name: "Apply" });
  fireEvent.click(applyButtons[applyButtons.length - 1]);
}

// Minimal note builder for driving the (client-computed) subject stats strip.
function buildNote(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    title: `Note ${id}`,
    courseProgram: null,
    subject: null,
    tags: [],
    contentPreview: "",
    summaryPreview: "",
    visibility: "PRIVATE",
    studyPackId: null,
    studyPackStatus: "DRAFT",
    quizCount: null,
    generatedQuizId: null,
    generatedQuizQuestionCount: null,
    createdAt: "2026-03-20T10:00:00Z",
    updatedAt: "2026-03-21T10:00:00Z",
    ...overrides,
  };
}

function notesAcrossSubjects(subjectsByCount: Array<[string, number]>) {
  const notes: ReturnType<typeof buildNote>[] = [];
  let counter = 0;
  for (const [subject, count] of subjectsByCount) {
    for (let i = 0; i < count; i += 1) {
      counter += 1;
      notes.push(buildNote(`note-${counter}`, { subject }));
    }
  }
  return notes;
}

describe("Library page", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    currentSearch = "";
    (addCollectionItems as jest.Mock).mockReset();
    (createCollection as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (addCollectionItems as jest.Mock).mockResolvedValue({
      id: "collection-1",
      title: "Midterm Plan",
      description: null,
      createdAt: "2026-03-24T00:00:00Z",
      updatedAt: "2026-03-24T00:00:00Z",
      items: [],
    });
    (createCollection as jest.Mock).mockResolvedValue({
      id: "created-collection",
      title: "New Plan",
      description: null,
      createdAt: "2026-03-24T00:00:00Z",
      updatedAt: "2026-03-24T00:00:00Z",
      items: [],
    });
    (getAuthUser as jest.Mock).mockReturnValue(null);
    (getSavedLibraryFilters as jest.Mock).mockResolvedValue([]);
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "collection-1",
        title: "Midterm Plan",
        description: null,
        itemCount: 2,
        createdAt: "2026-03-24T00:00:00Z",
        updatedAt: "2026-03-24T00:00:00Z",
      },
    ]);
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
    expect(await screen.findByRole("link", { name: "Import files" })).toHaveAttribute("href", "/notes/import");
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
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 3], ["Chemistry", 2]]),
    );

    render(<LibraryPage />);

    expect(await screen.findByRole("button", { name: "Biology 3" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Chemistry 2" })).toBeInTheDocument();
  });

  it("does not render subject stats below the total note threshold", async () => {
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 2], ["Chemistry", 2]]),
    );

    render(<LibraryPage />);

    await screen.findByText("Note note-1");
    expect(screen.queryByRole("button", { name: "Biology 2" })).not.toBeInTheDocument();
  });

  it("does not render subject stats when only one subject exists", async () => {
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 5]]),
    );

    render(<LibraryPage />);

    await screen.findByText("Note note-1");
    expect(screen.queryByRole("button", { name: "Biology 5" })).not.toBeInTheDocument();
  });

  it("shows an Other chip for subjects beyond the top subjects", async () => {
    // 8 distinct subjects (1 note each) → top 6 chips + Other 2
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([
        ["Anatomy", 1], ["Biology", 1], ["Chemistry", 1], ["Dosage", 1],
        ["Ethics", 1], ["Foundations", 1], ["Genetics", 1], ["History", 1],
      ]),
    );

    render(<LibraryPage />);

    expect(await screen.findByText("Other 2")).toBeInTheDocument();
  });

  it("recomputes subject facets within an active course/program filter", async () => {
    // Faceting: with a course/program filter active, only that program's subjects show.
    (listNotes as jest.Mock).mockResolvedValue([
      ...notesAcrossSubjects([["Pharmacology", 3], ["Community Health Nursing", 2]]).map((n) => ({
        ...n,
        courseProgram: "Nursing",
      })),
      ...notesAcrossSubjects([["Architectural Design", 4]]).map((n) => ({
        ...n,
        courseProgram: "Architecture",
      })),
    ]);
    currentSearch = "cp=Nursing";

    render(<LibraryPage />);

    expect(await screen.findByRole("button", { name: "Pharmacology 3" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Community Health Nursing 2" })).toBeInTheDocument();
    // Architecture subject must NOT appear while filtered to the Nursing program
    expect(screen.queryByRole("button", { name: "Architectural Design 4" })).not.toBeInTheDocument();
  });

  it("applies the subject URL filter when a subject stats chip is clicked", async () => {
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 3], ["Chemistry", 2]]),
    );

    render(<LibraryPage />);

    const biologyChip = await screen.findByRole("button", { name: "Biology 3" });
    replaceMock.mockClear();
    fireEvent.click(biologyChip);

    expect(replaceMock).toHaveBeenCalledWith("/library?subject=Biology", { scroll: false });
  });

  it("hides subject stats when a subject filter is already active", async () => {
    currentSearch = "subject=Biology";
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 3], ["Chemistry", 2]]),
    );

    render(<LibraryPage />);

    await screen.findByText("Note note-1");
    expect(screen.queryByRole("button", { name: "Biology 3" })).not.toBeInTheDocument();
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

  it("renders Select for student profiles and allows selecting draft notes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Select" }));
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));

    expect(screen.getByText("1 note selected")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add to Study Plan" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Build exam" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create Exam" })).not.toBeInTheDocument();
    expect(screen.queryByText("Generate a quiz for this note before adding it to an exam.")).not.toBeInTheDocument();
  });

  it("adds selected notes to an existing collection and clears selection", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Select" }));
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));
    fireEvent.click(screen.getByRole("button", { name: "Add to Study Plan" }));

    expect(await screen.findByRole("heading", { name: "Add to a Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add here" }));

    await waitFor(() => {
      expect(addCollectionItems).toHaveBeenCalledWith("collection-1", ["note-42"]);
    });
    expect(screen.queryByText("1 note selected")).not.toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "View Study Plan" })).toHaveAttribute("href", "/collections/collection-1");
  });

  it("creates a new collection from selected notes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    (listCollections as jest.Mock).mockResolvedValue([]);

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Select" }));
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));
    fireEvent.click(screen.getByLabelText("Select Zygote Review"));
    fireEvent.click(screen.getByRole("button", { name: "Add to Study Plan" }));

    expect(await screen.findByText("Create your first study plan and add these notes to it.")).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText("Study Plan title"), {
      target: { value: "Finals Plan" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create new Study Plan" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({
        title: "Finals Plan",
        noteIds: ["note-42", "note-99"],
      });
    });
    expect(screen.queryByText("2 notes selected")).not.toBeInTheDocument();
  });

  it("shows retry instead of an empty state when collection loading fails", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    (listCollections as jest.Mock).mockRejectedValueOnce(new Error("Network down"));

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Select" }));
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));
    fireEvent.click(screen.getByRole("button", { name: "Add to Study Plan" }));

    expect(await screen.findByText("Network down")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(screen.queryByText("Create your first study plan and add these notes to it.")).not.toBeInTheDocument();
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
    applyTopModal();

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
    applyTopModal();

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
    applyTopModal();

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
    applyTopModal();

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
    applyTopModal();

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
    applyTopModal();

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
    applyTopModal();

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

  it("shows teacher Build exam with mixed-readiness selected notes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Select" }));
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));
    fireEvent.click(screen.getByLabelText("Select Zygote Review"));

    expect(screen.getByText("2 notes selected")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add to Lesson Plan" })).toBeInTheDocument();
    expect(screen.getByText("Only quiz-ready notes will be added to the exam.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Build exam" }));

    expect(pushMock).toHaveBeenCalledWith("/library/exam-builder?notes=note-42%2Cnote-99");
  });

  it("disables teacher Build exam when no selected notes are quiz-ready", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Select" }));
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));

    expect(screen.getByText("Generate a quiz for at least one note to build an exam.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Build exam" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Create Exam" })).not.toBeInTheDocument();
  });
});
