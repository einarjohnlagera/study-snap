import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { DashboardStudyPlanSection } from "./dashboard-study-plan-section";
import {
  adoptGoal,
  adoptStudyPlan,
  listCollections,
  listPublicStudyPlans,
} from "@/lib/api";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}));

jest.mock("@/lib/api", () => ({
  adoptGoal: jest.fn(),
  adoptStudyPlan: jest.fn(),
  listCollections: jest.fn(),
  listPublicStudyPlans: jest.fn(),
}));

const publicPlan = {
  id: "source-plan-1",
  title: "LET Reviewer Plan",
  description: "A curated LET review sequence.",
  visibility: "PUBLIC" as const,
  courseProgram: "LET",
  sourcePlanId: null,
  itemCount: 3,
  childCount: 0,
  notesPracticed: 0,
  createdAt: "2026-06-01T00:00:00Z",
  updatedAt: "2026-06-02T00:00:00Z",
};

describe("DashboardStudyPlanSection", () => {
  beforeEach(() => {
    pushMock.mockReset();
    globalThis.sessionStorage.clear();
    (adoptGoal as jest.Mock).mockReset();
    (adoptStudyPlan as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (listPublicStudyPlans as jest.Mock).mockReset();
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([publicPlan]);
    (listCollections as jest.Mock).mockResolvedValue([]);
  });

  it("surfaces a matching public plan and starts adoption", async () => {
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-plan-1",
      copiedCount: 3,
      skippedCount: 1,
      alreadyAdopted: false,
    });

    render(<DashboardStudyPlanSection courseProgram=" let " profileType="STUDENT" />);

    expect(await screen.findByRole("heading", { name: "Recommended Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start this plan" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledWith("source-plan-1");
      expect(globalThis.sessionStorage.getItem("notelib-study-plan-skipped-personal-plan-1")).toBe("1");
      expect(pushMock).toHaveBeenCalledWith("/collections/personal-plan-1");
    });
  });

  it("continues an already adopted plan without adopting again", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      {
        ...publicPlan,
        id: "personal-plan-1",
        visibility: "PRIVATE",
        sourcePlanId: "source-plan-1",
      },
    ]);

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" />);

    const continueButton = await screen.findByRole("button", { name: "Continue this plan" });
    fireEvent.click(continueButton);

    expect(adoptStudyPlan).not.toHaveBeenCalled();
    expect(adoptGoal).not.toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/collections/personal-plan-1");
  });

  it("starts recursive Goal adoption for a matching public Goal", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([
      {
        ...publicPlan,
        id: "source-goal-1",
        title: "LET Goal",
        itemCount: 9,
        childCount: 3,
      },
    ]);
    (adoptGoal as jest.Mock).mockResolvedValue({
      goalCollectionId: "personal-goal-1",
      adoptedSubjectCount: 2,
      skippedSubjectCount: 1,
      totalNotesCopied: 9,
      totalNotesSkipped: 0,
      alreadyAdopted: false,
    });

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" />);

    expect(await screen.findByText("3 Subject plans")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start this Goal" }));

    await waitFor(() => {
      expect(adoptGoal).toHaveBeenCalledWith("source-goal-1");
      expect(adoptStudyPlan).not.toHaveBeenCalled();
      expect(globalThis.sessionStorage.getItem("notelib-study-plan-skipped-personal-goal-1")).toBe("1");
      expect(pushMock).toHaveBeenCalledWith("/collections/personal-goal-1");
    });
  });

  it("continues an already adopted Goal without adopting again", async () => {
    const publicGoal = {
      ...publicPlan,
      id: "source-goal-1",
      title: "LET Goal",
      childCount: 2,
    };
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([publicGoal]);
    (listCollections as jest.Mock).mockResolvedValue([
      {
        ...publicGoal,
        id: "personal-goal-1",
        visibility: "PRIVATE",
        sourcePlanId: "source-goal-1",
      },
    ]);

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" />);

    fireEvent.click(await screen.findByRole("button", { name: "Continue this Goal" }));

    expect(adoptGoal).not.toHaveBeenCalled();
    expect(adoptStudyPlan).not.toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/collections/personal-goal-1");
  });

  it("opens an owned source plan without re-adopting and shows the in-library badge", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { ...publicPlan, id: "source-plan-1", sourcePlanId: null }, // the user owns the published source itself
    ]);

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" />);

    expect(await screen.findByText("In your library")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open this plan" }));

    expect(adoptStudyPlan).not.toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/collections/source-plan-1");
  });

  it("renders nothing when no matching plan exists", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" />);

    await waitFor(() => {
      expect(listPublicStudyPlans).toHaveBeenCalled();
    });
    expect(screen.queryByText("Recommended Study Plan")).not.toBeInTheDocument();
  });

  it("renders a browse empty state when browseWhenEmpty is set and no plan matches", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" browseWhenEmpty />);

    expect(await screen.findByText(/No curated study plans for/i)).toBeInTheDocument();
  });

  it("shows a view-all link only when multiple plans match and a href is provided", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([
      publicPlan,
      { ...publicPlan, id: "source-plan-2", title: "LET Reviewer Plan 2" },
    ]);

    render(
      <DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" viewAllHref="/collections/published" />,
    );

    const viewAll = await screen.findByRole("link", { name: "See all 2 study plans" });
    expect(viewAll).toHaveAttribute("href", "/collections/published");
  });

  it("does not show a view-all link without a href even when multiple plans match (onboarding card)", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([
      publicPlan,
      { ...publicPlan, id: "source-plan-2", title: "LET Reviewer Plan 2" },
    ]);

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" />);

    expect(await screen.findByRole("heading", { name: "Recommended Study Plan" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /See all/ })).not.toBeInTheDocument();
  });

  it("does not show a view-all link when only one plan matches", async () => {
    render(
      <DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" viewAllHref="/collections/published" />,
    );

    expect(await screen.findByRole("heading", { name: "Recommended Study Plan" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /See all/ })).not.toBeInTheDocument();
  });
});
