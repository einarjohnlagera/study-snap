import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import QuickReviewPage from "./page";
import {
  completeProductOnboarding,
  completeQuickReviewSession,
  answerQuickReviewQuestion,
  forfeitQuickReviewSession,
  generateQuickReviewStudyTip,
  getCollectionGoal,
  getMe,
  getMyStudyPack,
  getPostSessionNextStep,
  getNote,
  getSharedNote,
  getSharedStudyPack,
  saveQuickReviewConfidence,
  startQuickReviewSession,
  trackAnalyticsEvent,
  updateQuickReviewSessionProgress,
  ApiRequestError,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";

const pushMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: jest.fn(),
};
let searchParamsValue = "";
let pathnameValue = "/notes/note-1/quick-review";
let routeIdValue = "note-1";
const searchParamsMock = {
  toString: () => searchParamsValue,
};
const useBottomViewportClaimMock = jest.fn();
const useExamFocusModeMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => pathnameValue,
  useParams: () => ({ id: routeIdValue }),
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

// ⚠️ A jest.mock factory is an ALLOW-LIST. This one listed only useBottomViewportClaim, so when the
// page began importing useExamFocusMode the call site would have received `undefined` — the exact shape
// that left app-shell.test.tsx green in v0.130.0 *because* its poll threw. Both are listed now.
jest.mock("@/components/exam-mode/exam-focus-context", () => ({
  useBottomViewportClaim: (active: boolean) => useBottomViewportClaimMock(active),
  useExamFocusMode: (active: boolean) => useExamFocusModeMock(active),
}));

jest.mock("@/lib/api", () => ({
  completeProductOnboarding: jest.fn(),
  completeQuickReviewSession: jest.fn(),
  answerQuickReviewQuestion: jest.fn(),
  forfeitQuickReviewSession: jest.fn(),
  generateQuickReviewStudyTip: jest.fn(),
  getCollectionGoal: jest.fn(),
  getMe: jest.fn().mockResolvedValue({ learnerLevel: "COLLEGE" }),
  getMyStudyPack: jest.fn(),
  getPostSessionNextStep: jest.fn(),
  getNote: jest.fn(),
  getSharedNote: jest.fn(),
  getSharedStudyPack: jest.fn(),
  recordReviewCommitmentPrompted: jest.fn().mockResolvedValue({ message: "recorded" }),
  saveQuickReviewConfidence: jest.fn(),
  startQuickReviewSession: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateProfileLearnerLevel: jest.fn().mockResolvedValue({ learnerLevel: "COLLEGE" }),
  updateQuickReviewSessionProgress: jest.fn(),
  ApiRequestError: class extends Error {
    code: string | null;
    status: number;
    action = null;
    details = null;

    constructor(message: string, options: { code?: string | null; status: number }) {
      super(message);
      this.code = options.code ?? null;
      this.status = options.status;
    }
  },
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
  quickReviewAvailable: true,
  challengeQuizAvailable: true,
  studyPackDone: true,
};
const baseSession = {
  sessionId: "session-1",
  status: "IN_PROGRESS",
  currentQuestionIndex: 0,
  currentRound: "INITIAL",
  retryCount: 0,
  sessionState: {},
  noteId: "note-1",
  quiz: baseNote.quiz,
  title: "Cells",
  keyConcepts: ["Cell organelles"],
  quizMastered: false,
  quizMasteredAt: null,
  quizCount: 1,
  isOwner: true,
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
    pathnameValue = "/notes/note-1/quick-review";
    routeIdValue = "note-1";
    pushMock.mockReset();
    routerMock.replace.mockReset();
    window.localStorage.clear();
    (getAuthUser as jest.Mock).mockReset();
    (setAuthUser as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (completeQuickReviewSession as jest.Mock).mockReset();
    (answerQuickReviewQuestion as jest.Mock).mockReset();
    (answerQuickReviewQuestion as jest.Mock).mockImplementation(async (_sessionId, request) => ({
      questionIndex: request.questionIndex,
      question: baseNote.quiz[request.questionIndex],
    }));
    (forfeitQuickReviewSession as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (startQuickReviewSession as jest.Mock).mockReset();
    (updateQuickReviewSessionProgress as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    useBottomViewportClaimMock.mockReset();
    useExamFocusModeMock.mockReset();
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
      quickReviewAvailable: true,
      adaptivePracticeAvailable: false,
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue(baseSession);
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    pathnameValue = "/notes/note-1/quick-review";
    routeIdValue = "note-1";
    pushMock.mockReset();
    window.localStorage.clear();
    window.sessionStorage.clear();
    (getAuthUser as jest.Mock).mockReset();
    (setAuthUser as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (completeQuickReviewSession as jest.Mock).mockReset();
    (answerQuickReviewQuestion as jest.Mock).mockReset();
    (answerQuickReviewQuestion as jest.Mock).mockImplementation(async (_sessionId, request) => ({
      questionIndex: request.questionIndex,
      question: baseNote.quiz[request.questionIndex],
    }));
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

  function setupCompleteState(overrides: {
    adaptivePracticeAvailable?: boolean;
    quizMastered?: boolean;
    quizMasteredAt?: string | null;
  } = {}) {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      planType: overrides.adaptivePracticeAvailable ? "PRO" : "FREE",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      adaptivePracticeAvailable: overrides.adaptivePracticeAvailable ?? false,
      quizMastered: overrides.quizMastered ?? false,
      // Default to "mastered just now" so the unlock announcement renders. A test that wants the
      // repeat case passes an older timestamp explicitly.
      quizMasteredAt: overrides.quizMasteredAt === undefined
        ? new Date(Date.now() + 1000).toISOString()
        : overrides.quizMasteredAt,
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue(baseSession);
    (completeQuickReviewSession as jest.Mock).mockResolvedValue(baseResult);
  }

  it.each(["GENERATING", "FAILED"] as const)(
    "starts from an intact quiz while Note lifecycle is %s",
    async (studyPackStatus) => {
      setupCompleteState();
      (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus });

      render(<QuickReviewPage />);

      expect(await screen.findByRole("button", { name: /Mitochondria/i })).toBeInTheDocument();
      expect(startQuickReviewSession).toHaveBeenCalled();
    },
  );

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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    (getMe as jest.Mock).mockResolvedValue({
      id: "user-1",
      learnerLevel: "COLLEGE",
      examDate: null,
      profileType: "STUDENT",
      reviewDays: [],
      reviewCommitmentPromptEligible: true,
      reviewCommitmentPromptCount: 0,
    });
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseResult,
      isFirstCompletedQuiz: false,
      isFirstCompletedSessionEver: false,
    });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Review" }));

    expect(await screen.findByRole("heading", { name: "Your results" })).toBeInTheDocument();
    expect(screen.queryByText(/concept secured/)).not.toBeInTheDocument();
    expect(await screen.findByText("When will you come back?")).toBeInTheDocument();
  });

  it("does not re-announce the unlock when the pack was mastered in an earlier session", async () => {
    // `quizMastered` is sticky, so it stays true forever once earned. Announcing off that alone told
    // a learner they had "earned access" to something they unlocked weeks ago, every repeat perfect
    // score. The previous fixture could not catch this: it returned quizMastered: true with no
    // timestamp, so the first-unlock and repeat cases were indistinguishable.
    setupCompleteState({ quizMastered: true, quizMasteredAt: "2026-01-01T00:00:00Z" });
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseResult,
      correctAnswers: 1,
      scorePercentage: 100,
      weakConcepts: [],
    });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    await screen.findByRole("heading", { name: "Your results" });
    expect(screen.queryByText("🔓 Quiz Unlocked")).not.toBeInTheDocument();
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    expect(await screen.findByText(/That's another session toward this week's target/)).toBeInTheDocument();
    expect(getCollectionGoal).toHaveBeenCalledWith("goal-1");
  });

  it("does not show a Weekly Countdown echo when the learner has no primary Review Set", async () => {
    setupCompleteState();
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE", primaryCollectionId: null });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
      expect(getNote).not.toHaveBeenCalled();
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

  it("hides the app-shell chrome during an active session, and keeps the Leave Quiz exit reachable", async () => {
    // ⚠️ GUARD 6 + GUARD 7 TOGETHER, AND THEY MUST BE ASSERTED TOGETHER ON PURPOSE. Focus mode hides
    // the WHOLE header (with it the notification bell) plus the mobile tab bar, so the only way out of
    // this page while a quiz runs is the in-page control. A surface that gains focus mode without an
    // exit traps the learner, which is worse than a visible bell.
    //
    // ⚠️ The kickoff for this release claimed Quick Review had NO such exit and widened the whole item
    // on that basis. It was wrong — the audit grepped for BackLink and links, not for the
    // onClick={requestLeave} button that is the real running-state exit. This test is what makes that
    // claim impossible to get wrong again.
    setupCompleteState();

    render(<QuickReviewPage />);

    await screen.findByTestId("quick-review-top-bar");
    expect(useExamFocusModeMock).toHaveBeenLastCalledWith(true);
    expect(screen.getByRole("button", { name: "Leave Quiz" })).toBeInTheDocument();
  });

  it("restores the app-shell chrome once the quiz is finished", async () => {
    // ⚠️ The other half of guard 6, and it drives the quiz to completion rather than asserting on the
    // initial render: a focus mode that only ever turns ON would leave the learner on the results
    // screen with no header, no bell and no navigation. Answer, finish, then assert it flipped back.
    setupCompleteState();
    render(<QuickReviewPage />);

    await screen.findByTestId("quick-review-top-bar");
    expect(useExamFocusModeMock).toHaveBeenLastCalledWith(true);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(useExamFocusModeMock).toHaveBeenLastCalledWith(false);
  });

  it('result screen shows "Note" navigation link', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Review" }));
    await screen.findByText("Quick Review Complete");

    const reviewNotesLink = screen.getByRole("link", { name: "Review the Notes" });
    expect(reviewNotesLink).toHaveAttribute("href", "/notes/note-1");
  });

  it('hides "Review the Notes" on a perfect score, which has nothing to go back and study', async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    (startQuickReviewSession as jest.Mock).mockResolvedValue({ ...baseSession, quiz: [], quizCount: 0 });

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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    // Perfect score → showChallengeGuidedCta = true → "Take Another Challenge" appears
    expect(screen.getByRole("link", { name: "Take Another Challenge" })).toBeInTheDocument();
  });

  it("opens answer review with selected answer, correct answer, explanation, and concept", async () => {
    setupCompleteState();
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
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

  it("uses Review the Notes as the primary next step after a non-mastered Quick Review", async () => {
    // Replaced an assertion that Retry Quick Review was primary alongside an Adaptive Practice
    // upsell. Quick Review no longer routes into Adaptive Practice at all (EXAM_MODES.md), and the
    // retry CTA was both redundant — already declined one screen earlier — and mislabelled, since
    // it restarted the whole Quick Review rather than the missed questions.
    setupCompleteState({ adaptivePracticeAvailable: false });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.getByRole("button", { name: "Review the Notes" })).toHaveClass("bg-primary");
    expect(screen.queryByRole("button", { name: "Retry Quick Review" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Get More Adaptive Practice" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Practice Weak Areas" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Review Answers" })).toHaveClass("border");
  });

  it("shows upgrade nudge on result screen when adaptive practice is not available", async () => {
    setupCompleteState({ adaptivePracticeAvailable: false });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.getByText("Ready to improve your weak areas?")).toBeInTheDocument();
  });

  it("hides upgrade nudge on result screen when adaptive practice is available (Pro user)", async () => {
    setupCompleteState({ adaptivePracticeAvailable: true });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await screen.findByText("Quick Review Complete");

    expect(screen.queryByText("Ready to improve your weak areas?")).not.toBeInTheDocument();
  });
});

describe("QuickReviewPage redacted answer flow", () => {
  const redactedQuiz = [{
    ...baseNote.quiz[0],
    correctIndex: null,
    correctIndices: null,
    explanation: null,
    workingSolution: null,
    acceptableAnswers: null,
  }];
  const secondQuestion = {
    question: "Which structure contains DNA?",
    choices: ["Cell wall", "Nucleus", "Cytoplasm", "Membrane"],
    correctIndex: 1,
    concept: "Cell organelles",
    explanation: "The nucleus contains the cell's DNA.",
  };
  const twoQuestionQuiz = [baseNote.quiz[0], secondQuestion];
  const redactedSecondQuestion = {
    ...secondQuestion,
    correctIndex: null,
    correctIndices: null,
    explanation: null,
    workingSolution: null,
    acceptableAnswers: null,
  };
  const redact = (question: typeof baseNote.quiz[number]) => ({
    ...question,
    correctIndex: null,
    correctIndices: null,
    explanation: null,
    workingSolution: null,
    acceptableAnswers: null,
  });

  beforeEach(() => {
    jest.clearAllMocks();
    window.sessionStorage.clear();
    routerMock.replace.mockReset();
    pathnameValue = "/notes/note-1/quick-review";
    routeIdValue = "note-1";
    searchParamsValue = "";
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: { plan: "FREE" } });
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: "COLLEGE" });
    (getPostSessionNextStep as jest.Mock).mockRejectedValue(new Error("not available"));
    (startQuickReviewSession as jest.Mock).mockResolvedValue({ ...baseSession, quiz: redactedQuiz });
    (getNote as jest.Mock).mockResolvedValue(baseNote);
    (getSharedNote as jest.Mock).mockResolvedValue(baseNote);
    (getSharedStudyPack as jest.Mock).mockResolvedValue({ ...baseNote, noteId: baseNote.id });
    (answerQuickReviewQuestion as jest.Mock).mockImplementation(async (_sessionId, request) => ({
      questionIndex: request.questionIndex,
      question: baseNote.quiz[request.questionIndex],
    }));
    (updateQuickReviewSessionProgress as jest.Mock).mockResolvedValue({});
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({ ...baseResult, correctAnswers: 1 });
    (generateQuickReviewStudyTip as jest.Mock).mockResolvedValue({ studyTip: null });
  });

  it("merges the per-answer reveal into an asymmetric redacted fixture", async () => {
    render(<QuickReviewPage />);

    expect(await screen.findByText("What is the powerhouse of the cell?")).toBeInTheDocument();
    expect(screen.queryByText("Mitochondria produce ATP.")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Mitochondria/i }));

    expect(await screen.findByText("Mitochondria produce ATP.")).toBeInTheDocument();
    expect(answerQuickReviewQuestion).toHaveBeenCalledWith("session-1", {
      questionIndex: 0,
      retryCount: 0,
      selectedChoiceIndex: 0,
    });
    expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled();
  });

  it("keeps a failed answer retryable without locking or revealing locally", async () => {
    (answerQuickReviewQuestion as jest.Mock)
      .mockRejectedValueOnce(new Error("Connection lost. Try again."))
      .mockResolvedValueOnce({ questionIndex: 0, question: baseNote.quiz[0] });
    render(<QuickReviewPage />);

    const choice = await screen.findByRole("button", { name: /Mitochondria/i });
    fireEvent.click(choice);
    expect(await screen.findByText("Connection lost. Try again.")).toBeInTheDocument();
    expect(screen.queryByText("Mitochondria produce ATP.")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeDisabled();

    fireEvent.click(choice);
    expect(await screen.findByText("Mitochondria produce ATP.")).toBeInTheDocument();
    expect(answerQuickReviewQuestion).toHaveBeenCalledTimes(2);
  });

  it.each([
    ["QUICK_REVIEW_NOT_AVAILABLE"],
    ["NOTE_STUDY_PACK_NOT_READY"],
  ])("maps %s to the Study Pack generation message", async (code) => {
    (startQuickReviewSession as jest.Mock).mockRejectedValue(new ApiRequestError(
      "Unavailable",
      { code, status: 409 },
    ));

    render(<QuickReviewPage />);

    expect(await screen.findByText("Generate a Study Pack first.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
  });

  it.each([403, 404])("maps start status %s to the not-found state", async (status) => {
    (startQuickReviewSession as jest.Mock).mockRejectedValue(new ApiRequestError(
      "Unavailable",
      { status },
    ));

    render(<QuickReviewPage />);

    expect(await screen.findByRole("heading", { name: "Note not found" })).toBeInTheDocument();
  });

  it("makes a transient start failure retryable", async () => {
    (startQuickReviewSession as jest.Mock)
      .mockRejectedValueOnce(new Error("Temporary outage"))
      .mockResolvedValueOnce({ ...baseSession, quiz: redactedQuiz });
    render(<QuickReviewPage />);

    expect(await screen.findByText("Temporary outage")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("What is the powerhouse of the cell?")).toBeInTheDocument();
    expect(startQuickReviewSession).toHaveBeenCalledTimes(2);
  });

  it("treats a deploy-skew response without quiz as retryable", async () => {
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      quiz: undefined,
    });

    render(<QuickReviewPage />);

    expect(await screen.findByText("Quick Review could not load. Please try again.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("restarts after the server reports a regeneration-stale answer", async () => {
    (answerQuickReviewQuestion as jest.Mock).mockRejectedValueOnce(new ApiRequestError(
      "Restart required",
      { code: "QUICK_REVIEW_SESSION_STALE", status: 409 },
    ));
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));

    await waitFor(() => expect(startQuickReviewSession).toHaveBeenCalledTimes(2));
    expect(screen.queryByText("Restart required")).not.toBeInTheDocument();
    expect(screen.queryByText("Mitochondria produce ATP.")).not.toBeInTheDocument();
  });

  it("restarts after a regeneration-stale completion instead of marking it tracked", async () => {
    (completeQuickReviewSession as jest.Mock).mockRejectedValueOnce(new ApiRequestError(
      "Restart required", { code: "QUICK_REVIEW_SESSION_STALE", status: 409 },
    ));
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await screen.findByText("Mitochondria produce ATP.");
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await waitFor(() => expect(startQuickReviewSession).toHaveBeenCalledTimes(2));
  });

  it("does not fetch any Note or shared Study Pack before completion", async () => {
    (completeQuickReviewSession as jest.Mock).mockReturnValue(new Promise(() => {}));
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await screen.findByText("Mitochondria produce ATP.");
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    await waitFor(() => expect(completeQuickReviewSession).toHaveBeenCalled());

    expect(getNote).not.toHaveBeenCalled();
    expect(getSharedNote).not.toHaveBeenCalled();
    expect(getSharedStudyPack).not.toHaveBeenCalled();
  });

  it.each([
    ["explicit shared link", "source=shared"],
    ["Dashboard-shaped link", ""],
    ["digest-shaped link", "source=due-concepts-digest"],
  ])("starts a recipient session from the %s", async (_label, query) => {
    searchParamsValue = query;
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      quiz: redactedQuiz,
      isOwner: false,
    });

    render(<QuickReviewPage />);

    expect(await screen.findByTestId("quick-review-top-bar")).toBeInTheDocument();
    expect(startQuickReviewSession).toHaveBeenCalledWith("note-1");
    expect(getNote).not.toHaveBeenCalled();
    expect(getSharedNote).not.toHaveBeenCalled();
    expect(getSharedStudyPack).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: /Mitochondria/i }));
    await screen.findByText("Mitochondria produce ATP.");
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    expect((await screen.findAllByRole("link", { name: "Note" })).map((link) => link.getAttribute("href")))
      .toContain("/shared/notes/note-1");
  });

  it.each([
    ["owner", true],
    ["recipient", false],
  ])("restores mid-INITIAL position, selections, and cumulative reveal for an %s", async (_caller, isOwner) => {
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      currentQuestionIndex: 1,
      quiz: [baseNote.quiz[0], redactedSecondQuestion],
      quizCount: 2,
      isOwner,
      sessionState: {
        selectedChoices: { "0": 0 },
        roundSelections: { "0": 0 },
        activeQuestionIndexes: [0, 1],
      },
    });
    (answerQuickReviewQuestion as jest.Mock).mockResolvedValue({
      questionIndex: 1,
      question: secondQuestion,
    });
    const firstMount = render(<QuickReviewPage />);
    await screen.findByText("Which structure contains DNA?");
    firstMount.unmount();
    render(<QuickReviewPage />);

    expect(await screen.findByText("Which structure contains DNA?")).toBeInTheDocument();
    expect(startQuickReviewSession).toHaveBeenCalledTimes(2);
    fireEvent.click(screen.getByRole("button", { name: /Nucleus/i }));
    await screen.findByText("The nucleus contains the cell's DNA.");
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    await waitFor(() => expect(completeQuickReviewSession).toHaveBeenCalledWith(
      "session-1",
      expect.objectContaining({ correctAnswers: 2, totalQuestions: 2, retryCount: 0 }),
    ));
  });

  it.each([
    ["owner", true],
    ["recipient", false],
  ])("restores mid-RETRY state and cumulative reveal for an %s", async (_caller, isOwner) => {
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      currentQuestionIndex: 0,
      currentRound: "RETRY",
      retryCount: 1,
      quiz: twoQuestionQuiz,
      quizCount: 2,
      isOwner,
      sessionState: {
        selectedChoices: { "0": 0, "1": 0 },
        roundSelections: {},
        retryQuestionIndexes: [1],
        activeQuestionIndexes: [1],
      },
    });
    (answerQuickReviewQuestion as jest.Mock).mockResolvedValue({
      questionIndex: 1,
      question: secondQuestion,
    });
    const firstMount = render(<QuickReviewPage />);
    await screen.findByText("Which structure contains DNA?");
    firstMount.unmount();
    render(<QuickReviewPage />);

    expect(await screen.findByText("Which structure contains DNA?")).toBeInTheDocument();
    expect(startQuickReviewSession).toHaveBeenCalledTimes(2);
    fireEvent.click(screen.getByRole("button", { name: /Nucleus/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Finish Retry" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Finish Retry" }));

    await waitFor(() => expect(completeQuickReviewSession).toHaveBeenCalledWith(
      "session-1",
      expect.objectContaining({ correctAnswers: 2, totalQuestions: 2, retryCount: 1 }),
    ));
  });

  it.each([
    ["owner", true],
    ["recipient", false],
  ])("resumes after an in-flight answer lands during an %s remount", async (_caller, isOwner) => {
    let finishAnswer!: (value: { questionIndex: number; question: typeof baseNote.quiz[0] }) => void;
    (startQuickReviewSession as jest.Mock).mockResolvedValueOnce({
      ...baseSession,
      quiz: redactedQuiz,
      isOwner,
    });
    (answerQuickReviewQuestion as jest.Mock).mockReturnValueOnce(new Promise((resolve) => {
      finishAnswer = resolve;
    }));
    const firstMount = render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await waitFor(() => expect(answerQuickReviewQuestion).toHaveBeenCalledTimes(1));
    firstMount.unmount();
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      quiz: baseNote.quiz,
      isOwner,
      sessionState: {
        selectedChoices: { "0": 0 },
        roundSelections: { "0": 0 },
        activeQuestionIndexes: [0],
      },
    });
    finishAnswer({ questionIndex: 0, question: baseNote.quiz[0] });
    render(<QuickReviewPage />);

    expect(await screen.findByText("Mitochondria produce ATP.")).toBeInTheDocument();
    expect(startQuickReviewSession).toHaveBeenCalledTimes(2);
    expect(answerQuickReviewQuestion).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["owner", true],
    ["recipient", false],
  ])("keeps the server session across logout and fresh-login remount for an %s", async (_caller, isOwner) => {
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      quiz: baseNote.quiz,
      isOwner,
      sessionState: {
        selectedChoices: { "0": 0 },
        roundSelections: { "0": 0 },
        activeQuestionIndexes: [0],
      },
    });
    const firstLogin = render(<QuickReviewPage />);
    expect(await screen.findByText("Mitochondria produce ATP.")).toBeInTheDocument();
    firstLogin.unmount();
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });

    render(<QuickReviewPage />);

    expect(await screen.findByText("Mitochondria produce ATP.")).toBeInTheDocument();
    expect(startQuickReviewSession).toHaveBeenCalledTimes(2);
    expect(forfeitQuickReviewSession).not.toHaveBeenCalled();
  });

  it("uses isOwner for the owner Note link", async () => {
    render(<QuickReviewPage />);
    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    await screen.findByText("Mitochondria produce ATP.");
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));
    expect((await screen.findAllByRole("link", { name: "Note" })).map((link) => link.getAttribute("href")))
      .toContain("/notes/note-1");
  });

  it("redirects a legacy Study Pack id and starts again only after the route id changes", async () => {
    pathnameValue = "/study-packs/pack-1/quick-review";
    routeIdValue = "pack-1";
    (startQuickReviewSession as jest.Mock).mockRejectedValueOnce(new ApiRequestError(
      "Not found",
      { status: 404 },
    ));
    (getMyStudyPack as jest.Mock).mockResolvedValue({ noteId: "note-1" });
    const view = render(<QuickReviewPage />);

    await waitFor(() => expect(routerMock.replace).toHaveBeenCalledWith("/notes/note-1/quick-review"));
    expect(startQuickReviewSession).toHaveBeenCalledTimes(1);

    pathnameValue = "/notes/note-1/quick-review";
    routeIdValue = "note-1";
    view.rerender(<QuickReviewPage />);
    await waitFor(() => expect(startQuickReviewSession).toHaveBeenCalledTimes(2));
    view.rerender(<QuickReviewPage />);
    expect(startQuickReviewSession).toHaveBeenCalledTimes(2);
  });

  it("does not restart on a re-render but does start for a changed note id", async () => {
    const view = render(<QuickReviewPage />);
    await screen.findByText("What is the powerhouse of the cell?");

    view.rerender(<QuickReviewPage />);
    expect(startQuickReviewSession).toHaveBeenCalledTimes(1);

    routeIdValue = "note-2";
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      noteId: "note-2",
      quiz: redactedQuiz,
    });
    view.rerender(<QuickReviewPage />);
    await waitFor(() => expect(startQuickReviewSession).toHaveBeenLastCalledWith("note-2"));
    expect(startQuickReviewSession).toHaveBeenCalledTimes(2);
  });

  it.each([
    ["owner", true],
    ["recipient", false],
  ])("resumes a day-later Dashboard-shaped URL for the %s", async (_caller, isOwner) => {
    pathnameValue = "/notes/note-1/quick-review";
    searchParamsValue = "";
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      isOwner,
      currentQuestionIndex: 1,
      quiz: [twoQuestionQuiz[0], redact(twoQuestionQuiz[1])],
      quizCount: 2,
      sessionState: {
        selectedChoices: { "0": 0 },
        roundSelections: { "0": 0 },
        activeQuestionIndexes: [0, 1],
      },
    });

    render(<QuickReviewPage />);

    expect(await screen.findByText(secondQuestion.question)).toBeInTheDocument();
    expect(startQuickReviewSession).toHaveBeenCalledWith("note-1");
    expect(getNote).not.toHaveBeenCalled();
    expect(getSharedNote).not.toHaveBeenCalled();
    expect(getSharedStudyPack).not.toHaveBeenCalled();
  });

  it("keeps MULTI_SELECT local until Submit returns the reveal", async () => {
    const redactedMultiSelect = {
      question: "Which functions have the listed derivatives?",
      choices: ["sin(x)", "x²", "x", "ln(x)"],
      questionFormat: "MULTI_SELECT",
      concept: "Derivatives",
      correctIndices: null,
      explanation: null,
    };
    const revealedMultiSelect = {
      ...redactedMultiSelect,
      correctIndices: [0, 2],
      explanation: "Sine and x have the listed derivatives.",
    };
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      quiz: [redactedMultiSelect],
    });
    (answerQuickReviewQuestion as jest.Mock).mockResolvedValue({
      questionIndex: 0,
      question: revealedMultiSelect,
    });
    render(<QuickReviewPage />);

    await screen.findByText("Which functions have the listed derivatives?");
    fireEvent.click(screen.getByRole("button", { name: /sin\(x\)$/i }));
    fireEvent.click(screen.getByRole("button", { name: /\. x$/i }));
    expect(answerQuickReviewQuestion).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Submit" }));

    expect(await screen.findByText("Sine and x have the listed derivatives.")).toBeInTheDocument();
    expect(answerQuickReviewQuestion).toHaveBeenCalledWith("session-1", {
      questionIndex: 0,
      retryCount: 0,
      selectedMultiChoiceIndices: [0, 2],
    });
    expect(screen.getByRole("button", { name: "Finish Quick Review" })).toBeEnabled();
  });

  it("retries only failed MATCHING items and reveals the group after every response succeeds", async () => {
    const matchingFullQuiz = [
      {
        question: "First item",
        choices: ["Alpha", "Beta", "Gamma", "Delta"],
        correctIndex: 0,
        concept: "Matching",
        explanation: "First explanation",
        questionFormat: "MATCHING",
        questionGroup: "group-1",
      },
      {
        question: "Second item",
        choices: ["Alpha", "Beta", "Gamma", "Delta"],
        correctIndex: 1,
        concept: "Matching",
        explanation: "Second explanation",
        questionFormat: "MATCHING",
        questionGroup: "group-1",
      },
    ];
    const matchingRedactedQuiz = matchingFullQuiz.map((question) => ({
      ...question,
      correctIndex: null,
      explanation: null,
    }));
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      quiz: matchingRedactedQuiz,
      quizCount: 2,
    });
    let secondAttempts = 0;
    (answerQuickReviewQuestion as jest.Mock).mockImplementation(async (_sessionId, request) => {
      if (request.questionIndex === 1 && secondAttempts++ === 0) {
        throw new Error("Second item failed.");
      }
      return { questionIndex: request.questionIndex, question: matchingFullQuiz[request.questionIndex] };
    });
    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Item 1 choice A: Alpha" }));
    fireEvent.click(screen.getByRole("button", { name: "Item 2 choice B: Beta" }));
    expect(await screen.findByText("Second item failed.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: /Item 1 choice A: Alpha Correct/ })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Retry answer" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Next" })).toBeEnabled());
    expect(screen.getByRole("button", { name: /Item 1 choice A: Alpha Correct/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Item 2 choice B: Beta Correct/ })).toBeInTheDocument();
    expect((answerQuickReviewQuestion as jest.Mock).mock.calls.filter(([, request]) => request.questionIndex === 0)).toHaveLength(1);
    expect((answerQuickReviewQuestion as jest.Mock).mock.calls.filter(([, request]) => request.questionIndex === 1)).toHaveLength(2);
  });

  it("resumes mid-RETRY into an already-confirmed MATCHING group without re-answering", async () => {
    const matchingFullQuiz = [
      {
        question: "First item",
        choices: ["Alpha", "Beta", "Gamma", "Delta"],
        correctIndex: 0,
        concept: "Matching",
        explanation: "First explanation",
        questionFormat: "MATCHING",
        questionGroup: "group-1",
      },
      {
        question: "Second item",
        choices: ["Alpha", "Beta", "Gamma", "Delta"],
        correctIndex: 1,
        concept: "Matching",
        explanation: "Second explanation",
        questionFormat: "MATCHING",
        questionGroup: "group-1",
      },
    ];
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      ...baseSession,
      quiz: matchingFullQuiz,
      quizCount: 2,
      currentRound: "RETRY",
      retryCount: 1,
      currentQuestionIndex: 0,
      sessionState: {
        selectedChoices: { "0": 0, "1": 1 },
        roundSelections: { "0": 0, "1": 1 },
        retryQuestionIndexes: [0, 1],
        activeQuestionIndexes: [0, 1],
      },
    });
    render(<QuickReviewPage />);

    expect(await screen.findByRole("button", { name: /Item 1 choice A: Alpha Correct/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Item 2 choice B: Beta Correct/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next" })).toBeEnabled();
    expect(answerQuickReviewQuestion).not.toHaveBeenCalled();
  });
});
