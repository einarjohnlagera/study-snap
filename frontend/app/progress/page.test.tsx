import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ProgressPage, { metadata } from "./page";
import { MILESTONES, ProgressReportClient } from "./progress-report-client";
import { DashboardFocusAreasCard } from "../dashboard/dashboard-focus-areas-card";
import {
  ApiRequestError,
  getCollectionGoal,
  getMe,
  getPlanReadiness,
  getProgressReport,
  listCollections,
  trackAnalyticsEvent,
  type GoalCollectionDetailResponse,
  type GoalSummaryResponse,
  type NoteCollectionSummary,
  type PlanReadinessResponse,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

const routerMock = {
  replace: jest.fn(),
  push: jest.fn(),
};

function goalSummary(overrides: Partial<GoalSummaryResponse>): GoalSummaryResponse {
  return {
    studyGoal: "Mathematics",
    goalType: "SUBJECT",
    goalName: "Mathematics",
    goalLabel: "Mathematics",
    masteryPercentage: 0,
    masteredConcepts: 0,
    totalConcepts: 0,
    notPracticedConcepts: 0,
    weakestGoalSubject: null,
    ...overrides,
  };
}

function reachedMilestones(summary: GoalSummaryResponse): boolean[] {
  return MILESTONES.map((milestone) => milestone.reached(summary));
}

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error {
    status: number;
    code: string | null;

    constructor(message: string, options: { status: number; code?: string | null }) {
      super(message);
      this.status = options.status;
      this.code = options.code ?? null;
    }
  },
  getCollectionGoal: jest.fn(),
  getMe: jest.fn(),
  getProgressReport: jest.fn(),
  getPlanReadiness: jest.fn(),
  listCollections: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

function collection(overrides: Partial<NoteCollectionSummary> = {}): NoteCollectionSummary {
  return {
    id: "collection-1",
    title: "Midterm Study Plan",
    description: null,
    visibility: "PRIVATE",
    courseProgram: null,
    sourcePlanId: null,
    parentCollectionId: null,
    itemCount: 2,
    childCount: 0,
    notesPracticed: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function planReadiness(overrides: Partial<PlanReadinessResponse> = {}): PlanReadinessResponse {
  return {
    collectionId: "collection-1",
    totalNotes: 3,
    notesWithStudyPack: 2,
    overallReadinessPercentage: 50,
    totalConcepts: 4,
    masteredConcepts: 2,
    dueConcepts: 1,
    notPracticedConcepts: 1,
    subjects: [
      {
        subject: "Biology",
        totalConcepts: 4,
        masteredConcepts: 2,
        dueConcepts: 1,
        notPracticedConcepts: 1,
        masteryPercentage: 50,
      },
    ],
    ...overrides,
  };
}

function goalReadiness(overrides: Partial<GoalCollectionDetailResponse> = {}): GoalCollectionDetailResponse {
  return {
    collectionId: "goal-1",
    title: "LET Goal",
    description: null,
    visibility: "PRIVATE",
    courseProgram: null,
    targetCompletionDate: null,
    companion: null,
    companionMayBeOutdated: false,
    sourcePlanId: null,
    parentCollectionId: null,
    itemCount: 0,
    childCount: 2,
    overallReadinessPercentage: 65,
    masteredConcepts: 13,
    dueConcepts: 2,
    notPracticedConcepts: 5,
    totalConcepts: 20,
    weeksRemaining: null,
    conceptsRemaining: null,
    todaysConceptBudget: null,
    weeklyFocusByDay: [],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    children: [],
    ...overrides,
  };
}

describe("ProgressPage", () => {
  beforeEach(() => {
    routerMock.replace.mockReset();
    routerMock.push.mockReset();
    (getCollectionGoal as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getProgressReport as jest.Mock).mockReset();
    (getPlanReadiness as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (requireAuthenticatedOnboardedUser as jest.Mock).mockReset();
    (requireAuthenticatedOnboardedUser as jest.Mock).mockReturnValue(true);
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT" });
    (listCollections as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({ primaryCollectionId: null });
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalReadiness());
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness());
  });

  it("exports page metadata", () => {
    expect(metadata).toMatchObject({
      title: "My Progress | NoteLib",
    });
  });

  it("renders subject progress entries with readiness stats", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [
        {
          subject: "Pharmacology",
          totalConcepts: 10,
          masteredConcepts: 7,
          dueConcepts: 2,
          notPracticedConcepts: 1,
          masteryPercentage: 70,
        },
      ],
      goalSummary: null,
    });

    render(await ProgressPage({ searchParams: Promise.resolve({}) }));

    expect(await screen.findByRole("heading", { name: "Pharmacology" })).toBeInTheDocument();
    expect(screen.getByText("70% ready")).toBeInTheDocument();
    expect(screen.getByText(/7 mastered.*2 due.*1 not started/)).toBeInTheDocument();
    expect(screen.queryByText(/due for review/)).not.toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Pharmacology readiness" })).toHaveAttribute("aria-valuenow", "70");
    expect(screen.getByRole("link", { name: "Dashboard" })).toHaveAttribute("href", "/dashboard");
    expect(screen.getByRole("link", { name: /Pharmacology[\s\S]*concepts tracked/ })).toHaveAttribute("href", "/library?subject=Pharmacology");
  });

  it("renders the empty state when there are no subjects", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({ subjects: [], goalSummary: null });

    render(<ProgressReportClient />);

    expect(await screen.findByText("No study packs with concepts yet. Generate a Study Pack to start tracking your progress.")).toBeInTheDocument();
  });

  it("renders not-started progress without a fill state", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [
        {
          subject: "Biochemistry",
          totalConcepts: 5,
          masteredConcepts: 0,
          dueConcepts: 0,
          notPracticedConcepts: 5,
          masteryPercentage: 0,
        },
      ],
      goalSummary: null,
    });

    render(<ProgressReportClient />);

    const progressbar = await screen.findByRole("progressbar", { name: "Biochemistry readiness" });
    expect(progressbar).toHaveAttribute("aria-valuenow", "0");
    expect(progressbar).toHaveAttribute("data-state", "not-started");
    expect(screen.getByText("Not started")).toBeInTheDocument();
    expect(screen.getByText(/0 mastered.*0 due.*5 not started/)).toBeInTheDocument();
    expect(screen.queryByText(/due for review/)).not.toBeInTheDocument();
  });

  it("renders an inline error when loading fails", async () => {
    (getProgressReport as jest.Mock).mockRejectedValue(new Error("Network error"));

    render(<ProgressReportClient />);

    expect(await screen.findByText("Could not load your progress report. Try refreshing.")).toBeInTheDocument();
  });

  it("renders a goal summary header when goalSummary is present", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [
        {
          subject: "Design",
          totalConcepts: 4,
          masteredConcepts: 2,
          dueConcepts: 1,
          notPracticedConcepts: 1,
          masteryPercentage: 50,
        },
      ],
      goalSummary: {
        studyGoal: "ale",
        goalType: "EXAM",
        goalName: "ALE",
        goalLabel: "Architect Licensure Examination",
        masteryPercentage: 50,
        masteredConcepts: 2,
        totalConcepts: 4,
        notPracticedConcepts: 1,
        weakestGoalSubject: "Design",
      },
    });

    render(<ProgressReportClient />);

    expect(await screen.findByRole("heading", { name: "Architect Licensure Examination" })).toBeInTheDocument();
    expect(screen.getByText("ALE Goal")).toBeInTheDocument();
    expect(screen.getByText("2 of 4 goal concepts mastered")).toBeInTheDocument();
    expect(screen.getAllByText("50%").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole("heading", { name: "Goal Milestones" })).toBeInTheDocument();
  });

  it("renders a next-study card with the weakest goal subject", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [],
      goalSummary: {
        studyGoal: "pnle",
        goalType: "EXAM",
        goalName: "PNLE",
        goalLabel: "Philippine Nurse Licensure Examination",
        masteryPercentage: 0,
        masteredConcepts: 0,
        totalConcepts: 0,
        notPracticedConcepts: 0,
        weakestGoalSubject: "Medical Surgical Nursing",
      },
    });

    render(<ProgressReportClient />);

    expect(await screen.findByRole("heading", { name: "What to study next" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Goal Milestones" })).not.toBeInTheDocument();
    expect(screen.getByText("Focus on Medical Surgical Nursing — you have concepts left to practice.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse PNLE notes →" })).toHaveAttribute("href", "/exam/pnle");
  });

  it("renders a generic next-study card when weakest goal subject is null", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [],
      goalSummary: {
        studyGoal: "let",
        goalType: "EXAM",
        goalName: "LET",
        goalLabel: "Licensure Examination for Teachers",
        masteryPercentage: 0,
        masteredConcepts: 0,
        totalConcepts: 0,
        notPracticedConcepts: 0,
        weakestGoalSubject: null,
      },
    });

    render(<ProgressReportClient />);

    expect(await screen.findByText("Browse your LET notes to build your knowledge.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse LET notes →" })).toHaveAttribute("href", "/exam/let");
  });

  it("renders a subject next-study card for subject goals", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [],
      goalSummary: {
        studyGoal: "Medical Surgical Nursing",
        goalType: "SUBJECT",
        goalName: "Medical Surgical Nursing",
        goalLabel: "Medical Surgical Nursing",
        masteryPercentage: 0,
        masteredConcepts: 0,
        totalConcepts: 0,
        notPracticedConcepts: 0,
        weakestGoalSubject: null,
      },
    });

    render(<ProgressReportClient />);

    expect(await screen.findByText("Browse your Medical Surgical Nursing notes to build your knowledge.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Study Medical Surgical Nursing notes in your Library →" }))
      .toHaveAttribute("href", "/library?cp=Medical%20Surgical%20Nursing");
  });

  it("renders an actionable study-focus card when no goal summary is set but subjects exist", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [
        {
          subject: "Pharmacology",
          totalConcepts: 10,
          masteredConcepts: 2,
          dueConcepts: 3,
          notPracticedConcepts: 5,
          masteryPercentage: 20,
        },
        {
          subject: "Anatomy",
          totalConcepts: 5,
          masteredConcepts: 3,
          dueConcepts: 1,
          notPracticedConcepts: 1,
          masteryPercentage: 60,
        },
      ],
      goalSummary: null,
    });

    render(<ProgressReportClient />);

    expect(await screen.findByRole("heading", { name: "Set your study focus" })).toBeInTheDocument();
    expect(screen.getByText("Pick subjects to track mastery toward. Your current subjects:")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Pharmacology" })).toHaveAttribute("href", "/profile#study-focus");
    expect(screen.getByRole("link", { name: "Anatomy" })).toHaveAttribute("href", "/profile#study-focus");
    expect(screen.getByRole("link", { name: "Or set from Profile →" })).toHaveAttribute("href", "/profile#study-focus");
  });

  it("routes subject-focus next study by weakest subject", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [],
      goalSummary: {
        studyGoal: "Pharmacology, Anatomy",
        goalType: "SUBJECT_FOCUS",
        goalName: "2 subjects in focus",
        goalLabel: "2 subjects in focus",
        masteryPercentage: 33,
        masteredConcepts: 1,
        totalConcepts: 3,
        notPracticedConcepts: 1,
        weakestGoalSubject: "Medical Surgical Nursing",
      },
    });

    render(<ProgressReportClient />);

    expect(await screen.findByText("Focus on Medical Surgical Nursing — you have concepts left to practice.")).toBeInTheDocument();
    expect(screen.getByText("Study Focus")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit in Profile" })).toHaveAttribute("href", "/profile#study-focus");
    expect(screen.getByRole("link", { name: "Study Medical Surgical Nursing in your Library →" }))
      .toHaveAttribute("href", "/library?subject=Medical%20Surgical%20Nursing");
  });

  it("does not fetch when the route guard redirects", async () => {
    (requireAuthenticatedOnboardedUser as jest.Mock).mockReturnValue(false);

    render(<ProgressReportClient />);

    await waitFor(() => {
      expect(getProgressReport).not.toHaveBeenCalled();
    });
  });

  it("lists only leaf plans in the picker and switches to plan readiness", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      collection({ id: "goal-1", title: "LET Goal", childCount: 2 }),
      collection({ id: "collection-1", title: "Biology Plan", childCount: 0 }),
    ]);
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [],
      goalSummary: null,
    });
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness());

    render(<ProgressReportClient />);

    await screen.findByText("No study packs with concepts yet. Generate a Study Pack to start tracking your progress.");
    expect(await screen.findByRole("option", { name: "Biology Plan" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "LET Goal" })).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Progress view"), { target: { value: "collection-1" } });

    expect(routerMock.push).toHaveBeenCalledWith("/progress?collectionId=collection-1");
    expect(await screen.findByText(/1 of 3 notes in this study plan don't have a Study Pack yet/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Generate Study Packs →" })).toHaveAttribute("href", "/collections/collection-1");
    expect(screen.getByRole("progressbar", { name: "Biology readiness" })).toHaveAttribute("aria-valuenow", "50");
    expect(screen.queryByRole("heading", { name: "Goal Milestones" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "What to study next" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Set your study focus" })).not.toBeInTheDocument();
  });

  it("loads plan readiness directly from the collectionId search param", async () => {
    (listCollections as jest.Mock).mockResolvedValue([collection({ id: "collection-1", title: "Biology Plan" })]);
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness());

    render(await ProgressPage({ searchParams: Promise.resolve({ collectionId: "collection-1" }) }));

    expect(await screen.findByText(/1 of 3 notes in this study plan don't have a Study Pack yet/)).toBeInTheDocument();
    expect(getPlanReadiness).toHaveBeenCalledWith("collection-1");
    expect(getProgressReport).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "My Progress" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("Biology Plan")).toBeInTheDocument();
  });

  it("defaults to the primary goal readiness view when no collectionId is present", async () => {
    (getMe as jest.Mock).mockResolvedValue({ primaryCollectionId: "goal-1" });
    (listCollections as jest.Mock).mockResolvedValue([
      collection({ id: "goal-1", title: "LET Goal", childCount: 2 }),
      collection({ id: "collection-1", title: "Biology Plan", childCount: 0 }),
    ]);
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalReadiness({
      collectionId: "goal-1",
      title: "LET Goal",
    }));

    render(<ProgressReportClient />);

    expect(await screen.findByRole("heading", { name: "LET Goal readiness" })).toBeInTheDocument();
    expect(screen.getByText(/65% ready.*13\/20 mastered.*2 due/)).toBeInTheDocument();
    expect(screen.getByText("5 not started")).toBeInTheDocument();
    expect(screen.getByDisplayValue("LET Goal")).toBeInTheDocument();
    expect(screen.queryByDisplayValue("Selected plan")).not.toBeInTheDocument();
    expect(getCollectionGoal).toHaveBeenCalledWith("goal-1");
    expect(getPlanReadiness).not.toHaveBeenCalled();
    expect(getProgressReport).not.toHaveBeenCalled();
    expect(screen.getByRole("link", { name: "Dashboard" })).toHaveAttribute("href", "/dashboard");
  });

  it("falls back to all subjects when no primary goal is set", async () => {
    (getMe as jest.Mock).mockResolvedValue({ primaryCollectionId: null });
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [
        {
          subject: "Chemistry",
          totalConcepts: 2,
          masteredConcepts: 1,
          dueConcepts: 1,
          notPracticedConcepts: 0,
          masteryPercentage: 50,
        },
      ],
      goalSummary: null,
    });

    render(<ProgressReportClient />);

    expect(await screen.findByRole("heading", { name: "Chemistry" })).toBeInTheDocument();
    expect(getProgressReport).toHaveBeenCalledTimes(1);
    expect(getCollectionGoal).not.toHaveBeenCalled();
    expect(getPlanReadiness).not.toHaveBeenCalled();
  });

  it("honors an explicit leaf collectionId even when a primary goal exists", async () => {
    (getMe as jest.Mock).mockResolvedValue({ primaryCollectionId: "goal-1" });
    (listCollections as jest.Mock).mockResolvedValue([
      collection({ id: "goal-1", title: "LET Goal", childCount: 2 }),
      collection({ id: "collection-1", title: "Biology Plan", childCount: 0 }),
    ]);
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness({ collectionId: "collection-1" }));

    render(<ProgressReportClient initialCollectionId="collection-1" />);

    expect(await screen.findByText(/1 of 3 notes in this study plan don't have a Study Pack yet/)).toBeInTheDocument();
    expect(getPlanReadiness).toHaveBeenCalledWith("collection-1");
    expect(getCollectionGoal).not.toHaveBeenCalled();
    expect(getProgressReport).not.toHaveBeenCalled();
    expect(screen.getByDisplayValue("Biology Plan")).toBeInTheDocument();
  });

  it("keeps all subjects reachable after the primary goal auto-default is applied", async () => {
    (getMe as jest.Mock).mockResolvedValue({ primaryCollectionId: "goal-1" });
    (listCollections as jest.Mock).mockResolvedValue([
      collection({ id: "goal-1", title: "LET Goal", childCount: 2 }),
      collection({ id: "collection-1", title: "Biology Plan", childCount: 0 }),
    ]);
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalReadiness({ collectionId: "goal-1", title: "LET Goal" }));
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [
        {
          subject: "Chemistry",
          totalConcepts: 2,
          masteredConcepts: 1,
          dueConcepts: 1,
          notPracticedConcepts: 0,
          masteryPercentage: 50,
        },
      ],
      goalSummary: null,
    });

    render(<ProgressReportClient />);

    expect(await screen.findByRole("heading", { name: "LET Goal readiness" })).toBeInTheDocument();
    expect(getCollectionGoal).toHaveBeenCalledTimes(1);

    fireEvent.change(screen.getByLabelText("Progress view"), { target: { value: "" } });

    expect(routerMock.push).toHaveBeenCalledWith("/progress");
    expect(await screen.findByRole("heading", { name: "Chemistry" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("All subjects")).toBeInTheDocument();
    expect(getProgressReport).toHaveBeenCalledTimes(1);
    expect(getCollectionGoal).toHaveBeenCalledTimes(1);
    expect(getPlanReadiness).not.toHaveBeenCalled();
  });

  it("renders not found for a stale primary goal default", async () => {
    (getMe as jest.Mock).mockResolvedValue({ primaryCollectionId: "goal-1" });
    (getCollectionGoal as jest.Mock).mockRejectedValue(new ApiRequestError("Missing", { status: 404 }));

    render(<ProgressReportClient />);

    expect(await screen.findByText("Goal not found")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Study Plans" })).toHaveAttribute("href", "/collections");
    expect(getPlanReadiness).not.toHaveBeenCalled();
    expect(trackAnalyticsEvent).not.toHaveBeenCalled();
  });

  it("renders primary goal readiness errors with retry", async () => {
    (getMe as jest.Mock).mockResolvedValue({ primaryCollectionId: "goal-1" });
    (getCollectionGoal as jest.Mock)
      .mockRejectedValueOnce(new Error("Network failed"))
      .mockResolvedValueOnce(goalReadiness({ collectionId: "goal-1", title: "LET Goal" }));

    render(<ProgressReportClient />);

    expect(await screen.findByText("Could not load readiness")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByRole("heading", { name: "LET Goal readiness" })).toBeInTheDocument();
    expect(getCollectionGoal).toHaveBeenCalledTimes(2);
  });

  it("switches back to all subjects and clears the collectionId query param", async () => {
    (listCollections as jest.Mock).mockResolvedValue([collection({ id: "collection-1", title: "Biology Plan" })]);
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness());
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [
        {
          subject: "Chemistry",
          totalConcepts: 2,
          masteredConcepts: 1,
          dueConcepts: 1,
          notPracticedConcepts: 0,
          masteryPercentage: 50,
        },
      ],
      goalSummary: null,
    });

    render(<ProgressReportClient initialCollectionId="collection-1" />);

    await screen.findByText(/1 of 3 notes in this study plan don't have a Study Pack yet/);
    fireEvent.change(screen.getByLabelText("Progress view"), { target: { value: "" } });

    expect(routerMock.push).toHaveBeenCalledWith("/progress");
    expect(await screen.findByRole("heading", { name: "Chemistry" })).toBeInTheDocument();
  });

  it("shows a backlink to the originating collection when reached via a specific plan", async () => {
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness());

    render(<ProgressReportClient initialCollectionId="collection-1" />);

    await screen.findByRole("progressbar", { name: "Biology readiness" });
    expect(screen.getByRole("link", { name: "Study Plan" })).toHaveAttribute("href", "/collections/collection-1");
    expect(screen.queryByRole("link", { name: "Dashboard" })).not.toBeInTheDocument();
  });

  it("hides the missing Study Pack caveat when every note has a Study Pack", async () => {
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness({ totalNotes: 2, notesWithStudyPack: 2 }));

    render(<ProgressReportClient initialCollectionId="collection-1" />);

    await screen.findByRole("progressbar", { name: "Biology readiness" });
    expect(screen.queryByText(/don't have a Study Pack yet/)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Generate Study Packs →" })).not.toBeInTheDocument();
  });

  it("renders not found for missing or non-owned selected plans", async () => {
    (getPlanReadiness as jest.Mock).mockRejectedValue(new ApiRequestError("Missing", { status: 404 }));

    render(<ProgressReportClient initialCollectionId="collection-1" />);

    expect(await screen.findByText("Study Plan not found")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Study Plans" })).toHaveAttribute("href", "/collections");
    expect(trackAnalyticsEvent).not.toHaveBeenCalled();
  });

  it("renders plan readiness errors with retry", async () => {
    (getPlanReadiness as jest.Mock)
      .mockRejectedValueOnce(new Error("Network failed"))
      .mockResolvedValueOnce(planReadiness());

    render(<ProgressReportClient initialCollectionId="collection-1" />);

    expect(await screen.findByText("Could not load readiness")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText(/1 of 3 notes in this study plan don't have a Study Pack yet/)).toBeInTheDocument();
    expect(getPlanReadiness).toHaveBeenCalledTimes(2);
  });

  it("tracks plan readiness once per distinct selected plan", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      collection({ id: "collection-a", title: "Plan A" }),
      collection({ id: "collection-b", title: "Plan B" }),
    ]);
    (getPlanReadiness as jest.Mock).mockImplementation((id: string) => Promise.resolve(planReadiness({
      collectionId: id,
    })));

    render(<ProgressReportClient initialCollectionId="collection-a" />);

    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);
    });
    expect(trackAnalyticsEvent).toHaveBeenLastCalledWith(expect.objectContaining({
      eventType: "PLAN_READINESS_VIEWED",
      entityId: "collection-a",
    }));

    fireEvent.change(await screen.findByLabelText("Progress view"), { target: { value: "collection-b" } });

    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledTimes(2);
    });
    expect(trackAnalyticsEvent).toHaveBeenLastCalledWith(expect.objectContaining({
      eventType: "PLAN_READINESS_VIEWED",
      entityId: "collection-b",
    }));

    fireEvent.change(screen.getByLabelText("Progress view"), { target: { value: "collection-a" } });

    await waitFor(() => {
      expect(getPlanReadiness).toHaveBeenCalledWith("collection-a");
    });
    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(2);
  });
});

describe("Progress goal milestones", () => {
  it("keeps the first milestone unreached when no concepts are mastered", () => {
    expect(reachedMilestones(goalSummary({
      masteredConcepts: 0,
      totalConcepts: 0,
      notPracticedConcepts: 0,
      masteryPercentage: 0,
    }))).toEqual([false, false, false, false, false, false]);
  });

  it("marks only the first concept milestone reached after one mastered concept", () => {
    expect(reachedMilestones(goalSummary({
      masteredConcepts: 1,
      totalConcepts: 10,
      notPracticedConcepts: 9,
      masteryPercentage: 10,
    }))).toEqual([true, false, false, false, false, false]);
  });

  it("marks milestones one through four reached at 50 percent with all concepts reviewed", () => {
    expect(reachedMilestones(goalSummary({
      masteredConcepts: 5,
      totalConcepts: 10,
      notPracticedConcepts: 0,
      masteryPercentage: 50,
    }))).toEqual([true, true, true, true, false, false]);
  });

  it("marks all milestones reached when the goal is fully mastered", () => {
    expect(reachedMilestones(goalSummary({
      masteredConcepts: 10,
      totalConcepts: 10,
      notPracticedConcepts: 0,
      masteryPercentage: 100,
    }))).toEqual([true, true, true, true, true, true]);
  });
});

describe("DashboardFocusAreasCard progress link", () => {
  it("shows the full progress report link when concepts are present", () => {
    render(
      <DashboardFocusAreasCard
        focusAreas={{
          concepts: [{ conceptName: "Mitosis", accuracyPercentage: 45 }],
          practiceNoteId: "note-1",
          adaptivePracticeAvailable: false,
        }}
        onUnlockAdaptivePractice={jest.fn()}
      />,
    );

    expect(screen.getByRole("link", { name: /View progress report/ })).toHaveAttribute("href", "/progress");
  });

  it("shows the full progress report link in the empty state", () => {
    render(
      <DashboardFocusAreasCard
        focusAreas={{ concepts: [], practiceNoteId: null, adaptivePracticeAvailable: false }}
        onUnlockAdaptivePractice={jest.fn()}
      />,
    );

    expect(screen.getByRole("link", { name: /View progress report/ })).toHaveAttribute("href", "/progress");
  });
});
