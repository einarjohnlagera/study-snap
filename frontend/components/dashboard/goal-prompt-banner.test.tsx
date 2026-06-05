import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GoalPromptBanner } from "./goal-prompt-banner";
import { setExamGoal, trackAnalyticsEvent } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  setExamGoal: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("GoalPromptBanner", () => {
  beforeEach(() => {
    document.cookie = "notelib-exam-intent=; path=/; max-age=0; SameSite=Strict";
    (setExamGoal as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (setExamGoal as jest.Mock).mockResolvedValue({ examGoal: "ale" });
  });

  it("renders nothing when an exam goal is already set", () => {
    render(<GoalPromptBanner examGoal="ale" courseProgram="Architecture" />);

    expect(screen.queryByText(/Make Architect Licensure Examination/)).not.toBeInTheDocument();
  });

  it("renders nothing when no cookie and no matching course program exist", () => {
    render(<GoalPromptBanner examGoal={null} courseProgram="Accountancy" />);

    expect(screen.queryByText(/Suggested Goal/)).not.toBeInTheDocument();
  });

  it("renders from a valid exam intent cookie", async () => {
    document.cookie = "notelib-exam-intent=pnle; path=/; max-age=1800; SameSite=Strict";

    render(<GoalPromptBanner examGoal={null} courseProgram={null} />);

    expect(await screen.findByText("Suggested Goal: PNLE")).toBeInTheDocument();
    expect(screen.getByText(/Philippine Nurse Licensure Examination/)).toBeInTheDocument();
  });

  it("falls back to course program mapping when no cookie exists", async () => {
    render(<GoalPromptBanner examGoal={null} courseProgram="Architecture" />);

    expect(await screen.findByText("Suggested Goal: ALE")).toBeInTheDocument();
    expect(screen.getByText(/Architect Licensure Examination/)).toBeInTheDocument();
  });

  it("sets the goal, clears cookie, tracks analytics, and hides", async () => {
    document.cookie = "notelib-exam-intent=ale; path=/; max-age=1800; SameSite=Strict";
    render(<GoalPromptBanner examGoal={null} courseProgram={null} />);

    fireEvent.click(await screen.findByRole("button", { name: "Set as my goal" }));

    await waitFor(() => {
      expect(setExamGoal).toHaveBeenCalledWith("ale");
    });
    expect(document.cookie).not.toContain("notelib-exam-intent=ale");
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "EXAM_GOAL_SET",
      metadata: { examGoal: "ale" },
    });
    await waitFor(() => {
      expect(screen.queryByText("Suggested Goal: ALE")).not.toBeInTheDocument();
    });
  });

  it("dismisses without calling setExamGoal", async () => {
    document.cookie = "notelib-exam-intent=let; path=/; max-age=1800; SameSite=Strict";
    render(<GoalPromptBanner examGoal={null} courseProgram={null} />);

    fireEvent.click(await screen.findByRole("button", { name: "Dismiss" }));

    expect(setExamGoal).not.toHaveBeenCalled();
    expect(document.cookie).not.toContain("notelib-exam-intent=let");
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "EXAM_GOAL_DISMISSED",
      metadata: { examGoal: "let" },
    });
    await waitFor(() => {
      expect(screen.queryByText("Suggested Goal: LET")).not.toBeInTheDocument();
    });
  });

  it("keeps the banner visible and shows an inline error when saving fails", async () => {
    (setExamGoal as jest.Mock).mockRejectedValue(new Error("Could not update exam goal."));
    render(<GoalPromptBanner examGoal={null} courseProgram="Education" />);

    fireEvent.click(await screen.findByRole("button", { name: "Set as my goal" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not update exam goal.");
    expect(screen.getByText("Suggested Goal: LET")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Set as my goal" })).toBeEnabled();
  });
});
