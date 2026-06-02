import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import DashboardPage from "./page";
import {
  completeProductOnboarding,
  getContinueStudyingRecommendation,
  getDashboardOverview,
  getMe,
  getNote,
  getQuickReviewPerformanceSummary,
  listNotes,
  listPublicNotes,
} from "@/lib/api";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { setAuthUser } from "@/lib/auth";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/dashboard",
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ id: "user-1", emailVerifiedAt: "2026-03-20T00:00:00Z", productOnboardingCompletedAt: null }),
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeProductOnboarding: jest.fn(),
  createPremiumCheckoutSession: jest.fn(),
  getContinueStudyingRecommendation: jest.fn(),
  getDashboardOverview: jest.fn(),
  getMe: jest.fn(),
  getNote: jest.fn(),
  getUserNotePerformanceSummary: jest.fn().mockResolvedValue([]),
  getQuickReviewPerformanceSummary: jest.fn(),
  listNotes: jest.fn(),
  listPublicNotes: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

jest.mock("@/hooks/use-billing-usage-summary", () => ({
  useBillingUsageSummary: jest.fn(),
}));

const notes = [
  {
    id: "note-1",
    title: "Biology Review",
    subject: "Biology",
    tags: ["Cells"],
    updatedAt: "2026-03-24T00:00:00Z",
    contentPreview: "Biology study content",
    studyPackStatus: "STUDY_PACK_READY",
    studyPackId: "pack-1",
    quizCount: 8,
  },
  {
    id: "note-2",
    title: "History Draft",
    subject: "History",
    tags: [],
    updatedAt: "2026-03-23T00:00:00Z",
    contentPreview: "Draft classroom material",
    studyPackStatus: "DRAFT",
    studyPackId: null,
    quizCount: 0,
  },
] as const;

const overview = {
  performanceSummary: {
    averageQuizScore: 82,
    totalQuizzesTaken: 4,
    studyPacksCreated: 2,
    strongestConcept: { conceptName: "Cell Theory", accuracyPercentage: 92 },
    weakestConcept: { conceptName: "Mitosis", accuracyPercentage: 48 },
  },
  focusAreas: {
    concepts: [
      { conceptName: "Mitosis", accuracyPercentage: 48 },
      { conceptName: "DNA Replication", accuracyPercentage: 55 },
    ],
    practiceNoteId: "note-1",
    adaptivePracticeAvailable: false,
  },
  weeklyActivity: {
    studyPacksCreated: 1,
    quizzesTaken: 2,
    adaptiveSessions: 0,
    studyDays: 2,
  },
};

const publicNotes = [
  {
    id: "public-note-1",
    ownerUserId: null,
    title: "PNLE Fundamentals",
    courseProgram: "PNLE",
    targetProfileType: "BOARD_TAKER",
    subject: "Nursing",
    tags: ["review"],
    contentPreview: "Nursing board exam fundamentals.",
    summaryPreview: "A quick PNLE review summary.",
    visibility: "PUBLIC",
    studyPackId: "pack-1",
    studyPackStatus: "STUDY_PACK_READY",
    quizCount: 12,
    copyCount: 4,
    likeCount: 2,
    shareCount: 1,
    viewCount: 30,
    authorDisplayName: "Creator",
    authorUsername: "creator",
    isOfficialAuthor: false,
    isCurrentUser: false,
    createdAt: "2026-03-20T00:00:00Z",
    updatedAt: "2026-03-21T00:00:00Z",
    likedByCurrentUser: false,
  },
] as const;

describe("DashboardPage profile variants", () => {
  beforeEach(() => {
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    (listNotes as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getContinueStudyingRecommendation as jest.Mock).mockReset();
    (getDashboardOverview as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
    (listPublicNotes as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (setAuthUser as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReset();

    (listNotes as jest.Mock).mockResolvedValue(notes);
    (getContinueStudyingRecommendation as jest.Mock).mockResolvedValue({
      noteId: "note-1",
      noteTitle: "Biology Review",
      subject: "Biology",
      courseProgram: "Nursing",
      summaryPreview: "Resume this biology review.",
      resumeType: "QUICK_REVIEW",
      reason: "RESUME_REVIEW",
      currentQuestionIndex: 1,
      totalQuestions: 8,
      resumeState: "QUESTION_IN_PROGRESS",
    });
    (getDashboardOverview as jest.Mock).mockResolvedValue(overview);
    (getQuickReviewPerformanceSummary as jest.Mock).mockResolvedValue(null);
    (listPublicNotes as jest.Mock).mockResolvedValue({ items: publicNotes, total: publicNotes.length });
  });

  it("renders the student dashboard with review-first sections", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Continue Studying")).toBeInTheDocument();
    expect(screen.getAllByText("Biology Review")).not.toHaveLength(0);
    expect(screen.getByText("Biology • Nursing")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Resume Quick Review" })).toHaveAttribute("href", "/notes/note-1/quick-review");
    expect(screen.getByText("Weak Concepts")).toBeInTheDocument();
    expect(screen.getByText("Recent Notes")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Notes for PNLE" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "See all in Public Library →" })).toHaveAttribute("href", "/public/library?courseProgram=PNLE");
    expect(screen.getByText("Quick Review")).toBeInTheDocument();
    expect(screen.getByText("Usage / Progress")).toBeInTheDocument();
    expect(screen.queryByText("Exam Countdown")).not.toBeInTheDocument();
    expect(screen.queryByText("Create Quiz")).not.toBeInTheDocument();
  });

  it("shows the personalization prompt after onboarding and routes to profile", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Too easy or too hard?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Adjust level" }));

    expect(routerMock.push).toHaveBeenCalledWith("/profile?from=dashboard#learning-profile");
  });

  it("persists dismissal of the personalization prompt per user", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    const { unmount } = render(<DashboardPage />);

    expect(await screen.findByText("Too easy or too hard?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Dismiss personalization prompt" }));

    await waitFor(() => {
      expect(screen.queryByText("Too easy or too hard?")).not.toBeInTheDocument();
    });
    expect(window.localStorage.getItem("notelib-dashboard-personalization-prompt:user-1")).toBe("1");

    unmount();
    render(<DashboardPage />);

    await screen.findByText("Continue Studying");
    expect(screen.queryByText("Too easy or too hard?")).not.toBeInTheDocument();
  });

  it("renders the board exam dashboard with countdown and challenge-quiz CTA", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      examDate: "2099-05-15",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PRO",
        limits: { studyPacksPerMonth: 100, challengeQuizzesPerMonth: 50, adaptivePracticePerMonth: 30 },
        usage: { studyPacksUsed: 12, challengeQuizzesUsed: 8, adaptivePracticeUsed: 3 },
      },
    });
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      focusAreas: {
        ...overview.focusAreas,
        adaptivePracticeAvailable: true,
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Exam Countdown")).toBeInTheDocument();
    expect(screen.getByText(/You have .* days until your exam\./)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Start Board Exam" })).toBeInTheDocument();
    expect(screen.getByText("Weak Areas")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Adaptive Practice" })).toBeInTheDocument();
    expect(screen.getByText("Study Activity This Week")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Notes for PNLE" })).toBeInTheDocument();
    expect(screen.getByText("Usage / Progress")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Practice Weak Areas" })).toBeInTheDocument();
  });

  it("renders community notes for professional profiles", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Pro",
      displayName: "Pro",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "PROFESSIONAL",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PLUS",
        limits: { studyPacksPerMonth: 40, challengeQuizzesPerMonth: 20, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByRole("heading", { name: "Notes for PNLE" })).toBeInTheDocument();
  });

  it("renders the teacher dashboard with material and quiz-focused sections", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Teach",
      displayName: "Teach",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "TEACHER",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });
    (getNote as jest.Mock).mockImplementation(async (noteId: string) => ({
      id: noteId,
      title: noteId === "note-1" ? "Biology Review" : "History Draft",
      subject: noteId === "note-1" ? "Biology" : "History",
      tags: noteId === "note-1" ? ["Cells"] : [],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: noteId === "note-1" ? "2026-03-24T00:00:00Z" : "2026-03-23T00:00:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: noteId === "note-1" ? "pack-1" : null,
      studyPackStatus: noteId === "note-1" ? "STUDY_PACK_READY" : "DRAFT",
      summary: "Summary",
      keyConcepts: [],
      quiz: [],
      generatedQuiz: noteId === "note-1"
        ? {
            noteId,
            generatedAt: "2026-03-24T00:00:00Z",
            questions: [
              {
                question: "What is a cell?",
                choices: ["A", "B", "C", "D"],
                correctIndex: 0,
                explanation: "Because it is.",
              },
            ],
          }
        : null,
      quizCount: 1,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    }));

    render(<DashboardPage />);

    expect(await screen.findByRole("heading", { name: "Create Teaching Material" })).toBeInTheDocument();
    expect(
      screen.getByText("Welcome to NoteLib! Start by creating a note, then generate a Study Pack and review the quiz in Quiz Preview."),
    ).toBeInTheDocument();
    expect(screen.getByText("Recent Notes")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Notes for PNLE" })).toBeInTheDocument();
    expect(screen.getByText("Recently Generated Quizzes")).toBeInTheDocument();
    expect(screen.getByText("Ready to Export")).toBeInTheDocument();
    expect(screen.getByText("Teacher Help / Tips")).toBeInTheDocument();
    expect(screen.queryByText("Continue Studying")).not.toBeInTheDocument();
    expect(screen.queryByText("Exam Countdown")).not.toBeInTheDocument();
    expect(screen.queryByText("Usage / Progress")).not.toBeInTheDocument();
  });

  it("gives teachers a recent-ready-note CTA when no generated quizzes exist yet", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Teach",
      displayName: "Teach",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "TEACHER",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });
    (getNote as jest.Mock).mockImplementation(async (noteId: string) => ({
      id: noteId,
      title: noteId === "note-1" ? "Biology Review" : "History Draft",
      subject: noteId === "note-1" ? "Biology" : "History",
      tags: noteId === "note-1" ? ["Cells"] : [],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: noteId === "note-1" ? "2026-03-24T00:00:00Z" : "2026-03-23T00:00:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: noteId === "note-1" ? "pack-1" : null,
      studyPackStatus: noteId === "note-1" ? "STUDY_PACK_READY" : "DRAFT",
      summary: "Summary",
      keyConcepts: [],
      quiz: [],
      generatedQuiz: null,
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: true,
    }));

    render(<DashboardPage />);

    expect(await screen.findByText("No generated quizzes yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open Recent Ready Note" })).toHaveAttribute("href", "/notes/note-1");
  });

  it("shows first-study onboarding to new users and routes them to create a note", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: null,
      studyPackCount: 0,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 0, challengeQuizzesUsed: 0, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Welcome to NoteLib")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create My First Note" }));

    expect(routerMock.push).toHaveBeenCalledWith("/notes/new");
  });

  it("shows the first-study dashboard empty state when the user has no notes yet", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 0,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 0, challengeQuizzesUsed: 0, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Start studying smarter")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Your First Note" })).toBeInTheDocument();
  });
});
