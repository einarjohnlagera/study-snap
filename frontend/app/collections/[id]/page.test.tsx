import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CollectionDetailPageClient } from "./collection-detail-page-client";
import {
  addCollectionItems,
  ApiRequestError,
  getCollection,
  listCoursePrograms,
  listNotes,
  removeCollectionItem,
  setCollectionItemOrder,
  updateCollection,
  updateCollectionVisibility,
  updateNoteVisibility,
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
    deleteCollection: jest.fn(),
    getCollection: jest.fn(),
    listCoursePrograms: jest.fn(),
    listNotes: jest.fn(),
    removeCollectionItem: jest.fn(),
    setCollectionItemOrder: jest.fn(),
    updateCollection: jest.fn(),
    updateCollectionVisibility: jest.fn(),
    updateNoteVisibility: jest.fn(),
  };
});

function collection(overrides: Record<string, unknown> = {}) {
  return {
    id: "collection-1",
    title: "Midterm Study Plan",
    description: "Weeks 1-4",
    visibility: "PRIVATE",
    courseProgram: null,
    sourcePlanId: null,
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-02T00:00:00Z",
    progress: {
      totalNotes: 2,
      notesWithStudyPack: 1,
      notesPracticed: 1,
    },
    items: [
      {
        noteId: "note-1",
        label: "Week 1",
        position: 0,
        title: "Cell Respiration",
        subject: "Biology",
        courseProgram: "Nursing",
        studyPackStatus: "DRAFT",
        generatedQuizId: null,
        lastSessionCompletedAt: null,
        dueConceptCount: 0,
        dueConcepts: [],
        updatedAt: "2026-06-01T00:00:00Z",
      },
      {
        noteId: "note-2",
        label: null,
        position: 1,
        title: "Dosage Calculations",
        subject: "Pharmacy",
        courseProgram: "Nursing",
        studyPackStatus: "STUDY_PACK_READY",
        generatedQuizId: "quiz-2",
        lastSessionCompletedAt: "2026-06-02T00:00:00Z",
        dueConceptCount: 0,
        dueConcepts: [],
        updatedAt: "2026-06-01T00:00:00Z",
      },
    ],
    ...overrides,
  };
}

function note(id: string, title: string) {
  return {
    id,
    title,
    ownerUserId: "user-1",
    courseProgram: null,
    targetProfileType: "STUDENT",
    subject: null,
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

describe("CollectionDetailPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (addCollectionItems as jest.Mock).mockReset();
    (getCollection as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockReset();
    (removeCollectionItem as jest.Mock).mockReset();
    (setCollectionItemOrder as jest.Mock).mockReset();
    (updateCollection as jest.Mock).mockReset();
    (updateCollectionVisibility as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockReset();
    (updateNoteVisibility as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockResolvedValue([]);
    (listNotes as jest.Mock).mockResolvedValue([]);
    (updateNoteVisibility as jest.Mock).mockResolvedValue(undefined);
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE" });
    (getCollection as jest.Mock).mockResolvedValue(collection());
  });

  it("renders collection items in persisted order", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], position: 1 },
        { ...collection().items[1], position: 0 },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    const headings = screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent);
    expect(headings).toEqual(["Dosage Calculations", "Cell Respiration"]);
    expect(screen.getByRole("link", { name: "Study Plans" })).toHaveAttribute("href", "/collections");
  });

  it("renders the collection progress rollup", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("1 of 2 Study Packs ready · 1 of 2 practiced")).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Notes practiced" })).toHaveAttribute("aria-valuenow", "1");
    expect(screen.getByRole("progressbar", { name: "Notes practiced" })).toHaveAttribute("aria-valuemax", "2");
  });

  it("renders neutral progress for an empty collection without an invalid percentage", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      progress: {
        totalNotes: 0,
        notesWithStudyPack: 0,
        notesPracticed: 0,
      },
      items: [],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("No progress yet")).toBeInTheDocument();
    expect(screen.getByText("Add notes to track Study Pack readiness and practice.")).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Notes practiced" })).toHaveAttribute("aria-valuenow", "0");
    expect(screen.queryByText("Next in this plan")).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain("NaN");
  });

  it("shows due concepts for entitled users and omits rows without due concepts", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "PLUS" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        {
          ...collection().items[0],
          studyPackStatus: "STUDY_PACK_READY",
          dueConceptCount: 2,
          dueConcepts: ["Cell membrane", "ATP synthesis"],
        },
        {
          ...collection().items[1],
          dueConceptCount: 0,
          dueConcepts: [],
        },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("2 concepts due")).toBeInTheDocument();
    expect(screen.getByText("Cell membrane · ATP synthesis")).toBeInTheDocument();
    expect(screen.queryByText("0 concepts due")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Upgrade to Plus" })).not.toBeInTheDocument();
  });

  it("shows one shared upgrade affordance for Free users without exposing due counts", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item, index) => ({
        ...item,
        dueConceptCount: index === 0 ? 2 : 0,
        dueConcepts: index === 0 ? ["Hidden concept", "Another hidden concept"] : [],
      })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const upgradeLink = await screen.findByRole("link", { name: "Upgrade to Plus" });
    expect(upgradeLink).toHaveAttribute("href", "/settings?section=plans");
    expect(screen.queryByText("2 concepts due")).not.toBeInTheDocument();
    expect(screen.queryByText("Hidden concept · Another hidden concept")).not.toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Upgrade to Plus" })).toHaveLength(1);
  });

  it("chooses the first note without a Study Pack in saved order", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const nextLink = await screen.findByRole("link", { name: "Generate Study Pack" });
    expect(screen.getByText("Next in this plan")).toBeInTheDocument();
    expect(nextLink).toHaveAttribute("href", "/notes/note-1?ref=%2Fcollections%2Fcollection-1");
  });

  it("chooses the first unpracticed note after every Study Pack is ready", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        {
          ...collection().items[0],
          studyPackStatus: "STUDY_PACK_READY",
          lastSessionCompletedAt: "2026-06-03T00:00:00Z",
        },
        {
          ...collection().items[1],
          lastSessionCompletedAt: null,
        },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const nextLink = await screen.findByRole("link", { name: "Study this note" });
    expect(nextLink).toHaveAttribute("href", "/notes/note-2?ref=%2Fcollections%2Fcollection-1");
  });

  it("chooses the first due-concept note for entitled users after all notes are practiced", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "PRO" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        {
          ...collection().items[0],
          studyPackStatus: "STUDY_PACK_READY",
          lastSessionCompletedAt: "2026-06-03T00:00:00Z",
          dueConceptCount: 0,
        },
        {
          ...collection().items[1],
          dueConceptCount: 2,
          dueConcepts: ["Dosage ratios", "Unit conversion"],
        },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const nextLink = await screen.findByRole("link", { name: "Review due concepts" });
    expect(nextLink).toHaveAttribute("href", "/notes/note-2?ref=%2Fcollections%2Fcollection-1");
  });

  it("shows caught up when no action remains", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "PLUS" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item) => ({
        ...item,
        studyPackStatus: "STUDY_PACK_READY",
        lastSessionCompletedAt: "2026-06-03T00:00:00Z",
        dueConceptCount: 0,
        dueConcepts: [],
      })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("All caught up in this plan")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Study this note|Review due concepts|Generate Study Pack/ })).not.toBeInTheDocument();
  });

  it("never uses due concepts as the next action for Free users", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item, index) => ({
        ...item,
        studyPackStatus: "STUDY_PACK_READY",
        lastSessionCompletedAt: "2026-06-03T00:00:00Z",
        dueConceptCount: index === 0 ? 2 : 0,
        dueConcepts: index === 0 ? ["Hidden concept", "Another hidden concept"] : [],
      })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("All caught up in this plan")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Review due concepts" })).not.toBeInTheDocument();
  });

  it("routes teacher collections to Exam Builder with the collection id and quiz-ready note ids", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "TEACHER", planType: "FREE" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const buildExamButton = await screen.findByRole("button", { name: "Build exam from this Lesson Plan" });
    expect(buildExamButton).toBeEnabled();
    expect(screen.getByText("Only quiz-ready notes will be included.")).toBeInTheDocument();

    fireEvent.click(buildExamButton);

    expect(pushMock).toHaveBeenCalledWith(
      "/library/exam-builder?collectionId=collection-1&notes=note-2",
    );
  });

  it("disables the teacher terminal action when no collection notes are quiz-ready", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "TEACHER", planType: "FREE" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item) => ({
        ...item,
        studyPackStatus: "DRAFT",
        generatedQuizId: null,
      })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const buildExamButton = await screen.findByRole("button", { name: "Build exam from this Lesson Plan" });
    expect(buildExamButton).toBeDisabled();
    expect(screen.getByText("Generate a quiz for at least one note to build an exam.")).toBeInTheDocument();

    fireEvent.click(buildExamButton);

    expect(pushMock).not.toHaveBeenCalled();
  });

  it("does not render a terminal CTA for non-teacher profiles", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    expect(screen.queryByRole("button", { name: /Build exam from this/i })).not.toBeInTheDocument();
  });

  it("hides admin publish action for non-admins", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));

    expect(screen.queryByRole("menuitem", { name: /Publish settings/ })).not.toBeInTheDocument();
  });

  it("publishes a study plan from the admin publish modal", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({ courseProgram: "LET" }));
    (updateCollectionVisibility as jest.Mock).mockResolvedValue(collection({ courseProgram: "LET", visibility: "PUBLIC" }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Publish settings/ }));

    fireEvent.click(await screen.findByRole("button", { name: "Publish" }));

    await waitFor(() => {
      expect(updateCollectionVisibility).toHaveBeenCalledWith("collection-1", "PUBLIC");
    });
  });

  it("makes private plan notes public from the publish modal", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({ courseProgram: "LET" }));
    (listNotes as jest.Mock).mockResolvedValue([
      { ...note("note-1", "Cell Respiration"), visibility: "PRIVATE" },
      { ...note("note-2", "Dosage Calculations"), visibility: "PUBLIC" },
    ]);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Publish settings/ }));

    const makePublicButton = await screen.findByRole("button", { name: "Make 1 public" });
    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();

    fireEvent.click(makePublicButton);

    await waitFor(() => {
      expect(updateNoteVisibility).toHaveBeenCalledWith("note-1", "PUBLIC");
    });
    expect(updateNoteVisibility).not.toHaveBeenCalledWith("note-2", "PUBLIC");
  });

  it("reorders by move button using the full ordered set", async () => {
    const reordered = collection({
      items: [
        { ...collection().items[1], position: 0 },
        { ...collection().items[0], position: 1 },
      ],
    });
    (setCollectionItemOrder as jest.Mock).mockResolvedValue(reordered);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { level: 2, name: "Cell Respiration" });
    fireEvent.click(screen.getAllByRole("button", { name: "Move down" })[0]);

    await waitFor(() => {
      expect(setCollectionItemOrder).toHaveBeenCalledWith("collection-1", [
        { noteId: "note-2", label: null },
        { noteId: "note-1", label: "Week 1" },
      ]);
    });
  });

  it("removes a collection item", async () => {
    (removeCollectionItem as jest.Mock).mockResolvedValue(undefined);
    (getCollection as jest.Mock)
      .mockResolvedValueOnce(collection())
      .mockResolvedValueOnce(collection({ items: [collection().items[1]] }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { level: 2, name: "Cell Respiration" });
    fireEvent.click(screen.getAllByRole("button", { name: "Remove" })[0]);

    await waitFor(() => {
      expect(removeCollectionItem).toHaveBeenCalledWith("collection-1", "note-1");
    });
  });

  it("adds notes from the picker and excludes notes already present", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      note("note-1", "Hidden Existing Note"),
      note("note-3", "Chemistry Notes"),
    ]);
    (addCollectionItems as jest.Mock).mockResolvedValue(collection({
      items: [...collection().items, {
        noteId: "note-3",
        label: null,
        position: 2,
        title: "Chemistry Notes",
        subject: null,
        courseProgram: null,
        studyPackStatus: "DRAFT",
        generatedQuizId: null,
        lastSessionCompletedAt: null,
        dueConceptCount: 0,
        dueConcepts: [],
        updatedAt: "2026-06-01T00:00:00Z",
      }],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Add notes" }));
    expect(await screen.findByText("Chemistry Notes")).toBeInTheDocument();
    expect(screen.queryByText("Hidden Existing Note")).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("Chemistry Notes"));
    fireEvent.click(screen.getByRole("button", { name: "Add selected" }));

    await waitFor(() => {
      expect(addCollectionItems).toHaveBeenCalledWith("collection-1", ["note-3"]);
    });
  });

  it("renders not-found state for 404", async () => {
    (getCollection as jest.Mock).mockRejectedValue(new ApiRequestError("Not found", { status: 404 }));

    render(<CollectionDetailPageClient collectionId="missing" />);

    expect(await screen.findByRole("heading", { name: "Study Plan not found" })).toBeInTheDocument();
    screen.getAllByRole("link", { name: "Study Plans" }).forEach((link) => {
      expect(link).toHaveAttribute("href", "/collections");
    });
  });
});
