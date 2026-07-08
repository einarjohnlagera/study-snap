import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import PublishedPlansPage, { metadata } from "./page";
import { adoptGoal, adoptStudyPlan, getMe, listCollections, listPublicStudyPlans } from "@/lib/api";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ profileType: "STUDENT" }),
}));

jest.mock("@/lib/api", () => ({
  adoptGoal: jest.fn(),
  adoptStudyPlan: jest.fn(),
  getMe: jest.fn(),
  listCollections: jest.fn(),
  listPublicStudyPlans: jest.fn(),
}));

const planOne = {
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

const planTwo = {
  ...planOne,
  id: "source-plan-2",
  title: "LET Math Focus",
  description: "Math-heavy LET plan.",
  itemCount: 5,
};

describe("PublishedPlansPage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    globalThis.sessionStorage.clear();
    (adoptGoal as jest.Mock).mockReset();
    (adoptStudyPlan as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (listPublicStudyPlans as jest.Mock).mockReset();
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "LET", profileType: "STUDENT" });
    (listCollections as jest.Mock).mockResolvedValue([]);
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([planOne, planTwo]);
  });

  it("exports page metadata", () => {
    expect(metadata).toMatchObject({ title: "Recommended Plans | NoteLib" });
  });

  it("lists every published plan for the learner's course/program", async () => {
    render(<PublishedPlansPage />);

    expect(await screen.findByText("LET Reviewer Plan")).toBeInTheDocument();
    expect(screen.getByText("LET Math Focus")).toBeInTheDocument();
    expect(listPublicStudyPlans).toHaveBeenCalledWith({ courseProgram: "LET" });
    expect(screen.getAllByRole("button", { name: "Start this Study Plan" })).toHaveLength(2);
  });

  it("shows Continue for an already-adopted plan and Start for the rest", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { ...planOne, id: "personal-1", visibility: "PRIVATE", sourcePlanId: "source-plan-1" },
    ]);

    render(<PublishedPlansPage />);

    expect(await screen.findByRole("button", { name: "Continue this Study Plan" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start this Study Plan" })).toBeInTheDocument();
  });

  it("adopts a plan and records the skipped notice before navigating", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([planOne]);
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-1",
      copiedCount: 2,
      skippedCount: 1,
      alreadyAdopted: false,
    });

    render(<PublishedPlansPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start this Study Plan" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledWith("source-plan-1");
      expect(globalThis.sessionStorage.getItem("notelib-study-plan-skipped-personal-1")).toBe("1");
      expect(pushMock).toHaveBeenCalledWith("/collections/personal-1");
    });
  });

  it("adopts a Goal through recursive adopt and redirects to the personal Goal", async () => {
    const goalPlan = {
      ...planOne,
      id: "source-goal-1",
      title: "LET Goal",
      itemCount: 8,
      childCount: 2,
    };
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([goalPlan]);
    (adoptGoal as jest.Mock).mockResolvedValue({
      goalCollectionId: "personal-goal-1",
      adoptedSubjectCount: 2,
      skippedSubjectCount: 1,
      totalNotesCopied: 8,
      totalNotesSkipped: 0,
      alreadyAdopted: false,
    });

    render(<PublishedPlansPage />);

    expect(await screen.findByText("2 Subject Plans · 8 notes")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Start this Goal" }));

    await waitFor(() => {
      expect(adoptGoal).toHaveBeenCalledWith("source-goal-1");
      expect(adoptStudyPlan).not.toHaveBeenCalled();
      expect(globalThis.sessionStorage.getItem("notelib-study-plan-skipped-personal-goal-1")).toBe("1");
      expect(pushMock).toHaveBeenCalledWith("/collections/personal-goal-1");
    });
  });

  it("shows an inline error when recursive Goal adopt fails", async () => {
    const goalPlan = {
      ...planOne,
      id: "source-goal-1",
      title: "LET Goal",
      childCount: 2,
    };
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([goalPlan]);
    (adoptGoal as jest.Mock).mockRejectedValue(new Error("Could not adopt Goal."));

    render(<PublishedPlansPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start this Goal" }));

    expect(await screen.findByText("Could not adopt Goal.")).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("shows a guidance state when no course/program is set", async () => {
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: null, profileType: "STUDENT" });

    render(<PublishedPlansPage />);

    expect(await screen.findByText("Set your course or program first")).toBeInTheDocument();
    expect(listPublicStudyPlans).not.toHaveBeenCalled();
  });

  it("shows an empty state when the track has no published plans", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);

    render(<PublishedPlansPage />);

    expect(await screen.findByText("No published study plans yet")).toBeInTheDocument();
  });
});
