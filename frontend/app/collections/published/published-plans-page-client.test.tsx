import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import PublishedPlansPage, { metadata } from "./page";
import { PublishedPlansPageClient } from "./published-plans-page-client";
import {
  adoptGoal,
  adoptStudyPlan,
  getMe,
  getPublicStudyPlanDetail,
  listCollections,
  listPublicStudyPlans,
  trackAnalyticsEvent,
} from "@/lib/api";
import { clearDiscoveryIntentCookie, getDiscoveryIntentCookie } from "@/lib/discovery-intent";

const pushMock = jest.fn();
const routerMock = { push: pushMock };
let currentAuthUser: { profileType: "STUDENT" } | null = { profileType: "STUDENT" };
let searchParamsMock = new URLSearchParams();

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  useSearchParams: () => searchParamsMock,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => currentAuthUser,
}));

jest.mock("@/lib/api", () => ({
  adoptGoal: jest.fn(),
  adoptStudyPlan: jest.fn(),
  getMe: jest.fn(),
  getPublicStudyPlanDetail: jest.fn(),
  listCollections: jest.fn(),
  listPublicStudyPlans: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
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
    currentAuthUser = { profileType: "STUDENT" };
    pushMock.mockReset();
    globalThis.sessionStorage.clear();
    clearDiscoveryIntentCookie();
    (adoptGoal as jest.Mock).mockReset();
    (adoptStudyPlan as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getPublicStudyPlanDetail as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (listPublicStudyPlans as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    searchParamsMock = new URLSearchParams();
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: "LET", profileType: "STUDENT" });
    (listCollections as jest.Mock).mockResolvedValue([]);
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([planOne, planTwo]);
    (getPublicStudyPlanDetail as jest.Mock).mockResolvedValue({
      ...planOne,
      estimatedStudyHours: 4,
      progress: { totalNotes: 1, notesWithStudyPack: 1, notesPracticed: 0 },
      items: [{ noteId: "note-1", title: "Educational Psychology", subject: "Professional Education", label: "Foundations" }],
    });
  });

  it("exports page metadata", () => {
    expect(metadata).toMatchObject({ title: "Recommended Plans | NoteLib" });
  });

  it("tracks the published-plan catalog page view once", async () => {
    const { rerender } = render(<PublishedPlansPage />);

    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "PUBLISHED_PLANS_VIEWED",
        entityId: null,
        metadata: { referrerSource: "direct" },
      });
    });

    rerender(<PublishedPlansPage />);

    expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["/dashboard", "Dashboard"],
    ["/public/library", "Public Library"],
    ["/collections", "Collections"],
    ["/collections/collection-1", "Collections"],
  ])("uses an allowlisted ref for the %s back link", async (ref, label) => {
    searchParamsMock = new URLSearchParams({ ref });

    render(<PublishedPlansPage />);

    expect(await screen.findByRole("link", { name: label })).toHaveAttribute("href", ref);
  });

  it.each(["/", "//evil.com", "/unrelated"])("falls back to Dashboard for an invalid ref: %s", async (ref) => {
    searchParamsMock = new URLSearchParams({ ref });

    render(<PublishedPlansPage />);

    expect(await screen.findByRole("link", { name: "Dashboard" })).toHaveAttribute("href", "/dashboard");
  });

  it("lists every published plan for the learner's course/program", async () => {
    render(<PublishedPlansPage />);

    expect(await screen.findByText("Recommended Study Plans")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Browse All Official Study Plans" })).toBeInTheDocument();
    expect(await screen.findAllByText("LET Reviewer Plan")).toHaveLength(2);
    expect(screen.getAllByText("LET Math Focus")).toHaveLength(2);
    expect(listPublicStudyPlans).toHaveBeenCalledWith({ courseProgram: "LET" });
    expect(listPublicStudyPlans).toHaveBeenCalledWith({});
    expect(listCollections).toHaveBeenCalledTimes(1);
    expect(screen.getAllByRole("button", { name: "Start this Study Plan" })).toHaveLength(4);
  });

  it("reuses the catalog without standalone navigation chrome when embedded", async () => {
    render(<PublishedPlansPageClient embedded discoverySource="explore" />);

    expect(await screen.findByRole("heading", { name: "Recommended Study Plans" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Dashboard" })).not.toBeInTheDocument();
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(expect.objectContaining({
      eventType: "PUBLISHED_PLANS_VIEWED",
    }));
    expect(listPublicStudyPlans).toHaveBeenCalledWith({ courseProgram: "LET" });
    expect(listPublicStudyPlans).toHaveBeenCalledWith({});
  });

  it("threads discoveryMetadata through to each plan card's preview/adopt analytics", async () => {
    render(
      <PublishedPlansPageClient embedded discoverySource="explore" discoveryMetadata={{ pointerSource: "dashboard" }} />,
    );

    const previewButtons = await screen.findAllByRole("button", { name: /Preview this plan/ });
    fireEvent.click(previewButtons[0]);

    expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({
      eventType: "EXPLORE_OFFICIAL_SET_PREVIEWED",
      metadata: { pointerSource: "dashboard", source: "explore" },
    }));
  });

  it("shows Continue for an already-adopted plan and Start for the rest", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      { ...planOne, id: "personal-1", visibility: "PRIVATE", sourcePlanId: "source-plan-1" },
    ]);

    render(<PublishedPlansPage />);

    expect(await screen.findAllByRole("button", { name: "Continue this Study Plan" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Start this Study Plan" })).toHaveLength(2);
  });

  it("adopts a plan and records the skipped notice before navigating", async () => {
    (listPublicStudyPlans as jest.Mock).mockImplementation((params?: { courseProgram?: string }) => (
      params?.courseProgram ? Promise.resolve([planOne]) : Promise.resolve([])
    ));
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
    (listPublicStudyPlans as jest.Mock).mockImplementation((params?: { courseProgram?: string }) => (
      params?.courseProgram ? Promise.resolve([goalPlan]) : Promise.resolve([])
    ));
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
    (listPublicStudyPlans as jest.Mock).mockImplementation((params?: { courseProgram?: string }) => (
      params?.courseProgram ? Promise.resolve([goalPlan]) : Promise.resolve([])
    ));
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
    expect(screen.getByText("LET Reviewer Plan")).toBeInTheDocument();
    expect(screen.getByText("LET Math Focus")).toBeInTheDocument();
    expect(listPublicStudyPlans).toHaveBeenCalledWith({});
    expect((listPublicStudyPlans as jest.Mock).mock.calls.every(([params]) => !params?.courseProgram)).toBe(true);
  });

  it("shows an empty state when the track has no published plans", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);

    render(<PublishedPlansPage />);

    expect(await screen.findByText("No published study plans yet")).toBeInTheDocument();
  });

  it("sorts Browse All alphabetically by title without deduplicating recommended plans", async () => {
    const alphaPlan = { ...planOne, id: "source-alpha", title: "Alpha Review" };
    const zetaPlan = { ...planOne, id: "source-zeta", title: "zeta Review" };
    (listPublicStudyPlans as jest.Mock).mockImplementation((params?: { courseProgram?: string }) => (
      params?.courseProgram ? Promise.resolve([zetaPlan]) : Promise.resolve([zetaPlan, alphaPlan])
    ));

    render(<PublishedPlansPage />);

    const browseAllSection = (await screen.findByRole("heading", { name: "Browse All Official Study Plans" }))
      .closest("section");
    expect(browseAllSection).not.toBeNull();
    const browseTitles = (await within(browseAllSection as HTMLElement).findAllByRole("heading", { level: 3 }))
      .map((heading) => heading.textContent);

    expect(browseTitles).toEqual(["Alpha Review", "zeta Review"]);
    expect(screen.getAllByText("zeta Review")).toHaveLength(2);
  });

  it("renders Browse All errors independently from Recommended", async () => {
    (listPublicStudyPlans as jest.Mock).mockImplementation((params?: { courseProgram?: string }) => (
      params?.courseProgram ? Promise.resolve([planOne]) : Promise.reject(new Error("Browse failed"))
    ));

    render(<PublishedPlansPage />);

    expect(await screen.findByText("LET Reviewer Plan")).toBeInTheDocument();
    expect(screen.getByText("Could not load official study plans")).toBeInTheDocument();
    expect(screen.getByText("Browse failed")).toBeInTheDocument();
  });

  it("renders a distinct Browse All empty state when no public plans exist", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);

    render(<PublishedPlansPage />);

    expect(await screen.findByText("No Official Study Plans have been published yet")).toBeInTheDocument();
    expect(screen.getByText("Check back later for curated sets from the NoteLib team.")).toBeInTheDocument();
  });

  it("still renders public plans when the owned-collection adoption join fails", async () => {
    (listCollections as jest.Mock).mockRejectedValue(new Error("Owned collections unavailable"));

    render(<PublishedPlansPage />);

    expect(await screen.findAllByText("LET Reviewer Plan")).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Start this Study Plan" })).toHaveLength(4);
    expect(screen.queryByText("In your library")).not.toBeInTheDocument();
  });

  it("lets anonymous visitors browse and preview plans without loading private profile data", async () => {
    currentAuthUser = null;

    render(<PublishedPlansPage />);

    expect(await screen.findByRole("heading", { name: "Browse All Official Study Plans" })).toBeInTheDocument();
    expect(await screen.findAllByRole("button", { name: "Sign in to adopt" })).toHaveLength(2);
    expect(getMe).not.toHaveBeenCalled();
    expect(listCollections).not.toHaveBeenCalled();

    fireEvent.click((await screen.findAllByRole("button", { name: /^Preview this plan/ }))[0]);

    expect(await screen.findByText("Educational Psychology")).toBeInTheDocument();
    expect(getPublicStudyPlanDetail).toHaveBeenCalledWith("source-plan-2");
  });

  it("preserves an anonymous Explore adoption through signup without calling adopt", async () => {
    currentAuthUser = null;
    searchParamsMock = new URLSearchParams({ source: "dashboard" });

    render(<PublishedPlansPageClient embedded discoverySource="explore" />);

    fireEvent.click((await screen.findAllByRole("button", { name: "Sign in to adopt" }))[0]);

    expect(adoptStudyPlan).not.toHaveBeenCalled();
    expect(adoptGoal).not.toHaveBeenCalled();
    expect(getDiscoveryIntentCookie()).toEqual({
      planId: "source-plan-2",
      planType: "study-plan",
      returnPath: "/explore?source=dashboard",
    });
    expect(pushMock).toHaveBeenCalledWith("/auth?mode=signup&intent=discovery-adopt");
  });

  it("still enters signup when browser privacy settings block the intent cookie", async () => {
    currentAuthUser = null;
    const setter = jest.spyOn(Document.prototype, "cookie", "set").mockImplementation(() => {
      throw new Error("Cookies blocked");
    });

    render(<PublishedPlansPageClient embedded discoverySource="explore" />);
    fireEvent.click((await screen.findAllByRole("button", { name: "Sign in to adopt" }))[0]);

    expect(pushMock).toHaveBeenCalledWith("/auth?mode=signup&intent=discovery-adopt");
    expect(adoptStudyPlan).not.toHaveBeenCalled();
    setter.mockRestore();
  });
});
