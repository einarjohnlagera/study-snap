import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { DashboardStudyPlanSection } from "./dashboard-study-plan-section";
import {
  adoptGoal,
  adoptStudyPlan,
  listCollections,
  listPublicStudyPlans,
} from "@/lib/api";
import { setJustAdoptedNotice } from "@/lib/just-adopted-notice";

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

jest.mock("@/lib/just-adopted-notice", () => ({
  setJustAdoptedNotice: jest.fn(),
}));

const publicPlan = {
  id: "source-plan-1",
  title: "LET Reviewer Plan",
  description: "A curated LET review sequence.",
  visibility: "PUBLIC" as const,
  courseProgram: "LET",
  sourcePlanId: null,
  parentCollectionId: null,
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
    (setJustAdoptedNotice as jest.Mock).mockReset();
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([publicPlan]);
    (listCollections as jest.Mock).mockResolvedValue([]);
  });

  // A zero-copy adopt yields an EMPTY collection. Routing to it as if it succeeded strands the learner
  // on a plan with nothing in it, at the most abandonment-sensitive moment and on the first thing they
  // chose to do. skippedCount was already surfaced; copiedCount was never checked.
  it("does not present a zero-copy adopt as success", async () => {
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-plan-1",
      copiedCount: 0,
      skippedCount: 4,
      alreadyAdopted: false,
    });

    render(<DashboardStudyPlanSection courseProgram=" let " profileType="STUDENT" />);

    expect(await screen.findByRole("heading", { name: "Recommended Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start this Study Plan" }));

    expect(await screen.findByText(/no notes ready to study yet/i)).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
  });

  // The caller has already resolved AND qualified the plan. Re-fetching would let the gate
  // (itemCount > 0 && readyCount > 0) and the render disagree, since this component's own effect takes
  // publicPlans[0] with no such predicate.
  it("uses a caller-resolved plan without re-fetching", async () => {
    render(
      <DashboardStudyPlanSection
        courseProgram=" let "
        profileType="STUDENT"
        context="practice-first"
        resolvedPlan={publicPlan}
      />,
    );

    expect(await screen.findByRole("heading", { name: /Study Plan/ })).toBeInTheDocument();
    expect(listPublicStudyPlans).not.toHaveBeenCalled();
  });

  it("keeps full discovery as the default and starts adoption from a matching public plan", async () => {
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-plan-1",
      copiedCount: 3,
      skippedCount: 1,
      alreadyAdopted: false,
    });

    render(<DashboardStudyPlanSection courseProgram=" let " profileType="STUDENT" />);

    expect(await screen.findByRole("heading", { name: "Recommended Study Plan" })).toBeInTheDocument();
    expect(listPublicStudyPlans).toHaveBeenCalledWith({ courseProgram: "let" });
    expect(screen.queryByText(/Optional: explore an official study plan/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start this Study Plan" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledWith("source-plan-1");
      expect(globalThis.sessionStorage.getItem("notelib-study-plan-skipped-personal-plan-1")).toBe("1");
      expect(setJustAdoptedNotice).not.toHaveBeenCalled();
      expect(pushMock).toHaveBeenCalledWith("/collections/personal-plan-1");
    });
  });

  it("shows an inline adoption error and allows a retry", async () => {
    (adoptStudyPlan as jest.Mock)
      .mockRejectedValueOnce(new Error("Could not adopt this plan."))
      .mockResolvedValueOnce({
        collectionId: "personal-plan-1",
        copiedCount: 3,
        skippedCount: 0,
        alreadyAdopted: false,
      });

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" />);

    const startButton = await screen.findByRole("button", { name: "Start this Study Plan" });
    fireEvent.click(startButton);

    expect(await screen.findByText("Could not adopt this plan.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start this Study Plan" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledTimes(2);
      expect(pushMock).toHaveBeenCalledWith("/collections/personal-plan-1");
    });
  });

  it("retries the post-adopt action without adopting the plan again", async () => {
    const onPlanStarted = jest.fn()
      .mockRejectedValueOnce(new Error("Could not save your profile."))
      .mockResolvedValueOnce(undefined);
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-plan-1",
      copiedCount: 3,
      skippedCount: 0,
      alreadyAdopted: false,
    });

    render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="STUDENT"
        onPlanStarted={onPlanStarted}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Start this Study Plan" }));
    expect(await screen.findByText("Could not save your profile.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start this Study Plan" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledTimes(1);
      expect(onPlanStarted).toHaveBeenCalledTimes(2);
    });
  });

  it("resolves the CTA and subject-count labels through profile-aware terminology, not a hardcoded generic word", async () => {
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-plan-1",
      copiedCount: 3,
      skippedCount: 0,
      alreadyAdopted: false,
    });

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="BOARD_EXAM" />);

    expect(await screen.findByRole("heading", { name: "Recommended Review Set" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start this Review Set" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start this plan" })).not.toBeInTheDocument();
  });

  it("adds supplementary framing only for the onboarding context", async () => {
    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" context="onboarding" />);

    expect(await screen.findByText("Optional: explore an official study plan alongside the Study Pack you just created.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start this Study Plan" })).toBeInTheDocument();
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

    const continueButton = await screen.findByRole("button", { name: "Continue this Study Plan" });
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

    expect(await screen.findByText("3 Subject Plans · 9 notes")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start this Goal" }));

    await waitFor(() => {
      expect(adoptGoal).toHaveBeenCalledWith("source-goal-1");
      expect(adoptStudyPlan).not.toHaveBeenCalled();
      expect(globalThis.sessionStorage.getItem("notelib-study-plan-skipped-personal-goal-1")).toBe("1");
      expect(setJustAdoptedNotice).toHaveBeenCalledWith("personal-goal-1");
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

  it("opens an owned source plan without re-adopting and shows the Adopted badge", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { ...publicPlan, id: "source-plan-1", sourcePlanId: null }, // the user owns the published source itself
    ]);

    render(<DashboardStudyPlanSection courseProgram="LET" profileType="STUDENT" />);

    expect(await screen.findByText("Adopted")).toBeInTheDocument();
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

  it("guides learners to set their course or program when none is configured", () => {
    render(<DashboardStudyPlanSection courseProgram={null} profileType="STUDENT" />);

    expect(screen.getByText("Set your course or program to find official study plans")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Set course or program" }))
      .toHaveAttribute("href", "/profile#learning-profile");
    expect(listPublicStudyPlans).not.toHaveBeenCalled();
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

  it("renders an Explore pointer without fetching public plans when no primary is set", () => {
    render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="STUDENT"
        discoveryPresentation="pointer"
      />,
    );

    expect(screen.getByRole("heading", { name: "Find your next Study Plan" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse in Explore" }))
      .toHaveAttribute("href", "/explore?source=dashboard");
    expect(screen.queryByText("Set your course or program to find official study plans")).not.toBeInTheDocument();
    expect(listPublicStudyPlans).not.toHaveBeenCalled();
  });

  it("renders nothing when suppressPointerWhenNoPrimary is set and no primary exists", () => {
    render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="STUDENT"
        discoveryPresentation="pointer"
        suppressPointerWhenNoPrimary
      />,
    );

    expect(screen.queryByRole("heading", { name: "Find your next Study Plan" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Browse in Explore" })).not.toBeInTheDocument();
    expect(listPublicStudyPlans).not.toHaveBeenCalled();
  });

  it("renders nothing in pointer mode while the primary lookup is pending", () => {
    (listCollections as jest.Mock).mockImplementation(() => new Promise(() => {}));

    render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="STUDENT"
        primaryCollectionId="primary-goal-1"
        discoveryPresentation="pointer"
      />,
    );

    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Browse in Explore" })).not.toBeInTheDocument();
    expect(listPublicStudyPlans).not.toHaveBeenCalled();
  });

  it("renders the Explore pointer when a configured primary cannot be found", async () => {
    (listCollections as jest.Mock).mockResolvedValue([]);

    render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="STUDENT"
        primaryCollectionId="stale-primary-id"
        discoveryPresentation="pointer"
      />,
    );

    expect(await screen.findByRole("link", { name: "Browse in Explore" }))
      .toHaveAttribute("href", "/explore?source=dashboard");
    expect(screen.queryByText("Set your course or program to find official study plans")).not.toBeInTheDocument();
    expect(listPublicStudyPlans).not.toHaveBeenCalled();
  });

  it("renders valid-primary continue mode identically in pointer and full presentations", async () => {
    const primaryPlan = {
      ...publicPlan,
      id: "primary-goal-1",
      visibility: "PRIVATE" as const,
      sourcePlanId: null,
      childCount: 2,
    };
    (listCollections as jest.Mock).mockResolvedValue([primaryPlan]);

    const pointerRender = render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="BOARD_EXAM"
        primaryCollectionId="primary-goal-1"
        discoveryPresentation="pointer"
      />,
    );
    expect(await screen.findByRole("heading", { name: "Primary Review Set" })).toBeInTheDocument();
    expect(listPublicStudyPlans).not.toHaveBeenCalled();
    const pointerMarkup = pointerRender.container.innerHTML;
    pointerRender.unmount();

    (listCollections as jest.Mock).mockClear();
    (listCollections as jest.Mock).mockResolvedValue([primaryPlan]);
    (listPublicStudyPlans as jest.Mock).mockClear();
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([publicPlan]);

    const fullRender = render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="BOARD_EXAM"
        primaryCollectionId="primary-goal-1"
        discoveryPresentation="full"
      />,
    );
    expect(await screen.findByRole("heading", { name: "Primary Review Set" })).toBeInTheDocument();
    await waitFor(() => {
      expect(listPublicStudyPlans).toHaveBeenCalled();
    });
    expect(fullRender.container.innerHTML).toBe(pointerMarkup);
  });

  it("still renders valid-primary continue mode when suppressPointerWhenNoPrimary is set", async () => {
    const primaryPlan = {
      ...publicPlan,
      id: "primary-goal-1",
      visibility: "PRIVATE" as const,
      sourcePlanId: null,
      childCount: 2,
    };
    (listCollections as jest.Mock).mockResolvedValue([primaryPlan]);

    render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="BOARD_EXAM"
        primaryCollectionId="primary-goal-1"
        discoveryPresentation="pointer"
        suppressPointerWhenNoPrimary
      />,
    );

    expect(await screen.findByRole("heading", { name: "Primary Review Set" })).toBeInTheDocument();
  });

  it("shows the owned Primary Review Set instead of the course/program recommendation", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { ...publicPlan, id: "primary-goal-1", visibility: "PRIVATE", sourcePlanId: null, childCount: 2 },
    ]);

    render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="BOARD_EXAM"
        primaryCollectionId="primary-goal-1"
      />,
    );

    expect(await screen.findByRole("heading", { name: "Primary Review Set" })).toBeInTheDocument();
    expect(screen.queryByText("LET")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /See all/ })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open this plan" }));

    expect(adoptGoal).not.toHaveBeenCalled();
    expect(adoptStudyPlan).not.toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith("/collections/primary-goal-1");
  });

  it("falls back to the course/program recommendation when the primary reference isn't found", async () => {
    (listCollections as jest.Mock).mockResolvedValue([]);

    render(
      <DashboardStudyPlanSection
        courseProgram="LET"
        profileType="STUDENT"
        primaryCollectionId="stale-primary-id"
      />,
    );

    expect(await screen.findByRole("heading", { name: "Recommended Study Plan" })).toBeInTheDocument();
  });

  it("renders nothing while the primary lookup is still pending", () => {
    (listCollections as jest.Mock).mockImplementation(() => new Promise(() => {}));

    render(
      <DashboardStudyPlanSection courseProgram={null} profileType="STUDENT" primaryCollectionId="primary-goal-1" />,
    );

    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });
});
