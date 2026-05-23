import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PrivateNoteDetailPageClient } from "./private-note-detail-page-client";
import {
  createStudyPackFromNote,
  completeProductOnboarding,
  copyNote,
  createPremiumCheckoutSession,
  deleteNote,
  generateGeneratedQuiz,
  getBillingPricing,
  getMyPlan,
  getMe,
  getChallengeQuizPerformanceSummary,
  getChallengeQuizSessionReview,
  getNote,
  getMyStudyPack,
  getQuickReviewPerformanceSummary,
  getQuickReviewSessionReview,
  listCoursePrograms,
  listRecentQuizSessions,
  listSubjects,
  startQuickReviewSession,
  updateNote,
  updateNoteVisibility,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

jest.mock("@/lib/checkout-redirect", () => ({
  redirectToCheckoutUrl: jest.fn(),
}));

const pushMock = jest.fn();
const replaceMock = jest.fn();
const clipboardWriteText = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};
let searchParamValues: Record<string, string> = {};
function createSearchParamsMock() {
  return {
    get: (key: string) => searchParamValues[key] ?? null,
    toString: () => new URLSearchParams(searchParamValues).toString(),
  };
}

let searchParamsMock = createSearchParamsMock();

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1",
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
  copyNote: jest.fn(),
  createPremiumCheckoutSession: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  deleteNote: jest.fn(),
  generateGeneratedQuiz: jest.fn(),
  getBillingPricing: jest.fn(),
  getMyPlan: jest.fn(),
  getChallengeQuizPerformanceSummary: jest.fn(),
  getChallengeQuizSessionReview: jest.fn(),
  getMe: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  listCoursePrograms: jest.fn(),
  listRecentQuizSessions: jest.fn(),
  listSubjects: jest.fn(),
  isEmailNotVerifiedError: () => false,
  trackAnalyticsEvent: jest.fn(),
  updateNote: jest.fn(),
  updateNoteVisibility: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
  getQuickReviewSessionReview: jest.fn(),
  startQuickReviewSession: jest.fn(),
}));

const baseNote = {
  id: "note-1",
  title: "Test Note",
  subject: "Biology",
  courseProgram: "Nursing",
  targetProfileType: "STUDENT" as const,
  tags: ["cells"],
  content: "Cell content",
  visibility: "PRIVATE" as const,
  createdAt: "2026-03-21T10:00:00Z",
  updatedAt: "2026-03-21T10:30:00Z",
  copiedFromNoteId: null,
  copiedFromUserId: null,
  copiedFromTitle: null,
  copiedFromPublic: false,
  copiedAt: null,
  studyPackId: null,
  studyPackStatus: "DRAFT" as const,
  summary: null,
  keyConcepts: [],
  quiz: [],
  generatedQuiz: null,
  lastUsedTargetLearnerLevel: null,
  quizCount: 0,
  quickReviewAvailable: false,
  challengeQuizAvailable: false,
  adaptivePracticeAvailable: false,
  difficultySelectionAvailable: false,
};

describe("PrivateNoteDetailPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    searchParamValues = {};
    searchParamsMock = createSearchParamsMock();
    window.localStorage.clear();
    window.sessionStorage.clear();
    clipboardWriteText.mockReset();
    clipboardWriteText.mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: clipboardWriteText },
      configurable: true,
    });
    (getNote as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (createStudyPackFromNote as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (copyNote as jest.Mock).mockReset();
    (deleteNote as jest.Mock).mockReset();
    (generateGeneratedQuiz as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (getChallengeQuizPerformanceSummary as jest.Mock).mockReset();
    (getChallengeQuizSessionReview as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getMyStudyPack as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
    (getQuickReviewSessionReview as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockReset();
    (listRecentQuizSessions as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    (createPremiumCheckoutSession as jest.Mock).mockReset();
    (startQuickReviewSession as jest.Mock).mockReset();
    (updateNote as jest.Mock).mockReset();
    (updateNoteVisibility as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry"]);
    (listCoursePrograms as jest.Mock).mockResolvedValue(["Nursing", "Senior High – STEM"]);
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getQuickReviewPerformanceSummary as jest.Mock).mockResolvedValue({
      attempts: 1,
      bestScorePercentage: 80,
      lastScorePercentage: 80,
      lastReviewedAt: "2026-03-21T10:30:00Z",
    });
    (getChallengeQuizPerformanceSummary as jest.Mock).mockResolvedValue({
      attempts: 1,
      bestScorePercentage: 75,
      lastScorePercentage: 75,
      lastCompletedAt: "2026-03-21T10:30:00Z",
      latestPerformanceLevel: "Good",
      latestWeakConcepts: ["Cells"],
    });
    (getChallengeQuizSessionReview as jest.Mock).mockResolvedValue({
      sessionId: "challenge-1",
      studyPackId: "sp-1",
      sessionMode: "CHALLENGE",
      status: "COMPLETED",
      totalQuestions: 10,
      correctAnswers: 8,
      scorePercentage: 80,
      retryCount: 0,
      durationSeconds: 120,
      weakConcepts: ["Cells"],
      conceptBreakdown: [],
      quiz: [],
      selectedChoices: {},
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:30:00Z",
    });
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue({
      sessionId: "quick-1",
      studyPackId: "sp-1",
      sessionMode: "QUICK_REVIEW",
      status: "COMPLETED",
      totalQuestions: 10,
      correctAnswers: 8,
      scorePercentage: 80,
      retryCount: 1,
      durationSeconds: 120,
      weakConcepts: ["Cells"],
      conceptBreakdown: [],
      quiz: [],
      selectedChoices: {},
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:30:00Z",
    });
    (listRecentQuizSessions as jest.Mock).mockResolvedValue([]);
    (getBillingPricing as jest.Mock).mockResolvedValue({
      region: "PH",
      currency: "PHP",
      plus: {
        planType: "PLUS",
        monthly: {
          amount: 179,
          durationDays: 30,
          introAmount: 149,
          introEligible: true,
          available: true,
        },
        yearly: {
          amount: 1790,
          durationDays: 365,
          introAmount: null,
          introEligible: false,
          available: false,
        },
      },
      pro: {
        planType: "PRO",
        monthly: {
          amount: 249,
          durationDays: 30,
          introAmount: 199,
          introEligible: true,
          available: true,
        },
        yearly: {
          amount: 2490,
          durationDays: 365,
          introAmount: null,
          introEligible: false,
          available: false,
        },
      },
    });
    (createPremiumCheckoutSession as jest.Mock).mockResolvedValue({
      checkoutUrl: "https://checkout.xendit.test/invoice_123",
    });
    (createStudyPackFromNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "GENERATING",
      studyPackId: null,
      quickReviewAvailable: false,
      challengeQuizAvailable: false,
      adaptivePracticeAvailable: false,
    });
    (generateGeneratedQuiz as jest.Mock).mockResolvedValue({
      noteId: "note-1",
      questions: [
        {
          question: "What is the nucleus?",
          choices: ["Control center", "Energy source", "Cell wall", "Waste product"],
          correctIndex: 0,
          concept: "Cells",
          explanation: "The nucleus controls cell activity.",
        },
      ],
      generatedAt: "2026-04-17T09:00:00Z",
    });
    (getMyStudyPack as jest.Mock).mockResolvedValue({
      id: "sp-1",
      noteId: "note-1",
      title: "Suggested Title",
      subject: "Biology",
      tags: ["cells"],
    });
    (listRecentQuizSessions as jest.Mock).mockResolvedValue([]);
    (copyNote as jest.Mock).mockResolvedValue({ id: "note-copy-1" });
    (deleteNote as jest.Mock).mockResolvedValue(undefined);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("routes Edit to note editor for draft note", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    const editButton = screen.getByRole("menuitem", { name: "Edit" });
    fireEvent.click(editButton);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/edit");
  });

  it("shows a compact note actions menu for long mobile note headers", async () => {
    const longTitle = "This is a very long note title that should wrap cleanly on mobile without pushing action buttons outside the header container";
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, title: longTitle, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const title = await screen.findByRole("heading", { name: longTitle });
    const menuTrigger = screen.getByRole("button", { name: "Open note actions" });
    const menuAnchor = menuTrigger.parentElement;
    const titleColumn = title.parentElement;

    expect(title).toHaveClass("wrap-break-word");
    expect(menuTrigger).toBeVisible();
    expect(menuAnchor).toHaveClass("relative", "shrink-0", "self-start");
    expect(titleColumn).toHaveClass("min-w-0", "flex-1", "space-y-3");

    fireEvent.click(menuTrigger);

    expect(screen.getByRole("menu", { name: "Note actions" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Edit" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Make a Copy" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Share" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Delete" })).toBeInTheDocument();
  });

  it("closes the note actions menu on outside click", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    expect(screen.getByRole("menu", { name: "Note actions" })).toBeInTheDocument();

    fireEvent.mouseDown(document.body);

    await waitFor(() => {
      expect(screen.queryByRole("menu", { name: "Note actions" })).not.toBeInTheDocument();
    });
  });

  it("disables Generate Study Pack and visibility toggle for unverified users", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: null });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PRIVATE" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    const generateButton = screen.getByRole("button", { name: "Generate Study Pack" });
    const visibilityButton = screen.getByRole("button", { name: /private/i });

    expect(generateButton).toBeDisabled();
    expect(visibilityButton).toBeDisabled();
  });

  it("for generated notes, Edit enables inline metadata editing instead of routing", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z", profileType: "TEACHER" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    expect(pushMock).not.toHaveBeenCalledWith("/notes/note-1/edit");
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, and tags.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Who is this note for?")).toBeInTheDocument();
    expect(screen.getByText("Changing audience will affect future quiz generation.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Share" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Quick Review" })).not.toBeInTheDocument();
  });

  it("navigates to the dedicated session review page from Recent Sessions on note detail", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });
    (listRecentQuizSessions as jest.Mock).mockResolvedValue([
      {
        sessionId: "quick-1",
        sessionMode: "QUICK_REVIEW",
        totalQuestions: 10,
        correctAnswers: 8,
        scorePercentage: 80,
        retryCount: 1,
        performanceLevel: null,
        weakConcepts: ["Cells"],
        participatingNoteCount: 1,
        createdAt: "2026-04-11T10:00:00Z",
        completedAt: "2026-04-11T10:05:00Z",
      },
      {
        sessionId: "challenge-1",
        sessionMode: "CHALLENGE",
        totalQuestions: 12,
        correctAnswers: 9,
        scorePercentage: 75,
        retryCount: 0,
        performanceLevel: "Good",
        weakConcepts: ["Genetics"],
        participatingNoteCount: 1,
        createdAt: "2026-04-10T10:00:00Z",
        completedAt: "2026-04-10T10:12:00Z",
      },
    ]);
    (getQuickReviewSessionReview as jest.Mock).mockResolvedValue({
      sessionId: "quick-1",
      studyPackId: "sp-1",
      sessionMode: "QUICK_REVIEW",
      status: "COMPLETED",
      totalQuestions: 10,
      correctAnswers: 8,
      scorePercentage: 80,
      retryCount: 1,
      durationSeconds: 120,
      weakConcepts: ["Cells"],
      conceptBreakdown: [
        {
          concept: "Cells",
          correctAnswers: 1,
          totalQuestions: 2,
          accuracyPercentage: 50,
        },
      ],
      quiz: [
        {
          question: "Which organelle produces ATP?",
          choices: ["Mitochondria", "Nucleus", "Ribosome", "Golgi body"],
          correctIndex: 0,
          concept: "Cells",
          explanation: "Mitochondria are the main ATP producers.",
        },
      ],
      selectedChoices: { 0: 1 },
      createdAt: "2026-04-11T10:00:00Z",
      completedAt: "2026-04-11T10:05:00Z",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Recent Sessions")).toBeInTheDocument();
    const quickReviewSessionButton = screen.getAllByText("Quick Review")
      .map((element) => element.closest("button"))
      .find((button) => button !== null);
    expect(quickReviewSessionButton).not.toBeNull();

    fireEvent.click(quickReviewSessionButton as HTMLButtonElement);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/sessions/quick-1?mode=quick-review&tab=summary");
    expect(getQuickReviewSessionReview).not.toHaveBeenCalled();
    expect(screen.queryByText("Currently reviewing")).not.toBeInTheDocument();
    expect(screen.queryByText("Loading session review...")).not.toBeInTheDocument();
  });

  it("keeps the same dedicated session review route behavior on smaller screens", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });
    (listRecentQuizSessions as jest.Mock).mockResolvedValue([
      {
        sessionId: "quick-1",
        sessionMode: "QUICK_REVIEW",
        totalQuestions: 10,
        correctAnswers: 8,
        scorePercentage: 80,
        retryCount: 1,
        performanceLevel: null,
        weakConcepts: ["Cells"],
        participatingNoteCount: 1,
        createdAt: "2026-04-11T10:00:00Z",
        completedAt: "2026-04-11T10:05:00Z",
      },
    ]);

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Recent Sessions")).toBeInTheDocument();
    const quickReviewSessionButton = screen.getAllByText("Quick Review")
      .map((element) => element.closest("button"))
      .find((button) => button !== null);
    expect(quickReviewSessionButton).not.toBeNull();

    fireEvent.click(quickReviewSessionButton as HTMLButtonElement);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/sessions/quick-1?mode=quick-review&tab=summary");
    expect(getQuickReviewSessionReview).not.toHaveBeenCalled();
    expect(screen.queryByText("Loading session review...")).not.toBeInTheDocument();
  });

  it("shows private-share modal and then opens share-link modal after making note public", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PRIVATE" });
    (updateNoteVisibility as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PUBLIC" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Share" }));

    expect(screen.getByText("This note is private")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Make Public & Share" }));

    expect(updateNoteVisibility).toHaveBeenCalledWith("note-1", "PUBLIC");
    expect(await screen.findByText("Share this note")).toBeInTheDocument();
    await waitFor(() => {
      expect(clipboardWriteText).toHaveBeenCalled();
      expect(screen.getByText("Copied ✓")).toBeInTheDocument();
    });
    expect(screen.getAllByText("Link copied to clipboard").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Copy Link" })).not.toBeInTheDocument();
  });

  it("supports Make a Copy and Delete from the note actions menu", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PRIVATE" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Make a Copy" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1");
    });
    expect(pushMock).toHaveBeenCalledWith("/notes/note-copy-1?copied=1");

    fireEvent.click(screen.getByRole("button", { name: "Open note actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Delete" }));

    expect(screen.getByText("Delete this note?")).toBeInTheDocument();
  });

  it("routes free users into the shared Challenge Quiz mode-selection entry without showing the premium modal", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz?entry=mode-selection");
    expect(screen.queryByText("Adaptive Practice is a Pro feature")).not.toBeInTheDocument();
  });

  it("navigates to quiz mode selection when a free user exhausted Challenge Quiz credits", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 5,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
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
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz?entry=mode-selection");
    expect(screen.queryByText("You've reached your quiz limit")).not.toBeInTheDocument();
  });

  it("shows a paywall modal when a free user clicks Adaptive Practice", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Adaptive Practice" }));

    expect(await screen.findByText("Unlock Adaptive Practice")).toBeInTheDocument();
  });

  it("routes premium users with exhausted Adaptive Practice usage into the limit flow", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PRO",
      limits: {
        studyPacksPerMonth: 100,
        challengeQuizzesPerMonth: 50,
        adaptivePracticePerMonth: 30,
        ocrPerMonth: 100,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 30,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 98,
        challengeQuizzesRemaining: 50,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 100,
      },
      features: {
        adaptivePracticeAvailable: true,
        difficultySelectionAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
    });
    (getChallengeQuizPerformanceSummary as jest.Mock).mockResolvedValue({
      attempts: 1,
      bestScorePercentage: 40,
      lastScorePercentage: 40,
      latestWeakConcepts: ["Cells"],
      lastReviewedAt: "2026-03-21T10:30:00Z",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Adaptive Practice" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/adaptive-practice");
    expect(screen.queryByText("Adaptive Practice is a Pro feature")).not.toBeInTheDocument();
  });

  it("renders teacher note detail with Study Pack tabs visible and student-only sections hidden", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("button", { name: "Generate Quiz" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Summary" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Key Concepts" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Quiz" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Full Notes" })).toBeInTheDocument();
    expect(screen.getByText("Teacher mode keeps quiz work separate from student quiz sessions. Generate, review, and export from the dedicated quiz preview.")).toBeInTheDocument();
    expect(screen.queryByText("Performance Overview")).not.toBeInTheDocument();
    expect(screen.queryByText("Recent Sessions")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Quick Review" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Challenge Quiz" })).not.toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Quiz question count" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Generate Quiz" }));

    expect(screen.getByRole("dialog", { name: "Generate quiz" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Quiz question count" })).toBeInTheDocument();
    expect(screen.getByText("Higher counts cover more material. Plus unlocks 20 and 30 questions.")).toBeInTheDocument();
    expect(screen.getByText("Target Level")).toBeInTheDocument();
    expect(screen.getByText("From your profile: College")).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "Generate Quiz" }).at(-1) as HTMLButtonElement);

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1", 10, "COLLEGE");
    });
  });

  it("keeps Target Level out of non-teacher note detail", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByRole("tab", { name: "Summary" });
    expect(screen.queryByRole("button", { name: "Generate Quiz" })).not.toBeInTheDocument();
    expect(screen.queryByText("Target Level")).not.toBeInTheDocument();
  });

  it("prefills teacher quiz Target Level from the last generation on the note", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      lastUsedTargetLearnerLevel: "JUNIOR_HIGH",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));

    expect(screen.getByRole("combobox", { name: "Target Level" })).toHaveValue("JUNIOR_HIGH");
    expect(screen.getByText("Last used: Junior High")).toBeInTheDocument();
  });

  it("keeps teacher quiz generation disabled when Target Level has no fallback", async () => {
    (getMe as jest.Mock).mockResolvedValue({ learnerLevel: null });
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));

    expect(screen.getAllByRole("button", { name: "Generate Quiz" }).at(-1)).toBeDisabled();
    expect(screen.getByText("Choose the default quiz difficulty before generating.")).toBeInTheDocument();
  });

  it("opens the longer teacher quiz paywall when a Free teacher clicks a locked count", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));
    expect(screen.getByRole("dialog", { name: "Generate quiz" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "20 Plus" }));

    expect(await screen.findByText("Unlock longer teacher quizzes")).toBeInTheDocument();
    expect(screen.getByText("Plus unlocks 20- and 30-question quizzes so you can match chapter quizzes and longer unit assessments.")).toBeInTheDocument();
    expect(generateGeneratedQuiz).not.toHaveBeenCalled();
  });

  it("generates the selected longer quiz count for Plus teachers", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PLUS",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PLUS",
      limits: {
        studyPacksPerMonth: 50,
        challengeQuizzesPerMonth: 25,
        adaptivePracticePerMonth: 10,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 48,
        challengeQuizzesRemaining: 25,
        adaptivePracticeRemaining: 10,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: true,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));
    expect(screen.getByRole("dialog", { name: "Generate quiz" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "30" }));
    fireEvent.change(screen.getByRole("combobox", { name: "Target Level" }), {
      target: { value: "JUNIOR_HIGH" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "Generate Quiz" }).at(-1) as HTMLButtonElement);

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1", 30, "JUNIOR_HIGH");
    });
  });

  it("shows the paywall modal for teachers who exhausted free quiz credits before generating", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 5,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
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
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: null,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Quiz" }));

    expect(await screen.findByText("You've reached your quiz generation limit")).toBeInTheDocument();
    expect(generateGeneratedQuiz).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("renders teacher note detail with View Quiz and regenerate confirmation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "TEACHER",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      generatedQuiz: {
        noteId: "note-1",
        questions: [
          {
            question: "What is the nucleus?",
            choices: ["Control center", "Energy source", "Cell wall", "Waste product"],
            correctIndex: 0,
            concept: "Cells",
            explanation: "The nucleus controls cell activity.",
          },
        ],
        generatedAt: "2026-04-17T09:00:00Z",
      },
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("button", { name: "View Quiz" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Regenerate" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Export" })).not.toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Summary" })).toBeInTheDocument();
    expect(screen.queryByText("Performance Overview")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "View Quiz" }));
    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/quiz");

    fireEvent.click(screen.getByRole("button", { name: "Regenerate" }));
    expect(screen.getByRole("dialog", { name: "Regenerate quiz?" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Quiz question count" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Regenerate Quiz" }));

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1", 10, "COLLEGE");
    });
  });

  it("shows the generate study pack guide for first-time users after creating a note", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "saved-note" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: null,
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Step 2: Generate your Study Pack")).toBeInTheDocument();
    await act(async () => {
      fireEvent.click(screen.getAllByRole("button", { name: "Generate Study Pack" }).at(-1) as HTMLButtonElement);
    });

    expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
  });

  it("auto-generates when the page is opened with generate=1", async () => {
    searchParamValues = { generate: "1" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await waitFor(() => {
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
    });
    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1", { scroll: false });
  });

  it("starts Quick Review automatically after copied-note generation finishes when requested", async () => {
    searchParamValues = { startQuickReview: "1" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue({ sessionId: "qr-1" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await waitFor(() => {
      expect(startQuickReviewSession).toHaveBeenCalledWith("note-1");
    });
    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1", { scroll: false });
    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/quick-review?sessionId=qr-1");
  });

  it("shows a first-study success banner after the first Study Pack is ready", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "study-pack-ready" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: null,
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Your Study Pack is ready!")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz?entry=mode-selection");
  });

  it("shows the generating state immediately after starting generation from note detail", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "BOARD_EXAM",
      productOnboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const generateButton = await screen.findByRole("button", { name: "Generate Study Pack" });
    await act(async () => {
      fireEvent.click(generateButton);
    });

    await waitFor(() => {
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
    });
    expect(await screen.findByText("Your Study Pack is being generated...")).toBeInTheDocument();
    expect(screen.getByText("Building your Study Pack...")).toBeInTheDocument();
  });

  it("polls a generating Study Pack until ready, then shows metadata suggestions and stops polling", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock)
      .mockResolvedValueOnce({ ...baseNote, studyPackStatus: "DRAFT" })
      .mockResolvedValueOnce({
        ...baseNote,
        studyPackStatus: "STUDY_PACK_READY",
        studyPackId: "sp-1",
        quickReviewAvailable: true,
        challengeQuizAvailable: true,
        summary: "Generated summary",
        keyConcepts: ["Cells"],
        quiz: [],
      });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const generateButton = await screen.findByRole("button", { name: "Generate Study Pack" });
    jest.useFakeTimers();
    await act(async () => {
      fireEvent.click(generateButton);
    });
    expect(await screen.findByText("Your Study Pack is being generated...")).toBeInTheDocument();

    await act(async () => {
      jest.advanceTimersByTime(3000);
    });
    expect(await screen.findByText("AI Suggestions")).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Quiz question count" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Skip" }));

    await waitFor(() => {
      expect(updateNote).not.toHaveBeenCalled();
      expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?created=1&tab=summary");
    });
    const getNoteCallsAfterReady = (getNote as jest.Mock).mock.calls.length;
    await act(async () => {
      jest.advanceTimersByTime(6000);
    });
    expect(getNote).toHaveBeenCalledTimes(getNoteCallsAfterReady);
    jest.useRealTimers();
  });

  it("shows a recoverable failure state and retries generation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "FAILED" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("We couldn't generate the Study Pack this time.")).toBeInTheDocument();
    await act(async () => {
      fireEvent.click(screen.getAllByRole("button", { name: "Retry Generate" })[0]);
    });

    await waitFor(() => {
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
    });
    expect(await screen.findByText("Your Study Pack is being generated...")).toBeInTheDocument();
  });

  it("applies selected AI metadata choices from note detail after async generation completes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock)
      .mockResolvedValueOnce({
        ...baseNote,
        title: "My Note",
        subject: "General Science",
        tags: ["review"],
        studyPackStatus: "DRAFT",
      })
      .mockResolvedValueOnce({
        ...baseNote,
        title: "My Note",
        subject: "General Science",
        tags: ["review"],
        studyPackStatus: "STUDY_PACK_READY",
        studyPackId: "sp-1",
        quickReviewAvailable: true,
        challengeQuizAvailable: true,
      });
    (getMyStudyPack as jest.Mock).mockResolvedValue({
      id: "sp-1",
      noteId: "note-1",
      title: "Suggested Title",
      subject: "Biology",
      tags: ["Review", "cells", "Memory"],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const generateButton = await screen.findByRole("button", { name: "Generate Study Pack" });
    jest.useFakeTimers();
    await act(async () => {
      fireEvent.click(generateButton);
    });
    expect(await screen.findByText("Your Study Pack is being generated...")).toBeInTheDocument();
    await act(async () => {
      jest.advanceTimersByTime(3000);
    });

    expect(await screen.findByText("AI Suggestions")).toBeInTheDocument();
    expect(screen.getAllByText("Memory").length).toBeGreaterThan(0);
    expect(screen.getByText("Already on your note")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Use AI Subject"));
    fireEvent.click(screen.getByLabelText("Merge My Tags + AI Tags"));
    fireEvent.click(screen.getByRole("button", { name: "Apply Changes" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        title: "My Note",
        subject: "Biology",
        courseProgram: "Nursing",
        tags: ["review", "cells", "Memory"],
      }));
      expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?created=1&tab=summary");
    });
    jest.useRealTimers();
  });

  it("shows the premium monthly-limit modal when Generate is clicked at the premium limit", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "PRO",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PRO",
      limits: {
        studyPacksPerMonth: 100,
        challengeQuizzesPerMonth: 50,
        adaptivePracticePerMonth: 30,
        ocrPerMonth: 100,
      },
      usage: {
        studyPacksUsed: 100,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 1,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 0,
        challengeQuizzesRemaining: 49,
        adaptivePracticeRemaining: 29,
        ocrRemaining: 100,
      },
      usageCycle: {
        startsAt: "2026-03-20T00:00:00Z",
        endsAt: "2026-04-20T00:00:00Z",
      },
      features: {
        adaptivePracticeAvailable: true,
        difficultySelectionAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByRole("dialog", { name: "You’ve reached your study pack limit for this month" })).toBeInTheDocument();
    expect(screen.getByText(/Your study pack limit resets on April 20\./)).toBeInTheDocument();
  });

  it("shows the upgrade paywall modal when Generate is clicked at the free Study Pack limit", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 10,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 0,
        challengeQuizzesRemaining: 4,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      usageCycle: {
        startsAt: "2026-03-20T00:00:00Z",
        endsAt: "2026-04-20T00:00:00Z",
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("You've reached your Study Pack limit")).toBeInTheDocument();
    expect(screen.queryByText("You've reached your Study Pack limit for this month")).not.toBeInTheDocument();
  });

  it("shows quiz view when tab=quiz is requested", async () => {
    searchParamValues = { tab: "quiz" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("tab", { name: "Quiz" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "Key Concepts" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Full Notes" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Summary" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Practice Quiz" })).toBeInTheDocument();
  });

  it("switches study pack views through tabs without reloading", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const summaryTab = await screen.findByRole("tab", { name: "Summary" });
    const quizTab = screen.getByRole("tab", { name: "Quiz" });

    expect(summaryTab).toHaveAttribute("aria-selected", "true");
    expect(getNote).toHaveBeenCalledTimes(1);

    fireEvent.click(quizTab);
    searchParamValues = { tab: "quiz" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=quiz", { scroll: false });
    expect(getNote).toHaveBeenCalledTimes(1);
    expect(screen.queryByText("Loading note...")).not.toBeInTheDocument();
    expect(await screen.findByRole("tab", { name: "Quiz" })).toHaveAttribute("aria-selected", "true");
  });

  it("uses the summary CTA to switch to Full Notes without refetching", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      content: "Original note body for review.",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "View Full Notes →" }));
    searchParamValues = { tab: "full-notes" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=full-notes", { scroll: false });
    expect(getNote).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("tab", { name: "Full Notes" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText(/Original note body for review\./i)).toBeInTheDocument();
  });

  it("shows the full original note content in the Full Notes tab without refetching", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      content: "Line one of the original note.\n\nLine two stays visible.",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("tab", { name: "Full Notes" }));
    searchParamValues = { tab: "full-notes" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=full-notes", { scroll: false });
    expect(getNote).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("tab", { name: "Full Notes" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("heading", { name: "Full Notes" })).toBeInTheDocument();
    expect(screen.getByText(/Line one of the original note\./i)).toBeInTheDocument();
    expect(screen.getByText(/Line two stays visible\./i)).toBeInTheDocument();
  });

  it("routes paid users into the shared Challenge Quiz mode-selection entry without showing the paywall modal", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PRO", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PRO",
      limits: {
        studyPacksPerMonth: 100,
        challengeQuizzesPerMonth: 50,
        adaptivePracticePerMonth: 30,
        ocrPerMonth: 100,
      },
      usage: {
        studyPacksUsed: 12,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 1,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 88,
        challengeQuizzesRemaining: 49,
        adaptivePracticeRemaining: 29,
        ocrRemaining: 100,
      },
      features: {
        adaptivePracticeAvailable: true,
        difficultySelectionAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      difficultySelectionAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz?entry=mode-selection");
    expect(screen.queryByText("Unlock Exam Mode")).not.toBeInTheDocument();
  });
});
