import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { PublicLibraryPageClient } from "./public-library-page-client";
import { copyNote, listNotes, listPublicNotes, listSubjects, togglePublicNoteLike } from "@/lib/api";
import { buildPublicLibraryNotePath } from "@/lib/public-note-path";

const pushMock = jest.fn();
let currentPathname = "/public/library";
let currentSearch = "";
let mobileSheetMatches = false;

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
  usePathname: () => currentPathname,
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
    learnerLevel: "COLLEGE",
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
    isOfficialAuthor: false,
    isCurrentUser: false,
    createdAt: "2026-03-30T08:00:00Z",
    updatedAt: "2026-03-31T08:00:00Z",
    likedByCurrentUser: false,
    ...overrides,
  };
}

describe("PublicLibraryPageClient", () => {
  let currentAuthUser: { id: string } | null = { id: "user-1" };

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
    (copyNote as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockReset();
    (listPublicNotes as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    (togglePublicNoteLike as jest.Mock).mockReset();
    currentAuthUser = { id: "user-1" };
    currentPathname = "/public/library";
    currentSearch = "";
    mobileSheetMatches = false;
    window.history.replaceState({}, "", "/public/library");
    (listNotes as jest.Mock).mockResolvedValue([]);
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry", "Physics"]);
  });

  it("shows viewer-relative author metadata and subtle save actions on non-owner cards", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
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
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("By You")).toBeInTheDocument();
    expect(screen.getByText("By NoteLib")).toBeInTheDocument();
    expect(screen.getByText("Official")).toBeInTheDocument();
    expect(screen.getByText("By Study Buddy")).toBeInTheDocument();
    expect(screen.getByText("8 views")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "By Study Buddy" })).toHaveAttribute("href", "/public/profile/user-2");
    expect(screen.getAllByRole("button", { name: "Save" })).toHaveLength(2);
    expect(screen.queryByRole("button", { name: "Copy to My Library" })).not.toBeInTheDocument();
  });

  it("updates the card like count when a user likes a public note", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      createPublicNote({
        id: "note-community",
        title: "Community Note",
        likeCount: 2,
      }),
    ]);
    (togglePublicNoteLike as jest.Mock).mockResolvedValue({ liked: true, likeCount: 3 });

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Community Note")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Like note" }));

    await waitFor(() => {
      expect(togglePublicNoteLike).toHaveBeenCalledWith("note-community");
    });

    expect(screen.getByRole("button", { name: "Unlike note" })).toHaveTextContent("3");
  });

  it("updates the author badge when auth state hydrates after mount", async () => {
    currentAuthUser = null;
    (listPublicNotes as jest.Mock).mockResolvedValue([
      createPublicNote({
        id: "note-1",
        ownerUserId: "user-1",
        title: "My Public Note",
        subject: "Biology",
        tags: ["cells"],
        authorDisplayName: "My Notes",
      }),
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("By My Notes")).toBeInTheDocument();

    await act(async () => {
      currentAuthUser = { id: "user-1" };
      globalThis.dispatchEvent(new Event("studysnap-auth-change"));
    });

    expect(await screen.findByText("By You")).toBeInTheDocument();
  });

  it("keeps the card clickable and shows Saved as the copied-state action", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      createPublicNote({
        id: "note-2",
        title: "Community Note",
        subject: "Physics",
        tags: ["motion"],
      }),
    ]);
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

    const savedButton = await screen.findByRole("button", { name: "Saved" });
    expect(savedButton).toBeDisabled();
    expect(screen.queryByText("Already in your library")).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("Community Note").closest("[role='link']") as HTMLElement);

    expect(pushMock).toHaveBeenCalledWith(buildPublicLibraryNotePath({ subject: "Physics", title: "Community Note" }));
    expect(copyNote).not.toHaveBeenCalled();
  });

  it("copies from the card and shows the refined success modal actions", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      createPublicNote({
        id: "note-9",
        title: "Cell Notes",
        subject: "Biology",
        tags: ["cells"],
        viewCount: 9,
        copyCount: 4,
      }),
    ]);
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-9" });

    render(<PublicLibraryPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-9");
    });

    const dialog = await screen.findByRole("dialog", { name: "Copied to your library" });
    const modal = within(dialog);

    expect(modal.getByText("You can start reviewing now or come back later from your library.")).toBeInTheDocument();
    expect(modal.getByRole("button", { name: "View Note" })).toBeInTheDocument();
    expect(modal.getByRole("button", { name: "Start Review" })).toBeInTheDocument();
    expect(modal.getByRole("button", { name: "Close copied to your library" })).toBeInTheDocument();

    fireEvent.click(modal.getByRole("button", { name: "Start Review" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/copied-note-9?copied=1&generate=1&startQuickReview=1");
  });

  it("uses a bottom-sheet success surface on mobile", async () => {
    mobileSheetMatches = true;
    (listPublicNotes as jest.Mock).mockResolvedValue([
      createPublicNote({
        id: "note-9",
        title: "Cell Notes",
        subject: "Biology",
        tags: ["cells"],
      }),
    ]);
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-9" });

    render(<PublicLibraryPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Save" }));

    const dialog = await screen.findByRole("dialog", { name: "Copied to your library" });
    const modal = within(dialog);

    expect(dialog.className).toContain("self-end");
    expect(dialog.className).toContain("rounded-t-[28px]");
    expect(modal.getByRole("button", { name: "View Note" }).className).toContain("w-full");
    expect(modal.getByRole("button", { name: "Start Review" }).className).toContain("w-full");
    const handle = dialog.querySelector(".h-1\\.5.w-12.rounded-full");
    expect(handle?.parentElement?.className).toContain("mb-4");
  });

  it("sorts public notes by most viewed from the shared sort sheet", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
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
    ]);

    render(<PublicLibraryPageClient />);

    await screen.findByText("Most Viewed");
    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Most Viewed" }));

    const titles = screen.getAllByRole("heading", { level: 3 }).map((element) => element.textContent);
    expect(titles.slice(0, 2)).toEqual(["Most Viewed", "Most Copied"]);
  });

  it("filters public notes from the advanced filter sheet", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      createPublicNote({
        id: "note-1",
        ownerUserId: "user-1",
        title: "Mine",
        courseProgram: "Nursing",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        authorDisplayName: "Me",
        isCurrentUser: true,
      }),
      createPublicNote({
        id: "note-2",
        ownerUserId: "admin-1",
        title: "Official Example",
        courseProgram: "Chemistry",
        learnerLevel: "PROFESSIONAL",
        subject: "Chemistry",
        authorDisplayName: "NoteLib",
        isOfficialAuthor: true,
      }),
      createPublicNote({
        id: "note-3",
        title: "Community Physics",
        courseProgram: "Engineering",
        learnerLevel: "BOARD_EXAM_REVIEW",
        subject: "Physics",
        tags: ["motion"],
      }),
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Mine")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    fireEvent.change(screen.getByLabelText("Course / Program"), {
      target: { value: "Engineering" },
    });
    fireEvent.change(screen.getByLabelText("Learner Level"), {
      target: { value: "BOARD_EXAM_REVIEW" },
    });
    fireEvent.click(screen.getByLabelText("Community"));

    expect(screen.queryByText("Mine")).not.toBeInTheDocument();
    expect(screen.queryByText("Official Example")).not.toBeInTheDocument();
    expect(screen.getByText("Community Physics")).toBeInTheDocument();
  });

  it("filters by subject from the horizontal chip rail", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
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
    ]);

    render(<PublicLibraryPageClient />);

    await screen.findByText("Biology Note");
    fireEvent.click(screen.getByRole("button", { name: "Biology" }));

    expect(screen.queryByText("⭐ Featured Notes")).not.toBeInTheDocument();
    expect(screen.getByText("Biology Note")).toBeInTheDocument();
    expect(screen.queryByText("Chemistry Note")).not.toBeInTheDocument();
  });

  it("opens the subject selector from + More and filters subjects with search", async () => {
    (listSubjects as jest.Mock).mockResolvedValue([
      "Biology",
      "Chemistry",
      "Physics",
      "Math",
      "History",
      "Programming",
      "Anatomy",
    ]);
    (listPublicNotes as jest.Mock).mockResolvedValue([
      createPublicNote({ id: "note-1", title: "Anatomy Note", subject: "Anatomy", tags: [] }),
      createPublicNote({ id: "note-2", title: "Biology Note", subject: "Biology", tags: [] }),
    ]);

    render(<PublicLibraryPageClient />);

    await screen.findByText("Anatomy Note");
    fireEvent.click(screen.getByRole("button", { name: "+ More" }));

    const dialog = await screen.findByRole("dialog", { name: "Select subject" });
    fireEvent.change(within(dialog).getByPlaceholderText("Search subjects..."), {
      target: { value: "anat" },
    });
    fireEvent.click(within(dialog).getByRole("button", { name: "Anatomy" }));
    fireEvent.click(within(dialog).getByRole("button", { name: "Apply" }));

    expect(screen.getByText("Anatomy Note")).toBeInTheDocument();
    expect(screen.queryByText("Biology Note")).not.toBeInTheDocument();
  });

  it("applies OR logic for multiple tag selections from the tag selector", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
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
    ]);

    render(<PublicLibraryPageClient />);

    await screen.findByText("Biology Cells");
    fireEvent.click(screen.getByRole("button", { name: "+ More" }));

    const dialog = await screen.findByRole("dialog", { name: "Select tags" });
    const modal = within(dialog);

    fireEvent.change(modal.getByPlaceholderText("Search tags..."), {
      target: { value: "motion" },
    });
    fireEvent.click(modal.getByRole("button", { name: "motion" }));
    fireEvent.change(modal.getByPlaceholderText("Search tags..."), {
      target: { value: "cells" },
    });
    fireEvent.click(modal.getByRole("button", { name: "cells" }));
    fireEvent.click(modal.getByRole("button", { name: "Apply" }));

    expect(screen.getByText("Biology Cells")).toBeInTheDocument();
    expect(screen.getByText("Physics Motion")).toBeInTheDocument();
    expect(screen.queryByText("History Essay")).not.toBeInTheDocument();
  });

  it("shows curated discovery sections without the old browse-by-subject block", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      createPublicNote({
        id: "note-1",
        title: "High Engagement Note",
        subject: "Biology",
        tags: ["cells"],
        viewCount: 15,
        copyCount: 8,
      }),
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("⭐ Featured Notes")).toBeInTheDocument();
    expect(screen.queryByText("📚 Browse by Subject")).not.toBeInTheDocument();
  });

  it("opens public note detail when a card is clicked", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      createPublicNote({
        id: "note-1",
        title: "Community Note",
        subject: "Physics",
        tags: ["motion"],
      }),
    ]);

    render(<PublicLibraryPageClient />);

    const title = await screen.findByText("Community Note");
    fireEvent.click(title.closest("[role='link']") as HTMLElement);

    expect(pushMock).toHaveBeenCalledWith(
      buildPublicLibraryNotePath({ subject: "Physics", title: "Community Note" }),
    );
  });
});
