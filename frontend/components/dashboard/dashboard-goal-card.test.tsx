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

  it("renders an exam goal with mastery and due concepts", () => {
    render(
      <DashboardGoalCard
        goalSummary={{
          studyGoal: "pnle",
          goalType: "EXAM",
          goalName: "PNLE",
          goalLabel: "Philippine Nurse Licensure Examination",
          masteryPercentage: 42,
          dueConcepts: 8,
          weakestGoalSubject: null,
        }}
      />,
    );

    expect(screen.getByText("PNLE Goal")).toBeInTheDocument();
    expect(screen.getByText("Philippine Nurse Licensure Examination")).toBeInTheDocument();
    expect(screen.getByText("42%")).toBeInTheDocument();
    expect(screen.getByText("8 concepts due for review")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View goal progress" })).toHaveAttribute("href", "/progress");
  });

  it("renders a subject goal with zero due concepts", () => {
    render(
      <DashboardGoalCard
        goalSummary={{
          studyGoal: "Biochemistry",
          goalType: "SUBJECT",
          goalName: "Biochemistry",
          goalLabel: "Biochemistry",
          masteryPercentage: 64,
          dueConcepts: 0,
          weakestGoalSubject: null,
        }}
      />,
    );

    expect(screen.getByText("Biochemistry Goal")).toBeInTheDocument();
    expect(screen.getByText("All caught up — keep practicing!")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View goal progress" })).toHaveAttribute("href", "/progress");
  });

  it("shows weakest goal subject when present", () => {
    render(
      <DashboardGoalCard
        goalSummary={{
          studyGoal: "pnle",
          goalType: "EXAM",
          goalName: "PNLE",
          goalLabel: "Philippine Nurse Licensure Examination",
          masteryPercentage: 30,
          dueConcepts: 12,
          weakestGoalSubject: "Medical – Surgical Nursing",
        }}
      />,
    );

    expect(screen.getByText("Medical – Surgical Nursing")).toBeInTheDocument();
  });

  it("does not show focus line when weakestGoalSubject is null", () => {
    render(
      <DashboardGoalCard
        goalSummary={{
          studyGoal: "pnle",
          goalType: "EXAM",
          goalName: "PNLE",
          goalLabel: "Philippine Nurse Licensure Examination",
          masteryPercentage: 42,
          dueConcepts: 8,
          weakestGoalSubject: null,
        }}
      />,
    );

    expect(screen.queryByText(/Focus:/)).not.toBeInTheDocument();
  });

  it("tracks view on mount and CTA click", () => {
    render(
      <DashboardGoalCard
        goalSummary={{
          studyGoal: "pnle",
          goalType: "EXAM",
          goalName: "PNLE",
          goalLabel: "Philippine Nurse Licensure Examination",
          masteryPercentage: 42,
          dueConcepts: 8,
          weakestGoalSubject: null,
        }}
      />,
    );

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "DASHBOARD_GOAL_CARD_VIEWED",
      metadata: { studyGoal: "pnle" },
    });

    fireEvent.click(screen.getByRole("link", { name: "View goal progress" }));

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "DASHBOARD_GOAL_CARD_CTA_CLICKED",
      metadata: { studyGoal: "pnle" },
    });
  });
});
