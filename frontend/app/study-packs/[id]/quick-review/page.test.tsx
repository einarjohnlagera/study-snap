import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import QuickReviewPage from "./page";
import {
  completeProductOnboarding,
  completeQuickReviewSession,
  forfeitQuickReviewSession,
  generateQuickReviewStudyTip,
  getNote,
  saveQuickReviewConfidence,
  startQuickReviewSession,
  updateQuickReviewSessionProgress,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";

const pushMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: jest.fn(),
};
const searchParamsMock = {
  toString: () => "",
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1/quick-review",
  useParams: () => ({ id: "note-1" }),
  useSearchParams: () => searchParamsMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeProductOnboarding: jest.fn(),
  completeQuickReviewSession: jest.fn(),
  forfeitQuickReviewSession: jest.fn(),
  generateQuickReviewStudyTip: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  saveQuickReviewConfidence: jest.fn(),
  startQuickReviewSession: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateQuickReviewSessionProgress: jest.fn(),
}));

// Shared fixture helpers
const baseNote = {
  id: "note-1",
  title: "Cells",
  studyPackStatus: "STUDY_PACK_READY",
  quiz: [
    {
      question: "What is the powerhouse of the cell?",
      choices: ["Mitochondria", "Nucleus", "Ribosome", "Cell wall"],
      correctIndex: 0,
      concept: "Cell organelles",
      explanation: "Mitochondria produce ATP.",
    },
  ],
  adaptivePracticeAvailable: false,
  challengeQuizAvailable: true,
};
const baseSession = {
  sessionId: "session-1",
  status: "IN_PROGRESS",
  currentQuestionIndex: 0,
  currentRound: "INITIAL",
  retryCount: 0,
  sessionState: {},
};
const baseResult = {
  id: "session-1",
  studyPackId: "study-pack-1",
  totalQuestions: 1,
  correctAnswers: 0,
  scorePercentage: 0,
  retryCount: 0,
  durationSeconds: 12,
  confidenceLevel: null,
  weakConcepts: ["Cell organelles"],
  createdAt: "2026-03-21T10:00:00Z",
  completedAt: "2026-03-21T10:01:00Z",
};

describe("QuickReviewPage first-study onboarding", () => {
  beforeEach(() => {
    pushMock.mockReset();
    routerMock.replace.mockReset();
    window.localStorage.clear();
    (getAuthUser as jest.Mock).mockReset();
    (setAuthUser as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (completeQuickReviewSession as jest.Mock).mockReset();
    (forfeitQuickReviewSession as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (startQuickReviewSession as jest.Mock).mockReset();
    (updateQuickReviewSessionProgress as jest.Mock).mockReset();
  });

  it("shows the completion modal after the first quick review and routes to dashboard", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "study-pack-ready" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: null,
      displayName: "Note",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Cells",
      studyPackStatus: "STUDY_PACK_READY",
      quiz: [
        {
          question: "What is the powerhouse of the cell?",
          choices: ["Mitochondria", "Nucleus", "Ribosome", "Cell wall"],
          correctIndex: 0,
          answer: "Mitochondria",
          explanation: "Mitochondria produce ATP.",
        },
      ],
      adaptivePracticeAvailable: false,
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      currentQuestionIndex: 0,
      currentRound: "INITIAL",
      retryCount: 0,
      sessionState: {},
    });
    (updateQuickReviewSessionProgress as jest.Mock).mockResolvedValue({});
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({
      id: "session-1",
      studyPackId: "study-pack-1",
      totalQuestions: 1,
      correctAnswers: 1,
      scorePercentage: 100,
      retryCount: 0,
      durationSeconds: 12,
      confidenceLevel: null,
      weakConcepts: [],
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:01:00Z",
    });
    (completeProductOnboarding as jest.Mock).mockResolvedValue({
      displayName: "Note",
      profileType: null,
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T10:05:00Z",
    });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    expect(await screen.findByText("You’re all set!")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Go to Dashboard" }));

    await waitFor(() => {
      expect(completeProductOnboarding).toHaveBeenCalledWith(false);
    });
    expect(pushMock).toHaveBeenCalledWith("/dashboard");
  });
});

describe("QuickReviewPage post-quiz UX", () => {
  beforeEach(() => {
    pushMock.mockReset();
    window.localStorage.clear();
    (getAuthUser as jest.Mock).mockReset();
    (setAuthUser as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (completeQuickReviewSession as jest.Mock).mockReset();
    (forfeitQuickReviewSession as jest.Mock).mockReset();
    (forfeitQuickReviewSession as jest.Mock).mockResolvedValue({ message: "Quick Review session forfeited." });
    (generateQuickReviewStudyTip as jest.Mock).mockReset();
    (generateQuickReviewStudyTip as jest.Mock).mockResolvedValue({ studyTip: null });
    (getNote as jest.Mock).mockReset();
    (startQuickReviewSession as jest.Mock).mockReset();
    (saveQuickReviewConfidence as jest.Mock).mockReset();
    (updateQuickReviewSessionProgress as jest.Mock).mockResolvedValue(undefined);
  });

  function setupCompleteState(overrides: { adaptivePracticeAvailable?: boolean } = {}) {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      adaptivePracticeAvailable: overrides.adaptivePracticeAvailable ?? false,
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue(baseSession);
    (completeQuickReviewSession as jest.Mock).mockResolvedValue(baseResult);
  }

  it('result screen does not contain a "Note" button', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    const noteButton = screen.queryByRole("button", { name: /^Note$/ });
    expect(noteButton).not.toBeInTheDocument();
  });

  it("loads Quick Review once and does not loop initialization calls", async () => {
    setupCompleteState();

    render(<QuickReviewPage />);

    await screen.findByText("Quick Review in progress");
    await waitFor(() => {
      expect(getNote).toHaveBeenCalledTimes(1);
      expect(startQuickReviewSession).toHaveBeenCalledTimes(1);
    });
    expect(routerMock.replace).not.toHaveBeenCalled();
  });

  it('result screen shows "← Back to Note" navigation link', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.getByRole("link", { name: /Back to Note/i })).toBeInTheDocument();
  });

  it("shows confidence badge after selecting HIGH confidence", async () => {
    setupCompleteState();
    (saveQuickReviewConfidence as jest.Mock).mockResolvedValue({
      ...baseResult,
      confidenceLevel: "HIGH",
    });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    fireEvent.click(await screen.findByRole("button", { name: "Very confident" }));
    expect(await screen.findByText("🟢 Confident")).toBeInTheDocument();
  });

  it("forfeits the active Quick Review session before leaving", async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Leave Quiz" }));
    expect(screen.getByRole("dialog", { name: "Leave quiz?" })).toBeInTheDocument();

    const leaveButtons = screen.getAllByRole("button", { name: "Leave Quiz" });
    fireEvent.click(leaveButtons[leaveButtons.length - 1]!);

    await waitFor(() => {
      expect(forfeitQuickReviewSession).toHaveBeenCalledWith("session-1");
    });
    expect(pushMock).toHaveBeenCalledWith("/notes/note-1");
  });

  it("shows confidence badge after selecting MEDIUM confidence", async () => {
    setupCompleteState();
    (saveQuickReviewConfidence as jest.Mock).mockResolvedValue({
      ...baseResult,
      confidenceLevel: "MEDIUM",
    });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    fireEvent.click(await screen.findByRole("button", { name: "Somewhat confident" }));
    expect(await screen.findByText("🟡 Improving")).toBeInTheDocument();
  });

  it("shows confidence badge after selecting LOW confidence", async () => {
    setupCompleteState();
    (saveQuickReviewConfidence as jest.Mock).mockResolvedValue({
      ...baseResult,
      confidenceLevel: "LOW",
    });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    fireEvent.click(await screen.findByRole("button", { name: "Not confident" }));
    expect(await screen.findByText("🔴 Needs Practice")).toBeInTheDocument();
  });

  it("hides confidence option buttons after selection", async () => {
    setupCompleteState();
    (saveQuickReviewConfidence as jest.Mock).mockResolvedValue({
      ...baseResult,
      confidenceLevel: "HIGH",
    });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    fireEvent.click(await screen.findByRole("button", { name: "Very confident" }));
    await screen.findByText("🟢 Confident");

    expect(screen.queryByRole("button", { name: "Very confident" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Somewhat confident" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Not confident" })).not.toBeInTheDocument();
  });

  it('shows "Start Challenge Quiz" CTA on perfect score (no weak concepts)', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    // Answer correctly (Mitochondria is correctIndex=0)
    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    // Perfect score → showChallengeGuidedCta = true → "Start Challenge Quiz" appears
    expect(screen.getByRole("link", { name: "Start Challenge Quiz" })).toBeInTheDocument();
  });

  it("opens answer review with selected answer, correct answer, explanation, and concept", async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Review" }));
    await screen.findByText("Quick Review Complete");
    fireEvent.click(screen.getByRole("button", { name: "Review Answers" }));

    const review = screen.getByLabelText("Answer review");
    expect(review).toHaveTextContent("What is the powerhouse of the cell?");
    expect(review).toHaveTextContent("Cell organelles");
    expect(review).toHaveTextContent("Nucleus");
    expect(review).toHaveTextContent("Your answer");
    expect(review).toHaveTextContent("Mitochondria");
    expect(review).toHaveTextContent("Correct answer");
    expect(review).toHaveTextContent("Mitochondria produce ATP.");
  });
});
