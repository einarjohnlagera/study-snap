import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import CollectionsPage, { metadata } from "./page";
import { CollectionsPageClient } from "./collections-page-client";
import { createCollection, getMe, listCollections } from "@/lib/api";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};

let searchParamsMock = new URLSearchParams();

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  useSearchParams: () => searchParamsMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ profileType: "STUDENT" }),
}));

jest.mock("@/lib/api", () => ({
  createCollection: jest.fn(),
  getMe: jest.fn(),
  listCollections: jest.fn(),
}));

jest.mock("@/app/dashboard/dashboard-study-plan-section", () => ({
  DashboardStudyPlanSection: (props: { primaryCollectionId?: string | null }) => (
    <div data-testid="dashboard-study-plan-section" data-primary-collection-id={props.primaryCollectionId ?? ""} />
  ),
}));

describe("CollectionsPage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (createCollection as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: null, primaryCollectionId: null });
    searchParamsMock = new URLSearchParams();
    globalThis.sessionStorage.clear();
  });

  it("auto-opens the create modal when arriving with ?new=1", async () => {
    searchParamsMock = new URLSearchParams("new=1");
    (listCollections as jest.Mock).mockResolvedValue([]);

    render(<CollectionsPage />);

    expect(await screen.findByRole("heading", { name: "New Study Plan" })).toBeInTheDocument();
  });

  it("exports page metadata", () => {
    expect(metadata).toMatchObject({ title: "Collections | NoteLib" });
  });

  it("renders collection cards returned by the API", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "collection-1",
        title: "Midterm Plan",
        description: "Weeks 1-4",
        visibility: "PRIVATE",
        courseProgram: null,
        sourcePlanId: null,
        parentCollectionId: null,
        itemCount: 2,
        childCount: 0,
        notesPracticed: 0,
        createdAt: "2026-06-01T00:00:00Z",
        updatedAt: "2026-06-02T00:00:00Z",
      },
    ]);

    render(<CollectionsPage />);

    expect(await screen.findByRole("heading", { name: "Your Study Plans" })).toBeInTheDocument();
    const title = await screen.findByText("Midterm Plan");
    expect(title.closest("a")).toHaveAttribute("href", "/collections/collection-1");
    expect(screen.getByText("2 notes")).toBeInTheDocument();
  });

  it("shows course/program on the card instead of the description", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "collection-1",
        title: "PNLE Mastery",
        description: "A long description that should not appear on the card",
        visibility: "PRIVATE",
        courseProgram: "Nursing",
        sourcePlanId: null,
        parentCollectionId: null,
        itemCount: 2,
        childCount: 0,
        notesPracticed: 0,
        createdAt: "2026-06-01T00:00:00Z",
        updatedAt: "2026-06-02T00:00:00Z",
      },
    ]);

    render(<CollectionsPage />);

    expect(await screen.findByText("PNLE Mastery")).toBeInTheDocument();
    expect(screen.getByText("Nursing")).toBeInTheDocument();
    expect(screen.queryByText("A long description that should not appear on the card")).not.toBeInTheDocument();
    expect(screen.queryByText("No description yet.")).not.toBeInTheDocument();
  });

  it("shows Not started when no notes have been practiced", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ itemCount: 3, notesPracticed: 0 }),
    ]);

    render(<CollectionsPage />);

    expect(await screen.findByText("Not started")).toBeInTheDocument();
  });

  it("shows In progress when some but not all notes have been practiced", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ itemCount: 3, notesPracticed: 1 }),
    ]);

    render(<CollectionsPage />);

    expect(await screen.findByText("In progress")).toBeInTheDocument();
  });

  it("shows Completed when practiced notes cover the plan", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ itemCount: 3, notesPracticed: 3 }),
    ]);

    render(<CollectionsPage />);

    expect(await screen.findByText("Completed")).toBeInTheDocument();
  });

  it("shows child plan count for a goal card", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ itemCount: 0, childCount: 3 }),
    ]);

    render(<CollectionsPage />);

    expect(await screen.findByText("3 plans")).toBeInTheDocument();
    expect(screen.queryByText("Not started")).not.toBeInTheDocument();
  });

  it("shows an Adopted badge only for a collection with a sourcePlanId", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ id: "collection-1", title: "Adopted Plan", sourcePlanId: "source-1" }),
      buildCollectionSummary({ id: "collection-2", title: "Own Plan", sourcePlanId: null }),
    ]);

    render(<CollectionsPage />);

    await screen.findByText("Adopted Plan");
    expect(screen.getAllByText("Adopted")).toHaveLength(1);
  });

  it("groups Primary/Adopted identity badges under the title, above status, above the notes count/updated-at row", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ id: "collection-1", title: "Flagship Plan", sourcePlanId: "source-1", itemCount: 3, notesPracticed: 1 }),
    ]);
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: null, primaryCollectionId: "collection-1" });

    render(<CollectionsPage />);

    const title = await screen.findByText("Flagship Plan");
    const primaryBadge = screen.getByText("Primary");
    const adoptedBadge = screen.getByText("Adopted");
    const statusBadge = screen.getByText("In progress");
    const notesScope = screen.getByText("3 notes");
    const updatedAt = screen.getByText(/Updated/);

    // DOCUMENT_POSITION_FOLLOWING on other.compareDocumentPosition(this) means `this` comes after `other`.
    expect(title.compareDocumentPosition(primaryBadge) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(primaryBadge.compareDocumentPosition(adoptedBadge) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(adoptedBadge.compareDocumentPosition(statusBadge) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(statusBadge.compareDocumentPosition(notesScope) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(notesScope.compareDocumentPosition(updatedAt) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("shows Not started for an empty plan", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ itemCount: 0, notesPracticed: 0 }),
    ]);

    render(<CollectionsPage />);

    expect(await screen.findByText("Not started")).toBeInTheDocument();
  });

  it("shows a pending action notice once as a toast and clears it", async () => {
    globalThis.sessionStorage.setItem("notelib-collection-action-notice", "Study Plan deleted.");
    (listCollections as jest.Mock).mockResolvedValue([]);

    const { unmount } = render(<CollectionsPage />);

    expect(await screen.findByText("Study Plan deleted.")).toBeInTheDocument();
    expect(globalThis.sessionStorage.getItem("notelib-collection-action-notice")).toBeNull();
    unmount();

    render(<CollectionsPage />);
    await screen.findByText("No study plans yet");
    expect(screen.queryByText("Study Plan deleted.")).not.toBeInTheDocument();
  });

  it("shows profile-aware empty state when there are no collections", async () => {
    (listCollections as jest.Mock).mockResolvedValue([]);

    render(<CollectionsPage />);

    expect(await screen.findByText("No study plans yet")).toBeInTheDocument();
    expect(screen.getByText(/Group notes for a unit/)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "New Study Plan" }).length).toBeGreaterThan(0);
  });

  it("shows retry on load error", async () => {
    (listCollections as jest.Mock).mockRejectedValueOnce(new Error("Network down"));
    (listCollections as jest.Mock).mockResolvedValueOnce([]);

    render(<CollectionsPage />);

    expect(await screen.findByRole("heading", { name: "Could not load study plans" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    await waitFor(() => {
      expect(listCollections).toHaveBeenCalledTimes(2);
    });
  });

  it("creates a collection and navigates to detail", async () => {
    (listCollections as jest.Mock).mockResolvedValue([]);
    (createCollection as jest.Mock).mockResolvedValue({
      id: "created-1",
      title: "Finals",
      description: null,
      visibility: "PRIVATE",
      courseProgram: null,
      sourcePlanId: null,
      parentCollectionId: null,
      childCount: 0,
      createdAt: "2026-06-01T00:00:00Z",
      updatedAt: "2026-06-01T00:00:00Z",
      progress: {
        totalNotes: 0,
        notesWithStudyPack: 0,
        notesPracticed: 0,
      },
      items: [],
    });

    render(<CollectionsPageClient />);

    await screen.findByText("No study plans yet");
    fireEvent.click(screen.getAllByRole("button", { name: "New Study Plan" })[0]);
    fireEvent.change(screen.getByPlaceholderText("Study Plan title"), { target: { value: "Finals" } });
    fireEvent.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({ title: "Finals", description: null });
      expect(pushMock).toHaveBeenCalledWith("/collections/created-1/builder");
    });
  });

  it("passes the user's primaryCollectionId through to DashboardStudyPlanSection", async () => {
    (listCollections as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: null, primaryCollectionId: "goal-1" });

    render(<CollectionsPageClient />);

    await waitFor(() => {
      expect(screen.getByTestId("dashboard-study-plan-section")).toHaveAttribute(
        "data-primary-collection-id",
        "goal-1",
      );
    });
  });

  it("shows a Primary badge only on the card matching primaryCollectionId", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ id: "collection-1", title: "Own Plan" }),
      buildCollectionSummary({ id: "collection-2", title: "Primary Plan" }),
    ]);
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: null, primaryCollectionId: "collection-2" });

    render(<CollectionsPageClient />);

    await screen.findByText("Primary Plan");
    expect(screen.getAllByText("Primary")).toHaveLength(1);
  });

  it("sorts the primary collection to the front of the grid regardless of API order", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ id: "collection-1", title: "First By Date" }),
      buildCollectionSummary({ id: "collection-2", title: "Second By Date" }),
      buildCollectionSummary({ id: "collection-3", title: "Primary Plan" }),
    ]);
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: null, primaryCollectionId: "collection-3" });

    render(<CollectionsPageClient />);

    await screen.findByText("Primary Plan");
    const headings = screen.getAllByRole("heading", { level: 3 });
    expect(headings.map((heading) => heading.textContent)).toEqual([
      "Primary Plan",
      "First By Date",
      "Second By Date",
    ]);
  });

  it("keeps the API-returned order when no primary collection is set", async () => {
    (listCollections as jest.Mock).mockResolvedValue([
      buildCollectionSummary({ id: "collection-1", title: "First By Date" }),
      buildCollectionSummary({ id: "collection-2", title: "Second By Date" }),
    ]);
    (getMe as jest.Mock).mockResolvedValue({ courseProgram: null, primaryCollectionId: null });

    render(<CollectionsPageClient />);

    await screen.findByText("First By Date");
    const headings = screen.getAllByRole("heading", { level: 3 });
    expect(headings.map((heading) => heading.textContent)).toEqual(["First By Date", "Second By Date"]);
    expect(screen.queryByText("Primary")).not.toBeInTheDocument();
  });
});

function buildCollectionSummary(overrides: Partial<{
  id: string;
  title: string;
  description: string | null;
  itemCount: number;
  childCount: number;
  notesPracticed: number;
  sourcePlanId: string | null;
}> = {}) {
  return {
    id: overrides.id ?? "collection-1",
    title: overrides.title ?? "Midterm Plan",
    description: overrides.description ?? "Weeks 1-4",
    visibility: "PRIVATE" as const,
    courseProgram: null,
    sourcePlanId: overrides.sourcePlanId ?? null,
    parentCollectionId: null,
    itemCount: overrides.itemCount ?? 2,
    childCount: overrides.childCount ?? 0,
    notesPracticed: overrides.notesPracticed ?? 0,
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-02T00:00:00Z",
  };
}
