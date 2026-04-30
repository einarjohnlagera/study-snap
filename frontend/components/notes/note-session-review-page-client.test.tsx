import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NoteSessionReviewPageClient } from "./note-session-review-page-client";
import {
  getNote,
  getChallengeQuizSessionReview,
  getQuickReviewSessionReview,
} from "@/lib/api";
import { exportQuizSessionReviewDocument, hasExportableContent } from "@/lib/quiz-session-export";

const pushMock = jest.fn();
const routerMock = { push: pushMock, replace: jest.fn() };
let currentSearch = "mode=quick-review&tab=quiz";

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
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

jest.mock("@/lib/quiz-session-export", () => ({
  exportQuizSessionReviewDocument: jest.fn(),
  hasExportableContent: jest.fn().mockReturnValue(true),
}));

describe("NoteSessionReviewPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    currentSearch = "mode=quick-review&tab=quiz";
    (getNote as jest.Mock).mockReset();
    (getChallengeQuizSessionReview as jest.Mock).mockReset();
    (getQuickReviewSessionReview as jest.Mock).mockReset();
    (exportQuizSessionReviewDocument as jest.Mock).mockReset();
    (hasExportableContent as jest.Mock).mockReturnValue(true);
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
    expect(await screen.findByText("Respiratory Notes")).toBeInTheDocument();
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

  const RESPIRATORY_REVIEW = {
    sessionId: "quick-1",
    studyPackId: "sp-1",
    sessionMode: "QUICK_REVIEW" as const,
    status: "COMPLETED" as const,
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
  };

  it("shows Full Review, Mistakes, Weak Concepts, and disabled Adaptive Practice when Export is clicked", async () => {
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue(RESPIRATORY_REVIEW);

    render(<NoteSessionReviewPageClient noteId="note-1" sessionId="quick-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Export" }));

    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: /^Full Review/ }).length).toBeGreaterThanOrEqual(1);
    });
    expect(screen.getAllByRole("button", { name: /^Mistakes/ }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole("button", { name: /^Weak Concepts/ }).length).toBeGreaterThanOrEqual(1);
    // Adaptive Practice is disabled for non-adaptive sessions
    const adaptiveButtons = screen.getAllByRole("button", { name: /^Adaptive Practice/ });
    expect(adaptiveButtons.length).toBeGreaterThanOrEqual(1);
    expect(adaptiveButtons[0]).toBeDisabled();
  });

  it("shows a free-user message on the disabled Adaptive Practice option", async () => {
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue(RESPIRATORY_REVIEW);

    render(<NoteSessionReviewPageClient noteId="note-1" sessionId="quick-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Export" }));

    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: /^Full Review/ }).length).toBeGreaterThanOrEqual(1);
    });
    expect(screen.getByText(/Adaptive Practice is available after completing a session \(Pro feature\)/i)).toBeInTheDocument();
  });

  it("exports the full review PDF with clear feedback when Full Review is selected", async () => {
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue(RESPIRATORY_REVIEW);
    (exportQuizSessionReviewDocument as jest.Mock).mockResolvedValue({ filename: "notelib-quiz-respiratory-notes-2026-04-14.pdf" });

    render(<NoteSessionReviewPageClient noteId="note-1" sessionId="quick-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Export" }));
    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: /^Full Review/ }).length).toBeGreaterThanOrEqual(1);
    });
    fireEvent.click(screen.getAllByRole("button", { name: /^Full Review/ })[0]);

    await waitFor(() => {
      expect(screen.getByText("Exporting PDF...")).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(exportQuizSessionReviewDocument).toHaveBeenCalledWith(expect.objectContaining({
        format: "pdf",
        exportType: "full",
        noteTitle: "Respiratory Notes",
        noteSubject: "Pulmonology",
        quizTypeLabel: "Quick Review",
      }));
    });
    expect(screen.getByText("PDF ready")).toBeInTheDocument();
  });

  it("exports a mistakes-only PDF with clear feedback when Mistakes is selected", async () => {
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue(RESPIRATORY_REVIEW);
    (exportQuizSessionReviewDocument as jest.Mock).mockResolvedValue({ filename: "notelib-mistakes-respiratory-notes-2026-04-14.pdf" });

    render(<NoteSessionReviewPageClient noteId="note-1" sessionId="quick-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Export" }));
    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: /^Mistakes/ }).length).toBeGreaterThanOrEqual(1);
    });
    fireEvent.click(screen.getAllByRole("button", { name: /^Mistakes/ })[0]);

    await waitFor(() => {
      expect(exportQuizSessionReviewDocument).toHaveBeenCalledWith(expect.objectContaining({
        format: "pdf",
        exportType: "mistakes-only",
        noteTitle: "Respiratory Notes",
        noteSubject: "Pulmonology",
        quizTypeLabel: "Quick Review",
      }));
    });
    expect(screen.getByText("PDF ready")).toBeInTheDocument();
  });

  it("exports a weak concepts PDF with clear feedback when Weak Concepts is selected", async () => {
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue(RESPIRATORY_REVIEW);
    (exportQuizSessionReviewDocument as jest.Mock).mockResolvedValue({ filename: "notelib-weak-concepts-respiratory-notes-2026-04-14.pdf" });

    render(<NoteSessionReviewPageClient noteId="note-1" sessionId="quick-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Export" }));
    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: /^Weak Concepts/ }).length).toBeGreaterThanOrEqual(1);
    });
    fireEvent.click(screen.getAllByRole("button", { name: /^Weak Concepts/ })[0]);

    await waitFor(() => {
      expect(exportQuizSessionReviewDocument).toHaveBeenCalledWith(expect.objectContaining({
        format: "pdf",
        exportType: "weak-concepts",
        noteTitle: "Respiratory Notes",
        noteSubject: "Pulmonology",
        quizTypeLabel: "Quick Review",
      }));
    });
    expect(screen.getByText("PDF ready")).toBeInTheDocument();
  });

  it("shows a toast and skips PDF generation when Mistakes is selected but there are no mistakes", async () => {
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue(RESPIRATORY_REVIEW);
    (hasExportableContent as jest.Mock).mockImplementation((type: string) => type !== "mistakes-only");

    render(<NoteSessionReviewPageClient noteId="note-1" sessionId="quick-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Export" }));
    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: /^Mistakes/ }).length).toBeGreaterThanOrEqual(1);
    });
    fireEvent.click(screen.getAllByRole("button", { name: /^Mistakes/ })[0]);

    await waitFor(() => {
      expect(screen.getByText(/No mistakes to export/i)).toBeInTheDocument();
    });
    expect(exportQuizSessionReviewDocument).not.toHaveBeenCalled();
  });

  it("shows a toast and skips PDF generation when Weak Concepts is selected but there are none", async () => {
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue(RESPIRATORY_REVIEW);
    (hasExportableContent as jest.Mock).mockImplementation((type: string) => type !== "weak-concepts");

    render(<NoteSessionReviewPageClient noteId="note-1" sessionId="quick-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Export" }));
    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: /^Weak Concepts/ }).length).toBeGreaterThanOrEqual(1);
    });
    fireEvent.click(screen.getAllByRole("button", { name: /^Weak Concepts/ })[0]);

    await waitFor(() => {
      expect(screen.getByText(/No weak concepts identified/i)).toBeInTheDocument();
    });
    expect(exportQuizSessionReviewDocument).not.toHaveBeenCalled();
  });
});
