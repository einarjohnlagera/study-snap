import { fireEvent, render, screen } from "@testing-library/react";
import { GoalNudgeCard } from "./goal-nudge-card";
import { trackAnalyticsEvent } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

const goalNudge = {
  studyGoal: "pnle",
  goalType: "EXAM" as const,
  goalName: "PNLE",
  goalLabel: "Philippine Nurse Licensure Examination",
  masteryPercentage: 42,
  dueConcepts: 8,
  weakestGoalSubject: null,
};

describe("GoalNudgeCard", () => {
  beforeEach(() => {
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockResolvedValue(undefined);
  });

  it("renders goal progress and due concepts", () => {
    render(<GoalNudgeCard goalNudge={goalNudge} noteId="note-1" />);

    expect(screen.getByText("PNLE Goal")).toBeInTheDocument();
    expect(screen.getByText("Philippine Nurse Licensure Examination")).toBeInTheDocument();
    expect(screen.getByText(/42%/)).toBeInTheDocument();
    expect(screen.getByText("8 concepts due for review")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View PNLE progress →" })).toHaveAttribute("href", "/progress");
  });

  it("renders completed-goal copy when no concepts are due", () => {
    render(<GoalNudgeCard goalNudge={{ ...goalNudge, dueConcepts: 0 }} noteId="note-1" />);

    expect(screen.getByText("You've reviewed all current goal concepts.")).toBeInTheDocument();
  });

  it("fires the shown analytics event once on mount", () => {
    render(<GoalNudgeCard goalNudge={goalNudge} noteId="note-1" />);

    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "GOAL_NUDGE_SHOWN",
      metadata: { studyGoal: "pnle", noteId: "note-1" },
    });
  });

  it("fires the CTA analytics event before navigation", () => {
    render(<GoalNudgeCard goalNudge={goalNudge} noteId="note-1" />);

    fireEvent.click(screen.getByRole("link", { name: "View PNLE progress →" }));

    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "GOAL_NUDGE_CTA_CLICKED",
      metadata: { studyGoal: "pnle", noteId: "note-1" },
    });
  });
});
