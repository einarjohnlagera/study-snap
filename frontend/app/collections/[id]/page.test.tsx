import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CollectionDetailPageClient } from "./collection-detail-page-client";
import {
  addCollectionItems,
  ApiRequestError,
  getCollection,
  getCollectionGoal,
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
    getCollectionGoal: jest.fn(),
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
    parentCollectionId: null,
    childCount: 0,
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

function goalDetail(overrides: Record<string, unknown> = {}) {
  return {
    collectionId: "collection-1",
    title: "LET Mastery",
    description: "Full LET goal",
    visibility: "PRIVATE",
    courseProgram: null,
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
      {
        collectionId: "child-1",
        title: "Professional Education Mastery",
        description: "Teaching foundations",
        itemCount: 4,
        overallReadinessPercentage: 50,
        masteredConcepts: 5,
        dueConcepts: 2,
        notPracticedConcepts: 3,
        totalConcepts: 10,
      },
      {
        collectionId: "child-2",
        title: "General Education Mastery",
        description: null,
        itemCount: 3,
        overallReadinessPercentage: 40,
        masteredConcepts: 4,
        dueConcepts: 2,
        notPracticedConcepts: 4,
        totalConcepts: 10,
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
    (getCollectionGoal as jest.Mock).mockReset();
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
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());
    Object.defineProperty(globalThis.window, "innerWidth", {
      configurable: true,
      value: 1024,
    });
  });

  it("renders collection items in persisted order", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], label: null, position: 1 },
        { ...collection().items[1], label: null, position: 0 },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    const headings = screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent);
    expect(headings).toEqual(["Dosage Calculations", "Cell Respiration"]);
    expect(screen.getByRole("link", { name: "Study Plans" })).toHaveAttribute("href", "/collections");
    expect(screen.getByRole("link", { name: "Check readiness" })).toHaveAttribute("href", "/collections/collection-1/readiness");
  });

  it("renders a goal view when the collection has children", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      title: "LET Mastery",
      description: "Full LET goal",
      childCount: 2,
      progress: {
        totalNotes: 0,
        notesWithStudyPack: 0,
        notesPracticed: 0,
      },
      items: [],
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.getByText("45% ready · 9/20 mastered · 4 due")).toBeInTheDocument();
    expect(screen.getByText("Professional Education Mastery").closest("a")).toHaveAttribute("href", "/collections/child-1");
    expect(screen.getByText("General Education Mastery").closest("a")).toHaveAttribute("href", "/collections/child-2");
    expect(screen.queryByRole("heading", { name: "Notes" })).not.toBeInTheDocument();
  });

  it("links an empty top-level plan to the goal builder instead of the old nest menu", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      progress: {
        totalNotes: 0,
        notesWithStudyPack: 0,
        notesPracticed: 0,
      },
      items: [],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });
    expect(screen.getByRole("link", { name: "Build goal" })).toHaveAttribute("href", "/collections/collection-1/builder");
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    expect(screen.queryByRole("menuitem", { name: /Nest under|Unnest/ })).not.toBeInTheDocument();
  });

  it("renders labeled items under section headers ordered by first item position", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], noteId: "note-1", title: "Foundations", label: "  General Education  ", position: 0 },
        { ...collection().items[1], noteId: "note-2", title: "Assessment", label: "Professional Education", position: 2 },
        { ...collection().items[0], noteId: "note-3", title: "Teaching Methods", label: "Professional Education", position: 1 },
      ],
      progress: {
        totalNotes: 3,
        notesWithStudyPack: 1,
        notesPracticed: 1,
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const notesCard = await screen.findByText("3 notes in saved order.");
    const notesRegion = notesCard.closest("div")?.parentElement?.parentElement;
    expect(notesRegion).not.toBeNull();
    const regionText = notesRegion?.textContent ?? "";
    expect(regionText.indexOf("General Education")).toBeLessThan(regionText.indexOf("Professional Education"));
    expect(regionText.indexOf("Teaching Methods")).toBeLessThan(regionText.indexOf("Assessment"));

    expect(screen.getByRole("button", { name: /General Education.*1 note/ })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("button", { name: /Professional Education.*2 notes/ })).toHaveAttribute("aria-expanded", "true");
  });

  it("renders null and empty labels in an Ungrouped section after named sections", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], noteId: "note-1", title: "Early Ungrouped", label: " ", position: 0 },
        { ...collection().items[1], noteId: "note-2", title: "Named Module", label: "Major Specialization", position: 1 },
        { ...collection().items[0], noteId: "note-3", title: "Null Ungrouped", label: null, position: 2 },
      ],
      progress: {
        totalNotes: 3,
        notesWithStudyPack: 1,
        notesPracticed: 1,
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const namedHeader = await screen.findByRole("button", { name: /Major Specialization.*1 note/ });
    const ungroupedHeader = screen.getByRole("button", { name: "Ungrouped 2 notes" });
    expect(Boolean(namedHeader.compareDocumentPosition(ungroupedHeader) & Node.DOCUMENT_POSITION_FOLLOWING)).toBe(true);
    expect(screen.getByRole("heading", { level: 2, name: "Early Ungrouped" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Null Ungrouped" })).toBeInTheDocument();
  });

  it("renders the flat list without section headers when no item has a label", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item) => ({ ...item, label: null })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { level: 2, name: "Cell Respiration" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Dosage Calculations" })).toBeInTheDocument();
    expect(screen.queryAllByTestId("collection-section-heading")).toHaveLength(0);
    expect(screen.queryByRole("button", { name: /Ungrouped/ })).not.toBeInTheDocument();
  });

  it("hides organize controls by default and reveals them from the Notes header", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { level: 2, name: "Cell Respiration" });
    expect(screen.getByRole("button", { name: "Organize" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Drag Cell Respiration")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Section")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Move up" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Move down" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Organize" }));

    expect(screen.getByRole("button", { name: "Done organizing" })).toBeInTheDocument();
    expect(screen.getByLabelText("Drag Cell Respiration")).toBeInTheDocument();
    expect(screen.getAllByLabelText("Section")).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Move up" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Move down" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Remove" })).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "Done organizing" }));

    expect(screen.queryByLabelText("Drag Cell Respiration")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Section")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove" })).not.toBeInTheDocument();
  });

  it("collapses section cards independently and removes collapsed rows from the DOM", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], noteId: "note-1", title: "Foundations", label: "General Education", position: 0 },
        { ...collection().items[1], noteId: "note-2", title: "Assessment", label: "Professional Education", position: 1 },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const generalSection = await screen.findByRole("button", { name: /General Education.*1 note/ });
    expect(screen.getByRole("heading", { level: 2, name: "Foundations" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Assessment" })).toBeInTheDocument();

    fireEvent.click(generalSection);

    expect(generalSection).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("heading", { level: 2, name: "Foundations" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Assessment" })).toBeInTheDocument();
  });

  it("starts section cards collapsed on mobile-sized viewports", async () => {
    Object.defineProperty(globalThis.window, "innerWidth", {
      configurable: true,
      value: 390,
    });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], noteId: "note-1", title: "Foundations", label: "General Education", position: 0 },
        { ...collection().items[1], noteId: "note-2", title: "Assessment", label: "Professional Education", position: 1 },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const generalSection = await screen.findByRole("button", { name: /General Education.*1 note/ });
    expect(generalSection).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("heading", { level: 2, name: "Foundations" })).not.toBeInTheDocument();

    fireEvent.click(generalSection);

    expect(generalSection).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("heading", { level: 2, name: "Foundations" })).toBeInTheDocument();
  });

  it("shows three-state per-note execution status and drops the study-pack/quiz readiness hint", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], noteId: "note-1", title: "Needs Pack Note", position: 0, studyPackStatus: "DRAFT", lastSessionCompletedAt: null },
        { ...collection().items[1], noteId: "note-2", title: "Ready Unpracticed Note", position: 1, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-2", lastSessionCompletedAt: null },
        { ...collection().items[1], noteId: "note-3", title: "Practiced Note", position: 2, studyPackStatus: "STUDY_PACK_READY", lastSessionCompletedAt: "2026-06-02T00:00:00Z" },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Needs Study Pack")).toBeInTheDocument();
    expect(screen.getByText("Not started")).toBeInTheDocument();
    expect(screen.getByText("Practiced")).toBeInTheDocument();
    expect(screen.queryByText("Study Pack ready")).not.toBeInTheDocument();
    expect(screen.queryByText("Quiz ready")).not.toBeInTheDocument();
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

    const buildExamButton = await screen.findByRole("button", { name: "Build Exam" });
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

    const buildExamButton = await screen.findByRole("button", { name: "Build Exam" });
    expect(buildExamButton).toBeDisabled();
    expect(screen.getByText("Generate a quiz for at least one note to build an exam.")).toBeInTheDocument();

    fireEvent.click(buildExamButton);

    expect(pushMock).not.toHaveBeenCalled();
  });

  it("routes student collections to the Long Exam prescreen with the collection anchor", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const examButton = await screen.findByRole("button", { name: "Take the Long Exam" });
    expect(examButton).toBeEnabled();
    expect(screen.getByText("Only Study Pack-ready notes will be included.")).toBeInTheDocument();

    fireEvent.click(examButton);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-2/long-exam?collectionId=collection-1");
  });

  it("enables the premium exam CTA for Study Pack-ready notes that have no generated quiz", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item) => ({
        ...item,
        studyPackStatus: "STUDY_PACK_READY",
        generatedQuizId: null,
        lastSessionCompletedAt: "2026-06-02T00:00:00Z",
      })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const examButton = await screen.findByRole("button", { name: "Take the Long Exam" });
    expect(examButton).toBeEnabled();

    fireEvent.click(examButton);

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/long-exam?collectionId=collection-1");
  });

  it("advises review first when a plan note has not been practiced, then proceeds on confirm", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item) => ({
        ...item,
        studyPackStatus: "STUDY_PACK_READY",
        lastSessionCompletedAt: null,
      })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Take the Long Exam" }));

    // Modal intercepts the launch; CTA does not route yet.
    expect(await screen.findByText("Review before the exam?")).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Start the exam anyway" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/long-exam?collectionId=collection-1");
  });

  it("does not advise review when every plan note has been practiced", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item) => ({
        ...item,
        studyPackStatus: "STUDY_PACK_READY",
        lastSessionCompletedAt: "2026-06-02T00:00:00Z",
      })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Take the Long Exam" }));

    expect(screen.queryByText("Review before the exam?")).not.toBeInTheDocument();
    expect(pushMock).toHaveBeenCalledWith("/notes/note-1/long-exam?collectionId=collection-1");
  });

  it("routes professional collections to Interview Practice with the collection anchor", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "PROFESSIONAL", planType: "PLUS" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Interview Practice" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/note-2/interview-practice?collectionId=collection-1");
  });

  it("routes board exam collections through the primary note studyPackId resolved from listNotes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM", planType: "FREE" });
    (listNotes as jest.Mock).mockResolvedValue([
      { ...note("note-2", "Dosage Calculations"), studyPackId: "sp-2", studyPackStatus: "STUDY_PACK_READY" },
    ]);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Take the Board Exam" }));

    expect(pushMock).toHaveBeenCalledWith("/study-packs/sp-2/challenge-quiz?collectionId=collection-1");
  });

  it("disables the board exam collection CTA when the primary studyPackId cannot be resolved", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM", planType: "FREE" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const examButton = await screen.findByRole("button", { name: "Take the Board Exam" });
    expect(examButton).toBeDisabled();

    fireEvent.click(examButton);

    expect(pushMock).not.toHaveBeenCalled();
  });

  it("disables the premium exam CTA when no collection notes are Study Pack-ready", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item) => ({
        ...item,
        studyPackStatus: "DRAFT",
        generatedQuizId: null,
      })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const examButton = await screen.findByRole("button", { name: "Take the Long Exam" });
    expect(examButton).toBeDisabled();
    expect(screen.getByText("Generate a Study Pack for at least one note to start an exam.")).toBeInTheDocument();

    fireEvent.click(examButton);

    expect(pushMock).not.toHaveBeenCalled();
  });

  it("does not render a terminal CTA for parent profiles", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "PARENT", planType: "FREE" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    expect(screen.queryByRole("button", { name: /Build Exam/i })).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Take the Long Exam|Take the Board Exam|Start Interview Practice/ }),
    ).not.toBeInTheDocument();
  });

  it("hides admin publish action for non-admins", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    expect(screen.queryByRole("button", { name: "Publish settings" })).not.toBeInTheDocument();
  });

  it("publishes a study plan from the admin publish modal", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({ courseProgram: "LET" }));
    (updateCollectionVisibility as jest.Mock).mockResolvedValue(collection({ courseProgram: "LET", visibility: "PUBLIC" }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });
    fireEvent.click(screen.getByRole("button", { name: "Publish settings" }));

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
    fireEvent.click(screen.getByRole("button", { name: "Publish settings" }));

    const makePublicButton = await screen.findByRole("button", { name: "Make 1 public" });
    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();

    fireEvent.click(makePublicButton);

    await waitFor(() => {
      expect(updateNoteVisibility).toHaveBeenCalledWith("note-1", "PUBLIC");
    });
    expect(updateNoteVisibility).not.toHaveBeenCalledWith("note-2", "PUBLIC");
  });

  it("offers a standalone Save action so course/program persists when publishing is blocked", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({ courseProgram: "LET" }));
    (listNotes as jest.Mock).mockResolvedValue([
      { ...note("note-1", "Cell Respiration"), visibility: "PRIVATE" },
      { ...note("note-2", "Dosage Calculations"), visibility: "PUBLIC" },
    ]);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });
    fireEvent.click(screen.getByRole("button", { name: "Publish settings" }));

    // Publishing is blocked by the private note, but metadata save is decoupled: a standalone
    // Save action remains so the course/program is never discarded just because publish can't proceed.
    expect(await screen.findByRole("button", { name: "Save" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();
  });

  it("preserves course/program when editing the description", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ courseProgram: "Accountancy" }));
    (updateCollection as jest.Mock).mockResolvedValue(collection({ courseProgram: "Accountancy", description: "New notes" }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    const description = await screen.findByLabelText("Description");
    fireEvent.change(description, { target: { value: "New notes" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateCollection).toHaveBeenCalledWith("collection-1", {
        title: "Midterm Study Plan",
        description: "New notes",
        courseProgram: "Accountancy",
      });
    });
  });

  it("reorders by move button within a section and persists the grouped order", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], noteId: "note-1", title: "Foundations", label: "Week 1", position: 0 },
        { ...collection().items[1], noteId: "note-3", title: "Assessment", label: "Week 1", position: 1 },
        { ...collection().items[0], noteId: "note-2", title: "Extra", label: null, position: 2 },
      ],
    }));
    (setCollectionItemOrder as jest.Mock).mockResolvedValue(collection());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { level: 2, name: "Foundations" });
    fireEvent.click(screen.getByRole("button", { name: "Organize" }));
    // first "Move down" belongs to note-1 (first item in the "Week 1" section)
    fireEvent.click(screen.getAllByRole("button", { name: "Move down" })[0]);

    await waitFor(() => {
      expect(setCollectionItemOrder).toHaveBeenCalledWith("collection-1", [
        { noteId: "note-3", label: "Week 1" },
        { noteId: "note-1", label: "Week 1" },
        { noteId: "note-2", label: null },
      ]);
    });
  });

  it("disables move buttons at section boundaries", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], noteId: "note-1", title: "Foundations", label: "Week 1", position: 0 },
        { ...collection().items[1], noteId: "note-2", title: "Extra", label: null, position: 1 },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { level: 2, name: "Foundations" });
    fireEvent.click(screen.getByRole("button", { name: "Organize" }));
    // each note is alone in its section, so every move button is a boundary and disabled
    screen.getAllByRole("button", { name: "Move up" }).forEach((button) => expect(button).toBeDisabled());
    screen.getAllByRole("button", { name: "Move down" }).forEach((button) => expect(button).toBeDisabled());
  });

  it("assigns a section through the existing order endpoint without changing other labels", async () => {
    const flatCollection = collection({
      items: collection().items.map((item) => ({ ...item, label: null })),
    });
    const savedCollection = collection({
      items: [
        { ...collection().items[0], label: null, position: 0 },
        { ...collection().items[1], label: "Professional Education", position: 1 },
      ],
    });
    (getCollection as jest.Mock).mockResolvedValue(flatCollection);
    (setCollectionItemOrder as jest.Mock).mockResolvedValue(savedCollection);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { level: 2, name: "Cell Respiration" });
    fireEvent.click(screen.getByRole("button", { name: "Organize" }));
    const sectionInputs = screen.getAllByLabelText("Section");
    fireEvent.change(sectionInputs[1], { target: { value: "Professional Education" } });
    fireEvent.blur(sectionInputs[1]);

    await waitFor(() => {
      expect(setCollectionItemOrder).toHaveBeenCalledWith("collection-1", [
        { noteId: "note-1", label: null },
        { noteId: "note-2", label: "Professional Education" },
      ]);
    });
    expect(await screen.findByLabelText("Rename section Professional Education")).toBeInTheDocument();
    expect(screen.getByText("Ungrouped")).toBeInTheDocument();
  });

  it("clears a section assignment back to Ungrouped through the order endpoint", async () => {
    const savedCollection = collection({
      items: [
        { ...collection().items[0], label: null, position: 0 },
        { ...collection().items[1], label: null, position: 1 },
      ],
    });
    (setCollectionItemOrder as jest.Mock).mockResolvedValue(savedCollection);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { level: 2, name: "Cell Respiration" });
    fireEvent.click(screen.getByRole("button", { name: "Organize" }));
    const sectionInputs = screen.getAllByLabelText("Section");
    fireEvent.change(sectionInputs[0], { target: { value: "" } });
    fireEvent.blur(sectionInputs[0]);

    await waitFor(() => {
      expect(setCollectionItemOrder).toHaveBeenCalledWith("collection-1", [
        { noteId: "note-1", label: null },
        { noteId: "note-2", label: null },
      ]);
    });
    expect(screen.queryAllByTestId("collection-section-heading")).toHaveLength(0);
  });

  it("removes a collection item", async () => {
    (removeCollectionItem as jest.Mock).mockResolvedValue(undefined);
    (getCollection as jest.Mock)
      .mockResolvedValueOnce(collection())
      .mockResolvedValueOnce(collection({ items: [collection().items[1]] }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { level: 2, name: "Cell Respiration" });
    fireEvent.click(screen.getByRole("button", { name: "Organize" }));
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

  it("selects all available notes from the picker with select-all", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      note("note-1", "Hidden Existing Note"),
      note("note-3", "Chemistry Notes"),
      note("note-4", "Biology Notes"),
    ]);
    (addCollectionItems as jest.Mock).mockResolvedValue(collection());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Add notes" }));
    await screen.findByText("Chemistry Notes");

    fireEvent.click(screen.getByRole("button", { name: /Select all/ }));
    fireEvent.click(screen.getByRole("button", { name: "Add selected" }));

    await waitFor(() => {
      expect(addCollectionItems).toHaveBeenCalledWith("collection-1", ["note-3", "note-4"]);
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
