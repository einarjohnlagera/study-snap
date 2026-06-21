import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { PublicLibraryPageClient } from "./public-library-page-client";
import { copyNote, listNotes, listPublicNotes, listSubjects, togglePublicNoteLike } from "@/lib/api";
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
  listNotes: jest.fn(),
  listPublicNotes: jest.fn(),
  listSubjects: jest.fn(),
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
  return { items, total };
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
    (listNotes as jest.Mock).mockReset();
    (listPublicNotes as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    (togglePublicNoteLike as jest.Mock).mockReset();
    currentAuthUser = { id: "user-1" };
    currentSearch = "";
    mobileSheetMatches = false;
    window.history.replaceState({}, "", "/public/library");
    (listNotes as jest.Mock).mockResolvedValue([]);
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry", "Physics"]);
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
    currentSearch = "?search=cinco&subject=history&tag=mexican-history&courseProgram=latin-american-studies&sort=views";
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
      search: "cinco",
      sort: "views",
      subject: "history",
      tags: ["mexican-history"],
    }));
    expect(await screen.findByText("Subject: History")).toBeInTheDocument();
    expect(screen.getAllByText("Mexican History")).not.toHaveLength(0);
    expect(screen.getByTestId("note-count-pill")).toHaveTextContent("1 of 1 notes");
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

  it("sorts public notes by most viewed from the shared sort sheet", async () => {
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

  it("filters public notes from the advanced filter sheet", async () => {
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
    const courseProgramInput = modal.getByLabelText("Course / Program");
    fireEvent.focus(courseProgramInput);
    fireEvent.change(courseProgramInput, {
      target: { value: "Engineering" },
    });
    fireEvent.mouseDown(modal.getByRole("button", { name: "Engineering" }));
    expect(modal.queryByLabelText("Learner Level")).not.toBeInTheDocument();
    fireEvent.click(modal.getByLabelText("Community"));
    fireEvent.click(modal.getByRole("button", { name: "Apply" }));

    expect(screen.queryByText("Mine")).not.toBeInTheDocument();
    expect(screen.queryByText("Official Example")).not.toBeInTheDocument();
    expect(screen.getByText("Community Physics")).toBeInTheDocument();
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

  it("applies OR logic for multiple tag selections from the tag selector", async () => {
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

    expect(screen.getByText("Biology Cells")).toBeInTheDocument();
    expect(screen.getByText("Physics Motion")).toBeInTheDocument();
    expect(screen.queryByText("History Essay")).not.toBeInTheDocument();
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
    (listPublicNotes as jest.Mock).mockResolvedValue(publicNoteListResponse([
      createPublicNote({
        id: "note-1",
        title: "High Engagement Note",
        subject: "Biology",
        tags: ["cells"],
        viewCount: 15,
        copyCount: 8,
      }),
    ]));

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("⭐ Featured Notes")).toBeInTheDocument();
    expect(screen.queryByText("📚 Browse by Subject")).not.toBeInTheDocument();
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
