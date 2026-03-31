import { render, screen, waitFor } from "@testing-library/react";
import { PublicLibraryPageClient } from "./public-library-page-client";
import { listPublicNotes } from "@/lib/api";

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
}));

describe("PublicLibraryPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    (listPublicNotes as jest.Mock).mockReset();
  });

  it("shows By You, NoteLib, and Community badges for public notes", async () => {
    (listPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        ownerUserId: "user-1",
        title: "My Public Note",
        subject: "Biology",
        tags: ["cells"],
        contentPreview: "My note preview",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        official: false,
        updatedAt: "2026-03-31T10:00:00Z",
      },
      {
        id: "note-2",
        ownerUserId: "admin-1",
        title: "Official Example",
        subject: "Chemistry",
        tags: [],
        contentPreview: "Official preview",
        visibility: "PUBLIC",
        studyPackId: "pack-2",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 4,
        official: true,
        updatedAt: "2026-03-31T09:00:00Z",
      },
      {
        id: "note-3",
        ownerUserId: "user-2",
        title: "Community Note",
        subject: "Physics",
        tags: ["motion"],
        contentPreview: "Community preview",
        visibility: "PUBLIC",
        studyPackId: "pack-3",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        official: false,
        updatedAt: "2026-03-31T08:00:00Z",
      },
    ]);

    render(<PublicLibraryPageClient />);

    await waitFor(() => {
      expect(listPublicNotes).toHaveBeenCalled();
    });

    expect(await screen.findByText("By You")).toBeInTheDocument();
    expect(screen.getByText("NoteLib")).toBeInTheDocument();
    expect(screen.getByText("Community")).toBeInTheDocument();
    expect(screen.getByText("My Public Note")).toBeInTheDocument();
    expect(screen.getByText("Official Example")).toBeInTheDocument();
    expect(screen.getByText("Community Note")).toBeInTheDocument();
  });
});
