import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LibraryPage from "./page";
import { listNotes } from "@/lib/api";

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
  getQuickReviewPerformanceSummary: jest.fn(),
  copyNote: jest.fn(),
  deleteNote: jest.fn(),
}));

describe("My Library page", () => {
  beforeEach(() => {
    pushMock.mockReset();
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
  });

  it("opens note detail when a card is clicked", async () => {
    render(<LibraryPage />);

    const title = await screen.findByText("Cell Respiration");
    const card = title.closest("[role='link']");
    expect(card).not.toBeNull();

    fireEvent.click(card as HTMLElement);

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/notes/note-42?from=library");
    });
  });
});
