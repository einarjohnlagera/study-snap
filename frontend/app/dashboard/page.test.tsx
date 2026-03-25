import { render, screen } from "@testing-library/react";
import DashboardPage from "./page";
import {
  getMasterySnapshot,
  getMe,
  getQuickReviewPerformanceSummary,
  getTodayFocus,
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
  getMasterySnapshot: jest.fn(),
  getMe: jest.fn(),
  joinPremiumWaitlist: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
  getTodayFocus: jest.fn(),
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
    (getTodayFocus as jest.Mock).mockReset();
    (getMasterySnapshot as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReset();

    (listNotes as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
    });
    (getTodayFocus as jest.Mock).mockResolvedValue(null);
    (getMasterySnapshot as jest.Mock).mockResolvedValue(null);
    (getQuickReviewPerformanceSummary as jest.Mock).mockResolvedValue(null);
  });

  it("shows the Free plan upgrade card for free users", async () => {
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        planType: "FREE",
        studyPacksUsed: 2,
        studyPacksLimit: 10,
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("You are using the Free Plan.")).toBeInTheDocument();
    expect(
      screen.getByText(/unlock Challenge Quiz and Adaptive Practice and generate up to 100 Study Packs per month/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Upgrade to Premium/i })).toBeInTheDocument();
  });

  it("does not show the Free plan upgrade card for premium users", async () => {
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        planType: "PREMIUM",
        studyPacksUsed: 12,
        studyPacksLimit: 100,
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Start your first note")).toBeInTheDocument();
    expect(screen.queryByText("You are using the Free Plan.")).not.toBeInTheDocument();
  });
});
