import { render, screen } from "@testing-library/react";
import AdminFunnelPage from "./page";
import { getAdminFunnelMetrics } from "@/lib/api";

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
  getAdminFunnelMetrics: jest.fn(),
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

describe("AdminFunnelPage", () => {
  beforeEach(() => {
    routerMock.replace.mockReset();
    requireAdminUser.mockReset();
    (getAdminFunnelMetrics as jest.Mock).mockReset();
  });

  it("renders checkout conversion and weekly retention cohorts", async () => {
    requireAdminUser.mockReturnValue(true);
    (getAdminFunnelMetrics as jest.Mock).mockResolvedValue(buildMetricsResponse({
      retentionCohort: {
        eligibleActivatedUsers: 12,
        returnedWeek2Users: 5,
        ratePercent: 41.7,
        weeklyCohorts: [
          {
            weekStart: "2026-05-04",
            cohortSize: 6,
            returnedCount: 3,
            ratePercent: 50,
          },
        ],
      },
      checkoutConversion: {
        usersClickedUpgrade: 10,
        usersInitiatedCheckout: 4,
        usersSubscribed: 1,
        clickToCheckoutRatePercent: 40,
        checkoutToPaidRatePercent: 25,
        clickToPaidRatePercent: 10,
      },
    }));

    render(<AdminFunnelPage />);

    expect(await screen.findByText("Checkout conversion")).toBeInTheDocument();
    expect(screen.getByText("Upgrade Clicks")).toBeInTheDocument();
    expect(screen.getByText("Checkout Initiated")).toBeInTheDocument();
    expect(screen.getByText("Paid Conversions")).toBeInTheDocument();
    expect(screen.getByText("Click to Checkout")).toBeInTheDocument();
    expect(screen.getAllByText("40.0%").length).toBeGreaterThan(0);
    expect(screen.getByText("Checkout to Paid")).toBeInTheDocument();
    expect(screen.getByText("25.0%")).toBeInTheDocument();
    expect(screen.getByText("Click to Paid")).toBeInTheDocument();
    expect(screen.getByText("10.0%")).toBeInTheDocument();

    expect(screen.getByText("W1→W2 retention")).toBeInTheDocument();
    expect(screen.getByText("Returned in Week 2")).toBeInTheDocument();
    expect(screen.getByText("5 of 12 eligible activated users")).toBeInTheDocument();
    expect(screen.getByText("May 4, 2026")).toBeInTheDocument();
    expect(screen.getByText("50.0%")).toBeInTheDocument();
  });

  it("renders the retention empty state with zero metrics", async () => {
    requireAdminUser.mockReturnValue(true);
    (getAdminFunnelMetrics as jest.Mock).mockResolvedValue(buildMetricsResponse({
      retentionCohort: {
        eligibleActivatedUsers: 0,
        returnedWeek2Users: 0,
        ratePercent: 0,
        weeklyCohorts: [],
      },
      checkoutConversion: {
        usersClickedUpgrade: 0,
        usersInitiatedCheckout: 0,
        usersSubscribed: 0,
        clickToCheckoutRatePercent: 0,
        checkoutToPaidRatePercent: 0,
        clickToPaidRatePercent: 0,
      },
    }));

    render(<AdminFunnelPage />);

    expect(await screen.findByText("No eligible retention cohorts yet.")).toBeInTheDocument();
    expect(screen.getAllByText("0.0%").length).toBeGreaterThan(0);
  });
});

function buildMetricsResponse(overrides: {
  retentionCohort: {
    eligibleActivatedUsers: number;
    returnedWeek2Users: number;
    ratePercent: number;
    weeklyCohorts: Array<{
      weekStart: string;
      cohortSize: number;
      returnedCount: number;
      ratePercent: number;
    }>;
  };
  checkoutConversion: {
    usersClickedUpgrade: number;
    usersInitiatedCheckout: number;
    usersSubscribed: number;
    clickToCheckoutRatePercent: number;
    checkoutToPaidRatePercent: number;
    clickToPaidRatePercent: number;
  };
}) {
  return {
    activation: {
      totalVerifiedUsers: 20,
      activatedUsers: 8,
      activationRatePercent: 40,
      medianDaysToFirstPack: 2.5,
    },
    stuckUsers: {
      stuckUsersCount: 3,
    },
    quotaHit: {
      freeUsersHitQuota: 2,
      totalFreeUsers: 10,
      ratePercent: 20,
    },
    paywallConversion: {
      usersSeenPaywall: 5,
      usersUpgradedAfterPaywall: 1,
      ratePercent: 20,
    },
    valueLoop: {
      usersGeneratedPack: 8,
      usersStartedQuizWithin7Days: 6,
      ratePercent: 75,
    },
    retentionCohort: overrides.retentionCohort,
    checkoutConversion: overrides.checkoutConversion,
  };
}
