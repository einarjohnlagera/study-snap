import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import AdaptivePracticePage from "./page";
import { getAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import {
  completeAdaptivePracticeSession,
  forfeitAdaptivePracticeSession,
  generateAdaptiveQuickReviewQuiz,
  getAdaptivePracticeSession,
  getCollectionGoal,
  getInProgressAdaptivePracticeSession,
  getMe,
  getNote,
  getPostSessionNextStep,
} from "@/lib/api";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
};
let searchParamsMock = new URLSearchParams();
let pathnameMock = "/notes/note-1/adaptive-practice";

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => pathnameMock,
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
  getAdaptivePracticeSession: jest.fn(),
  getCollectionGoal: jest.fn(),
  getInProgressAdaptivePracticeSession: jest.fn(),
  getMe: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  getPostSessionNextStep: jest.fn(),
  isEmailNotVerifiedError: () => false,
  trackAnalyticsEvent: jest.fn(),
}));

describe("AdaptivePracticePage", () => {
  beforeEach(() => {
    window.localStorage.clear();
    searchParamsMock = new URLSearchParams();
    pathnameMock = "/notes/note-1/adaptive-practice";
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
    (getAdaptivePracticeSession as jest.Mock).mockReset();
    (getInProgressAdaptivePracticeSession as jest.Mock).mockReset();
    (getInProgressAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: null,
      status: null,
      studyPackId: "study-pack-1",
      title: "Derivatives",
      focusConcepts: [{ concept: "Trigonometric derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
      message: "Focusing on concepts you need to improve.",
      quiz: [],
    });
    (completeAdaptivePracticeSession as jest.Mock).mockReset();
    (getPostSessionNextStep as jest.Mock).mockReset();
    (getPostSessionNextStep as jest.Mock).mockRejectedValue(new Error("next-step unavailable"));
    (getMe as jest.Mock).mockReset();
    (getMe as jest.Mock).mockRejectedValue(new Error("me unavailable"));
    (getCollectionGoal as jest.Mock).mockReset();
    (forfeitAdaptivePracticeSession as jest.Mock).mockReset();
    (forfeitAdaptivePracticeSession as jest.Mock).mockResolvedValue({ message: "Adaptive Practice session forfeited." });
  });

  it("reloads a collection-anchored session by session id with both note anchors absent", async () => {
    pathnameMock = "/adaptive-practice/sessions/note-1";
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: "note-1",
      status: "IN_PROGRESS",
      studyPackId: null,
      noteId: null,
      title: "Structural Engineering Plan",
      focusConcepts: [{
        concept: "Shear Force",
        sourceStudyPackId: "pack-1",
        sourceTitle: "Statics",
        selectionReason: "DUE",
      }],
      message: "Focusing on concepts you need to improve.",
      quiz: [{
        question: "Where is shear highest?",
        choices: ["A", "B", "C", "D"],
        correctIndex: 0,
        concept: "Shear Force",
        explanation: "At the support.",
        sourceStudyPackId: "pack-1",
      }],
    });

    render(<AdaptivePracticePage />);

    expect(await screen.findByText("Structural Engineering Plan")).toBeInTheDocument();
    expect(screen.getByText(/Where is shear highest\?/)).toBeInTheDocument();
    expect(getAdaptivePracticeSession).toHaveBeenCalledWith("note-1");
    expect(getInProgressAdaptivePracticeSession).not.toHaveBeenCalled();
    expect(getNote).not.toHaveBeenCalled();
  });

  function setupGeneratedAdaptiveQuiz() {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      keyConcepts: ["Trigonometric derivatives"],
      adaptivePracticeAvailable: true,
    });
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      focusConcepts: [{ concept: "Trigonometric derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
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
      focusConcepts: [{ concept: "Trigonometric derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
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
    (completeAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      message: "Saved",
      isFirstCompletedSessionEver: true,
    });
  }

  it.each([
    ["DUE", "Reviewing: Trigonometric derivatives — due for review"],
    ["WEAK", "Reviewing: Trigonometric derivatives — missed last time"],
    ["BOTH", "Reviewing: Trigonometric derivatives — missed last time and due for review"],
  ])("restores and renders the %s question rationale from an in-progress session", async (reason, expectedLabel) => {
    setupGeneratedAdaptiveQuiz();
    (getInProgressAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      focusConcepts: [
        { concept: "Trigonometric derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: reason }
      ],
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

    render(<AdaptivePracticePage />);

    expect(await screen.findByText(expectedLabel)).toBeInTheDocument();
  });

  it("renders no rationale tag when the resumed question has no selection reason", async () => {
    setupGeneratedAdaptiveQuiz();
    (getInProgressAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      focusConcepts: [
        { concept: "Trigonometric derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }
      ],
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

    render(<AdaptivePracticePage />);

    await screen.findByText("1. What is the derivative of sin(x)?");
    expect(screen.queryByLabelText("Why these questions")).not.toBeInTheDocument();
  });

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
      focusConcepts: [{ concept: "Trigonometric derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
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
        // ⚠️ LOAD-BEARING. Adaptive Practice has no progress endpoint, so nothing persists the
        // learner's answers during the session. If the client stops sending them the server's
        // per-source breakdown is empty, and a plan-scoped session silently attributes every
        // concept to the anchor pack and records NO MISSES -- the over-attribution shape item 1
        // removed. This assertion is what stops that regressing unnoticed.
        selectedChoices: { 0: 0 },
        selectedMultiChoices: {},
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

  it("reads a known entry from the page URL and forwards it when starting", async () => {
    searchParamsMock = new URLSearchParams("entry=dashboard-today-focus");
    setupGeneratedAdaptiveQuiz();
    (getInProgressAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: null,
      status: null,
      studyPackId: "study-pack-1",
      title: "Derivatives",
      focusConcepts: [{ concept: "Trigonometric derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
      message: "Focusing on concepts you need to improve.",
      quiz: [],
    });

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Adaptive Practice" }));
    await waitFor(() => {
      expect(generateAdaptiveQuickReviewQuiz).toHaveBeenCalledWith("note-1", "dashboard-today-focus");
    });
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
      focusConcepts: [{ concept: "Derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
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
    expect(screen.getByText("Was this quiz helpful?")).toBeInTheDocument();
    expect(screen.queryByText("How did your first quiz go?")).not.toBeInTheDocument();

    expect(screen.getByRole("button", { name: "Generate New Set" })).toBeInTheDocument();
    expect(screen.queryByTestId("adaptive-next-step-guidance")).not.toBeInTheDocument();
    expect(screen.queryByTestId("adaptive-companion-guidance")).not.toBeInTheDocument();
  });

  it("links only targeted weak areas that have a Key Concepts explanation", async () => {
    setupGeneratedAdaptiveQuiz();
    (generateAdaptiveQuickReviewQuiz as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      title: "Derivatives",
      focusConcepts: [{ concept: "  TRIGONOMETRIC DERIVATIVES  ", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }, { concept: "Unmapped concept", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
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
      focusConcepts: [{ concept: "  TRIGONOMETRIC DERIVATIVES  ", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }, { concept: "Unmapped concept", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
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
    render(<AdaptivePracticePage />);

    const correctChoice = (await screen.findAllByRole("button")).find((button) => (
      /^[A-D]\.\s*cos\(x\)$/i.test(button.textContent?.trim() ?? "")
    ));
    fireEvent.click(correctChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));
    await screen.findByText("Adaptive Practice Complete");

    expect(screen.getByRole("link", { name: /TRIGONOMETRIC DERIVATIVES/i })).toHaveAttribute(
      "href",
      "/notes/note-1?tab=key-concepts#concept-trigonometric-derivatives",
    );
    expect(screen.getByText("Unmapped concept").closest("a")).toBeNull();
  });

  it("fetches and renders the server-resolved next step after Adaptive Practice completion", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      planType: "PRO",
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
      focusConcepts: [{ concept: "Derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
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
    (getPostSessionNextStep as jest.Mock).mockResolvedValue({
      type: "REVIEW_PACK",
      studyPackId: "study-pack-1",
      noteId: "note-1",
      title: "Derivatives",
      message: "You are in good shape here. Step up with a challenge or review the note when ready.",
      actionLabel: "Take a Challenge",
      actionHref: "/notes/note-1/challenge-quiz",
      concepts: [],
      adaptivePracticeAvailable: true,
      adaptivePracticeRemaining: null,
      goalNudge: null,
    });

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Adaptive Practice" }));
    await screen.findByText("1. What is the derivative of sin(x)?");
    const correctChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    fireEvent.click(correctChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));

    expect(await screen.findByText("Recommended next step")).toBeInTheDocument();
    expect(screen.getByTestId("adaptive-next-step-guidance")).toHaveAttribute("aria-label", "What to do next");
    expect(screen.getByRole("link", { name: "Take a Challenge" })).toHaveAttribute(
      "href",
      "/notes/note-1/challenge-quiz",
    );
    expect(getPostSessionNextStep).toHaveBeenCalledWith("study-pack-1");
  });

  it("shows the primary Review Set's Companion excerpt when it has Common Mistakes content", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      planType: "PRO",
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
      focusConcepts: [{ concept: "Derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
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
    (completeAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      message: "Saved",
      twiceMissedConcepts: ["Derivatives"],
    });
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE", primaryCollectionId: "goal-1" });
    (getCollectionGoal as jest.Mock).mockResolvedValue({
      weeksRemaining: null,
      companion: { commonMistakes: "Watch out for mixing up mitosis and meiosis.", studyStrategy: null },
    });

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Adaptive Practice" }));
    await screen.findByText("1. What is the derivative of sin(x)?");
    const correctChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    fireEvent.click(correctChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));

    expect(await screen.findByText(/Watch out for mixing up mitosis and meiosis\./)).toBeInTheDocument();
    expect(screen.getByTestId("adaptive-companion-guidance")).toHaveAttribute("aria-label", "Companion guidance");
    expect(getCollectionGoal).toHaveBeenCalledWith("goal-1");
    expect(screen.getByRole("link", { name: "Ask Companion about this" })).toHaveAttribute(
      "href",
      "/collections/goal-1?askCompanionDraft=Can+you+explain+Derivatives+a+different+way%3F",
    );
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
      focusConcepts: [],
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

  it('result screen shows "Note" navigation link', async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
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
      focusConcepts: [],
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
    (completeAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      message: "Saved",
      isFirstCompletedSessionEver: true,
    });

    render(<AdaptivePracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Adaptive Practice" }));
    await screen.findByText("1. What is the derivative of sin(x)?");
    const correctChoice = (await screen.findAllByRole("button")).find((button) =>
      /^[A-D]\.\s*cos\(x\)$/i.test(button.textContent?.trim() ?? ""),
    );
    fireEvent.click(correctChoice!);
    fireEvent.click(screen.getByRole("button", { name: "Finish Adaptive Practice" }));
    await screen.findByText("Adaptive Practice Complete");

    expect(screen.getAllByRole("link", { name: "Note" }).length).toBeGreaterThan(0);
    expect(await screen.findByText("How did your first quiz go?")).toBeInTheDocument();
    expect(screen.queryByText("Was this quiz helpful?")).not.toBeInTheDocument();
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
      focusConcepts: [{ concept: "Trigonometric derivatives", sourceStudyPackId: "study-pack-1", sourceTitle: "Derivatives", selectionReason: null }],
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

  it("keeps two packs' identical concept apart, labels the source, and matches the rationale by pack", async () => {
    // ⚠️ THE ONLY FIXTURE IN THE SUITE WHOSE QUIZ ITEMS CARRY sourceStudyPackId. Every other one
    // short-circuits the pack-matching clause via `!question?.sourceStudyPackId`, so the clause the
    // comment calls load-bearing was never exercised, and the duplicate-key case the "never merge
    // concepts" decision mandates was never rendered.
    setupGeneratedAdaptiveQuiz();
    (getInProgressAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: null,
      status: null,
      studyPackId: "study-pack-1",
      noteId: "note-1",
      title: "Structural Engineering",
      focusConcepts: [
        { concept: "Shear Force", sourceStudyPackId: "pack-a", sourceTitle: "Structural Analysis", selectionReason: "BOTH" },
        { concept: "Shear Force", sourceStudyPackId: "pack-b", sourceTitle: "Reinforced Concrete", selectionReason: "WEAK" },
      ],
      message: "Focusing on concepts you need to improve.",
      quiz: [],
    });

    render(<AdaptivePracticePage />);

    // Both entries render -- NOT merged into one -- and each carries its source, which is the
    // disambiguation the "never merge concepts across packs" decision rests on.
    expect(await screen.findAllByText(/Shear Force/)).toHaveLength(2);
    expect(screen.getByText(/Structural Analysis/)).toBeInTheDocument();
    expect(screen.getByText(/Reinforced Concrete/)).toBeInTheDocument();
  });

  /**
   * ⚠️ THE FIXTURE IS DELIBERATELY MULTI-SOURCE WITH UNIQUE CONCEPT NAMES, WHICH IS WHAT MAKES IT
   * DISCRIMINATING. The previous surface showed a source label only when two packs shared a concept
   * STRING, so a plan-scoped session whose 14 concepts happen to be distinct rendered as one
   * undifferentiated 14-item list with no source anywhere and no idea how many notes it spanned --
   * exactly the reported illegibility. A single-source fixture, or one relying on duplicate names,
   * passes under both the defect and the fix and proves nothing.
   */
  it("summarises a plan-scoped session by source note instead of one undifferentiated concept list", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Derivatives",
      studyPackStatus: "STUDY_PACK_READY",
      keyConcepts: [],
      adaptivePracticeAvailable: true,
    });
    (getInProgressAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: null,
      status: null,
      studyPackId: "study-pack-1",
      noteId: "note-1",
      title: "Structural Engineering Plan",
      focusConcepts: [
        ...["Shear force", "Bending moment", "Influence lines", "Truss analysis", "Deflection"]
          .map((concept) => ({ concept, sourceStudyPackId: "pack-a", sourceTitle: "Structural Analysis", selectionReason: null })),
        ...["Flexural strength", "Development length", "Shear reinforcement", "Serviceability"]
          .map((concept) => ({ concept, sourceStudyPackId: "pack-b", sourceTitle: "Reinforced Concrete", selectionReason: null })),
        ...["Bearing capacity", "Consolidation", "Slope stability"]
          .map((concept) => ({ concept, sourceStudyPackId: "pack-c", sourceTitle: "Soil Mechanics", selectionReason: null })),
        ...["Flow nets", "Weir discharge"]
          .map((concept) => ({ concept, sourceStudyPackId: "pack-d", sourceTitle: "Hydraulics", selectionReason: null })),
      ],
      message: "Focusing on concepts you need to improve.",
      quiz: [],
    });

    render(<AdaptivePracticePage />);

    // Layer 3 -- the compact weakness summary, which did not exist before.
    expect(await screen.findByText("14 weak concepts across 4 notes")).toBeInTheDocument();
    // Layer 2 -- scope. After the backend fix this is the Subject Plan, not one borrowed pack.
    expect(screen.getByRole("heading", { name: "Structural Engineering Plan" })).toBeInTheDocument();
    // Layer 4 -- source grouping, shown unconditionally rather than only on duplicate names.
    expect(screen.getByText(/Structural Analysis/)).toBeInTheDocument();
    expect(screen.getByText(/Reinforced Concrete/)).toBeInTheDocument();
    // Layer 5 -- progressive disclosure: the later notes and the long tail of each group are held back.
    expect(screen.queryByText(/Soil Mechanics/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Hydraulics/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Deflection/)).not.toBeInTheDocument();
    expect(screen.getByText("+ 2 more notes")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Show all concepts" }));

    expect(screen.getByText(/Soil Mechanics/)).toBeInTheDocument();
    expect(screen.getByText(/Hydraulics/)).toBeInTheDocument();
    expect(screen.getByText(/Deflection/)).toBeInTheDocument();
    expect(screen.getByText(/Weir discharge/)).toBeInTheDocument();
  });

  /**
   * The in-quiz header joined every concept name with ", " and discarded the source entirely, which
   * merges two packs' identical concept into what reads as one duplicated entry -- the cross-pack
   * merge the response shape exists to prevent. The fixture uses ONE concept string across TWO packs
   * because that is the only shape in which the merge is observable.
   */
  it("does not merge two packs' identical concept into one line in the in-quiz header", async () => {
    setupGeneratedAdaptiveQuiz();
    (getInProgressAdaptivePracticeSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "study-pack-1",
      noteId: "note-1",
      title: "Structural Engineering Plan",
      focusConcepts: [
        { concept: "Shear Force", sourceStudyPackId: "pack-a", sourceTitle: "Structural Analysis", selectionReason: null },
        { concept: "Shear Force", sourceStudyPackId: "pack-b", sourceTitle: "Reinforced Concrete", selectionReason: null },
      ],
      message: "Focusing on concepts you need to improve.",
      quiz: [
        {
          question: "Where is shear highest?",
          choices: ["A", "B", "C", "D"],
          correctIndex: 0,
          concept: "Shear Force",
          explanation: "At the support.",
        },
      ],
    });

    render(<AdaptivePracticePage />);

    expect(
      await screen.findByText("Focus: Structural Analysis (1) · Reinforced Concrete (1)"),
    ).toBeInTheDocument();
    expect(screen.queryByText("Focus concepts: Shear Force, Shear Force")).not.toBeInTheDocument();
  });

});
