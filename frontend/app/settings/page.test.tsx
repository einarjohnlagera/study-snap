import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import SettingsPage from "./page";
import {
  cancelPremiumSubscription,
  getBillingPricing,
  getBillingHistory,
  getMyPlan,
  getMe,
  joinPremiumWaitlist,
  updateEngagementMode,
  updateStudyReminders,
} from "@/lib/api";

const routerMock = {
  push: jest.fn(),
  refresh: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/settings",
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ id: "user-1" }),
}));

jest.mock("@/lib/route-guards", () => ({
  redirectToLoginWithCurrentDestination: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  cancelPremiumSubscription: jest.fn(),
  getBillingPricing: jest.fn(),
  getBillingHistory: jest.fn(),
  getMyPlan: jest.fn(),
  getMe: jest.fn(),
  joinPremiumWaitlist: jest.fn(),
  logout: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateEngagementMode: jest.fn(),
  updateStudyReminders: jest.fn(),
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
  inactivityRemindersEnabled: false,
  weakConceptRemindersEnabled: false,
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
    (getMyPlan as jest.Mock).mockReset();
    (getBillingHistory as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (cancelPremiumSubscription as jest.Mock).mockReset();
    (joinPremiumWaitlist as jest.Mock).mockReset();
    (updateEngagementMode as jest.Mock).mockReset();
    (updateStudyReminders as jest.Mock).mockReset();

    (getMe as jest.Mock).mockResolvedValue(premiumProfile);
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PREMIUM",
      limits: {
        studyPacksPerMonth: 100,
        challengeQuizzesPerMonth: 50,
        adaptivePracticePerMonth: 30,
        ocrPerMonth: 100,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 0,
        ocrUsed: 4,
      },
      remaining: {
        studyPacksRemaining: 98,
        challengeQuizzesRemaining: 49,
        adaptivePracticeRemaining: 30,
        ocrRemaining: 96,
      },
      features: {
        adaptivePracticeAvailable: true,
        difficultySelectionAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
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
    (joinPremiumWaitlist as jest.Mock).mockResolvedValue({
      message: "You're on the list! We'll notify you when Premium launches.",
    });
  });

  it("opens the Premium coming soon modal and joins the waitlist from Settings", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...premiumProfile,
      planType: "FREE",
      subscription: {
        cancelAtPeriodEnd: false,
        premiumEndsAt: null,
        cancelledAt: null,
      },
    });

    render(<SettingsPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Upgrade to Premium" }));

    expect(await screen.findByText("Premium is coming soon")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Join Waitlist" }));

    await waitFor(() => {
      expect(joinPremiumWaitlist).toHaveBeenCalled();
    });
    expect(await screen.findByText("You're on the list! We'll notify you when Premium launches.")).toBeInTheDocument();
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
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 1,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 1,
      },
      remaining: {
        studyPacksRemaining: 9,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 19,
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
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

  it("renders Preferences before Plan & Billing and Account", async () => {
    render(<SettingsPage />);

    const preferencesHeading = await screen.findByRole("heading", { name: "Preferences" });
    const billingHeading = screen.getByRole("heading", { name: "Plan & Billing" });
    const accountHeading = screen.getByRole("heading", { name: "Account" });

    expect(preferencesHeading.compareDocumentPosition(billingHeading)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(billingHeading.compareDocumentPosition(accountHeading)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it("does not show OCR usage in the settings usage section", async () => {
    render(<SettingsPage />);

    expect(await screen.findByText("Monthly Usage")).toBeInTheDocument();
    expect(screen.queryByText(/OCR:/)).not.toBeInTheDocument();
  });

  it("persists learning style changes", async () => {
    (updateEngagementMode as jest.Mock).mockResolvedValue({
      ...premiumProfile,
      engagementMode: "STREAK",
      inactivityRemindersEnabled: false,
      weakConceptRemindersEnabled: false,
    });

    render(<SettingsPage />);

    fireEvent.click(await screen.findByDisplayValue("STREAK"));
    fireEvent.click(screen.getByRole("button", { name: "Save Learning Style" }));

    await waitFor(() => {
      expect(updateEngagementMode).toHaveBeenCalledWith({ engagementMode: "STREAK" });
    });
    expect(await screen.findByText("Learning style updated.")).toBeInTheDocument();
  });

  it("persists study reminder toggles", async () => {
    (updateStudyReminders as jest.Mock).mockResolvedValue({
      ...premiumProfile,
      inactivityRemindersEnabled: true,
      weakConceptRemindersEnabled: true,
    });

    render(<SettingsPage />);

    fireEvent.click(await screen.findByRole("checkbox", { name: /Inactivity reminders/i }));
    fireEvent.click(screen.getByRole("checkbox", { name: /Weak concept reminders/i }));
    fireEvent.click(screen.getByRole("button", { name: "Save Study Reminders" }));

    await waitFor(() => {
      expect(updateStudyReminders).toHaveBeenCalledWith({
        inactivityRemindersEnabled: true,
        weakConceptRemindersEnabled: true,
      });
    });
    expect(await screen.findByText("Study reminders updated.")).toBeInTheDocument();
  });
});
