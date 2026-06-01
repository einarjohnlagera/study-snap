import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import PublicProfilePage, { generateMetadata } from "./page";
import { getServerPublicProfile } from "@/lib/server-public-profiles";

const notFoundMock = jest.fn(() => {
  throw new Error("NEXT_NOT_FOUND");
});
const backMock = jest.fn();
const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  notFound: () => notFoundMock(),
  useRouter: () => ({
    back: backMock,
    push: pushMock,
  }),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => null,
}));

jest.mock("@/lib/api", () => {
  const actual = jest.requireActual("@/lib/api");
  return {
    ...actual,
    getPublicProfile: jest.fn(),
    updatePublicProfileVisibility: jest.fn(),
  };
});

jest.mock("@/lib/server-public-profiles", () => ({
  getServerPublicProfile: jest.fn(),
}));

describe("PublicProfilePage", () => {
  beforeEach(() => {
    notFoundMock.mockClear();
    backMock.mockClear();
    pushMock.mockClear();
    (getServerPublicProfile as jest.Mock).mockReset();
  });

  it("renders the public profile and public notes", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      status: "ok",
      profile: {
        displayName: "Study Buddy",
        bio: "Biology notes and board-review practice.",
        learnerLevel: "BOARD_EXAM_REVIEW",
        courseProgram: "Biology",
        profileType: "TEACHER",
        isOfficial: true,
        publicProfileVisible: true,
        publicNotesCount: 2,
        totalCopies: 7,
        totalShares: 4,
        totalViews: 20,
        publicNotes: [
          {
            noteId: "note-1",
            title: "Plant Cells",
            courseProgram: "Biology",
            subject: "Biology",
            tags: ["cells", "plants"],
            contentPreview: "Plant cells contain chloroplasts and cell walls.",
            summaryPreview: "Plant cells use chloroplasts for photosynthesis.",
            copyCount: 5,
            shareCount: 3,
            viewCount: 12,
            slug: "plant-cells",
          },
          {
            noteId: "note-2",
            title: "Atomic Bonds",
            courseProgram: "Chemistry",
            subject: "Chemistry",
            tags: [],
            contentPreview: "Atoms share and transfer electrons to form bonds.",
            summaryPreview: "",
            copyCount: 2,
            shareCount: 1,
            viewCount: 8,
            slug: "atomic-bonds",
          },
        ],
      },
    });

    render(await PublicProfilePage({ params: Promise.resolve({ userId: "user-1" }) }));

    expect(screen.getByRole("heading", { name: "Study Buddy" })).toBeInTheDocument();
    expect(screen.getByText("Biology notes and board-review practice.")).toBeInTheDocument();
    expect(screen.getByText("Board Exam Review")).toBeInTheDocument();
    expect(screen.getAllByText("Biology")).not.toHaveLength(0);
    expect(screen.getAllByText("Teacher")).not.toHaveLength(0);
    expect(screen.getByText("Official")).toBeInTheDocument();
    expect(screen.getByText("Total Shares")).toBeInTheDocument();
    expect(screen.getByText("Total Views")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Featured note" })).toBeInTheDocument();
    expect(screen.getByText("Public notes")).toBeInTheDocument();
    expect(screen.getAllByText("Plant Cells")).not.toHaveLength(0);
    expect(screen.getAllByText("5 copies")).not.toHaveLength(0);
    expect(screen.getAllByText("12 views")).not.toHaveLength(0);
    expect(screen.getAllByText("Plant cells contain chloroplasts and cell walls.")).not.toHaveLength(0);
    expect(screen.getAllByText("Plant cells use chloroplasts for photosynthesis.")).not.toHaveLength(0);
    expect(screen.getByText("No summary available yet.")).toBeInTheDocument();
    fireEvent.click(screen.getAllByText("Plant Cells")[0].closest("[role='link']") as HTMLElement);
    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/public/library/biology/plant-cells");
    });
    expect(screen.queryByRole("button", { name: "Edit Profile" })).not.toBeInTheDocument();
  });

  it("shows the empty state when the user has no public notes", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      status: "ok",
      profile: {
        displayName: "Quiet Creator",
        bio: null,
        learnerLevel: null,
        courseProgram: null,
        profileType: "STUDENT",
        isOfficial: false,
        publicProfileVisible: true,
        publicNotesCount: 0,
        totalCopies: 0,
        totalShares: 0,
        totalViews: 0,
        publicNotes: [],
      },
    });

    render(await PublicProfilePage({ params: Promise.resolve({ userId: "user-2" }) }));

    expect(screen.getByText("This user has no public notes yet.")).toBeInTheDocument();
  });

  it("links to the creator-filtered public library when capped notes exist", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      status: "ok",
      profile: {
        displayName: "Prolific Creator",
        username: "einarjohn",
        bio: null,
        learnerLevel: null,
        courseProgram: null,
        profileType: "STUDENT",
        isOfficial: false,
        publicProfileVisible: true,
        publicNotesCount: 23,
        totalCopies: 0,
        totalShares: 0,
        totalViews: 0,
        publicNotes: [
          {
            noteId: "note-1",
            title: "Visible Note",
            courseProgram: "PNLE",
            subject: "Nursing",
            tags: [],
            contentPreview: "Public note preview.",
            summaryPreview: "",
            copyCount: 0,
            shareCount: 0,
            viewCount: 0,
            slug: "visible-note",
          },
        ],
      },
    });

    render(await PublicProfilePage({ params: Promise.resolve({ userId: "user-1" }) }));

    expect(screen.getByRole("link", { name: "View all 23 notes →" })).toHaveAttribute(
      "href",
      "/public/library?creator=einarjohn",
    );
  });

  it("hides the view-all link when all public notes are already shown", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      status: "ok",
      profile: {
        displayName: "Small Catalog",
        username: "smallcatalog",
        bio: null,
        learnerLevel: null,
        courseProgram: null,
        profileType: "STUDENT",
        isOfficial: false,
        publicProfileVisible: true,
        publicNotesCount: 8,
        totalCopies: 0,
        totalShares: 0,
        totalViews: 0,
        publicNotes: [
          {
            noteId: "note-1",
            title: "Visible Note",
            courseProgram: "PNLE",
            subject: "Nursing",
            tags: [],
            contentPreview: "Public note preview.",
            summaryPreview: "",
            copyCount: 0,
            shareCount: 0,
            viewCount: 0,
            slug: "visible-note",
          },
        ],
      },
    });

    render(await PublicProfilePage({ params: Promise.resolve({ userId: "user-1" }) }));

    expect(screen.queryByRole("link", { name: /View all/i })).not.toBeInTheDocument();
  });

  it("hides the view-all link for legacy profiles without usernames", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      status: "ok",
      profile: {
        displayName: "Legacy Creator",
        username: null,
        bio: null,
        learnerLevel: null,
        courseProgram: null,
        profileType: "STUDENT",
        isOfficial: false,
        publicProfileVisible: true,
        publicNotesCount: 23,
        totalCopies: 0,
        totalShares: 0,
        totalViews: 0,
        publicNotes: [
          {
            noteId: "note-1",
            title: "Visible Note",
            courseProgram: "PNLE",
            subject: "Nursing",
            tags: [],
            contentPreview: "Public note preview.",
            summaryPreview: "",
            copyCount: 0,
            shareCount: 0,
            viewCount: 0,
            slug: "visible-note",
          },
        ],
      },
    });

    render(await PublicProfilePage({ params: Promise.resolve({ userId: "user-1" }) }));

    expect(screen.queryByRole("link", { name: /View all/i })).not.toBeInTheDocument();
  });

  it("shows the private-profile message when the profile is not public", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      status: "private",
    });

    render(await PublicProfilePage({ params: Promise.resolve({ userId: "user-3" }) }));

    expect(screen.getAllByText("This profile is private.")).not.toHaveLength(0);
  });

  it("returns noindex metadata for visible public profile pages", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      status: "ok",
      profile: {
        displayName: "Study Buddy",
        bio: "Biology notes and board-review practice.",
        learnerLevel: "BOARD_EXAM_REVIEW",
        courseProgram: "Biology",
        profileType: "TEACHER",
        isOfficial: false,
        publicProfileVisible: true,
        publicNotesCount: 1,
        totalCopies: 5,
        totalShares: 0,
        totalViews: 0,
        publicNotes: [],
      },
    });

    const metadata = await generateMetadata({
      params: Promise.resolve({ userId: "user-1" }),
    });

    expect(metadata.title).toBe("Study Buddy | NoteLib Public Profile");
    expect(metadata.robots).toEqual({ index: false, follow: true });
    expect(metadata.alternates).toEqual({
      canonical: "https://notelib.app/public/profile/user-1",
    });
  });

  it("returns noindex metadata for private profiles", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      status: "private",
    });

    const metadata = await generateMetadata({
      params: Promise.resolve({ userId: "user-1" }),
    });

    expect(metadata.title).toBe("This Profile Is Private | NoteLib");
    expect(metadata.robots).toEqual({ index: false, follow: false });
  });

  it("does not render missing profiles", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      status: "not_found",
    });

    await expect(
      PublicProfilePage({ params: Promise.resolve({ userId: "missing-user" }) }),
    ).rejects.toThrow("NEXT_NOT_FOUND");
  });
});
