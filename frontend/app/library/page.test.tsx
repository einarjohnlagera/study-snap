import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LibraryPage from "./page";
import {
  getQuickReviewPerformanceSummary,
  listNotes,
  listSubjects,
} from "@/lib/api";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  listNotes: jest.fn(),
  listSubjects: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
}));

describe("Library page", () => {
  beforeEach(() => {
    pushMock.mockReset();
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry"]);
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

  it("shows limited popular tags and applies hidden tags from the selector sheet", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    expect(screen.getByRole("button", { name: "cells" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "review" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "mitochondria" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "+ More" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "+ More" }));

    expect(screen.getByRole("heading", { name: "Select tags" })).toBeInTheDocument();
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
    fireEvent.click(screen.getByRole("button", { name: "+ More" }));
    fireEvent.click(screen.getAllByRole("button", { name: "review" }).at(-1) as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    expect(screen.getByText("No study packs found")).toBeInTheDocument();
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

    expect(await screen.findByText("You don't have any notes yet.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Your First Note" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Try Demo" })).toBeInTheDocument();
  });
});
