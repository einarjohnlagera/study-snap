import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import {
  aggregateSectionReadiness,
  CollectionDetailPageClient,
  getLatestPracticedCollectionItem,
} from "./collection-detail-page-client";
import {
  addCollectionItems,
  ApiRequestError,
  clearCollectionTargetDate,
  clearCompanion,
  clearPrimaryCollection,
  generateCompanion,
  getCollection,
  getCollectionGoal,
  getActiveAskCompanionSession,
  getMe,
  getNoteConceptCounts,
  getPlanReadiness,
  listCoursePrograms,
  listNotes,
  setCompanion,
  setPrimaryCollection,
  trackAnalyticsEvent,
  updateCollection,
  updateCollectionVisibility,
  updateNoteVisibility,
  updateStudyDaysPerWeek,
  type NoteCollectionItem,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { setJustAdoptedNotice } from "@/lib/just-adopted-notice";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  useSearchParams: () => new URLSearchParams(),
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
    clearCollectionTargetDate: jest.fn(),
    clearCompanion: jest.fn(),
    clearPrimaryCollection: jest.fn(),
    generateCompanion: jest.fn(),
    deleteCollection: jest.fn(),
    getCollection: jest.fn(),
    getCollectionGoal: jest.fn(),
    getActiveAskCompanionSession: jest.fn(),
    startAskCompanionSession: jest.fn(),
    askCompanionQuestion: jest.fn(),
    getMe: jest.fn(),
    getNoteConceptCounts: jest.fn(),
    getPlanReadiness: jest.fn(),
    listCoursePrograms: jest.fn(),
    listNotes: jest.fn(),
    setCompanion: jest.fn(),
    setPrimaryCollection: jest.fn(),
    trackAnalyticsEvent: jest.fn(),
    updateCollection: jest.fn(),
    updateCollectionVisibility: jest.fn(),
    updateNoteVisibility: jest.fn(),
    updateStudyDaysPerWeek: jest.fn(),
  };
});

function collection(overrides: Record<string, unknown> = {}) {
  return {
    id: "collection-1",
    title: "Midterm Study Plan",
    description: "Weeks 1-4",
    visibility: "PRIVATE",
    courseProgram: null,
    estimatedStudyHours: null,
    targetCompletionDate: null,
    companion: null,
    companionMayBeOutdated: false,
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
    targetCompletionDate: null,
    companion: null,
    sourcePlanId: null,
    parentCollectionId: null,
    itemCount: 0,
    childCount: 2,
    overallReadinessPercentage: 45,
    masteredConcepts: 9,
    dueConcepts: 4,
    notPracticedConcepts: 7,
    totalConcepts: 20,
    weeksRemaining: null,
    conceptsRemaining: null,
    todaysConceptBudget: null,
    weeklyFocusByDay: [],
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
        todaysConceptBudget: null,
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
        todaysConceptBudget: null,
      },
    ],
    ...overrides,
  };
}

function planReadiness(overrides: Record<string, unknown> = {}) {
  return {
    collectionId: "collection-1",
    totalNotes: 2,
    notesWithStudyPack: 1,
    overallReadinessPercentage: 40,
    totalConcepts: 10,
    masteredConcepts: 4,
    dueConcepts: 2,
    notPracticedConcepts: 4,
    subjects: [
      {
        subject: "Biology",
        totalConcepts: 10,
        masteredConcepts: 4,
        dueConcepts: 2,
        notPracticedConcepts: 4,
        masteryPercentage: 40,
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

describe("collection detail helpers", () => {
  it("aggregates concept counts by section label", () => {
    const items = [
      { ...collection().items[0], noteId: "note-1", label: "Week 1" },
      { ...collection().items[1], noteId: "note-2", label: "Week 1" },
      { ...collection().items[0], noteId: "note-3", label: null },
    ] as NoteCollectionItem[];

    const result = aggregateSectionReadiness(items, {
      "note-1": { totalConceptCount: 4, masteredConceptCount: 2, dueConceptCount: 1, notPracticedConceptCount: 1 },
      "note-2": { totalConceptCount: 3, masteredConceptCount: 1, dueConceptCount: 1, notPracticedConceptCount: 1 },
      "note-3": { totalConceptCount: 2, masteredConceptCount: 0, dueConceptCount: 0, notPracticedConceptCount: 2 },
    });

    expect(result.get("Week 1")).toEqual({ mastered: 3, total: 7, due: 2 });
    expect(result.get("Ungrouped")).toEqual({ mastered: 0, total: 2, due: 0 });
  });

  it("selects the latest practiced note for continue state", () => {
    const latest = getLatestPracticedCollectionItem([
      { ...collection().items[0], noteId: "note-1", lastSessionCompletedAt: "2026-06-01T00:00:00Z" },
      { ...collection().items[1], noteId: "note-2", lastSessionCompletedAt: "2026-06-03T00:00:00Z" },
    ] as NoteCollectionItem[]);

    expect(latest?.noteId).toBe("note-2");
    expect(getLatestPracticedCollectionItem(collection().items.map((item) => ({ ...item, lastSessionCompletedAt: null })) as NoteCollectionItem[])).toBeNull();
  });
});

describe("CollectionDetailPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (addCollectionItems as jest.Mock).mockReset();
    (clearCollectionTargetDate as jest.Mock).mockReset();
    (clearCompanion as jest.Mock).mockReset();
    (clearPrimaryCollection as jest.Mock).mockReset();
    (generateCompanion as jest.Mock).mockReset();
    (getCollection as jest.Mock).mockReset();
    (getCollectionGoal as jest.Mock).mockReset();
    (getActiveAskCompanionSession as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getNoteConceptCounts as jest.Mock).mockReset();
    (getPlanReadiness as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockReset();
    (setCompanion as jest.Mock).mockReset();
    (setPrimaryCollection as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (updateCollection as jest.Mock).mockReset();
    (updateCollectionVisibility as jest.Mock).mockReset();
    (updateStudyDaysPerWeek as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockReset();
    (updateNoteVisibility as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockResolvedValue([]);
    (listNotes as jest.Mock).mockResolvedValue([]);
    (updateNoteVisibility as jest.Mock).mockResolvedValue(undefined);
    (setCompanion as jest.Mock).mockResolvedValue(collection());
    (clearCompanion as jest.Mock).mockResolvedValue(collection({ companion: null }));
    (setPrimaryCollection as jest.Mock).mockResolvedValue(undefined);
    (clearPrimaryCollection as jest.Mock).mockResolvedValue(undefined);
    (updateCollection as jest.Mock).mockResolvedValue(collection());
    (generateCompanion as jest.Mock).mockResolvedValue({
      overview: "Generated overview",
      studyStrategy: "Generated strategy",
      commonMistakes: "Generated mistakes",
      faq: [
        { question: "Generated question 1?", answer: "Generated answer 1." },
        { question: "Generated question 2?", answer: "Generated answer 2." },
        { question: "Generated question 3?", answer: "Generated answer 3." },
      ],
      mentorTips: [
        {
          id: "tip-generated-1",
          title: "Generated tip",
          body: "Generated mentor tip body.",
          linkedAction: "NONE",
          surfacingCondition: null,
        },
      ],
    });
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE" });
    (trackAnalyticsEvent as jest.Mock).mockResolvedValue(undefined);
    (getCollection as jest.Mock).mockResolvedValue(collection());
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());
    (getActiveAskCompanionSession as jest.Mock).mockResolvedValue(null);
    (getMe as jest.Mock).mockResolvedValue({ studyDaysPerWeek: null, primaryCollectionId: null });
    (updateStudyDaysPerWeek as jest.Mock).mockResolvedValue({ studyDaysPerWeek: null });
    (getNoteConceptCounts as jest.Mock).mockResolvedValue({});
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness());
    Object.defineProperty(globalThis.window, "innerWidth", {
      configurable: true,
      value: 1024,
    });
    let uuidCounter = 0;
    Object.defineProperty(globalThis, "crypto", {
      configurable: true,
      value: {
        ...globalThis.crypto,
        randomUUID: jest.fn(() => {
          uuidCounter += 1;
          return `draft-tip-${uuidCounter}`;
        }),
      },
    });
    globalThis.localStorage.clear();
    globalThis.sessionStorage.clear();
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
    const dosageHeading = await screen.findByRole("heading", { level: 2, name: "Dosage Calculations" });
    const cellHeading = screen.getByRole("heading", { level: 2, name: "Cell Respiration" });
    expect(Boolean(dosageHeading.compareDocumentPosition(cellHeading) & Node.DOCUMENT_POSITION_FOLLOWING)).toBe(true);
    expect(screen.getByRole("link", { name: "Study Plans" })).toHaveAttribute("href", "/collections");
    expect(screen.getByRole("link", { name: "Build" })).toHaveAttribute("href", "/collections/collection-1/builder");
    expect(screen.getByRole("link", { name: "View full progress" })).toHaveAttribute("href", "/progress?collectionId=collection-1");
  });

  it("renders plan readiness inline for leaf plans after the hero", async () => {
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness({
      overallReadinessPercentage: 60,
      masteredConcepts: 6,
      dueConcepts: 1,
      notPracticedConcepts: 3,
      totalConcepts: 10,
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan readiness" })).toBeInTheDocument();
    await waitFor(() => {
      expect(document.body).toHaveTextContent("60% ready · 6/10 mastered · 1 due");
    });
    const bodyText = document.body.textContent ?? "";
    expect(bodyText.indexOf("Midterm Study Plan readiness")).toBeLessThan(bodyText.indexOf("1 of 2 practiced"));
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
    const professionalEducationLink = screen.getAllByText("Professional Education Mastery")
      .map((element) => element.closest("a"))
      .find((link) => link?.getAttribute("href") === "/collections/child-1");
    expect(professionalEducationLink).toHaveAttribute("href", "/collections/child-1");
    expect(screen.getByText("General Education Mastery").closest("a")).toHaveAttribute("href", "/collections/child-2");
    expect(screen.queryByRole("heading", { name: "Notes" })).not.toBeInTheDocument();
    expect(screen.getByText("2 Subject Plans")).toBeInTheDocument();
    expect(getPlanReadiness).not.toHaveBeenCalled();
    expect(getNoteConceptCounts).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "Read more" })).not.toBeInTheDocument();
  });

  it("does not start leaf readiness requests while Goal detail is still pending", async () => {
    let resolveGoal!: (value: ReturnType<typeof goalDetail>) => void;
    const pendingGoal = new Promise<ReturnType<typeof goalDetail>>((resolve) => {
      resolveGoal = resolve;
    });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      title: "LET Mastery",
      childCount: 2,
      progress: { totalNotes: 0, notesWithStudyPack: 0, notesPracticed: 0 },
      items: [],
    }));
    (getCollectionGoal as jest.Mock).mockReturnValue(pendingGoal);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await waitFor(() => expect(getCollectionGoal).toHaveBeenCalledWith("collection-1"));
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByRole("heading", { name: "Loading..." })).toBeInTheDocument();
    expect(getNoteConceptCounts).not.toHaveBeenCalled();
    expect(getPlanReadiness).not.toHaveBeenCalled();

    await act(async () => {
      resolveGoal(goalDetail());
    });

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(getNoteConceptCounts).not.toHaveBeenCalled();
    expect(getPlanReadiness).not.toHaveBeenCalled();
  });

  it("starts leaf readiness requests while childless Goal detail is still pending", async () => {
    let resolveGoal!: (value: ReturnType<typeof goalDetail>) => void;
    const pendingGoal = new Promise<ReturnType<typeof goalDetail>>((resolve) => {
      resolveGoal = resolve;
    });
    (getCollectionGoal as jest.Mock).mockReturnValue(pendingGoal);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await waitFor(() => {
      expect(getNoteConceptCounts).toHaveBeenCalledWith("collection-1");
      expect(getPlanReadiness).toHaveBeenCalledWith("collection-1");
    });
    expect(screen.getByRole("heading", { name: "Loading..." })).toBeInTheDocument();

    await act(async () => {
      resolveGoal(goalDetail({ childCount: 0, children: [] }));
    });

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
  });

  it("starts nested-plan readiness without requesting Goal detail", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ parentCollectionId: "goal-1" }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await waitFor(() => {
      expect(getNoteConceptCounts).toHaveBeenCalledWith("collection-1");
      expect(getPlanReadiness).toHaveBeenCalledWith("collection-1");
    });
    expect(getCollectionGoal).not.toHaveBeenCalled();
  });

  it("expands clamped Goal and Subject Plan descriptions", async () => {
    const longDescription = "A detailed curator description that gives learners useful context before they begin this section. ".repeat(8);
    const originalClientHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "clientHeight");
    const originalScrollHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollHeight");
    Object.defineProperty(HTMLElement.prototype, "clientHeight", { configurable: true, get: () => 20 });
    Object.defineProperty(HTMLElement.prototype, "scrollHeight", { configurable: true, get: () => 80 });

    try {
      (getCollection as jest.Mock).mockResolvedValue(collection({
        title: "LET Mastery",
        description: longDescription,
        childCount: 2,
        progress: { totalNotes: 0, notesWithStudyPack: 0, notesPracticed: 0 },
        items: [],
      }));
      (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({
        children: [
          { ...goalDetail().children[0], description: longDescription },
          goalDetail().children[1],
        ],
      }));

      render(<CollectionDetailPageClient collectionId="collection-1" />);

      const readMoreButtons = await screen.findAllByRole("button", { name: "Read more" });
      expect(readMoreButtons).toHaveLength(2);

      fireEvent.click(readMoreButtons[0]);

      expect(screen.getByRole("button", { name: "Show less" })).toBeInTheDocument();
    } finally {
      if (originalClientHeight) {
        Object.defineProperty(HTMLElement.prototype, "clientHeight", originalClientHeight);
      } else {
        delete (HTMLElement.prototype as { clientHeight?: number }).clientHeight;
      }
      if (originalScrollHeight) {
        Object.defineProperty(HTMLElement.prototype, "scrollHeight", originalScrollHeight);
      } else {
        delete (HTMLElement.prototype as { scrollHeight?: number }).scrollHeight;
      }
    }
  });

  it("resolves the Goal view's Primary Action CTA to the first child when there is no continue or next-note action", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      title: "LET Mastery",
      childCount: 2,
      progress: { totalNotes: 0, notesWithStudyPack: 0, notesPracticed: 0 },
      items: [],
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("link", { name: /Continue Studying/ })).toHaveAttribute("href", "/collections/child-1");
    expect(screen.getByText("Open the next subject plan in this goal.")).toBeInTheDocument();
    expect(screen.getAllByText("Professional Education Mastery").length).toBeGreaterThan(0);
  });

  it("shows an Adopted badge on the leaf view only when sourcePlanId is set", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ sourcePlanId: "source-1" }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    expect(screen.getByText("Adopted")).toBeInTheDocument();
  });

  it("hides the Adopted badge on the leaf view for a self-created collection", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ sourcePlanId: null }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    expect(screen.queryByText("Adopted")).not.toBeInTheDocument();
  });

  it("shows an Adopted badge on the Goal view only when sourcePlanId is set", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      title: "LET Mastery",
      childCount: 2,
      sourcePlanId: "source-1",
      items: [],
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.getByText("Adopted")).toBeInTheDocument();
  });

  it("keeps Adopted near the title and renders Primary as a hero accent", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ sourcePlanId: "source-1", courseProgram: "Nursing" }));
    (getMe as jest.Mock).mockResolvedValue({ studyDaysPerWeek: null, primaryCollectionId: "collection-1" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const title = await screen.findByRole("heading", { name: "Midterm Study Plan" });
    const adoptedBadge = screen.getByText("Adopted");
    const primaryIndicator = screen.getByText("Primary");

    expect(title.compareDocumentPosition(adoptedBadge) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(title.compareDocumentPosition(primaryIndicator) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByText("Nursing")).toBeInTheDocument();
  });

  it("sets a top-level collection as primary from the detail menu", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Set as primary" }));

    await waitFor(() => {
      expect(setPrimaryCollection).toHaveBeenCalledWith("collection-1");
    });
    expect(screen.getByText("Primary")).toBeInTheDocument();
    expect(await screen.findByText("Set as primary.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    expect(screen.getByRole("menuitem", { name: "Remove as primary" })).toBeInTheDocument();
  });

  it("removes primary status from the detail menu when the collection is already primary", async () => {
    (getMe as jest.Mock).mockResolvedValue({ studyDaysPerWeek: null, primaryCollectionId: "collection-1" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    expect(await screen.findByText("Primary")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Remove as primary" }));

    await waitFor(() => {
      expect(clearPrimaryCollection).toHaveBeenCalledWith("collection-1");
    });
    expect(screen.queryByText("Primary")).not.toBeInTheDocument();
    expect(await screen.findByText("Removed as primary.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    expect(screen.getByRole("menuitem", { name: "Set as primary" })).toBeInTheDocument();
  });

  it("records a success notice and navigates to the list after deleting", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Delete" }));
    fireEvent.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/collections");
    });
    expect(globalThis.sessionStorage.getItem("notelib-collection-action-notice")).toBe("Study Plan deleted.");
  });

  it("keeps primary UI unchanged and shows the error banner when setting primary fails", async () => {
    (setPrimaryCollection as jest.Mock).mockRejectedValue(new Error("Request failed"));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Set as primary" }));

    expect(await screen.findByText("Could not set this as your primary review set.")).toBeInTheDocument();
    expect(screen.queryByText("Primary")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    expect(screen.getByRole("menuitem", { name: "Set as primary" })).toBeInTheDocument();
  });

  it("shows the primary action on a childless top-level leaf collection", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ childCount: 0, parentCollectionId: null }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));

    expect(screen.getByRole("menuitem", { name: "Set as primary" })).toBeInTheDocument();
  });

  it("hides the primary action for a child Subject plan", async () => {
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "goal-1") {
        return Promise.resolve(collection({ id: "goal-1", title: "Parent Goal", childCount: 1 }));
      }
      return Promise.resolve(collection({ parentCollectionId: "goal-1" }));
    });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));

    expect(screen.queryByRole("menuitem", { name: "Set as primary" })).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Remove as primary" })).not.toBeInTheDocument();
  });

  it("keeps the detail page usable when loading primary state fails", async () => {
    (getMe as jest.Mock).mockRejectedValue(new Error("Profile unavailable"));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));

    expect(screen.getByRole("menuitem", { name: "Set as primary" })).toBeInTheDocument();
  });

  it("shows primary status on initial load when getMe returns the collection as primary", async () => {
    (getMe as jest.Mock).mockResolvedValue({ studyDaysPerWeek: null, primaryCollectionId: "collection-1" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    expect(await screen.findByText("Primary")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    expect(screen.getByRole("menuitem", { name: "Remove as primary" })).toBeInTheDocument();
  });

  it("shows the primary badge and toggle action on the Goal view", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      title: "LET Mastery",
      childCount: 2,
      items: [],
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());
    (getMe as jest.Mock).mockResolvedValue({ studyDaysPerWeek: null, primaryCollectionId: "collection-1" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(await screen.findByText("Primary")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    expect(screen.getByRole("menuitem", { name: "Remove as primary" })).toBeInTheDocument();
  });

  it("shows the companion editor action for admins on a top-level leaf collection", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({ childCount: 0, parentCollectionId: null }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));

    expect(screen.getByRole("menuitem", { name: "Manage Companion" })).toBeInTheDocument();
  });

  it("shows the companion editor action for admins on the Goal view", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      title: "LET Mastery",
      childCount: 2,
      items: [],
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));

    expect(screen.getByRole("menuitem", { name: "Manage Companion" })).toBeInTheDocument();
  });

  it("hides the companion editor action for non-admins", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    expect(screen.queryByRole("menuitem", { name: "Manage Companion" })).not.toBeInTheDocument();
  });

  it("hides the companion editor action for child Subject plans", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "goal-1") {
        return Promise.resolve(collection({ id: "goal-1", title: "Parent Goal", childCount: 1 }));
      }
      return Promise.resolve(collection({ parentCollectionId: "goal-1" }));
    });

    render(<CollectionDetailPageClient collectionId="collection-2" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    expect(screen.queryByRole("menuitem", { name: "Manage Companion" })).not.toBeInTheDocument();
  });

  it("seeds the companion editor from existing content", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "Start with the foundations.",
        studyStrategy: "Review one section per day.",
        commonMistakes: "Skipping practice questions.",
        resources: "- [Curriculum guide](https://example.com/curriculum)",
        faq: [{ question: "How long should I study?", answer: "Use the target date." }],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    const dialog = within(screen.getByRole("dialog", { name: "Manage Companion" }));

    expect(dialog.getByLabelText("Overview")).toHaveValue("Start with the foundations.");
    expect(dialog.getByLabelText("Study Strategy")).toHaveValue("Review one section per day.");
    expect(dialog.getByLabelText("Common Mistakes")).toHaveValue("Skipping practice questions.");
    expect(dialog.getByLabelText("Question 1")).toHaveValue("How long should I study?");
    expect(dialog.getByLabelText("Answer 1")).toHaveValue("Use the target date.");
    expect(dialog.getByLabelText("Resources")).toHaveValue("- [Curriculum guide](https://example.com/curriculum)");
    expect(dialog.getByRole("button", { name: "Remove Companion" })).toBeInTheDocument();
    expect(dialog.queryByRole("button", { name: "Generate Resources" })).not.toBeInTheDocument();
  });

  it("opens an empty companion editor when no companion exists", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));

    expect(screen.getByLabelText("Overview")).toHaveValue("");
    expect(screen.getByLabelText("Study Strategy")).toHaveValue("");
    expect(screen.getByLabelText("Common Mistakes")).toHaveValue("");
    expect(screen.getByLabelText("Resources")).toHaveValue("");
    expect(screen.queryByRole("button", { name: "Generate Resources" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove Companion" })).not.toBeInTheDocument();
  });

  it("shows the outdated companion signal only inside the admin editor", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "Start with the foundations.",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
      },
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({ companionMayBeOutdated: true }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "View Full Guide" }));
    expect(screen.getByText("Start with the foundations.")).toBeInTheDocument();
    expect(screen.queryByText(/may be outdated/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));

    const dialog = within(screen.getByRole("dialog", { name: "Manage Companion" }));
    expect(dialog.getByText(/may be outdated/i)).toBeInTheDocument();
  });

  it("generates one companion section into local modal state without saving", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    const confirmSpy = jest.spyOn(globalThis, "confirm");
    (generateCompanion as jest.Mock).mockResolvedValue({
      overview: "Generated overview only",
      studyStrategy: null,
      commonMistakes: null,
      faq: [],
      mentorTips: [],
    });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.click(screen.getByRole("button", { name: "Generate Overview" }));

    await waitFor(() => {
      expect(generateCompanion).toHaveBeenCalledWith("collection-1", ["OVERVIEW"]);
    });
    expect(screen.getByLabelText("Overview")).toHaveValue("Generated overview only");
    expect(screen.getByLabelText("Study Strategy")).toHaveValue("");
    expect(setCompanion).not.toHaveBeenCalled();
    expect(trackAnalyticsEvent).toHaveBeenCalledWith({
      eventType: "COMPANION_GENERATED",
      entityId: "collection-1",
      metadata: { sections: ["OVERVIEW"] },
    });
    expect(await screen.findByText("Draft generated. Review and edit it before saving.")).toBeInTheDocument();
    expect(confirmSpy).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it("generates all companion sections into local modal state", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.click(screen.getByRole("button", { name: "Generate all sections" }));

    await waitFor(() => {
      expect(generateCompanion).toHaveBeenCalledWith("collection-1", [
        "OVERVIEW",
        "STUDY_STRATEGY",
        "COMMON_MISTAKES",
        "FAQ",
        "MENTOR_TIPS",
      ]);
    });
    expect(screen.getByLabelText("Overview")).toHaveValue("Generated overview");
    expect(screen.getByLabelText("Study Strategy")).toHaveValue("Generated strategy");
    expect(screen.getByLabelText("Common Mistakes")).toHaveValue("Generated mistakes");
    expect(screen.getByLabelText("Question 1")).toHaveValue("Generated question 1?");
    expect(screen.getByLabelText("Answer 1")).toHaveValue("Generated answer 1.");
    expect(screen.getByLabelText("Tip title 1")).toHaveValue("Generated tip");
    expect(screen.getByLabelText("Tip body 1")).toHaveValue("Generated mentor tip body.");
    expect(setCompanion).not.toHaveBeenCalled();
  });

  it("preserves companion fields when generation fails", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "Existing overview",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
        mentorTips: [],
      },
    }));
    (generateCompanion as jest.Mock).mockRejectedValue(new Error("Could not generate Companion draft content."));
    const confirmSpy = jest.spyOn(globalThis, "confirm").mockReturnValue(true);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.click(screen.getByRole("button", { name: "Generate Overview" }));

    expect(await screen.findByText("Could not generate Companion draft content.")).toBeInTheDocument();
    expect(within(screen.getByRole("dialog", { name: "Manage Companion" })).getByLabelText("Overview")).toHaveValue("Existing overview");
    expect(setCompanion).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it("asks before overwriting non-empty companion fields and leaves them untouched when declined", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "Existing overview",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
        mentorTips: [],
      },
    }));
    const confirmSpy = jest.spyOn(globalThis, "confirm").mockReturnValue(false);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.click(screen.getByRole("button", { name: "Generate Overview" }));

    expect(confirmSpy).toHaveBeenCalled();
    expect(generateCompanion).not.toHaveBeenCalled();
    expect(within(screen.getByRole("dialog", { name: "Manage Companion" })).getByLabelText("Overview")).toHaveValue("Existing overview");
    confirmSpy.mockRestore();
  });

  it("adds and removes companion FAQ rows by index", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.click(screen.getByRole("button", { name: "Add question" }));
    fireEvent.click(screen.getByRole("button", { name: "Add question" }));
    fireEvent.change(screen.getByLabelText("Question 1"), { target: { value: "First question" } });
    fireEvent.change(screen.getByLabelText("Question 2"), { target: { value: "Second question" } });

    fireEvent.click(screen.getAllByRole("button", { name: "Remove" })[0]!);

    expect(screen.getByLabelText("Question 1")).toHaveValue("Second question");
  });

  it("adds, edits, and removes Mentor Tip rows by index", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.click(screen.getByRole("button", { name: "Add tip" }));
    fireEvent.click(screen.getByRole("button", { name: "Add tip" }));
    fireEvent.change(screen.getByLabelText("Tip title 1"), { target: { value: "First tip" } });
    fireEvent.change(screen.getByLabelText("Tip title 2"), { target: { value: "Second tip" } });
    fireEvent.change(screen.getByLabelText("Linked action 2"), { target: { value: "REVIEW_DUE_CONCEPTS" } });
    fireEvent.change(screen.getByLabelText("Surfacing condition 2"), { target: { value: "DAYS_BEFORE_TARGET_DATE" } });
    fireEvent.change(screen.getByLabelText("Surfacing threshold 2"), { target: { value: "5" } });

    fireEvent.click(screen.getAllByRole("button", { name: "Remove tip" })[0]!);

    expect(screen.getByLabelText("Tip title 1")).toHaveValue("Second tip");
    expect(screen.getByLabelText("Linked action 1")).toHaveValue("REVIEW_DUE_CONCEPTS");
    expect(screen.getByLabelText("Surfacing condition 1")).toHaveValue("DAYS_BEFORE_TARGET_DATE");
    expect(screen.getByLabelText("Surfacing threshold 1")).toHaveValue(5);
  });

  it("generates Mentor Tip drafts and appends them to the local editor state", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    const confirmSpy = jest.spyOn(globalThis, "confirm").mockReturnValue(true);
    (generateCompanion as jest.Mock).mockResolvedValue({
      overview: null,
      studyStrategy: null,
      commonMistakes: null,
      faq: [],
      mentorTips: [
        {
          id: null,
          title: "Generated first pass",
          body: "Start with one short action.",
          linkedAction: "NONE",
          surfacingCondition: null,
        },
      ],
    });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.click(screen.getByRole("button", { name: "Add tip" }));
    fireEvent.change(screen.getByLabelText("Tip title 1"), { target: { value: "Manual tip" } });
    fireEvent.click(screen.getByRole("button", { name: "Generate tips" }));

    await waitFor(() => {
      expect(generateCompanion).toHaveBeenCalledWith("collection-1", ["MENTOR_TIPS"]);
    });
    expect(screen.getByLabelText("Tip title 1")).toHaveValue("Manual tip");
    expect(screen.getByLabelText("Tip title 2")).toHaveValue("Generated first pass");
    expect(screen.getByLabelText("Tip body 2")).toHaveValue("Start with one short action.");
    expect(screen.getByLabelText("Linked action 2")).toHaveValue("NONE");
    expect(screen.getByLabelText("Surfacing condition 2")).toHaveValue("ALWAYS");
    expect(setCompanion).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it("saves companion content and applies the returned collection locally", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (setCompanion as jest.Mock).mockResolvedValue(collection({
      title: "Updated Companion Plan",
      companion: {
        overview: "Overview draft",
        studyStrategy: null,
        commonMistakes: null,
        resources: "- [Reviewer archive](https://example.com/reviewer)",
        faq: [{ question: "What now?", answer: "Practice." }],
        mentorTips: [
          {
            id: "tip-saved-1",
            title: "Start timed practice",
            body: "Use one timed pass after reviewing basics.",
            linkedAction: "TERMINAL_ACTION",
            surfacingCondition: { type: "AFTER_SUBJECTS_COMPLETED", threshold: 1 },
          },
        ],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.change(screen.getByLabelText("Overview"), { target: { value: "Overview draft" } });
    fireEvent.click(screen.getByRole("button", { name: "Add question" }));
    fireEvent.change(screen.getByLabelText("Question 1"), { target: { value: "What now?" } });
    fireEvent.change(screen.getByLabelText("Answer 1"), { target: { value: "Practice." } });
    fireEvent.change(screen.getByLabelText("Resources"), { target: { value: "- [Reviewer archive](https://example.com/reviewer)" } });
    fireEvent.click(screen.getByRole("button", { name: "Add tip" }));
    fireEvent.change(screen.getByLabelText("Tip title 1"), { target: { value: "Start timed practice" } });
    fireEvent.change(screen.getByLabelText("Tip body 1"), { target: { value: "Use one timed pass after reviewing basics." } });
    fireEvent.change(screen.getByLabelText("Linked action 1"), { target: { value: "TERMINAL_ACTION" } });
    fireEvent.change(screen.getByLabelText("Surfacing condition 1"), { target: { value: "AFTER_SUBJECTS_COMPLETED" } });
    fireEvent.change(screen.getByLabelText("Surfacing threshold 1"), { target: { value: "1" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      const savedPayload = (setCompanion as jest.Mock).mock.calls[0]?.[1];
      expect(savedPayload).toEqual({
        overview: "Overview draft",
        studyStrategy: null,
        commonMistakes: null,
        resources: "- [Reviewer archive](https://example.com/reviewer)",
        faq: [{ question: "What now?", answer: "Practice." }],
        mentorTips: [
          expect.objectContaining({
            title: "Start timed practice",
            body: "Use one timed pass after reviewing basics.",
            linkedAction: "TERMINAL_ACTION",
            surfacingCondition: { type: "AFTER_SUBJECTS_COMPLETED", threshold: 1 },
          }),
        ],
      });
    });
    expect(await screen.findByRole("heading", { name: "Updated Companion Plan" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Learning Companion" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "View Full Guide" }));
    expect(screen.getByText("Overview draft")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Common questions" })).toBeInTheDocument();
    expect(screen.getByText("What now?")).toBeInTheDocument();
    expect(screen.getByText("Practice.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Extra resources" })).toBeInTheDocument();
    expect(screen.getByText("- [Reviewer archive](https://example.com/reviewer)")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Quick tips" })).toBeInTheDocument();
    expect(screen.getByText("Start timed practice")).toBeInTheDocument();
    expect(await screen.findByText("Companion saved.")).toBeInTheDocument();
  });

  it("keeps companion input visible when saving fails", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (setCompanion as jest.Mock).mockRejectedValue(new Error("Could not save this Companion."));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.change(screen.getByLabelText("Overview"), { target: { value: "Preserve this draft" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Could not save this Companion.")).toBeInTheDocument();
    expect(screen.getByLabelText("Overview")).toHaveValue("Preserve this draft");
  });

  it("clears an existing companion and applies the returned collection locally", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "Existing overview",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
      },
    }));
    (clearCompanion as jest.Mock).mockResolvedValue(collection({ title: "Companion Removed", companion: null }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.click(screen.getByRole("button", { name: "Remove Companion" }));

    await waitFor(() => {
      expect(clearCompanion).toHaveBeenCalledWith("collection-1");
    });
    expect(await screen.findByRole("heading", { name: "Companion Removed" })).toBeInTheDocument();
    expect(await screen.findByText("Companion removed.")).toBeInTheDocument();
  });

  it("keeps companion state unchanged when removing fails", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "FREE", role: "ADMIN" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "Existing overview",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
      },
    }));
    (clearCompanion as jest.Mock).mockRejectedValue(new Error("Could not remove this Companion."));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Manage Companion" }));
    fireEvent.click(screen.getByRole("button", { name: "Remove Companion" }));

    expect(await screen.findByText("Could not remove this Companion.")).toBeInTheDocument();
    expect(within(screen.getByRole("dialog", { name: "Manage Companion" })).getByLabelText("Overview")).toHaveValue("Existing overview");
  });

  it("shows the Coach encouragement even when companion is null", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Learning Companion" })).not.toBeInTheDocument();
    expect(screen.queryByText("Companion")).not.toBeInTheDocument();
    expect(screen.getByText("Pick up where you left off — you've got this.")).toBeInTheDocument();
    expect(screen.queryByText(/worked through everything here/)).not.toBeInTheDocument();
  });

  it("shows the Coach encouragement above the companion when a primary action remains", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "Start with the foundations.",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const introText = await screen.findByText("Pick up where you left off — you've got this.");
    const companionHeading = screen.getByRole("heading", { name: "Learning Companion" });
    expect(introText).toBeInTheDocument();
    expect(screen.queryByText(/worked through everything here/)).not.toBeInTheDocument();
    expect(introText.compareDocumentPosition(companionHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("shows the Coach caught-up message when no primary action remains", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [],
      companion: {
        overview: "Start with the foundations.",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText(/worked through everything here/)).toBeInTheDocument();
    expect(screen.queryByText(/Pick up where you left off/)).not.toBeInTheDocument();
  });

  it("does not render a companion display card for an empty draft", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: " ",
        studyStrategy: null,
        commonMistakes: "",
        resources: " ",
        faq: [],
        mentorTips: [],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Learning Companion" })).not.toBeInTheDocument();
    expect(screen.getByText("Pick up where you left off — you've got this.")).toBeInTheDocument();
  });

  it("surfaces an always-eligible Mentor Tip near Today's Focus without requiring long-form Companion prose", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        resources: null,
        faq: [],
        mentorTips: [
          {
            id: "tip-1",
            title: "Make one clean pass",
            body: "Start with the next note, then review mistakes after.",
            linkedAction: "CONTINUE_STUDYING",
            surfacingCondition: null,
          },
        ],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Mentor Tip")).toBeInTheDocument();
    expect(screen.getByText("Make one clean pass")).toBeInTheDocument();
    expect(screen.getByText("Start with the next note, then review mistakes after.")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Continue Studying" })[0]).toHaveAttribute("href", "/notes/note-2?ref=%2Fcollections%2Fcollection-1");
    expect(screen.getByRole("heading", { name: "Learning Companion" })).toBeInTheDocument();
  });

  it("selects the first eligible Mentor Tip in authored order", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        resources: null,
        faq: [],
        mentorTips: [
          {
            id: "tip-not-yet",
            title: "Not yet",
            body: "This should wait.",
            linkedAction: "NONE",
            surfacingCondition: { type: "DAYS_BEFORE_TARGET_DATE", threshold: 1 },
          },
          {
            id: "tip-now",
            title: "Use the next doable step",
            body: "This one is always available.",
            linkedAction: "NONE",
            surfacingCondition: null,
          },
        ],
      },
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({
      childCount: 0,
      children: [],
      targetCompletionDate: "2999-01-01",
      weeksRemaining: 999,
      conceptsRemaining: 11,
      todaysConceptBudget: 4,
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Use the next doable step")).toBeInTheDocument();
    expect(screen.queryByText("Not yet")).not.toBeInTheDocument();
  });

  it("uses target-date Mentor Tip eligibility only when the threshold is met", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        resources: null,
        faq: [],
        mentorTips: [
          {
            id: "tip-date",
            title: "Switch to review mode",
            body: "Use tighter review blocks near the target date.",
            linkedAction: "NONE",
            surfacingCondition: { type: "DAYS_BEFORE_TARGET_DATE", threshold: 999999 },
          },
        ],
      },
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({
      childCount: 0,
      children: [],
      targetCompletionDate: "2999-01-01",
      weeksRemaining: 999,
      conceptsRemaining: 11,
      todaysConceptBudget: 4,
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Switch to review mode")).toBeInTheDocument();
  });

  it("uses completed-subject Mentor Tip eligibility from existing readiness subjects", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        resources: null,
        faq: [],
        mentorTips: [
          {
            id: "tip-subject",
            title: "Celebrate and connect",
            body: "Use the completed subject as a base for the next one.",
            linkedAction: "NONE",
            surfacingCondition: { type: "AFTER_SUBJECTS_COMPLETED", threshold: 1 },
          },
        ],
      },
    }));
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness({
      subjects: [
        {
          subject: "Biology",
          totalConcepts: 10,
          masteredConcepts: 10,
          dueConcepts: 0,
          notPracticedConcepts: 0,
          masteryPercentage: 100,
        },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Celebrate and connect")).toBeInTheDocument();
  });

  it("does not surface Mentor Tips whose required data is unavailable, but lists them in the full guide", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        resources: null,
        faq: [],
        mentorTips: [
          {
            id: "tip-date",
            title: "Target-date only tip",
            body: "This should stay in the guide until a date exists.",
            linkedAction: "NONE",
            surfacingCondition: { type: "DAYS_BEFORE_TARGET_DATE", threshold: 7 },
          },
        ],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Learning Companion" })).toBeInTheDocument();
    expect(screen.queryByText("Mentor Tip")).not.toBeInTheDocument();
    expect(screen.queryByText("Target-date only tip")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "View Full Guide" }));

    expect(screen.getByRole("heading", { name: "Quick tips" })).toBeInTheDocument();
    expect(screen.getByText("Target-date only tip")).toBeInTheDocument();
  });

  it("renders a Mentor Tip action as plain text when its linked target is unavailable", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [],
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        resources: null,
        faq: [],
        mentorTips: [
          {
            id: "tip-dangling",
            title: "Use this later",
            body: "The primary action is unavailable in a caught-up state.",
            linkedAction: "CONTINUE_STUDYING",
            surfacingCondition: null,
          },
        ],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Use this later")).toBeInTheDocument();
    expect(screen.getByText("Continue Studying")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Continue Studying" })).not.toBeInTheDocument();
  });

  it("resolves Mentor Tip links to the due-concept quick action when available", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "PLUS" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        { ...collection().items[0], dueConceptCount: 2, dueConcepts: ["Concept 1"] },
        collection().items[1],
      ],
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        resources: null,
        faq: [],
        mentorTips: [
          {
            id: "tip-due",
            title: "Clear due concepts",
            body: "Refresh the concepts that need attention.",
            linkedAction: "REVIEW_DUE_CONCEPTS",
            surfacingCondition: null,
          },
          {
            id: "tip-terminal",
            title: "Terminal fallback",
            body: "Not selected because the due tip is first.",
            linkedAction: "TERMINAL_ACTION",
            surfacingCondition: null,
          },
        ],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Clear due concepts")).toBeInTheDocument();
    const dueLinks = await screen.findAllByRole("link", { name: "Review Due Concepts" });
    expect(dueLinks[0]).toHaveAttribute(
      "href",
      "/notes/note-1?ref=%2Fcollections%2Fcollection-1",
    );
    expect(screen.queryByText("Terminal fallback")).not.toBeInTheDocument();
  });

  it("resolves Mentor Tip links to the terminal quick action when available", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "PLUS" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        resources: null,
        faq: [],
        mentorTips: [
          {
            id: "tip-terminal",
            title: "Try the exam mode",
            body: "Use the terminal practice action when your Study Pack is ready.",
            linkedAction: "TERMINAL_ACTION",
            surfacingCondition: null,
          },
        ],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Try the exam mode")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Take the Long Exam" }).length).toBeGreaterThan(0);
  });

  it("renders only populated companion prose sections", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "**Start** with the foundations.",
        studyStrategy: " ",
        commonMistakes: null,
        faq: [],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Learning Companion" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "View Full Guide" }));
    expect(screen.getByRole("heading", { name: "What this covers" })).toBeInTheDocument();
    expect(screen.getByTestId("summary-markdown")).toHaveTextContent("**Start** with the foundations.");
    expect(screen.queryByRole("heading", { name: "How to study this" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Avoid these traps" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Common questions" })).not.toBeInTheDocument();
  });

  it("renders a companion with only resources populated", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        resources: "- [Curriculum guide](https://example.com/curriculum)",
        faq: [],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Learning Companion" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "View Full Guide" }));
    expect(screen.getByRole("heading", { name: "Extra resources" })).toBeInTheDocument();
    expect(screen.getByTestId("summary-markdown")).toHaveTextContent("- [Curriculum guide](https://example.com/curriculum)");
    expect(screen.queryByRole("heading", { name: "What this covers" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Common questions" })).not.toBeInTheDocument();
  });

  it("skips the companion FAQ section when every entry is blank", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        faq: [
          { question: " ", answer: null },
          { question: "", answer: " " },
        ],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Learning Companion" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Common questions" })).not.toBeInTheDocument();
  });

  it("renders populated companion FAQ entries and skips blank entries", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: null,
        commonMistakes: null,
        faq: [
          { question: " ", answer: null },
          { question: "How should I start?", answer: "Read the **overview** first." },
          { question: null, answer: "Then practice the weakest concepts." },
        ],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Learning Companion" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "View Full Guide" }));
    expect(screen.getByRole("heading", { name: "Common questions" })).toBeInTheDocument();
    expect(screen.getByText("How should I start?")).toBeInTheDocument();
    expect(screen.getAllByTestId("summary-markdown").map((element) => element.textContent)).toEqual([
      "Read the **overview** first.",
      "Then practice the weakest concepts.",
    ]);
    expect(screen.getByText("Then practice the weakest concepts.")).toBeInTheDocument();
    expect(screen.queryByText(/^ $/)).not.toBeInTheDocument();
  });

  it("collapses the full companion guide by default and toggles it via View Full Guide", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "Start with the foundations.",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Learning Companion" })).toBeInTheDocument();
    const toggle = screen.getByRole("button", { name: "View Full Guide" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByText("Start with the foundations.")).toBeInTheDocument();

    fireEvent.click(toggle);

    const hideButton = screen.getByRole("button", { name: "Hide Full Guide" });
    expect(hideButton).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Start with the foundations.")).toBeInTheDocument();

    fireEvent.click(hideButton);

    expect(screen.getByRole("button", { name: "View Full Guide" })).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByText("Start with the foundations.")).toBeInTheDocument();
  });

  it("shows a one-line teaser of the collapsed companion guide's overview", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "**Start** with the foundations before moving on.",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Learning Companion" })).toBeInTheDocument();
    expect(screen.getByText("Start with the foundations before moving on.")).toBeInTheDocument();
  });

  it("falls back to Study Strategy for the collapsed teaser when Overview is empty", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: null,
        studyStrategy: "Review one chapter per day.",
        commonMistakes: null,
        faq: [],
      },
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Learning Companion" })).toBeInTheDocument();
    expect(screen.getByText("Review one chapter per day.")).toBeInTheDocument();
  });

  it("renders the companion after Goal-view readiness content", async () => {
    setJustAdoptedNotice("collection-1");
    (getCollection as jest.Mock).mockResolvedValue(collection({
      title: "LET Mastery",
      childCount: 2,
      items: [],
      companion: {
        overview: "Goal guidance first.",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
      },
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const readinessHeading = await screen.findByRole("heading", { name: "LET Mastery readiness" });
    const guidanceTip = screen.getByText("Set a target completion date to see your weekly countdown and daily study budget.");
    const companionHeading = screen.getByRole("heading", { name: "Learning Companion" });

    expect(readinessHeading.compareDocumentPosition(guidanceTip) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(guidanceTip.compareDocumentPosition(companionHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("renders the companion after leaf-view readiness content", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({
      companion: {
        overview: "Leaf guidance first.",
        studyStrategy: null,
        commonMistakes: null,
        faq: [],
      },
    }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({
      childCount: 0,
      children: [],
      targetCompletionDate: "2026-12-01",
      weeksRemaining: 3,
      conceptsRemaining: 11,
      todaysConceptBudget: 4,
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const readinessHeading = await screen.findByRole("heading", { name: "Midterm Study Plan readiness" });
    const companionHeading = screen.getByRole("heading", { name: "Learning Companion" });

    expect(readinessHeading.compareDocumentPosition(companionHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("shows target-date pacing in Today's Focus and Progress when countdown fields are set", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [] }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({
      targetCompletionDate: "2026-12-01",
      weeksRemaining: 3,
      conceptsRemaining: 11,
      todaysConceptBudget: 4,
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.getByText("Study about 4 concepts today to stay on pace.")).toBeInTheDocument();
    expect(await screen.findByText("3 weeks until Dec 1, 2026 · 11 concepts remaining")).toBeInTheDocument();
    expect(screen.queryByText("This Week")).not.toBeInTheDocument();
  });

  it("uses the on-pace Coach sentence instead of a literal zero when today's concept budget is 0", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [] }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({
      targetCompletionDate: "2026-12-01",
      weeksRemaining: 3,
      conceptsRemaining: 0,
      todaysConceptBudget: 0,
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.getByText("You're on pace — no new concepts scheduled today.")).toBeInTheDocument();
    expect(screen.queryByText(/Study about 0/)).not.toBeInTheDocument();
    expect(await screen.findByText("3 weeks until Dec 1, 2026 · 0 concepts remaining")).toBeInTheDocument();
  });

  it("hides Progress countdown and uses the no-target Coach sentence when no target date is set", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [] }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.queryByText("This Week")).not.toBeInTheDocument();
    expect(screen.queryByText(/weeks until/)).not.toBeInTheDocument();
    expect(screen.getByText("Pick up where you left off — you've got this.")).toBeInTheDocument();
  });

  it("shows target-date pacing on a childless top-level plan while still rendering the leaf view", async () => {
    // Regression test: a top-level collection with zero children (a flat "leaf" Study Plan, not a
    // Goal with Subject plans) can still carry a target date — the countdown must appear even though
    // this collection renders the leaf view (Build/notes list), not the Goal view (children list).
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "Midterm Study Plan", childCount: 0 }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({
      childCount: 0,
      children: [],
      targetCompletionDate: "2026-12-01",
      weeksRemaining: 3,
      conceptsRemaining: 11,
      todaysConceptBudget: 4,
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("Study about 4 concepts today to stay on pace.")).toBeInTheDocument();
    // Still the leaf view — not switched to the Goal view's children list.
    expect(await screen.findByRole("heading", { name: "Midterm Study Plan readiness" })).toBeInTheDocument();
    expect(screen.getByText("3 weeks until Dec 1, 2026 · 11 concepts remaining")).toBeInTheDocument();
    expect(screen.queryByText(/Subject Plans?$/)).not.toBeInTheDocument();
  });

  it("does not show the Progress countdown on a childless top-level plan with no target date", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "Midterm Study Plan", childCount: 0 }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({ childCount: 0, children: [] }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan readiness" })).toBeInTheDocument();
    expect(screen.queryByText("This Week")).not.toBeInTheDocument();
    expect(screen.queryByText(/weeks until/)).not.toBeInTheDocument();
  });

  it("does not show the post-adopt target-date tip on a freshly-adopted childless leaf plan", async () => {
    // Leaf-plan adoption is documented to never show this tip (it's Goal-adoption-specific) — must
    // still hold now that goalDetail is also populated for childless top-level collections.
    setJustAdoptedNotice("collection-1");
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "Midterm Study Plan", childCount: 0 }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({ childCount: 0, children: [] }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan readiness" })).toBeInTheDocument();
    expect(screen.queryByText("Set a target completion date to see your weekly countdown and daily study budget."))
      .not.toBeInTheDocument();
  });

  it("refetches the leaf-view countdown after editing a childless top-level plan's target date", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "Midterm Study Plan", childCount: 0 }));
    (getCollectionGoal as jest.Mock).mockResolvedValueOnce(goalDetail({ childCount: 0, children: [] }));
    (updateCollection as jest.Mock).mockResolvedValue(
      collection({ title: "Midterm Study Plan", childCount: 0, targetCompletionDate: "2026-12-01" }),
    );

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    expect(await screen.findByRole("heading", { name: "Midterm Study Plan readiness" })).toBeInTheDocument();
    expect(screen.queryByText("This Week")).not.toBeInTheDocument();

    (getCollectionGoal as jest.Mock).mockResolvedValueOnce(goalDetail({
      childCount: 0,
      children: [],
      targetCompletionDate: "2026-12-01",
      weeksRemaining: 3,
      conceptsRemaining: 11,
      todaysConceptBudget: 4,
    }));

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));
    fireEvent.click(await screen.findByRole("button", { name: "Save" }));

    expect(await screen.findByText("Study about 4 concepts today to stay on pace.")).toBeInTheDocument();
    expect(screen.getByText("3 weeks until Dec 1, 2026 · 11 concepts remaining")).toBeInTheDocument();
  });

  it("shows a post-adopt target-date tip for a dateless Goal", async () => {
    setJustAdoptedNotice("collection-1");
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [] }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.getByText("Set a target completion date to see your weekly countdown and daily study budget.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Set target date" })).toBeInTheDocument();
  });

  it("does not show the post-adopt target-date tip when the Goal already has a target date", async () => {
    setJustAdoptedNotice("collection-1");
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [] }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({
      targetCompletionDate: "2026-12-01",
      weeksRemaining: 3,
      conceptsRemaining: 11,
      todaysConceptBudget: 4,
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.queryByText("Set a target completion date to see your weekly countdown and daily study budget.")).not.toBeInTheDocument();
  });

  it("does not show the post-adopt target-date tip on a normal dateless Goal visit", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [] }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.queryByText("Set a target completion date to see your weekly countdown and daily study budget.")).not.toBeInTheDocument();
  });

  it("opens the edit modal from the post-adopt target-date tip", async () => {
    setJustAdoptedNotice("collection-1");
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [] }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Set target date" }));

    expect(await screen.findByLabelText("Target completion date")).toBeInTheDocument();
  });

  it("refetches the Goal countdown after editing so it reflects a newly-set target date", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [] }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());
    (updateCollection as jest.Mock).mockResolvedValue(
      collection({ title: "LET Mastery", childCount: 2, targetCompletionDate: "2026-12-01" }),
    );

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    expect(await screen.findByRole("heading", { name: "LET Mastery" })).toBeInTheDocument();
    expect(screen.queryByText("This Week")).not.toBeInTheDocument();

    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail({
      targetCompletionDate: "2026-12-01",
      weeksRemaining: 2,
      conceptsRemaining: 6,
      todaysConceptBudget: 2,
    }));

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));
    fireEvent.change(await screen.findByLabelText("Target completion date"), { target: { value: "2026-12-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("2 weeks until Dec 1, 2026 · 6 concepts remaining")).toBeInTheDocument();
  });

  it("shows the readiness unavailable state without hiding the leaf plan", async () => {
    (getPlanReadiness as jest.Mock).mockRejectedValue(new Error("Readiness failed"));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan readiness" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Readiness is unavailable right now. Try refreshing this plan.")).toBeInTheDocument();
    });
    expect(screen.getByText("1 of 2 practiced")).toBeInTheDocument();
  });

  it("back-links a nested Subject plan to its parent Goal instead of the flat Study Plans list", async () => {
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "goal-1") {
        return Promise.resolve(collection({ id: "goal-1", title: "LET Mastery", childCount: 2 }));
      }
      return Promise.resolve(collection({ id: "collection-1", parentCollectionId: "goal-1" }));
    });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("link", { name: "LET Mastery" })).toHaveAttribute("href", "/collections/goal-1");
  });

  it("falls back to the flat Study Plans link while the parent Goal title has not resolved", async () => {
    (getCollection as jest.Mock).mockImplementation((id: string) => {
      if (id === "goal-1") {
        return new Promise(() => {});
      }
      return Promise.resolve(collection({ id: "collection-1", parentCollectionId: "goal-1" }));
    });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("link", { name: "Study Plans" })).toHaveAttribute("href", "/collections");
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
    expect(screen.getByRole("link", { name: "Build" })).toHaveAttribute("href", "/collections/collection-1/builder");
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

    const notesCard = await screen.findByText(/3 notes in saved order/);
    const notesRegion = notesCard.closest("div")?.parentElement;
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

  it("routes note organization to the Builder from the leaf hero only", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { level: 2, name: "Cell Respiration" });
    expect(screen.queryByRole("button", { name: "Organize" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Build" })).toHaveAttribute("href", "/collections/collection-1/builder");
    expect(screen.queryByRole("link", { name: "Edit" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Drag Cell Respiration")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Section")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Move up" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Move down" })).not.toBeInTheDocument();
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

    expect(await screen.findByText("1 of 2 practiced")).toBeInTheDocument();
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
    expect(screen.getByText("Add notes to start tracking practice.")).toBeInTheDocument();
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
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item) => ({ ...item, lastSessionCompletedAt: null })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const nextLink = await screen.findByRole("link", { name: "Continue Studying" });
    expect(screen.getByText("Turn this note into a Study Pack before moving to the next step.")).toBeInTheDocument();
    expect(nextLink).toHaveAttribute("href", "/notes/note-1?ref=%2Fcollections%2Fcollection-1");
  });

  it("continues from the latest practiced note before falling back to the next plan action", async () => {
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

    const nextLink = await screen.findByRole("link", { name: "Continue Studying" });
    expect(screen.getByText("Continue from this note. Next step: Study this note.")).toBeInTheDocument();
    expect(nextLink).toHaveAttribute("href", "/notes/note-1?ref=%2Fcollections%2Fcollection-1");
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

    const nextLinks = await screen.findAllByRole("link", { name: "Review Due Concepts" });
    const nextLink = nextLinks.find((link) => link.getAttribute("href") === "/notes/note-2?ref=%2Fcollections%2Fcollection-1");
    if (!nextLink) {
      throw new Error("Expected Review due concepts link for note-2");
    }
    expect(nextLink).toHaveAttribute("href", "/notes/note-2?ref=%2Fcollections%2Fcollection-1");
  });

  it("shows the readiness due-concepts CTA even when the primary action prioritizes generation", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "PLUS" });
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness({ dueConcepts: 3 }));
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: [
        {
          ...collection().items[0],
          studyPackStatus: "DRAFT",
          dueConceptCount: 3,
          dueConcepts: ["ATP", "Glycolysis", "Krebs cycle"],
        },
        {
          ...collection().items[1],
          studyPackStatus: "STUDY_PACK_READY",
          lastSessionCompletedAt: null,
          dueConceptCount: 0,
          dueConcepts: [],
        },
      ],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("link", { name: "Review Due Concepts" })).toHaveAttribute(
      "href",
      "/notes/note-1?ref=%2Fcollections%2Fcollection-1",
    );
    expect(screen.getByRole("link", { name: "Continue Studying" })).toHaveAttribute(
      "href",
      "/notes/note-1?ref=%2Fcollections%2Fcollection-1",
    );
  });

  it("does not show the dedicated due-concepts CTA without an entitled due item", async () => {
    (getPlanReadiness as jest.Mock).mockResolvedValue(planReadiness({ dueConcepts: 3 }));
    (getCollection as jest.Mock).mockResolvedValue(collection({
      items: collection().items.map((item) => ({
        ...item,
        studyPackStatus: "STUDY_PACK_READY",
        lastSessionCompletedAt: "2026-06-03T00:00:00Z",
        dueConceptCount: 2,
        dueConcepts: ["Hidden concept"],
      })),
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("link", { name: "Continue Studying" })).toHaveAttribute(
      "href",
      "/notes/note-1?ref=%2Fcollections%2Fcollection-1",
    );
    expect(screen.queryByRole("link", { name: "Review Due Concepts" })).not.toBeInTheDocument();
  });

  it("shows caught up when no action remains", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT", planType: "PLUS" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      progress: {
        totalNotes: 0,
        notesWithStudyPack: 0,
        notesPracticed: 0,
      },
      items: [],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("You've worked through everything here. Nice work.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Continue Studying" })).not.toBeInTheDocument();
  });

  it("keeps terminal quick actions visible when no primary action remains", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "TEACHER", planType: "FREE" });
    (getCollection as jest.Mock).mockResolvedValue(collection({
      progress: {
        totalNotes: 0,
        notesWithStudyPack: 0,
        notesPracticed: 0,
      },
      items: [],
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByText("You've worked through everything here. Nice work.")).toBeInTheDocument();
    expect(screen.getByText("Quick Actions")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Build Exam" })).toBeDisabled();
    expect(screen.getByText("Generate a quiz for at least one note to build an exam.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Continue Studying" })).not.toBeInTheDocument();
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

    expect(await screen.findByRole("link", { name: "Continue Studying" })).toHaveAttribute(
      "href",
      "/notes/note-1?ref=%2Fcollections%2Fcollection-1",
    );
    expect(screen.queryByRole("link", { name: "Review Due Concepts" })).not.toBeInTheDocument();
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
    expect(screen.queryByRole("button", { name: /Prove it/ })).not.toBeInTheDocument();
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

  it("renders the terminal exam as the secondary primary-action CTA", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const examButton = await screen.findByRole("button", { name: "Take the Long Exam" });
    expect(examButton).toBeEnabled();

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

    expect(pushMock).toHaveBeenCalledWith(
      "/study-packs/sp-2/challenge-quiz?collectionId=collection-1&entry=mode-selection",
    );
  });

  it("disables the board exam collection CTA when the primary studyPackId cannot be resolved", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM", planType: "FREE" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    const examButton = await screen.findByRole("button", { name: "Take the Board Exam" });
    expect(examButton).toBeDisabled();

    fireEvent.click(examButton);

    expect(pushMock).not.toHaveBeenCalled();
  });

  it("resolves the hero eyebrow and back link through getCollectionLabels for the BOARD_EXAM profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM", planType: "FREE" });

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    expect(await screen.findByRole("heading", { name: "Midterm Study Plan" })).toBeInTheDocument();
    expect(screen.getByText("Review Set")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Review Sets" })).toHaveAttribute("href", "/collections");
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
    // No terminal action and no due concepts for this profile/data combination — the "Quick
    // Actions" label itself must not render with nothing underneath it.
    expect(screen.queryByText("Quick Actions")).not.toBeInTheDocument();
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
    (listNotes as jest.Mock)
      .mockResolvedValueOnce([
        { ...note("note-1", "Cell Respiration"), visibility: "PRIVATE" },
        { ...note("note-2", "Dosage Calculations"), visibility: "PUBLIC" },
      ])
      .mockResolvedValue([
        { ...note("note-1", "Cell Respiration"), visibility: "PUBLIC" },
        { ...note("note-2", "Dosage Calculations"), visibility: "PUBLIC" },
      ]);

    render(<CollectionDetailPageClient collectionId="collection-1" />);

    await screen.findByRole("heading", { name: "Midterm Study Plan" });
    expect(listNotes).toHaveBeenCalledTimes(1);
    const cellRespirationRow = screen.getByRole("heading", { level: 2, name: "Cell Respiration" }).closest("li");
    expect(cellRespirationRow).not.toBeNull();
    expect(within(cellRespirationRow as HTMLElement).getByText("Private")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Publish settings" }));

    const makePublicButton = await screen.findByRole("button", { name: "Make 1 public" });
    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();

    fireEvent.click(makePublicButton);

    await waitFor(() => {
      expect(updateNoteVisibility).toHaveBeenCalledWith("note-1", "PUBLIC");
    });
    expect(updateNoteVisibility).not.toHaveBeenCalledWith("note-2", "PUBLIC");
    await waitFor(() => expect(listNotes).toHaveBeenCalledTimes(2));
    expect(within(cellRespirationRow as HTMLElement).queryByText("Private")).not.toBeInTheDocument();
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

  it("preserves course/program and estimated study hours when editing the description", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ courseProgram: "Accountancy", estimatedStudyHours: 3 }));
    (updateCollection as jest.Mock).mockResolvedValue(collection({ courseProgram: "Accountancy", estimatedStudyHours: 3, description: "New notes" }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    const description = await screen.findByLabelText("Description");
    fireEvent.change(description, { target: { value: "New notes" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      // updateMetadata now preserves fields omitted from the request, so the edit modal no longer
      // re-sends course/program: leaving it out is what keeps it from being wiped.
      expect(updateCollection).toHaveBeenCalledWith("collection-1", {
        title: "Midterm Study Plan",
        description: "New notes",
        estimatedStudyHours: 3,
      });
    });
    expect(await screen.findByText("Saved.")).toBeInTheDocument();
  });

  it("shows a Saved toast after editing a Goal-view collection", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [] }));
    (getCollectionGoal as jest.Mock).mockResolvedValue(goalDetail());
    (updateCollection as jest.Mock).mockResolvedValue(collection({ title: "LET Mastery", childCount: 2, items: [], description: "New notes" }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "LET Mastery" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    const description = await screen.findByLabelText("Description");
    fireEvent.change(description, { target: { value: "New notes" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Saved.")).toBeInTheDocument();
  });

  it("sends estimated study hours when set from the edit modal", async () => {
    (updateCollection as jest.Mock).mockResolvedValue(collection({ estimatedStudyHours: 4 }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    fireEvent.change(await screen.findByLabelText("Estimated study time (hours)"), { target: { value: "4" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateCollection).toHaveBeenCalledWith("collection-1", {
        title: "Midterm Study Plan",
        description: "Weeks 1-4",
        estimatedStudyHours: 4,
      });
    });
  });

  // An empty estimate field sends null. Server-side, updateMetadata now treats null as "unchanged"
  // (PATCH semantics), so clearing a previously-set estimate via this modal is deferred to a later
  // release; the modal still sends null, which the backend preserves. This asserts the payload only.
  it("sends a null estimated study hours when the edit field is empty", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ estimatedStudyHours: 2 }));
    (updateCollection as jest.Mock).mockResolvedValue(collection({ estimatedStudyHours: 2 }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    fireEvent.change(await screen.findByLabelText("Estimated study time (hours)"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateCollection).toHaveBeenCalledWith("collection-1", {
        title: "Midterm Study Plan",
        description: "Weeks 1-4",
        estimatedStudyHours: null,
      });
    });
  });

  it("shows the target date and study intensity fields for a top-level Goal", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    expect(await screen.findByLabelText("Target completion date")).toBeInTheDocument();
    expect(screen.getByLabelText("Study days per week")).toBeInTheDocument();
  });

  it("hides the target date and study intensity fields when editing a child Subject plan", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ parentCollectionId: "goal-1" }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    expect(await screen.findByLabelText("Estimated study time (hours)")).toBeInTheDocument();
    expect(screen.queryByLabelText("Target completion date")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Study days per week")).not.toBeInTheDocument();
    expect(getMe).toHaveBeenCalledTimes(1);
  });

  it("sets the target completion date via the general metadata PATCH", async () => {
    (updateCollection as jest.Mock).mockResolvedValue(collection({ targetCompletionDate: "2026-12-01" }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    fireEvent.change(await screen.findByLabelText("Target completion date"), { target: { value: "2026-12-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateCollection).toHaveBeenCalledWith("collection-1", {
        title: "Midterm Study Plan",
        description: "Weeks 1-4",
        estimatedStudyHours: null,
        targetCompletionDate: "2026-12-01",
      });
    });
    expect(clearCollectionTargetDate).not.toHaveBeenCalled();
  });

  it("clears a previously-set target date via the dedicated endpoint when the field is emptied", async () => {
    (getCollection as jest.Mock).mockResolvedValue(collection({ targetCompletionDate: "2026-12-01" }));
    (clearCollectionTargetDate as jest.Mock).mockResolvedValue(collection({ targetCompletionDate: null }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    const dateInput = await screen.findByLabelText("Target completion date");
    expect(dateInput).toHaveValue("2026-12-01");
    fireEvent.change(dateInput, { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(clearCollectionTargetDate).toHaveBeenCalledWith("collection-1");
    });
    expect(updateCollection).toHaveBeenCalledWith("collection-1", {
      title: "Midterm Study Plan",
      description: "Weeks 1-4",
      estimatedStudyHours: null,
    });
  });

  it("does not call the clear endpoint when the target date field was already empty", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    fireEvent.click(await screen.findByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateCollection).toHaveBeenCalled();
    });
    expect(clearCollectionTargetDate).not.toHaveBeenCalled();
  });

  it("pre-fills study intensity from the user's profile and saves a change", async () => {
    (getMe as jest.Mock).mockResolvedValue({ studyDaysPerWeek: 5 });

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    const intensityInput = await screen.findByLabelText("Study days per week");
    await waitFor(() => expect(intensityInput).toHaveValue(5));
    fireEvent.change(intensityInput, { target: { value: "3" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateStudyDaysPerWeek).toHaveBeenCalledWith(3);
    });
  });

  it("does not send a study intensity update if saved before the profile prefill resolves", async () => {
    (updateCollection as jest.Mock).mockResolvedValue(collection());
    let resolveGetMe: (value: { studyDaysPerWeek: number | null }) => void = () => {};
    (getMe as jest.Mock).mockReturnValue(new Promise((resolve) => {
      resolveGetMe = resolve;
    }));

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    // Save before the getMe() prefill promise ever resolves — this must not wipe the user's real
    // intensity to null. The rest of the form (title/description/etc.) still saves normally.
    await screen.findByLabelText("Study days per week");
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateCollection).toHaveBeenCalled();
    });
    expect(updateStudyDaysPerWeek).not.toHaveBeenCalled();

    resolveGetMe({ studyDaysPerWeek: 5 });
  });

  it("does not resend a study intensity update when the field is untouched", async () => {
    (getMe as jest.Mock).mockResolvedValue({ studyDaysPerWeek: 5 });
    (updateCollection as jest.Mock).mockResolvedValue(collection());

    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    const intensityInput = await screen.findByLabelText("Study days per week");
    await waitFor(() => expect(intensityInput).toHaveValue(5));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateCollection).toHaveBeenCalled();
    });
    expect(updateStudyDaysPerWeek).not.toHaveBeenCalled();
  });

  it("rejects a study intensity value outside 1-7 before saving", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });

    fireEvent.click(screen.getByRole("button", { name: "Open study plan actions" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Edit" }));

    const intensityInput = await screen.findByLabelText("Study days per week");
    // Wait for the async studyDaysPerWeek prefill from getMe() to fully resolve and apply before
    // changing the field — otherwise its resolution can land after fireEvent.change and overwrite
    // the "8" back to empty.
    await waitFor(() => expect(intensityInput).toHaveValue(null));
    fireEvent.change(intensityInput, { target: { value: "8" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Study days per week must be between 1 and 7.")).toBeInTheDocument();
    expect(updateCollection).not.toHaveBeenCalled();
  });

  it("does not show an Add notes button on the detail page (note addition moved to Builder)", async () => {
    render(<CollectionDetailPageClient collectionId="collection-1" />);
    await screen.findByRole("heading", { name: "Midterm Study Plan" });
    expect(screen.queryByRole("button", { name: "Add notes" })).not.toBeInTheDocument();
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
