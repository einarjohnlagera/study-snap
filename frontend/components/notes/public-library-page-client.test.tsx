import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { PublicLibraryPageClient } from "./public-library-page-client";
import {
  copyNote,
  listCoursePrograms,
  listNotes,
  listPublicLibraryDiscoverySections,
  listPublicNotes,
  listPublicStudyPlans,
  listSubjects,
  listTags,
  togglePublicNoteLike,
} from "@/lib/api";
import { buildPublicLibraryNotePath } from "@/lib/public-note-path";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const pathnameMock = jest.fn();
let currentSearch = "";
let mobileSheetMatches = false;

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
    replace: replaceMock,
  }),
  usePathname: () => pathnameMock(),
  useSearchParams: () => new URLSearchParams(currentSearch),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => ({ id: "user-1" })),
}));

jest.mock("@/lib/api", () => ({
  copyNote: jest.fn(),
  listCoursePrograms: jest.fn(),
  listNotes: jest.fn(),
  listPublicLibraryDiscoverySections: jest.fn(),
  listPublicNotes: jest.fn(),
  listPublicStudyPlans: jest.fn(),
  listSubjects: jest.fn(),
  listTags: jest.fn(),
  togglePublicNoteLike: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

function createPublicNote(overrides: Record<string, unknown> = {}) {
  return {
    id: "note-1",
    ownerUserId: "user-2",
    title: "Community Note",
    courseProgram: "Engineering",
    targetProfileType: "STUDENT",
    subject: "Physics",
    tags: ["motion"],
    contentPreview: "Community preview",
    summaryPreview: "Community summary",
    visibility: "PUBLIC",
    studyPackId: "pack-1",
    studyPackStatus: "STUDY_PACK_READY",
    quizCount: 2,
    copyCount: 1,
    likeCount: 0,
    shareCount: 0,
    viewCount: 4,
    authorDisplayName: "Study Buddy",
    authorUsername: "studybuddy",
    isOfficialAuthor: false,
    isCurrentUser: false,
    createdAt: "2026-03-30T08:00:00Z",
    updatedAt: "2026-03-31T08:00:00Z",
    likedByCurrentUser: false,
    ...overrides,
  };
}

function publicNoteListResponse(items: ReturnType<typeof createPublicNote>[], total = items.length) {
  (listPublicLibraryDiscoverySections as jest.Mock).mockResolvedValue({
    featured: [],
    popular: [],
    recent: items,
  });
  return {
    items,
    total,
    page: 0,
    pageSize: 20,
    totalMatching: total,
    hasMore: false,
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

describe("PublicLibraryPageClient", () => {
  let currentAuthUser: { id: string; profileType?: "STUDENT" | "BOARD_EXAM" | "TEACHER" } | null = { id: "user-1" };

  beforeAll(() => {
    const authModule = jest.requireMock("@/lib/auth") as { getAuthUser: jest.Mock };
    authModule.getAuthUser.mockImplementation(() => currentAuthUser);
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: jest.fn((query: string) => ({
        matches: query === "(max-width: 639px)" ? mobileSheetMatches : false,
        media: query,
        onchange: null,
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        addListener: jest.fn(),
        removeListener: jest.fn(),
        dispatchEvent: jest.fn(),
      })),
    });
  });

  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    pathnameMock.mockReset();
    pathnameMock.mockReturnValue("/public/library");
    window.localStorage.clear();
    (copyNote as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockReset();
    (listPublicLibraryDiscoverySections as jest.Mock).mockReset();
    (listPublicNotes as jest.Mock).mockReset();
    (listPublicStudyPlans as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    (listTags as jest.Mock).mockReset();
    (togglePublicNoteLike as jest.Mock).mockReset();
    currentAuthUser = { id: "user-1" };
    currentSearch = "";
    mobileSheetMatches = false;
    window.history.replaceState({}, "", "/public/library");
    (listNotes as jest.Mock).mockResolvedValue([]);
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry", "Physics"]);
    (listCoursePrograms as jest.Mock).mockResolvedValue([
      "Engineering",
      "Licensure Examination for Teachers",
      "PNLE",
    ]);
    (listTags as jest.Mock).mockResolvedValue(["cells", "history", "motion"]);
    (listPublicLibraryDiscoverySections as jest.Mock).mockResolvedValue({ featured: [], popular: [], recent: [] });
  });

  it("shows viewer-relative author metadata and subtle save actions on non-owner cards", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-owner",
        ownerUserId: "user-1",
        title: "My Public Note",
        subject: "Biology",
        tags: ["cells"],
        authorDisplayName: "My Notes",
        isCurrentUser: true,
      }),
      createPublicNote({
        id: "note-official",
        ownerUserId: "admin-1",
        title: "Official Example",
        subject: "Chemistry",
        tags: [],
        authorDisplayName: "NoteLib",
        isOfficialAuthor: true,
        viewCount: 8,
      }),
      createPublicNote({
        id: "note-community",
        title: "Community Note",
        subject: "Physics",
        tags: ["motion"],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("By You")).toBeInTheDocument();
    expect(screen.getByTestId("note-count-pill")).toHaveTextContent("3 notes");
    expect(screen.getByText("By NoteLib")).toBeInTheDocument();
    expect(screen.getByText("Official")).toBeInTheDocument();
    expect(screen.getByText("By Study Buddy · @studybuddy")).toBeInTheDocument();
    expect(screen.getByText("8 views")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "By Study Buddy · @studybuddy" })).toHaveAttribute("href", "/public/creator/studybuddy");
    expect(screen.getAllByRole("button", { name: "Add to Library" })).toHaveLength(2);
    expect(screen.queryByRole("button", { name: "Copy to My Library" })).not.toBeInTheDocument();
  });

  it("shows the filtered public note count against the full public baseline", async () => {
    currentSearch = "?search=cinco";
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-history",
        title: "Cinco de Mayo",
        subject: "History",
        tags: ["history"],
      }),
    ], 3));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Cinco de Mayo")).toBeInTheDocument();
    expect(screen.getByTestId("note-count-pill")).toHaveTextContent("1 of 3 notes");
  });

  it("reads the audience filter from the URL and hides any Teacher audience filter", async () => {
    currentAuthUser = { id: "user-1", profileType: "STUDENT" };
    currentSearch = "?audience=student";
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-student",
        title: "Student Note",
        targetProfileType: "STUDENT",
      }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Student Note")).toBeInTheDocument();
    expect(listPublicNotes).toHaveBeenCalledWith(expect.objectContaining({ audience: "STUDENT", tags: [] }));
    expect(screen.queryByRole("button", { name: "Teacher" })).not.toBeInTheDocument();
  });

  it("updates the card like count when a user likes a public note", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-community",
        title: "Community Note",
        likeCount: 2,
      }),
    ]));
    (togglePublicNoteLike as jest.Mock).mockResolvedValue({ liked: true, likeCount: 3 });

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Community Note")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Like note" }));

    await waitFor(() => {
      expect(togglePublicNoteLike).toHaveBeenCalledWith("note-community");
    });

    expect(screen.getByRole("button", { name: "Unlike note" })).toHaveTextContent("3");
  });

  it("hydrates the public library filters from the URL query params", async () => {
    currentSearch = "?search=cinco&subject=history&tag=mexican-history&courseProgram=latin-american-studies&creator=studybuddy&sort=views";
    (listSubjects as jest.Mock).mockResolvedValue(["History"]);
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-history",
        title: "Cinco de Mayo",
        subject: "History",
        courseProgram: "Latin American Studies",
        tags: ["Mexican History"],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByDisplayValue("cinco")).toBeInTheDocument();
    expect(listPublicNotes).toHaveBeenCalledWith(expect.objectContaining({
      courseProgram: "latin-american-studies",
      creator: "studybuddy",
      page: 0,
      pageSize: 20,
      readyOnly: false,
      search: "cinco",
      sort: "views",
      source: [],
      subject: "history",
      tags: ["mexican-history"],
    }));
    expect(await screen.findByText("Subject: History")).toBeInTheDocument();
    expect(screen.getAllByText("Mexican History")).not.toHaveLength(0);
    expect(screen.getByTestId("note-count-pill")).toHaveTextContent("1 of 1 notes");
  });

  it("resolves an arriving slugified courseProgram URL param back to its real display value in the chip", async () => {
    // Regression coverage for the v0.67.1 DashboardCommunityNotesSection fix: confirms the
    // producer side (slugifying before building the URL) actually round-trips through
    // resolvePublicLibraryValueBySlug on arrival, not just that listPublicNotes receives a slug.
    currentSearch = "?tab=notes&courseProgram=medical-surgical-nursing";
    pathnameMock.mockReturnValue("/explore");
    (listCoursePrograms as jest.Mock).mockResolvedValue(["Medical – Surgical Nursing", "PNLE"]);
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "note-1", courseProgram: "Medical – Surgical Nursing" }),
    ]));

    render(<PublicLibraryPageClient basePath="/explore" embedded />);

    expect(await screen.findByText("Course: Medical – Surgical Nursing")).toBeInTheDocument();
    expect(listPublicNotes).toHaveBeenCalledWith(expect.objectContaining({
      courseProgram: "medical-surgical-nursing",
    }));
  });

  it("shows course/program chips from server facets and applies the canonical filter URL", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "pnle-1", courseProgram: "PNLE" }),
      createPublicNote({ id: "pnle-2", courseProgram: "PNLE" }),
      createPublicNote({ id: "pnle-3", courseProgram: "PNLE" }),
      createPublicNote({ id: "let-1", courseProgram: "Licensure Examination for Teachers" }),
      createPublicNote({ id: "let-2", courseProgram: "Licensure Examination for Teachers" }),
      createPublicNote({ id: "engineering-1", courseProgram: "Engineering" }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByRole("button", { name: "PNLE" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Licensure Examination for Teachers" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Engineering" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Licensure Examination for Teachers" }));

    expect(replaceMock).toHaveBeenCalledWith(
      "/public/library?courseProgram=licensure-examination-for-teachers",
      { scroll: false },
    );
  });

  it("keeps Explore tab state when embedded filters update the URL", async () => {
    currentSearch = "tab=notes";
    pathnameMock.mockReturnValue("/explore");
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ courseProgram: "PNLE" }),
    ]));

    render(<PublicLibraryPageClient basePath="/explore" embedded />);

    fireEvent.click(await screen.findByRole("button", { name: "PNLE" }));

    expect(replaceMock).toHaveBeenCalledWith(
      "/explore?tab=notes&courseProgram=pnle",
      { scroll: false },
    );
  });

  it("copies the current filtered public library URL from the share action", async () => {
    const writeTextMock = jest.fn().mockResolvedValue(undefined);
    Object.defineProperty(globalThis.navigator, "clipboard", {
      configurable: true,
      value: {
        writeText: writeTextMock,
      },
    });

    currentSearch = "?search=cinco&subject=history&tag=mexican-history";
    (listSubjects as jest.Mock).mockResolvedValue(["History"]);
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-history",
        title: "Cinco de Mayo",
        subject: "History",
        tags: ["Mexican History"],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Cinco de Mayo")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Share this list" }));

    await waitFor(() => {
      expect(writeTextMock).toHaveBeenCalled();
    });
    expect(writeTextMock.mock.calls[0]?.[0]).toContain("/public/library?search=cinco&subject=history&tag=mexican-history");
    expect(await screen.findByText("Link copied")).toBeInTheDocument();
  });

  it("keeps the main search focused and syncs the URL after a debounce", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        title: "Pyrolysis Basics",
        subject: "Chemistry",
        tags: ["pyro"],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Pyrolysis Basics")).toBeInTheDocument();

    jest.useFakeTimers();
    try {
      const input = screen.getByLabelText("Search");
      input.focus();

      fireEvent.change(input, { target: { value: "pyro" } });

      expect(document.activeElement).toBe(input);
      expect(replaceMock).not.toHaveBeenCalledWith("/public/library?search=pyro", { scroll: false });

      act(() => {
        jest.advanceTimersByTime(250);
      });

      expect(replaceMock).toHaveBeenCalledWith("/public/library?search=pyro", { scroll: false });
      expect(document.activeElement).toBe(input);
    } finally {
      jest.useRealTimers();
    }
  });

  it("keeps prior results visible with a searching indicator during a refetch (no skeleton flash)", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "note-a", title: "Alpha Note", subject: "Physics", tags: [] }),
    ], 1));

    const { rerender } = render(<PublicLibraryPageClient />);
    expect(await screen.findByText("Alpha Note")).toBeInTheDocument();

    let resolveSecond: (value: unknown) => void = () => {};
    (listPublicNotes as jest.Mock).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveSecond = resolve;
      }),
    );

    // A refetch matching the stale item: it must stay visible (no skeleton swap) while the backend is in flight.
    currentSearch = "?search=alpha";
    rerender(<PublicLibraryPageClient />);

    expect(screen.getByText("Alpha Note")).toBeInTheDocument();
    expect(screen.getByLabelText("Search")).toBeInTheDocument();
    expect(screen.getByText("Searching…")).toBeInTheDocument();

    await act(async () => {
      resolveSecond(publicNoteListResponse([
        createPublicNote({ id: "note-a2", title: "Alpha Note Refreshed", subject: "Physics", tags: [] }),
      ], 1));
    });

    expect(await screen.findByText("Alpha Note Refreshed")).toBeInTheDocument();
    expect(screen.queryByText("Searching…")).not.toBeInTheDocument();
  });

  it("does not drop characters typed after a debounced write when the URL echo arrives", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "n1", title: "Shock Basics", subject: "Nursing", tags: [] }),
    ], 1));

    const { rerender } = render(<PublicLibraryPageClient />);
    const input = (await screen.findByLabelText("Search")) as HTMLInputElement;

    jest.useFakeTimers();
    try {
      fireEvent.change(input, { target: { value: "sho" } });
      act(() => {
        jest.advanceTimersByTime(250);
      });
      expect(replaceMock).toHaveBeenCalledWith("/public/library?search=sho", { scroll: false });
    } finally {
      jest.useRealTimers();
    }

    // User keeps typing before the URL/refetch round-trips back.
    fireEvent.change(input, { target: { value: "shock" } });

    // The URL now catches up to our earlier debounced write — its echo must be
    // ignored so the newer characters are not clobbered.
    currentSearch = "?search=sho";
    rerender(<PublicLibraryPageClient />);

    expect(input.value).toBe("shock");
  });

  it("shows a clear button in the search field and resets the query when clicked", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "note-1", title: "Pyrolysis Basics", subject: "Chemistry", tags: ["pyro"] }),
    ]));

    render(<PublicLibraryPageClient />);

    const input = await screen.findByLabelText("Search");
    expect(screen.queryByRole("button", { name: "Clear search" })).not.toBeInTheDocument();

    fireEvent.change(input, { target: { value: "pyro" } });
    expect(input).toHaveValue("pyro");

    fireEvent.click(screen.getByRole("button", { name: "Clear search" }));

    expect(input).toHaveValue("");
    expect(screen.queryByRole("button", { name: "Clear search" })).not.toBeInTheDocument();
  });

  it("updates the author badge when auth state hydrates after mount", async () => {
    currentAuthUser = null;
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        ownerUserId: "user-1",
        title: "My Public Note",
        subject: "Biology",
        tags: ["cells"],
        authorDisplayName: "My Notes",
      }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("By My Notes · @studybuddy")).toBeInTheDocument();

    await act(async () => {
      currentAuthUser = { id: "user-1" };
      globalThis.dispatchEvent(new Event("studysnap-auth-change"));
    });

    expect(await screen.findByText("By You")).toBeInTheDocument();
  });

  it("keeps the card clickable and shows Saved as the copied-state action", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-2",
        title: "Community Note",
        subject: "Physics",
        tags: ["motion"],
      }),
    ]));
    (listNotes as jest.Mock).mockResolvedValue([
      {
        ...createPublicNote({
          id: "copied-note-7",
          ownerUserId: "user-1",
          title: "Copied Community Note",
          visibility: "PRIVATE",
          studyPackId: null,
          studyPackStatus: "DRAFT",
          isCurrentUser: true,
          copiedFromNoteId: "note-2",
          copiedFromPublic: true,
        }),
      },
    ]);

    render(<PublicLibraryPageClient />);

    const savedButton = await screen.findByRole("button", { name: "In Library" });
    expect(savedButton).toBeDisabled();
    expect(screen.queryByText("Already in your library")).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("Community Note").closest("[role='link']") as HTMLElement);

    expect(pushMock).toHaveBeenCalledWith(buildPublicLibraryNotePath({ subject: "Physics", title: "Community Note" }));
    expect(copyNote).not.toHaveBeenCalled();
  });

  it("copies from the card and shows the refined success modal actions", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-9",
        title: "Cell Notes",
        subject: "Biology",
        tags: ["cells"],
        viewCount: 9,
        copyCount: 4,
      }),
    ]));
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-9", studyPackStatus: "STUDY_PACK_READY" });

    render(<PublicLibraryPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add to Library" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-9");
    });

    const dialog = await screen.findByRole("dialog", { name: "Copied to your library" });
    const modal = within(dialog);

    expect(modal.getByText("The note and its Study Pack are now in your library — open it to read, quiz yourself, and track your progress.")).toBeInTheDocument();
    expect(modal.getByRole("button", { name: "View Note" })).toBeInTheDocument();
    expect(modal.queryByRole("button", { name: "Start Review" })).not.toBeInTheDocument();
    expect(modal.getAllByRole("button", { name: "Close" })).toHaveLength(1);

    fireEvent.click(modal.getByRole("button", { name: "View Note" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/copied-note-9?copied=1");
  });

  it("uses a bottom-sheet success surface on mobile", async () => {
    mobileSheetMatches = true;
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-9",
        title: "Cell Notes",
        subject: "Biology",
        tags: ["cells"],
      }),
    ]));
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-9" });

    render(<PublicLibraryPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Add to Library" }));

    const dialog = await screen.findByRole("dialog", { name: "Copied to your library" });
    const modal = within(dialog);

    expect(dialog.className).toContain("self-end");
    expect(dialog.className).toContain("rounded-t-[28px]");
    expect(modal.getByRole("button", { name: "View Note" }).className).toContain("w-full");
    expect(modal.queryByRole("button", { name: "Start Review" })).not.toBeInTheDocument();
    const handle = dialog.querySelector(".h-1\\.5.w-12.rounded-full");
    expect(handle?.parentElement?.className).toContain("mb-4");
  });

  it("requests Most Viewed ordering from the shared sort sheet", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        title: "Most Viewed",
        subject: "Biology",
        tags: ["cells"],
        viewCount: 12,
        copyCount: 1,
      }),
      createPublicNote({
        id: "note-2",
        title: "Most Copied",
        subject: "Biology",
        tags: ["genetics"],
        viewCount: 5,
        copyCount: 9,
        createdAt: "2026-03-29T09:00:00Z",
      }),
    ]));

    render(<PublicLibraryPageClient />);

    await screen.findByText("Most Viewed");
    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Most Viewed" }));

    expect(replaceMock).toHaveBeenCalledWith("/public/library?sort=views", { scroll: false });
    const titles = screen.getAllByRole("heading", { level: 3 }).map((element) => element.textContent);
    expect(titles.slice(0, 2)).toEqual(["Most Viewed", "Most Copied"]);
  });

  it("requests Recommended ordering for filtered results without an explicit sort query", async () => {
    currentSearch = "?search=note";
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-newest",
        title: "Newest Note",
        viewCount: 0,
        copyCount: 0,
        likeCount: 0,
        createdAt: "2026-03-31T08:00:00Z",
      }),
      createPublicNote({
        id: "note-recommended",
        title: "Recommended Note",
        viewCount: 6,
        copyCount: 5,
        likeCount: 2,
        createdAt: "2026-03-01T08:00:00Z",
      }),
    ]));

    render(<PublicLibraryPageClient />);

    await screen.findByText("Recommended Note");
    expect(listPublicNotes).toHaveBeenCalledWith(expect.objectContaining({ search: "note", sort: "recommended" }));
    const titles = screen.getAllByRole("heading", { level: 3 }).map((element) => element.textContent);
    expect(titles.slice(0, 2)).toEqual(["Newest Note", "Recommended Note"]);
    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    expect(screen.getByRole("button", { name: "Recommended" })).toHaveClass("border-blue-600");
  });

  it("keeps explicit sort alternatives selectable in filter mode", async () => {
    currentSearch = "?search=note";
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "note-a", title: "Alpha Note", viewCount: 3, copyCount: 1 }),
      createPublicNote({ id: "note-b", title: "Beta Note", viewCount: 1, copyCount: 5 }),
    ]));

    render(<PublicLibraryPageClient />);

    await screen.findByText("Alpha Note");
    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Newest" }));
    expect(replaceMock).toHaveBeenCalledWith("/public/library?search=note&sort=recent", { scroll: false });

    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Most Copied" }));
    expect(replaceMock).toHaveBeenCalledWith("/public/library?search=note&sort=most_copied", { scroll: false });

    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Title A-Z" }));
    expect(replaceMock).toHaveBeenCalledWith("/public/library?search=note&sort=title", { scroll: false });
  });

  it("shows one official-plan bridge for an active course/program filter", async () => {
    currentSearch = "?courseProgram=engineering";
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "engineering-note", title: "Engineering Note", courseProgram: "Engineering" }),
    ]));
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([
      { id: "plan-1", title: "Engineering Review Set" },
      { id: "plan-2", title: "Engineering Practice Plan" },
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText(/Looking for a full Study Plan for Engineering\?/i)).toBeInTheDocument();
    expect(listPublicStudyPlans).toHaveBeenCalledWith({ courseProgram: "Engineering" });
    expect(screen.getByRole("link", { name: "Browse official plans →" }))
      .toHaveAttribute("href", "/collections/published?ref=/public/library");
  });

  it("keeps the official-plan bridge inside Explore (switches tabs, doesn't leave) when embedded", async () => {
    // Explore is the single owner of authenticated content discovery (AGENTS.md Page
    // Responsibility Rule, locked 2026-07-30) — this bridge must not route an Explore-embedded
    // visitor out to the standalone `/collections/published`.
    currentSearch = "?courseProgram=engineering";
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "engineering-note", title: "Engineering Note", courseProgram: "Engineering" }),
    ]));
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([
      { id: "plan-1", title: "Engineering Review Set" },
    ]);

    render(<PublicLibraryPageClient basePath="/explore" embedded />);

    expect(await screen.findByRole("link", { name: "Browse official plans →" }))
      .toHaveAttribute("href", "/explore");
  });

  it("hides the official-plan bridge when the lookup has no match or fails", async () => {
    currentSearch = "?courseProgram=engineering";
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "engineering-note", title: "Engineering Note", courseProgram: "Engineering" }),
    ]));
    (listPublicStudyPlans as jest.Mock).mockRejectedValue(new Error("Network unavailable"));

    render(<PublicLibraryPageClient />);

    await screen.findByText("Engineering Note");
    await waitFor(() => {
      expect(listPublicStudyPlans).toHaveBeenCalledWith({ courseProgram: "Engineering" });
    });
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.queryByRole("link", { name: "Browse official plans →" })).not.toBeInTheDocument();
  });

  it("forwards source and course/program filters from the advanced filter sheet", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        ownerUserId: "user-1",
        title: "Mine",
        courseProgram: "Nursing",
        subject: "Biology",
        authorDisplayName: "Me",
        isCurrentUser: true,
      }),
      createPublicNote({
        id: "note-2",
        ownerUserId: "admin-1",
        title: "Official Example",
        courseProgram: "Chemistry",
        subject: "Chemistry",
        authorDisplayName: "NoteLib",
        isOfficialAuthor: true,
      }),
      createPublicNote({
        id: "note-3",
        title: "Community Physics",
        courseProgram: "Engineering",
        subject: "Physics",
        tags: ["motion"],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Mine")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    const dialog = await screen.findByRole("dialog", { name: "More Filters" });
    const modal = within(dialog);
    expect(modal.getByText(
      "A note can apply to several programs, so these counts can exceed the note total.",
    )).toBeInTheDocument();
    const courseProgramInput = modal.getByLabelText("Course / Program");
    fireEvent.focus(courseProgramInput);
    fireEvent.change(courseProgramInput, {
      target: { value: "Engineering" },
    });
    fireEvent.mouseDown(modal.getByRole("button", { name: "Engineering" }));
    expect(modal.queryByLabelText("Learner Level")).not.toBeInTheDocument();
    fireEvent.click(modal.getByLabelText("Community"));
    fireEvent.click(modal.getByRole("button", { name: "Apply" }));

    expect(replaceMock).toHaveBeenCalledWith("/public/library?courseProgram=engineering", { scroll: false });
    await waitFor(() => {
      expect(listPublicNotes).toHaveBeenCalledWith(expect.objectContaining({ source: ["community"] }));
    });
  });

  it("forwards Study Pack Ready filtering and restores the unfiltered request when cleared", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "ready-note", title: "Ready note", studyPackStatus: "STUDY_PACK_READY" }),
      createPublicNote({ id: "draft-note", title: "Draft note", studyPackStatus: "DRAFT" }),
    ]));

    render(<PublicLibraryPageClient />);
    expect(await screen.findByText("Draft note")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Study Pack Ready" }));
    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    await waitFor(() => {
      expect(listPublicNotes).toHaveBeenCalledWith(expect.objectContaining({ readyOnly: true }));
    });

    fireEvent.click(screen.getByRole("button", { name: "Clear all" }));
    await waitFor(() => {
      expect(listPublicNotes).toHaveBeenCalledWith(expect.objectContaining({ readyOnly: false }));
    });
  });

  it("applies a subject filter from the filter sheet", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        title: "Biology Note",
        subject: "Biology",
        tags: [],
      }),
      createPublicNote({
        id: "note-2",
        title: "Chemistry Note",
        subject: "Chemistry",
        tags: [],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    await screen.findByText("Biology Note");
    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    const dialog = await screen.findByRole("dialog", { name: "More Filters" });
    const modal = within(dialog);
    const subjectInput = modal.getByLabelText("Subject");
    fireEvent.focus(subjectInput);
    fireEvent.mouseDown(modal.getByRole("button", { name: "Biology" }));
    fireEvent.click(modal.getByRole("button", { name: "Apply" }));

    expect(replaceMock).toHaveBeenCalledWith("/public/library?subject=biology", { scroll: false });
  });

  it("clearing active URL filters returns to the canonical public library route", async () => {
    currentSearch = "?subject=biology&tag=cells&search=cell";
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        title: "Biology Note",
        subject: "Biology",
        tags: ["cells"],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    await screen.findByText("Biology Note");
    fireEvent.click(screen.getByRole("button", { name: "Clear all" }));

    expect(replaceMock).toHaveBeenCalledWith("/public/library", { scroll: false });
  });

  it("filters subjects with search from the filter sheet", async () => {
    (listSubjects as jest.Mock).mockResolvedValue([
      "Biology",
      "Chemistry",
      "Physics",
      "Math",
      "History",
      "Programming",
      "Anatomy",
    ]);
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "note-1", title: "Anatomy Note", subject: "Anatomy", tags: [] }),
      createPublicNote({ id: "note-2", title: "Biology Note", subject: "Biology", tags: [] }),
    ]));

    render(<PublicLibraryPageClient />);

    await screen.findByText("Anatomy Note");
    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    const dialog = await screen.findByRole("dialog", { name: "More Filters" });
    const modal = within(dialog);
    const subjectInput = modal.getByLabelText("Subject");
    fireEvent.focus(subjectInput);
    fireEvent.change(subjectInput, {
      target: { value: "anat" },
    });
    fireEvent.mouseDown(modal.getByRole("button", { name: "Anatomy" }));
    fireEvent.click(modal.getByRole("button", { name: "Apply" }));

    expect(replaceMock).toHaveBeenCalledWith("/public/library?subject=anatomy", { scroll: false });
  });

  it("sends multiple selected tags as an OR-filter request", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        title: "Biology Cells",
        subject: "Biology",
        tags: ["cells", "dna", "lab", "micro", "quiz", "study", "review"],
      }),
      createPublicNote({
        id: "note-2",
        title: "Physics Motion",
        subject: "Physics",
        tags: ["motion"],
      }),
      createPublicNote({
        id: "note-3",
        title: "History Essay",
        subject: "History",
        tags: ["essay"],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    await screen.findByText("Biology Cells");
    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    const filtersDialog = await screen.findByRole("dialog", { name: "More Filters" });
    expect(within(filtersDialog).getByRole("button", { name: "Browse all" })).toBeInTheDocument();
    fireEvent.click(within(filtersDialog).getByRole("button", { name: "Browse all" }));

    const dialog = await screen.findByRole("dialog", { name: "Select tags" });
    const modal = within(dialog);
    const searchInput = modal.getByPlaceholderText("Search tags...");

    searchInput.focus();
    fireEvent.change(searchInput, {
      target: { value: "motion" },
    });
    expect(document.activeElement).toBe(searchInput);

    fireEvent.click(modal.getByRole("button", { name: "motion" }));
    fireEvent.change(searchInput, {
      target: { value: "cells" },
    });
    expect(document.activeElement).toBe(searchInput);
    fireEvent.click(modal.getByRole("button", { name: "cells" }));
    fireEvent.click(modal.getByRole("button", { name: "Apply" }));

    expect(replaceMock).toHaveBeenCalledWith("/public/library?tag=motion&tag=cells", { scroll: false });
  });

  it("keeps tag browsing accessible even when only a few popular tags are visible", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        title: "Single Tag Note",
        subject: "Physics",
        tags: ["motion"],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Single Tag Note")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    const filtersDialog = await screen.findByRole("dialog", { name: "More Filters" });
    fireEvent.click(within(filtersDialog).getByRole("button", { name: "Browse all" }));

    expect(await screen.findByRole("dialog", { name: "Select tags" })).toBeInTheDocument();
  });

  it("shows curated discovery sections without the old browse-by-subject block", async () => {
    const featuredNote = createPublicNote({
      id: "note-1",
      title: "High Engagement Note",
      subject: "Biology",
      tags: ["cells"],
      viewCount: 15,
      copyCount: 8,
    });
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([featuredNote]));
    (listPublicLibraryDiscoverySections as jest.Mock).mockResolvedValue({
      featured: [featuredNote],
      popular: [],
      recent: [],
    });

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("⭐ Featured Notes")).toBeInTheDocument();
    expect(screen.queryByText("📚 Browse by Subject")).not.toBeInTheDocument();
  });

  it("appends paginated browse results without duplicating or reordering loaded notes", async () => {
    currentSearch = "?search=biology";
    (listPublicNotes as jest.Mock)
      .mockResolvedValueOnce({
        items: [createPublicNote({ id: "note-page-1", title: "First page" })],
        total: 3,
        page: 0,
        pageSize: 20,
        totalMatching: 3,
        hasMore: true,
      })
      .mockResolvedValueOnce({
        items: [
          createPublicNote({ id: "note-page-1", title: "First page duplicate" }),
          createPublicNote({ id: "note-page-2", title: "Second page" }),
        ],
        total: 3,
        page: 1,
        pageSize: 20,
        totalMatching: 3,
        hasMore: false,
      });

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("First page")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Load more" }));

    expect(await screen.findByText("Second page")).toBeInTheDocument();
    expect(screen.queryByText("First page duplicate")).not.toBeInTheDocument();
    expect(screen.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent).slice(0, 2))
      .toEqual(["First page", "Second page"]);
    expect(listPublicNotes).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, pageSize: 20 }));
    expect(screen.queryByRole("button", { name: "Load more" })).not.toBeInTheDocument();
  });

  it("does not let an older filter response overwrite a newer result", async () => {
    const olderResponse = deferred<ReturnType<typeof publicNoteListResponse>>();
    (listPublicNotes as jest.Mock).mockResolvedValueOnce(publicNoteListResponse([
      createPublicNote({ id: "initial-note", title: "Initial result" }),
    ]));

    const { rerender } = render(<PublicLibraryPageClient />);
    expect(await screen.findByText("Initial result")).toBeInTheDocument();

    (listPublicNotes as jest.Mock).mockReturnValueOnce(olderResponse.promise);
    currentSearch = "?search=older";
    rerender(<PublicLibraryPageClient />);
    await waitFor(() => {
      expect(listPublicNotes).toHaveBeenCalledWith(expect.objectContaining({ search: "older" }));
    });

    (listPublicNotes as jest.Mock).mockResolvedValueOnce(publicNoteListResponse([
      createPublicNote({ id: "newer-note", title: "Newer result" }),
    ]));
    currentSearch = "?search=newer";
    rerender(<PublicLibraryPageClient />);
    expect(await screen.findByText("Newer result")).toBeInTheDocument();

    await act(async () => {
      olderResponse.resolve({
        items: [createPublicNote({ id: "older-note", title: "Older stale result" })],
        total: 1,
        page: 0,
        pageSize: 20,
        totalMatching: 1,
        hasMore: false,
      });
      await olderResponse.promise;
    });

    expect(screen.getByText("Newer result")).toBeInTheDocument();
    expect(screen.queryByText("Older stale result")).not.toBeInTheDocument();
  });

  it("renders server-ranked discovery sections at the existing 3/5/5 display limits", async () => {
    const featured = Array.from({ length: 6 }, (_, index) => createPublicNote({
      id: `featured-${index}`,
      title: `Featured ${index}`,
    }));
    const popular = Array.from({ length: 6 }, (_, index) => createPublicNote({
      id: `popular-${index}`,
      title: `Popular ${index}`,
    }));
    const recent = Array.from({ length: 6 }, (_, index) => createPublicNote({
      id: `recent-${index}`,
      title: `Recent ${index}`,
    }));
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([...featured, ...popular, ...recent], 18));
    (listPublicLibraryDiscoverySections as jest.Mock).mockResolvedValue({ featured, popular, recent });

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Featured 2")).toBeInTheDocument();
    expect(screen.queryByText("Featured 3")).not.toBeInTheDocument();
    expect(screen.getByText("Popular 4")).toBeInTheDocument();
    expect(screen.queryByText("Popular 5")).not.toBeInTheDocument();
    expect(screen.getByText("Recent 4")).toBeInTheDocument();
    expect(screen.queryByText("Recent 5")).not.toBeInTheDocument();
    expect(listPublicLibraryDiscoverySections).toHaveBeenCalledWith({ audience: undefined });
  });

  it.each([
    ["featured", "featured"],
    ["popular", "popular"],
    ["recent", "recent"],
  ] as const)("paginates the %s discovery section through the backend sort", async (view, sort) => {
    currentSearch = `?view=${view}`;
    (listPublicNotes as jest.Mock).mockResolvedValue({
      items: [createPublicNote({ id: `${view}-note`, title: `${view} section note` })],
      total: 2,
      page: 0,
      pageSize: 20,
      totalMatching: 2,
      hasMore: true,
    });

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText(`${view} section note`)).toBeInTheDocument();
    expect(listPublicNotes).toHaveBeenCalledWith(expect.objectContaining({ page: 0, pageSize: 20, sort }));
    expect(listPublicLibraryDiscoverySections).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Load more" }));
    await waitFor(() => {
      expect(listPublicNotes).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, pageSize: 20, sort }));
    });
  });

  it("sources facet choices from the dedicated endpoints and prioritizes a recently chosen course", async () => {
    (listSubjects as jest.Mock).mockResolvedValue(["Server Subject"]);
    (listCoursePrograms as jest.Mock).mockResolvedValue([
      "Accounting",
      "Algebra",
      "Biology",
      "Chemistry",
      "Engineering",
      "Medicine",
      "Zoology",
    ]);
    (listTags as jest.Mock).mockResolvedValue(["server-tag"]);
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "facet-note", title: "Facet note", subject: "Client-only subject", tags: ["client-only-tag"] }),
    ]));

    render(<PublicLibraryPageClient />);
    expect(await screen.findByText("Facet note")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Zoology" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    const dialog = await screen.findByRole("dialog", { name: "More Filters" });
    const modal = within(dialog);
    fireEvent.focus(modal.getByLabelText("Subject"));
    expect(modal.getByRole("button", { name: "Server Subject" })).toBeInTheDocument();
    expect(modal.queryByRole("button", { name: "Client-only subject" })).not.toBeInTheDocument();
    expect(modal.getByRole("button", { name: "server-tag" })).toBeInTheDocument();
    expect(modal.queryByRole("button", { name: "client-only-tag" })).not.toBeInTheDocument();

    const courseInput = modal.getByLabelText("Course / Program");
    fireEvent.focus(courseInput);
    fireEvent.change(courseInput, { target: { value: "Zoology" } });
    fireEvent.mouseDown(modal.getByRole("button", { name: "Zoology" }));
    fireEvent.click(modal.getByRole("button", { name: "Apply" }));

    expect(screen.getByRole("button", { name: "Zoology" })).toBeInTheDocument();
  });

  it("shows retry controls when discovery or load-more requests fail", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({ id: "discovery-note", title: "Discovery note" }),
    ]));
    (listPublicLibraryDiscoverySections as jest.Mock).mockRejectedValue(new Error("Discovery unavailable"));

    const { rerender } = render(<PublicLibraryPageClient />);
    expect(await screen.findByText("Could not load discovery sections")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();

    currentSearch = "?search=page";
    (listPublicNotes as jest.Mock)
      .mockResolvedValueOnce({
        items: [createPublicNote({ id: "page-note", title: "Page note" })],
        total: 2,
        page: 0,
        pageSize: 20,
        totalMatching: 2,
        hasMore: true,
      })
      .mockRejectedValueOnce(new Error("Next page unavailable"));
    rerender(<PublicLibraryPageClient />);
    expect(await screen.findByText("Page note")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Load more" }));
    expect(await screen.findByText("Next page unavailable")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry load more" })).toBeInTheDocument();
  });

  it("opens public note detail when a card is clicked", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        title: "Community Note",
        subject: "Physics",
        tags: ["motion"],
      }),
    ]));

    render(<PublicLibraryPageClient />);

    const title = await screen.findByText("Community Note");
    fireEvent.click(title.closest("[role='link']") as HTMLElement);

    expect(pushMock).toHaveBeenCalledWith(
      buildPublicLibraryNotePath({ subject: "Physics", title: "Community Note" }),
    );
  });
});
