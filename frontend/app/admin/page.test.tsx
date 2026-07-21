import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import AdminPage from "./page";
import {
  getAdminDashboardRecentEvents,
  getAdminDashboardSummary,
  getAdminDashboardTopContent,
  getAdminFeedbackImage,
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
  getAdminFeedbackImage: jest.fn(),
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
    (getAdminFeedbackImage as jest.Mock).mockReset();
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: jest.fn(() => "blob:feedback-screenshot"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: jest.fn(),
    });
  });

  it("renders admin summary cards and tables for admins", async () => {
    requireAdminUser.mockReturnValue(true);
    (getAdminDashboardSummary as jest.Mock).mockResolvedValue({
      overview: {
        totalUsers: 120,
        verifiedUsers: 90,
        premiumUsers: 18,
        premiumWaitlistCount: 41,
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
          provider: "XENDIT",
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
          provider: "XENDIT",
          createdAt: "2026-03-21T00:00:00Z",
        },
      ],
      recentFeedback: [
        {
          feedbackId: "feedback-1",
          userEmail: "[email protected]",
          message: "The note editor feels confusing on mobile.",
          pageUrl: "https://www.notelib.app/notes/new",
          status: "NEW",
          createdAt: "2026-03-22T00:00:00Z",
          hasImage: false,
        },
      ],
    });

    render(<AdminPage />);

    expect(await screen.findByText("Admin Dashboard")).toBeInTheDocument();
    expect(await screen.findByText("Cell Structure")).toBeInTheDocument();
    expect(screen.getByText("Legacy Upgrade Waitlist")).toBeInTheDocument();
    expect(screen.getByText("Total Users")).toBeInTheDocument();
    expect(screen.getAllByText("120")).toHaveLength(2);
    expect(screen.getByText("Most Viewed Public Notes")).toBeInTheDocument();
    expect(screen.getByText("Recent Failed Payments")).toBeInTheDocument();
    expect(screen.getByText("Recent Feedback")).toBeInTheDocument();
    expect(screen.getByText("The note editor feels confusing on mobile.")).toBeInTheDocument();
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

  it("opens and closes a full Recent Feedback detail view", async () => {
    const fullMessage = "The dashboard study plan card wraps incorrectly on a narrow phone screen, and the final action disappears below the visible area until I rotate the device.";
    const fullPageUrl = "https://www.notelib.app/dashboard?source=feedback-detail-test&collectionId=collection-with-a-very-long-identifier";
    const submittedAt = "2026-07-20T08:35:00Z";
    const expectedSubmittedAt = new Intl.DateTimeFormat("en-US", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(submittedAt));
    requireAdminUser.mockReturnValue(true);
    (getAdminDashboardSummary as jest.Mock).mockResolvedValue({
      overview: {
        totalUsers: 1,
        verifiedUsers: 1,
        premiumUsers: 0,
        premiumWaitlistCount: 0,
        totalNotes: 1,
        totalStudyPacksGenerated: 1,
        totalPublicNotes: 0,
        totalPublicNoteViews: 0,
        totalPublicNoteCopies: 0,
        totalUpgrades: 0,
      },
      billing: {
        activePremiumSubscriptions: 0,
        monthlySubscriptions: 0,
        yearlySubscriptions: 0,
        cancelAtPeriodEndSubscriptions: 0,
        failedPayments: 0,
        estimatedMrr: 0,
        estimatedArr: 0,
      },
      engagement: {
        studyPacksGeneratedThisWeek: 0,
        quickReviewsStarted: 0,
        challengeQuizzesStarted: 0,
        adaptivePracticeStarted: 0,
        longExamsStarted: 0,
        interviewPracticeStarted: 0,
        paywallViews: 0,
        upgradeClicks: 0,
        signups: 0,
        verifiedAccounts: 0,
      },
    });
    (getAdminDashboardTopContent as jest.Mock).mockResolvedValue({
      mostViewedPublicNotes: [],
      mostCopiedPublicNotes: [],
      topSubjectsByStudyPackGeneration: [],
    });
    (getAdminDashboardRecentEvents as jest.Mock).mockResolvedValue({
      recentPremiumUpgrades: [],
      recentFailedPayments: [],
      recentFeedback: [{
        feedbackId: "feedback-long",
        userEmail: "reader@notelib.app",
        message: fullMessage,
        pageUrl: fullPageUrl,
        status: "REVIEWED",
        createdAt: submittedAt,
        hasImage: true,
      }],
    });
    (getAdminFeedbackImage as jest.Mock).mockResolvedValue(new Blob(["image-bytes"], { type: "image/png" }));

    render(<AdminPage />);

    expect(await screen.findByText("Recent Feedback")).toBeInTheDocument();
    expect(screen.queryByText(fullMessage)).not.toBeInTheDocument();
    expect(screen.queryByText(fullPageUrl)).not.toBeInTheDocument();
    expect(getAdminFeedbackImage).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "View" }));

    const dialog = screen.getByRole("dialog", { name: "Feedback Details" });
    expect(within(dialog).getByText(fullMessage)).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: fullPageUrl })).toHaveAttribute("href", fullPageUrl);
    expect(within(dialog).getByText("reader@notelib.app")).toBeInTheDocument();
    expect(within(dialog).getByText(expectedSubmittedAt)).toBeInTheDocument();
    expect(within(dialog).getByText("Reviewed")).toBeInTheDocument();
    expect(getAdminFeedbackImage).toHaveBeenCalledWith("feedback-long");
    expect(await within(dialog).findByRole("img", { name: "Feedback screenshot" })).toHaveAttribute(
      "src",
      "blob:feedback-screenshot",
    );

    fireEvent.click(within(dialog).getAllByRole("button", { name: "Close" }).at(-1)!);

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: "Feedback Details" })).not.toBeInTheDocument();
    });
  });
});
