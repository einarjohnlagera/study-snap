import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LibraryPage from "./page";
import { deleteNote, listNotes, listSubjects } from "@/lib/api";

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
  copyNote: jest.fn(),
  deleteNote: jest.fn(),
}));

describe("My Library page", () => {
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
        visibility: "PRIVATE",
        studyPackId: null,
        studyPackStatus: "DRAFT",
        quizCount: null,
        updatedAt: "2026-03-21T10:00:00Z",
      },
    ]);
    (deleteNote as jest.Mock).mockReset();
    (deleteNote as jest.Mock).mockResolvedValue(undefined);
  });

  it("opens note detail when a card is clicked", async () => {
    render(<LibraryPage />);

    expect(await screen.findByRole("button", { name: "+ Create Note" })).toBeInTheDocument();
    expect(listSubjects).toHaveBeenCalledWith("mine");
    const title = await screen.findByText("Cell Respiration");
    const card = title.closest("[role='link']");
    expect(card).not.toBeNull();

    fireEvent.click(card as HTMLElement);

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/notes/note-42?from=library");
    });
  });

  it("uses the shared delete modal from card actions", async () => {
    render(<LibraryPage />);

    const menuButton = await screen.findByRole("button", { name: "Open note actions" });
    fireEvent.click(menuButton);
    fireEvent.click(screen.getByRole("button", { name: "Delete" }));

    expect(screen.getByText("Delete this note?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Delete note" }));

    await waitFor(() => {
      expect(deleteNote).toHaveBeenCalledWith("note-42");
    });
  });

  it("shows create-note and demo actions when the library is empty", async () => {
    (listNotes as jest.Mock).mockResolvedValueOnce([]);

    render(<LibraryPage />);

    expect(await screen.findByText("You don't have any notes yet.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create Your First Note" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try Demo" })).toBeInTheDocument();
  });
});
