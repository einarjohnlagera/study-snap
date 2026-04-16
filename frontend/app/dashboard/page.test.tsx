import { fireEvent, render, screen } from "@testing-library/react";
import DashboardPage from "./page";
import {
  completeProductOnboarding,
  getContinueStudyingRecommendation,
  getDashboardOverview,
  getMe,
  getQuickReviewPerformanceSummary,
  listNotes,
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
  getContinueStudyingRecommendation: jest.fn(),
  getDashboardOverview: jest.fn(),
  getMe: jest.fn(),
  getUserNotePerformanceSummary: jest.fn().mockResolvedValue([]),
  joinPremiumWaitlist: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
  listNotes: jest.fn(),
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

describe("DashboardPage profile variants", () => {
  beforeEach(() => {
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    (listNotes as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getContinueStudyingRecommendation as jest.Mock).mockReset();
    (getDashboardOverview as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
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
  });

  it("renders the student dashboard with review-first sections", async () => {
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

    expect(await screen.findByText("Continue Studying")).toBeInTheDocument();
    expect(screen.getAllByText("Biology Review")).not.toHaveLength(0);
    expect(screen.getByText("Biology • Nursing")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Resume Quick Review" })).toHaveAttribute("href", "/notes/note-1/quick-review");
    expect(screen.getByText("Weak Concepts")).toBeInTheDocument();
    expect(screen.getByText("Recent Notes")).toBeInTheDocument();
    expect(screen.getByText("Quick Review")).toBeInTheDocument();
    expect(screen.getByText("Usage / Progress")).toBeInTheDocument();
    expect(screen.queryByText("Exam Countdown")).not.toBeInTheDocument();
    expect(screen.queryByText("Create Quiz")).not.toBeInTheDocument();
  });

  it("renders the board exam dashboard with countdown and challenge-quiz CTA", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      examDate: "2026-04-15",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PREMIUM",
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
    expect(screen.getByRole("heading", { name: "Practice Challenge Quiz" })).toBeInTheDocument();
    expect(screen.getByText("Weak Areas")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Adaptive Practice" })).toBeInTheDocument();
    expect(screen.getByText("Study Activity This Week")).toBeInTheDocument();
    expect(screen.getByText("Usage / Progress")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Practice Weak Areas" })).toBeInTheDocument();
  });

  it("renders the teacher dashboard with material and quiz-focused sections", async () => {
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

    render(<DashboardPage />);

    expect(await screen.findByRole("heading", { name: "Create Quiz" })).toBeInTheDocument();
    expect(screen.getByText("Upload / Paste Material")).toBeInTheDocument();
    expect(screen.getByText("Recent Materials")).toBeInTheDocument();
    expect(screen.getByText("Recently Generated Quizzes")).toBeInTheDocument();
    expect(screen.getByText("Activity")).toBeInTheDocument();
    expect(screen.getByText("Usage")).toBeInTheDocument();
    expect(screen.queryByText("Continue Studying")).not.toBeInTheDocument();
    expect(screen.queryByText("Exam Countdown")).not.toBeInTheDocument();
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

    expect(await screen.findByText("You don't have any Study Packs yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Your First Note" })).toBeInTheDocument();
  });
});
