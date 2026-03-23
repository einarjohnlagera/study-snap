import { render, screen, waitFor } from "@testing-library/react";
import AdminPage from "./page";
import {
  getAdminDashboardRecentEvents,
  getAdminDashboardSummary,
  getAdminDashboardTopContent,
} from "@/lib/api";

const routerMock = {
  replace: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAdminUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  getAdminDashboardSummary: jest.fn(),
  getAdminDashboardTopContent: jest.fn(),
  getAdminDashboardRecentEvents: jest.fn(),
  ApiRequestError: class ApiRequestError extends Error {
    status: number;

    constructor(message: string, options: { status: number }) {
      super(message);
      this.status = options.status;
    }
  },
}));

const { requireAdminUser } = jest.requireMock("@/lib/route-guards") as {
  requireAdminUser: jest.Mock;
};

describe("AdminPage", () => {
  beforeEach(() => {
    routerMock.replace.mockReset();
    requireAdminUser.mockReset();
    (getAdminDashboardSummary as jest.Mock).mockReset();
    (getAdminDashboardTopContent as jest.Mock).mockReset();
    (getAdminDashboardRecentEvents as jest.Mock).mockReset();
  });

  it("renders admin summary cards and tables for admins", async () => {
    requireAdminUser.mockReturnValue(true);
    (getAdminDashboardSummary as jest.Mock).mockResolvedValue({
      overview: {
        totalUsers: 120,
        verifiedUsers: 90,
        premiumUsers: 18,
        totalNotes: 420,
        totalStudyPacksGenerated: 275,
        totalPublicNotes: 44,
        totalPublicNoteViews: 830,
        totalPublicNoteCopies: 67,
        totalUpgrades: 18,
      },
      billing: {
        activePremiumSubscriptions: 18,
        monthlySubscriptions: 12,
        yearlySubscriptions: 6,
        cancelAtPeriodEndSubscriptions: 2,
        failedPayments: 3,
        estimatedMrr: 199.5,
        estimatedArr: 1499,
      },
      engagement: {
        studyPacksGeneratedThisWeek: 38,
        quickReviewsStarted: 240,
        challengeQuizzesStarted: 81,
        adaptivePracticeStarted: 44,
        paywallViews: 130,
        upgradeClicks: 27,
        signups: 52,
        verifiedAccounts: 31,
      },
    });
    (getAdminDashboardTopContent as jest.Mock).mockResolvedValue({
      mostViewedPublicNotes: [
        { noteId: "note-1", title: "Cell Structure", subject: "Science", totalCount: 120 },
      ],
      mostCopiedPublicNotes: [
        { noteId: "note-2", title: "World War 1 Causes", subject: "History", totalCount: 22 },
      ],
      topSubjectsByStudyPackGeneration: [
        { subject: "Biology", studyPackCount: 45 },
      ],
    });
    (getAdminDashboardRecentEvents as jest.Mock).mockResolvedValue({
      recentPremiumUpgrades: [
        {
          subscriptionId: "sub-1",
          userEmail: "[email protected]",
          billingCycle: "MONTHLY",
          provider: "PAYMONGO",
          cancelAtPeriodEnd: false,
          startedAt: "2026-03-20T00:00:00Z",
        },
      ],
      recentFailedPayments: [
        {
          transactionId: "txn-1",
          userEmail: "[email protected]",
          amount: 249,
          currency: "PHP",
          provider: "PAYMONGO",
          createdAt: "2026-03-21T00:00:00Z",
        },
      ],
    });

    render(<AdminPage />);

    expect(await screen.findByText("Admin Dashboard")).toBeInTheDocument();
    expect(await screen.findByText("Cell Structure")).toBeInTheDocument();
    expect(screen.getByText("Total Users")).toBeInTheDocument();
    expect(screen.getAllByText("120")).toHaveLength(2);
    expect(screen.getByText("Most Viewed Public Notes")).toBeInTheDocument();
    expect(screen.getByText("Recent Failed Payments")).toBeInTheDocument();
    expect(screen.getByText("PHP 249.00")).toBeInTheDocument();
  });

  it("does not load data when the admin guard rejects access", async () => {
    requireAdminUser.mockReturnValue(false);

    render(<AdminPage />);

    await waitFor(() => {
      expect(getAdminDashboardSummary).not.toHaveBeenCalled();
      expect(getAdminDashboardTopContent).not.toHaveBeenCalled();
      expect(getAdminDashboardRecentEvents).not.toHaveBeenCalled();
    });
  });
});
