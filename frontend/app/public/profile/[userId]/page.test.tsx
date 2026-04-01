import { render, screen } from "@testing-library/react";
import PublicProfilePage, { generateMetadata } from "./page";
import { getServerPublicProfile } from "@/lib/server-public-profiles";

const notFoundMock = jest.fn(() => {
  throw new Error("NEXT_NOT_FOUND");
});

jest.mock("next/navigation", () => ({
  notFound: () => notFoundMock(),
}));

jest.mock("@/lib/server-public-profiles", () => ({
  getServerPublicProfile: jest.fn(),
}));

describe("PublicProfilePage", () => {
  beforeEach(() => {
    notFoundMock.mockClear();
    (getServerPublicProfile as jest.Mock).mockReset();
  });

  it("renders the public profile and public notes", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      displayName: "Study Buddy",
      profileType: "TEACHER",
      isOfficial: true,
      publicNotesCount: 2,
      totalCopies: 7,
      publicNotes: [
        {
          noteId: "note-1",
          title: "Plant Cells",
          subject: "Biology",
          tags: ["cells", "plants"],
          copyCount: 5,
          slug: "plant-cells",
        },
        {
          noteId: "note-2",
          title: "Atomic Bonds",
          subject: "Chemistry",
          tags: [],
          copyCount: 2,
          slug: "atomic-bonds",
        },
      ],
    });

    render(await PublicProfilePage({ params: Promise.resolve({ userId: "user-1" }) }));

    expect(screen.getByRole("heading", { name: "Study Buddy" })).toBeInTheDocument();
    expect(screen.getAllByText("Teacher")).not.toHaveLength(0);
    expect(screen.getByText("Official")).toBeInTheDocument();
    expect(screen.getByText("Public notes")).toBeInTheDocument();
    expect(screen.getByText("Plant Cells")).toBeInTheDocument();
    expect(screen.getByText("5 copies")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Open Note" })[0]).toHaveAttribute("href", "/public/library/biology/plant-cells");
  });

  it("shows the empty state when the user has no public notes", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      displayName: "Quiet Creator",
      profileType: "STUDENT",
      isOfficial: false,
      publicNotesCount: 0,
      totalCopies: 0,
      publicNotes: [],
    });

    render(await PublicProfilePage({ params: Promise.resolve({ userId: "user-2" }) }));

    expect(screen.getByText("This user has no public notes yet.")).toBeInTheDocument();
  });

  it("returns noindex metadata for v1 public profile pages", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue({
      displayName: "Study Buddy",
      profileType: "TEACHER",
      isOfficial: false,
      publicNotesCount: 1,
      totalCopies: 5,
      publicNotes: [],
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

  it("does not render missing profiles", async () => {
    (getServerPublicProfile as jest.Mock).mockResolvedValue(null);

    await expect(
      PublicProfilePage({ params: Promise.resolve({ userId: "missing-user" }) }),
    ).rejects.toThrow("NEXT_NOT_FOUND");
  });
});
