import { render, screen, waitFor } from "@testing-library/react";
import { NoteSessionReviewPageClient } from "./note-session-review-page-client";
import {
  getNote,
  getChallengeQuizSessionReview,
  getQuickReviewSessionReview,
} from "@/lib/api";

const pushMock = jest.fn();
let currentSearch = "mode=quick-review&tab=quiz";

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
    replace: jest.fn(),
  }),
  useSearchParams: () => new URLSearchParams(currentSearch),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  getNote: jest.fn(),
  getChallengeQuizSessionReview: jest.fn(),
  getQuickReviewSessionReview: jest.fn(),
}));

describe("NoteSessionReviewPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    currentSearch = "mode=quick-review&tab=quiz";
    (getNote as jest.Mock).mockReset();
    (getChallengeQuizSessionReview as jest.Mock).mockReset();
    (getQuickReviewSessionReview as jest.Mock).mockReset();
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Respiratory Notes",
      subject: "Pulmonology",
      courseProgram: "Medicine",
    });
  });

  it("renders the dedicated session review page with note context and a note back path", async () => {
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue({
      sessionId: "quick-1",
      studyPackId: "sp-1",
      sessionMode: "QUICK_REVIEW",
      status: "COMPLETED",
      totalQuestions: 1,
      correctAnswers: 0,
      scorePercentage: 0,
      retryCount: 1,
      durationSeconds: 120,
      weakConcepts: ["Respiratory system"],
      conceptBreakdown: [
        {
          concept: "Respiratory system",
          correctAnswers: 0,
          totalQuestions: 1,
          accuracyPercentage: 0,
        },
      ],
      quiz: [
        {
          question: "What does pneumonoultramicroscopicsilicovolcanoconiosis indicate in a respiratory system review session?",
          choices: ["A", "B", "C", "D"],
          correctIndex: 0,
          concept: "Respiratory system",
          explanation: "Supercalifragilisticexpialidocious-style terminology should still wrap instead of forcing horizontal scrolling in the review page.",
        },
      ],
      selectedChoices: { 0: 1 },
      createdAt: "2026-04-11T10:00:00Z",
      completedAt: "2026-04-11T10:05:00Z",
    });

    render(<NoteSessionReviewPageClient noteId="note-1" sessionId="quick-1" />);

    expect(await screen.findByRole("heading", { name: "Focused review" })).toBeInTheDocument();
    await waitFor(() => {
      expect(getQuickReviewSessionReview).toHaveBeenCalledWith("note-1", "quick-1");
    });
    expect(getNote).toHaveBeenCalledWith("note-1");
    expect(screen.getByRole("link", { name: "Note" })).toHaveAttribute(
      "href",
      "/notes/note-1?tab=quiz",
    );
    expect(screen.getByText("Respiratory Notes")).toBeInTheDocument();
    expect(screen.getByText("Pulmonology")).toBeInTheDocument();
    expect(screen.getAllByText("Quick Review")).not.toHaveLength(0);
    expect(screen.getByText(/pneumonoultramicroscopicsilicovolcanoconiosis/i)).toBeInTheDocument();
    expect(screen.getByText(/Supercalifragilisticexpialidocious-style terminology/i)).toBeInTheDocument();
  });

  it("shows a clear error state when the review mode is invalid", async () => {
    currentSearch = "mode=unknown";

    render(<NoteSessionReviewPageClient noteId="note-1" sessionId="quick-1" />);

    expect(await screen.findByText("Session review not found.")).toBeInTheDocument();
    expect(getQuickReviewSessionReview).not.toHaveBeenCalled();
    expect(getChallengeQuizSessionReview).not.toHaveBeenCalled();
  });
});
