import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
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

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  function getModeCard(label: string): HTMLButtonElement {
    const card = screen.getByText(label).closest("button");
    expect(card).not.toBeNull();
    return card as HTMLButtonElement;
  }

  function setupInProgressChallengeQuiz(mode: "challenge" | "board_exam" = "challenge") {
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
      status: "IN_PROGRESS",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      difficultySelectionAvailable: true,
      mode,
      selectedDifficulty: mode === "board_exam" ? "mixed" : "medium",
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

  function setupChallengePrestart(difficultySelectionAvailable = true) {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: difficultySelectionAvailable ? "PREMIUM" : "FREE",
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
      difficultySelectionAvailable,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: null,
      status: null,
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 0,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: difficultySelectionAvailable ? 50 : 5,
      difficultySelectionAvailable,
      mode: "challenge",
      selectedDifficulty: "medium",
      quiz: [],
      currentQuestionIndex: 0,
      sessionState: {},
    });
  }

  function setupBoardExamSession(options: {
    currentQuestionIndex?: number;
    selectedChoices?: Record<string, number>;
    timerStartedAtEpochSeconds?: number;
  } = {}) {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PREMIUM",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Board Exam Note",
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
      keyConcepts: ["Cell Biology"],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      studyPackId: "sp-1",
      title: "Board Exam Note",
      totalQuestions: 2,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      difficultySelectionAvailable: true,
      mode: "board_exam",
      selectedDifficulty: "mixed",
      quiz: [
        {
          question: "What powers the cell?",
          choices: ["Mitochondria", "Nucleus", "Golgi apparatus", "Cell wall"],
          correctIndex: 0,
          concept: "Cell Biology",
          explanation: "Mitochondria produce ATP.",
        },
        {
          question: "What protects the plant cell?",
          choices: ["Cell wall", "Mitochondria", "Nucleus", "Ribosome"],
          correctIndex: 0,
          concept: "Cell Structure",
          explanation: "The cell wall protects plant cells.",
        },
      ],
      currentQuestionIndex: options.currentQuestionIndex ?? 0,
      sessionState: {
        selectedChoices: options.selectedChoices ?? {},
        timerStartedAtEpochSeconds: options.timerStartedAtEpochSeconds ?? Math.floor(Date.now() / 1000),
      },
    });
  }

  it("shows a mode-selection entry screen and does not auto-start the quiz", async () => {
    setupChallengePrestart(true);

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    expect(getModeCard("Practice Mode")).toBeInTheDocument();
    expect(getModeCard("Board Exam Mode")).toBeInTheDocument();
    expect(screen.getByText("Both modes use your standard Challenge Quiz attempt for this note.")).toBeInTheDocument();
    expect(startChallengeQuizSession).not.toHaveBeenCalled();
  });

  it("shows Premium difficulty selection only after Practice Mode is chosen", async () => {
    setupChallengePrestart(true);

    render(<ChallengeQuizPage />);

    await screen.findByRole("heading", { name: "Choose your quiz mode" });
    fireEvent.click(getModeCard("Practice Mode"));

    expect(await screen.findByRole("heading", { name: "Practice challenge setup" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "easy" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "medium" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "hard" })).toBeInTheDocument();
    expect(screen.getByText("Premium lets you choose the level before you start.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start Challenge Quiz" })).toBeInTheDocument();
  });

  it("starts Practice Mode immediately for Free users without showing difficulty selection", async () => {
    setupChallengePrestart(false);
    (startChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "GENERATING",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 0,
      timeLimitSeconds: 600,
      usedThisMonth: 1,
      monthlyLimit: 5,
      difficultySelectionAvailable: false,
      mode: "challenge",
      selectedDifficulty: "medium",
      quiz: [],
      currentQuestionIndex: 0,
      sessionState: {},
    });

    render(<ChallengeQuizPage />);

    await screen.findByRole("heading", { name: "Choose your quiz mode" });
    fireEvent.click(getModeCard("Practice Mode"));

    await waitFor(() => {
      expect(startChallengeQuizSession).toHaveBeenCalledWith("note-1", { mode: "challenge" });
    });
    expect(screen.queryByRole("heading", { name: "Practice challenge setup" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "easy" })).not.toBeInTheDocument();
  });

  it("shows Board Exam Mode as a distinct setup flow without difficulty controls", async () => {
    setupChallengePrestart(true);

    render(<ChallengeQuizPage />);

    await screen.findByRole("heading", { name: "Choose your quiz mode" });
    fireEvent.click(getModeCard("Board Exam Mode"));

    expect(await screen.findByRole("heading", { name: "Board Exam setup" })).toBeInTheDocument();
    expect(screen.getByText("Simulate a focused exam session for Challenge Note. Results appear only after submission, and leaving may forfeit the attempt.")).toBeInTheDocument();
    expect(screen.getByText("12 questions with mixed difficulty.")).toBeInTheDocument();
    expect(screen.getByText("Mixed and internally controlled for exam simulation.")).toBeInTheDocument();
    expect(screen.getByText("Board Exam Mode rules")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "easy" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "medium" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "hard" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start Exam" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
  });

  it("keeps Board Exam Mode available to Free users without a difficulty step", async () => {
    setupChallengePrestart(false);

    render(<ChallengeQuizPage />);

    await screen.findByRole("heading", { name: "Choose your quiz mode" });
    fireEvent.click(getModeCard("Board Exam Mode"));

    expect(await screen.findByRole("heading", { name: "Board Exam setup" })).toBeInTheDocument();
    expect(screen.getByText("12 questions with mixed difficulty.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start Exam" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "easy" })).not.toBeInTheDocument();
  });

  it("starts Board Exam Mode with the standard Challenge Quiz request for Free users", async () => {
    setupChallengePrestart(false);
    (startChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "GENERATING",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 0,
      timeLimitSeconds: 600,
      usedThisMonth: 1,
      monthlyLimit: 5,
      difficultySelectionAvailable: false,
      mode: "board_exam",
      selectedDifficulty: "mixed",
      quiz: [],
      currentQuestionIndex: 0,
      sessionState: {},
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Board Exam Mode/i }));
    fireEvent.click(screen.getByRole("button", { name: "Start Exam" }));

    await waitFor(() => {
      expect(startChallengeQuizSession).toHaveBeenCalledWith("note-1", { mode: "board_exam" });
    });
  });

  it("keeps Board Exam answers neutral and allows question navigation", async () => {
    setupBoardExamSession();

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));

    expect(screen.queryByText(/Correct/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Incorrect/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Mitochondria produce ATP/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Go to question 2 (unanswered)" }));

    expect(screen.getByText("What protects the plant cell?")).toBeInTheDocument();
    expect(screen.getByText("1 answered")).toBeInTheDocument();
  });

  it("resumes Board Exam timer, question index, and answer state from session state", async () => {
    const nowSpy = jest.spyOn(Date, "now").mockReturnValue(1_720_000_120_000);
    setupBoardExamSession({
      currentQuestionIndex: 1,
      selectedChoices: { "0": 0 },
      timerStartedAtEpochSeconds: 1_720_000_000,
    });

    render(<ChallengeQuizPage />);

    expect(await screen.findByText("What protects the plant cell?")).toBeInTheDocument();
    expect(screen.getByLabelText("Exam timer")).toHaveTextContent("08:00");
    expect(screen.getByText("1 answered")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Go to question 1 (answered)" })).toBeInTheDocument();

    nowSpy.mockRestore();
  });

  it("auto-submits Board Exam when the timer expires", async () => {
    const nowSpy = jest.spyOn(Date, "now").mockReturnValue(1_720_000_601_000);
    setupBoardExamSession({
      selectedChoices: { "0": 0 },
      timerStartedAtEpochSeconds: 1_720_000_000,
    });
    (completeChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      status: "COMPLETED",
      totalQuestions: 2,
      correctAnswers: 1,
      scorePercentage: 50,
      performanceLevel: "Fair",
      conceptBreakdown: [
        { concept: "Cell Biology", correctAnswers: 1, totalQuestions: 1, accuracyPercentage: 100 },
        { concept: "Cell Structure", correctAnswers: 0, totalQuestions: 1, accuracyPercentage: 0 },
      ],
      weakConcepts: ["Cell Structure"],
      durationSeconds: 600,
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:10:00Z",
    });

    render(<ChallengeQuizPage />);

    await waitFor(() => {
      expect(completeChallengeQuizSession).toHaveBeenCalledWith("session-1", {
        correctAnswers: 1,
        totalQuestions: 2,
        durationSeconds: 600,
      });
    });
    expect(await screen.findByText("Board Exam Result")).toBeInTheDocument();
    expect(screen.getByText("Time ran out. Your answers were submitted automatically.")).toBeInTheDocument();

    nowSpy.mockRestore();
  });

  it("shows the Board Exam timer warning state when time is running low", async () => {
    const nowSpy = jest.spyOn(Date, "now").mockReturnValue(1_720_000_421_000);
    setupBoardExamSession({
      timerStartedAtEpochSeconds: 1_720_000_000,
    });

    render(<ChallengeQuizPage />);

    expect(await screen.findByTestId("board-exam-timer")).toHaveAttribute("data-timer-state", "warning");
    expect(screen.getByText("Less than 3 minutes remaining.")).toBeInTheDocument();

    nowSpy.mockRestore();
  });

  it("shows the Board Exam timer urgent state in the final minute", async () => {
    const nowSpy = jest.spyOn(Date, "now").mockReturnValue(1_720_000_560_000);
    setupBoardExamSession({
      timerStartedAtEpochSeconds: 1_720_000_000,
    });

    render(<ChallengeQuizPage />);

    expect(await screen.findByTestId("board-exam-timer")).toHaveAttribute("data-timer-state", "urgent");
    expect(screen.getByText("Final minute.")).toBeInTheDocument();

    nowSpy.mockRestore();
  });

  it("does not retry timeout auto-submit on every tick after a timeout submission failure", async () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(1_720_000_601_000));
    setupBoardExamSession({
      selectedChoices: { "0": 0 },
      timerStartedAtEpochSeconds: 1_720_000_000,
    });
    (completeChallengeQuizSession as jest.Mock).mockRejectedValue(new Error("Could not save Challenge Quiz results."));

    render(<ChallengeQuizPage />);

    await waitFor(() => {
      expect(completeChallengeQuizSession).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => {
      expect(screen.getByText("Could not save Challenge Quiz results.")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "Submit Exam" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Mitochondria/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Go to question 2 (unanswered)" })).toBeDisabled();

    await act(async () => {
      jest.advanceTimersByTime(5_000);
    });

    expect(completeChallengeQuizSession).toHaveBeenCalledTimes(1);
    jest.useRealTimers();
  });

  it("does not double-submit when manual submit is already in flight as the timer expires", async () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(1_720_000_598_000));
    setupBoardExamSession({
      currentQuestionIndex: 1,
      selectedChoices: { "0": 0 },
      timerStartedAtEpochSeconds: 1_720_000_000,
    });
    (completeChallengeQuizSession as jest.Mock).mockImplementation(() => new Promise(() => {}));

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Submit Exam" }));

    await waitFor(() => {
      expect(completeChallengeQuizSession).toHaveBeenCalledTimes(1);
    });

    await act(async () => {
      jest.advanceTimersByTime(5_000);
    });

    expect(completeChallengeQuizSession).toHaveBeenCalledTimes(1);
    jest.useRealTimers();
  });

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
      status: "IN_PROGRESS",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      difficultySelectionAvailable: true,
      mode: "challenge",
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

  it("locks difficulty controls and prevents duplicate Challenge Quiz starts", async () => {
    setupChallengePrestart(true);
    (startChallengeQuizSession as jest.Mock).mockImplementation(() => new Promise(() => {}));

    render(<ChallengeQuizPage />);

    await screen.findByRole("heading", { name: "Choose your quiz mode" });
    fireEvent.click(getModeCard("Practice Mode"));
    const hardButton = await screen.findByRole("button", { name: "hard" });
    fireEvent.click(hardButton);
    expect(hardButton).toHaveClass("border-blue-500");

    const startButton = screen.getByRole("button", { name: "Start Challenge Quiz" });
    fireEvent.click(startButton);
    fireEvent.click(startButton);

    await waitFor(() => {
      expect(startChallengeQuizSession).toHaveBeenCalledTimes(1);
    });
    expect(startChallengeQuizSession).toHaveBeenCalledWith("note-1", { difficulty: "hard", mode: "challenge" });
    expect(screen.getByRole("button", { name: "easy" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "medium" })).toBeDisabled();
    expect(hardButton).toBeDisabled();
    expect(screen.getByRole("button", { name: "Starting..." })).toBeDisabled();
    expect(screen.getByRole("alertdialog", { name: "Generating your quiz..." })).toBeInTheDocument();
    expect(screen.getByText("Creating personalized questions from your notes")).toBeInTheDocument();
    expect(screen.getByText("Please keep this page open")).toBeInTheDocument();
    expect(screen.getByText("Preparing your practice challenge...")).toBeInTheDocument();
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
      mode: "challenge",
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
      mode: "board_exam",
      selectedDifficulty: "mixed",
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
    fireEvent.click(screen.getByRole("button", { name: "Submit Exam" }));
    await screen.findByText("Board Exam Result");

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
      mode: "board_exam",
      selectedDifficulty: "mixed",
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
    fireEvent.click(screen.getByRole("button", { name: "Submit Exam" }));
    await screen.findByText("Board Exam Result");

    expect(screen.getByRole("link", { name: /Back to Note/i })).toBeInTheDocument();
    expect(screen.getByText("No weak concepts were identified in this exam. Review your answers or take another Board Exam when ready.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Take Another Board Exam" })).toHaveClass("bg-blue-600");
    expect(screen.getByRole("button", { name: "Review Answers" })).toHaveClass("border");
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
      mode: "board_exam",
      selectedDifficulty: "mixed",
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
    fireEvent.click(screen.getByRole("button", { name: "Submit Exam" }));
    await screen.findByText("Board Exam Result");
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
