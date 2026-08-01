import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import LibraryPage from "./page";
import { reorderSelectedNoteIdsByDrag } from "./exam-builder-order";
import {
  addCollectionItems,
  createCollection,
  createSavedLibraryFilter,
  deleteSavedLibraryFilter,
  getBulkGenerationResult,
  getLibraryFilterOptions,
  getLibrarySubjectStats,
  getSavedLibraryFilters,
  listLibraryMatchingIds,
  listLibraryPage,
  listCollections,
  listNoteStatuses,
  listNotes,
  listSubjects,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import {
  consumeBulkGenerationRetryStash,
  setBulkQueuedFlash,
} from "@/lib/bulk-generation-flash";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};
let currentSearch = "";

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  useSearchParams: () => new URLSearchParams(currentSearch),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error {
    status: number;

    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  addCollectionItems: jest.fn(),
  createCollection: jest.fn(),
  createSavedLibraryFilter: jest.fn(),
  deleteSavedLibraryFilter: jest.fn(),
  exportCombinedGeneratedQuizDocx: jest.fn(),
  getBulkGenerationResult: jest.fn(),
  getLibraryFilterOptions: jest.fn(),
  getLibrarySubjectStats: jest.fn(),
  getSavedLibraryFilters: jest.fn(),
  listCollections: jest.fn(),
  listLibraryMatchingIds: jest.fn(),
  listLibraryPage: jest.fn(),
  listNoteStatuses: jest.fn(),
  listNotes: jest.fn(),
  listSubjects: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/study-pack-generation", () => ({
  LIBRARY_GENERATION_POLL_MAX_TICKS: 3,
  LIBRARY_GENERATION_POLL_QUIET_TICKS: 1,
  STUDY_PACK_GENERATION_POLL_INTERVAL_MS: 10,
}));

async function openMoreFilters() {
  fireEvent.click(screen.getByRole("button", { name: "Open more filters" }));
  await screen.findByRole("heading", { name: "More Filters" });
}

function selectSubjectFilter(subject: string) {
  fireEvent.focus(screen.getAllByPlaceholderText("All")[0]);
  fireEvent.mouseDown(screen.getAllByRole("button", { name: subject })[0]);
}

async function openTagSelectorFromFilters() {
  fireEvent.click(screen.getByRole("button", { name: "Browse all" }));
  await screen.findByRole("heading", { name: "Select tags" });
}

function applyTopModal() {
  const applyButtons = screen.getAllByRole("button", { name: "Apply" });
  fireEvent.click(applyButtons[applyButtons.length - 1]);
}

// Minimal note builder for driving the (client-computed) subject stats strip.
function buildNote(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    title: `Note ${id}`,
    courseProgram: null,
    subject: null,
    tags: [],
    contentPreview: "",
    summaryPreview: "",
    visibility: "PRIVATE",
    studyPackId: null,
    studyPackStatus: "DRAFT",
    quizCount: null,
    keyConceptCount: null,
    generatedQuizId: null,
    generatedQuizQuestionCount: null,
    createdAt: "2026-03-20T10:00:00Z",
    updatedAt: "2026-03-21T10:00:00Z",
    lastSessionCompletedAt: null,
    ...overrides,
  };
}

function notesAcrossSubjects(subjectsByCount: Array<[string, number]>) {
  const notes: ReturnType<typeof buildNote>[] = [];
  let counter = 0;
  for (const [subject, count] of subjectsByCount) {
    for (let i = 0; i < count; i += 1) {
      counter += 1;
      notes.push(buildNote(`note-${counter}`, { subject }));
    }
  }
  return notes;
}

type MockLibraryParams = {
  search?: string;
  readiness?: string;
  courseProgram?: string;
  subject?: string;
  tags?: string[];
  visibility?: string;
  sort?: string;
  page?: number;
  pageSize?: number;
};

function mockLibrarySubject(note: ReturnType<typeof buildNote>) {
  return (note.subject as string | null) ?? (note.courseProgram as string | null) ?? "General";
}

function filterMockNotes(notes: ReturnType<typeof buildNote>[], params: MockLibraryParams) {
  const query = params.search?.trim().toLowerCase() ?? "";
  return notes.filter((note) => {
    const tags = (note.tags as string[]) ?? [];
    const searchMatches = !query
      || String(note.title ?? "Untitled note").toLowerCase().includes(query)
      || tags.some((tag) => tag.toLowerCase().includes(query));
    const readinessMatches = !params.readiness || params.readiness === "ALL"
      || (params.readiness === "DRAFT" && note.studyPackStatus === "DRAFT")
      || (params.readiness === "QUIZ_READY" && Boolean(note.generatedQuizId))
      || (params.readiness === "STUDY_PACK_READY" && note.studyPackStatus === "STUDY_PACK_READY");
    const courseMatches = !params.courseProgram || note.courseProgram === params.courseProgram;
    const subjectMatches = !params.subject || mockLibrarySubject(note) === params.subject;
    const tagsMatch = !params.tags?.length || params.tags.some((tag) => tags.includes(tag));
    const visibilityMatches = !params.visibility || params.visibility === "ALL" || note.visibility === params.visibility;
    return searchMatches && readinessMatches && courseMatches && subjectMatches && tagsMatch && visibilityMatches;
  });
}

function sortMockNotes(notes: ReturnType<typeof buildNote>[], sort = "RECENTLY_UPDATED") {
  const time = (value: unknown) => value ? new Date(String(value)).getTime() : 0;
  return [...notes].sort((left, right) => {
    if (sort === "TITLE_ASC") return String(left.title ?? "Untitled note").localeCompare(String(right.title ?? "Untitled note"));
    if (sort === "TITLE_DESC") return String(right.title ?? "Untitled note").localeCompare(String(left.title ?? "Untitled note"));
    if (sort === "OLDEST") return time(left.createdAt) - time(right.createdAt);
    if (sort === "NEWEST") return time(right.createdAt) - time(left.createdAt);
    if (sort === "RECENTLY_REVIEWED") {
      return time(right.lastSessionCompletedAt) - time(left.lastSessionCompletedAt)
        || time(right.updatedAt) - time(left.updatedAt);
    }
    return time(right.updatedAt) - time(left.updatedAt);
  });
}

function buildMockSubjectStats(notes: ReturnType<typeof buildNote>[], params: MockLibraryParams) {
  const matching = filterMockNotes(notes, {...params, subject: undefined});
  const counts = new Map<string, number>();
  matching.forEach((note) => {
    const subject = mockLibrarySubject(note);
    counts.set(subject, (counts.get(subject) ?? 0) + 1);
  });
  const sorted = [...counts.entries()].sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]));
  return {
    topSubjects: sorted.slice(0, 6).map(([subject, count]) => ({subject, count})),
    otherSubjectsCount: sorted.slice(6).reduce((total, [, count]) => total + count, 0),
    total: matching.length,
  };
}

function buildMockFilterOptions(notes: ReturnType<typeof buildNote>[]) {
  const subjects = new Map<string, number>();
  const coursePrograms = new Map<string, number>();
  const tags = new Map<string, number>();
  notes.forEach((note) => {
    const subject = mockLibrarySubject(note);
    subjects.set(subject, (subjects.get(subject) ?? 0) + 1);
    if (note.courseProgram) {
      const value = String(note.courseProgram);
      coursePrograms.set(value, (coursePrograms.get(value) ?? 0) + 1);
    }
    ((note.tags as string[]) ?? []).forEach((tag) => tags.set(tag, (tags.get(tag) ?? 0) + 1));
  });
  const facets = (counts: Map<string, number>) => [...counts.entries()]
    .map(([value, count]) => ({value, count}))
    .sort((left, right) => right.count - left.count || left.value.localeCompare(right.value));
  return {subjects: facets(subjects), coursePrograms: facets(coursePrograms), tags: facets(tags)};
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return {promise, resolve, reject};
}

describe("Library page", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    currentSearch = "";
    (addCollectionItems as jest.Mock).mockReset();
    (createCollection as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (addCollectionItems as jest.Mock).mockResolvedValue({
      id: "collection-1",
      title: "Midterm Plan",
      description: null,
      createdAt: "2026-03-24T00:00:00Z",
      updatedAt: "2026-03-24T00:00:00Z",
      items: [],
    });
    (createCollection as jest.Mock).mockResolvedValue({
      id: "created-collection",
      title: "New Plan",
      description: null,
      createdAt: "2026-03-24T00:00:00Z",
      updatedAt: "2026-03-24T00:00:00Z",
      items: [],
    });
    (getAuthUser as jest.Mock).mockReturnValue(null);
    (getSavedLibraryFilters as jest.Mock).mockResolvedValue([]);
    (listCollections as jest.Mock).mockResolvedValue([
      {
        id: "collection-1",
        title: "Midterm Plan",
        description: null,
        itemCount: 2,
        notesPracticed: 0,
        createdAt: "2026-03-24T00:00:00Z",
        updatedAt: "2026-03-24T00:00:00Z",
      },
    ]);
    (createSavedLibraryFilter as jest.Mock).mockResolvedValue({
      id: "saved-filter-1",
      name: "Review Notes",
      filterState: { search: "review" },
      createdAt: "2026-03-24T00:00:00Z",
    });
    (deleteSavedLibraryFilter as jest.Mock).mockResolvedValue(undefined);
    (getBulkGenerationResult as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry", "Pharmacy"]);
    (listNoteStatuses as jest.Mock).mockReset();
    (listNoteStatuses as jest.Mock).mockResolvedValue([]);
    (listNotes as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-42",
        title: "Cell Respiration",
        courseProgram: "Nursing",
        subject: "Biology",
        tags: ["cells", "energy", "mitochondria"],
        contentPreview: "ATP production in mitochondria...",
        summaryPreview: "Mitochondria convert glucose into usable ATP energy.",
        visibility: "PRIVATE",
        studyPackId: null,
        studyPackStatus: "DRAFT",
        quizCount: null,
        keyConceptCount: null,
        generatedQuizId: null,
        generatedQuizQuestionCount: null,
        createdAt: "2026-03-20T10:00:00Z",
        updatedAt: "2026-03-21T10:00:00Z",
      },
      {
        id: "note-99",
        title: "Zygote Review",
        courseProgram: "Chemistry",
        subject: "Chemistry",
        tags: ["review", "exam", "cells"],
        contentPreview: "Generated chemistry review preview...",
        summaryPreview: "Generated chemistry summary preview.",
        visibility: "PUBLIC",
        studyPackId: "pack-99",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        keyConceptCount: 4,
        generatedQuizId: "generated-99",
        generatedQuizQuestionCount: 10,
        createdAt: "2026-03-21T10:00:00Z",
        updatedAt: "2026-03-22T10:00:00Z",
      },
      {
        id: "note-77",
        title: "Dosage Calculations",
        courseProgram: "Pharmacy",
        subject: null,
        tags: ["math", "medication", "review"],
        contentPreview: "Medication dosage formulas and unit conversions...",
        summaryPreview: "Practice dosage conversion steps for common prescriptions.",
        visibility: "PRIVATE",
        studyPackId: "pack-77",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 4,
        keyConceptCount: 6,
        generatedQuizId: "generated-77",
        generatedQuizQuestionCount: 10,
        createdAt: "2026-03-18T10:00:00Z",
        updatedAt: "2026-03-23T10:00:00Z",
      },
    ]);
    (listLibraryPage as jest.Mock).mockReset();
    (listLibraryPage as jest.Mock).mockImplementation(async (params: MockLibraryParams) => {
      const notes = await (listNotes as jest.Mock)() as ReturnType<typeof buildNote>[];
      const matching = sortMockNotes(filterMockNotes(notes, params), params.sort);
      const page = params.page ?? 0;
      const pageSize = params.pageSize ?? 20;
      const items = matching.slice(page * pageSize, (page + 1) * pageSize);
      return {
        items,
        page,
        pageSize,
        totalMatching: matching.length,
        hasMore: (page + 1) * pageSize < matching.length,
      };
    });
    (getLibrarySubjectStats as jest.Mock).mockReset();
    (getLibrarySubjectStats as jest.Mock).mockImplementation(async (params: MockLibraryParams) => {
      const notes = await (listNotes as jest.Mock)() as ReturnType<typeof buildNote>[];
      return buildMockSubjectStats(notes, params);
    });
    (getLibraryFilterOptions as jest.Mock).mockReset();
    (getLibraryFilterOptions as jest.Mock).mockImplementation(async () => {
      const notes = await (listNotes as jest.Mock)() as ReturnType<typeof buildNote>[];
      return buildMockFilterOptions(notes);
    });
    (listLibraryMatchingIds as jest.Mock).mockReset();
    (listLibraryMatchingIds as jest.Mock).mockImplementation(async (params: MockLibraryParams) => {
      const notes = await (listNotes as jest.Mock)() as ReturnType<typeof buildNote>[];
      const matching = filterMockNotes(notes, params);
      return {noteIds: matching.map((note) => note.id), totalMatching: matching.length, truncated: false};
    });
  });

  it("loads the initial library page exactly once", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await act(async () => {
      await new Promise((resolve) => globalThis.setTimeout(resolve, 450));
    });

    expect(listLibraryPage).toHaveBeenCalledTimes(1);
  });

  it("shows scope only on Study-Pack-ready private Library cards", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    expect(screen.getByText("4 concepts · 3 questions · ~5 min")).toBeInTheDocument();
    expect(screen.getByText("6 concepts · 4 questions · ~7 min")).toBeInTheDocument();
    expect(screen.queryByText(/0 concepts|~0 min/)).not.toBeInTheDocument();
  });

  it("hides scope for a regenerating note even though its prior pack's counts are still attached", async () => {
    // A note mid-regeneration keeps the previous StudyPackEntity row (and its counts) attached
    // to the response while status flips to GENERATING — showing the old pack's scope here would
    // be stale, not just absent. The STUDY_PACK_READY gate must hide it despite non-null counts.
    const regeneratingNote = buildNote("note-regenerating", {
      title: "Regenerating Note",
      studyPackStatus: "GENERATING",
      quizCount: 5,
      keyConceptCount: 6,
    });
    (listNotes as jest.Mock).mockResolvedValue([regeneratingNote]);

    render(<LibraryPage />);

    await screen.findByText("Regenerating Note");

    expect(screen.queryByText(/6 concepts|5 questions/)).not.toBeInTheDocument();
  });

  it("debounces filter requests and fetches the page and subject stats once", async () => {
    render(<LibraryPage />);
    await screen.findByText("Cell Respiration");
    (listLibraryPage as jest.Mock).mockClear();
    (getLibrarySubjectStats as jest.Mock).mockClear();

    const search = screen.getByLabelText("Search");
    fireEvent.change(search, {target: {value: "r"}});
    fireEvent.change(search, {target: {value: "re"}});
    fireEvent.change(search, {target: {value: "review"}});

    await waitFor(() => expect(listLibraryPage).toHaveBeenCalledTimes(1));
    expect(getLibrarySubjectStats).toHaveBeenCalledTimes(1);
    expect(listLibraryPage).toHaveBeenCalledWith(expect.objectContaining({search: "review", page: 0, pageSize: 20}));
  });

  it("ignores an older filter response that resolves after a newer one", async () => {
    const defaultImplementation = (listLibraryPage as jest.Mock).getMockImplementation()!;
    const older = deferred<Record<string, unknown>>();
    const newer = deferred<Record<string, unknown>>();
    (listLibraryPage as jest.Mock).mockImplementation((params: MockLibraryParams) => {
      if (params.search === "older") return older.promise;
      if (params.search === "newer") return newer.promise;
      return defaultImplementation(params);
    });
    render(<LibraryPage />);
    await screen.findByText("Cell Respiration");

    fireEvent.change(screen.getByLabelText("Search"), {target: {value: "older"}});
    await waitFor(() => expect(listLibraryPage).toHaveBeenCalledWith(expect.objectContaining({search: "older"})));
    fireEvent.change(screen.getByLabelText("Search"), {target: {value: "newer"}});
    await waitFor(() => expect(listLibraryPage).toHaveBeenCalledWith(expect.objectContaining({search: "newer"})));

    await act(async () => newer.resolve({
      items: [buildNote("newer-note", {title: "Newer result"})],
      page: 0,
      pageSize: 20,
      totalMatching: 1,
      hasMore: false,
    }));
    expect(await screen.findByText("Newer result")).toBeInTheDocument();
    await act(async () => older.resolve({
      items: [buildNote("older-note", {title: "Older result"})],
      page: 0,
      pageSize: 20,
      totalMatching: 1,
      hasMore: false,
    }));

    expect(screen.getByText("Newer result")).toBeInTheDocument();
    expect(screen.queryByText("Older result")).not.toBeInTheDocument();
  });

  it("appends the next server page and removes Load more at the end", async () => {
    const first = buildNote("page-one", {title: "Page one"});
    const second = buildNote("page-two", {title: "Page two"});
    (listLibraryPage as jest.Mock)
      .mockResolvedValueOnce({items: [first], page: 0, pageSize: 20, totalMatching: 2, hasMore: true})
      .mockResolvedValueOnce({items: [second], page: 1, pageSize: 20, totalMatching: 2, hasMore: false});

    render(<LibraryPage />);
    await screen.findByText("Page one");
    fireEvent.click(screen.getByRole("button", {name: "Load more"}));

    expect(await screen.findByText("Page two")).toBeInTheDocument();
    expect(screen.getByText("Page one")).toBeInTheDocument();
    expect(screen.queryByRole("button", {name: "Load more"})).not.toBeInTheDocument();
    expect(listLibraryPage).toHaveBeenLastCalledWith(expect.objectContaining({page: 1, pageSize: 20}));
  });

  it("keeps loaded notes and shows a toast when Load more fails", async () => {
    const first = buildNote("page-one", {title: "Page one"});
    (listLibraryPage as jest.Mock)
      .mockResolvedValueOnce({items: [first], page: 0, pageSize: 20, totalMatching: 2, hasMore: true})
      .mockRejectedValueOnce(new Error("network"));

    render(<LibraryPage />);
    await screen.findByText("Page one");
    fireEvent.click(screen.getByRole("button", {name: "Load more"}));

    expect(await screen.findByText("Could not load more notes.")).toBeInTheDocument();
    expect(screen.getByText("Page one")).toBeInTheDocument();
  });

  it("shows the existing load error when the initial library page fails", async () => {
    (listLibraryPage as jest.Mock).mockRejectedValue(new Error("Library unavailable"));

    render(<LibraryPage />);

    expect(await screen.findByText("Could not load notes")).toBeInTheDocument();
    expect(screen.getByText("Library unavailable")).toBeInTheDocument();
  });

  it("shows the existing load error when a debounced filter request fails", async () => {
    render(<LibraryPage />);
    await screen.findByText("Cell Respiration");
    (listLibraryPage as jest.Mock).mockRejectedValueOnce(new Error("Filtered library unavailable"));

    fireEvent.change(screen.getByLabelText("Search"), {target: {value: "biology"}});

    expect(await screen.findByText("Could not load notes")).toBeInTheDocument();
    expect(screen.getByText("Filtered library unavailable")).toBeInTheDocument();
  });

  it("renders notes and subject stats when filter options fail", async () => {
    (getLibraryFilterOptions as jest.Mock).mockRejectedValue(new Error("options unavailable"));
    (listNotes as jest.Mock).mockResolvedValue(notesAcrossSubjects([["Biology", 3], ["Chemistry", 2]]));

    render(<LibraryPage />);

    expect(await screen.findByText("Note note-1")).toBeInTheDocument();
    expect(screen.getByRole("button", {name: "Biology 3"})).toBeInTheDocument();
  });

  it("renders notes and hides the subject strip when subject stats fail", async () => {
    (getLibrarySubjectStats as jest.Mock).mockRejectedValue(new Error("stats unavailable"));
    (listNotes as jest.Mock).mockResolvedValue(notesAcrossSubjects([["Biology", 3], ["Chemistry", 2]]));

    render(<LibraryPage />);

    expect(await screen.findByText("Note note-1")).toBeInTheDocument();
    expect(screen.queryByRole("button", {name: "Biology 3"})).not.toBeInTheDocument();
  });

  it("opens note detail when a card is clicked and does not render card action menus", async () => {
    render(<LibraryPage />);

    expect(await screen.findByRole("heading", { name: "Library" })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Create options" })).toBeInTheDocument();
    expect(screen.getByText("3 notes")).toBeInTheDocument();
    expect(screen.getByText("Nursing")).toBeInTheDocument();
    expect(listSubjects).toHaveBeenCalledWith("mine");
    expect(screen.queryByRole("button", { name: "Open note actions" })).not.toBeInTheDocument();

    const title = await screen.findByText("Cell Respiration");
    const card = title.closest("[role='link']");
    expect(card).not.toBeNull();

    fireEvent.click(card as HTMLElement);

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/notes/note-42?from=library&ref=%2Flibrary");
    });
  });

  it("shows a clear button in the search field and resets the query when clicked", async () => {
    render(<LibraryPage />);

    const searchInput = await screen.findByLabelText("Search");
    expect(screen.queryByRole("button", { name: "Clear search" })).not.toBeInTheDocument();

    fireEvent.change(searchInput, { target: { value: "cell" } });
    expect(searchInput).toHaveValue("cell");

    const clearButton = screen.getByRole("button", { name: "Clear search" });
    fireEvent.click(clearButton);

    expect(searchInput).toHaveValue("");
    expect(screen.queryByRole("button", { name: "Clear search" })).not.toBeInTheDocument();
  });

  it("auto-refreshes the note list after a bulk queue so generated notes appear without a manual refresh", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    setBulkQueuedFlash(2);
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-bulk-1",
        title: "Newton's Laws",
        courseProgram: "Physics",
        subject: "Physics",
        tags: ["mechanics"],
        contentPreview: "Generated content...",
        summaryPreview: "Generated summary.",
        visibility: "PRIVATE",
        studyPackId: null,
        studyPackStatus: "GENERATING",
        quizCount: null,
        generatedQuizId: null,
        generatedQuizQuestionCount: null,
        createdAt: "2026-03-20T10:00:00Z",
        updatedAt: "2026-03-21T10:00:00Z",
      },
    ]);
    (listNoteStatuses as jest.Mock).mockResolvedValue([
      { id: "note-bulk-1", studyPackStatus: "GENERATING" },
    ]);

    render(<LibraryPage />);

    expect(await screen.findByRole("heading", { name: "Library" })).toBeInTheDocument();
    // A GENERATING note keeps the poller running, so the paginated list is
    // refreshed beyond the initial load without returning to GET /notes.
    // Assert the pageSize inside the predicate, not after it. `fetchNotesSilently` depends on
    // `items.length`, so the 0 -> 1 transition tears down and re-arms the poller effect; whichever
    // interval instance ticks first decides whether refresh #2 carries pageSize 20 (items.length
    // still 0, so `items.length || LIBRARY_PAGE_SIZE` falls through) or pageSize 1. With the poll
    // interval mocked to 10ms of real time and no fake timers, that race is decided by whether a
    // cold React commit beats the timer — which made the old post-hoc assertion fail under load or
    // on a cold single-test run while passing in a warm full-file run.
    await waitFor(
      () => expect(listLibraryPage).toHaveBeenLastCalledWith(
        expect.objectContaining({page: 0, pageSize: 1}),
      ),
      { timeout: 5000 },
    );
    expect((listLibraryPage as jest.Mock).mock.calls.length).toBeGreaterThanOrEqual(2);
  }, 10000);

  it("skips the enriched note fetch when a status tick has no changes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    const generatingNote = buildNote("note-generating", { studyPackStatus: "GENERATING" });
    (listNotes as jest.Mock).mockResolvedValue([generatingNote]);
    (listNoteStatuses as jest.Mock)
      .mockResolvedValueOnce([{ id: generatingNote.id, studyPackStatus: "GENERATING" }])
      .mockResolvedValueOnce([{ id: generatingNote.id, studyPackStatus: "GENERATING" }])
      .mockRejectedValue(new Error("stop polling after the assertion tick"));

    render(<LibraryPage />);

    await screen.findByText(generatingNote.title);
    await waitFor(() => expect((listNoteStatuses as jest.Mock).mock.calls.length).toBeGreaterThanOrEqual(3));
    expect(listLibraryPage).toHaveBeenCalledTimes(2);
  });

  it("refreshes the enriched note list when a new row appears without a generating status", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    const visibleGeneratingNote = buildNote("note-visible", { studyPackStatus: "GENERATING" });
    (listNotes as jest.Mock).mockResolvedValue([visibleGeneratingNote]);
    (listNoteStatuses as jest.Mock)
      .mockResolvedValueOnce([{ id: visibleGeneratingNote.id, studyPackStatus: "DRAFT" }])
      .mockResolvedValueOnce([
        { id: visibleGeneratingNote.id, studyPackStatus: "DRAFT" },
        { id: "note-new-row", studyPackStatus: "DRAFT" },
      ])
      .mockRejectedValue(new Error("stop polling after the growth tick"));

    render(<LibraryPage />);

    await screen.findByText(visibleGeneratingNote.title);
    await waitFor(() => expect((listNoteStatuses as jest.Mock).mock.calls.length).toBeGreaterThanOrEqual(3));
    expect(listLibraryPage).toHaveBeenCalledTimes(3);
  });

  it("refreshes the enriched note list when a generating status resolves", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    const generatingNote = buildNote("note-resolving", { studyPackStatus: "GENERATING" });
    (listNotes as jest.Mock).mockResolvedValue([generatingNote]);
    (listNoteStatuses as jest.Mock)
      .mockResolvedValueOnce([{ id: generatingNote.id, studyPackStatus: "GENERATING" }])
      .mockResolvedValueOnce([{ id: generatingNote.id, studyPackStatus: "STUDY_PACK_READY" }])
      .mockRejectedValue(new Error("stop polling after the resolution tick"));

    render(<LibraryPage />);

    await screen.findByText(generatingNote.title);
    await waitFor(() => expect(listNoteStatuses).toHaveBeenCalledTimes(2));
    expect(listLibraryPage).toHaveBeenCalledTimes(3);
  });

  it("renders subject stats when the library has enough notes across multiple subjects", async () => {
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 3], ["Chemistry", 2]]),
    );

    render(<LibraryPage />);

    expect(await screen.findByRole("button", { name: "Biology 3" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Chemistry 2" })).toBeInTheDocument();
  });

  it("does not render subject stats below the total note threshold", async () => {
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 2], ["Chemistry", 2]]),
    );

    render(<LibraryPage />);

    await screen.findByText("Note note-1");
    expect(screen.queryByRole("button", { name: "Biology 2" })).not.toBeInTheDocument();
  });

  it("does not render subject stats when only one subject exists", async () => {
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 5]]),
    );

    render(<LibraryPage />);

    await screen.findByText("Note note-1");
    expect(screen.queryByRole("button", { name: "Biology 5" })).not.toBeInTheDocument();
  });

  it("shows an Other chip for subjects beyond the top subjects", async () => {
    // 8 distinct subjects (1 note each) → top 6 chips + Other 2
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([
        ["Anatomy", 1], ["Biology", 1], ["Chemistry", 1], ["Dosage", 1],
        ["Ethics", 1], ["Foundations", 1], ["Genetics", 1], ["History", 1],
      ]),
    );

    render(<LibraryPage />);

    expect(await screen.findByText("Other 2")).toBeInTheDocument();
  });

  it("recomputes subject facets within an active course/program filter", async () => {
    // Faceting: with a course/program filter active, only that program's subjects show.
    (listNotes as jest.Mock).mockResolvedValue([
      ...notesAcrossSubjects([["Pharmacology", 3], ["Community Health Nursing", 2]]).map((n) => ({
        ...n,
        courseProgram: "Nursing",
      })),
      ...notesAcrossSubjects([["Architectural Design", 4]]).map((n) => ({
        ...n,
        courseProgram: "Architecture",
      })),
    ]);
    currentSearch = "cp=Nursing";

    render(<LibraryPage />);

    expect(await screen.findByRole("button", { name: "Pharmacology 3" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Community Health Nursing 2" })).toBeInTheDocument();
    // Architecture subject must NOT appear while filtered to the Nursing program
    expect(screen.queryByRole("button", { name: "Architectural Design 4" })).not.toBeInTheDocument();
  });

  it("applies the subject URL filter when a subject stats chip is clicked", async () => {
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 3], ["Chemistry", 2]]),
    );

    render(<LibraryPage />);

    const biologyChip = await screen.findByRole("button", { name: "Biology 3" });
    replaceMock.mockClear();
    fireEvent.click(biologyChip);

    expect(replaceMock).toHaveBeenCalledWith("/library?subject=Biology", { scroll: false });
  });

  it("hides subject stats when a subject filter is already active", async () => {
    currentSearch = "subject=Biology";
    (listNotes as jest.Mock).mockResolvedValue(
      notesAcrossSubjects([["Biology", 3], ["Chemistry", 2]]),
    );

    render(<LibraryPage />);

    await screen.findByText("Note note-1");
    expect(screen.queryByRole("button", { name: "Biology 3" })).not.toBeInTheDocument();
  });

  it("uses the all-mode session timestamp from the note list for the reviewed label", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "challenge-only-note",
        title: "Challenge Only Review",
        courseProgram: "Nursing",
        subject: "Biology",
        tags: [],
        contentPreview: "Challenge content",
        summaryPreview: "Challenge summary",
        visibility: "PRIVATE",
        studyPackId: "pack-challenge",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 4,
        generatedQuizId: null,
        generatedQuizQuestionCount: null,
        createdAt: "2026-03-20T10:00:00Z",
        updatedAt: "2026-03-21T10:00:00Z",
        lastSessionCompletedAt: new Date().toISOString(),
      },
    ]);

    render(<LibraryPage />);

    await screen.findByText("Challenge Only Review");
    expect(screen.getByText("Last reviewed today")).toBeInTheDocument();
    expect(screen.queryByText("Not reviewed yet")).not.toBeInTheDocument();
  });

  it("reorders selected notes by drag target order", () => {
    expect(reorderSelectedNoteIdsByDrag(
      ["note-99", "note-77", "note-42"],
      "note-77",
      "note-99",
    )).toEqual(["note-77", "note-99", "note-42"]);
  });

  it("offers a split Create menu with Bulk generate for authenticated users and no standalone Select", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
      planType: "FREE",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    expect(screen.queryByRole("button", { name: "Select" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /New Note/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create options" }));
    expect(screen.getByRole("menuitem", { name: /^Note/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Import files/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Bulk generate/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Study Plan/ })).toBeInTheDocument();
  });

  it("opens Bulk generate from the Create menu for non-admins", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
      planType: "FREE",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Create options" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Bulk generate/ }));
    expect(pushMock).toHaveBeenCalledWith("/library/bulk-generate");
  });

  it("shows a queued toast after a bulk-generate redirect", async () => {
    setBulkQueuedFlash(2);

    render(<LibraryPage />);

    expect(await screen.findByText(/Queued 2 notes/)).toBeInTheDocument();
  });

  it("shows failed bulk topics after the poller settles and stashes them for retry", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "admin-1",
      role: "ADMIN",
      profileType: "STUDENT",
    });
    setBulkQueuedFlash(5, "result-1");
    (getBulkGenerationResult as jest.Mock).mockResolvedValueOnce({
      id: "result-1",
      subject: "Maternal Health",
      courseProgram: "Nursing",
      targetProfileType: "BOARD_TAKER",
      makePublic: true,
      requestedCount: 5,
      createdCount: 4,
      failedTopics: ["Prenatal Care", "Labor Stages"],
      quotaBlockedTopics: [],
      createdAt: "2026-06-17T00:00:00Z",
    });

    render(<LibraryPage />);

    expect(await screen.findByText(/4 of 5 notes generated/)).toBeInTheDocument();
    expect(screen.getByText("Prenatal Care")).toBeInTheDocument();
    expect(screen.getByText("Labor Stages")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Retry these" }));

    expect(consumeBulkGenerationRetryStash()).toEqual({
      subject: "Maternal Health",
      courseProgram: "Nursing",
      targetProfileType: "BOARD_TAKER",
      makePublic: true,
      topics: ["Prenatal Care", "Labor Stages"],
    });
    expect(pushMock).toHaveBeenCalledWith("/library/bulk-generate");
  });

  it("shows quota-blocked bulk topics with an upgrade CTA and no retry", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
      planType: "FREE",
    });
    setBulkQueuedFlash(5, "result-quota");
    (getBulkGenerationResult as jest.Mock).mockResolvedValueOnce({
      id: "result-quota",
      subject: "Maternal Health",
      courseProgram: "Nursing",
      targetProfileType: "STUDENT",
      makePublic: false,
      requestedCount: 5,
      createdCount: 3,
      failedTopics: [],
      quotaBlockedTopics: ["Pediatric Milestones", "Immunization Schedule"],
      createdAt: "2026-06-17T00:00:00Z",
    });

    render(<LibraryPage />);

    expect(await screen.findByText(/used all your topic notes this month/i)).toBeInTheDocument();
    expect(screen.getByText("Pediatric Milestones")).toBeInTheDocument();
    expect(screen.getByText("Immunization Schedule")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upgrade to Plus" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Retry these" })).not.toBeInTheDocument();
  });

  it("shows mixed quota and generation failures with distinct actions", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
      planType: "FREE",
    });
    setBulkQueuedFlash(4, "result-mixed");
    (getBulkGenerationResult as jest.Mock).mockResolvedValueOnce({
      id: "result-mixed",
      subject: "Maternal Health",
      courseProgram: "Nursing",
      targetProfileType: "STUDENT",
      makePublic: false,
      requestedCount: 4,
      createdCount: 2,
      failedTopics: ["Broken Prompt"],
      quotaBlockedTopics: ["Over Limit Topic"],
      createdAt: "2026-06-17T00:00:00Z",
    });

    render(<LibraryPage />);

    expect(await screen.findByText("Broken Prompt")).toBeInTheDocument();
    expect(screen.getByText("Over Limit Topic")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upgrade to Plus" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry these" })).toBeInTheDocument();
  });

  it("renders no bulk failure banner for zero-failure results or missing receipts", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "admin-1",
      role: "ADMIN",
      profileType: "STUDENT",
    });
    setBulkQueuedFlash(2, "result-success");
    (getBulkGenerationResult as jest.Mock).mockResolvedValueOnce({
      id: "result-success",
      subject: "Maternal Health",
      courseProgram: "Nursing",
      targetProfileType: "STUDENT",
      makePublic: false,
      requestedCount: 2,
      createdCount: 2,
      failedTopics: [],
      quotaBlockedTopics: [],
      createdAt: "2026-06-17T00:00:00Z",
    });

    const { unmount } = render(<LibraryPage />);

    await waitFor(() => expect(getBulkGenerationResult).toHaveBeenCalledWith("result-success"));
    expect(screen.queryByText(/couldn't be generated/)).not.toBeInTheDocument();

    unmount();
    pushMock.mockReset();
    replaceMock.mockReset();
    setBulkQueuedFlash(2, "result-missing");
    (getBulkGenerationResult as jest.Mock).mockRejectedValue(new Error("not found"));

    render(<LibraryPage />);

    await waitFor(() => expect(getBulkGenerationResult).toHaveBeenCalledWith("result-missing"));
    expect(screen.queryByText(/couldn't be generated/)).not.toBeInTheDocument();
    expect(await screen.findByText("Cell Respiration")).toBeInTheDocument();
  });

  it("creates a Study Plan from notes selected in the Library", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    (createCollection as jest.Mock).mockResolvedValue({ id: "collection-1", title: "Finals Plan" });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Create options" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Study Plan/ }));

    expect(await screen.findByText("Pick notes for your new Study Plan")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));
    fireEvent.click(screen.getByLabelText("Select Zygote Review"));
    fireEvent.click(screen.getByRole("button", { name: "Create Study Plan" }));

    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByPlaceholderText("Study Plan title"), {
      target: { value: "Finals Plan" },
    });
    fireEvent.change(within(dialog).getByPlaceholderText("Optional context for this study plan"), {
      target: { value: "Cram set for finals" },
    });
    fireEvent.click(within(dialog).getByRole("button", { name: "Create Study Plan" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({
        title: "Finals Plan",
        description: "Cram set for finals",
        noteIds: ["note-42", "note-99"],
      });
    });
    expect(pushMock).toHaveBeenCalledWith("/collections/collection-1");
  });

  it("selects all filtered notes with select-all when building a Study Plan", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    (createCollection as jest.Mock).mockResolvedValue({ id: "collection-3", title: "All Notes Plan" });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Create options" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Study Plan/ }));

    await screen.findByText("Pick notes for your new Study Plan");
    fireEvent.click(screen.getByRole("button", { name: /Select all/ }));
    fireEvent.click(screen.getByRole("button", { name: "Create Study Plan" }));

    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByPlaceholderText("Study Plan title"), {
      target: { value: "All Notes Plan" },
    });
    fireEvent.click(within(dialog).getByRole("button", { name: "Create Study Plan" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalled();
    });
    const call = (createCollection as jest.Mock).mock.calls[0][0];
    expect(call.noteIds).toHaveLength(3);
    expect(call.noteIds).toEqual(expect.arrayContaining(["note-42", "note-99", "note-77"]));
  });

  it("selects unloaded matching ids, reports the quiz-count caveat, and warns when capped", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({id: "teacher-1", role: "USER", profileType: "TEACHER"});
    (listLibraryMatchingIds as jest.Mock).mockResolvedValue({
      noteIds: ["note-99", "unloaded-note"],
      totalMatching: 1200,
      truncated: true,
    });

    render(<LibraryPage />);
    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", {name: "Create options"}));
    fireEvent.click(screen.getByRole("menuitem", {name: /Lesson Plan/}));
    fireEvent.click(screen.getByRole("button", {name: /Select all/}));

    expect(await screen.findByText(/Quiz-ready count is based on the 1 of 2 selected notes currently loaded/)).toBeInTheDocument();
    expect(screen.getByText("Selection was capped at the first 1,000 matching notes.")).toBeInTheDocument();
    expect(screen.getByText(/2 notes selected/)).toBeInTheDocument();
    expect(screen.getByRole("button", {name: "Build exam"})).toBeEnabled();
  });

  it("keeps selection unchanged and shows feedback when select-all fails", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({id: "student-1", role: "USER", profileType: "STUDENT"});
    (listLibraryMatchingIds as jest.Mock).mockRejectedValue(new Error("network"));
    render(<LibraryPage />);
    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", {name: "Create options"}));
    fireEvent.click(screen.getByRole("menuitem", {name: /Study Plan/}));
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));
    fireEvent.click(screen.getByRole("button", {name: /Select all/}));

    expect(await screen.findByText("Could not select all matching notes.")).toBeInTheDocument();
    expect(screen.getByText(/1 note selected/)).toBeInTheDocument();
  });

  it("allows creating an empty Study Plan with no notes selected", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    (createCollection as jest.Mock).mockResolvedValue({ id: "collection-2", title: "Empty Plan" });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Create options" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Study Plan/ }));
    fireEvent.click(await screen.findByRole("button", { name: "Create Study Plan" }));

    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByPlaceholderText("Study Plan title"), {
      target: { value: "Empty Plan" },
    });
    fireEvent.click(within(dialog).getByRole("button", { name: "Create Study Plan" }));

    await waitFor(() => {
      expect(createCollection).toHaveBeenCalledWith({ title: "Empty Plan", description: null, noteIds: [] });
    });
  });

  it("lets teachers build an exam from the same selection", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Create options" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Lesson Plan/ }));
    fireEvent.click(screen.getByLabelText("Select Zygote Review"));
    fireEvent.click(screen.getByRole("button", { name: "Build exam" }));

    expect(pushMock).toHaveBeenCalledWith("/library/exam-builder?notes=note-99");
  });

  it("hides Quiz Ready filter and badges for student profiles", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await openMoreFilters();

    expect(screen.queryByRole("button", { name: "Quiz Ready" })).not.toBeInTheDocument();
    expect(screen.queryByText("Quiz Ready")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Study Pack Ready" })).toBeInTheDocument();
  });

  it("hides Quiz Ready filter and badges for board taker profiles", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "board-1",
      role: "USER",
      profileType: "BOARD_EXAM",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await openMoreFilters();

    expect(screen.queryByRole("button", { name: "Quiz Ready" })).not.toBeInTheDocument();
    expect(screen.queryByText("Quiz Ready")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Study Pack Ready" })).toBeInTheDocument();
  });

  it("filters notes by subject from the more filters modal", async () => {
    render(<LibraryPage />);

    expect(await screen.findByText("Cell Respiration")).toBeInTheDocument();
    expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();

    await openMoreFilters();
    selectSubjectFilter("Biology");

    await waitFor(() => {
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
      expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
      expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
    });
  });

  it("filters notes by readiness chips", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await openMoreFilters();

    expect(screen.getByRole("button", { name: "Draft" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Draft" }));

    await waitFor(() => {
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
      expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
      expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "Quiz Ready" }));

    await waitFor(() => {
      expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
      expect(screen.getByText("2 notes matching your filters")).toBeInTheDocument();
      expect(screen.getByText("Zygote Review")).toBeInTheDocument();
      expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "Study Pack Ready" }));

    await waitFor(() => {
      expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
      expect(screen.getByText("Zygote Review")).toBeInTheDocument();
      expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
    });
  });

  it("shows draft empty state copy when no draft notes match", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-ready",
        title: "Ready Note",
        courseProgram: "Nursing",
        subject: "Biology",
        tags: ["review"],
        contentPreview: "Ready content",
        summaryPreview: "Ready summary",
        visibility: "PRIVATE",
        studyPackId: "pack-ready",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        generatedQuizId: null,
        generatedQuizQuestionCount: null,
        createdAt: "2026-03-20T10:00:00Z",
        updatedAt: "2026-03-21T10:00:00Z",
      },
    ]);

    render(<LibraryPage />);

    await screen.findByText("Ready Note");
    await openMoreFilters();
    fireEvent.click(screen.getByRole("button", { name: "Draft" }));

    await waitFor(() => {
      expect(screen.queryByText("Ready Note")).not.toBeInTheDocument();
      expect(screen.getByText("No draft notes")).toBeInTheDocument();
      expect(screen.getByText("No draft notes — you've generated Study Packs for everything in your library.")).toBeInTheDocument();
    });
  });

  it("shows Quiz Ready filter and badges for teacher profiles", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await openMoreFilters();

    expect(screen.getByRole("button", { name: "Quiz Ready" })).toBeInTheDocument();
    expect(screen.getAllByText("Quiz Ready").length).toBeGreaterThanOrEqual(2);
  });

  it("clears hidden Quiz Ready filter after switching away from teacher profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    const { rerender } = render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await openMoreFilters();
    fireEvent.click(screen.getByRole("button", { name: "Quiz Ready" }));

    await waitFor(() => expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument());

    (getAuthUser as jest.Mock).mockReturnValue({
      id: "student-1",
      role: "USER",
      profileType: "STUDENT",
    });
    rerender(<LibraryPage />);

    await waitFor(() => {
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
    });
    await openMoreFilters();
    expect(screen.queryByRole("button", { name: "Quiz Ready" })).not.toBeInTheDocument();
  });

  it("filters subjects from the searchable subject field", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await openMoreFilters();

    fireEvent.focus(screen.getAllByPlaceholderText("All")[0]);
    fireEvent.change(screen.getAllByPlaceholderText("Search subjects...")[0], {
      target: { value: "pha" },
    });
    fireEvent.mouseDown(screen.getAllByRole("button", { name: "Pharmacy" })[0]);

    await waitFor(() => {
      expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
      expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
      expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
    });
  });

  it("searches library notes by title and tags in real time", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "review" },
    });

    await waitFor(() => {
      expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
      expect(screen.getByText("Zygote Review")).toBeInTheDocument();
      expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
    });
  });

  it("shows the save filter action only when a filter is active", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "Loading filters..." })).not.toBeInTheDocument();
    });

    expect(screen.queryByRole("button", { name: "Save filter" })).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "review" },
    });

    expect(screen.getByRole("button", { name: "Save filter" })).toBeInTheDocument();
  });

  it("keeps the save filter dialog open and shows an inline error for a blank name", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save filter" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByText("Name is required")).toBeInTheDocument();
    expect(createSavedLibraryFilter).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "Save filter" })).toBeInTheDocument();
  });

  it("saves the active filter state and adds the saved filter to the picker", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save filter" }));
    fireEvent.change(screen.getByLabelText("Filter name"), {
      target: { value: "Review Notes" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(createSavedLibraryFilter).toHaveBeenCalledWith("Review Notes", { search: "review" });
    });
    expect(await screen.findByText("Filter saved")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Saved filters" }));
    expect(screen.getByRole("button", { name: "Review Notes" })).toBeInTheDocument();
  });

  it("applies a saved filter by replacing the current library URL params", async () => {
    (getSavedLibraryFilters as jest.Mock).mockResolvedValue([
      {
        id: "saved-filter-2",
        name: "Pharmacy Ready",
        filterState: {
          courseProgram: "Pharmacy",
          tags: ["math"],
          status: "STUDY_PACK_READY",
          sort: "TITLE_ASC",
        },
        createdAt: "2026-03-24T00:00:00Z",
      },
    ]);

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "cell" },
    });

    fireEvent.click(await screen.findByRole("button", { name: "Saved filters" }));
    fireEvent.click(screen.getByRole("button", { name: "Pharmacy Ready" }));

    expect(replaceMock).toHaveBeenCalledWith(
      "/library?cp=Pharmacy&tags=math&status=study_pack_ready&sort=title_asc",
      { scroll: false },
    );
    await waitFor(() => {
      expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
      expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
    });
  });

  it("deletes a saved filter from the picker", async () => {
    (getSavedLibraryFilters as jest.Mock).mockResolvedValue([
      {
        id: "saved-filter-3",
        name: "Chemistry Review",
        filterState: { subject: "Chemistry" },
        createdAt: "2026-03-24T00:00:00Z",
      },
    ]);

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(await screen.findByRole("button", { name: "Saved filters" }));
    expect(screen.getByRole("button", { name: "Chemistry Review" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Delete saved filter Chemistry Review" }));

    await waitFor(() => {
      expect(deleteSavedLibraryFilter).toHaveBeenCalledWith("saved-filter-3");
    });
    expect(screen.queryByRole("button", { name: "Chemistry Review" })).not.toBeInTheDocument();
    expect(screen.getByText("Filter deleted")).toBeInTheDocument();
  });

  it("silently hides saved filters when loading them fails", async () => {
    (getSavedLibraryFilters as jest.Mock).mockRejectedValue(new Error("network"));

    render(<LibraryPage />);

    expect(await screen.findByText("Cell Respiration")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "Loading filters..." })).not.toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: "Saved filters" })).not.toBeInTheDocument();
  });

  it("filters notes by a single selected tag", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "energy" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "energy" })[0]);
    applyTopModal();

    await waitFor(() => {
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
      expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
      expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
    });
  });

  it("uses OR logic for multiple tags from the same note", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "math" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "math" })[0]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "med" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "medication" })[0]);
    applyTopModal();

    await waitFor(() => {
      expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
      expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
      expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
    });
  });

  it("uses OR logic for multiple tags from different notes", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "energy" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "energy" })[0]);
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "exam" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "exam" })[0]);
    applyTopModal();

    await waitFor(() => {
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
      expect(screen.getByText("Zygote Review")).toBeInTheDocument();
      expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
    });
  });

  it("combines subject and tag filters while keeping subject restrictive", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    selectSubjectFilter("Biology");
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "cells" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "cells" })[0]);
    applyTopModal();

    await waitFor(() => {
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
      expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
      expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
    });
  });

  it("combines search and tag filters while keeping search restrictive", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "dosage" },
    });
    await openMoreFilters();
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "review" })[0]);
    applyTopModal();

    await waitFor(() => {
      expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
      expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
      expect(screen.getByText("Dosage Calculations")).toBeInTheDocument();
    });
  });

  it("searches tags inside the selector and supports quick deselect from selected tags", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    await openTagSelectorFromFilters();

    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "mito" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "mitochondria" })[0]);
    expect(screen.getByText("Selected tags")).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "mitochondria" })[0]);
    expect(screen.queryByText("Selected tags")).not.toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "mitochondria" })[0]);
    applyTopModal();

    await waitFor(() => {
      expect(screen.getAllByRole("button", { name: "mitochondria" })[0]).toBeInTheDocument();
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
      expect(screen.queryByText("Zygote Review")).not.toBeInTheDocument();
      expect(screen.queryByText("Dosage Calculations")).not.toBeInTheDocument();
    });
  });

  it("shows the empty filtered state and clears filters back to results", async () => {
    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    await openMoreFilters();
    selectSubjectFilter("Biology");
    await openTagSelectorFromFilters();
    fireEvent.change(screen.getByPlaceholderText("Search tags..."), {
      target: { value: "review" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: "review" })[0]);
    applyTopModal();

    await waitFor(() => {
      expect(screen.getByText("No notes match these filters")).toBeInTheDocument();
      expect(screen.getByText("Try adjusting your filters")).toBeInTheDocument();
    });

    fireEvent.click(screen.getAllByRole("button", { name: "Clear filters" })[0]);

    await waitFor(() => {
      expect(screen.getByText("Cell Respiration")).toBeInTheDocument();
      expect(screen.getByText("Zygote Review")).toBeInTheDocument();
    });
  });

  it("sorts notes from the shared sort sheet", async () => {
    const { container } = render(<LibraryPage />);

    await screen.findByText("Cell Respiration");
    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Title (Z-A)" }));

    await waitFor(() => {
      const cardTitles = Array.from(container.querySelectorAll("h3")).map((element) => element.textContent);
      expect(cardTitles.slice(0, 3)).toEqual(["Zygote Review", "Dosage Calculations", "Cell Respiration"]);
    });
  });

  it("shows the derived subject fallback when a note has no explicit subject", async () => {
    render(<LibraryPage />);

    await screen.findByText("Dosage Calculations");

    await openMoreFilters();
    fireEvent.focus(screen.getAllByPlaceholderText("All")[0]);
    expect(screen.getAllByRole("button", { name: "Pharmacy" })[0]).toBeInTheDocument();
    expect(screen.getAllByText("Pharmacy")).not.toHaveLength(0);
  });

  it("shows create-note and demo actions when the library is empty", async () => {
    (listNotes as jest.Mock).mockResolvedValueOnce([]);

    render(<LibraryPage />);

    expect(await screen.findByText("Your note library is empty")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Your First Note" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Try Demo" })).toBeInTheDocument();
  });

  it("shows teacher-framed empty library copy without the demo action", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });
    (listNotes as jest.Mock).mockResolvedValueOnce([]);

    render(<LibraryPage />);

    expect(await screen.findByText("Your note library is empty")).toBeInTheDocument();
    expect(
      screen.getByText("Create a note, generate a quiz, then export it as DOCX for your class. Build multi-note exams with Exam Builder."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Your First Note" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Try Demo" })).not.toBeInTheDocument();
  });

  it("shows teacher Build exam with mixed-readiness selected notes", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Create options" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Lesson Plan/ }));
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));
    fireEvent.click(screen.getByLabelText("Select Zygote Review"));

    expect(screen.getByText(/2 notes selected/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create Lesson Plan" })).toBeInTheDocument();
    expect(screen.getByText("Only quiz-ready notes will be added to the exam.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Build exam" }));

    expect(pushMock).toHaveBeenCalledWith("/library/exam-builder?notes=note-42%2Cnote-99");
  });

  it("disables teacher Build exam when no selected notes are quiz-ready", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });

    render(<LibraryPage />);

    await screen.findByText("Cell Respiration");

    fireEvent.click(screen.getByRole("button", { name: "Create options" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Lesson Plan/ }));
    fireEvent.click(screen.getByLabelText("Select Cell Respiration"));

    expect(screen.getByText("Generate a quiz for at least one note to build an exam.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Build exam" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Create Exam" })).not.toBeInTheDocument();
  });
});
