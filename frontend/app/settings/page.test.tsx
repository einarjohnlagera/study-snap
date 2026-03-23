import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import SettingsPage from "./page";
import {
  cancelPremiumSubscription,
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
    (getBillingHistory as jest.Mock).mockResolvedValue([]);
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

    expect(await screen.findByText(/Premium ends on/i)).toBeInTheDocument();
    expect(screen.getByText("Your subscription will not renew.")).toBeInTheDocument();
    expect(screen.getByText("Your notes and Study Packs will remain in your library.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel Subscription" })).not.toBeInTheDocument();
  });
});
