import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import DashboardPage from "./page";
import {
  completeProductOnboarding,
  completeOnboarding,
  getContinueStudyingRecommendation,
  getDashboardOverview,
  generateAdaptivePracticeForCollection,
  getFeedbackPromptContext,
  getCollectionGoal,
  getGoalSummary,
  getLinkedLearners,
  getMe,
  getNote,
  getQuickReviewLastReviewedBatch,
  getQuickReviewPerformanceSummary,
  getTodayFocus,
  listNotes,
  listCollections,
  listPublicNotes,
  updateLearningProfileContext,
} from "@/lib/api";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { setAuthUser } from "@/lib/auth";
import {
  clearPendingLightweightProfileCompletion,
  hasPendingLightweightProfileCompletion,
  setPendingLightweightProfileCompletion,
} from "@/lib/onboarding-v2";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
};
const mockDashboardStudyPlanSection = jest.fn((props: Record<string, unknown>) => (
  <div
    data-testid="dashboard-study-plan-section"
    data-discovery-presentation={String(props.discoveryPresentation ?? "")}
  />
));

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/dashboard",
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/components/dashboard/discovery-intent-consumer", () => ({
  DiscoveryIntentConsumer: () => <div data-testid="discovery-intent-consumer" />,
}));
jest.mock("@/components/dashboard/linked-learner-invitation-intent-consumer", () => ({
  LinkedLearnerInvitationIntentConsumer: () => <div data-testid="linked-learner-invitation-intent-consumer" />,
}));

jest.mock("./dashboard-study-plan-section", () => ({
  DashboardStudyPlanSection: (props: Record<string, unknown>) => mockDashboardStudyPlanSection(props),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ id: "user-1", emailVerifiedAt: "2026-03-20T00:00:00Z", productOnboardingCompletedAt: null }),
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeProductOnboarding: jest.fn(),
  completeOnboarding: jest.fn(),
  createPremiumCheckoutSession: jest.fn(),
  getContinueStudyingRecommendation: jest.fn(),
  getDashboardOverview: jest.fn(),
  generateAdaptivePracticeForCollection: jest.fn(),
  getFeedbackPromptContext: jest.fn(),
  getCollectionGoal: jest.fn(),
  getGoalSummary: jest.fn(),
  getLinkedLearners: jest.fn(),
  getMe: jest.fn(),
  getNote: jest.fn(),
  getUserNotePerformanceSummary: jest.fn().mockResolvedValue([]),
  getQuickReviewLastReviewedBatch: jest.fn(),
  getQuickReviewPerformanceSummary: jest.fn(),
  getTodayFocus: jest.fn(),
  listCollections: jest.fn(),
  listNotes: jest.fn(),
  listPublicNotes: jest.fn(),
  setStudyGoal: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateLearningProfileContext: jest.fn(),
}));

jest.mock("@/hooks/use-billing-usage-summary", () => ({
  useBillingUsageSummary: jest.fn(),
}));

const notes = [
  {
    id: "note-1",
    title: "Biology Review",
    subject: "Biology",
    tags: ["Cells"],
    updatedAt: "2026-03-24T00:00:00Z",
    contentPreview: "Biology study content",
    studyPackStatus: "STUDY_PACK_READY",
    studyPackId: "pack-1",
    quizCount: 8,
  },
  {
    id: "note-2",
    title: "History Draft",
    subject: "History",
    tags: [],
    updatedAt: "2026-03-23T00:00:00Z",
    contentPreview: "Draft classroom material",
    studyPackStatus: "DRAFT",
    studyPackId: null,
    quizCount: 0,
  },
] as const;

const overview = {
  performanceSummary: {
    averageQuizScore: 82,
    totalQuizzesTaken: 4,
    studyPacksCreated: 2,
    strongestConcept: { conceptName: "Cell Theory", accuracyPercentage: 92 },
    weakestConcept: { conceptName: "Mitosis", accuracyPercentage: 48 },
  },
  focusAreas: {
    concepts: [
      { conceptName: "Mitosis", accuracyPercentage: 48 },
      { conceptName: "DNA Replication", accuracyPercentage: 55 },
    ],
    practiceNoteId: "note-1",
      practiceCollectionId: null,
      practiceCollectionTitle: null,
    adaptivePracticeAvailable: false,
  },
  weeklyActivity: {
    studyPacksCreated: 1,
    quizzesTaken: 2,
    adaptiveSessions: 0,
    studyDays: 2,
  },
  examPacingPlan: null,
  totalNoteCount: 25,
  hasQuizQuestions: true,
  mostRecentReadyNoteId: "note-1",
};

const primaryCollectionGoal = {
  collectionId: "primary-review-set-1",
  title: "PNLE Mastery",
  description: "A focused nursing review journey.",
  visibility: "PRIVATE",
  courseProgram: "PNLE",
  targetCompletionDate: null,
  companion: null,
  companionMayBeOutdated: false,
  sourcePlanId: "official-pnle-set-1",
  parentCollectionId: null,
  itemCount: 0,
  childCount: 2,
  overallReadinessPercentage: 64,
  masteredConcepts: 32,
  dueConcepts: 5,
  notPracticedConcepts: 8,
  totalConcepts: 50,
  weeksRemaining: null,
  conceptsRemaining: null,
  todaysConceptBudget: null,
  weeklyFocusByDay: [],
  createdAt: "2026-07-24T00:00:00Z",
  updatedAt: "2026-07-24T00:00:00Z",
  children: [
    {
      collectionId: "primary-subject-1",
      title: "Foundations",
      description: null,
      itemCount: 3,
      overallReadinessPercentage: 40,
      masteredConcepts: 10,
      dueConcepts: 5,
      notPracticedConcepts: 4,
      totalConcepts: 25,
      todaysConceptBudget: null,
    },
  ],
};

const publicNotes = [
  {
    id: "public-note-1",
    ownerUserId: null,
    title: "PNLE Fundamentals",
    courseProgram: "PNLE",
    subject: "Nursing",
    tags: ["review"],
    contentPreview: "Nursing board exam fundamentals.",
    summaryPreview: "A quick PNLE review summary.",
    visibility: "PUBLIC",
    studyPackId: "pack-1",
    studyPackStatus: "STUDY_PACK_READY",
    quizCount: 12,
    copyCount: 4,
    likeCount: 2,
    shareCount: 1,
    viewCount: 30,
    authorDisplayName: "Creator",
    authorUsername: "creator",
    isOfficialAuthor: false,
    isCurrentUser: false,
    createdAt: "2026-03-20T00:00:00Z",
    updatedAt: "2026-03-21T00:00:00Z",
    likedByCurrentUser: false,
  },
] as const;

describe("DashboardPage profile variants", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    (listNotes as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getContinueStudyingRecommendation as jest.Mock).mockReset();
    (getDashboardOverview as jest.Mock).mockReset();
    (generateAdaptivePracticeForCollection as jest.Mock).mockReset();
    (getFeedbackPromptContext as jest.Mock).mockReset();
    (getCollectionGoal as jest.Mock).mockReset();
    (getGoalSummary as jest.Mock).mockReset();
    (getLinkedLearners as jest.Mock).mockReset();
    (getQuickReviewLastReviewedBatch as jest.Mock).mockReset();
    (getQuickReviewPerformanceSummary as jest.Mock).mockReset();
    (getTodayFocus as jest.Mock).mockReset();
    (listPublicNotes as jest.Mock).mockReset();
    mockDashboardStudyPlanSection.mockClear();
    (getNote as jest.Mock).mockReset();
    (completeProductOnboarding as jest.Mock).mockReset();
    (completeOnboarding as jest.Mock).mockReset();
    (updateLearningProfileContext as jest.Mock).mockReset();
    (setAuthUser as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReset();
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });
    clearPendingLightweightProfileCompletion("user-1");

    (listNotes as jest.Mock).mockResolvedValue(notes);
    (getContinueStudyingRecommendation as jest.Mock).mockResolvedValue({
      noteId: "note-1",
      noteTitle: "Biology Review",
      subject: "Biology",
      courseProgram: "Nursing",
      summaryPreview: "Resume this biology review.",
      resumeType: "QUICK_REVIEW",
      reason: "RESUME_REVIEW",
      currentQuestionIndex: 1,
      totalQuestions: 8,
      resumeState: "QUESTION_IN_PROGRESS",
    });
    (getDashboardOverview as jest.Mock).mockResolvedValue(overview);
    (getFeedbackPromptContext as jest.Mock).mockResolvedValue({
      returningAfterInactivity: false,
      hasCompletedQuizSession: true,
    });
    (getTodayFocus as jest.Mock).mockResolvedValue({
      type: "REVIEW_PACK",
      studyPackId: "pack-1",
      noteId: "note-1",
      title: "Reinforce Biology Review",
      message: "A quick review today can strengthen your understanding.",
      actionLabel: "Start Quick Review",
      concepts: [],
      adaptivePracticeAvailable: false,
    });
    (getGoalSummary as jest.Mock).mockResolvedValue(null);
    (getLinkedLearners as jest.Mock).mockResolvedValue([]);
    (getQuickReviewLastReviewedBatch as jest.Mock).mockResolvedValue([]);
    (getQuickReviewPerformanceSummary as jest.Mock).mockResolvedValue(null);
    (listPublicNotes as jest.Mock).mockResolvedValue({ items: publicNotes, total: publicNotes.length });
    (listCollections as jest.Mock).mockResolvedValue([]);
  });

  it("gives a supporter-only account a direct home surface for accepted and pending learners", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Mara",
      displayName: "Mara",
      emailVerifiedAt: "2026-08-19T00:00:00Z",
      productOnboardingCompletedAt: "2026-08-19T00:00:00Z",
      studyPackCount: 0,
      profileType: "STUDENT",
      courseProgram: "",
      onboardingCompletedAt: "2026-08-19T00:00:00Z",
    });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (getDashboardOverview as jest.Mock).mockResolvedValue({ ...overview, totalNoteCount: 0 });
    (getLinkedLearners as jest.Mock).mockResolvedValue([
      {
        id: "accepted-link",
        callerRole: "SUPPORTER",
        initiatedBy: "SUPPORTER",
        incomingInvitation: false,
        counterpartyDisplayName: "Alex Learner",
        counterpartyEmail: "alex@example.com",
        status: "ACCEPTED",
        createdAt: "2026-08-18T00:00:00Z",
        acceptedAt: "2026-08-19T00:00:00Z",
        revokedAt: null,
        birthYearRequired: false,
        guardianConsentRequired: false,
        guardianConsentRecorded: false,
        progressSharedWithMe: true,
      },
      {
        id: "accepted-without-progress",
        callerRole: "SUPPORTER",
        initiatedBy: "SUPPORTER",
        incomingInvitation: false,
        counterpartyDisplayName: "No Grant Learner",
        counterpartyEmail: "no-grant@example.com",
        status: "ACCEPTED",
        createdAt: "2026-08-18T00:00:00Z",
        acceptedAt: "2026-08-19T00:00:00Z",
        revokedAt: null,
        birthYearRequired: false,
        guardianConsentRequired: false,
        guardianConsentRecorded: false,
        progressSharedWithMe: false,
      },
      {
        id: "pending-link",
        callerRole: "SUPPORTER",
        initiatedBy: "SUPPORTER",
        incomingInvitation: false,
        counterpartyDisplayName: "Sam Learner",
        counterpartyEmail: "sam@example.com",
        status: "PENDING",
        createdAt: "2026-08-19T00:00:00Z",
        acceptedAt: null,
        revokedAt: null,
        birthYearRequired: false,
        guardianConsentRequired: false,
        guardianConsentRecorded: false,
      },
    ]);

    render(<DashboardPage />);

    expect(await screen.findByRole("heading", { name: "People you support" })).toBeInTheDocument();
    expect(screen.getByText("Alex Learner")).toBeInTheDocument();
    expect(screen.getByText("Sam Learner")).toBeInTheDocument();
    expect(screen.getByText("No Grant Learner is not sharing progress with you.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View progress" })).toHaveAttribute(
      "href",
      "/linked-learners/accepted-link/progress",
    );
    expect(screen.getAllByRole("link", { name: "View progress" })).toHaveLength(1);
    // ⚠️ This PENDING row carries no birth-year or consent blocker, which since V122 can only be a
    // LEGACY pre-migration row. The copy must stay neutral: claiming the invitation still needs
    // accepting is false for every row written after the migration.
    expect(screen.getByText("Connection not active yet")).toBeInTheDocument();
    expect(screen.queryByText(/invitation pending/i)).not.toBeInTheDocument();
    expect(
      screen.queryByText("Progress becomes available after the invitation is accepted."),
    ).not.toBeInTheDocument();
  });

  it("names the actual reason a supported connection is pending, per blocker", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Sup",
      displayName: "Sup",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      profileType: "STUDENT",
    });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (getDashboardOverview as jest.Mock).mockResolvedValue({ ...overview, totalNoteCount: 0 });
    const pendingLink = (id: string, name: string, overrides: Record<string, unknown>) => ({
      id,
      callerRole: "SUPPORTER",
      initiatedBy: "SUPPORTER",
      incomingInvitation: false,
      counterpartyDisplayName: name,
      counterpartyEmail: `${id}@example.com`,
      status: "PENDING",
      createdAt: "2026-08-19T00:00:00Z",
      acceptedAt: null,
      revokedAt: null,
      birthYearRequired: false,
      guardianConsentRequired: false,
      guardianConsentRecorded: false,
      ...overrides,
    });
    (getLinkedLearners as jest.Mock).mockResolvedValue([
      pendingLink("year-link", "Year Learner", { birthYearRequired: true }),
      pendingLink("consent-link", "Consent Learner", { guardianConsentRequired: true }),
      pendingLink("recorded-link", "Recorded Learner", {
        guardianConsentRequired: true,
        guardianConsentRecorded: true,
      }),
    ]);

    render(<DashboardPage />);

    expect(await screen.findByRole("heading", { name: "People you support" })).toBeInTheDocument();
    expect(screen.getByText("Waiting on the learner's birth year")).toBeInTheDocument();
    expect(screen.getByText("Paused — guardian consent required")).toBeInTheDocument();
    expect(screen.getByText("Guardian consent recorded")).toBeInTheDocument();
    // ⚠️ None of these are waiting to be accepted — acceptance already happened.
    expect(screen.queryByText(/invitation pending/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "View progress" })).not.toBeInTheDocument();
  });

  it("renders the student dashboard with review-first sections", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Continue Studying")).toBeInTheDocument();
    expect(screen.getAllByText("Biology Review")).not.toHaveLength(0);
    expect(screen.getByText("Biology • Nursing")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Resume Quick Review" })).toHaveAttribute("href", "/notes/note-1/quick-review");
    expect(screen.getByText("Weak Concepts")).toBeInTheDocument();
    expect(screen.getByText("Recent Notes")).toBeInTheDocument();
    expect(screen.getByText("25 saved")).toBeInTheDocument();
    expect(mockDashboardStudyPlanSection).toHaveBeenCalled();
    for (const [props] of mockDashboardStudyPlanSection.mock.calls) {
      expect(props).toEqual(expect.objectContaining({ discoveryPresentation: "recommendation" }));
      expect(props).not.toHaveProperty("viewAllHref");
      expect(props).not.toHaveProperty("browseWhenEmpty");
    }
    expect(await screen.findByRole("heading", { name: "Notes for PNLE" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "See all in Explore →" }))
      .toHaveAttribute("href", "/explore?tab=notes&source=dashboard&courseProgram=pnle");
    expect(screen.getByText("Quick Review")).toBeInTheDocument();
    expect(screen.getByText("Usage / Progress")).toBeInTheDocument();
    expect(screen.queryByText("Exam Countdown")).not.toBeInTheDocument();
    expect(screen.queryByText("Create Quiz")).not.toBeInTheDocument();
    expect(listNotes).toHaveBeenCalledWith(20);
  });

  it("renders the primary review set hero above unchanged dashboard sections", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      primaryCollectionId: primaryCollectionGoal.collectionId,
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getCollectionGoal as jest.Mock).mockResolvedValue(primaryCollectionGoal);

    render(<DashboardPage />);

    const identityLink = await screen.findByRole("link", { name: "PNLE Mastery" });
    expect(screen.getByText("Primary Review Set")).toBeInTheDocument();
    expect(screen.getByText("Review due concepts")).toBeInTheDocument();
    expect(screen.getByText("64%")).toBeInTheDocument();
    expect(identityLink).toHaveAttribute("href", "/collections/primary-review-set-1");
    expect(screen.getByRole("link", { name: "Continue Studying" })).toHaveAttribute(
      "href",
      "/collections/primary-review-set-1",
    );
    expect(getCollectionGoal).toHaveBeenCalledWith("primary-review-set-1");

    const boardExamHeading = screen.getByRole("heading", { name: "Start Board Exam" });
    expect(identityLink.compareDocumentPosition(boardExamHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Weak Areas" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Adaptive Practice" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Study Activity This Week" })).toBeInTheDocument();
    expect(mockDashboardStudyPlanSection).toHaveBeenCalled();
    for (const [props] of mockDashboardStudyPlanSection.mock.calls) {
      expect(props).toEqual(expect.objectContaining({ discoveryPresentation: "recommendation" }));
      expect(props).not.toHaveProperty("viewAllHref");
      expect(props).not.toHaveProperty("browseWhenEmpty");
    }
  });

  it("shows a primary review set skeleton while its details load", async () => {
    let resolvePrimaryCollection: (goal: typeof primaryCollectionGoal) => void = () => undefined;
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      primaryCollectionId: primaryCollectionGoal.collectionId,
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getCollectionGoal as jest.Mock).mockReturnValue(new Promise<typeof primaryCollectionGoal>((resolve) => {
      resolvePrimaryCollection = resolve;
    }));

    render(<DashboardPage />);

    expect(await screen.findByLabelText("Loading primary review set")).toBeInTheDocument();
    expect(screen.queryByText("Studying for a board exam?")).not.toBeInTheDocument();

    resolvePrimaryCollection(primaryCollectionGoal);
    expect(await screen.findByRole("link", { name: "PNLE Mastery" })).toBeInTheDocument();
  });

  it("falls back to the existing goal prompt when the primary review set cannot load", async () => {
    const warnSpy = jest.spyOn(globalThis.console, "warn").mockImplementation(() => undefined);
    let rejectPrimaryCollection: (error: Error) => void = () => undefined;
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "Mathematics",
      primaryCollectionId: primaryCollectionGoal.collectionId,
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getCollectionGoal as jest.Mock).mockReturnValue(new Promise<typeof primaryCollectionGoal>((_, reject) => {
      rejectPrimaryCollection = reject;
    }));

    render(<DashboardPage />);

    expect(await screen.findByLabelText("Loading primary review set")).toBeInTheDocument();
    rejectPrimaryCollection(new Error("not found"));
    expect(await screen.findByText("Track your progress in Mathematics.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Loading primary review set")).not.toBeInTheDocument();
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it("shows the one-time welcome-back feedback ask from the inactivity context", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      id: "user-1",
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getFeedbackPromptContext as jest.Mock).mockResolvedValue({
      returningAfterInactivity: true,
      hasCompletedQuizSession: true,
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Welcome back — what got in the way?")).toBeInTheDocument();
  });

  it("does not block or show the welcome-back ask when feedback context fails", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      id: "user-1",
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getFeedbackPromptContext as jest.Mock).mockRejectedValue(new Error("context unavailable"));

    render(<DashboardPage />);

    expect(await screen.findByRole("heading", { name: "Continue Studying" })).toBeInTheDocument();
    expect(screen.queryByText("Welcome back — what got in the way?")).not.toBeInTheDocument();
  });

  it("batches Quick Review timestamps once for all eligible recent notes", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 5,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });
    const eligibleNotes = Array.from({ length: 5 }, (_, index) => ({
      ...notes[0],
      id: `ready-note-${index + 1}`,
      studyPackId: `pack-${index + 1}`,
      updatedAt: `2026-03-${25 - index}T00:00:00Z`,
    }));
    (listNotes as jest.Mock).mockResolvedValue(eligibleNotes);
    (getQuickReviewLastReviewedBatch as jest.Mock).mockResolvedValue([
      { noteId: "ready-note-1", lastReviewedAt: "2026-03-25T12:00:00Z" },
    ]);

    render(<DashboardPage />);

    await waitFor(() => {
      expect(getQuickReviewLastReviewedBatch).toHaveBeenCalledTimes(1);
    });
    expect(getQuickReviewLastReviewedBatch).toHaveBeenCalledWith([
      "ready-note-1",
      "ready-note-2",
      "ready-note-3",
      "ready-note-4",
    ]);
    expect(getQuickReviewPerformanceSummary).not.toHaveBeenCalled();
  });

  it("keeps Stage 1 Dashboard content visible when the Quick Review batch fails", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });
    (getQuickReviewLastReviewedBatch as jest.Mock).mockRejectedValue(new Error("history unavailable"));

    render(<DashboardPage />);

    expect(await screen.findByText("Recent Notes")).toBeInTheDocument();
    expect(screen.getAllByText("Biology Review")).not.toHaveLength(0);
    expect(screen.queryByRole("heading", { name: "We could not load your notes" })).not.toBeInTheDocument();
    expect(getQuickReviewPerformanceSummary).not.toHaveBeenCalled();
  });

  it("moves Quick Review below Usage / Progress after a completed session while preserving first-time order", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });
    (listNotes as jest.Mock).mockResolvedValue(notes.map((note) => ({ ...note, quizCount: 0 })));
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      hasQuizQuestions: false,
    });

    const firstTimeRender = render(<DashboardPage />);
    const firstTimeQuickReview = await screen.findByText("Quick Review");
    const firstTimeUsage = screen.getByText("Usage / Progress");

    expect(firstTimeQuickReview.compareDocumentPosition(firstTimeUsage) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    firstTimeRender.unmount();
    (listNotes as jest.Mock).mockResolvedValue(notes);
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      hasQuizQuestions: true,
    });

    render(<DashboardPage />);
    const returningQuickReview = await screen.findByText("Quick Review");
    const returningUsage = screen.getByText("Usage / Progress");

    expect(returningUsage.compareDocumentPosition(returningQuickReview) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("shows the personalization prompt after onboarding and routes to profile", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Too easy or too hard?")).toBeInTheDocument();
    expect(screen.getByText("You can adjust your learner level anytime — quizzes will match your new study stage next time you practice.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Adjust level" }));

    expect(routerMock.push).toHaveBeenCalledWith("/profile?from=dashboard#learning-profile");
  });

  it("shows a non-blocking profile completion prompt for pending copy-on-signup users", async () => {
    setPendingLightweightProfileCompletion("user-1");
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: null,
      studyPackCount: 1,
      profileType: null,
      learnerLevel: null,
      courseProgram: null,
      examDate: null,
      onboardingCompletedAt: null,
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });

    render(<DashboardPage />);

    expect(await screen.findByText("Finish setting up your study profile")).toBeInTheDocument();
    expect(screen.getByText("Continue Studying")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Dismiss profile completion prompt" }));

    await waitFor(() => {
      expect(screen.queryByText("Finish setting up your study profile")).not.toBeInTheDocument();
    });
    expect(hasPendingLightweightProfileCompletion("user-1")).toBe(true);
  });

  it("does not show the lightweight prompt once copy-on-signup profile fields are complete", async () => {
    setPendingLightweightProfileCompletion("user-1");
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: null,
      studyPackCount: 1,
      profileType: "STUDENT",
      learnerLevel: "COLLEGE",
      courseProgram: "Nursing",
      examDate: null,
      onboardingCompletedAt: "2026-03-20T00:05:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });

    render(<DashboardPage />);

    await screen.findByText("Continue Studying");
    await waitFor(() => {
      expect(screen.queryByText("Finish setting up your study profile")).not.toBeInTheDocument();
    });
  });

  it("persists dismissal of the personalization prompt per user", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    const { unmount } = render(<DashboardPage />);

    expect(await screen.findByText("Too easy or too hard?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Dismiss personalization prompt" }));

    await waitFor(() => {
      expect(screen.queryByText("Too easy or too hard?")).not.toBeInTheDocument();
    });
    expect(globalThis.localStorage.getItem("notelib-dashboard-personalization-prompt:user-1")).toBe("1");

    unmount();
    render(<DashboardPage />);

    await screen.findByText("Continue Studying");
    expect(screen.queryByText("Too easy or too hard?")).not.toBeInTheDocument();
  });

  it("shows a dashboard goal card for an exam goal", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      studyGoal: "pnle",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getGoalSummary as jest.Mock).mockResolvedValue({
      studyGoal: "pnle",
      goalType: "EXAM",
      goalName: "PNLE",
      goalLabel: "Philippine Nurse Licensure Examination",
      masteryPercentage: 42,
      dueConcepts: 8,
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PRO",
        limits: { studyPacksPerMonth: 100, challengeQuizzesPerMonth: 50, adaptivePracticePerMonth: 30 },
        usage: { studyPacksUsed: 12, challengeQuizzesUsed: 8, adaptivePracticeUsed: 3 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("PNLE Goal")).toBeInTheDocument();
    expect(screen.getByText("Philippine Nurse Licensure Examination")).toBeInTheDocument();
    expect(screen.getByText("42%")).toBeInTheDocument();
    expect(screen.getByText("8 concepts due")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View goal progress" })).toHaveAttribute("href", "/progress");
    expect(getGoalSummary).toHaveBeenCalled();
  });

  it("shows a skeleton while the dashboard goal summary loads", async () => {
    let resolveGoalSummary: (summary: unknown) => void = () => undefined;
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      studyGoal: "pnle",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getGoalSummary as jest.Mock).mockReturnValue(new Promise((resolve) => {
      resolveGoalSummary = resolve;
    }));
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PRO",
        limits: { studyPacksPerMonth: 100, challengeQuizzesPerMonth: 50, adaptivePracticePerMonth: 30 },
        usage: { studyPacksUsed: 12, challengeQuizzesUsed: 8, adaptivePracticeUsed: 3 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByLabelText("Loading study goal")).toBeInTheDocument();

    resolveGoalSummary({
      studyGoal: "pnle",
      goalType: "EXAM",
      goalName: "PNLE",
      goalLabel: "Philippine Nurse Licensure Examination",
      masteryPercentage: 42,
      dueConcepts: 8,
    });

    expect(await screen.findByText("PNLE Goal")).toBeInTheDocument();
  });

  it("shows a dashboard goal card for a subject goal", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "Biochemistry",
      studyGoal: "Biochemistry",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getGoalSummary as jest.Mock).mockResolvedValue({
      studyGoal: "Biochemistry",
      goalType: "SUBJECT",
      goalName: "Biochemistry",
      goalLabel: "Biochemistry",
      masteryPercentage: 64,
      dueConcepts: 0,
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Biochemistry Goal")).toBeInTheDocument();
    expect(screen.getByText("All caught up — keep practicing!")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View goal progress" })).toHaveAttribute("href", "/progress");
    expect(getGoalSummary).toHaveBeenCalled();
  });

  it("does not fetch the dashboard goal summary when no goal is set", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "Mathematics",
      studyGoal: null,
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Track your progress in Mathematics.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Set as my focus" })).toBeInTheDocument();
    expect(getGoalSummary).not.toHaveBeenCalled();
    expect(getCollectionGoal).not.toHaveBeenCalled();
    expect(screen.queryByLabelText("Loading study goal")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Loading primary review set")).not.toBeInTheDocument();
  });

  it("mounts the discovery-intent handoff only inside the loaded branch", async () => {
    // Pins where the handoff lives, which is a correctness property rather than layout. Child
    // effects commit before parent effects, so mounting it beside the PageHeader ran it BEFORE
    // loadDashboard's requireAuthenticatedOnboardedUser guard — burning the intent cookie for a
    // signed-out visitor (who was then told their session expired) or a not-yet-onboarded one
    // (whose adoption succeeded server-side and was never shown to them).
    //
    // Asserting absence in the error branch is what catches a regression: if it moves back above
    // the loading/error ternary it renders in every state, including this one.
    (getDashboardOverview as jest.Mock).mockRejectedValue(new Error("overview failed"));

    render(<DashboardPage />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    });
    expect(screen.queryByTestId("discovery-intent-consumer")).not.toBeInTheDocument();
  });

  it("hides the dashboard goal slot when goal summary loading fails", async () => {
    let rejectGoalSummary: (error: Error) => void = () => undefined;
    const warnSpy = jest.spyOn(globalThis.console, "warn").mockImplementation(() => undefined);
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      studyGoal: "pnle",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (getGoalSummary as jest.Mock).mockReturnValue(new Promise((_, reject) => {
      rejectGoalSummary = reject;
    }));
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PRO",
        limits: { studyPacksPerMonth: 100, challengeQuizzesPerMonth: 50, adaptivePracticePerMonth: 30 },
        usage: { studyPacksUsed: 12, challengeQuizzesUsed: 8, adaptivePracticeUsed: 3 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByLabelText("Loading study goal")).toBeInTheDocument();
    rejectGoalSummary(new Error("offline"));
    await waitFor(() => {
      expect(screen.queryByLabelText("Loading study goal")).not.toBeInTheDocument();
    });
    expect(screen.queryByText("PNLE Goal")).not.toBeInTheDocument();
    expect(screen.queryByText("Studying for a board exam?")).not.toBeInTheDocument();
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });

  it("renders the board exam dashboard with countdown and challenge-quiz CTA", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      examDate: "2099-05-15",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PRO",
        limits: { studyPacksPerMonth: 100, challengeQuizzesPerMonth: 50, adaptivePracticePerMonth: 30 },
        usage: { studyPacksUsed: 12, challengeQuizzesUsed: 8, adaptivePracticeUsed: 3 },
      },
    });
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      focusAreas: {
        ...overview.focusAreas,
        adaptivePracticeAvailable: true,
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Exam Countdown")).toBeInTheDocument();
    expect(screen.getByText(/You have .* days until your exam\./)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Start Board Exam" })).toBeInTheDocument();
    expect(screen.getByText("Weak Areas")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Adaptive Practice" })).toBeInTheDocument();
    expect(screen.getByText("Study Activity This Week")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Notes for PNLE" })).toBeInTheDocument();
    expect(screen.getByText("Usage / Progress")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Practice Weak Areas" })).toBeInTheDocument();
  });

  it("routes Board Exam to the overview's most-recent ready note outside the bounded page", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 21,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      examDate: "2099-05-15",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });
    (getContinueStudyingRecommendation as jest.Mock).mockResolvedValue(null);
    (listNotes as jest.Mock).mockResolvedValue(notes.map((note) => ({
      ...note,
      studyPackStatus: "DRAFT",
      studyPackId: null,
    })));
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      totalNoteCount: 21,
      mostRecentReadyNoteId: "ready-note-outside-page",
    });

    render(<DashboardPage />);

    expect(await screen.findByRole("link", { name: "Start Board Exam" })).toHaveAttribute(
      "href",
      "/notes/ready-note-outside-page/challenge-quiz",
    );
  });

  it("falls back to bounded note signals when the overview fetch fails", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });
    (getDashboardOverview as jest.Mock).mockRejectedValue(new Error("overview unavailable"));

    render(<DashboardPage />);

    expect(await screen.findByText("Recent Notes")).toBeInTheDocument();
    expect(screen.getByText("2 saved")).toBeInTheDocument();
    const quickReview = screen.getByText("Quick Review");
    const usage = screen.getByText("Usage / Progress");
    expect(usage.compareDocumentPosition(quickReview) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "We could not load your notes" })).not.toBeInTheDocument();
  });

  it("falls back to the bounded ready note for Board Exam when the overview fetch fails", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      examDate: "2099-05-15",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });
    (getContinueStudyingRecommendation as jest.Mock).mockResolvedValue(null);
    (getDashboardOverview as jest.Mock).mockRejectedValue(new Error("overview unavailable"));

    render(<DashboardPage />);

    expect(await screen.findByRole("link", { name: "Start Board Exam" })).toHaveAttribute(
      "href",
      "/notes/note-1/challenge-quiz",
    );
  });

  it("shows the pacing line instead of the plain countdown when the backend provides an exam pacing plan", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      examDate: "2099-05-15",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PRO",
        limits: { studyPacksPerMonth: 100, challengeQuizzesPerMonth: 50, adaptivePracticePerMonth: 30 },
        usage: { studyPacksUsed: 12, challengeQuizzesUsed: 8, adaptivePracticeUsed: 3 },
      },
    });
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      examPacingPlan: { dueConceptCount: 12, dailyConceptTarget: 2, daysRemaining: 41 },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Exam Countdown")).toBeInTheDocument();
    expect(
      screen.getByText("12 concepts due — study ~2/day to stay on track for your exam in 41 days."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/You have .* days until your exam\./)).not.toBeInTheDocument();
  });

  it("renders community notes for professional profiles", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Pro",
      displayName: "Pro",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "PROFESSIONAL",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "PLUS",
        limits: { studyPacksPerMonth: 40, challengeQuizzesPerMonth: 20, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByRole("heading", { name: "Notes for PNLE" })).toBeInTheDocument();
    expect(screen.getByText("25 saved")).toBeInTheDocument();
  });

  it("renders the teacher dashboard with material and quiz-focused sections", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Teach",
      displayName: "Teach",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "TEACHER",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });
    (listNotes as jest.Mock).mockResolvedValue(notes.map((note) => ({
      ...note,
      generatedQuizId: note.id === "note-1" ? "generated-quiz-1" : null,
      generatedQuizGeneratedAt: note.id === "note-1" ? "2026-03-24T00:00:00Z" : null,
      generatedQuizQuestionCount: note.id === "note-1" ? 1 : null,
    })));

    render(<DashboardPage />);

    expect(await screen.findByRole("heading", { name: "Create Teaching Material" })).toBeInTheDocument();
    expect(
      screen.getByText("Welcome to NoteLib! Start by creating a note, then generate a Study Pack and review the quiz in Quiz Preview."),
    ).toBeInTheDocument();
    expect(screen.getByText("Recent Notes")).toBeInTheDocument();
    expect(screen.getByText("25 notes")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Notes for PNLE" })).toBeInTheDocument();
    expect(screen.getByText("Recently Generated Quizzes")).toBeInTheDocument();
    expect(screen.getByText("Ready to Export")).toBeInTheDocument();
    expect(screen.getByText("Teacher Help / Tips")).toBeInTheDocument();
    expect(screen.queryByText("Continue Studying")).not.toBeInTheDocument();
    expect(screen.queryByText("Exam Countdown")).not.toBeInTheDocument();
    expect(screen.queryByText("Usage / Progress")).not.toBeInTheDocument();
    expect(getNote).not.toHaveBeenCalled();
  });

  it("preserves the teacher updated-time slice before filtering generated quizzes", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Teach",
      displayName: "Teach",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 10,
      profileType: "TEACHER",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });
    const teacherNotes = Array.from({ length: 10 }, (_, index) => ({
      ...notes[0],
      id: `teacher-note-${index + 1}`,
      title: index === 0 ? "Quiz inside recent window" : index === 8 ? "Quiz outside recent window" : `Note ${index + 1}`,
      updatedAt: `2026-03-${25 - index}T00:00:00Z`,
      generatedQuizId: index === 0 || index === 8 ? `quiz-${index + 1}` : null,
      generatedQuizGeneratedAt: index === 0
        ? "2026-04-10T00:00:00Z"
        : index === 8 ? "2026-04-02T00:00:00Z" : null,
      generatedQuizQuestionCount: index === 0 || index === 8 ? 3 : null,
    }));
    (listNotes as jest.Mock).mockResolvedValue(teacherNotes);

    render(<DashboardPage />);

    expect(await screen.findAllByText("Quiz inside recent window")).not.toHaveLength(0);
    expect(screen.queryByText("Quiz outside recent window")).not.toBeInTheDocument();
    expect(screen.getByText("1 quiz previews")).toBeInTheDocument();
    expect(getNote).not.toHaveBeenCalled();
  });

  it("gives teachers a recent-ready-note CTA when no generated quizzes exist yet", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Teach",
      displayName: "Teach",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "TEACHER",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 2, challengeQuizzesUsed: 1, adaptivePracticeUsed: 0 },
      },
    });
    (listNotes as jest.Mock).mockResolvedValue(notes.map((note) => ({
      ...note,
      generatedQuizId: null,
      generatedQuizGeneratedAt: null,
      generatedQuizQuestionCount: null,
    })));

    render(<DashboardPage />);

    expect(await screen.findByText("No generated quizzes yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open Recent Ready Note" })).toHaveAttribute("href", "/notes/note-1");
  });

  it("shows first-study onboarding to new users and routes them to create a note", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: null,
      studyPackCount: 0,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      totalNoteCount: 0,
      hasQuizQuestions: false,
      mostRecentReadyNoteId: null,
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 0, challengeQuizzesUsed: 0, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Welcome to NoteLib")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create My First Note" }));

    expect(routerMock.push).toHaveBeenCalledWith("/notes/new");
  });

  it("shows the first-study dashboard empty state when the user has no notes yet", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 0,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      totalNoteCount: 0,
      hasQuizQuestions: false,
      mostRecentReadyNoteId: null,
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({
      usageSummary: {
        plan: "FREE",
        limits: { studyPacksPerMonth: 10, challengeQuizzesPerMonth: 5, adaptivePracticePerMonth: 0 },
        usage: { studyPacksUsed: 0, challengeQuizzesUsed: 0, adaptivePracticeUsed: 0 },
      },
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Start studying smarter")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Import files" })).toHaveAttribute("href", "/notes/import");
    expect(screen.getByRole("link", { name: "Create a note" })).toHaveAttribute("href", "/notes/new");
    expect(screen.getByRole("link", { name: "Or start from a ready-made study plan instead" }))
      .toHaveAttribute("href", "/explore?source=dashboard");
    await waitFor(() => {
      expect(mockDashboardStudyPlanSection).toHaveBeenCalled();
    });
    for (const [props] of mockDashboardStudyPlanSection.mock.calls) {
      expect(props).toEqual(expect.objectContaining({ suppressPointerWhenNoPrimary: true }));
    }
  });

  it("shows the Teacher empty state from the overview's zero total", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Teach",
      displayName: "Teach",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 0,
      profileType: "TEACHER",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      totalNoteCount: 0,
      hasQuizQuestions: false,
      mostRecentReadyNoteId: null,
    });

    render(<DashboardPage />);

    expect(await screen.findByText("Start your teaching workspace")).toBeInTheDocument();
    expect(screen.queryByText("Recent Notes")).not.toBeInTheDocument();
  });

  it("keeps the Board Exam zero-note plan section in recommendation mode without browse-empty props", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Board",
      displayName: "Board",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 0,
      profileType: "BOARD_EXAM",
      courseProgram: "PNLE",
      examDate: "2099-05-15",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });
    (useBillingUsageSummary as jest.Mock).mockReturnValue({ usageSummary: null });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      totalNoteCount: 0,
      hasQuizQuestions: false,
      mostRecentReadyNoteId: null,
    });

    render(<DashboardPage />);

    expect(await screen.findByRole("heading", { name: "Start Board Exam" })).toBeInTheDocument();
    await waitFor(() => {
      expect(mockDashboardStudyPlanSection).toHaveBeenCalled();
    });
    for (const [props] of mockDashboardStudyPlanSection.mock.calls) {
      expect(props).toEqual(expect.objectContaining({ discoveryPresentation: "recommendation" }));
      expect(props).not.toHaveProperty("viewAllHref");
      expect(props).not.toHaveProperty("browseWhenEmpty");
      expect(props.suppressPointerWhenNoPrimary).not.toBe(true);
    }
  });

  it("tells the learner why plan practice did not start, instead of silently doing nothing", async () => {
    // ⚠️ PINNED AT THE PAGE LAYER ON PURPOSE. A card-level test that passes the notice as a prop
    // cannot catch the page dropping it -- that is the guard-one-layer-below-the-defect mistake, and
    // the first version of this test made exactly it.
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      focusAreas: {
        ...overview.focusAreas,
        practiceCollectionId: "collection-1",
        practiceCollectionTitle: "CE Board Review",
        adaptivePracticeAvailable: true,
      },
    });
    (generateAdaptivePracticeForCollection as jest.Mock).mockResolvedValue({
      sessionId: null,
      status: null,
      studyPackId: null,
      noteId: null,
      title: "CE Board Review",
      focusConcepts: [],
      quiz: [],
      message: "No weak concepts found from your latest review.",
    });

    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
    });

    render(<DashboardPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Practice Across This Plan" }));

    expect(
      await screen.findByText(/No weak concepts found from your latest review/i),
    ).toBeInTheDocument();
  });

  it("routes a collection-anchored plan session by session id", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      firstName: "Note",
      displayName: "Note",
      emailVerifiedAt: "2026-03-20T00:00:00Z",
      productOnboardingCompletedAt: "2026-03-21T00:00:00Z",
      studyPackCount: 2,
      profileType: "STUDENT",
      courseProgram: "PNLE",
      onboardingCompletedAt: "2026-03-20T00:00:00Z",
      primaryCollectionId: null,
    });
    (getDashboardOverview as jest.Mock).mockResolvedValue({
      ...overview,
      focusAreas: {
        ...overview.focusAreas,
        practiceCollectionId: "collection-1",
        practiceCollectionTitle: "CE Board Review",
        adaptivePracticeAvailable: true,
      },
    });
    (generateAdaptivePracticeForCollection as jest.Mock).mockResolvedValue({
      sessionId: "adaptive-session-1",
      status: "IN_PROGRESS",
      studyPackId: null,
      noteId: null,
      title: "CE Board Review",
      focusConcepts: [],
      quiz: [],
      message: "Focusing on concepts you need to improve.",
    });

    render(<DashboardPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Practice Across This Plan" }));

    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(
      "/adaptive-practice/sessions/adaptive-session-1",
    ));
  });

});
