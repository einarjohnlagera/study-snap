import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import ChallengeQuizPage from "./page";
import { getAuthUser } from "@/lib/auth";
import {
  completeChallengeQuizSession,
  forfeitChallengeQuizSession,
  getCollection,
  getInProgressChallengeQuizSession,
  getNote,
  getPostSessionNextStep,
  listNotes,
  startChallengeQuizSession,
  updateChallengeQuizSessionProgress,
} from "@/lib/api";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};
const paramsMock = { id: "note-1" };
let searchParamsMock = new URLSearchParams();
let mobileViewport = false;
const mediaQueryListeners = new Set<(event: MediaQueryListEvent) => void>();

function setMobileViewport(matches: boolean) {
  mobileViewport = matches;
  mediaQueryListeners.forEach((listener) => listener({ matches } as MediaQueryListEvent));
}

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1/challenge-quiz",
  useParams: () => paramsMock,
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
  completeChallengeQuizSession: jest.fn(),
  forfeitChallengeQuizSession: jest.fn(),
  getCollection: jest.fn(),
  getInProgressChallengeQuizSession: jest.fn(),
  getMe: jest.fn().mockResolvedValue({ learnerLevel: "COLLEGE" }),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  getPostSessionNextStep: jest.fn(),
  isEmailNotVerifiedError: () => false,
  listNotes: jest.fn(),
  startChallengeQuizSession: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateChallengeQuizSessionProgress: jest.fn(),
  updateProfileLearnerLevel: jest.fn().mockResolvedValue({ learnerLevel: "COLLEGE" }),
}));

describe("ChallengeQuizPage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    searchParamsMock = new URLSearchParams();
    mobileViewport = false;
    mediaQueryListeners.clear();
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: jest.fn().mockImplementation((query: string) => ({
        matches: query === "(max-width: 639px)" ? mobileViewport : false,
        media: query,
        onchange: null,
        addEventListener: (_eventName: string, listener: (event: MediaQueryListEvent) => void) => {
          mediaQueryListeners.add(listener);
        },
        removeEventListener: (_eventName: string, listener: (event: MediaQueryListEvent) => void) => {
          mediaQueryListeners.delete(listener);
        },
        addListener: (listener: (event: MediaQueryListEvent) => void) => {
          mediaQueryListeners.add(listener);
        },
        removeListener: (listener: (event: MediaQueryListEvent) => void) => {
          mediaQueryListeners.delete(listener);
        },
        dispatchEvent: jest.fn(),
      })),
    });
    window.localStorage.clear();
    window.sessionStorage.clear();
    Element.prototype.scrollIntoView = jest.fn();
    (getAuthUser as jest.Mock).mockReset();
    (getCollection as jest.Mock).mockReset();
    (getCollection as jest.Mock).mockResolvedValue({ id: "collection-1", items: [] });
    (getNote as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockResolvedValue([]);
    (getInProgressChallengeQuizSession as jest.Mock).mockReset();
    (completeChallengeQuizSession as jest.Mock).mockReset();
    (forfeitChallengeQuizSession as jest.Mock).mockReset();
    (forfeitChallengeQuizSession as jest.Mock).mockResolvedValue({ message: "Challenge Quiz session forfeited." });
    (startChallengeQuizSession as jest.Mock).mockReset();
    (updateChallengeQuizSessionProgress as jest.Mock).mockReset();
    (updateChallengeQuizSessionProgress as jest.Mock).mockResolvedValue(undefined);
    (getPostSessionNextStep as jest.Mock).mockReset();
    (getPostSessionNextStep as jest.Mock).mockRejectedValue(new Error("next-step unavailable"));
    (useBillingUsageSummary as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PRO",
        limits: { adaptivePracticePerMonth: 30 },
        usage: { adaptivePracticeUsed: 0 },
        remaining: { adaptivePracticeRemaining: 30 },
      },
    });
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  async function getModeCard(label: string): Promise<HTMLButtonElement> {
    const buttons = await screen.findAllByRole("button");
    const card = buttons.find((button) => within(button).queryByText(label));
    expect(card).toBeDefined();
    return card as HTMLButtonElement;
  }

  function setupInProgressChallengeQuiz(mode: "challenge" | "board_exam" = "challenge") {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "PRO",
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

  function setupChallengePrestart(
    difficultySelectionAvailable = true,
    profileType: "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PROFESSIONAL" = "STUDENT",
    planType?: "FREE" | "PLUS" | "PRO",
    options: {
      usedThisMonth?: number;
      monthlyLimit?: number;
      boardExamUsedThisMonth?: number;
      boardExamMonthlyLimit?: number;
    } = {},
  ) {
    const resolvedPlanType = planType ?? (difficultySelectionAvailable ? "PRO" : "FREE");
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      profileType,
      planType: resolvedPlanType,
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
      usedThisMonth: options.usedThisMonth ?? 0,
      monthlyLimit: options.monthlyLimit ?? (difficultySelectionAvailable ? 50 : 5),
      boardExamUsedThisMonth: options.boardExamUsedThisMonth ?? 0,
      boardExamMonthlyLimit: options.boardExamMonthlyLimit ?? 10,
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
      id: "user-1",
      planType: "PRO",
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

  it("starts students on the shared mode-selection screen with Challenge Quiz emphasized", async () => {
    setupChallengePrestart(true, "STUDENT");

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    expect(await getModeCard("Challenge Quiz")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("Recommended")).toBeInTheDocument();
    expect(startChallengeQuizSession).not.toHaveBeenCalled();
  });

  it("shows Long Exam Mode card (not Board Exam Mode) for STUDENT", async () => {
    setupChallengePrestart(true, "STUDENT");

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    expect(await getModeCard("Long Exam Mode")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Board Exam Mode/ })).not.toBeInTheDocument();
    expect(screen.queryByText("Coming Soon")).not.toBeInTheDocument();
  });

  it("shows the cross-profile escape-hatch line for STUDENT", async () => {
    setupChallengePrestart(true, "STUDENT");

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    expect(screen.getByText(/Preparing for boards/)).toBeInTheDocument();
  });

  it("routes students to Long Exam after clicking Long Exam Mode", async () => {
    setupChallengePrestart(true, "STUDENT");

    render(<ChallengeQuizPage />);

    fireEvent.click(await getModeCard("Long Exam Mode"));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/long-exam");
  });

  it("shows Interview Practice for Professional Pro users and routes to the note session", async () => {
    setupChallengePrestart(true, "PROFESSIONAL", "PRO");

    render(<ChallengeQuizPage />);

    expect(await getModeCard("Certification Review")).toBeInTheDocument();
    expect(await getModeCard("Full Practice Exam")).toBeInTheDocument();
    fireEvent.click(await getModeCard("Interview Practice"));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/interview-practice");
  });

  it("routes Professional non-Pro users to Interview Practice setup instead of opening the paywall at mode selection", async () => {
    setupChallengePrestart(false, "PROFESSIONAL", "PLUS");

    render(<ChallengeQuizPage />);

    const interviewPracticeCard = await getModeCard("Interview Practice");
    expect(within(interviewPracticeCard).getByText("Pro")).toBeInTheDocument();
    fireEvent.click(interviewPracticeCard);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/interview-practice");
    expect(screen.queryByRole("dialog", { name: "Unlock Interview Practice" })).not.toBeInTheDocument();
  });

  it("keeps Note Detail Challenge Quiz entry on the shared mode-selection screen for students even when an in-progress session exists", async () => {
    searchParamsMock = new URLSearchParams("entry=mode-selection");
    setupInProgressChallengeQuiz("challenge");

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    expect(await getModeCard("Challenge Quiz")).toHaveAttribute("aria-pressed", "true");
    expect(screen.queryByRole("heading", { name: "Challenge Quiz Setup" })).not.toBeInTheDocument();
    expect(screen.queryByTestId("challenge-quiz-top-bar")).not.toBeInTheDocument();
    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz", { scroll: false });
  });

  it("lets students choose either setup from the shared mode-selection screen", async () => {
    setupChallengePrestart(true, "STUDENT");

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    fireEvent.click(await getModeCard("Challenge Quiz"));

    expect(await screen.findByRole("heading", { name: "Challenge Quiz Setup" })).toBeInTheDocument();
  });

  it("shows Pro difficulty selection after students choose Challenge Quiz", async () => {
    setupChallengePrestart(true, "STUDENT");

    render(<ChallengeQuizPage />);

    fireEvent.click(await getModeCard("Challenge Quiz"));
    expect(await screen.findByRole("heading", { name: "Challenge Quiz Setup" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "easy" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "medium" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "hard" })).toBeInTheDocument();
    expect(screen.getByText("Pro lets you choose the level before you start.")).toBeInTheDocument();
    expect(screen.getByText("10 minutes. Timer runs until submission or expiration.")).toBeInTheDocument();
    expect(screen.getByText("Counts toward your monthly quiz limit.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Choose another mode" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start Quiz" })).toBeInTheDocument();
  });

  it("shows the free Challenge Quiz prescreen before starting", async () => {
    setupChallengePrestart(false, "STUDENT");

    render(<ChallengeQuizPage />);

    fireEvent.click(await getModeCard("Challenge Quiz"));
    expect(await screen.findByRole("heading", { name: "Challenge Quiz Setup" })).toBeInTheDocument();
    expect(screen.getByText("Recommended difficulty: Medium")).toBeInTheDocument();
    expect(screen.getByText("Choose difficulty (Pro)")).toBeInTheDocument();
    expect(screen.getByText("10 minutes. Timer runs until submission or expiration.")).toBeInTheDocument();
    expect(screen.getByText("Recommended based on your recent performance.")).toBeInTheDocument();
    expect(screen.getByText("Counts toward your monthly quiz limit.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "easy" })).not.toBeInTheDocument();
    expect(startChallengeQuizSession).not.toHaveBeenCalled();
  });

  it("starts Challenge Quiz from the free prescreen", async () => {
    setupChallengePrestart(false, "STUDENT");
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

    fireEvent.click(await getModeCard("Challenge Quiz"));
    fireEvent.click(await screen.findByRole("button", { name: "Start Quiz" }));

    await waitFor(() => {
      expect(startChallengeQuizSession).toHaveBeenCalledWith("note-1", { mode: "challenge" });
    });
  });

  it("starts Exam Reviewers on the shared mode-selection screen with Board Exam emphasized", async () => {
    setupChallengePrestart(true, "BOARD_EXAM", "PRO");

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    expect(await getModeCard("Board Exam Mode")).toHaveAttribute("aria-pressed", "true");
    expect(await getModeCard("Challenge Quiz")).toHaveAttribute("aria-pressed", "false");
    expect(screen.getAllByText("Recommended")).not.toHaveLength(0);
    expect(screen.getByText(/Board Exam Mode emphasizes exam simulation/)).toBeInTheDocument();
  });

  it("keeps Note Detail Challenge Quiz entry on the shared mode-selection screen for Exam Reviewers even when an in-progress session exists", async () => {
    searchParamsMock = new URLSearchParams("entry=mode-selection");
    setupInProgressChallengeQuiz("board_exam");
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      profileType: "BOARD_EXAM",
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    expect(await getModeCard("Board Exam Mode")).toHaveAttribute("aria-pressed", "true");
    expect(await getModeCard("Challenge Quiz")).toHaveAttribute("aria-pressed", "false");
    expect(screen.queryByRole("heading", { name: "Board Exam Setup" })).not.toBeInTheDocument();
    expect(screen.queryByTestId("board-exam-timer")).not.toBeInTheDocument();
    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz", { scroll: false });
  });

  it("opens Board Exam setup for Pro Exam Reviewers after mode selection", async () => {
    setupChallengePrestart(true, "BOARD_EXAM", "PRO");

    render(<ChallengeQuizPage />);

    fireEvent.click(await getModeCard("Board Exam Mode"));

    expect(await screen.findByRole("heading", { name: "Board Exam" })).toBeInTheDocument();
    expect(screen.getByText("A timed simulation. The clock does not pause. Unanswered questions count against you.")).toBeInTheDocument();
    expect(screen.getByText("Challenge Note")).toBeInTheDocument();
    expect(screen.getByText("Pre-flight checklist")).toBeInTheDocument();
    expect(screen.getByText("Do not leave the page")).toBeInTheDocument();
    expect(screen.getByText("Fullscreen recommended")).toBeInTheDocument();
    expect(screen.getByText("~1 minute per question")).toBeInTheDocument();
    expect(screen.getByText("Scored against every question")).toBeInTheDocument();
    expect(screen.getByText(/Counts toward your monthly Board Exam usage\./)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "easy" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "medium" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "hard" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Begin Board Exam" })).toBeInTheDocument();
  });

  it("opens Board Exam setup for free Exam Reviewers and shows the paywall from the Start CTA", async () => {
    setupChallengePrestart(false, "BOARD_EXAM", "FREE", { usedThisMonth: 5, monthlyLimit: 5 });

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    fireEvent.click(await getModeCard("Board Exam Mode"));

    expect(await screen.findByRole("heading", { name: "Board Exam" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Unlock Board Exam Mode" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Unlock Board Exam - Pro" }));

    expect(await screen.findByRole("dialog", { name: "Unlock Board Exam Mode" })).toBeInTheDocument();
    expect(startChallengeQuizSession).not.toHaveBeenCalled();
  });

  it("opens Board Exam setup from a collection launch and scopes source notes to the plan", async () => {
    searchParamsMock = new URLSearchParams("collectionId=collection-1");
    setupChallengePrestart(true, "BOARD_EXAM", "PRO");
    (getCollection as jest.Mock).mockResolvedValue({
      id: "collection-1",
      items: [
        { noteId: "note-1", position: 0, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-1" },
        { noteId: "note-2", position: 1, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-2" },
        { noteId: "note-3", position: 2, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-3" },
        { noteId: "note-4", position: 3, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-4" },
      ],
    });
    (listNotes as jest.Mock).mockResolvedValue([
      { id: "note-2", title: "Plan Board Two", subject: "Biology", studyPackId: "sp-2", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-3", title: "Plan Board Three", subject: "Biology", studyPackId: "sp-3", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-4", title: "Plan Board Four", subject: "Biology", studyPackId: "sp-4", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-9", title: "Outside Board Note", subject: "Biology", studyPackId: "sp-9", studyPackStatus: "STUDY_PACK_READY" },
    ]);

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Board Exam" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Choose your quiz mode" })).not.toBeInTheDocument();
    expect(await screen.findByRole("button", { name: /Plan Board Two/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Plan Board Three/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Plan Board Four/ })).toHaveAttribute("aria-pressed", "false");
    expect(screen.queryByRole("button", { name: /Outside Board Note/ })).not.toBeInTheDocument();
    expect(screen.getByText("Add up to 2 more notes from this plan.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Review Set/ })).toHaveAttribute("href", "/collections/collection-1");
    expect(screen.queryByRole("button", { name: "Choose another mode" })).not.toBeInTheDocument();
  });

  it("allows multi-note Board Exam starts when one session remains", async () => {
    setupChallengePrestart(true, "BOARD_EXAM", "PRO", {
      boardExamUsedThisMonth: 9,
      boardExamMonthlyLimit: 10,
    });
    (listNotes as jest.Mock).mockResolvedValue([
      { id: "note-2", title: "Board Two", subject: "Biology", studyPackId: "sp-2", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-3", title: "Board Three", subject: "Biology", studyPackId: "sp-3", studyPackStatus: "STUDY_PACK_READY" },
    ]);

    render(<ChallengeQuizPage />);

    fireEvent.click(await getModeCard("Board Exam Mode"));
    fireEvent.click(await screen.findByRole("button", { name: /Board Two/ }));
    fireEvent.click(screen.getByRole("button", { name: /Board Three/ }));

    expect(screen.getByText("This session uses 1 of your 1 remaining Board Exam sessions.")).toBeInTheDocument();
    expect(screen.queryByText(/Not enough Board Exam quota/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Begin Board Exam" })).toBeEnabled();
  });

  it("falls back to the normal Board Exam source picker when collection lookup fails", async () => {
    searchParamsMock = new URLSearchParams("collectionId=missing");
    setupChallengePrestart(true, "BOARD_EXAM", "PRO");
    (getCollection as jest.Mock).mockRejectedValue(new Error("Not found"));
    (listNotes as jest.Mock).mockResolvedValue([
      { id: "note-2", title: "Fallback Board Note", subject: "Biology", studyPackId: "sp-2", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-3", title: "Other Board Subject", subject: "Chemistry", studyPackId: "sp-3", studyPackStatus: "STUDY_PACK_READY" },
    ]);

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Board Exam" })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: /Fallback Board Note/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Other Board Subject/ })).not.toBeInTheDocument();
  });

  it("shows the mode-selection screen first, then premium modal on click, for free users who exhausted Challenge Quiz credits", async () => {
    setupChallengePrestart(false, "STUDENT", "FREE", { usedThisMonth: 5, monthlyLimit: 5 });

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(await getModeCard("Challenge Quiz"));

    expect(await screen.findByRole("dialog", { name: /quiz limit/i })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "You’ve reached your quiz limit for this month" })).not.toBeInTheDocument();
  });

  it("shows the mode-selection screen first, then limit page on click, for Pro users who exhausted Challenge Quiz credits", async () => {
    setupChallengePrestart(true, "STUDENT", "PRO", { usedThisMonth: 50, monthlyLimit: 50 });

    render(<ChallengeQuizPage />);

    expect(await screen.findByRole("heading", { name: "Choose your quiz mode" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "You’ve reached your quiz limit for this month" })).not.toBeInTheDocument();

    fireEvent.click(await getModeCard("Challenge Quiz"));

    expect(await screen.findByRole("heading", { name: "You’ve reached your quiz limit for this month" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "You’ve reached your quiz limit" })).not.toBeInTheDocument();
  });

  it("keeps the Challenge Quiz navigator expanded by default on desktop", async () => {
    setupInProgressChallengeQuiz();

    render(<ChallengeQuizPage />);

    const topBar = await screen.findByTestId("challenge-quiz-top-bar");
    const actionBar = screen.getByTestId("challenge-quiz-action-bar");
    const navigatorDisclosure = screen.getByTestId("challenge-question-navigator-disclosure");

    expect(topBar).toHaveClass("sticky");
    expect(topBar).toHaveTextContent("Challenge Quiz");
    expect(actionBar).toHaveClass("fixed");
    expect(await screen.findByRole("button", { name: /Question Navigator/i })).toHaveAttribute("aria-expanded", "true");
    expect(navigatorDisclosure).toHaveAttribute("data-state", "expanded");
    expect(navigatorDisclosure).toHaveClass("motion-collapse");
    expect(screen.getByRole("button", { name: "Go to question 1 (unanswered)" })).toBeInTheDocument();
  });

  it("collapses the Challenge Quiz navigator by default on mobile and lets users expand it", async () => {
    setMobileViewport(true);
    setupInProgressChallengeQuiz();

    render(<ChallengeQuizPage />);

    const navigatorToggle = await screen.findByRole("button", { name: /Question Navigator/i });
    const navigatorDisclosure = screen.getByTestId("challenge-question-navigator-disclosure");
    await waitFor(() => {
      expect(navigatorToggle).toHaveAttribute("aria-expanded", "false");
    });
    expect(navigatorDisclosure).toHaveAttribute("data-state", "collapsed");
    expect(screen.getByText("Question Navigator · 1 of 1 · 0 answered")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Go to question 1 (unanswered)" })).not.toBeInTheDocument();

    fireEvent.click(navigatorToggle);

    expect(screen.getByRole("button", { name: "Go to question 1 (unanswered)" })).toBeInTheDocument();
    expect(navigatorToggle).toHaveAttribute("aria-expanded", "true");
    expect(navigatorDisclosure).toHaveAttribute("data-state", "expanded");
  });

  it("keeps Board Exam answers neutral and allows question navigation", async () => {
    setupBoardExamSession();

    render(<ChallengeQuizPage />);

    const topBar = await screen.findByTestId("challenge-quiz-top-bar");
    const actionBar = screen.getByTestId("challenge-quiz-action-bar");
    const navigatorDisclosure = screen.getByTestId("challenge-question-navigator-disclosure");

    expect(topBar).toHaveTextContent("Board Exam");
    expect(actionBar).toHaveClass("fixed");
    expect(await screen.findByText("Board Exam Mode hides distractions to simulate a real test environment.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Question Navigator/i })).toHaveAttribute("aria-expanded", "false");
    expect(navigatorDisclosure).toHaveAttribute("data-state", "collapsed");
    fireEvent.click(await screen.findByRole("button", { name: /Nucleus/i }));

    expect(screen.queryByText(/Correct/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Incorrect/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Mitochondria produce ATP/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Question Navigator/i }));
    fireEvent.click(screen.getByRole("button", { name: "Go to question 2 (unanswered)" }));

    expect(screen.getByText("What protects the plant cell?")).toBeInTheDocument();
    expect(screen.getByText("Question Navigator · 2 of 2 · 1 answered")).toBeInTheDocument();
    expect(navigatorDisclosure).toHaveAttribute("data-state", "expanded");
  });

  it("keeps the Board Exam navigator collapsed by default on mobile", async () => {
    setMobileViewport(true);
    setupBoardExamSession();

    render(<ChallengeQuizPage />);

    const navigatorToggle = await screen.findByRole("button", { name: /Question Navigator/i });
    const navigatorDisclosure = screen.getByTestId("challenge-question-navigator-disclosure");
    await waitFor(() => {
      expect(navigatorToggle).toHaveAttribute("aria-expanded", "false");
    });
    expect(navigatorDisclosure).toHaveAttribute("data-state", "collapsed");
    expect(screen.queryByRole("button", { name: "Go to question 2 (unanswered)" })).not.toBeInTheDocument();
  });

  it("shows the Board Exam start confirmation modal before generation begins", async () => {
    setupChallengePrestart(true, "BOARD_EXAM", "PRO");

    render(<ChallengeQuizPage />);

    fireEvent.click(await getModeCard("Board Exam Mode"));
    const beginButton = await screen.findByRole("button", { name: "Begin Board Exam" });
    await waitFor(() => {
      expect(beginButton).toBeEnabled();
    });
    fireEvent.click(beginButton);

    expect(screen.getByRole("dialog", { name: "Start Board Exam Mode?" })).toBeInTheDocument();
    expect(screen.getByText("You are about to start a board exam simulation.")).toBeInTheDocument();
    expect(screen.getByText("You will not see results until the end, and navigation will be limited during the exam.")).toBeInTheDocument();
    expect(startChallengeQuizSession).not.toHaveBeenCalled();
  });

  it("shows the Board Exam focus tip once per user", async () => {
    setupBoardExamSession();

    const { unmount } = render(<ChallengeQuizPage />);

    expect(await screen.findByText("Board Exam Mode hides distractions to simulate a real test environment.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Got it" }));

    await waitFor(() => {
      expect(screen.queryByText("Board Exam Mode hides distractions to simulate a real test environment.")).not.toBeInTheDocument();
    });

    unmount();
    setupBoardExamSession();
    render(<ChallengeQuizPage />);

    expect(await screen.findByTestId("challenge-quiz-top-bar")).toHaveTextContent("Board Exam");
    expect(screen.queryByText("Board Exam Mode hides distractions to simulate a real test environment.")).not.toBeInTheDocument();
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
    expect(screen.getByText("Question Navigator · 2 of 2 · 1 answered")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Question Navigator/i })).toHaveAttribute("aria-expanded", "false");
    });
    fireEvent.click(screen.getByRole("button", { name: /Question Navigator/i }));
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
    fireEvent.click(screen.getByRole("button", { name: /Question Navigator/i }));
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
      planType: "PRO",
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

    await screen.findByTestId("challenge-quiz-top-bar");
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

  it("submits the active Board Exam before leaving", async () => {
    const nowSpy = jest.spyOn(Date, "now").mockReturnValue(1_720_000_120_000);
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
      durationSeconds: 120,
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:02:00Z",
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Leave Exam" }));
    expect(screen.getByRole("dialog", { name: "Leave exam?" })).toBeInTheDocument();
    expect(screen.getByText("Your progress will be submitted and counted as complete.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Submit & Leave" }));

    await waitFor(() => {
      expect(completeChallengeQuizSession).toHaveBeenCalledWith("session-1", {
        correctAnswers: 1,
        totalQuestions: 2,
        durationSeconds: 120,
      });
    });
    expect(pushMock).toHaveBeenCalledWith("/notes/note-1");

    nowSpy.mockRestore();
  });

  it("blocks route clicks during an active Challenge Quiz until confirmed", async () => {
    setupInProgressChallengeQuiz();

    render(<ChallengeQuizPage />);

    await screen.findByTestId("challenge-quiz-top-bar");
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
    setupChallengePrestart(true, "STUDENT");
    (startChallengeQuizSession as jest.Mock).mockImplementation(() => new Promise(() => {}));

    render(<ChallengeQuizPage />);

    fireEvent.click(await getModeCard("Challenge Quiz"));
    const hardButton = await screen.findByRole("button", { name: "hard" });
    fireEvent.click(hardButton);
    expect(hardButton).toHaveClass("border-blue-500");

    const startButton = screen.getByRole("button", { name: "Start Quiz" });
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
    fireEvent.click(screen.getByRole("button", { name: "Complete Quiz" }));

    expect(await screen.findByText("Great job! Keep studying and improve your weak areas.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View Weak Concepts" })).toBeInTheDocument();
  });

  it('result screen does not contain a "Note" button', async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
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
      adaptivePracticeAvailable: true,
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

  it("fetches and renders the server-resolved next step after regular Challenge completion", async () => {
    setupInProgressChallengeQuiz("challenge");
    (completeChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      status: "COMPLETED",
      totalQuestions: 1,
      correctAnswers: 1,
      scorePercentage: 100,
      performanceLevel: "Excellent",
      conceptBreakdown: [
        { concept: "Concept", correctAnswers: 1, totalQuestions: 1, accuracyPercentage: 100 },
      ],
      weakConcepts: [],
      durationSeconds: 24,
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:10:00Z",
    });
    (getPostSessionNextStep as jest.Mock).mockResolvedValue({
      type: "REVIEW_PACK",
      studyPackId: "sp-1",
      noteId: "note-1",
      title: "Challenge Note",
      message: "You are in good shape here. Step up with a challenge or review the note when ready.",
      actionLabel: "Take a Challenge",
      actionHref: "/notes/note-1/challenge-quiz",
      concepts: [],
      adaptivePracticeAvailable: true,
      adaptivePracticeRemaining: null,
      goalNudge: null,
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Complete Quiz" }));

    expect(await screen.findByText("Recommended next step")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Take a Challenge" })).toHaveAttribute(
      "href",
      "/notes/note-1/challenge-quiz",
    );
    expect(getPostSessionNextStep).toHaveBeenCalledWith("sp-1");
  });

  it('result screen shows "← Back to Note" navigation link', async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
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
      adaptivePracticeAvailable: true,
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
    expect(screen.getByText("Excellent")).toBeInTheDocument();
    expect(screen.getByText("No weak concepts were identified in this exam. Review your answers or take another Board Exam when ready.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Take Another Board Exam" })).toHaveClass("bg-primary");
    expect(screen.getByRole("button", { name: "Review Answers" })).toHaveClass("border");
    expect(screen.getByText("Was this quiz helpful?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Give Feedback" })).toBeInTheDocument();
  });

  it("opens answer review with selected answer, correct answer, explanation, and concept", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
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
      adaptivePracticeAvailable: true,
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
    expect(review).toHaveTextContent("Performance");
    expect(review).toHaveTextContent("Needs Improvement");
    expect(review).toHaveTextContent("What powers the cell?");
    expect(review).toHaveTextContent("Concept: Cell Biology");
    expect(review).toHaveTextContent("Incorrect");
    expect(review).toHaveTextContent("Your Answer");
    expect(review).toHaveTextContent(/Your Answer[\s\S]*Nucleus/);
    expect(review).toHaveTextContent("Correct Answer");
    expect(review).toHaveTextContent(/Correct Answer[\s\S]*Mitochondria/);
    expect(review).toHaveTextContent("Nucleus");
    expect(review).toHaveTextContent("Your Answer");
    expect(review).toHaveTextContent("Mitochondria");
    expect(review).toHaveTextContent("Correct Answer");
    expect(screen.getByRole("button", { name: "Collapse Explanation" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Expand All" })).toBeInTheDocument();
    expect(within(review).getByRole("link", { name: "Practice Weak Concepts" })).toHaveAttribute("href", "/notes/note-1/adaptive-practice");
    expect(within(review).getByRole("link", { name: "Review Study Pack" })).toHaveAttribute("href", "/notes/note-1");
    expect(review).toHaveTextContent("Mitochondria produce ATP for the cell.");
    expect(screen.getByText("Found a confusing question or explanation while reviewing answers? Tell us what felt off.")).toBeInTheDocument();
  });

  it("shows upgrade nudge on result screen when adaptive practice is not available", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
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
      difficultySelectionAvailable: false,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 5,
      difficultySelectionAvailable: false,
      mode: "challenge",
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
      sessionState: { selectedChoices: {}, timerStartedAtEpochSeconds: Math.floor(Date.now() / 1000) },
    });
    (completeChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      status: "COMPLETED",
      totalQuestions: 1,
      correctAnswers: 1,
      scorePercentage: 100,
      performanceLevel: "Excellent",
      conceptBreakdown: [{ concept: "Cell Biology", correctAnswers: 1, totalQuestions: 1, accuracyPercentage: 100 }],
      weakConcepts: [],
      durationSeconds: 10,
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:01:00Z",
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Complete Quiz" }));
    await screen.findByText("Challenge Quiz Result");

    expect(screen.getByText("You're building momentum. Keep going without limits.")).toBeInTheDocument();
  });

  it("hides upgrade nudge on result screen when adaptive practice is available (Pro user)", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "PRO",
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
      adaptivePracticeAvailable: true,
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
      mode: "challenge",
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
      sessionState: { selectedChoices: {}, timerStartedAtEpochSeconds: Math.floor(Date.now() / 1000) },
    });
    (completeChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      status: "COMPLETED",
      totalQuestions: 1,
      correctAnswers: 1,
      scorePercentage: 100,
      performanceLevel: "Excellent",
      conceptBreakdown: [{ concept: "Cell Biology", correctAnswers: 1, totalQuestions: 1, accuracyPercentage: 100 }],
      weakConcepts: [],
      durationSeconds: 10,
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:01:00Z",
    });

    render(<ChallengeQuizPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Mitochondria/i }));
    fireEvent.click(screen.getByRole("button", { name: "Complete Quiz" }));
    await screen.findByText("Challenge Quiz Result");

    expect(screen.queryByText("You're building momentum. Keep going without limits.")).not.toBeInTheDocument();
  });
});
