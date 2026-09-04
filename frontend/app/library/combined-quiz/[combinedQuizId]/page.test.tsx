import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import CombinedQuizSharePage from "./page";
import {
  createCombinedQuizShareLink,
  getCombinedQuiz,
  getCombinedQuizShareLink,
  toggleQuizShareLink,
  trackAnalyticsEvent,
} from "@/lib/api";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useParams: () => ({ combinedQuizId: "combined-1" }),
  useRouter: () => ({ push: pushMock }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/hooks/use-billing-usage-summary", () => ({
  useBillingUsageSummary: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => ({ id: "supporter-1", role: "USER", profileType: "SUPPORTER", planType: "FREE" })),
}));

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error {
    status: number;
    constructor(message: string, options: { status?: number } = {}) {
      super(message);
      this.status = options.status ?? 500;
    }
  },
  createCombinedQuizShareLink: jest.fn(),
  getCombinedQuiz: jest.fn(),
  getCombinedQuizShareLink: jest.fn(),
  isQuizShareLinkLimitExceededError: jest.fn(() => false),
  toggleQuizShareLink: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

const combinedQuiz = {
  id: "combined-1",
  title: "Unit review",
  sections: [{ title: "Cell biology", questions: [{ question: "Q", choices: [], correctIndex: 0, explanation: "" }] }],
  createdAt: "2026-09-03T00:00:00Z",
};

describe("CombinedQuizSharePage", () => {
  afterEach(async () => {
    await act(async () => {
      await new Promise((resolve) => globalThis.setTimeout(resolve, 0));
    });
    cleanup();
  });

  beforeEach(() => {
    pushMock.mockReset();
    (getCombinedQuiz as jest.Mock).mockReset().mockResolvedValue(combinedQuiz);
    (getCombinedQuizShareLink as jest.Mock).mockReset().mockResolvedValue(null);
    (createCombinedQuizShareLink as jest.Mock).mockReset().mockResolvedValue({
      id: "link-1",
      token: "token-1",
      shareUrl: "https://notelib.test/quiz/token-1",
      isActive: true,
      createdAt: "2026-09-03T00:00:00Z",
    });
    (toggleQuizShareLink as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: { plan: "FREE", remaining: { quizShareLinksRemaining: 2 } },
      refreshUsageSummary: jest.fn(),
    });
  });

  it("loads the snapshot and reads the existing share link without POSTing on a fresh mount", async () => {
    render(<CombinedQuizSharePage />);

    expect(await screen.findByRole("heading", { name: "Unit review" })).toBeInTheDocument();
    expect(getCombinedQuiz).toHaveBeenCalledWith("combined-1");
    expect(getCombinedQuizShareLink).toHaveBeenCalledWith("combined-1");
    expect(createCombinedQuizShareLink).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Create share link" })).toBeInTheDocument();
  });

  it("keeps an inactive link visible on refresh and does not create a replacement", async () => {
    (getCombinedQuizShareLink as jest.Mock).mockResolvedValue({
      id: "link-1",
      token: "token-1",
      shareUrl: "https://notelib.test/quiz/token-1",
      isActive: false,
      createdAt: "2026-09-03T00:00:00Z",
    });

    render(<CombinedQuizSharePage />);

    expect(await screen.findByText("Sharing off")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Turn sharing on" })).toBeInTheDocument();
    expect(createCombinedQuizShareLink).not.toHaveBeenCalled();
  });

  it("creates a link only from the explicit action and reuses the existing analytics event with combined metadata", async () => {
    render(<CombinedQuizSharePage />);
    fireEvent.click(await screen.findByRole("button", { name: "Create share link" }));

    await waitFor(() => expect(createCombinedQuizShareLink).toHaveBeenCalledWith("combined-1"));
    expect(await screen.findByText("Share link created.")).toBeInTheDocument();
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "QUIZ_SHARE_LINK_CREATED",
      entityId: "combined-1",
      metadata: { scope: "combined_quiz", sourceNoteCount: 1, token: "token-1" },
    });
  });

  it("renders a retry state for a transient share-link read failure instead of treating it as no link", async () => {
    (getCombinedQuizShareLink as jest.Mock).mockRejectedValue(new Error("Network unavailable"));
    render(<CombinedQuizSharePage />);

    expect(await screen.findByText("Could not load share link")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create share link" })).not.toBeInTheDocument();
  });
});
