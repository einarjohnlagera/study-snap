import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { GeneratedQuizPreviewPageClient } from "./generated-quiz-preview-page-client";
import {
  createQuizShareLink,
  exportGeneratedQuizDocx,
  generateGeneratedQuiz,
  getGeneratedQuiz,
  getMe,
  getNote,
  getQuizShareLinkByQuizId,
  trackAnalyticsEvent,
} from "@/lib/api";
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
  getMe: jest.fn(),
  getNote: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  exportGeneratedQuizDocx: jest.fn(),
  isMultipleExamVersionsNotAllowedError: jest.fn(() => false),
  getQuizShareLinkByQuizId: jest.fn(() => Promise.resolve(null)),
  createQuizShareLink: jest.fn(),
  toggleQuizShareLink: jest.fn(),
  isExportLimitReachedError: jest.fn(() => false),
  isQuizShareLinkLimitExceededError: jest.fn(() => false),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/hooks/use-billing-usage-summary", () => ({
  useBillingUsageSummary: jest.fn(),
}));

describe("GeneratedQuizPreviewPageClient", () => {
  beforeEach(() => {
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText: jest.fn().mockResolvedValue(undefined) },
    });
    pushMock.mockReset();
    replaceMock.mockReset();
    (getNote as jest.Mock).mockReset();
    (getGeneratedQuiz as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (generateGeneratedQuiz as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (exportGeneratedQuizDocx as jest.Mock).mockReset();
    (getQuizShareLinkByQuizId as jest.Mock).mockReset();
    (createQuizShareLink as jest.Mock).mockReset();
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
    (getMe as jest.Mock).mockResolvedValue({
      schoolName: "NoteLib Academy",
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
    (getQuizShareLinkByQuizId as jest.Mock).mockResolvedValue(null);
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
          fileUploadAvailable: true,
          ocrAvailable: true,
        },
      },
      refreshUsageSummary: jest.fn(),
    });
  });

  it("renders the generated quiz with answers visible", async () => {
    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Teacher Note" })).toBeInTheDocument();
    expect(await screen.findByText("What is the nucleus?")).toBeInTheDocument();
    expect(screen.getByText("Quiz Preview")).toBeInTheDocument();
    expect(screen.getByText("Generated Quiz - Ready for export")).toBeInTheDocument();
    expect(screen.getByText("1 question")).toBeInTheDocument();
    expect(screen.getByText("✓ Correct")).toBeInTheDocument();
    expect(screen.getByText("The nucleus controls cell activity.")).toBeInTheDocument();
  });

  it("exports from the dedicated quiz view", async () => {
    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    fireEvent.click(screen.getByRole("button", { name: "Export" }));
    expect(screen.getByText(/From your profile:/)).toHaveTextContent("NoteLib Academy");
    fireEvent.click(screen.getByRole("button", { name: /Quiz \+ Answers Includes answer key and explanations for teacher review\./i }));

    await waitFor(() => {
      expect(exportGeneratedQuizDocx).toHaveBeenCalledWith("quiz-1", "WITH_ANSWERS", {
        className: null,
        includeDate: true,
      }, 1);
    });
  });

  it("opens the exam versions paywall when a Free teacher clicks a locked version", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      role: "USER",
      planType: "FREE",
      emailVerifiedAt: "2026-04-17T09:00:00Z",
      profileType: "TEACHER",
    });

    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    fireEvent.click(screen.getByRole("button", { name: "Export" }));
    fireEvent.click(screen.getByRole("button", { name: "2 Plus" }));

    expect(await screen.findByRole("heading", { name: "Unlock multiple exam versions" })).toBeInTheDocument();
    expect(screen.getByText("Plus unlocks multiple exam versions for anti-cheating.")).toBeInTheDocument();
    expect(exportGeneratedQuizDocx).not.toHaveBeenCalled();
  });

  it("exports the selected version count for Plus teachers", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      role: "USER",
      planType: "PLUS",
      emailVerifiedAt: "2026-04-17T09:00:00Z",
      profileType: "TEACHER",
    });

    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    fireEvent.click(screen.getByRole("button", { name: "Export" }));
    fireEvent.click(screen.getByRole("button", { name: "3" }));
    fireEvent.click(screen.getByRole("button", { name: /Quiz Only Questions and choices only/i }));

    await waitFor(() => {
      expect(exportGeneratedQuizDocx).toHaveBeenCalledWith("quiz-1", "QUIZ_ONLY", {
        className: null,
        includeDate: true,
      }, 3);
    });
  });

  it("lets a student create a share link while keeping DOCX export hidden", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-2",
      role: "USER",
      profileType: "STUDENT",
    });
    (createQuizShareLink as jest.Mock).mockResolvedValue({
      id: "link-1",
      token: "token-1",
      shareUrl: "https://notelib.app/quiz/token-1",
      isActive: true,
      createdAt: "2026-04-17T09:00:00Z",
    });

    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    expect(screen.queryByRole("button", { name: "Export" })).not.toBeInTheDocument();
    const shareHeading = screen.getByRole("heading", { name: "Share with Someone" });
    const shareCard = shareHeading.closest("section, div");
    expect(shareCard).not.toBeNull();
    expect(within(shareCard as HTMLElement).queryByText(/students/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Create Share Link" }));

    await waitFor(() => {
      expect(createQuizShareLink).toHaveBeenCalledWith("quiz-1");
    });
    expect(await screen.findByText("https://notelib.app/quiz/token-1")).toBeInTheDocument();
  });

  it("loads an existing share link for a student on mount", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-2",
      role: "USER",
      profileType: "STUDENT",
    });
    (getQuizShareLinkByQuizId as jest.Mock).mockResolvedValue({
      id: "link-1",
      token: "token-1",
      shareUrl: "https://notelib.app/quiz/token-1",
      isActive: true,
      createdAt: "2026-04-17T09:00:00Z",
    });

    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    expect(await screen.findByText("https://notelib.app/quiz/token-1")).toBeInTheDocument();
    expect(getQuizShareLinkByQuizId).toHaveBeenCalledWith("quiz-1");
    expect(screen.queryByRole("button", { name: "Export" })).not.toBeInTheDocument();
  });

  it("regenerates after confirmation", async () => {
    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    fireEvent.click(screen.getByRole("button", { name: "More quiz actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Regenerate quiz" }));
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
          fileUploadAvailable: true,
          ocrAvailable: true,
        },
      },
      refreshUsageSummary: jest.fn(),
    });

    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByText("What is the nucleus?");
    fireEvent.click(screen.getByRole("button", { name: "More quiz actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Regenerate quiz" }));
    fireEvent.click(screen.getAllByRole("button", { name: "Regenerate Quiz" }).at(-1) as HTMLButtonElement);

    expect(await screen.findByRole("dialog", { name: "You've reached your quiz generation limit" })).toBeInTheDocument();
    expect(generateGeneratedQuiz).not.toHaveBeenCalled();
  });
});
