import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GoalPromptBanner } from "./goal-prompt-banner";
import { setStudyGoal, trackAnalyticsEvent } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  setStudyGoal: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("GoalPromptBanner", () => {
  beforeEach(() => {
    document.cookie = "notelib-exam-intent=; path=/; max-age=0; SameSite=Strict";
    (setStudyGoal as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (setStudyGoal as jest.Mock).mockResolvedValue({ studyGoal: "ale" });
  });

  it("renders nothing when an exam goal is already set", () => {
    render(<GoalPromptBanner studyGoal="ale" courseProgram="Architecture" profileType="STUDENT" />);

    expect(screen.queryByText(/Make Architect Licensure Examination/)).not.toBeInTheDocument();
  });

  it("renders nothing when no cookie and no course program exist", () => {
    render(<GoalPromptBanner studyGoal={null} courseProgram={null} profileType="STUDENT" />);

    expect(screen.queryByText(/Suggested Goal/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Suggested Focus/)).not.toBeInTheDocument();
  });

  it("renders from a valid exam intent cookie", async () => {
    document.cookie = "notelib-exam-intent=pnle; path=/; max-age=1800; SameSite=Strict";

    render(<GoalPromptBanner studyGoal={null} courseProgram={null} profileType="STUDENT" />);

    expect(await screen.findByText("Suggested Goal: PNLE")).toBeInTheDocument();
    expect(screen.getByText(/Philippine Nurse Licensure Examination/)).toBeInTheDocument();
  });

  it("falls back to course program mapping when no cookie exists", async () => {
    render(<GoalPromptBanner studyGoal={null} courseProgram="Architecture" profileType="STUDENT" />);

    expect(await screen.findByText("Suggested Goal: ALE")).toBeInTheDocument();
    expect(screen.getByText(/Architect Licensure Examination/)).toBeInTheDocument();
  });

  it("renders a subject focus banner when no exam slug resolves for a non-board-exam profile", async () => {
    render(<GoalPromptBanner studyGoal={null} courseProgram="Mathematics" profileType="STUDENT" />);

    expect(await screen.findByText("Suggested Focus")).toBeInTheDocument();
    expect(screen.getByText("Track your progress in Mathematics.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Set as my focus" })).toBeInTheDocument();
  });

  it("does not render a subject focus banner for board exam profiles", () => {
    render(<GoalPromptBanner studyGoal={null} courseProgram="Mathematics" profileType="BOARD_EXAM" />);

    expect(screen.queryByText("Suggested Focus")).not.toBeInTheDocument();
  });

  it("sets the goal, clears cookie, tracks analytics, and hides", async () => {
    document.cookie = "notelib-exam-intent=ale; path=/; max-age=1800; SameSite=Strict";
    render(<GoalPromptBanner studyGoal={null} courseProgram={null} profileType="STUDENT" />);

    fireEvent.click(await screen.findByRole("button", { name: "Set as my goal" }));

    await waitFor(() => {
      expect(setStudyGoal).toHaveBeenCalledWith("ale");
    });
    expect(document.cookie).not.toContain("notelib-exam-intent=ale");
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "STUDY_GOAL_SET",
      metadata: { studyGoal: "ale" },
    });
    await waitFor(() => {
      expect(screen.queryByText("Suggested Goal: ALE")).not.toBeInTheDocument();
    });
  });

  it("dismisses without calling setStudyGoal", async () => {
    document.cookie = "notelib-exam-intent=let; path=/; max-age=1800; SameSite=Strict";
    render(<GoalPromptBanner studyGoal={null} courseProgram={null} profileType="STUDENT" />);

    fireEvent.click(await screen.findByRole("button", { name: "Dismiss" }));

    expect(setStudyGoal).not.toHaveBeenCalled();
    expect(document.cookie).not.toContain("notelib-exam-intent=let");
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "STUDY_GOAL_DISMISSED",
      metadata: { studyGoal: "let" },
    });
    await waitFor(() => {
      expect(screen.queryByText("Suggested Goal: LET")).not.toBeInTheDocument();
    });
  });

  it("keeps the banner visible and shows an inline error when saving fails", async () => {
    (setStudyGoal as jest.Mock).mockRejectedValue(new Error("Could not update exam goal."));
    render(<GoalPromptBanner studyGoal={null} courseProgram="Education" profileType="STUDENT" />);

    fireEvent.click(await screen.findByRole("button", { name: "Set as my goal" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not update exam goal.");
    expect(screen.getByText("Suggested Goal: LET")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Set as my goal" })).toBeEnabled();
  });

  it("sets a subject focus, tracks analytics, and hides", async () => {
    render(<GoalPromptBanner studyGoal={null} courseProgram="Mathematics" profileType="STUDENT" />);

    fireEvent.click(await screen.findByRole("button", { name: "Set as my focus" }));

    await waitFor(() => {
      expect(setStudyGoal).toHaveBeenCalledWith("Mathematics");
    });
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "STUDY_GOAL_SET",
      metadata: { studyGoal: "Mathematics" },
    });
    await waitFor(() => {
      expect(screen.queryByText("Suggested Focus")).not.toBeInTheDocument();
    });
  });

  it("keeps the subject banner visible and shows an inline error when saving fails", async () => {
    (setStudyGoal as jest.Mock).mockRejectedValue(new Error("Could not update goal."));
    render(<GoalPromptBanner studyGoal={null} courseProgram="Mathematics" profileType="STUDENT" />);

    fireEvent.click(await screen.findByRole("button", { name: "Set as my focus" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not update goal.");
    expect(screen.getByText("Suggested Focus")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Set as my focus" })).toBeEnabled();
  });
});
