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
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "ATP production in mitochondria...",
        summaryPreview: "Mitochondria convert glucose into usable ATP energy.",
        visibility: "PRIVATE",
        studyPackId: null,
        studyPackStatus: "DRAFT",
        quizCount: null,
        updatedAt: "2026-03-21T10:00:00Z",
      },
      {
        id: "note-99",
        title: "Zygote Review",
        subject: "Chemistry",
        tags: ["review"],
        contentPreview: "Generated chemistry review preview...",
        summaryPreview: "Generated chemistry summary preview.",
        visibility: "PUBLIC",
        studyPackId: "pack-99",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        updatedAt: "2026-03-22T10:00:00Z",
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

  it("filters notes from the shared filter sheet", async () => {
    render(<LibraryPage />);

    expect(await screen.findByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    fireEvent.click(screen.getByLabelText("Study Pack Ready"));

    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
  });

  it("sorts notes from the shared sort sheet", async () => {
    const { container } = render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Title (Z-A)" }));

    const cardTitles = Array.from(container.querySelectorAll("h3")).map((element) => element.textContent);
    expect(cardTitles.slice(0, 2)).toEqual(["Zygote Review", "Cell Respiration"]);
  });

  it("shows create-note and demo actions when the library is empty", async () => {
    (listNotes as jest.Mock).mockResolvedValueOnce([]);

    render(<LibraryPage />);

    expect(await screen.findByText("You don't have any notes yet.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Your First Note" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Try Demo" })).toBeInTheDocument();
  });
});
