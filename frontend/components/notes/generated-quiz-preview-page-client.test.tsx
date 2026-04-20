import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GeneratedQuizPreviewPageClient } from "./generated-quiz-preview-page-client";
import { exportGeneratedQuizDocx, generateGeneratedQuiz, getGeneratedQuiz, getNote, trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1/quiz",
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  getGeneratedQuiz: jest.fn(),
  generateGeneratedQuiz: jest.fn(),
  getNote: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  exportGeneratedQuizDocx: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/hooks/use-billing-usage-summary", () => ({
  useBillingUsageSummary: jest.fn(),
}));

describe("GeneratedQuizPreviewPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (getNote as jest.Mock).mockReset();
    (getGeneratedQuiz as jest.Mock).mockReset();
    (generateGeneratedQuiz as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (exportGeneratedQuizDocx as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReset();

    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      role: "USER",
      profileType: "TEACHER",
    });

    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Teacher Note",
      subject: "Biology",
      courseProgram: "Nursing",
      tags: [],
      content: "Cells",
      visibility: "PRIVATE",
      createdAt: "2026-04-17T09:00:00Z",
      updatedAt: "2026-04-17T09:00:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: null,
      studyPackStatus: "DRAFT",
      summary: null,
      keyConcepts: [],
      quiz: [],
      generatedQuiz: null,
      quizCount: 0,
      quickReviewAvailable: false,
      challengeQuizAvailable: false,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: false,
    });
    (getGeneratedQuiz as jest.Mock).mockResolvedValue({
      id: "quiz-1",
      noteId: "note-1",
      generatedAt: "2026-04-17T09:00:00Z",
      questions: [
        {
          question: "What is the nucleus?",
          choices: ["Control center", "Energy source", "Cell wall", "Waste product"],
          correctIndex: 0,
          concept: "Cells",
          explanation: "The nucleus controls cell activity.",
        },
      ],
    });
    (generateGeneratedQuiz as jest.Mock).mockResolvedValue({
      id: "quiz-2",
      noteId: "note-1",
      generatedAt: "2026-04-17T10:00:00Z",
      questions: [
        {
          question: "What does the membrane do?",
          choices: ["Protects the cell", "Creates ATP", "Stores DNA", "Builds proteins"],
          correctIndex: 0,
          concept: "Cells",
          explanation: "The membrane protects and regulates transport.",
        },
      ],
    });
    (exportGeneratedQuizDocx as jest.Mock).mockResolvedValue({ filename: "teacher-note-quiz.docx" });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: {
          studyPacksPerMonth: 10,
          challengeQuizzesPerMonth: 5,
          adaptivePracticePerMonth: 0,
          ocrPerMonth: 20,
        },
        usage: {
          studyPacksUsed: 1,
          challengeQuizzesUsed: 1,
          adaptivePracticeUsed: 0,
          ocrUsed: 0,
        },
        remaining: {
          studyPacksRemaining: 9,
          challengeQuizzesRemaining: 4,
          adaptivePracticeRemaining: 0,
          ocrRemaining: 20,
        },
        features: {
          adaptivePracticeAvailable: false,
          difficultySelectionAvailable: false,
          fileUploadAvailable: true,
          ocrAvailable: true,
        },
      },
      refreshUsageSummary: jest.fn(),
    });
  });

  it("renders the generated quiz with answers visible", async () => {
    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Quiz Preview" })).toBeInTheDocument();
    expect(await screen.findByText("What is the nucleus?")).toBeInTheDocument();
    expect(screen.getByText("Teacher Note")).toBeInTheDocument();
    expect(screen.getByText("Generated Quiz - Ready for export")).toBeInTheDocument();
    expect(screen.getByText("✓ Correct")).toBeInTheDocument();
    expect(screen.getByText("The nucleus controls cell activity.")).toBeInTheDocument();
  });

  it("exports from the dedicated quiz view", async () => {
    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    fireEvent.click(screen.getByRole("button", { name: "Export" }));
    fireEvent.click(screen.getByRole("button", { name: /Quiz \+ Answers \(Teacher Version\)/i }));

    await waitFor(() => {
      expect(exportGeneratedQuizDocx).toHaveBeenCalledWith("quiz-1", "WITH_ANSWERS");
    });
  });

  it("hides DOCX export for non-teacher non-admin users", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-2",
      role: "USER",
      profileType: "STUDENT",
    });

    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    expect(screen.queryByRole("button", { name: "Export" })).not.toBeInTheDocument();
  });

  it("regenerates after confirmation", async () => {
    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    fireEvent.click(screen.getByRole("button", { name: "Regenerate" }));
    expect(screen.getByText("Regenerate quiz?")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "Regenerate Quiz" }).at(-1) as HTMLButtonElement);

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1");
    });
    expect(await screen.findByText("What does the membrane do?")).toBeInTheDocument();
  });

  it("shows the paywall modal instead of regenerating when free teacher quiz credits are exhausted", async () => {
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: {
          studyPacksPerMonth: 10,
          challengeQuizzesPerMonth: 5,
          adaptivePracticePerMonth: 0,
          ocrPerMonth: 20,
        },
        usage: {
          studyPacksUsed: 1,
          challengeQuizzesUsed: 5,
          adaptivePracticeUsed: 0,
          ocrUsed: 0,
        },
        remaining: {
          studyPacksRemaining: 9,
          challengeQuizzesRemaining: 0,
          adaptivePracticeRemaining: 0,
          ocrRemaining: 20,
        },
        features: {
          adaptivePracticeAvailable: false,
          difficultySelectionAvailable: false,
          fileUploadAvailable: true,
          ocrAvailable: true,
        },
      },
      refreshUsageSummary: jest.fn(),
    });

    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    fireEvent.click(screen.getByRole("button", { name: "Regenerate" }));
    fireEvent.click(screen.getAllByRole("button", { name: "Regenerate Quiz" }).at(-1) as HTMLButtonElement);

    expect(await screen.findByRole("dialog", { name: "You've reached your monthly quiz limit" })).toBeInTheDocument();
    expect(generateGeneratedQuiz).not.toHaveBeenCalled();
  });
});
