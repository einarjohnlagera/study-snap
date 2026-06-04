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
    });

    render(<ProgressPage />);

    expect(await screen.findByRole("heading", { name: "Pharmacology" })).toBeInTheDocument();
    expect(screen.getByText("70%")).toBeInTheDocument();
    expect(screen.getByText(/7 mastered.*2 due for review.*1 not started/)).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Pharmacology mastery" })).toHaveAttribute("aria-valuenow", "70");
    expect(screen.getByRole("link", { name: /Back to Dashboard/ })).toHaveAttribute("href", "/dashboard");
  });

  it("renders the empty state when there are no subjects", async () => {
    (getProgressReport as jest.Mock).mockResolvedValue({ subjects: [] });

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

  it("does not show the full progress report link in the empty state", () => {
    render(
      <DashboardFocusAreasCard
        focusAreas={{ concepts: [], practiceNoteId: null, adaptivePracticeAvailable: false }}
        onUnlockAdaptivePractice={jest.fn()}
      />,
    );

    expect(screen.queryByRole("link", { name: /View full progress report/ })).not.toBeInTheDocument();
  });
});
