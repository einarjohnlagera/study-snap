import { fireEvent, render, screen } from "@testing-library/react";
import { DashboardGoalCard } from "./dashboard-goal-card";
import { trackAnalyticsEvent } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

describe("DashboardGoalCard", () => {
  beforeEach(() => {
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockResolvedValue(undefined);
  });

  it("renders an exam goal with exam destination", () => {
    render(
      <DashboardGoalCard
        goalSummary={{
          studyGoal: "pnle",
          goalType: "EXAM",
          goalName: "PNLE",
          goalLabel: "Philippine Nurse Licensure Examination",
          masteryPercentage: 42,
          dueConcepts: 8,
        }}
      />,
    );

    expect(screen.getByText("PNLE Goal")).toBeInTheDocument();
    expect(screen.getByText("Philippine Nurse Licensure Examination")).toBeInTheDocument();
    expect(screen.getByText("42%")).toBeInTheDocument();
    expect(screen.getByText("8 concepts due for review")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse PNLE notes →" })).toHaveAttribute("href", "/exam/pnle");
    expect(screen.getByRole("link", { name: "View full progress →" })).toHaveAttribute("href", "/progress");
  });

  it("renders a subject goal with public library destination", () => {
    render(
      <DashboardGoalCard
        goalSummary={{
          studyGoal: "Biochemistry",
          goalType: "SUBJECT",
          goalName: "Biochemistry",
          goalLabel: "Biochemistry",
          masteryPercentage: 64,
          dueConcepts: 0,
        }}
      />,
    );

    expect(screen.getByText("Biochemistry Goal")).toBeInTheDocument();
    expect(screen.getByText("All caught up — keep practicing!")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse Biochemistry notes →" }))
      .toHaveAttribute("href", "/public/library?courseProgram=Biochemistry");
  });

  it("tracks view and primary CTA clicks", () => {
    render(
      <DashboardGoalCard
        goalSummary={{
          studyGoal: "pnle",
          goalType: "EXAM",
          goalName: "PNLE",
          goalLabel: "Philippine Nurse Licensure Examination",
          masteryPercentage: 42,
          dueConcepts: 8,
        }}
      />,
    );

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "DASHBOARD_GOAL_CARD_VIEWED",
      metadata: { studyGoal: "pnle" },
    });

    fireEvent.click(screen.getByRole("link", { name: "Browse PNLE notes →" }));

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "DASHBOARD_GOAL_CARD_CTA_CLICKED",
      metadata: { studyGoal: "pnle", destination: "/exam/pnle" },
    });
  });
});
