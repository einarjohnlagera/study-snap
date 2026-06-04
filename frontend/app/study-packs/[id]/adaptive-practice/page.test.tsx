import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import AdaptivePracticePage from "./page";
import { getAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import {
  completeAdaptivePracticeSession,
  forfeitAdaptivePracticeSession,
  generateAdaptiveQuickReviewQuiz,
  getInProgressAdaptivePracticeSession,
  getNote,
} from "@/lib/api";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
};
const searchParamsMock = {
  toString: () => "",
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1/adaptive-practice",
  useParams: () => ({ id: "note-1" }),
  useSearchParams: () => searchParamsMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/hooks/use-billing-usage-summary", () => ({
  useBillingUsageSummary: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeAdaptivePracticeSession: jest.fn(),
  forfeitAdaptivePracticeSession: jest.fn(),
  generateAdaptiveQuickReviewQuiz: jest.fn(),
  getInProgressAdaptivePracticeSession: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  isEmailNotVerifiedError: () => false,
  trackAnalyticsEvent: jest.fn(),
}));

describe("AdaptivePracticePage", () => {
  beforeEach(() => {
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    (useBillingUsageSummary as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PRO",
        limits: {
          studyPacksPerMonth: 100,
          challengeQuizzesPerMonth: 50,
          adaptivePracticePerMonth: 30,
          ocrPerMonth: 100,
        },
        usage: {
          studyPacksUsed: 0,
          challengeQuizzesUsed: 0,
          adaptivePracticeUsed: 0,
          ocrUsed: 0,
        },
        remaining: {
          studyPacksRemaining: 100,
          challengeQuizzesRemaining: 50,
          adaptivePracticeRemaining: 30,
          ocrRemaining: 100,
        },
      },
      usageLoaded: true,
      refreshUsageSummary: jest.fn(),
    });
    (getAuthUser as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockReset();
    (getInProgressAdaptivePracticeSession as jest.Mock).mockReset();
    (getInProgressAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: null,
      status: null,
      studyPackId: "study-pack-1",
      title: "Derivatives",
      weakConcepts: ["Trigonometric derivatives"],
      message: "Focusing on concepts you need to improve.",
      quiz: [],
    });
    (completeAdaptivePracticeSession as jest.Mock).mockReset();
    (forfeitAdaptivePracticeSession as jest.Mock).mockReset();
    (forfeitAdaptivePracticeSession as jest.Mock).mockResolvedValue({ message: "Adaptive Practice session forfeited." });
  });

  function setupGeneratedAdaptiveQuiz() {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      adaptivePracticeAvailable: true,
    });
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      weakConcepts: ["Trigonometric derivatives"],
      message: "Focusing on concepts you need to improve.",
      quiz: [
        {
          question: "What is the derivative of sin(x)?",
          choices: ["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"],
          correctIndex: 0,
          concept: "Trigonometric derivatives",
          explanation: "The derivative of sin(x) is cos(x).",
        },
      ],
    });
    (getInProgressAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      weakConcepts: ["Trigonometric derivatives"],
      message: "Focusing on concepts you need to improve.",
      quiz: [
        {
          question: "What is the derivative of sin(x)?",
          choices: ["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"],
          correctIndex: 0,
          concept: "Trigonometric derivatives",
          explanation: "The derivative of sin(x) is cos(x).",
        },
      ],
    });
    (completeAdaptivePracticeSession as jest.Mock).mockResolvedValue({ message: "Saved" });
  }

  it("keeps answer correctness after displayed choice shuffling", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      adaptivePracticeAvailable: true,
    });
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      weakConcepts: ["Trigonometric derivatives"],
      message: "Focusing on concepts you need to improve.",
      quiz: [
        {
          question: "What is the derivative of sin(x)?",
          choices: ["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"],
          correctIndex: 0,
          concept: "Trigonometric derivatives",
          explanation: "The derivative of sin(x) is cos(x).",
        },
      ],
    });
    (completeAdaptivePracticeSession as jest.Mock).mockResolvedValue({ message: "Saved" });

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Adaptive Practice" }));
    await screen.findByText("1. What is the derivative of sin(x)?");
    const correctChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    expect(correctChoice).toBeDefined();
    fireEvent.click(correctChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));

    expect(await screen.findByText("Adaptive Practice Complete")).toBeInTheDocument();
    expect(screen.getByText("Score: 1 / 1 (100%)")).toBeInTheDocument();
  });

  it("includes correctly answered concept names when completing Adaptive Practice", async () => {
    setupGeneratedAdaptiveQuiz();

    render(<AdaptivePracticePage />);

    await screen.findByText("1. What is the derivative of sin(x)?");
    const correctChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    fireEvent.click(correctChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));

    await waitFor(() => {
      expect(completeAdaptivePracticeSession).toHaveBeenCalledWith("session-1", expect.objectContaining({
        correctAnswers: 1,
        totalQuestions: 1,
        correctConceptNames: ["Trigonometric derivatives"],
      }));
    });
  });

  it("omits correct concept names when all Adaptive Practice answers are wrong", async () => {
    setupGeneratedAdaptiveQuiz();

    render(<AdaptivePracticePage />);

    await screen.findByText("1. What is the derivative of sin(x)?");
    const wrongChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*-cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    fireEvent.click(wrongChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));

    await waitFor(() => {
      expect(completeAdaptivePracticeSession).toHaveBeenCalledWith("session-1", expect.objectContaining({
        correctAnswers: 0,
        totalQuestions: 1,
      }));
    });
    const completeRequest = (completeAdaptivePracticeSession as jest.Mock).mock.calls[0]?.[1];
    expect(completeRequest).not.toHaveProperty("correctConceptNames");
  });

  it("forfeits the active Adaptive Practice session before leaving", async () => {
    setupGeneratedAdaptiveQuiz();

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Leave Quiz" }));
    expect(screen.getByRole("dialog", { name: "Leave quiz?" })).toBeInTheDocument();

    const leaveButtons = screen.getAllByRole("button", { name: "Leave Quiz" });
    fireEvent.click(leaveButtons[leaveButtons.length - 1]!);

    await waitFor(() => {
      expect(forfeitAdaptivePracticeSession).toHaveBeenCalledWith("session-1");
    });
    expect(routerMock.push).toHaveBeenCalledWith("/notes/note-1");
  });

  it("locks the page and prevents duplicate Adaptive Practice starts while generating", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      adaptivePracticeAvailable: true,
    });
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockImplementation(() => new Promise(() => {}));

    render(<AdaptivePracticePage />);

    const startButton = await screen.findByRole("button", { name: "Start Adaptive Practice" });
    fireEvent.click(startButton);
    fireEvent.click(startButton);

    await waitFor(() => {
      expect(generateAdaptiveQuickReviewQuiz).toHaveBeenCalledTimes(1);
    });
    expect(screen.getByRole("alertdialog", { name: "Generating your quiz..." })).toBeInTheDocument();
    expect(screen.getByText("Creating personalized questions from your notes")).toBeInTheDocument();
    expect(screen.getByText("Please keep this page open")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Starting..." })).toBeDisabled();
  });

  it("shows the monthly limit state for premium users who exhausted Adaptive Practice usage", async () => {
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PRO",
        limits: {
          studyPacksPerMonth: 100,
          challengeQuizzesPerMonth: 50,
          adaptivePracticePerMonth: 30,
          ocrPerMonth: 100,
        },
        usage: {
          studyPacksUsed: 0,
          challengeQuizzesUsed: 0,
          adaptivePracticeUsed: 30,
          ocrUsed: 0,
        },
        remaining: {
          studyPacksRemaining: 100,
          challengeQuizzesRemaining: 50,
          adaptivePracticeRemaining: 0,
          ocrRemaining: 100,
        },
      },
      usageLoaded: true,
      refreshUsageSummary: jest.fn(),
    });
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      adaptivePracticeAvailable: true,
    });

    render(<AdaptivePracticePage />);

    expect(await screen.findByRole("heading", { name: "You’ve reached your quiz limit for this month" })).toBeInTheDocument();
    expect(screen.getByText("Your Adaptive Practice limit resets on your next billing cycle.")).toBeInTheDocument();
    expect(screen.queryByText("Adaptive Practice is a Pro feature")).not.toBeInTheDocument();
  });

  it("shows the upgrade paywall when free users exhaust Adaptive Practice usage", async () => {
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: {
          studyPacksPerMonth: 10,
          challengeQuizzesPerMonth: 5,
          adaptivePracticePerMonth: 3,
          ocrPerMonth: 20,
        },
        usage: {
          studyPacksUsed: 0,
          challengeQuizzesUsed: 0,
          adaptivePracticeUsed: 3,
          ocrUsed: 0,
        },
        remaining: {
          studyPacksRemaining: 10,
          challengeQuizzesRemaining: 5,
          adaptivePracticeRemaining: 0,
          ocrRemaining: 20,
        },
      },
      usageLoaded: true,
      refreshUsageSummary: jest.fn(),
    });
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      adaptivePracticeAvailable: true,
    });

    render(<AdaptivePracticePage />);

    expect(await screen.findByText("You've used your free Adaptive Practice sessions")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "You’ve reached your quiz limit for this month" })).not.toBeInTheDocument();
  });

  it('result screen shows "Generate New Set" as the primary action', async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      adaptivePracticeAvailable: true,
    });
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      weakConcepts: ["Derivatives"],
      message: "Focusing on weak areas.",
      quiz: [
        {
          question: "What is the derivative of sin(x)?",
          choices: ["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"],
          correctIndex: 0,
          concept: "Derivatives",
          explanation: "The derivative of sin(x) is cos(x).",
        },
      ],
    });
    (completeAdaptivePracticeSession as jest.Mock).mockResolvedValue({ message: "Saved" });

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Adaptive Practice" }));
    await screen.findByText("1. What is the derivative of sin(x)?");
    const correctChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    fireEvent.click(correctChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));
    await screen.findByText("Adaptive Practice Complete");

    expect(screen.getByRole("button", { name: "Generate New Set" })).toBeInTheDocument();
  });

  it('result screen does not contain a "Note" button', async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      adaptivePracticeAvailable: true,
    });
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      weakConcepts: [],
      message: "Focusing on weak areas.",
      quiz: [
        {
          question: "What is the derivative of sin(x)?",
          choices: ["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"],
          correctIndex: 0,
          concept: "Derivatives",
          explanation: "The derivative of sin(x) is cos(x).",
        },
      ],
    });
    (completeAdaptivePracticeSession as jest.Mock).mockResolvedValue({ message: "Saved" });

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Adaptive Practice" }));
    await screen.findByText("1. What is the derivative of sin(x)?");
    const correctChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    fireEvent.click(correctChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));
    await screen.findByText("Adaptive Practice Complete");

    expect(screen.queryByRole("button", { name: /^Note$/ })).not.toBeInTheDocument();
    expect(screen.getByText("No targeted weak areas were attached to this set. Generate a new set or review your answers to keep practicing.")).toBeInTheDocument();
  });

  it('result screen shows "← Back to Note" navigation link', async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      adaptivePracticeAvailable: true,
    });
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      weakConcepts: [],
      message: "Focusing on weak areas.",
      quiz: [
        {
          question: "What is the derivative of sin(x)?",
          choices: ["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"],
          correctIndex: 0,
          concept: "Derivatives",
          explanation: "The derivative of sin(x) is cos(x).",
        },
      ],
    });
    (completeAdaptivePracticeSession as jest.Mock).mockResolvedValue({ message: "Saved" });

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Adaptive Practice" }));
    await screen.findByText("1. What is the derivative of sin(x)?");
    const correctChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    fireEvent.click(correctChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));
    await screen.findByText("Adaptive Practice Complete");

    expect(screen.getByRole("link", { name: /Back to Note/i })).toBeInTheDocument();
    expect(screen.getByText("Was this quiz helpful?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Give Feedback" })).toBeInTheDocument();
  });

  it("opens answer review with selected answer, correct answer, explanation, and concept", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      adaptivePracticeAvailable: true,
    });
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      weakConcepts: ["Trigonometric derivatives"],
      message: "Focusing on weak areas.",
      quiz: [
        {
          question: "What is the derivative of sin(x)?",
          choices: ["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"],
          correctIndex: 0,
          concept: "Trigonometric derivatives",
          explanation: "The derivative of sin(x) is cos(x).",
        },
      ],
    });
    (completeAdaptivePracticeSession as jest.Mock).mockResolvedValue({ message: "Saved" });

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Adaptive Practice" }));
    await screen.findByText("1. What is the derivative of sin(x)?");
    const wrongChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*-cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    fireEvent.click(wrongChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));
    await screen.findByText("Adaptive Practice Complete");
    fireEvent.click(screen.getByRole("button", { name: "Review Answers" }));

    const review = screen.getByLabelText("Answer review");
    expect(review).toHaveTextContent("What is the derivative of sin(x)?");
    expect(review).toHaveTextContent("Trigonometric derivatives");
    expect(review).toHaveTextContent("-cos(x)");
    expect(review).toHaveTextContent("Your Answer");
    expect(review).toHaveTextContent("cos(x)");
    expect(review).toHaveTextContent("Correct Answer");
    expect(review).toHaveTextContent("The derivative of sin(x) is cos(x).");
  });
});
