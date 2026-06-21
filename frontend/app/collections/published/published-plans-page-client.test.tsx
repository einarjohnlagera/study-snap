import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import PublishedPlansPage, { metadata } from "./page";
import { adoptStudyPlan, getMe, listCollections, listPublicStudyPlans } from "@/lib/api";

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
    expect(screen.getAllByRole("button", { name: "Start this plan" })).toHaveLength(2);
  });

  it("shows Continue for an already-adopted plan and Start for the rest", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { ...planOne, id: "personal-1", visibility: "PRIVATE", sourcePlanId: "source-plan-1" },
    ]);

    render(<PublishedPlansPage />);

    expect(await screen.findByRole("button", { name: "Continue this plan" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start this plan" })).toBeInTheDocument();
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

    fireEvent.click(await screen.findByRole("button", { name: "Start this plan" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledWith("source-plan-1");
      expect(globalThis.sessionStorage.getItem("notelib-study-plan-skipped-personal-1")).toBe("1");
      expect(pushMock).toHaveBeenCalledWith("/collections/personal-1");
    });
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
