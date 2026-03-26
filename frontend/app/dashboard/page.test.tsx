import { render, screen } from "@testing-library/react";
import DashboardPage from "./page";
import {
  getContinueStudyingRecommendation,
  getDashboardOverview,
  getMe,
  getQuickReviewPerformanceSummary,
  listNotes,
} from "@/lib/api";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";

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
  getAuthUser: () => ({ id: "user-1", emailVerifiedAt: "2026-03-20T00:00:00Z" }),
}));

jest.mock("@/lib/api", () => ({
  getContinueStudyingRecommendation: jest.fn(),
  getDashboardOverview: jest.fn(),
  getMe: jest.fn(),
  joinPremiumWaitlist: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
  listNotes: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

jest.mock("@/hooks/use-billing-usage-summary", () => ({
  useBillingUsageSummary: jest.fn(),
}));

describe("DashboardPage upgrade messaging", () => {
  beforeEach(() => {
    (listNotes as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getContinueStudyingRecommendation as jest.Mock).mockReset();
    (getDashboardOverview as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReset();

    (listNotes as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
    });
    (getContinueStudyingRecommendation as jest.Mock).mockResolvedValue(null);
    (getDashboardOverview as jest.Mock).mockResolvedValue({
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
    });
    (getQuickReviewPerformanceSummary as jest.Mock).mockResolvedValue(null);
  });

  it("shows the dashboard summary sections and Free plan upgrade card for free users", async () => {
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("You are using the Free Plan.")).toBeInTheDocument();
    expect(screen.getByText("Performance Summary")).toBeInTheDocument();
    expect(screen.getByText("Focus Areas")).toBeInTheDocument();
    expect(screen.getByText("This Week")).toBeInTheDocument();
    expect(screen.getByText("This Month")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Unlock Adaptive Practice/i })).toBeInTheDocument();
    expect(
      screen.getByText(/unlock Adaptive Practice, choose quiz difficulty, and access higher monthly limits/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Upgrade to Premium/i })).toBeInTheDocument();
  });

  it("does not show the Free plan upgrade card for premium users and shows the premium focus action", async () => {
    (getDashboardOverview as jest.Mock).mockResolvedValue({
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
        ],
        practiceNoteId: "note-1",
        adaptivePracticeAvailable: true,
      },
      weeklyActivity: {
        studyPacksCreated: 1,
        quizzesTaken: 2,
        adaptiveSessions: 1,
        studyDays: 2,
      },
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PREMIUM",
        limits: { studyPacksPerMonth: 100, challengeQuizzesPerMonth: 50, adaptivePracticePerMonth: 30 },
        usage: { studyPacksUsed: 12, challengeQuizzesUsed: 8, adaptivePracticeUsed: 3 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Practice Weak Concepts")).toBeInTheDocument();
    expect(screen.queryByText("You are using the Free Plan.")).not.toBeInTheDocument();
  });
});
