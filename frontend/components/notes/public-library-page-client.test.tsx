import { act, render, screen, waitFor } from "@testing-library/react";
import { PublicLibraryPageClient } from "./public-library-page-client";
import { listPublicNotes, listSubjects } from "@/lib/api";

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

  it("shows By You, NoteLib, and display-name badges for public notes", async () => {
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
    expect(screen.getByText("My Public Note")).toBeInTheDocument();
    expect(screen.getByText("Official Example")).toBeInTheDocument();
    expect(screen.getByText("Community Note")).toBeInTheDocument();
    expect(screen.getByText("My generated summary preview")).toBeInTheDocument();
    expect(screen.getByText("Official summary preview")).toBeInTheDocument();
    expect(screen.getByText("No summary available yet.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "By Study Buddy" })).toHaveAttribute("href", "/public/profile/user-2");
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
});
