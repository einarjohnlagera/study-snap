import { render, screen, waitFor } from "@testing-library/react";
import ProgressPage, { metadata } from "./page";
import { ProgressReportClient } from "./progress-report-client";
import { DashboardFocusAreasCard } from "../dashboard/dashboard-focus-areas-card";
import { getProgressReport } from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

const routerMock = {
  replace: jest.fn(),
  push: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  getProgressReport: jest.fn(),
}));

describe("ProgressPage", () => {
  beforeEach(() => {
    routerMock.replace.mockReset();
    routerMock.push.mockReset();
    (getProgressReport as jest.Mock).mockReset();
    (requireAuthenticatedOnboardedUser as jest.Mock).mockReset();
    (requireAuthenticatedOnboardedUser as jest.Mock).mockReturnValue(true);
  });

  it("exports page metadata", () => {
    expect(metadata).toMatchObject({
      title: "My Progress | NoteLib",
    });
  });

  it("renders subject progress entries with mastery stats", async () => {
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

    render(<ProgressPage />);

    expect(await screen.findByRole("heading", { name: "Pharmacology" })).toBeInTheDocument();
    expect(screen.getByText("70%")).toBeInTheDocument();
    expect(screen.getByText(/7 mastered.*2 due for review.*1 not started/)).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Pharmacology mastery" })).toHaveAttribute("aria-valuenow", "70");
    expect(screen.getByRole("link", { name: /Back to Dashboard/ })).toHaveAttribute("href", "/dashboard");
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

    const progressbar = await screen.findByRole("progressbar", { name: "Biochemistry mastery" });
    expect(progressbar).toHaveAttribute("aria-valuenow", "0");
    expect(progressbar).toHaveAttribute("data-state", "not-started");
    expect(screen.getByText(/0 mastered.*0 due for review.*5 not started/)).toBeInTheDocument();
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
        examGoal: "ale",
        examShortName: "ALE",
        examFullName: "Architect Licensure Examination (ALE)",
        masteryPercentage: 50,
        masteredConcepts: 2,
        totalConcepts: 4,
        weakestGoalSubject: "Design",
      },
    });

    render(<ProgressReportClient />);

    expect(await screen.findByRole("heading", { name: "Architect Licensure Examination (ALE)" })).toBeInTheDocument();
    expect(screen.getByText("2 of 4 goal concepts mastered")).toBeInTheDocument();
    expect(screen.getAllByText("50%").length).toBeGreaterThanOrEqual(1);
  });

  it("renders a next-study card with the weakest goal subject", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [],
      goalSummary: {
        examGoal: "pnle",
        examShortName: "PNLE",
        examFullName: "Philippine Nurse Licensure Examination (PNLE)",
        masteryPercentage: 0,
        masteredConcepts: 0,
        totalConcepts: 0,
        weakestGoalSubject: "Medical Surgical Nursing",
      },
    });

    render(<ProgressReportClient />);

    expect(await screen.findByRole("heading", { name: "What to study next" })).toBeInTheDocument();
    expect(screen.getByText("Focus on Medical Surgical Nursing — you have concepts left to practice.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse PNLE notes →" })).toHaveAttribute("href", "/exam/pnle");
  });

  it("renders a generic next-study card when weakest goal subject is null", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [],
      goalSummary: {
        examGoal: "let",
        examShortName: "LET",
        examFullName: "Licensure Examination for Teachers (LET)",
        masteryPercentage: 0,
        masteredConcepts: 0,
        totalConcepts: 0,
        weakestGoalSubject: null,
      },
    });

    render(<ProgressReportClient />);

    expect(await screen.findByText("Browse community LET notes to build your knowledge.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse LET notes →" })).toHaveAttribute("href", "/exam/let");
  });

  it("renders an exam hub callout when subjects exist without a goal summary", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({
      subjects: [
        {
          subject: "Biochemistry",
          totalConcepts: 5,
          masteredConcepts: 1,
          dueConcepts: 1,
          notPracticedConcepts: 3,
          masteryPercentage: 20,
        },
      ],
      goalSummary: null,
    });

    render(<ProgressReportClient />);

    expect(await screen.findByRole("link", { name: /Explore exam hubs to set a goal/ })).toHaveAttribute("href", "/exam");
  });

  it("does not fetch when the route guard redirects", async () => {
    (requireAuthenticatedOnboardedUser as jest.Mock).mockReturnValue(false);

    render(<ProgressReportClient />);

    await waitFor(() => {
      expect(getProgressReport).not.toHaveBeenCalled();
    });
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

    expect(screen.getByRole("link", { name: /View full progress report/ })).toHaveAttribute("href", "/progress");
  });

  it("shows the full progress report link in the empty state", () => {
    render(
      <DashboardFocusAreasCard
        focusAreas={{ concepts: [], practiceNoteId: null, adaptivePracticeAvailable: false }}
        onUnlockAdaptivePractice={jest.fn()}
      />,
    );

    expect(screen.getByRole("link", { name: /View full progress report/ })).toHaveAttribute("href", "/progress");
  });
});
