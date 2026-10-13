import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GoalPromptBanner } from "./goal-prompt-banner";
import { addExamGoal, adoptStudyPlan, setStudyGoal, trackAnalyticsEvent } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  addExamGoal: jest.fn(),
  adoptStudyPlan: jest.fn(),
  setStudyGoal: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("GoalPromptBanner", () => {
  beforeEach(() => {
    document.cookie = "notelib-exam-intent=; path=/; max-age=0; SameSite=Strict";
    localStorage.clear();
    (setStudyGoal as jest.Mock).mockReset();
    (addExamGoal as jest.Mock).mockReset();
    (adoptStudyPlan as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (setStudyGoal as jest.Mock).mockResolvedValue({ studyGoal: "ale" });
    (addExamGoal as jest.Mock).mockResolvedValue([{ slug: "ale", isActive: true }]);
  });

  it("renders nothing when an exam goal is already set", () => {
    render(<GoalPromptBanner studyGoal="ale" courseProgram="Architecture" profileType="STUDENT" />);

    expect(screen.queryByText(/Make Architect Licensure Examination/)).not.toBeInTheDocument();
  });

  it("suppresses the generic prompt when subjects are already focused", () => {
    render(<GoalPromptBanner studyGoal={null} courseProgram="Architecture" profileType="STUDENT"
      focusSubjects={["Biology"]} />);
    expect(screen.queryByText(/Suggested Goal/)).not.toBeInTheDocument();
  });

  it("offers an intent goal even when a different goal is active", async () => {
    document.cookie = "notelib-exam-intent=pnle; path=/; max-age=1800; SameSite=Strict";
    render(<GoalPromptBanner studyGoal="ale" courseProgram="Architecture" profileType="STUDENT"
      examGoals={[{ slug: "ale", isActive: true }]} />);
    fireEvent.click(await screen.findByRole("button", { name: "Set as my goal" }));
    await waitFor(() => expect(addExamGoal).toHaveBeenCalledWith("pnle"));
    expect(adoptStudyPlan).not.toHaveBeenCalled();
    expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({ eventType: "EXAM_GOAL_ADDED" }));
  });

  it("confirms before intent clears an existing subject focus", async () => {
    document.cookie = "notelib-exam-intent=pnle; path=/; max-age=1800; SameSite=Strict";
    render(<GoalPromptBanner studyGoal={null} courseProgram="Nursing" profileType="STUDENT"
      focusSubjects={["Biology"]} />);
    fireEvent.click(await screen.findByRole("button", { name: "Set as my goal" }));
    expect(addExamGoal).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Add exam goal" }));
    await waitFor(() => expect(addExamGoal).toHaveBeenCalledWith("pnle"));
  });

  it("renders nothing when no cookie and no course program exist", () => {
    render(<GoalPromptBanner studyGoal={null} courseProgram={null} profileType="STUDENT" />);

    expect(screen.queryByText(/Suggested Goal/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Suggested Focus/)).not.toBeInTheDocument();
  });

  it("persists generic dismissal across a remount", async () => {
    const props = { studyGoal: null, courseProgram: "Architecture", profileType: "STUDENT", userId: "user-1" };
    const view = render(<GoalPromptBanner {...props} />);
    fireEvent.click(await screen.findByRole("button", { name: "Dismiss" }));
    expect(localStorage.getItem("notelib-goal-prompt-dismissed:user-1")).toBe("1");
    view.unmount();
    render(<GoalPromptBanner {...props} />);
    expect(screen.queryByText("Suggested Goal: ALE")).not.toBeInTheDocument();
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
      expect(addExamGoal).toHaveBeenCalledWith("ale");
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
    (addExamGoal as jest.Mock).mockRejectedValue(new Error("Could not update exam goal."));
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
