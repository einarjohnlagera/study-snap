import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CollectionDetailPageClient } from "./collection-detail-page-client";
import {
  addCollectionItems,
  ApiRequestError,
  getCollection,
  listNotes,
  removeCollectionItem,
  setCollectionItemOrder,
} from "@/lib/api";

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
  getAuthUser: () => ({ profileType: "STUDENT" }),
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
    listNotes: jest.fn(),
    removeCollectionItem: jest.fn(),
    setCollectionItemOrder: jest.fn(),
    updateCollection: jest.fn(),
  };
});

function collection(overrides: Record<string, unknown> = {}) {
  return {
    id: "collection-1",
    title: "Midterm Study Plan",
    description: "Weeks 1-4",
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-02T00:00:00Z",
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

  it("reorders by move button using the full ordered set", async () => {
    const reordered = collection({
      items: [
        { ...collection().items[1], position: 0 },
        { ...collection().items[0], position: 1 },
      ],
    });
    (setCollectionItemOrder as jest.Mock).mockResolvedValue(reordered);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByText("Cell Respiration");
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

    await screen.findByText("Cell Respiration");
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
