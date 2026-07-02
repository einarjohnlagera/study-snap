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
    targetProfileType: "STUDENT",
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

  it("renders the goal builder canvas with subject blocks and notes", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("Professional Education Mastery")).toBeInTheDocument();
    expect(screen.getByDisplayValue("General Education Mastery")).toBeInTheDocument();
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
    expect(screen.getByDisplayValue("Week 1")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Ungrouped")).toBeInTheDocument();
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
    expect(screen.getAllByRole("button", { name: "Add notes" }).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Add subject plan" })).toBeInTheDocument();
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

    fireEvent.click(await screen.findByRole("button", { name: "Add subject plan" }));
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Physiology Plan" } });
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Add subject plan" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({ title: "Physiology Plan" });
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
    expect(screen.queryByRole("button", { name: "Add subject plan" })).not.toBeInTheDocument();
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
    fireEvent.change(screen.getByLabelText("Move Muscle Groups to section"), { target: { value: "Week 1" } });

    await waitFor(() => {
      expect(setCollectionItemOrder).toHaveBeenCalledWith("leaf-1", [
        { noteId: "note-1", label: "Week 1" },
        { noteId: "note-2", label: "Week 1" },
      ]);
    });
  });

  it("shows an empty-goal state with add subject", async () => {
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({ childCount: 0, children: [] }));

    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    expect(await screen.findByText("No subject plans yet")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /Add subject/ }).length).toBeGreaterThan(0);
  });

  it("adds a subject by creating a collection and nesting it under the goal", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

    fireEvent.click(await screen.findByRole("button", { name: /Add subject/ }));
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Major Specialization Mastery" } });
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: /Add subject/ }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({ title: "Major Specialization Mastery" });
      expect(setCollectionParent).toHaveBeenCalledWith("child-3", "goal-1");
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
    fireEvent.click(within(block).getByRole("button", { name: "Move down" }));

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

  it("moves a note across subjects with remove plus add and then saves target order", async () => {
    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

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

    const block = await waitFor(() => subjectBlock("Professional Education Mastery"));
    fireEvent.click(within(block).getByRole("button", { name: /^Remove/ }));

    await waitFor(() => {
      expect(removeCollectionItem).toHaveBeenCalledWith("child-1", "note-1");
    });
  });

  it("surfaces move failures and refetches authoritative state", async () => {
    (addCollectionItems as jest.Mock).mockRejectedValueOnce(new Error("Target add failed"));

    render(<StudyPlanBuilderPageClient collectionId="goal-1" />);

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
