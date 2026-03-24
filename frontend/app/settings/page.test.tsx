import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import SettingsPage from "./page";
import {
  cancelPremiumSubscription,
  getBillingPricing,
  getBillingHistory,
  getBillingUsageSummary,
  getMe,
} from "@/lib/api";

const routerMock = {
  push: jest.fn(),
  refresh: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ id: "user-1" }),
}));

jest.mock("@/lib/route-guards", () => ({
  redirectToLoginWithCurrentDestination: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  cancelPremiumSubscription: jest.fn(),
  createPremiumCheckoutSession: jest.fn(),
  getBillingPricing: jest.fn(),
  getBillingHistory: jest.fn(),
  getBillingUsageSummary: jest.fn(),
  getMe: jest.fn(),
  isEmailNotVerifiedError: jest.fn(() => false),
  logout: jest.fn(),
  updateEngagementMode: jest.fn(),
}));

const premiumProfile = {
  id: "user-1",
  email: "[email protected]",
  firstName: "Note",
  lastName: null,
  displayName: "Note",
  countryCode: null,
  profileType: "STUDENT",
  engagementMode: "FOCUSED",
  emailVerifiedAt: "2026-03-20T00:00:00Z",
  role: "USER",
  status: "ACTIVE",
  planType: "PREMIUM",
  subscription: {
    cancelAtPeriodEnd: false,
    premiumEndsAt: "2026-04-20T00:00:00Z",
    cancelledAt: null,
  },
} as const;

const scheduledCancellationProfile = {
  ...premiumProfile,
  subscription: {
    cancelAtPeriodEnd: true,
    premiumEndsAt: "2026-04-20T00:00:00Z",
    cancelledAt: "2026-03-23T00:00:00Z",
  },
} as const;

describe("Settings page cancellation flow", () => {
  beforeEach(() => {
    (getMe as jest.Mock).mockReset();
    (getBillingUsageSummary as jest.Mock).mockReset();
    (getBillingHistory as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (cancelPremiumSubscription as jest.Mock).mockReset();

    (getMe as jest.Mock).mockResolvedValue(premiumProfile);
    (getBillingUsageSummary as jest.Mock).mockResolvedValue({
      planType: "PREMIUM",
      studyPacksUsed: 2,
      studyPacksLimit: 100,
      challengeQuizUsed: 1,
      challengeQuizLimit: 50,
      adaptivePracticeUsed: 0,
      adaptivePracticeLimit: 50,
      currentPeriodStart: "2026-03-01T00:00:00Z",
      currentPeriodEnd: "2026-04-01T00:00:00Z",
    });
    (getBillingHistory as jest.Mock).mockResolvedValue({
      currentPlan: "PREMIUM",
      subscriptionStatus: "ACTIVE",
      billingType: "MONTHLY",
      currentPeriodStart: "2026-03-01T00:00:00Z",
      currentPeriodEnd: "2026-04-01T00:00:00Z",
      cancelAtPeriodEnd: false,
      cancellationEffectiveAt: null,
      transactions: [],
    });
    (getBillingPricing as jest.Mock).mockResolvedValue({
      region: "PH",
      currency: "PHP",
      monthlyPrice: 249,
      yearlyPrice: 1999,
      introMonthlyPrice: 199,
      hasIntroPromo: true,
      introEligible: true,
    });
  });

  it("opens the cancellation confirmation modal from Settings", async () => {
    render(<SettingsPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Cancel Subscription" }));

    expect(screen.getByText("Cancel Premium?")).toBeInTheDocument();
    expect(
      screen.getByText(/Your Premium access will remain active until the end of your current billing period\./i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Keep Premium" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Confirm Cancellation" })).toBeInTheDocument();
  });

  it("submits the optional cancellation reason and updates the scheduled-cancellation state", async () => {
    (cancelPremiumSubscription as jest.Mock).mockResolvedValue(scheduledCancellationProfile);
    (getBillingHistory as jest.Mock).mockResolvedValue({
      currentPlan: "PREMIUM",
      subscriptionStatus: "ACTIVE",
      billingType: "MONTHLY",
      currentPeriodStart: "2026-03-01T00:00:00Z",
      currentPeriodEnd: "2026-04-20T00:00:00Z",
      cancelAtPeriodEnd: false,
      cancellationEffectiveAt: null,
      transactions: [],
    });

    render(<SettingsPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Cancel Subscription" }));
    fireEvent.click(screen.getByLabelText("Missing features I need"));
    fireEvent.change(screen.getByLabelText("Anything we can improve?"), {
      target: { value: "Please add better exports." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm Cancellation" }));

    await waitFor(() => {
      expect(cancelPremiumSubscription).toHaveBeenCalledWith({
        reason: "MISSING_FEATURES",
        feedback: "Please add better exports.",
      });
    });

    expect(await screen.findByText(/Your Premium plan will end on .* and will not renew\./i)).toBeInTheDocument();
    expect(screen.getByText("Your notes and Study Packs will remain in your library.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel Subscription" })).not.toBeInTheDocument();
  });

  it("renders the billing history empty state", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...premiumProfile,
      planType: "FREE",
      subscription: {
        cancelAtPeriodEnd: false,
        premiumEndsAt: null,
        cancelledAt: null,
      },
    });
    (getBillingUsageSummary as jest.Mock).mockResolvedValue({
      planType: "FREE",
      studyPacksUsed: 1,
      studyPacksLimit: 5,
      challengeQuizUsed: 0,
      challengeQuizLimit: 0,
      adaptivePracticeUsed: 0,
      adaptivePracticeLimit: 0,
      currentPeriodStart: "2026-03-01T00:00:00Z",
      currentPeriodEnd: "2026-04-01T00:00:00Z",
    });
    (getBillingHistory as jest.Mock).mockResolvedValue({
      currentPlan: "FREE",
      subscriptionStatus: null,
      billingType: null,
      currentPeriodStart: null,
      currentPeriodEnd: null,
      cancelAtPeriodEnd: false,
      cancellationEffectiveAt: null,
      transactions: [],
    });

    render(<SettingsPage />);

    expect(await screen.findByText("No billing history yet")).toBeInTheDocument();
    expect(screen.getAllByText("Your payment history will appear here once you subscribe to Premium.")).toHaveLength(2);
  });

  it("renders billing transactions from newest to oldest", async () => {
    (getBillingHistory as jest.Mock).mockResolvedValue({
      currentPlan: "PREMIUM",
      subscriptionStatus: "ACTIVE",
      billingType: "MONTHLY",
      currentPeriodStart: "2026-03-01T00:00:00Z",
      currentPeriodEnd: "2026-04-01T00:00:00Z",
      cancelAtPeriodEnd: false,
      cancellationEffectiveAt: null,
      transactions: [
        {
          id: "txn-new",
          date: "2026-03-22T00:00:00Z",
          description: "Failed payment",
          amount: 249,
          currency: "PHP",
          status: "FAILED",
          provider: "PAYMONGO",
          providerReferenceId: "evt_new",
        },
        {
          id: "txn-old",
          date: "2026-03-01T00:00:00Z",
          description: "Premium Monthly",
          amount: 249,
          currency: "PHP",
          status: "SUCCESS",
          provider: "PAYMONGO",
          providerReferenceId: "evt_old",
        },
      ],
    });

    render(<SettingsPage />);

    expect(await screen.findByRole("heading", { name: "Payment History" })).toBeInTheDocument();
    const descriptions = screen.getAllByText(/Failed payment|Premium Monthly/);
    expect(descriptions[0]).toHaveTextContent("Failed payment");
    expect(descriptions[1]).toHaveTextContent("Premium Monthly");
  });
});
