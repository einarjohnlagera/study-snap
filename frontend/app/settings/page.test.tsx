import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import SettingsPage from "./page";
import {
  cancelPremiumSubscription,
  createPremiumCheckoutSession,
  getBillingPricing,
  getBillingHistory,
  getMyPlan,
  getMe,
  requestEmailVerification,
  updateEngagementMode,
  updateStudyReminders,
} from "@/lib/api";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";
import { PLAN_BILLING_SECTION_ID } from "@/lib/plans";

const routerMock = {
  push: jest.fn(),
  refresh: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/settings",
}));

jest.mock("@/lib/auth", () => ({
  buildLoginPath: jest.fn(() => "/login?reason=logged_out"),
  getAuthUser: () => ({ id: "user-1", emailVerifiedAt: "2026-03-20T00:00:00Z" }),
  getCurrentPathWithQuery: () => `${window.location.pathname}${window.location.search}`,
  getSafeRedirectPath: (path: string | null | undefined) => (
    path && path.startsWith("/") && !path.startsWith("//") ? path : null
  ),
  LOGIN_REASON_LOGGED_OUT: "logged_out",
}));

jest.mock("@/lib/route-guards", () => ({
  redirectToLoginWithCurrentDestination: jest.fn(),
}));

jest.mock("@/lib/checkout-redirect", () => ({
  redirectToCheckoutUrl: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  cancelPremiumSubscription: jest.fn(),
  createPremiumCheckoutSession: jest.fn(),
  getBillingPricing: jest.fn(),
  getBillingHistory: jest.fn(),
  getMyPlan: jest.fn(),
  getMe: jest.fn(),
  isEmailNotVerifiedError: (error: unknown) => error instanceof Error && error.message === "EMAIL_VERIFICATION_REQUIRED",
  logout: jest.fn(),
  requestEmailVerification: jest.fn(),
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
  examDate: null,
  engagementMode: "FOCUSED",
  inactivityRemindersEnabled: false,
  weakConceptRemindersEnabled: false,
  emailVerifiedAt: "2026-03-20T00:00:00Z",
  onboardingCompletedAt: "2026-03-20T00:05:00Z",
  productOnboardingCompletedAt: null,
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
    window.history.replaceState(null, "", "/settings");
    (redirectToCheckoutUrl as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (getBillingHistory as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (cancelPremiumSubscription as jest.Mock).mockReset();
    (createPremiumCheckoutSession as jest.Mock).mockReset();
    (updateEngagementMode as jest.Mock).mockReset();
    (updateStudyReminders as jest.Mock).mockReset();

    (getMe as jest.Mock).mockResolvedValue(premiumProfile);
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PREMIUM",
      usageCycle: {
        startsAt: "2026-03-20T00:00:00Z",
        endsAt: "2026-04-20T00:00:00Z",
      },
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
    (createPremiumCheckoutSession as jest.Mock).mockResolvedValue({
      checkoutUrl: "https://checkout.xendit.test/invoice_123",
    });
    (requestEmailVerification as jest.Mock).mockResolvedValue({
      message: "Verification email sent. Please check your inbox.",
    });
  });

  it("shows the theme selector in Preferences", async () => {
    render(<SettingsPage />);

    expect(await screen.findByText("Theme")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Use Light theme" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Use Dark theme" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Use System theme" })).toBeInTheDocument();
    expect(
      screen.getByText("Choose Light, Dark, or System. System follows your device setting automatically."),
    ).toBeInTheDocument();
  });

  it("starts hosted checkout from Settings", async () => {
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

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ returnUrl: "/settings" });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
    });
  });

  it("shows manual renewal wording for active Premium access", async () => {
    render(<SettingsPage />);

    expect(await screen.findByText("Manual renewal")).toBeInTheDocument();
    expect(screen.getByText("Valid until")).toBeInTheDocument();
    expect(
      screen.getByText("Your Premium access is active until Apr 1, 2026. Renew manually whenever you're ready."),
    ).toBeInTheDocument();
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
      usageCycle: {
        startsAt: "2026-03-20T00:00:00Z",
        endsAt: "2026-04-20T00:00:00Z",
      },
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
          provider: "XENDIT",
          providerReferenceId: "evt_new",
        },
        {
          id: "txn-old",
          date: "2026-03-01T00:00:00Z",
          description: "Premium Monthly",
          amount: 249,
          currency: "PHP",
          status: "SUCCESS",
          provider: "XENDIT",
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

  it("shows usage reset date and hides adaptive practice usage for free users", async () => {
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
      usageCycle: {
        startsAt: "2026-03-15T00:00:00Z",
        endsAt: "2026-04-15T00:00:00Z",
      },
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 3,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 7,
        challengeQuizzesRemaining: 4,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<SettingsPage />);

    expect(await screen.findByText("Usage resets on: April 15")).toBeInTheDocument();
    expect(screen.queryByTestId("usage-metric-adaptive-practice")).not.toBeInTheDocument();
  });

  it("shows adaptive practice usage for premium users", async () => {
    render(<SettingsPage />);

    expect(await screen.findByText("Usage resets on: April 20")).toBeInTheDocument();
    expect(screen.getByTestId("usage-metric-adaptive-practice")).toBeInTheDocument();
    expect(within(screen.getByTestId("usage-metric-adaptive-practice")).getByText("0 / 30")).toBeInTheDocument();
  });

  it("shows upgrade CTA when a free user reaches a usage limit", async () => {
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
      usageCycle: {
        startsAt: "2026-03-15T00:00:00Z",
        endsAt: "2026-04-15T00:00:00Z",
      },
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 10,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 0,
        challengeQuizzesRemaining: 4,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<SettingsPage />);

    const studyPackMetric = await screen.findByTestId("usage-metric-study-packs");
    expect(studyPackMetric).toHaveTextContent("April 15");
    expect(studyPackMetric).toHaveTextContent("Upgrade to Premium");
    expect(within(studyPackMetric).getByRole("button", { name: "Upgrade to Premium" })).toBeInTheDocument();
  });

  it("scrolls to Plan & Billing when the page loads with the billing hash", async () => {
    const scrollIntoViewMock = jest.fn();
    const requestAnimationFrameSpy = jest
      .spyOn(window, "requestAnimationFrame")
      .mockImplementation((callback: FrameRequestCallback) => {
        callback(0);
        return 0;
      });

    Object.defineProperty(Element.prototype, "scrollIntoView", {
      configurable: true,
      value: scrollIntoViewMock,
      writable: true,
    });

    window.history.replaceState(null, "", `/settings#${PLAN_BILLING_SECTION_ID}`);

    render(<SettingsPage />);

    expect(await screen.findByRole("heading", { name: "Plan & Billing" })).toBeInTheDocument();

    await waitFor(() => {
      expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: "smooth", block: "start" });
    });

    requestAnimationFrameSpy.mockRestore();
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
