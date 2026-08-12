import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
        wideRetention: {
          eligibleActivatedUsers: 10,
          returnedAfterDay7Users: 4,
          afterDay7RatePercent: 40,
          returnedDays2To30Users: 6,
          days2To30RatePercent: 60,
          returnedAfterDay1Users: 7,
          afterDay1RatePercent: 70,
        },
        weeklyCohorts: [
          {
            weekStart: "2026-05-04",
            cohortSize: 6,
            returnedCount: 3,
            ratePercent: 50,
            returnedAfterDay7Count: 4,
            afterDay7RatePercent: 66.7,
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

    await waitFor(() => {
      expect(getAdminFunnelMetrics).toHaveBeenCalledWith(30);
    });
    expect(await screen.findByText("Checkout conversion")).toBeInTheDocument();
    expect(screen.getAllByText("Event window: Last 30 days").length).toBeGreaterThan(0);
    expect(screen.getByText("Upgrade Clicks")).toBeInTheDocument();
    expect(screen.getByText("Checkout Initiated")).toBeInTheDocument();
    expect(screen.getByText("Paid Conversions")).toBeInTheDocument();
    expect(screen.getByText("Click to Checkout")).toBeInTheDocument();
    expect(screen.getAllByText("40.0%").length).toBeGreaterThan(0);
    expect(screen.getByText("Checkout to Paid")).toBeInTheDocument();
    expect(screen.getByText("25.0%")).toBeInTheDocument();
    expect(screen.getByText("Click to Paid")).toBeInTheDocument();
    expect(screen.getAllByText("10.0%").length).toBeGreaterThan(0);

    expect(screen.getByText("Retention by window")).toBeInTheDocument();
    expect(screen.getByText("Days 7–14 (strict)")).toBeInTheDocument();
    expect(screen.getByText("5 of 12 users eligible after 14 days")).toBeInTheDocument();
    expect(screen.getByText("After day 7 (unbounded)")).toBeInTheDocument();
    expect(screen.getByText("4 of 10 users eligible after 30 days")).toBeInTheDocument();
    expect(screen.getByText("Days 2–30")).toBeInTheDocument();
    expect(screen.getByText("6 of 10 users eligible after 30 days")).toBeInTheDocument();
    expect(screen.getByText("After day 1 (unbounded)")).toBeInTheDocument();
    expect(screen.getByText("7 of 10 users eligible after 30 days")).toBeInTheDocument();
    expect(screen.getByText(/Unbounded windows accumulate with account age/)).toBeInTheDocument();
    expect(screen.getByText("May 4, 2026")).toBeInTheDocument();
    expect(screen.getByText("50.0% (3)")).toBeInTheDocument();
    expect(screen.getByText("After day 7")).toBeInTheDocument();
    expect(screen.getByText("66.7% (4)")).toBeInTheDocument();
  });

  it("renders the retention and onboarding empty states with zero metrics", async () => {
    requireAdminUser.mockReturnValue(true);
    (getAdminFunnelMetrics as jest.Mock).mockResolvedValue(buildMetricsResponse({
      onboarding: {
        totalSignups: 0,
        onboardingCompletedUsers: 0,
        completionRatePercent: 0,
        steps: buildOnboardingSteps([0, 0, 0, 0, 0, 0, 0, 0, 0]),
        legacyStep: {
          stepName: "legacy",
          label: "Legacy / other step names",
          userCount: 0,
          dropOffFromPrevious: null,
        },
        requestedPrograms: [],
      },
      retentionCohort: {
        eligibleActivatedUsers: 0,
        returnedWeek2Users: 0,
        ratePercent: 0,
        wideRetention: {
          eligibleActivatedUsers: 0,
          returnedAfterDay7Users: 0,
          afterDay7RatePercent: 0,
          returnedDays2To30Users: 0,
          days2To30RatePercent: 0,
          returnedAfterDay1Users: 0,
          afterDay1RatePercent: 0,
        },
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
    expect(screen.getByText("Days 7–14")).toBeInTheDocument();
    expect(screen.getByText("After day 7")).toBeInTheDocument();
    expect(screen.getByText("0 of 0 users eligible after 14 days")).toBeInTheDocument();
    expect(screen.getAllByText("0 of 0 users eligible after 30 days")).toHaveLength(3);
    expect(screen.getAllByText("0.0%").length).toBeGreaterThan(0);
    // The onboarding section must RENDER on an all-zero dataset rather than hide. This is the exact
    // state the release's checkpoint reads on a young dataset, and a hidden section reads as "no
    // instrumentation" rather than "no data yet" -- opposite meanings.
    expect(screen.getByText("Onboarding")).toBeInTheDocument();
    expect(screen.getByText("0 of 0 signups · users-table figure for baseline comparability")).toBeInTheDocument();
    expect(screen.getByText("Legacy / other step names")).toBeInTheDocument();
    expect(screen.getByText("No Official Study Plan requests yet.")).toBeInTheDocument();
  });

  it("renders onboarding completion, ordered steps, and the legacy bucket", async () => {
    requireAdminUser.mockReturnValue(true);
    (getAdminFunnelMetrics as jest.Mock).mockResolvedValue(buildMetricsResponse({
      onboarding: {
        totalSignups: 375,
        onboardingCompletedUsers: 234,
        completionRatePercent: 62.4,
        steps: buildOnboardingSteps([20, 18, 16, 15, 13, 10, 9, 8, 4]),
        legacyStep: {
          stepName: "legacy",
          label: "Legacy / other step names",
          userCount: 7,
          dropOffFromPrevious: null,
        },
        requestedPrograms: [
          { courseProgram: "Nursing", requestCount: 8, distinctLearners: 8 },
          { courseProgram: "Accountancy", requestCount: 3, distinctLearners: 3 },
        ],
      },
      retentionCohort: emptyRetentionMetrics(),
      checkoutConversion: emptyCheckoutMetrics(),
    }));

    render(<AdminFunnelPage />);

    expect(await screen.findByText("Onboarding")).toBeInTheDocument();
    expect(screen.getByText("Onboarding Completion Rate")).toBeInTheDocument();
    expect(screen.getByText("62.4%")).toBeInTheDocument();
    expect(screen.getByText("234 of 375 signups · users-table figure for baseline comparability")).toBeInTheDocument();
    expect(screen.getByText(/Step names changed in v0.73.0/)).toBeInTheDocument();
    expect(screen.getByText("Profile")).toBeInTheDocument();
    expect(screen.getAllByText("Course / Program").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("Confirm & Practice")).toBeInTheDocument();
    expect(screen.getByText("Legacy / other step names")).toBeInTheDocument();
    expect(screen.getByText("Excluded from current-flow ordering")).toBeInTheDocument();
    expect(screen.getByText("Requested Official Study Plans")).toBeInTheDocument();
    expect(screen.getByText("Nursing")).toBeInTheDocument();
    expect(screen.getByText("Accountancy")).toBeInTheDocument();
    const requestedProgramRows = screen.getByText("Nursing").closest("tbody")?.querySelectorAll("tr");
    expect(requestedProgramRows?.[0]).toHaveTextContent("Nursing");
    expect(requestedProgramRows?.[0]).toHaveTextContent("8");
    expect(requestedProgramRows?.[1]).toHaveTextContent("Accountancy");
  });

  it("renders onboarding when every count is zero", async () => {
    requireAdminUser.mockReturnValue(true);
    (getAdminFunnelMetrics as jest.Mock).mockResolvedValue(buildMetricsResponse({
      onboarding: {
        totalSignups: 0,
        onboardingCompletedUsers: 0,
        completionRatePercent: 0,
        steps: buildOnboardingSteps([0, 0, 0, 0, 0, 0, 0, 0, 0]),
        legacyStep: {
          stepName: "legacy",
          label: "Legacy / other step names",
          userCount: 0,
          dropOffFromPrevious: null,
        },
        requestedPrograms: [],
      },
      retentionCohort: emptyRetentionMetrics(),
      checkoutConversion: emptyCheckoutMetrics(),
    }));

    render(<AdminFunnelPage />);

    expect(await screen.findByText("Onboarding Completion Rate")).toBeInTheDocument();
    expect(screen.getByText("0 of 0 signups · users-table figure for baseline comparability")).toBeInTheDocument();
    expect(screen.getByText("Legacy / other step names")).toBeInTheDocument();
    expect(screen.getByText("No Official Study Plan requests yet.")).toBeInTheDocument();
  });

  it("refetches metrics when the window selector changes and renders quota breakdown", async () => {
    requireAdminUser.mockReturnValue(true);
    (getAdminFunnelMetrics as jest.Mock).mockResolvedValue(buildMetricsResponse({
      retentionCohort: {
        eligibleActivatedUsers: 0,
        returnedWeek2Users: 0,
        ratePercent: 0,
        wideRetention: {
          eligibleActivatedUsers: 0,
          returnedAfterDay7Users: 0,
          afterDay7RatePercent: 0,
          returnedDays2To30Users: 0,
          days2To30RatePercent: 0,
          returnedAfterDay1Users: 0,
          afterDay1RatePercent: 0,
        },
        weeklyCohorts: [],
      },
      checkoutConversion: {
        usersClickedUpgrade: 2,
        usersInitiatedCheckout: 1,
        usersSubscribed: 0,
        clickToCheckoutRatePercent: 50,
        checkoutToPaidRatePercent: 0,
        clickToPaidRatePercent: 0,
      },
    }));

    render(<AdminFunnelPage />);

    expect(await screen.findByText("Free quota hits")).toBeInTheDocument();
    expect(screen.getByText("Any Free Quota Hit")).toBeInTheDocument();
    expect(screen.getByText("Study Packs")).toBeInTheDocument();
    expect(screen.getByText("Challenge Quiz")).toBeInTheDocument();
    expect(screen.getByText("Adaptive Practice")).toBeInTheDocument();
    expect(screen.getByText("Interview Practice")).toBeInTheDocument();
    expect(screen.getAllByText("Not on Free").length).toBeGreaterThan(0);

    fireEvent.change(screen.getByLabelText("Funnel window"), { target: { value: "7" } });

    await waitFor(() => {
      expect(getAdminFunnelMetrics).toHaveBeenLastCalledWith(7);
    });

    fireEvent.change(screen.getByLabelText("Funnel window"), { target: { value: "all" } });

    await waitFor(() => {
      expect(getAdminFunnelMetrics).toHaveBeenLastCalledWith(undefined);
    });
  });
});

function buildMetricsResponse(overrides: {
  onboarding?: {
    totalSignups: number;
    onboardingCompletedUsers: number;
    completionRatePercent: number;
    steps: Array<{
      stepName: string;
      label: string;
      userCount: number;
      dropOffFromPrevious: number | null;
    }>;
    legacyStep: {
      stepName: string;
      label: string;
      userCount: number;
      dropOffFromPrevious: number | null;
    };
    requestedPrograms: Array<{
      courseProgram: string;
      requestCount: number;
      distinctLearners: number;
    }>;
  };
  retentionCohort: {
    eligibleActivatedUsers: number;
    returnedWeek2Users: number;
    ratePercent: number;
    wideRetention: {
      eligibleActivatedUsers: number;
      returnedAfterDay7Users: number;
      afterDay7RatePercent: number;
      returnedDays2To30Users: number;
      days2To30RatePercent: number;
      returnedAfterDay1Users: number;
      afterDay1RatePercent: number;
    };
    weeklyCohorts: Array<{
      weekStart: string;
      cohortSize: number;
      returnedCount: number;
      ratePercent: number;
      returnedAfterDay7Count: number;
      afterDay7RatePercent: number;
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
    windowDays: 30,
    windowStartedAt: "2026-05-24T00:00:00Z",
    onboarding: overrides.onboarding ?? {
      totalSignups: 20,
      onboardingCompletedUsers: 12,
      completionRatePercent: 60,
      steps: buildOnboardingSteps([20, 18, 16, 14, 12, 10, 9, 8, 4]),
      legacyStep: {
        stepName: "legacy",
        label: "Legacy / other step names",
        userCount: 3,
        dropOffFromPrevious: null,
      },
      requestedPrograms: [],
    },
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
      quotaTypes: [
        {
          quotaType: "study_pack",
          label: "Study Packs",
          monthlyLimit: 10,
          usersHitQuota: 0,
          applicableFreeUsers: 10,
          ratePercent: 0,
          applicable: true,
        },
        {
          quotaType: "quiz",
          label: "Challenge Quiz",
          monthlyLimit: 5,
          usersHitQuota: 2,
          applicableFreeUsers: 10,
          ratePercent: 20,
          applicable: true,
        },
        {
          quotaType: "adaptive",
          label: "Adaptive Practice",
          monthlyLimit: 3,
          usersHitQuota: 1,
          applicableFreeUsers: 10,
          ratePercent: 10,
          applicable: true,
        },
        {
          quotaType: "long_exam",
          label: "Long Exam",
          monthlyLimit: 0,
          usersHitQuota: 0,
          applicableFreeUsers: 0,
          ratePercent: 0,
          applicable: false,
        },
        {
          quotaType: "board_exam",
          label: "Board Exam",
          monthlyLimit: 0,
          usersHitQuota: 0,
          applicableFreeUsers: 0,
          ratePercent: 0,
          applicable: false,
        },
        {
          quotaType: "interview",
          label: "Interview Practice",
          monthlyLimit: 0,
          usersHitQuota: 0,
          applicableFreeUsers: 0,
          ratePercent: 0,
          applicable: false,
        },
      ],
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

function buildOnboardingSteps(counts: number[]) {
  const definitions = [
    ["profile", "Profile"],
    ["course-program", "Course / Program"],
    ["learner-level", "Learner Level"],
    ["first-intent", "First Intent"],
    ["input-method", "Input Method"],
    ["note", "Note"],
    ["generating", "Generating"],
    ["completion", "Completion"],
    ["confirm-practice", "Confirm & Practice"],
  ] as const;

  return definitions.map(([stepName, label], index) => ({
    stepName,
    label,
    userCount: counts[index] ?? 0,
    dropOffFromPrevious: index === 0 ? null : (counts[index - 1] ?? 0) - (counts[index] ?? 0),
  }));
}

function emptyRetentionMetrics() {
  return {
    eligibleActivatedUsers: 0,
    returnedWeek2Users: 0,
    ratePercent: 0,
    wideRetention: {
      eligibleActivatedUsers: 0,
      returnedAfterDay7Users: 0,
      afterDay7RatePercent: 0,
      returnedDays2To30Users: 0,
      days2To30RatePercent: 0,
      returnedAfterDay1Users: 0,
      afterDay1RatePercent: 0,
    },
    weeklyCohorts: [],
  };
}

function emptyCheckoutMetrics() {
  return {
    usersClickedUpgrade: 0,
    usersInitiatedCheckout: 0,
    usersSubscribed: 0,
    clickToCheckoutRatePercent: 0,
    checkoutToPaidRatePercent: 0,
    clickToPaidRatePercent: 0,
  };
}
