import { fireEvent, render, screen } from "@testing-library/react";
import { PrivateNoteDetailPageClient } from "./private-note-detail-page-client";
import {
  getBillingPricing,
  getMyPlan,
  getChallengeQuizPerformanceSummary,
  getNote,
  getQuickReviewPerformanceSummary,
  joinPremiumWaitlist,
  updateNote,
  updateNoteVisibility,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};
const searchParamsMock = {
  get: () => null,
  toString: () => "",
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1",
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
  copyNote: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  deleteNote: jest.fn(),
  getBillingPricing: jest.fn(),
  getMyPlan: jest.fn(),
  getChallengeQuizPerformanceSummary: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  isEmailNotVerifiedError: () => false,
  joinPremiumWaitlist: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateNote: jest.fn(),
  updateNoteVisibility: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
  startQuickReviewSession: jest.fn(),
}));

const baseNote = {
  id: "note-1",
  title: "Test Note",
  subject: "Biology",
  tags: ["cells"],
  content: "Cell content",
  visibility: "PRIVATE" as const,
  createdAt: "2026-03-21T10:00:00Z",
  updatedAt: "2026-03-21T10:30:00Z",
  copiedFromNoteId: null,
  copiedFromUserId: null,
  copiedFromTitle: null,
  copiedFromPublic: false,
  copiedAt: null,
  studyPackId: null,
  studyPackStatus: "DRAFT" as const,
  summary: null,
  keyConcepts: [],
  quiz: [],
  quizCount: 0,
  quickReviewAvailable: false,
  challengeQuizAvailable: false,
  adaptivePracticeAvailable: false,
  difficultySelectionAvailable: false,
};

describe("PrivateNoteDetailPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    window.localStorage.clear();
    window.sessionStorage.clear();
    (getNote as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (getChallengeQuizPerformanceSummary as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
    (joinPremiumWaitlist as jest.Mock).mockReset();
    (updateNote as jest.Mock).mockReset();
    (updateNoteVisibility as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
      },
      usage: {
        studyPacksUsed: 2,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 8,
        challengeQuizzesRemaining: 5,
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
    (getQuickReviewPerformanceSummary as jest.Mock).mockResolvedValue({
      attempts: 1,
      bestScorePercentage: 80,
      lastScorePercentage: 80,
      lastReviewedAt: "2026-03-21T10:30:00Z",
    });
    (getChallengeQuizPerformanceSummary as jest.Mock).mockResolvedValue({
      attempts: 1,
      bestScorePercentage: 75,
      lastScorePercentage: 75,
      lastCompletedAt: "2026-03-21T10:30:00Z",
      latestPerformanceLevel: "Good",
      latestWeakConcepts: ["Cells"],
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

  it("routes Edit to note editor for draft note", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    const editButton = screen.getByRole("button", { name: "Edit" });
    fireEvent.click(editButton);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/edit");
  });

  it("disables Generate Study Pack and visibility toggle for unverified users", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: null });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PRIVATE" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    const generateButton = screen.getByRole("button", { name: "Generate Study Pack" });
    const visibilityButton = screen.getByRole("button", { name: /private/i });

    expect(generateButton).toBeDisabled();
    expect(visibilityButton).toBeDisabled();
  });

  it("for generated notes, Edit enables inline metadata editing instead of routing", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));

    expect(pushMock).not.toHaveBeenCalledWith("/notes/note-1/edit");
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Note content cannot be edited after generating a Study Pack. You can still update the title, subject, and tags.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Share" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Quick Review" })).not.toBeInTheDocument();
  });

  it("shows private-share modal and then opens share-link modal after making note public", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PRIVATE" });
    (updateNoteVisibility as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT", visibility: "PUBLIC" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await screen.findByText("Test Note");
    fireEvent.click(screen.getByRole("button", { name: "Share" }));

    expect(screen.getByText("This note is private")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Make Public & Share" }));

    expect(updateNoteVisibility).toHaveBeenCalledWith("note-1", "PUBLIC");
    expect(await screen.findByText("Share this note")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copy Link" })).toBeInTheDocument();
  });

  it("lets free users start Challenge Quiz without showing the premium modal", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz");
    expect(screen.queryByText("Adaptive Practice is a Premium feature")).not.toBeInTheDocument();
  });

  it("shows a paywall modal when a free user clicks Adaptive Practice", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Adaptive Practice" }));

    expect(await screen.findByText("Adaptive Practice is a Premium feature")).toBeInTheDocument();
  });

  it("lets Premium users go straight to Challenge Quiz without showing the paywall modal", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PREMIUM",
      limits: {
        studyPacksPerMonth: 100,
        challengeQuizzesPerMonth: 50,
        adaptivePracticePerMonth: 30,
        ocrPerMonth: 100,
      },
      usage: {
        studyPacksUsed: 12,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 1,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 88,
        challengeQuizzesRemaining: 49,
        adaptivePracticeRemaining: 29,
        ocrRemaining: 100,
      },
      features: {
        adaptivePracticeAvailable: true,
        difficultySelectionAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      difficultySelectionAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz");
    expect(screen.queryByText("Unlock Exam Mode")).not.toBeInTheDocument();
  });
});
