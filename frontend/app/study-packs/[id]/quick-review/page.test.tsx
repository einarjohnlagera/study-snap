import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import QuickReviewPage from "./page";
import {
  completeProductOnboarding,
  completeQuickReviewSession,
  getNote,
  startQuickReviewSession,
  updateQuickReviewSessionProgress,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";

const pushMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: jest.fn(),
};
const searchParamsMock = {
  toString: () => "",
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1/quick-review",
  useParams: () => ({ id: "note-1" }),
  useSearchParams: () => searchParamsMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeProductOnboarding: jest.fn(),
  completeQuickReviewSession: jest.fn(),
  generateQuickReviewStudyTip: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  saveQuickReviewConfidence: jest.fn(),
  startQuickReviewSession: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateQuickReviewSessionProgress: jest.fn(),
}));

describe("QuickReviewPage first-study onboarding", () => {
  beforeEach(() => {
    pushMock.mockReset();
    window.localStorage.clear();
    (getAuthUser as jest.Mock).mockReset();
    (setAuthUser as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (completeQuickReviewSession as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (startQuickReviewSession as jest.Mock).mockReset();
    (updateQuickReviewSessionProgress as jest.Mock).mockReset();
  });

  it("shows the completion modal after the first quick review and routes to dashboard", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "study-pack-ready" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: null,
      displayName: "Note",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Cells",
      studyPackStatus: "STUDY_PACK_READY",
      quiz: [
        {
          question: "What is the powerhouse of the cell?",
          choices: ["Mitochondria", "Nucleus"],
          answer: "Mitochondria",
          explanation: "Mitochondria produce ATP.",
        },
      ],
      adaptivePracticeAvailable: false,
    });
    (startQuickReviewSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      currentQuestionIndex: 0,
      currentRound: "INITIAL",
      retryCount: 0,
      sessionState: {},
    });
    (updateQuickReviewSessionProgress as jest.Mock).mockResolvedValue({});
    (completeQuickReviewSession as jest.Mock).mockResolvedValue({
      id: "session-1",
      studyPackId: "study-pack-1",
      totalQuestions: 1,
      correctAnswers: 1,
      scorePercentage: 100,
      retryCount: 0,
      durationSeconds: 12,
      confidenceLevel: null,
      weakConcepts: [],
      createdAt: "2026-03-21T10:00:00Z",
      completedAt: "2026-03-21T10:01:00Z",
    });
    (completeProductOnboarding as jest.Mock).mockResolvedValue({
      displayName: "Note",
      profileType: null,
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T10:05:00Z",
    });

    render(<QuickReviewPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Mitochondria" }));
    fireEvent.click(screen.getByRole("button", { name: "Finish Quick Review" }));

    expect(await screen.findByText("You’re all set!")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Go to Dashboard" }));

    await waitFor(() => {
      expect(completeProductOnboarding).toHaveBeenCalledWith(false);
    });
    expect(pushMock).toHaveBeenCalledWith("/dashboard");
  });
});
