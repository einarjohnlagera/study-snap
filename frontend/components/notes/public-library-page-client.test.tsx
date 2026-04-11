import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { PublicLibraryPageClient } from "./public-library-page-client";
import { copyNote, listNotes, listPublicNotes, listSubjects } from "@/lib/api";
import { buildPublicLibraryNotePath } from "@/lib/public-note-path";

const pushMock = jest.fn();
let currentPathname = "/public/library";
let currentSearch = "";

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
  trackAnalyticsEvent: jest.fn(),
}));

describe("PublicLibraryPageClient", () => {
  let currentAuthUser: { id: string } | null = { id: "user-1" };

  beforeAll(() => {
    const authModule = jest.requireMock("@/lib/auth") as { getAuthUser: jest.Mock };
    authModule.getAuthUser.mockImplementation(() => currentAuthUser);
  });

  beforeEach(() => {
    pushMock.mockReset();
    (copyNote as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockReset();
    (listPublicNotes as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    currentAuthUser = { id: "user-1" };
    currentPathname = "/public/library";
    currentSearch = "";
    window.history.replaceState({}, "", "/public/library");
    (listNotes as jest.Mock).mockResolvedValue([]);
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry", "Physics"]);
  });

  it("shows viewer-relative author badges and only action-oriented card CTAs", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-1",
        title: "My Public Note",
        courseProgram: "Nursing",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "My note preview",
        summaryPreview: "My generated summary preview",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 1,
        shareCount: 0,
        viewCount: 2,
        authorDisplayName: "My Notes",
        isOfficialAuthor: false,
        isCurrentUser: true,
        createdAt: "2026-03-30T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "admin-1",
        title: "Official Example",
        courseProgram: "Chemistry",
        learnerLevel: "PROFESSIONAL",
        subject: "Chemistry",
        tags: [],
        contentPreview: "Official preview",
        summaryPreview: "Official summary preview",
        visibility: "PUBLIC",
        studyPackId: "pack-2",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 4,
        copyCount: 5,
        shareCount: 2,
        viewCount: 8,
        authorDisplayName: "NoteLib",
        isOfficialAuthor: true,
        isCurrentUser: false,
        createdAt: "2026-03-29T09:00:00Z",
        updatedAt: "2026-03-31T09:00:00Z",
      },
      {
        id: "note-3",
        ownerUserId: "user-2",
        title: "Community Note",
        courseProgram: "Engineering",
        learnerLevel: "BOARD_EXAM_REVIEW",
        subject: "Physics",
        tags: ["motion"],
        contentPreview: "Community preview",
        summaryPreview: "",
        visibility: "PUBLIC",
        studyPackId: "pack-3",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        copyCount: 0,
        shareCount: 1,
        viewCount: 4,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-28T08:00:00Z",
        updatedAt: "2026-03-31T08:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);

    await waitFor(() => {
      expect(listPublicNotes).toHaveBeenCalled();
    });
    expect(listSubjects).toHaveBeenCalledWith("public");

    expect(await screen.findByText("By You")).toBeInTheDocument();
    expect(screen.getByText("By NoteLib")).toBeInTheDocument();
    expect(screen.getByText("Official")).toBeInTheDocument();
    expect(screen.getByText("By Study Buddy")).toBeInTheDocument();
    expect(screen.getByText("No summary available yet.")).toBeInTheDocument();
    expect(screen.getByText("8 views")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "By Study Buddy" })).toHaveAttribute("href", "/public/profile/user-2");
    expect(screen.queryByRole("button", { name: "Open note actions" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Copy to My Library" })).toHaveLength(2);
    expect(screen.queryByRole("button", { name: "Open Note" })).not.toBeInTheDocument();
  });

  it("updates the author badge when auth state hydrates after mount", async () => {
    currentAuthUser = null;
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-1",
        title: "My Public Note",
        courseProgram: "Nursing",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "My note preview",
        summaryPreview: "My generated summary preview",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 0,
        shareCount: 0,
        viewCount: 0,
        authorDisplayName: "My Notes",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("By My Notes")).toBeInTheDocument();

    await act(async () => {
      currentAuthUser = { id: "user-1" };
      globalThis.dispatchEvent(new Event("studysnap-auth-change"));
    });

    expect(await screen.findByText("By You")).toBeInTheDocument();
  });

  it("shows an already-copied state, keeps the card clickable, and uses View Note for the copied note", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-2",
        ownerUserId: "user-2",
        title: "Community Note",
        courseProgram: "Engineering",
        learnerLevel: "COLLEGE",
        subject: "Physics",
        tags: ["motion"],
        contentPreview: "Community preview",
        summaryPreview: "Community summary",
        visibility: "PUBLIC",
        studyPackId: "pack-3",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        copyCount: 0,
        shareCount: 1,
        viewCount: 4,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-28T08:00:00Z",
        updatedAt: "2026-03-31T08:00:00Z",
      },
    ]);
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "copied-note-7",
        ownerUserId: "user-1",
        title: "Copied Community Note",
        courseProgram: "Engineering",
        learnerLevel: "COLLEGE",
        subject: "Physics",
        tags: ["motion"],
        contentPreview: "Copied preview",
        summaryPreview: "",
        visibility: "PRIVATE",
        studyPackId: null,
        studyPackStatus: "DRAFT",
        quizCount: 0,
        copyCount: 0,
        shareCount: 0,
        viewCount: 0,
        authorDisplayName: "You",
        isOfficialAuthor: false,
        isCurrentUser: true,
        createdAt: "2026-03-30T08:00:00Z",
        updatedAt: "2026-03-31T08:00:00Z",
        copiedFromNoteId: "note-2",
        copiedFromPublic: true,
      },
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Already in your library")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Community Note").closest("[role='link']") as HTMLElement);

    expect(pushMock).toHaveBeenCalledWith(buildPublicLibraryNotePath({ subject: "Physics", title: "Community Note" }));

    pushMock.mockReset();

    fireEvent.click(screen.getByRole("button", { name: "View Note" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/copied-note-7?copied=1");
    expect(copyNote).not.toHaveBeenCalled();
  });

  it("copies from the card and shows the refined success modal actions", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-9",
        ownerUserId: "user-2",
        title: "Cell Notes",
        courseProgram: "Nursing",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "Cell note preview",
        summaryPreview: "Cell summary preview",
        visibility: "PUBLIC",
        studyPackId: "pack-9",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 4,
        shareCount: 1,
        viewCount: 9,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T08:00:00Z",
        updatedAt: "2026-03-31T08:00:00Z",
      },
    ]);
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-9" });

    render(<PublicLibraryPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Copy to My Library" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-9");
    });
    const dialog = await screen.findByRole("dialog", { name: "Copied to your library" });
    const modal = within(dialog);

    expect(modal.getByText("This note is now in your library.")).toBeInTheDocument();
    expect(modal.getByText("Start reviewing now or continue exploring.")).toBeInTheDocument();
    const continueButton = modal.getByRole("button", { name: "Continue" });
    expect(continueButton).toBeInTheDocument();
    expect(continueButton.className).toContain("bg-transparent");
    expect(continueButton.className).toContain("hover:bg-muted/60");
    expect(modal.getByRole("button", { name: "View Note" })).toBeInTheDocument();
    expect(modal.getByRole("button", { name: "Start Review" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Open in My Library" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Quick Review" })).not.toBeInTheDocument();

    expect(dialog.className).toContain("p-6");
    expect(dialog.className).toContain("sm:p-7");
    const actionRow = modal.getByRole("button", { name: "Start Review" }).parentElement;
    expect(actionRow?.className).toContain("flex-col");
    expect(actionRow?.className).toContain("sm:flex-row");
    expect(actionRow?.className).toContain("gap-3");

    fireEvent.click(modal.getByRole("button", { name: "Start Review" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/copied-note-9?copied=1&generate=1&startQuickReview=1");
  });

  it("lets the user continue exploring after a successful copy", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-9",
        ownerUserId: "user-2",
        title: "Cell Notes",
        courseProgram: "Nursing",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "Cell note preview",
        summaryPreview: "Cell summary preview",
        visibility: "PUBLIC",
        studyPackId: "pack-9",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 4,
        shareCount: 1,
        viewCount: 9,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T08:00:00Z",
        updatedAt: "2026-03-31T08:00:00Z",
      },
    ]);
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-9" });

    render(<PublicLibraryPageClient />);

    fireEvent.click(await screen.findByRole("button", { name: "Copy to My Library" }));

    const dialog = await screen.findByRole("dialog", { name: "Copied to your library" });
    const modal = within(dialog);

    fireEvent.click(modal.getByRole("button", { name: "Continue" }));

    await waitFor(() => {
      expect(screen.queryByText("Copied to your library")).not.toBeInTheDocument();
    });
  });

  it("sorts public notes by most viewed from the shared sort sheet", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "Most Viewed",
        courseProgram: "Biology",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "Most viewed preview",
        summaryPreview: "Most viewed summary",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 1,
        shareCount: 0,
        viewCount: 12,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-31T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "user-3",
        title: "Most Copied",
        courseProgram: "Physics",
        learnerLevel: "PROFESSIONAL",
        subject: "Biology",
        tags: ["genetics"],
        contentPreview: "Most copied preview",
        summaryPreview: "Most copied summary",
        visibility: "PUBLIC",
        studyPackId: "pack-2",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 9,
        shareCount: 4,
        viewCount: 5,
        authorDisplayName: "Top Creator",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T09:00:00Z",
        updatedAt: "2026-03-31T09:00:00Z",
      },
    ]);

    const { container } = render(<PublicLibraryPageClient />);

    await screen.findByText("Most Viewed");
    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Most Viewed" }));

    const cardTitles = Array.from(container.querySelectorAll("h3")).map((element) => element.textContent);
    expect(cardTitles.slice(0, 2)).toEqual(["Most Viewed", "Most Copied"]);
    expect(screen.getByText("12 views")).toBeInTheDocument();
    expect(screen.getByText("9 copies")).toBeInTheDocument();
  });

  it("filters public notes from the shared filter sheet", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-1",
        title: "Mine",
        courseProgram: "Nursing",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "Mine preview",
        summaryPreview: "Mine summary",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 1,
        copyCount: 0,
        shareCount: 0,
        viewCount: 0,
        authorDisplayName: "Me",
        isOfficialAuthor: false,
        isCurrentUser: true,
        createdAt: "2026-03-31T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "admin-1",
        title: "Official Example",
        courseProgram: "Chemistry",
        learnerLevel: "PROFESSIONAL",
        subject: "Chemistry",
        tags: [],
        contentPreview: "Official preview",
        summaryPreview: "Official summary",
        visibility: "PUBLIC",
        studyPackId: "pack-2",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        copyCount: 2,
        shareCount: 2,
        viewCount: 2,
        authorDisplayName: "NoteLib",
        isOfficialAuthor: true,
        isCurrentUser: false,
        createdAt: "2026-03-30T09:00:00Z",
        updatedAt: "2026-03-31T09:00:00Z",
      },
      {
        id: "note-3",
        ownerUserId: "user-9",
        title: "Community Physics",
        courseProgram: "Engineering",
        learnerLevel: "BOARD_EXAM_REVIEW",
        subject: "Physics",
        tags: ["motion"],
        contentPreview: "Community preview",
        summaryPreview: "Community summary",
        visibility: "PUBLIC",
        studyPackId: "pack-3",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        copyCount: 1,
        shareCount: 1,
        viewCount: 3,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-29T09:00:00Z",
        updatedAt: "2026-03-31T08:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Mine")).toBeInTheDocument();
    expect(screen.getByText("Official Example")).toBeInTheDocument();
    expect(screen.getByText("Community Physics")).toBeInTheDocument();

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

  // ── Discovery sections ───────────────────────────────────────────────────

  it("shows discovery sections when no filters or sort are active", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "High Engagement Note",
        courseProgram: "Biology",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "Preview of high engagement note",
        summaryPreview: "",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 8,
        shareCount: 2,
        viewCount: 15,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("🔥 Featured Notes")).toBeInTheDocument();
    expect(screen.getByText("High Engagement Note")).toBeInTheDocument();
    expect(screen.getByText("📚 Browse by Subject")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Biology" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "View More" }).length).toBeGreaterThan(0);
  });

  it("limits discovery sections and opens a section view from View More", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "Featured One",
        courseProgram: "Biology",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "Preview one",
        summaryPreview: "Summary one",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 10,
        shareCount: 3,
        viewCount: 20,
        authorDisplayName: "Creator One",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-31T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "user-3",
        title: "Featured Two",
        courseProgram: "Chemistry",
        learnerLevel: "COLLEGE",
        subject: "Chemistry",
        tags: ["atoms"],
        contentPreview: "Preview two",
        summaryPreview: "Summary two",
        visibility: "PUBLIC",
        studyPackId: "pack-2",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        copyCount: 9,
        shareCount: 2,
        viewCount: 18,
        authorDisplayName: "Creator Two",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T10:00:00Z",
        updatedAt: "2026-03-30T10:00:00Z",
      },
      {
        id: "note-3",
        ownerUserId: "user-4",
        title: "Featured Three",
        courseProgram: "Physics",
        learnerLevel: "COLLEGE",
        subject: "Physics",
        tags: ["motion"],
        contentPreview: "Preview three",
        summaryPreview: "Summary three",
        visibility: "PUBLIC",
        studyPackId: "pack-3",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        copyCount: 8,
        shareCount: 1,
        viewCount: 17,
        authorDisplayName: "Creator Three",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-29T10:00:00Z",
        updatedAt: "2026-03-29T10:00:00Z",
      },
      {
        id: "note-4",
        ownerUserId: "user-5",
        title: "Featured Four",
        courseProgram: "Math",
        learnerLevel: "COLLEGE",
        subject: "Math",
        tags: ["algebra"],
        contentPreview: "Preview four",
        summaryPreview: "Summary four",
        visibility: "PUBLIC",
        studyPackId: "pack-4",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        copyCount: 7,
        shareCount: 1,
        viewCount: 16,
        authorDisplayName: "Creator Four",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-28T10:00:00Z",
        updatedAt: "2026-03-28T10:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Featured One")).toBeInTheDocument();
    const featuredSection = screen.getByRole("region", { name: /Featured Notes/ });
    expect(within(featuredSection).getByText("Featured One")).toBeInTheDocument();
    expect(within(featuredSection).getByText("Featured Two")).toBeInTheDocument();
    expect(within(featuredSection).getByText("Featured Three")).toBeInTheDocument();
    expect(within(featuredSection).queryByText("Featured Four")).not.toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "View More" })[0]);
    expect(pushMock).toHaveBeenCalledWith("/public/library?view=featured");
  });

  it("renders a full section view when a discovery view query param is active", async () => {
    currentSearch = "view=recent";
    window.history.replaceState({}, "", "/public/library?view=recent");
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "Newest Note",
        courseProgram: "Biology",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "Newest preview",
        summaryPreview: "Newest summary",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 2,
        shareCount: 0,
        viewCount: 5,
        authorDisplayName: "Creator One",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-31T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "user-3",
        title: "Older Note",
        courseProgram: "Chemistry",
        learnerLevel: "COLLEGE",
        subject: "Chemistry",
        tags: ["atoms"],
        contentPreview: "Older preview",
        summaryPreview: "Older summary",
        visibility: "PUBLIC",
        studyPackId: "pack-2",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        copyCount: 1,
        shareCount: 0,
        viewCount: 2,
        authorDisplayName: "Creator Two",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T10:00:00Z",
        updatedAt: "2026-03-30T10:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText(/🆕 Recently Added/)).toBeInTheDocument();
    expect(screen.getByText("Browse the newest public notes without the rest of the homepage sections competing for space.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back to Discovery" })).toBeInTheDocument();
    expect(screen.queryByText("📚 Browse by Subject")).not.toBeInTheDocument();
  });

  it("hides discovery sections and shows sorted list when sort is changed", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "High Views Note",
        courseProgram: "Biology",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: [],
        contentPreview: "Preview",
        summaryPreview: "",
        visibility: "PUBLIC",
        studyPackId: null,
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: null,
        copyCount: 1,
        shareCount: 0,
        viewCount: 20,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);
    await screen.findByText("🔥 Featured Notes");

    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Most Viewed" }));

    expect(screen.queryByText("🔥 Featured Notes")).not.toBeInTheDocument();
    expect(screen.getByText("High Views Note")).toBeInTheDocument();
  });

  it("hides discovery sections and shows filtered list when a filter is applied", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "Biology Note",
        courseProgram: "Biology",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: [],
        contentPreview: "Biology preview",
        summaryPreview: "",
        visibility: "PUBLIC",
        studyPackId: null,
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: null,
        copyCount: 0,
        shareCount: 0,
        viewCount: 5,
        authorDisplayName: "Tester",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);
    await screen.findByText("🔥 Featured Notes");

    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    fireEvent.change(screen.getByLabelText("Subject"), {
      target: { value: "Biology" },
    });

    expect(screen.queryByText("🔥 Featured Notes")).not.toBeInTheDocument();
    expect(screen.getByText("Biology Note")).toBeInTheDocument();
  });

  it("clicking a subject chip filters the library by that subject", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "Biology Note",
        courseProgram: "Biology",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: [],
        contentPreview: "Biology preview",
        summaryPreview: "",
        visibility: "PUBLIC",
        studyPackId: null,
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: null,
        copyCount: 0,
        shareCount: 0,
        viewCount: 5,
        authorDisplayName: "Tester",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "user-3",
        title: "Chemistry Note",
        courseProgram: "Chemistry",
        learnerLevel: "COLLEGE",
        subject: "Chemistry",
        tags: [],
        contentPreview: "Chemistry preview",
        summaryPreview: "",
        visibility: "PUBLIC",
        studyPackId: null,
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: null,
        copyCount: 0,
        shareCount: 0,
        viewCount: 3,
        authorDisplayName: "Tester2",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-29T10:00:00Z",
        updatedAt: "2026-03-30T10:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);
    await screen.findByText("📚 Browse by Subject");

    fireEvent.click(screen.getByRole("button", { name: "Biology" }));

    // Discovery sections hidden; filtered list shows only Biology note
    expect(screen.queryByText("🔥 Featured Notes")).not.toBeInTheDocument();
    expect(screen.getByText("Biology Note")).toBeInTheDocument();
    expect(screen.queryByText("Chemistry Note")).not.toBeInTheDocument();
  });

  it("does not show Most Popular or Recently Added sections when all notes are in Featured", async () => {
    // With only 2 notes and limit 6, both go into Featured; other sections are empty
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "Note One",
        courseProgram: null,
        learnerLevel: null,
        subject: null,
        tags: [],
        contentPreview: "Preview one",
        summaryPreview: "",
        visibility: "PUBLIC",
        studyPackId: null,
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: null,
        copyCount: 2,
        shareCount: 0,
        viewCount: 5,
        authorDisplayName: "User A",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T10:00:00Z",
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "user-3",
        title: "Note Two",
        courseProgram: null,
        learnerLevel: null,
        subject: null,
        tags: [],
        contentPreview: "Preview two",
        summaryPreview: "",
        visibility: "PUBLIC",
        studyPackId: null,
        studyPackStatus: "DRAFT",
        quizCount: null,
        copyCount: 0,
        shareCount: 0,
        viewCount: 1,
        authorDisplayName: "User B",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-29T10:00:00Z",
        updatedAt: "2026-03-30T10:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);
    await screen.findByText("🔥 Featured Notes");

    expect(screen.queryByText("📈 Most Popular")).not.toBeInTheDocument();
    expect(screen.queryByText("🆕 Recently Added")).not.toBeInTheDocument();
  });

  it("opens public note detail when a card is clicked", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "Community Note",
        courseProgram: "Engineering",
        learnerLevel: "BOARD_EXAM_REVIEW",
        subject: "Physics",
        tags: ["motion"],
        contentPreview: "Community preview",
        summaryPreview: "Community summary",
        visibility: "PUBLIC",
        studyPackId: "pack-3",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        copyCount: 0,
        shareCount: 1,
        viewCount: 4,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        createdAt: "2026-03-30T08:00:00Z",
        updatedAt: "2026-03-31T08:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);

    const title = await screen.findByText("Community Note");
    fireEvent.click(title.closest("[role='link']") as HTMLElement);

    expect(pushMock).toHaveBeenCalledWith(
      buildPublicLibraryNotePath({ subject: "Physics", title: "Community Note" }),
    );
  });
});
