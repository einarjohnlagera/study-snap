import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PublicProfilePageClient } from "./public-profile-page-client";
import { getAuthUser } from "@/lib/auth";
import { getPublicProfile, updatePublicProfileVisibility } from "@/lib/api";

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => {
  const actual = jest.requireActual("@/lib/api");
  return {
    ...actual,
    getPublicProfile: jest.fn(),
    updatePublicProfileVisibility: jest.fn(),
  };
});

const baseProfile = {
  displayName: "Study Buddy",
  profileType: "TEACHER",
  isOfficial: false,
  publicProfileVisible: true,
  publicNotesCount: 1,
  totalCopies: 5,
  publicNotes: [
    {
      noteId: "note-1",
      title: "Plant Cells",
      subject: "Biology",
      tags: ["cells"],
      contentPreview: "Plant cells contain chloroplasts and cell walls.",
      summaryPreview: "Plant cells use chloroplasts for photosynthesis.",
      copyCount: 5,
      slug: "plant-cells",
    },
  ],
} as const;

describe("PublicProfilePageClient", () => {
  const clipboardWriteText = jest.fn();

  beforeEach(() => {
    (getAuthUser as jest.Mock).mockReset();
    (getPublicProfile as jest.Mock).mockReset();
    (updatePublicProfileVisibility as jest.Mock).mockReset();
    clipboardWriteText.mockReset();
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: clipboardWriteText },
      configurable: true,
    });
  });

  it("shows owner-only controls and toggles visibility on the public profile page", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getPublicProfile as jest.Mock).mockResolvedValue(baseProfile);
    (updatePublicProfileVisibility as jest.Mock).mockResolvedValue({
      publicProfileVisible: false,
    });

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "ok", profile: baseProfile }}
      />,
    );

    expect(await screen.findByRole("link", { name: "Edit Profile" })).toHaveAttribute("href", "/profile");
    expect(screen.getByRole("button", { name: "Public" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Share Profile" }));
    await waitFor(() => {
      expect(clipboardWriteText).toHaveBeenCalledWith("http://localhost/public/profile/user-1");
    });

    fireEvent.click(screen.getByRole("button", { name: "Public" }));
    expect(screen.getByText("Hide this profile from other users.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Private/ }));

    await waitFor(() => {
      expect(updatePublicProfileVisibility).toHaveBeenCalledWith({ publicProfileVisible: false });
    });
    expect(await screen.findByText("Public profile is now private.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Private" })).toBeInTheDocument();
    expect(screen.getByText("Plant cells contain chloroplasts and cell walls.")).toBeInTheDocument();
    expect(screen.getByText("Plant cells use chloroplasts for photosynthesis.")).toBeInTheDocument();
  });

  it("does not show owner controls for other viewers", async () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "ok", profile: baseProfile }}
      />,
    );

    expect(screen.queryByRole("link", { name: "Edit Profile" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Public" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Private" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Share Profile" })).toBeInTheDocument();
  });

  it("lets the owner load a private profile and manage it from the public page", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getPublicProfile as jest.Mock).mockResolvedValue({
      ...baseProfile,
      publicProfileVisible: false,
    });

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "private" }}
      />,
    );

    expect(screen.getByText("Loading public profile...")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Study Buddy" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Private" })).toBeInTheDocument();
  });
});
