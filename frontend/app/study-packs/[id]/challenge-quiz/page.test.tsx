import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ChallengeQuizPage from "./page";
import { getAuthUser } from "@/lib/auth";
import {
  completeChallengeQuizSession,
  forfeitChallengeQuizSession,
  getInProgressChallengeQuizSession,
  getNote,
  startChallengeQuizSession,
  updateChallengeQuizSessionProgress,
} from "@/lib/api";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};
const paramsMock = { id: "note-1" };

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1/challenge-quiz",
  useParams: () => paramsMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeChallengeQuizSession: jest.fn(),
  forfeitChallengeQuizSession: jest.fn(),
  getInProgressChallengeQuizSession: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  isEmailNotVerifiedError: () => false,
  startChallengeQuizSession: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateChallengeQuizSessionProgress: jest.fn(),
}));

describe("ChallengeQuizPage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    window.localStorage.clear();
    window.sessionStorage.clear();
    Element.prototype.scrollIntoView = jest.fn();
    (getAuthUser as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (getInProgressChallengeQuizSession as jest.Mock).mockReset();
    (completeChallengeQuizSession as jest.Mock).mockReset();
    (forfeitChallengeQuizSession as jest.Mock).mockReset();
    (forfeitChallengeQuizSession as jest.Mock).mockResolvedValue({ message: "Challenge Quiz session forfeited." });
    (startChallengeQuizSession as jest.Mock).mockReset();
    (updateChallengeQuizSessionProgress as jest.Mock).mockReset();
    (updateChallengeQuizSessionProgress as jest.Mock).mockResolvedValue(undefined);
  });

  function setupInProgressChallengeQuiz() {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PREMIUM",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Challenge Note",
      subject: "Biology",
      tags: ["cells"],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: "2026-03-21T10:30:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: ["Concept"],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      difficultySelectionAvailable: true,
      selectedDifficulty: "medium",
      quiz: [
        {
          question: "What powers the cell?",
          choices: ["Mitochondria", "Nucleus", "Golgi apparatus", "Cell wall"],
          correctIndex: 0,
          concept: "Concept",
          explanation: "Explanation",
        },
      ],
      currentQuestionIndex: 0,
      sessionState: {
        selectedChoices: {},
        timerStartedAtEpochSeconds: Math.floor(Date.now() / 1000),
      },
    });
  }

  it("loads note/session once and does not loop initialization calls", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PREMIUM",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Challenge Note",
      subject: "Biology",
      tags: ["cells"],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: "2026-03-21T10:30:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: ["Concept"],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      difficultySelectionAvailable: true,
      selectedDifficulty: "medium",
      quiz: [
        {
          question: "What powers the cell?",
          choices: ["Mitochondria", "Nucleus", "Golgi apparatus", "Cell wall"],
          correctIndex: 0,
          concept: "Concept",
          explanation: "Explanation",
        },
      ],
      currentQuestionIndex: 0,
      sessionState: {
        selectedChoices: {},
        timerStartedAtEpochSeconds: Math.floor(Date.now() / 1000),
      },
    });

    render(<ChallengeQuizPage />);

    await screen.findByText("Challenge Quiz in progress");
    await waitFor(() => {
      expect(getNote).toHaveBeenCalledTimes(1);
      expect(getInProgressChallengeQuizSession).toHaveBeenCalledTimes(1);
    });
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("forfeits the active Challenge Quiz session before leaving", async () => {
    setupInProgressChallengeQuiz();

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Leave Quiz" }));
    expect(screen.getByRole("dialog", { name: "Leave quiz?" })).toBeInTheDocument();

    const leaveButtons = screen.getAllByRole("button", { name: "Leave Quiz" });
    fireEvent.click(leaveButtons[leaveButtons.length - 1]!);

    await waitFor(() => {
      expect(forfeitChallengeQuizSession).toHaveBeenCalledWith("session-1");
    });
    expect(pushMock).toHaveBeenCalledWith("/notes/note-1");
  });

  it("blocks route clicks during an active Challenge Quiz until confirmed", async () => {
    setupInProgressChallengeQuiz();

    render(<ChallengeQuizPage />);

    await screen.findByText("Challenge Quiz in progress");
    const dashboardLink = document.createElement("a");
    dashboardLink.href = "/dashboard";
    dashboardLink.textContent = "Dashboard";
    document.body.appendChild(dashboardLink);

    fireEvent.click(dashboardLink);

    expect(screen.getByRole("dialog", { name: "Leave quiz?" })).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();

    const leaveButtons = screen.getAllByRole("button", { name: "Leave Quiz" });
    fireEvent.click(leaveButtons[leaveButtons.length - 1]!);

    await waitFor(() => {
      expect(forfeitChallengeQuizSession).toHaveBeenCalledWith("session-1");
    });
    expect(pushMock).toHaveBeenCalledWith("/dashboard");

    dashboardLink.remove();
  });

  it("shows the difficulty-selection paywall instead of redirecting immediately", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Challenge Note",
      subject: "Biology",
      tags: ["cells"],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: "2026-03-21T10:30:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: ["Concept"],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: false,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: null,
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 0,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 5,
      difficultySelectionAvailable: false,
      selectedDifficulty: "medium",
      quiz: [],
      currentQuestionIndex: 0,
      sessionState: {},
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Choose Difficulty (Premium)" }));

    expect(await screen.findByText("Difficulty Selection is a Premium feature")).toBeInTheDocument();
  });

  it("locks difficulty controls and prevents duplicate Challenge Quiz starts", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PREMIUM",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Challenge Note",
      subject: "Biology",
      tags: ["cells"],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: "2026-03-21T10:30:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: ["Concept"],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: null,
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 0,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      difficultySelectionAvailable: true,
      selectedDifficulty: "medium",
      quiz: [],
      currentQuestionIndex: 0,
      sessionState: {},
    });
    (startChallengeQuizSession as jest.Mock).mockImplementation(() => new Promise(() => {}));

    render(<ChallengeQuizPage />);

    const hardButton = await screen.findByRole("button", { name: "hard" });
    fireEvent.click(hardButton);
    expect(hardButton).toHaveClass("border-blue-500");

    const startButton = screen.getByRole("button", { name: "Start Challenge Quiz" });
    fireEvent.click(startButton);
    fireEvent.click(startButton);

    await waitFor(() => {
      expect(startChallengeQuizSession).toHaveBeenCalledTimes(1);
    });
    expect(startChallengeQuizSession).toHaveBeenCalledWith("note-1", { difficulty: "hard" });
    expect(screen.getByRole("button", { name: "easy" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "medium" })).toBeDisabled();
    expect(hardButton).toBeDisabled();
    expect(screen.getByRole("button", { name: "Starting..." })).toBeDisabled();
    expect(screen.getByRole("alertdialog", { name: "Generating your quiz..." })).toBeInTheDocument();
    expect(screen.getByText("Creating personalized questions from your notes")).toBeInTheDocument();
    expect(screen.getByText("Please keep this page open")).toBeInTheDocument();
    expect(screen.getByText("Preparing your Challenge Quiz...")).toBeInTheDocument();
  });

  it("shows the first-quiz completion banner after completing the first challenge quiz", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "study-pack-ready" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Challenge Note",
      subject: "Biology",
      tags: ["cells"],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: "2026-03-21T10:30:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: ["Concept"],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 5,
      difficultySelectionAvailable: true,
      selectedDifficulty: "medium",
      quiz: [
        {
          question: "What powers the cell?",
          choices: ["Mitochondria", "Nucleus", "Golgi apparatus", "Cell wall"],
          correctIndex: 0,
          concept: "Concept",
          explanation: "Explanation",
        },
      ],
      currentQuestionIndex: 0,
      sessionState: {
        selectedChoices: {},
        timerStartedAtEpochSeconds: Math.floor(Date.now() / 1000),
      },
    });
    (completeChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      status: "COMPLETED",
      totalQuestions: 1,
      correctAnswers: 1,
      scorePercentage: 100,
      performanceLevel: "Excellent",
      conceptBreakdown: [
        {
          concept: "Concept",
          correctAnswers: 1,
          totalQuestions: 1,
          accuracyPercentage: 100,
        },
      ],
      weakConcepts: ["Concept"],
      durationSeconds: 24,
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:10:00Z",
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Submit Challenge Quiz" }));

    expect(await screen.findByText("Great job! Keep studying and improve your weak areas.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View Weak Concepts" })).toBeInTheDocument();
  });

  it('result screen does not contain a "Note" button', async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PREMIUM",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Challenge Note",
      subject: "Biology",
      tags: [],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: "2026-03-21T10:30:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: [],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      difficultySelectionAvailable: true,
      selectedDifficulty: "medium",
      quiz: [
        {
          question: "What powers the cell?",
          choices: ["Mitochondria", "Nucleus", "Golgi apparatus", "Cell wall"],
          correctIndex: 0,
          concept: "Cell Biology",
          explanation: "Explanation",
        },
      ],
      currentQuestionIndex: 0,
      sessionState: {
        selectedChoices: {},
        timerStartedAtEpochSeconds: Math.floor(Date.now() / 1000),
      },
    });
    (completeChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      status: "COMPLETED",
      totalQuestions: 1,
      correctAnswers: 1,
      scorePercentage: 100,
      performanceLevel: "Excellent",
      conceptBreakdown: [
        { concept: "Cell Biology", correctAnswers: 1, totalQuestions: 1, accuracyPercentage: 100 },
      ],
      weakConcepts: [],
      durationSeconds: 10,
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:01:00Z",
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Submit Challenge Quiz" }));
    await screen.findByText("Challenge Quiz Result");

    expect(screen.queryByRole("button", { name: /^Note$/ })).not.toBeInTheDocument();
  });

  it('result screen shows "← Back to Note" navigation link', async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PREMIUM",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Challenge Note",
      subject: "Biology",
      tags: [],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: "2026-03-21T10:30:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: [],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      difficultySelectionAvailable: true,
      selectedDifficulty: "medium",
      quiz: [
        {
          question: "What powers the cell?",
          choices: ["Mitochondria", "Nucleus", "Golgi apparatus", "Cell wall"],
          correctIndex: 0,
          concept: "Cell Biology",
          explanation: "Explanation",
        },
      ],
      currentQuestionIndex: 0,
      sessionState: {
        selectedChoices: {},
        timerStartedAtEpochSeconds: Math.floor(Date.now() / 1000),
      },
    });
    (completeChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      status: "COMPLETED",
      totalQuestions: 1,
      correctAnswers: 1,
      scorePercentage: 100,
      performanceLevel: "Excellent",
      conceptBreakdown: [
        { concept: "Cell Biology", correctAnswers: 1, totalQuestions: 1, accuracyPercentage: 100 },
      ],
      weakConcepts: [],
      durationSeconds: 10,
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:01:00Z",
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Submit Challenge Quiz" }));
    await screen.findByText("Challenge Quiz Result");

    expect(screen.getByRole("link", { name: /Back to Note/i })).toBeInTheDocument();
  });

  it("opens answer review with selected answer, correct answer, explanation, and concept", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PREMIUM",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Challenge Note",
      subject: "Biology",
      tags: [],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: "2026-03-21T10:30:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: [],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      difficultySelectionAvailable: true,
      selectedDifficulty: "medium",
      quiz: [
        {
          question: "What powers the cell?",
          choices: ["Mitochondria", "Nucleus", "Golgi apparatus", "Cell wall"],
          correctIndex: 0,
          concept: "Cell Biology",
          explanation: "Mitochondria produce ATP for the cell.",
        },
      ],
      currentQuestionIndex: 0,
      sessionState: {
        selectedChoices: {},
        timerStartedAtEpochSeconds: Math.floor(Date.now() / 1000),
      },
    });
    (completeChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      status: "COMPLETED",
      totalQuestions: 1,
      correctAnswers: 0,
      scorePercentage: 0,
      performanceLevel: "Needs Improvement",
      conceptBreakdown: [
        { concept: "Cell Biology", correctAnswers: 0, totalQuestions: 1, accuracyPercentage: 0 },
      ],
      weakConcepts: ["Cell Biology"],
      durationSeconds: 10,
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:01:00Z",
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Submit Challenge Quiz" }));
    await screen.findByText("Challenge Quiz Result");
    fireEvent.click(screen.getByRole("button", { name: "Review Answers" }));

    const review = screen.getByLabelText("Answer review");
    expect(review).toHaveTextContent("What powers the cell?");
    expect(review).toHaveTextContent("Cell Biology");
    expect(review).toHaveTextContent("Nucleus");
    expect(review).toHaveTextContent("Your answer");
    expect(review).toHaveTextContent("Mitochondria");
    expect(review).toHaveTextContent("Correct answer");
    expect(review).toHaveTextContent("Mitochondria produce ATP for the cell.");
  });
});
