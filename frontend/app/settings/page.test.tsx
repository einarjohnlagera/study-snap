import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import SettingsPage from "./page";
import {
  cancelPremiumSubscription,
  createPremiumCheckoutSession,
  deleteAccount,
  downloadMyData,
  getBillingPricing,
  getBillingHistory,
  getCreatorImpact,
  getMyPlan,
  getMe,
  requestEmailVerification,
  trackAnalyticsEvent,
  updateEngagementMode,
  updateEmailPreferences,
  updateMobileTabBarPreference,
} from "@/lib/api";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";
import { clearAuthUser } from "@/lib/auth";
import { PLAN_BILLING_SECTION_ID } from "@/lib/plans";
import { PASS_NO_AUTO_CHARGE_FOOTER } from "@/src/config/plans";

const routerMock = {
  push: jest.fn(),
  refresh: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/settings",
  useSearchParams: () => new URLSearchParams(),
}));

jest.mock("@/lib/auth", () => ({
  buildLoginPath: jest.fn(() => "/login?reason=logged_out"),
  clearAuthUser: jest.fn(),
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
  ApiRequestError: class ApiRequestError extends Error {
    code: string | null;
    action: string | null;
    status: number;

    constructor(message: string, options: { code?: string | null; action?: string | null; status: number }) {
      super(message);
      this.name = "ApiRequestError";
      this.code = options.code ?? null;
      this.action = options.action ?? null;
      this.status = options.status;
    }
  },
  cancelPremiumSubscription: jest.fn(),
  createPremiumCheckoutSession: jest.fn(),
  deleteAccount: jest.fn(),
  downloadMyData: jest.fn(),
  getBillingPricing: jest.fn(),
  getBillingHistory: jest.fn(),
  getCreatorImpact: jest.fn(),
  getMyPlan: jest.fn(),
  getMe: jest.fn(),
  isEmailNotVerifiedError: (error: unknown) => error instanceof Error && error.message === "EMAIL_VERIFICATION_REQUIRED",
  logout: jest.fn(),
  requestEmailVerification: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateEngagementMode: jest.fn(),
  updateEmailPreferences: jest.fn(),
  updateMobileTabBarPreference: jest.fn(),
}));

const proProfile = {
  id: "user-1",
  email: "note@example.com",
  firstName: "Note",
  lastName: null,
  displayName: "Note",
  countryCode: null,
  profileType: "STUDENT",
  examDate: null,
  engagementMode: "FOCUSED",
  inactivityRemindersEnabled: false,
  weakConceptRemindersEnabled: false,
  weeklySummaryRemindersEnabled: false,
  dueConceptsDigestRemindersEnabled: false,
  knowledgeImpactDigestRemindersEnabled: false,
  marketingEmailsEnabled: false,
  mobileTabBarEnabled: true,
  emailVerifiedAt: "2026-03-20T00:00:00Z",
  onboardingCompletedAt: "2026-03-20T00:05:00Z",
  productOnboardingCompletedAt: null,
  role: "USER",
  status: "ACTIVE",
  planType: "PRO",
  subscription: {
    cancelAtPeriodEnd: false,
    premiumEndsAt: "2026-04-20T00:00:00Z",
    cancelledAt: null,
  },
} as const;

const scheduledCancellationProfile = {
  ...proProfile,
  subscription: {
    cancelAtPeriodEnd: true,
    premiumEndsAt: "2026-04-20T00:00:00Z",
    cancelledAt: "2026-03-23T00:00:00Z",
  },
} as const;

const proBillingPricing = {
  region: "PH",
  currency: "PHP",
  plus: {
    planType: "PLUS",
    monthly: { amount: 179, durationDays: 30, introAmount: 149, introEligible: true, available: true },
    yearly: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false },
    examCycle: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false },
  },
  pro: {
    planType: "PRO",
    monthly: { amount: 249, durationDays: 30, introAmount: 199, introEligible: true, available: true },
    yearly: { amount: 1999, durationDays: 365, introAmount: null, introEligible: false, available: true },
    examCycle: { amount: 599, durationDays: 90, introAmount: null, introEligible: false, available: true },
  },
};

const proUsageSummary = {
  plan: "PRO",
  usageCycle: {
    startsAt: "2026-03-20T00:00:00Z",
    endsAt: "2026-04-20T00:00:00Z",
  },
  limits: {
    studyPacksPerMonth: 100,
    challengeQuizzesPerMonth: 50,
    adaptivePracticePerMonth: 30,
    ocrPerMonth: 100,
    docxExportsPerMonth: null,
    pdfExportsPerMonth: null,
  },
  usage: {
    studyPacksUsed: 2,
    challengeQuizzesUsed: 1,
    adaptivePracticeUsed: 0,
    ocrUsed: 4,
    docxExportsUsed: 5,
    pdfExportsUsed: 2,
  },
  remaining: {
    studyPacksRemaining: 98,
    challengeQuizzesRemaining: 49,
    adaptivePracticeRemaining: 30,
    ocrRemaining: 96,
    docxExportsRemaining: null,
    pdfExportsRemaining: null,
  },
  features: {
    adaptivePracticeAvailable: true,
    fileUploadAvailable: true,
    ocrAvailable: true,
  },
};

const freeUsageSummary = {
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
    docxExportsPerMonth: 2,
    pdfExportsPerMonth: 2,
  },
  usage: {
    studyPacksUsed: 1,
    challengeQuizzesUsed: 0,
    adaptivePracticeUsed: 0,
    ocrUsed: 0,
    docxExportsUsed: 1,
    pdfExportsUsed: 0,
  },
  remaining: {
    studyPacksRemaining: 9,
    challengeQuizzesRemaining: 5,
    adaptivePracticeRemaining: 0,
    ocrRemaining: 20,
    docxExportsRemaining: 1,
    pdfExportsRemaining: 2,
  },
  features: {
    adaptivePracticeAvailable: false,
    fileUploadAvailable: true,
    ocrAvailable: true,
  },
};

describe("Settings page cancellation flow", () => {
  beforeEach(() => {
    window.history.replaceState(null, "", "/settings");
    (redirectToCheckoutUrl as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (getBillingHistory as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (getCreatorImpact as jest.Mock).mockReset();
    (cancelPremiumSubscription as jest.Mock).mockReset();
    (createPremiumCheckoutSession as jest.Mock).mockReset();
    (deleteAccount as jest.Mock).mockReset();
    (downloadMyData as jest.Mock).mockReset();
    (clearAuthUser as jest.Mock).mockReset();
    (updateEngagementMode as jest.Mock).mockReset();
    (updateEmailPreferences as jest.Mock).mockReset();
    (updateMobileTabBarPreference as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();

    (getMe as jest.Mock).mockResolvedValue(proProfile);
    (getMyPlan as jest.Mock).mockResolvedValue(proUsageSummary);
    (getBillingHistory as jest.Mock).mockResolvedValue({
      currentPlan: "PRO",
      subscriptionStatus: "ACTIVE",
      billingType: "MONTHLY",
      currentPeriodStart: "2026-03-01T00:00:00Z",
      currentPeriodEnd: "2026-04-01T00:00:00Z",
      cancelAtPeriodEnd: false,
      cancellationEffectiveAt: null,
      transactions: [],
    });
    (getBillingPricing as jest.Mock).mockResolvedValue(proBillingPricing);
    (getCreatorImpact as jest.Mock).mockResolvedValue({
      distinctLearnersHelped: 0,
      notes: [
        {
          noteId: "public-note-1",
          title: "Public note",
          distinctLearnersHelped: 0,
          viewCount: 0,
          copyCount: 0,
        },
      ],
    });
    (createPremiumCheckoutSession as jest.Mock).mockResolvedValue({
      checkoutUrl: "https://checkout.xendit.test/invoice_123",
    });
    (requestEmailVerification as jest.Mock).mockResolvedValue({
      message: "Verification email sent. Please check your inbox.",
    });
    (deleteAccount as jest.Mock).mockResolvedValue({ message: "Account deletion scheduled." });
    (downloadMyData as jest.Mock).mockResolvedValue({ filename: "notelib-export-2026-06-23.json" });
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

  it("starts Plus monthly checkout from Settings for a free user", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      planType: "FREE",
      subscription: { cancelAtPeriodEnd: false, premiumEndsAt: null, cancelledAt: null },
    });
    (getMyPlan as jest.Mock).mockResolvedValue(freeUsageSummary);
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

    expect(await screen.findAllByText(PASS_NO_AUTO_CHARGE_FOOTER)).toHaveLength(2);
    fireEvent.click(await screen.findByRole("button", { name: "Get Plus" }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({
        planType: "PLUS",
        billingCycle: "MONTHLY",
        returnUrl: "/settings",
      });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "UPGRADE_CLICKED",
        metadata: {
          source: "settings_plan_billing",
          feature: "plus_checkout_monthly",
          path: "/settings",
          target: "xendit_checkout",
        },
      });
    });
  });

  it("starts Pro monthly checkout from Settings for a free user", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      planType: "FREE",
      subscription: { cancelAtPeriodEnd: false, premiumEndsAt: null, cancelledAt: null },
    });
    (getMyPlan as jest.Mock).mockResolvedValue(freeUsageSummary);
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

    fireEvent.click(await screen.findByRole("button", { name: "Get Pro" }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({
        planType: "PRO",
        billingCycle: "MONTHLY",
        returnUrl: "/settings",
      });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
    });
  });

  it("starts Pro exam-cycle checkout from Settings for a free user", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      planType: "FREE",
      subscription: { cancelAtPeriodEnd: false, premiumEndsAt: null, cancelledAt: null },
    });
    (getMyPlan as jest.Mock).mockResolvedValue(freeUsageSummary);
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

    // 3-month pass surfaces its own savings badge (₱599 vs three ₱249 passes ≈ 20%).
    expect(await screen.findByText("Save 20%")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^3 months/ }));
    fireEvent.click(screen.getByRole("button", { name: "Get Pro — ₱599 / 3 months" }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({
        planType: "PRO",
        billingCycle: "EXAM_CYCLE",
        returnUrl: "/settings",
      });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
    });
  });

  it("hides the Pro exam-cycle checkout when live pricing marks it unavailable", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      planType: "FREE",
      subscription: { cancelAtPeriodEnd: false, premiumEndsAt: null, cancelledAt: null },
    });
    (getMyPlan as jest.Mock).mockResolvedValue(freeUsageSummary);
    (getBillingPricing as jest.Mock).mockResolvedValue({
      ...proBillingPricing,
      pro: {
        ...proBillingPricing.pro,
        examCycle: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false },
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

    await screen.findByRole("button", { name: "Get Pro" });
    expect(screen.queryByRole("button", { name: "Get Pro — ₱599 / 3 months" })).not.toBeInTheDocument();
  });

  it("starts Pro annual checkout after switching to the 1-year pass", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      planType: "FREE",
      subscription: { cancelAtPeriodEnd: false, premiumEndsAt: null, cancelledAt: null },
    });
    (getMyPlan as jest.Mock).mockResolvedValue(freeUsageSummary);
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

    await screen.findByRole("button", { name: "Get Pro" });
    fireEvent.click(screen.getByRole("button", { name: /1 year/ }));
    fireEvent.click(screen.getByRole("button", { name: /^Get Pro/ }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({
        planType: "PRO",
        billingCycle: "YEARLY",
        returnUrl: "/settings",
      });
    });
  });

  it("shows manual renewal wording for active Pro access", async () => {
    render(<SettingsPage />);

    expect(await screen.findByText("Valid until")).toBeInTheDocument();
    expect(
      screen.getByText("Your Pro access is active until Apr 1, 2026. Renew manually whenever you're ready."),
    ).toBeInTheDocument();
    const cycleCells = screen.getAllByText(/Won't auto-renew/);
    expect(cycleCells.length).toBeGreaterThanOrEqual(1);
  });

  it("starts the existing current-plan checkout flow from the expiry notice", async () => {
    const premiumEndsAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      subscription: { ...proProfile.subscription, premiumEndsAt },
    });

    render(<SettingsPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Get another Pro pass" }));
    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({
        planType: "PRO",
        billingCycle: "MONTHLY",
        returnUrl: "/settings",
      });
    });
  });

  it("shows exam-pass billing cycle wording for active exam-cycle access", async () => {
    (getBillingHistory as jest.Mock).mockResolvedValue({
      currentPlan: "PRO",
      subscriptionStatus: "ACTIVE",
      billingType: "EXAM_CYCLE",
      currentPeriodStart: "2026-03-01T00:00:00Z",
      currentPeriodEnd: "2026-05-30T00:00:00Z",
      cancelAtPeriodEnd: false,
      cancellationEffectiveAt: null,
      transactions: [],
    });

    render(<SettingsPage />);

    expect(await screen.findByText("3-Month Pass · Won't auto-renew")).toBeInTheDocument();
  });

  it("shows cancel plan link for active Pro subscription", async () => {
    render(<SettingsPage />);

    expect(await screen.findByRole("button", { name: "Cancel plan" })).toBeInTheDocument();
  });

  it("hides cancel plan link when cancellation is already scheduled", async () => {
    (getMe as jest.Mock).mockResolvedValue(scheduledCancellationProfile);
    (getBillingHistory as jest.Mock).mockResolvedValue({
      currentPlan: "PRO",
      subscriptionStatus: "ACTIVE",
      billingType: "MONTHLY",
      currentPeriodStart: "2026-03-01T00:00:00Z",
      currentPeriodEnd: "2026-04-20T00:00:00Z",
      cancelAtPeriodEnd: true,
      cancellationEffectiveAt: "2026-04-20T00:00:00Z",
      transactions: [],
    });

    render(<SettingsPage />);

    expect(await screen.findByText("Access ends Apr 20, 2026")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel plan" })).not.toBeInTheDocument();
  });

  it("renders the billing history empty state", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      planType: "FREE",
      subscription: { cancelAtPeriodEnd: false, premiumEndsAt: null, cancelledAt: null },
    });
    (getMyPlan as jest.Mock).mockResolvedValue(freeUsageSummary);
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
    expect(screen.getByText("Your payment history will appear here once you subscribe to Plus or Pro.")).toBeInTheDocument();
  });

  it("renders billing transactions from newest to oldest", async () => {
    (getBillingHistory as jest.Mock).mockResolvedValue({
      currentPlan: "PRO",
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
          description: "Pro Monthly",
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
    const descriptions = screen.getAllByText(/Failed payment|Pro Monthly/);
    expect(descriptions[0]).toHaveTextContent("Failed payment");
    expect(descriptions[1]).toHaveTextContent("Pro Monthly");
  });

  it("renders Preferences and Email Preferences before Plan & Billing and Account", async () => {
    render(<SettingsPage />);

    const preferencesHeading = await screen.findByRole("heading", { name: "Preferences" });
    const emailPreferencesHeading = screen.getByRole("heading", { name: "Email Preferences" });
    const billingHeading = screen.getByRole("heading", { name: "Plan & Billing" });
    const accountHeading = screen.getByRole("heading", { name: "Account" });

    expect(preferencesHeading.compareDocumentPosition(emailPreferencesHeading)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(emailPreferencesHeading.compareDocumentPosition(billingHeading)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(preferencesHeading.compareDocumentPosition(billingHeading)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(billingHeading.compareDocumentPosition(accountHeading)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it("requires DELETE confirmation before deleting the account", async () => {
    render(<SettingsPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Delete Account" }));

    const dialog = await screen.findByRole("dialog", { name: "Delete account?" });
    const confirmButton = within(dialog).getByRole("button", { name: "Delete Account" });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText("Type DELETE to confirm"), {
      target: { value: "DELETE" },
    });
    expect(confirmButton).toBeEnabled();
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(deleteAccount).toHaveBeenCalledWith("DELETE");
      expect(clearAuthUser).toHaveBeenCalled();
      expect(routerMock.push).toHaveBeenCalledWith("/login?reason=logged_out");
      expect(routerMock.refresh).toHaveBeenCalled();
    });
  });

  it("downloads account data from the Account section", async () => {
    render(<SettingsPage />);

    const downloadButton = await screen.findByRole("button", { name: "Download my data" });
    fireEvent.click(downloadButton);

    expect(downloadButton).toHaveAttribute("aria-busy", "true");
    await waitFor(() => {
      expect(downloadMyData).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => {
      expect(downloadButton).toBeEnabled();
    });
  });

  it("shows an inline error when account data download fails", async () => {
    const { ApiRequestError } = jest.requireMock("@/lib/api") as typeof import("@/lib/api");
    (downloadMyData as jest.Mock).mockRejectedValue(
      new ApiRequestError("Too many requests.", {
        code: "TOO_MANY_REQUESTS",
        status: 429,
      }),
    );

    render(<SettingsPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Download my data" }));

    expect(await screen.findByText("Please wait a moment before exporting again.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download my data" })).toBeEnabled();
  });

  it("warns about remaining active paid access before account deletion", async () => {
    const futureAccessEnd = new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString();
    (getBillingHistory as jest.Mock).mockResolvedValue({
      currentPlan: "PRO",
      subscriptionStatus: "ACTIVE",
      billingType: "MONTHLY",
      currentPeriodStart: new Date().toISOString(),
      currentPeriodEnd: futureAccessEnd,
      cancelAtPeriodEnd: false,
      cancellationEffectiveAt: null,
      transactions: [],
    });

    render(<SettingsPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Delete Account" }));

    expect(await screen.findByText("You'll lose your remaining 5 days of access — no refund.")).toBeInTheDocument();
  });

  it("does not show OCR usage in the settings usage section", async () => {
    render(<SettingsPage />);

    expect(await screen.findByText("Monthly Usage")).toBeInTheDocument();
    expect(screen.queryByText(/OCR:/)).not.toBeInTheDocument();
  });

  it("shows usage reset date and hides adaptive practice usage for free users", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      planType: "FREE",
      subscription: { cancelAtPeriodEnd: false, premiumEndsAt: null, cancelledAt: null },
    });
    (getMyPlan as jest.Mock).mockResolvedValue(freeUsageSummary);

    render(<SettingsPage />);

    expect(await screen.findByText("Usage resets on: April 15")).toBeInTheDocument();
    expect(screen.queryByTestId("usage-metric-adaptive-practice")).not.toBeInTheDocument();
    expect(within(screen.getByTestId("usage-metric-docx-exports")).getByText("1 / 2")).toBeInTheDocument();
    expect(within(screen.getByTestId("usage-metric-pdf-exports")).getByText("0 / 2")).toBeInTheDocument();
    expect(screen.getByText("1 remaining this cycle.")).toBeInTheDocument();
    expect(screen.getByText("2 remaining this cycle.")).toBeInTheDocument();
  });

  it("shows adaptive practice usage for Pro users", async () => {
    render(<SettingsPage />);

    expect(await screen.findByText("Usage resets on: April 20")).toBeInTheDocument();
    expect(screen.getByTestId("usage-metric-adaptive-practice")).toBeInTheDocument();
    expect(within(screen.getByTestId("usage-metric-adaptive-practice")).getByText("0 / 30")).toBeInTheDocument();
  });

  it("shows unlimited Teacher DOCX exports and the Teacher Plus callout", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      profileType: "TEACHER",
      planType: "PLUS",
      subscription: { cancelAtPeriodEnd: false, premiumEndsAt: "2026-04-20T00:00:00Z", cancelledAt: null },
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      ...freeUsageSummary,
      plan: "PLUS",
      limits: { ...freeUsageSummary.limits, docxExportsPerMonth: null, pdfExportsPerMonth: 15 },
      usage: { ...freeUsageSummary.usage, docxExportsUsed: 11, pdfExportsUsed: 3 },
      remaining: { ...freeUsageSummary.remaining, docxExportsRemaining: null, pdfExportsRemaining: 12 },
    });

    render(<SettingsPage />);

    expect(await screen.findByText("Teachers get unlimited quiz exports on Plus.")).toBeInTheDocument();
    expect(within(screen.getByTestId("usage-metric-docx-exports")).getByText("11 used / Unlimited")).toBeInTheDocument();
    expect(within(screen.getByTestId("usage-metric-pdf-exports")).getByText("3 / 15")).toBeInTheDocument();
  });

  it("shows reached-limit message without an upgrade button inside the metric", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      planType: "FREE",
      subscription: { cancelAtPeriodEnd: false, premiumEndsAt: null, cancelledAt: null },
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      ...freeUsageSummary,
      usage: { ...freeUsageSummary.usage, studyPacksUsed: 10 },
      remaining: { ...freeUsageSummary.remaining, studyPacksRemaining: 0 },
    });

    render(<SettingsPage />);

    const studyPackMetric = await screen.findByTestId("usage-metric-study-packs");
    expect(studyPackMetric).toHaveTextContent("April 15");
    expect(studyPackMetric).toHaveTextContent("You've reached your limit");
    expect(within(studyPackMetric).queryByRole("button")).not.toBeInTheDocument();
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
      ...proProfile,
      engagementMode: "STREAK",
      inactivityRemindersEnabled: false,
      weakConceptRemindersEnabled: false,
      weeklySummaryRemindersEnabled: false,
      marketingEmailsEnabled: false,
    });

    render(<SettingsPage />);

    fireEvent.click(await screen.findByDisplayValue("STREAK"));
    fireEvent.click(screen.getByRole("button", { name: "Save Learning Style" }));

    await waitFor(() => {
      expect(updateEngagementMode).toHaveBeenCalledWith({ engagementMode: "STREAK" });
    });
    expect(await screen.findByText("Learning style updated.")).toBeInTheDocument();
  });

  it("persists email preference toggles", async () => {
    (updateEmailPreferences as jest.Mock).mockResolvedValue({
      ...proProfile,
      inactivityRemindersEnabled: true,
      weakConceptRemindersEnabled: true,
      weeklySummaryRemindersEnabled: true,
      dueConceptsDigestRemindersEnabled: true,
      knowledgeImpactDigestRemindersEnabled: true,
      marketingEmailsEnabled: true,
      reviewDays: ["MONDAY"],
    });

    render(<SettingsPage />);

    expect(await screen.findByRole("heading", { name: "Email Preferences" })).toBeInTheDocument();
    expect(screen.getByText("Account & security — sign-in verification, password resets")).toBeInTheDocument();
    expect(screen.getByText("Billing — payment receipts, plan-expiry reminders, refunds")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("checkbox", { name: /Study reminders/i }));
    fireEvent.click(screen.getByRole("checkbox", { name: /Weak-concept nudges/i }));
    fireEvent.click(screen.getByRole("checkbox", { name: /Weekly summary/i }));
    fireEvent.click(screen.getByRole("checkbox", { name: /Due-concepts digest/i }));
    fireEvent.click(screen.getByRole("checkbox", { name: /Knowledge Impact digest/i }));
    fireEvent.click(screen.getByRole("checkbox", { name: /Product news & tips/i }));
    fireEvent.click(screen.getByRole("button", { name: "Mon" }));
    fireEvent.click(screen.getByRole("button", { name: "Save email preferences" }));

    await waitFor(() => {
      expect(updateEmailPreferences).toHaveBeenCalledWith({
        inactivityRemindersEnabled: true,
        weakConceptRemindersEnabled: true,
        weeklySummaryRemindersEnabled: true,
        dueConceptsDigestRemindersEnabled: true,
        knowledgeImpactDigestRemindersEnabled: true,
        marketingEmailsEnabled: true,
        reviewDays: ["MONDAY"],
      });
    });
    expect(await screen.findByText("Email preferences updated.")).toBeInTheDocument();
  });

  it("loads the Knowledge Impact digest preference for creators with public notes", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...proProfile,
      knowledgeImpactDigestRemindersEnabled: true,
    });

    render(<SettingsPage />);

    expect(await screen.findByRole("checkbox", { name: "Knowledge Impact digest" })).toBeChecked();
  });

  it("hides the Knowledge Impact digest preference when the account has no public notes", async () => {
    (getCreatorImpact as jest.Mock).mockResolvedValue({ distinctLearnersHelped: 0, notes: [] });

    render(<SettingsPage />);

    expect(await screen.findByRole("heading", { name: "Email Preferences" })).toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Knowledge Impact digest" })).not.toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Weekly summary" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Due-concepts digest" })).toBeInTheDocument();
  });

  it("shows a retryable notice instead of silently hiding the digest option when the impact check fails", async () => {
    (getCreatorImpact as jest.Mock).mockRejectedValueOnce(new Error("Network error"));

    render(<SettingsPage />);

    expect(
      await screen.findByText("Couldn't check your public notes, so the Knowledge Impact digest option isn't shown."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Knowledge Impact digest" })).not.toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Weekly summary" })).toBeInTheDocument();

    (getCreatorImpact as jest.Mock).mockResolvedValueOnce({
      distinctLearnersHelped: 0,
      notes: [{ noteId: "public-note-1", title: "Public note", distinctLearnersHelped: 0, viewCount: 0, copyCount: 0 }],
    });
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByRole("checkbox", { name: "Knowledge Impact digest" })).toBeInTheDocument();
    expect(
      screen.queryByText("Couldn't check your public notes, so the Knowledge Impact digest option isn't shown."),
    ).not.toBeInTheDocument();
  });

  it("persists the mobile navigation preference without optimistically changing the toggle", async () => {
    (updateMobileTabBarPreference as jest.Mock).mockResolvedValue({
      ...proProfile,
      mobileTabBarEnabled: false,
    });

    render(<SettingsPage />);

    const toggle = await screen.findByRole("checkbox", { name: "Show mobile navigation bar" });
    expect(toggle).toBeChecked();
    fireEvent.click(toggle);

    expect(toggle).toBeChecked();
    await waitFor(() => {
      expect(updateMobileTabBarPreference).toHaveBeenCalledWith({ mobileTabBarEnabled: false });
    });
    expect(await screen.findByText("Mobile navigation preference updated.")).toBeInTheDocument();
    expect(toggle).not.toBeChecked();
  });

  it("keeps the mobile navigation toggle unchanged when saving fails", async () => {
    (updateMobileTabBarPreference as jest.Mock).mockRejectedValue(new Error("Could not save preference."));

    render(<SettingsPage />);

    const toggle = await screen.findByRole("checkbox", { name: "Show mobile navigation bar" });
    fireEvent.click(toggle);

    expect(await screen.findByText("Could not save preference.")).toBeInTheDocument();
    expect(toggle).toBeChecked();
  });
});
