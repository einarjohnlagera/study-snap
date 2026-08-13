import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import QuickReviewPage from "./page";
import {
  completeProductOnboarding,
  completeQuickReviewSession,
  forfeitQuickReviewSession,
  generateQuickReviewStudyTip,
  getCollectionGoal,
  getMe,
  getPostSessionNextStep,
  getNote,
  saveQuickReviewConfidence,
  startQuickReviewSession,
  trackAnalyticsEvent,
  updateQuickReviewSessionProgress,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";

const pushMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: jest.fn(),
};
let searchParamsValue = "";
const searchParamsMock = {
  toString: () => searchParamsValue,
};
const useBottomViewportClaimMock = jest.fn();

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

jest.mock("@/hooks/use-billing-usage-summary", () => ({
  useBillingUsageSummary: jest.fn(),
}));

jest.mock("@/components/exam-mode/exam-focus-context", () => ({
  useBottomViewportClaim: (active: boolean) => useBottomViewportClaimMock(active),
}));

jest.mock("@/lib/api", () => ({
  completeProductOnboarding: jest.fn(),
  completeQuickReviewSession: jest.fn(),
  forfeitQuickReviewSession: jest.fn(),
  generateQuickReviewStudyTip: jest.fn(),
  getCollectionGoal: jest.fn(),
  getMe: jest.fn().mockResolvedValue({ learnerLevel: "COLLEGE" }),
  getMyStudyPack: jest.fn(),
  getPostSessionNextStep: jest.fn(),
  getNote: jest.fn(),
  saveQuickReviewConfidence: jest.fn(),
  startQuickReviewSession: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateProfileLearnerLevel: jest.fn().mockResolvedValue({ learnerLevel: "COLLEGE" }),
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
  keyConcepts: ["Cell organelles"],
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
    searchParamsValue = "";
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
    (trackAnalyticsEvent as jest.Mock).mockReset();
    useBottomViewportClaimMock.mockReset();
    (getPostSessionNextStep as jest.Mock).mockReset();
    (getPostSessionNextStep as jest.Mock).mockRejectedValue(new Error("next-step unavailable"));
    (useBillingUsageSummary as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { adaptivePracticePerMonth: 3 },
        usage: { adaptivePracticeUsed: 0 },
        remaining: { adaptivePracticeRemaining: 3 },
      },
    });
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
    searchParamsValue = "";
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
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (updateQuickReviewSessionProgress as jest.Mock).mockResolvedValue(undefined);
    (getPostSessionNextStep as jest.Mock).mockReset();
    (getPostSessionNextStep as jest.Mock).mockRejectedValue(new Error("next-step unavailable"));
    (getMe as jest.Mock).mockReset();
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE" });
    (getCollectionGoal as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { adaptivePracticePerMonth: 3 },
        usage: { adaptivePracticeUsed: 0 },
        remaining: { adaptivePracticeRemaining: 3 },
      },
    });
  });

  function setupCompleteState(overrides: { adaptivePracticeAvailable?: boolean; quizMastered?: boolean } = {}) {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      adaptivePracticeAvailable: overrides.adaptivePracticeAvailable ?? false,
      quizMastered: overrides.quizMastered ?? false,
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue(baseSession);
    (completeQuickReviewSession as jest.Mock).mockResolvedValue(baseResult);
  }

  it("tracks a due-concepts digest landing and its first submitted answer", async () => {
    searchParamsValue = "source=due-concepts-digest";
    setupCompleteState();

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({
        eventType: "DUE_CONCEPTS_DIGEST_LANDED",
      }));
      expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({
        eventType: "DUE_CONCEPTS_DIGEST_FIRST_ANSWER_SUBMITTED",
      }));
    });
  });

  it('result screen does not contain a "Note" button', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    const noteButton = screen.queryByRole("button", { name: /^Note$/ });
    expect(noteButton).not.toBeInTheDocument();
  });

  it("links only weak concepts that have a Key Concepts explanation", async () => {
    setupCompleteState();
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseResult,
      weakConcepts: ["  CELL ORGANELLES  ", "Unmapped concept"],
    });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.getByRole("link", { name: /CELL ORGANELLES/i })).toHaveAttribute(
      "href",
      "/notes/note-1?tab=key-concepts#concept-cell-organelles",
    );
    expect(screen.getByText("Unmapped concept").closest("a")).toBeNull();
  });

  it("shows the open loop for a first incomplete quiz and tracks it once", async () => {
    setupCompleteState();
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseResult,
      isFirstCompletedQuiz: true,
      isFirstCompletedSessionEver: true,
    });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Review" }));

    expect(await screen.findByRole("heading", { name: "0 of 1 concept secured" })).toBeInTheDocument();
    expect(screen.getByText("How did your first quiz go?")).toBeInTheDocument();
    expect(screen.queryByText("Was this quiz helpful?")).not.toBeInTheDocument();
    expect(screen.getByText("The rest are best reviewed tomorrow — you're not done yet.")).toBeInTheDocument();
    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "QUICK_REVIEW_OPEN_LOOP_SHOWN",
        entityId: "session-1",
        metadata: { securedCount: 0, totalConcepts: 1 },
      });
    });
    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(2);
  });

  it("keeps the standard header for a returning learner", async () => {
    setupCompleteState();
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseResult,
      isFirstCompletedQuiz: false,
      isFirstCompletedSessionEver: false,
    });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Review" }));

    expect(await screen.findByRole("heading", { name: "Your results" })).toBeInTheDocument();
    expect(screen.queryByText(/concept secured/)).not.toBeInTheDocument();
  });

  it("keeps the standard header for a perfect first quiz", async () => {
    setupCompleteState({ quizMastered: true });
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseResult,
      correctAnswers: 1,
      scorePercentage: 100,
      weakConcepts: [],
      isFirstCompletedQuiz: true,
      isFirstCompletedSessionEver: true,
    });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    expect(await screen.findByRole("heading", { name: "Your results" })).toBeInTheDocument();
    const unlockAnnouncement = screen.getByText("🔓 Quiz Unlocked").parentElement;
    expect(unlockAnnouncement).not.toBeNull();
    expect(within(unlockAnnouncement as HTMLElement).queryByRole("link")).not.toBeInTheDocument();
    expect(within(unlockAnnouncement as HTMLElement).queryByRole("button")).not.toBeInTheDocument();
    expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({ eventType: "QUICK_REVIEW_COMPLETED" }));
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(expect.objectContaining({ eventType: "QUICK_REVIEW_OPEN_LOOP_SHOWN" }));
  });

  it("fetches and renders the server-resolved next step after completion", async () => {
    setupCompleteState({ adaptivePracticeAvailable: true });
    (getPostSessionNextStep as jest.Mock).mockResolvedValue({
      type: "REVIEW_PACK",
      studyPackId: "study-pack-1",
      noteId: "note-1",
      title: "Cells",
      message: "Strong Quick Review. Step up with a Challenge, with targeted review still available below.",
      actionLabel: "Take a Challenge",
      actionHref: "/notes/note-1/challenge-quiz",
      concepts: ["Cell organelles"],
      adaptivePracticeAvailable: true,
      adaptivePracticeRemaining: 2,
      goalNudge: null,
      secondaryAction: {
        actionLabel: "Practice Weak Concepts",
        actionHref: "/notes/note-1/adaptive-practice",
        adaptivePractice: true,
      },
    });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    expect(await screen.findByText("Recommended next step")).toBeInTheDocument();
    expect(screen.getByTestId("quick-review-next-step-guidance")).toHaveAttribute("aria-label", "What to do next");
    expect(screen.getByText("Cell organelles")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Take a Challenge" })).toHaveAttribute(
      "href",
      "/notes/note-1/challenge-quiz",
    );
    expect(screen.getByRole("link", { name: "Practice Weak Concepts" })).toHaveAttribute(
      "href",
      "/notes/note-1/adaptive-practice",
    );
    expect(getPostSessionNextStep).toHaveBeenCalledWith("study-pack-1");
  });

  it("echoes Weekly Countdown pacing when the learner has a primary Review Set with a target date", async () => {
    setupCompleteState();
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE", primaryCollectionId: "goal-1" });
    (getCollectionGoal as jest.Mock).mockResolvedValue({ weeksRemaining: 2 });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    expect(await screen.findByText(/That's another session toward this week's target/)).toBeInTheDocument();
    expect(getCollectionGoal).toHaveBeenCalledWith("goal-1");
  });

  it("does not show a Weekly Countdown echo when the learner has no primary Review Set", async () => {
    setupCompleteState();
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE", primaryCollectionId: null });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(getCollectionGoal).not.toHaveBeenCalled();
    expect(screen.queryByText(/That's another session toward this week's target/)).not.toBeInTheDocument();
    expect(screen.queryByTestId("quick-review-next-step-guidance")).not.toBeInTheDocument();
    expect(screen.queryByTestId("quick-review-companion-guidance")).not.toBeInTheDocument();
  });

  it("shows the primary Review Set's Companion excerpt when it has Common Mistakes content", async () => {
    setupCompleteState();
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE", primaryCollectionId: "goal-1" });
    (getCollectionGoal as jest.Mock).mockResolvedValue({
      weeksRemaining: null,
      companion: { commonMistakes: "Watch out for mixing up mitosis and meiosis.", studyStrategy: null },
    });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    expect(await screen.findByText(/Watch out for mixing up mitosis and meiosis\./)).toBeInTheDocument();
    expect(screen.getByTestId("quick-review-companion-guidance")).toHaveAttribute("aria-label", "Companion guidance");
    expect(screen.getByText(/Common Mistakes/)).toBeInTheDocument();
    expect(getCollectionGoal).toHaveBeenCalledWith("goal-1");
  });

  it("links a twice-missed Quick Review concept to the resolved Primary Review Set Companion", async () => {
    setupCompleteState();
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PLUS",
        limits: { adaptivePracticePerMonth: 10 },
        usage: { adaptivePracticeUsed: 0 },
        remaining: { adaptivePracticeRemaining: 10 },
      },
    });
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseResult,
      twiceMissedConcepts: ["Cell organelles"],
    });
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE", primaryCollectionId: "goal-1" });
    (getCollectionGoal as jest.Mock).mockResolvedValue({
      weeksRemaining: null,
      companion: { overview: "Review how organelles work together." },
    });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    expect(await screen.findByRole("link", { name: "Ask Companion about this" })).toHaveAttribute(
      "href",
      "/collections/goal-1?askCompanionDraft=Can+you+explain+Cell+organelles+a+different+way%3F",
    );
  });

  it("does not show a Companion excerpt when the primary Review Set has no Companion content", async () => {
    setupCompleteState();
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE", primaryCollectionId: "goal-1" });
    (getCollectionGoal as jest.Mock).mockResolvedValue({ weeksRemaining: null, companion: null });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.queryByText(/Common Mistakes/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Study Strategy/)).not.toBeInTheDocument();
  });

  it("loads Quick Review once and does not loop initialization calls", async () => {
    setupCompleteState();

    render(<QuickReviewPage />);

    expect(await screen.findByTestId("quick-review-top-bar")).toBeInTheDocument();
    await waitFor(() => {
      expect(getNote).toHaveBeenCalledTimes(1);
      expect(startQuickReviewSession).toHaveBeenCalledTimes(1);
    });
    expect(routerMock.replace).not.toHaveBeenCalled();
  });

  it("uses a compact top bar and sticky action bar during active Quick Review", async () => {
    setupCompleteState();

    render(<QuickReviewPage />);

    const topBar = await screen.findByTestId("quick-review-top-bar");
    const actionBar = screen.getByTestId("quick-review-action-bar");

    expect(topBar).toHaveClass("sticky");
    expect(topBar).toHaveTextContent("Quick Review");
    expect(topBar).toHaveTextContent("1 / 1");
    expect(actionBar).toHaveClass("fixed");
    expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeInTheDocument();
    expect(useBottomViewportClaimMock).toHaveBeenLastCalledWith(true);
  });

  it('result screen shows "Note" navigation link', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.getAllByRole("link", { name: "Note" }).length).toBeGreaterThan(0);
    expect(screen.getByText("Was this quiz helpful?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Give Feedback" })).toBeInTheDocument();
  });

  it('offers "Review the Notes" on the result screen after a miss', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Review" }));
    await screen.findByText("Quick Review Complete");

    const reviewNotesLink = screen.getByRole("link", { name: "Review the Notes" });
    expect(reviewNotesLink).toHaveAttribute("href", "/notes/note-1");
    expect(screen.getByText(/Study the notes again/)).toBeInTheDocument();
  });

  it('hides "Review the Notes" on a perfect score, which has nothing to go back and study', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.queryByRole("link", { name: "Review the Notes" })).not.toBeInTheDocument();
  });

  it('keeps "Finish Review" completing the session on the incorrect-answers screen', async () => {
    // Guards the placement decision: this button is the only route to the result screen, which
    // carries the Challenge promotion and the first-session commitment prompt. Replacing it here
    // would make a learner who missed a question skip both, and both feed dated checkpoints.
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    expect(await screen.findByRole("button", { name: "Finish Review" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Review the Notes" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Finish Review" }));

    await waitFor(() => expect(completeQuickReviewSession).toHaveBeenCalled());
  });

  it("uses Note as a text link in empty quiz edge states", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      quiz: [],
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue(baseSession);

    render(<QuickReviewPage />);

    await screen.findByText("No quiz questions available");

    expect(screen.getAllByRole("link", { name: "Note" }).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Back to Note" })).not.toBeInTheDocument();
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

  it('shows "Take Another Challenge" CTA on perfect score (no weak concepts)', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    // Answer correctly (Mitochondria is correctIndex=0)
    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    // Perfect score → showChallengeGuidedCta = true → "Take Another Challenge" appears
    expect(screen.getByRole("link", { name: "Take Another Challenge" })).toBeInTheDocument();
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
    expect(review).toHaveTextContent("Your Answer");
    expect(review).toHaveTextContent("Mitochondria");
    expect(review).toHaveTextContent("Correct Answer");
    expect(review).toHaveTextContent("Mitochondria produce ATP.");
  });

  it("uses Retry Quick Review as the primary next step when weak practice is locked", async () => {
    setupCompleteState({ adaptivePracticeAvailable: false });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.getByRole("button", { name: "Retry Quick Review" })).toHaveClass("bg-primary");
    expect(screen.getByRole("button", { name: "Get More Adaptive Practice" })).toHaveClass("border");
    expect(screen.getByRole("button", { name: "Review Answers" })).toHaveClass("border");
  });

  it("shows upgrade nudge on result screen when adaptive practice is not available", async () => {
    setupCompleteState({ adaptivePracticeAvailable: false });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.getByText("Ready to improve your weak areas?")).toBeInTheDocument();
  });

  it("hides upgrade nudge on result screen when adaptive practice is available (Pro user)", async () => {
    setupCompleteState({ adaptivePracticeAvailable: true });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.queryByText("Ready to improve your weak areas?")).not.toBeInTheDocument();
  });
});
