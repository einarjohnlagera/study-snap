import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PublicLibraryPageClient } from "./public-library-page-client";
import { listPublicNotes, listSubjects } from "@/lib/api";
import { buildPublicLibraryNotePath } from "@/lib/public-note-path";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => ({ id: "user-1" })),
}));

jest.mock("@/lib/api", () => ({
  listPublicNotes: jest.fn(),
  listSubjects: jest.fn(),
}));

describe("PublicLibraryPageClient", () => {
  let currentAuthUser: { id: string } | null = { id: "user-1" };

  beforeAll(() => {
    const authModule = jest.requireMock("@/lib/auth") as { getAuthUser: jest.Mock };
    authModule.getAuthUser.mockImplementation(() => currentAuthUser);
  });

  beforeEach(() => {
    pushMock.mockReset();
    (listPublicNotes as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    currentAuthUser = { id: "user-1" };
    (listSubjects as jest.Mock).mockResolvedValue(["Biology", "Chemistry", "Physics"]);
  });

  it("shows viewer-relative author badges and keeps cards action-free", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-1",
        title: "My Public Note",
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
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "admin-1",
        title: "Official Example",
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
        updatedAt: "2026-03-31T09:00:00Z",
      },
      {
        id: "note-3",
        ownerUserId: "user-2",
        title: "Community Note",
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
    expect(screen.getByRole("link", { name: "By Study Buddy" })).toHaveAttribute("href", "/public/profile/user-2");
    expect(screen.queryByRole("button", { name: "Open note actions" })).not.toBeInTheDocument();
  });

  it("updates the author badge when auth state hydrates after mount", async () => {
    currentAuthUser = null;
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-1",
        title: "My Public Note",
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

  it("sorts public notes from the shared sort sheet", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "Least Copied",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "Least copied preview",
        summaryPreview: "Least copied summary",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        copyCount: 1,
        shareCount: 0,
        viewCount: 2,
        authorDisplayName: "Study Buddy",
        isOfficialAuthor: false,
        isCurrentUser: false,
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "user-3",
        title: "Most Copied",
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
        updatedAt: "2026-03-31T09:00:00Z",
      },
    ]);

    const { container } = render(<PublicLibraryPageClient />);

    await screen.findByText("Least Copied");
    fireEvent.click(screen.getByRole("button", { name: "Open sorting" }));
    fireEvent.click(screen.getByRole("button", { name: "Most Copied" }));

    const cardTitles = Array.from(container.querySelectorAll("h3")).map((element) => element.textContent);
    expect(cardTitles.slice(0, 2)).toEqual(["Most Copied", "Least Copied"]);
    expect(screen.getByText("9 copies")).toBeInTheDocument();
  });

  it("filters public notes from the shared filter sheet", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-1",
        title: "Mine",
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
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "admin-1",
        title: "Official Example",
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
        updatedAt: "2026-03-31T09:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);

    expect(await screen.findByText("Mine")).toBeInTheDocument();
    expect(screen.getByText("Official Example")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open filters" }));
    fireEvent.click(screen.getByLabelText("Official"));

    expect(screen.queryByText("Mine")).not.toBeInTheDocument();
    expect(screen.getByText("Official Example")).toBeInTheDocument();
  });

  it("opens public note detail when a card is clicked", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-2",
        title: "Community Note",
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
