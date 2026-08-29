import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { StudyPlanBuilderPageClient } from "./study-plan-builder-page-client";
import {
  addCollectionItems,
  createCollection,
  deleteCollection,
  getCollection,
  getCollectionGoal,
  listNotes,
  removeCollectionItem,
  reorderCollectionChildren,
  setCollectionItemOrder,
  setCollectionParent,
  trackAnalyticsEvent,
  updateCollection,
  type NoteCollectionItem,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => {
  class ApiRequestError extends Error {
    status: number;
    code: string | null;

    constructor(message: string, options: { status: number; code?: string | null }) {
      super(message);
      this.status = options.status;
      this.code = options.code ?? null;
    }
  }

  return {
    addCollectionItems: jest.fn(),
    ApiRequestError,
    createCollection: jest.fn(),
    deleteCollection: jest.fn(),
    getCollection: jest.fn(),
    getCollectionGoal: jest.fn(),
    listNotes: jest.fn(),
    removeCollectionItem: jest.fn(),
    reorderCollectionChildren: jest.fn(),
    setCollectionItemOrder: jest.fn(),
    setCollectionParent: jest.fn(),
    trackAnalyticsEvent: jest.fn(),
    updateCollection: jest.fn(),
  };
});

function goalDetail(overrides: Record<string, unknown> = {}) {
  return {
    collectionId: "goal-1",
    title: "LET Mastery",
    description: "Licensure goal",
    visibility: "PRIVATE",
    courseProgram: null,
    estimatedStudyHours: null,
    sourcePlanId: null,
    parentCollectionId: null,
    itemCount: 0,
    childCount: 2,
    overallReadinessPercentage: 45,
    masteredConcepts: 9,
    dueConcepts: 4,
    notPracticedConcepts: 7,
    totalConcepts: 20,
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-02T00:00:00Z",
    children: [
      goalChild("child-1", "Professional Education Mastery", 50),
      goalChild("child-2", "General Education Mastery", 40),
    ],
    ...overrides,
  };
}

function goalChild(collectionId: string, title: string, readiness: number) {
  return {
    collectionId,
    title,
    description: null,
    itemCount: 1,
    overallReadinessPercentage: readiness,
    masteredConcepts: readiness / 10,
    dueConcepts: 1,
    notPracticedConcepts: 1,
    totalConcepts: 10,
  };
}

function collectionDetail(
  id: string,
  title: string,
  items: NoteCollectionItem[] = [collectionItem(`${id}-note-1`, `${title} Foundations`, 0) as NoteCollectionItem],
  overrides: Record<string, unknown> = {},
) {
  return {
    id,
    title,
    description: null,
    visibility: "PRIVATE",
    courseProgram: null,
    sourcePlanId: null,
    parentCollectionId: "goal-1",
    childCount: 0,
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-02T00:00:00Z",
    progress: {
      totalNotes: items.length,
      notesWithStudyPack: 0,
      notesPracticed: 0,
    },
    items,
    ...overrides,
  };
}

function collectionItem(noteId: string, title: string, position: number): NoteCollectionItem {
  return {
    noteId,
    label: null,
    position,
    title,
    subject: "Education",
    courseProgram: "LET",
    studyPackStatus: "DRAFT",
    generatedQuizId: null,
    lastSessionCompletedAt: null,
    dueConceptCount: 0,
    dueConcepts: [],
    updatedAt: "2026-06-01T00:00:00Z",
  };
}

function note(id: string, title: string) {
  return {
    id,
    title,
    ownerUserId: "user-1",
    courseProgram: "LET",
    subject: "Education",
    tags: [],
    contentPreview: "",
    summaryPreview: "",
    visibility: "PRIVATE",
    studyPackId: null,
    studyPackStatus: "DRAFT",
    quizCount: null,
    copyCount: null,
    likeCount: null,
    shareCount: null,
    viewCount: null,
    authorDisplayName: "User",
    isOfficialAuthor: false,
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-01T00:00:00Z",
    likedByCurrentUser: false,
  };
}

function subjectBlock(title: string): HTMLElement {
  const input = screen.getByDisplayValue(title);
  const section = input.closest("section");
  if (!section) {
    throw new Error(`Could not find section for ${title}`);
  }
  return section;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}

describe("StudyPlanBuilderPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (addCollectionItems as jest.Mock).mockReset();
    (createCollection as jest.Mock).mockReset();
    (deleteCollection as jest.Mock).mockReset();
    (getCollection as jest.Mock).mockReset();
    (getCollectionGoal as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockReset();
    (removeCollectionItem as jest.Mock).mockReset();
    (reorderCollectionChildren as jest.Mock).mockReset();
    (setCollectionItemOrder as jest.Mock).mockReset();
    (setCollectionParent as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (updateCollection as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE" });
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "goal-1") {
        return Promise.resolve(collectionDetail("goal-1", "LET Mastery", [], {
          parentCollectionId: null,
          childCount: 2,
        }));
      }
      if (id === "child-1") {
        return Promise.resolve(collectionDetail("child-1", "Professional Education Mastery", [
          collectionItem("note-1", "Professional Foundations", 0),
        ]));
      }
      return Promise.resolve(collectionDetail("child-2", "General Education Mastery", [
        collectionItem("note-2", "General Foundations", 0),
      ]));
    });
    (listNotes as jest.Mock).mockResolvedValue([
      note("note-1", "Professional Foundations"),
      note("note-2", "General Foundations"),
      note("note-3", "Assessment Notes"),
    ]);
    (createCollection as jest.Mock).mockResolvedValue(collectionDetail("child-3", "Major Specialization Mastery", []));
    (deleteCollection as jest.Mock).mockResolvedValue(undefined);
    (addCollectionItems as jest.Mock).mockResolvedValue(collectionDetail("child-1", "Professional Education Mastery"));
    (removeCollectionItem as jest.Mock).mockResolvedValue(undefined);
    (reorderCollectionChildren as jest.Mock).mockResolvedValue(goalDetail());
    (setCollectionItemOrder as jest.Mock).mockResolvedValue(collectionDetail("child-1", "Professional Education Mastery"));
    (setCollectionParent as jest.Mock).mockResolvedValue(collectionDetail("child-3", "Major Specialization Mastery", []));
    (updateCollection as jest.Mock).mockResolvedValue(collectionDetail("child-1", "Professional Education Mastery"));
  });

  it("shows a distinct empty state for a subject plan with no notes, even while collapsed", async () => {
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "goal-1") {
        return Promise.resolve(collectionDetail("goal-1", "LET Mastery", [], {
          parentCollectionId: null,
          childCount: 2,
        }));
      }
      if (id === "child-1") {
        return Promise.resolve(collectionDetail("child-1", "Professional Education Mastery", [
          collectionItem("note-1", "Professional Foundations", 0),
        ]));
      }
      return Promise.resolve(collectionDetail("child-2", "General Education Mastery", []));
    });

    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.getByText("No notes yet")).toBeInTheDocument();
    expect(screen.queryByText("0% ready")).not.toBeInTheDocument();
  });

  it("renders the goal builder canvas with subject blocks and notes, collapsed by default", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("Professional Education Mastery")).toBeInTheDocument();
    expect(screen.getByDisplayValue("General Education Mastery")).toBeInTheDocument();
    expect(screen.queryByText("Professional Foundations")).not.toBeInTheDocument();
    expect(screen.queryByText("General Foundations")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Expand all" }));

    expect(screen.getByText("Professional Foundations")).toBeInTheDocument();
    expect(screen.getByText("General Foundations")).toBeInTheDocument();
  });

  it("renders the leaf builder canvas when the collection has no children", async () => {
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "leaf-1") {
        return Promise.resolve(collectionDetail("leaf-1", "Anatomy Plan", [
          { ...collectionItem("note-1", "Skeletal System", 0), label: "Week 1" },
          { ...collectionItem("note-2", "Muscle Groups", 1), label: null },
        ], { parentCollectionId: null, childCount: 0 }));
      }
      return Promise.resolve(collectionDetail(id, "Child", []));
    });

    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    expect(await screen.findByRole("heading", { name: "Anatomy Plan" })).toBeInTheDocument();
    expect(screen.getByLabelText("Section name Week 1")).toBeInTheDocument();
    expect(screen.getAllByText("Not in a section")[0]).toBeInTheDocument();
    expect(screen.queryByLabelText("Section name Not in a section")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Drag section Not in a section" })).not.toBeInTheDocument();
    expect(screen.getByText("Skeletal System")).toBeInTheDocument();
    expect(screen.getByText("Muscle Groups")).toBeInTheDocument();
    expect(getCollectionGoal).not.toHaveBeenCalled();
  });

  it("shows both leaf and goal-building actions for a brand-new empty collection", async () => {
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "leaf-1") {
        return Promise.resolve(collectionDetail("leaf-1", "Anatomy Plan", [], {
          parentCollectionId: null,
          childCount: 0,
        }));
      }
      return Promise.resolve(collectionDetail(id, "Child", []));
    });

    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    expect(await screen.findByText("No notes yet")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Add notes" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Add Subject Plan" })).toHaveLength(2);
  });

  it("adds the first subject plan from an empty leaf collection and transitions to the goal canvas without an error flash", async () => {
    const goalLoad = deferred<ReturnType<typeof goalDetail>>();
    let rootFetchCount = 0;
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "leaf-1") {
        rootFetchCount += 1;
        return Promise.resolve(collectionDetail("leaf-1", "Anatomy Plan", [], {
          parentCollectionId: null,
          childCount: rootFetchCount === 1 ? 0 : 1,
        }));
      }
      return Promise.resolve(collectionDetail("child-new", "Physiology Plan", []));
    });
    (createCollection as jest.Mock).mockResolvedValue(collectionDetail("child-new", "Physiology Plan", []));
    (setCollectionParent as jest.Mock).mockResolvedValue(collectionDetail("child-new", "Physiology Plan", []));
    (getCollectionGoal as jest.Mock).mockReturnValue(goalLoad.promise);

    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    const [addSubjectButton] = await screen.findAllByRole("button", { name: "Add Subject Plan" });
    fireEvent.click(addSubjectButton);
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Physiology Plan" } });
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Add Subject Plan" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({ title: "Physiology Plan", description: null });
      expect(setCollectionParent).toHaveBeenCalledWith("child-new", "leaf-1");
      expect(getCollectionGoal).toHaveBeenCalledWith("leaf-1");
    });
    expect(await screen.findByText("Loading builder...")).toBeInTheDocument();
    expect(screen.queryByText("Could not load builder")).not.toBeInTheDocument();

    goalLoad.resolve(goalDetail({
      collectionId: "leaf-1",
      title: "Anatomy Plan",
      childCount: 1,
      children: [goalChild("child-new", "Physiology Plan", 0)],
    }));

    expect(await screen.findByRole("heading", { name: "Anatomy Plan" })).toBeInTheDocument();
    expect(screen.getByText("Subject Plan canvas")).toBeInTheDocument();
    expect(screen.queryByText("Could not load builder")).not.toBeInTheDocument();
  });

  it("hides the subject-plan action once the collection has notes", async () => {
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "leaf-1") {
        return Promise.resolve(collectionDetail("leaf-1", "Anatomy Plan", [
          collectionItem("note-1", "Skeletal System", 0),
        ], { parentCollectionId: null, childCount: 0 }));
      }
      return Promise.resolve(collectionDetail(id, "Child", []));
    });

    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    expect(await screen.findByText("Skeletal System")).toBeInTheDocument();
    expect(screen.queryAllByRole("button", { name: "Add Subject Plan" })).toHaveLength(0);
  });

  it("hides the subject-plan action for an already-nested Subject plan with no notes, since it can never itself become a Goal", async () => {
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "child-1") {
        return Promise.resolve(collectionDetail("child-1", "Physiology Plan", [], {
          parentCollectionId: "goal-1",
          childCount: 0,
        }));
      }
      return Promise.resolve(collectionDetail(id, "Child", []));
    });

    render(<StudyPlanBuilderPageClient collectionId="child-1" />);

    expect(await screen.findByText("No notes yet")).toBeInTheDocument();
    expect(screen.getByText("Add your existing notes to get started.")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Add notes" })).toHaveLength(2);
    expect(screen.queryAllByRole("button", { name: "Add Subject Plan" })).toHaveLength(0);
  });

  it("persists leaf builder relabeling through the item order endpoint", async () => {
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "leaf-1") {
        return Promise.resolve(collectionDetail("leaf-1", "Anatomy Plan", [
          { ...collectionItem("note-1", "Skeletal System", 0), label: "Week 1" },
          { ...collectionItem("note-2", "Muscle Groups", 1), label: null },
        ], { parentCollectionId: null, childCount: 0 }));
      }
      return Promise.resolve(collectionDetail(id, "Child", []));
    });

    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    await screen.findByRole("heading", { name: "Anatomy Plan" });
    fireEvent.change(screen.getByLabelText("Section for Muscle Groups"), { target: { value: "Week 1" } });

    await waitFor(() => {
      expect(setCollectionItemOrder).toHaveBeenCalledWith("leaf-1", [
        { noteId: "note-1", label: "Week 1" },
        { noteId: "note-2", label: "Week 1" },
      ]);
    });
  });

  it("snaps a typed case variant to the existing section casing", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Skeletal System", 0), label: "Algebra" },
      { ...collectionItem("note-2", "Muscle Groups", 1), label: "Calculus" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    fireEvent.change(await screen.findByLabelText("Section for Muscle Groups"), { target: { value: "algebra" } });

    await waitFor(() => expect(setCollectionItemOrder).toHaveBeenCalledWith("leaf-1", [
      { noteId: "note-1", label: "Algebra" },
      { noteId: "note-2", label: "Algebra" },
    ]));
  });

  it.each(["Not in a section", "not in a section", "  NOT IN A SECTION  "])(
    "rejects the reserved section name %s before writing",
    async (reservedName) => {
      (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
        { ...collectionItem("note-1", "Skeletal System", 0), label: "Algebra" },
      ], { parentCollectionId: null, childCount: 0 }));
      render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

      const sectionInput = await screen.findByLabelText("Section name Algebra");
      fireEvent.change(sectionInput, { target: { value: reservedName } });
      fireEvent.blur(sectionInput);

      expect(await screen.findByText(/is reserved for notes without a section/i)).toBeInTheDocument();
      expect(sectionInput).toHaveValue("Algebra");
      expect(setCollectionItemOrder).not.toHaveBeenCalled();
    },
  );

  it("pins the unsectioned bucket last and suppresses it when it is the only group", async () => {
    (getCollection as jest.Mock).mockResolvedValueOnce(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Loose Note", 0), label: null },
      { ...collectionItem("note-2", "Grouped Note", 1), label: "Algebra" },
    ], { parentCollectionId: null, childCount: 0 }));
    const { unmount } = render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    const groupedHeading = await screen.findByLabelText("Section name Algebra");
    const unsectionedHeading = screen.getAllByText("Not in a section")
      .find((element) => element.classList.contains("text-lg"));
    expect(unsectionedHeading).toBeDefined();
    expect(groupedHeading.compareDocumentPosition(unsectionedHeading!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Drag section Not in a section" })).not.toBeInTheDocument();

    unmount();
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Loose Note", 0), label: null },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);
    await screen.findByText("Loose Note");
    expect(screen.queryByText("Not in a section")).not.toBeInTheDocument();
  });

  it("asks before combining a renamed section and cancellation writes nothing", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Skeletal System", 0), label: "Algebra" },
      { ...collectionItem("note-2", "Muscle Groups", 1), label: "Calculus" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    const sectionInput = await screen.findByLabelText("Section name Algebra");
    fireEvent.change(sectionInput, { target: { value: "calculus" } });
    fireEvent.blur(sectionInput);
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/two sections will be combined/i)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(setCollectionItemOrder).not.toHaveBeenCalled();
    expect(trackAnalyticsEvent).not.toHaveBeenCalled();
  });

  it("does not let stale card state revert a multi-note section rename after refresh", async () => {
    let fetchCount = 0;
    (getCollection as jest.Mock).mockImplementation(() => {
      fetchCount += 1;
      const label = fetchCount === 1 ? "Algebra" : "Foundations";
      return Promise.resolve(collectionDetail("leaf-1", "Anatomy Plan", [
        { ...collectionItem("note-1", "Skeletal System", 0), label },
        { ...collectionItem("note-2", "Muscle Groups", 1), label },
      ], { parentCollectionId: null, childCount: 0 }));
    });
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    const sectionInput = await screen.findByLabelText("Section name Algebra");
    fireEvent.change(sectionInput, { target: { value: "Foundations" } });
    fireEvent.blur(sectionInput);

    await waitFor(() => expect(screen.getByLabelText("Section name Foundations")).toBeInTheDocument());
    await new Promise((resolve) => globalThis.setTimeout(resolve, 600));
    expect(setCollectionItemOrder).toHaveBeenCalledTimes(1);
    expect(setCollectionItemOrder).not.toHaveBeenCalledWith("leaf-1", expect.arrayContaining([
      expect.objectContaining({ label: "Algebra" }),
    ]));
  });

  it("does not refetch every note the user owns just to reorder one plan", async () => {
    // ⚠️ listNotes() fetches the ENTIRE note list. A reorder adds, removes and edits no note, so
    // refetching it after each drop was the bulk of the unresponsiveness on a large plan.
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Skeletal System", 0), label: "Algebra" },
      { ...collectionItem("note-2", "Muscle Groups", 1), label: "Algebra" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    const moveDown = await screen.findByLabelText("Move Skeletal System down");
    const notesCallsBefore = (listNotes as jest.Mock).mock.calls.length;
    fireEvent.click(moveDown);

    await waitFor(() => expect(setCollectionItemOrder).toHaveBeenCalledTimes(1));
    expect((listNotes as jest.Mock).mock.calls.length).toBe(notesCallsBefore);
  });

  it("says a save is running, because nothing on this surface used to say so", async () => {
    // The curator could not tell a save was in flight, so they kept dragging into it. The drag
    // handles carry disabled:opacity-50, but a disabled 9px handle looks like an enabled one.
    let resolveOrder: (() => void) | undefined;
    (setCollectionItemOrder as jest.Mock).mockImplementation(() => new Promise<void>((resolve) => {
      resolveOrder = () => resolve();
    }));
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Skeletal System", 0), label: "Algebra" },
      { ...collectionItem("note-2", "Muscle Groups", 1), label: "Algebra" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    fireEvent.click(await screen.findByLabelText("Move Skeletal System down"));

    expect(await screen.findByText(/Saving/i)).toBeInTheDocument();
    expect(screen.queryByText(/to reorganize/i)).not.toBeInTheDocument();

    resolveOrder?.();
    await waitFor(() => expect(screen.getByText(/to reorganize/i)).toBeInTheDocument());
  });

  it("sets every section from note subjects after confirmation", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Skeletal System", 0), label: "Old", subject: "Biology" },
      { ...collectionItem("note-2", "Muscle Groups", 1), label: "Old", subject: "Anatomy" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Group by subject" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/all 2 notes/i)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole("button", { name: "Set sections" }));

    await waitFor(() => expect(setCollectionItemOrder).toHaveBeenCalledWith("leaf-1", [
      { noteId: "note-1", label: "Biology" },
      { noteId: "note-2", label: "Anatomy" },
    ]));
    expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({
      eventType: "COLLECTION_SECTION_ASSIGNED",
      metadata: { collectionId: "leaf-1", source: "set-from-subjects", noteCount: 2 },
    }));
  });

  it("folds a stored label matching the reserved bucket in any casing into the bucket", async () => {
    // Guards against data already stored by a path with no reserved-name check. With an exact-only
    // comparison this renders as a real section whose uppercase header is indistinguishable from
    // the synthetic bucket -- the collision the whole sentinel design exists to prevent.
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Plan", [
      { ...collectionItem("note-1", "Stored Lowercase", 0), label: "not in a section" },
      { ...collectionItem("note-2", "Real Section", 1), label: "Algebra" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    await screen.findByText("Stored Lowercase");
    // A real section is editable and draggable; the synthetic bucket is neither. If the stored
    // lowercase label had minted a section, both of these would exist.
    expect(screen.queryByLabelText("Section name not in a section")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Drag section not in a section$/i })).not.toBeInTheDocument();
    // The real section beside it still renders normally, so the fold is not over-broad.
    expect(screen.getByLabelText("Section name Algebra")).toBeInTheDocument();
  });

  it("snaps subject case and whitespace variants onto one section when grouping", async () => {
    // Group by subject must apply the SAME normalization and snap as the combobox. Without it,
    // "Cash  and Receivables" and "cash and receivables" become two sections that render
    // identically -- the defect the combobox snap exists to prevent.
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Accountancy Plan", [
      { ...collectionItem("note-1", "Petty Cash", 0), label: null, subject: "Cash  and Receivables" },
      { ...collectionItem("note-2", "Bank Recon", 1), label: null, subject: "cash and receivables" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Group by subject" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Set sections" }));

    await waitFor(() => expect(setCollectionItemOrder).toHaveBeenCalledWith("leaf-1", [
      { noteId: "note-1", label: "Cash and Receivables" },
      { noteId: "note-2", label: "Cash and Receivables" },
    ]));
  });

  it("routes a subject matching the reserved bucket to no section", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Plan", [
      { ...collectionItem("note-1", "Loose Note", 0), label: null, subject: "not in a section" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Group by subject" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Set sections" }));

    await waitFor(() => expect(setCollectionItemOrder).toHaveBeenCalledWith("leaf-1", [
      { noteId: "note-1", label: null },
    ]));
  });

  it("disables set-from-subjects when every subject is blank", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Loose Note", 0), subject: " " },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);
    expect(await screen.findByRole("button", { name: "Group by subject" })).toBeDisabled();
  });

  it("labels the bulk shortcut with its action and its source", async () => {
    // "Set sections" would name the generic capability and read as THE way sections get set,
    // when the general mechanism is the per-note combobox. Within the four-word label cap in
    // docs/ui-standards.md; the blast radius lives in the confirmation dialog.
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Skeletal System", 0), subject: "Biology" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    const trigger = await screen.findByRole("button", { name: "Group by subject" });
    expect(trigger).toHaveTextContent(/^Group by subject$/);
  });

  it("starts sections expanded and collapses them at desktop widths", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collectionDetail("leaf-1", "Anatomy Plan", [
      { ...collectionItem("note-1", "Skeletal System", 0), label: "Algebra" },
    ], { parentCollectionId: null, childCount: 0 }));
    render(<StudyPlanBuilderPageClient collectionId="leaf-1" />);

    expect(await screen.findByText("Skeletal System")).toBeInTheDocument();
    const collapseButton = screen.getByRole("button", { name: "Collapse section Algebra" });
    expect(collapseButton).not.toHaveClass("sm:hidden");
    fireEvent.click(collapseButton);
    expect(screen.getByRole("button", { name: "Expand section Algebra" })).toBeInTheDocument();
    expect(screen.getByText("Skeletal System").closest("div.space-y-3")?.parentElement).toHaveClass("hidden");
  });

  it("shows an empty-goal state with add subject", async () => {
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({ childCount: 0, children: [] }));

    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    expect(await screen.findByText("No Subject Plans yet")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /Add Subject/ }).length).toBeGreaterThan(0);
  });

  it("adds a subject by creating a collection and nesting it under the goal", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    fireEvent.click(await screen.findByRole("button", { name: /Add Subject/ }));
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Major Specialization Mastery" } });
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: /Add Subject/ }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({ title: "Major Specialization Mastery", description: null });
      expect(setCollectionParent).toHaveBeenCalledWith("child-3", "goal-1");
    });
  });

  it("adds a subject with an optional description threaded through to createCollection", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    fireEvent.click(await screen.findByRole("button", { name: /Add Subject/ }));
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Major Specialization Mastery" } });
    fireEvent.change(screen.getByLabelText("Description"), { target: { value: "  Covers advanced electives.  " } });
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: /Add Subject/ }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({
        title: "Major Specialization Mastery",
        description: "Covers advanced electives.",
      });
    });
  });

  it("renames a subject with the metadata endpoint", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    const titleInput = await screen.findByDisplayValue("Professional Education Mastery");
    fireEvent.change(titleInput, { target: { value: "Professional Education" } });
    fireEvent.blur(titleInput);

    await waitFor(() => {
      expect(updateCollection).toHaveBeenCalledWith("child-1", { title: "Professional Education" });
    });
  });

  it("deletes a subject without deleting notes", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    const block = await waitFor(() => subjectBlock("Professional Education Mastery"));
    fireEvent.click(within(block).getByRole("button", { name: "Delete" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(deleteCollection).toHaveBeenCalledWith("child-1");
      expect(removeCollectionItem).not.toHaveBeenCalled();
    });
  });

  it("reorders subjects through the child-order endpoint", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    const block = await waitFor(() => subjectBlock("Professional Education Mastery"));
    fireEvent.click(within(block).getByRole("button", { name: "Move Professional Education Mastery down" }));

    await waitFor(() => {
      expect(reorderCollectionChildren).toHaveBeenCalledWith("goal-1", ["child-2", "child-1"]);
    });
  });

  it("adds notes into one subject through the existing add-items endpoint", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    const block = await waitFor(() => subjectBlock("Professional Education Mastery"));
    fireEvent.click(within(block).getByRole("button", { name: "Add notes" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Assessment Notes")).toBeInTheDocument();
    expect(within(dialog).queryByText("Professional Foundations")).not.toBeInTheDocument();
    fireEvent.click(within(dialog).getByText("Assessment Notes"));
    fireEvent.click(within(dialog).getByRole("button", { name: /Add selected/ }));

    await waitFor(() => {
      expect(addCollectionItems).toHaveBeenCalledWith("child-1", ["note-3"]);
    });
  });

  it("refreshes the note list from within the Add-notes modal without a standalone header Refresh button", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    const block = await waitFor(() => subjectBlock("Professional Education Mastery"));
    expect(screen.queryByRole("button", { name: "Refresh" })).not.toBeInTheDocument();

    fireEvent.click(within(block).getByRole("button", { name: "Add notes" }));
    const dialog = await screen.findByRole("dialog");
    (listNotes as jest.Mock).mockClear();

    fireEvent.click(within(dialog).getByRole("button", { name: "Refresh notes" }));

    await waitFor(() => {
      expect(listNotes).toHaveBeenCalledTimes(1);
    });
  });

  it("moves a note across subjects with remove plus add and then saves target order", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    await screen.findByRole("heading", { name: "LET Mastery" });
    fireEvent.click(screen.getByRole("button", { name: "Expand all" }));
    await screen.findByText("Professional Foundations");
    fireEvent.change(screen.getByLabelText("Move Professional Foundations to subject"), {
      target: { value: "child-2" },
    });

    await waitFor(() => {
      expect(removeCollectionItem).toHaveBeenCalledWith("child-1", "note-1");
      expect(addCollectionItems).toHaveBeenCalledWith("child-2", ["note-1"]);
      expect(setCollectionItemOrder).toHaveBeenCalledWith("child-2", [
        { noteId: "note-2", label: null },
        { noteId: "note-1", label: null },
      ]);
    });
  });

  it("removes a note from a subject through the existing remove endpoint", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    await screen.findByRole("heading", { name: "LET Mastery" });
    fireEvent.click(screen.getByRole("button", { name: "Expand all" }));
    const block = await waitFor(() => subjectBlock("Professional Education Mastery"));
    fireEvent.click(within(block).getByRole("button", { name: /^Remove/ }));

    await waitFor(() => {
      expect(removeCollectionItem).toHaveBeenCalledWith("child-1", "note-1");
    });
  });

  it("surfaces move failures and refetches authoritative state", async () => {
    (addCollectionItems as jest.Mock).mockRejectedValueOnce(new Error("Target add failed"));

    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    await screen.findByRole("heading", { name: "LET Mastery" });
    fireEvent.click(screen.getByRole("button", { name: "Expand all" }));
    await screen.findByText("Professional Foundations");
    fireEvent.change(screen.getByLabelText("Move Professional Foundations to subject"), {
      target: { value: "child-2" },
    });

    expect(await screen.findByText("Target add failed")).toBeInTheDocument();
    await waitFor(() => {
      expect(getCollectionGoal).toHaveBeenCalledTimes(2);
    });
  });
});
