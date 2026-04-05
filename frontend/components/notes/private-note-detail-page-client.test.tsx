import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PrivateNoteDetailPageClient } from "./private-note-detail-page-client";
import {
  createStudyPackFromNote,
  completeProductOnboarding,
  getBillingPricing,
  getMyPlan,
  getChallengeQuizPerformanceSummary,
  getMe,
  getNote,
  getQuickReviewPerformanceSummary,
  listCoursePrograms,
  listSubjects,
  joinPremiumWaitlist,
  startQuickReviewSession,
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
let searchParamValues: Record<string, string> = {};
function createSearchParamsMock() {
  return {
    get: (key: string) => searchParamValues[key] ?? null,
    toString: () => new URLSearchParams(searchParamValues).toString(),
  };
}

let searchParamsMock = createSearchParamsMock();

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
  completeProductOnboarding: jest.fn(),
  copyNote: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  deleteNote: jest.fn(),
  getBillingPricing: jest.fn(),
  getMyPlan: jest.fn(),
  getChallengeQuizPerformanceSummary: jest.fn(),
  getMe: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  listCoursePrograms: jest.fn(),
  listSubjects: jest.fn(),
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
  courseProgram: "Nursing",
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
    searchParamValues = {};
    searchParamsMock = createSearchParamsMock();
    window.localStorage.clear();
    window.sessionStorage.clear();
    (getNote as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (createStudyPackFromNote as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (getMyPlan as jest.Mock).mockReset();
    (getChallengeQuizPerformanceSummary as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    (joinPremiumWaitlist as jest.Mock).mockReset();
    (startQuickReviewSession as jest.Mock).mockReset();
    (updateNote as jest.Mock).mockReset();
    (updateNoteVisibility as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry"]);
    (listCoursePrograms as jest.Mock).mockResolvedValue(["Nursing", "Senior High – STEM"]);
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
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
      courseProgram: "Nursing",
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
    (createStudyPackFromNote as jest.Mock).mockResolvedValue({
      title: "Suggested Title",
      subject: "Biology",
      tags: ["cells"],
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
        "Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, and tags.",
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

  it("shows the generate study pack guide for first-time users after creating a note", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "saved-note" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: null,
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Step 2: Generate your Study Pack")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "Generate Study Pack" }).at(-1) as HTMLButtonElement);

    expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
  });

  it("auto-generates when the page is opened with generate=1", async () => {
    searchParamValues = { generate: "1" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "FREE", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    await waitFor(() => {
      expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
    });
    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1", { scroll: false });
  });

  it("shows a first-study success banner after the first Study Pack is ready", async () => {
    window.localStorage.setItem("notelib-first-study-onboarding:user-1", JSON.stringify({ step: "study-pack-ready" }));
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      productOnboardingCompletedAt: null,
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByText("Your Study Pack is ready!")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start Challenge Quiz" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/challenge-quiz");
  });

  it("routes board exam note generation to quiz view after creating a Study Pack", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "BOARD_EXAM",
      productOnboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      title: "",
      subject: null,
      tags: [],
      studyPackStatus: "DRAFT",
    });
    (updateNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      title: "Suggested Title",
      subject: "Biology",
      tags: ["cells"],
      studyPackStatus: "STUDY_PACK_READY",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Study Pack" }));
    expect(await screen.findByText("AI Suggestions")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Apply Changes" }));

    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?created=1&tab=quiz");
    });
  });

  it("shows metadata suggestions after generating from note detail when the note already has metadata", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("AI Suggestions")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Skip" }));

    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?created=1&tab=summary");
    });
  });

  it("applies selected AI metadata choices from note detail without clearing course/program", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      title: "My Note",
      subject: "General Science",
      tags: ["review"],
      studyPackStatus: "DRAFT",
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("AI Suggestions")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Use AI Subject"));
    fireEvent.click(screen.getByLabelText("Merge My Tags + AI Tags"));
    fireEvent.click(screen.getByRole("button", { name: "Apply Changes" }));

    await waitFor(() => {
      expect(updateNote).toHaveBeenCalledWith("note-1", expect.objectContaining({
        title: "My Note",
        subject: "Biology",
        courseProgram: "Nursing",
        tags: ["review", "cells"],
      }));
      expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?created=1&tab=summary");
    });
  });

  it("shows the premium monthly-limit modal when Generate is clicked at the premium limit", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      planType: "PREMIUM",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
      profileType: "STUDENT",
    });
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "PREMIUM",
      limits: {
        studyPacksPerMonth: 100,
        challengeQuizzesPerMonth: 50,
        adaptivePracticePerMonth: 30,
        ocrPerMonth: 100,
      },
      usage: {
        studyPacksUsed: 100,
        challengeQuizzesUsed: 1,
        adaptivePracticeUsed: 1,
        ocrUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 0,
        challengeQuizzesRemaining: 49,
        adaptivePracticeRemaining: 29,
        ocrRemaining: 100,
      },
      usageCycle: {
        startsAt: "2026-03-20T00:00:00Z",
        endsAt: "2026-04-20T00:00:00Z",
      },
      features: {
        adaptivePracticeAvailable: true,
        difficultySelectionAvailable: true,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (getNote as jest.Mock).mockResolvedValue({ ...baseNote, studyPackStatus: "DRAFT" });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Generate Study Pack" }));

    expect(await screen.findByText("Monthly Limit Reached")).toBeInTheDocument();
    expect(screen.getByText(/Your limit resets on April 20\./)).toBeInTheDocument();
  });

  it("shows quiz view when tab=quiz is requested", async () => {
    searchParamValues = { tab: "quiz" };
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    render(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(await screen.findByRole("tab", { name: "Quiz" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "Key Concepts" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Full Notes" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Summary" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Practice Quiz" })).toBeInTheDocument();
  });

  it("switches study pack views through tabs without reloading", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    const summaryTab = await screen.findByRole("tab", { name: "Summary" });
    const quizTab = screen.getByRole("tab", { name: "Quiz" });

    expect(summaryTab).toHaveAttribute("aria-selected", "true");
    expect(getNote).toHaveBeenCalledTimes(1);

    fireEvent.click(quizTab);
    searchParamValues = { tab: "quiz" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=quiz", { scroll: false });
    expect(getNote).toHaveBeenCalledTimes(1);
    expect(screen.queryByText("Loading note...")).not.toBeInTheDocument();
    expect(await screen.findByRole("tab", { name: "Quiz" })).toHaveAttribute("aria-selected", "true");
  });

  it("uses the summary CTA to switch to Full Notes without refetching", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      content: "Original note body for review.",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "View Full Notes →" }));
    searchParamValues = { tab: "full-notes" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=full-notes", { scroll: false });
    expect(getNote).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("tab", { name: "Full Notes" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText(/Original note body for review\./i)).toBeInTheDocument();
  });

  it("shows the full original note content in the Full Notes tab without refetching", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ planType: "PREMIUM", emailVerifiedAt: "2026-03-21T09:00:00Z" });
    (getNote as jest.Mock).mockResolvedValue({
      ...baseNote,
      content: "Line one of the original note.\n\nLine two stays visible.",
      studyPackStatus: "STUDY_PACK_READY",
      studyPackId: "sp-1",
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: true,
      summary: "Generated summary",
      keyConcepts: ["Cells"],
      quiz: [
        {
          question: "What is a cell?",
          choices: ["Basic unit of life", "A tissue", "An organ", "A molecule"],
          correctAnswerIndex: 0,
          explanation: "Cells are the basic unit of life.",
        },
      ],
    });

    const { rerender } = render(<PrivateNoteDetailPageClient routeId="note-1" />);

    fireEvent.click(await screen.findByRole("tab", { name: "Full Notes" }));
    searchParamValues = { tab: "full-notes" };
    searchParamsMock = createSearchParamsMock();
    rerender(<PrivateNoteDetailPageClient routeId="note-1" />);

    expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=full-notes", { scroll: false });
    expect(getNote).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("tab", { name: "Full Notes" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("heading", { name: "Full Notes" })).toBeInTheDocument();
    expect(screen.getByText(/Line one of the original note\./i)).toBeInTheDocument();
    expect(screen.getByText(/Line two stays visible\./i)).toBeInTheDocument();
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
