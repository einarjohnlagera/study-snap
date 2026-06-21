import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { DashboardStudyPlanSection } from "./dashboard-study-plan-section";
import {
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
  notesPracticed: 0,
  createdAt: "2026-06-01T00:00:00Z",
  updatedAt: "2026-06-02T00:00:00Z",
};

describe("DashboardStudyPlanSection", () => {
  beforeEach(() => {
    pushMock.mockReset();
    globalThis.sessionStorage.clear();
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
    expect(pushMock).toHaveBeenCalledWith("/collections/personal-plan-1");
  });

  it("renders nothing when no matching plan exists", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" />);

    await waitFor(() => {
      expect(listPublicStudyPlans).toHaveBeenCalled();
    });
    expect(screen.queryByText("Recommended Study Plan")).not.toBeInTheDocument();
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
