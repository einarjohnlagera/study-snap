import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { PublicProfilePageClient } from "./public-profile-page-client";
import { getAuthUser } from "@/lib/auth";
import {
  getCreatorImpact,
  getPublicCreatorProfile,
  getPublicProfile,
  trackAnalyticsEvent,
  updatePublicProfileVisibility,
  type CreatorImpactResponse,
  type PublicProfileResponse,
} from "@/lib/api";
import { buildPublicLibraryNotePathFromSlug } from "@/lib/public-note-path";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => {
  const actual = jest.requireActual("@/lib/api");
  return {
    ...actual,
    getCreatorImpact: jest.fn(),
    getPublicProfile: jest.fn(),
    getPublicCreatorProfile: jest.fn(),
    trackAnalyticsEvent: jest.fn(),
    updatePublicProfileVisibility: jest.fn(),
  };
});

const baseProfile: PublicProfileResponse = {
  displayName: "Study Buddy",
  username: "studybuddy",
  bio: "Biology notes and board-review practice.",
  learnerLevel: "BOARD_EXAM_REVIEW",
  courseProgram: "Biology",
  profileType: "TEACHER",
  isOfficial: false,
  publicProfileVisible: true,
  isCurrentUser: false,
  userId: "user-1",
  publicNotesCount: 1,
  totalCopies: 5,
  totalShares: 2,
  totalViews: 9,
  totalProfileShares: 0,
  notesBySubject: [{ subject: "Biology", count: 1 }],
  totalPublicSubjectCount: 1,
  publicNotes: [
    {
      noteId: "note-1",
      title: "Plant Cells",
      courseProgram: "Biology",
      subject: "Biology",
      tags: ["cells"],
      contentPreview: "Plant cells contain chloroplasts and cell walls.",
      summaryPreview: "Plant cells use chloroplasts for photosynthesis.",
      copyCount: 5,
      shareCount: 2,
      viewCount: 9,
      slug: "plant-cells",
    },
  ],
};

const baseImpact: CreatorImpactResponse = {
  distinctLearnersHelped: 3,
  notes: [
    {
      noteId: "note-1",
      title: "Plant Cells",
      distinctLearnersHelped: 3,
      viewCount: 9,
      copyCount: 5,
    },
  ],
};

describe("PublicProfilePageClient", () => {
  const clipboardWriteText = jest.fn();

  beforeEach(() => {
    (getAuthUser as jest.Mock).mockReset();
    (getCreatorImpact as jest.Mock).mockReset();
    (getPublicProfile as jest.Mock).mockReset();
    (getPublicCreatorProfile as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (updatePublicProfileVisibility as jest.Mock).mockReset();
    pushMock.mockReset();
    clipboardWriteText.mockReset();
    (getCreatorImpact as jest.Mock).mockResolvedValue(baseImpact);
    (trackAnalyticsEvent as jest.Mock).mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: clipboardWriteText },
      configurable: true,
    });
  });

  it("shows owner-only profile controls while keeping note cards action-free", async () => {
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
    expect(screen.getByText("Biology notes and board-review practice.")).toBeInTheDocument();
    expect(screen.getByText("@studybuddy")).toBeInTheDocument();
    expect(screen.getByText("Board Exam Review")).toBeInTheDocument();
    expect(screen.getAllByText("Biology")).not.toHaveLength(0);
    expect(screen.getByText("Total Shares")).toBeInTheDocument();
    expect(screen.getByText("Total Views")).toBeInTheDocument();
    expect(screen.getAllByText("1 note")).not.toHaveLength(0);
    expect(screen.getByText("Mostly shares notes in Biology.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Featured note" })).toBeInTheDocument();
    expect(screen.getByText("Featured Note")).toBeInTheDocument();
    expect(screen.getAllByText("5 copies")).not.toHaveLength(0);
    expect(screen.getAllByText("9 views")).not.toHaveLength(0);
    expect(screen.queryByRole("button", { name: "Open note actions" })).not.toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Your Impact" })).toBeInTheDocument();
    expect(screen.getByText("distinct learners helped")).toBeInTheDocument();
    expect(screen.getByText(/Per-note learner counts can therefore add up/)).toBeInTheDocument();
    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledTimes(1);
    });
    expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({
      eventType: "KNOWLEDGE_IMPACT_DASHBOARD_VIEWED",
      entityId: "user-1",
    }));

    fireEvent.click(screen.getByRole("button", { name: "Share Profile" }));
    const dialog = await screen.findByRole("dialog", { name: "Share this profile" });
    const modal = within(dialog);
    expect(screen.getByText(/\/public\/creator\/studybuddy/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Copy Link" }));
    await waitFor(() => {
      expect(clipboardWriteText).toHaveBeenCalledWith("http://localhost/public/creator/studybuddy");
    });
    expect(await screen.findByText("Link copied")).toBeInTheDocument();

    fireEvent.click(modal.getByLabelText("Close"));
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: "Share this profile" })).not.toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "Public" }));
    expect(screen.getByText("Hide this profile from other users.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Private/ }));

    await waitFor(() => {
      expect(updatePublicProfileVisibility).toHaveBeenCalledWith({ publicProfileVisible: false });
    });
    expect(await screen.findByText("Public profile is now private.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Private" })).toBeInTheDocument();
  });

  it("renders the public subject scope and links subject chips into the creator-filtered public library", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);
    const profile = {
      ...baseProfile,
      publicNotesCount: 7,
      notesBySubject: [
        { subject: "Biology", count: 4 },
        { subject: "Chemistry", count: 3 },
      ],
      totalPublicSubjectCount: 2,
    };

    render(
      <PublicProfilePageClient
        userId="studybuddy"
        lookupType="username"
        initialResult={{ status: "ok", profile }}
      />,
    );

    expect(screen.getByText("7 notes across 2 subjects")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Biology" })).toHaveAttribute(
      "href",
      "/public/library?subject=Biology&creator=studybuddy",
    );
    expect(screen.getByRole("link", { name: "Chemistry" })).toHaveAttribute(
      "href",
      "/public/library?subject=Chemistry&creator=studybuddy",
    );
  });

  it("falls back to the note count and hides subject chips when no public subject breakdown exists", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);
    const profile = {
      ...baseProfile,
      publicNotesCount: 0,
      notesBySubject: [],
      totalPublicSubjectCount: 0,
      publicNotes: [],
    };

    render(
      <PublicProfilePageClient
        userId="studybuddy"
        lookupType="username"
        initialResult={{ status: "ok", profile }}
      />,
    );

    expect(screen.getAllByText("0 notes")).not.toHaveLength(0);
    expect(screen.queryByText(/across/)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Biology" })).not.toBeInTheDocument();
  });

  it("shows private confirm modal when owner tries to share a private profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getPublicProfile as jest.Mock).mockResolvedValue({
      ...baseProfile,
      publicProfileVisible: false,
    });
    (updatePublicProfileVisibility as jest.Mock).mockResolvedValue({
      publicProfileVisible: true,
    });

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "private" }}
      />,
    );

    expect(await screen.findByRole("heading", { name: "Study Buddy" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Share Profile" }));
    expect(await screen.findByRole("dialog", { name: "This profile is private" })).toBeInTheDocument();
    expect(screen.getByText(/You need to make this profile public before sharing/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Make Public & Share" }));
    await waitFor(() => {
      expect(updatePublicProfileVisibility).toHaveBeenCalledWith({ publicProfileVisible: true });
    });
    expect(await screen.findByRole("dialog", { name: "Share this profile" })).toBeInTheDocument();
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
    expect(screen.queryByRole("button", { name: "Open note actions" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Share Profile" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse more notes from Study Buddy →" }))
      .toHaveAttribute("href", "/public/library?creator=studybuddy");
    expect(screen.queryByRole("heading", { name: "Your Impact" })).not.toBeInTheDocument();
    expect(getCreatorImpact).not.toHaveBeenCalled();
    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(expect.objectContaining({
      eventType: "KNOWLEDGE_IMPACT_DASHBOARD_VIEWED",
    }));
  });

  it("shows a retryable impact error without hiding the rest of the owner profile", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getPublicProfile as jest.Mock).mockResolvedValue(baseProfile);
    (getCreatorImpact as jest.Mock)
      .mockRejectedValueOnce(new Error("Network error"))
      .mockResolvedValueOnce(baseImpact);

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "ok", profile: baseProfile }}
      />,
    );

    expect(await screen.findByText("Could not load your impact.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Study Buddy" })).toBeInTheDocument();
    expect(screen.getByText("Total Views")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("distinct learners helped")).toBeInTheDocument();
    expect(getCreatorImpact).toHaveBeenCalledTimes(2);
  });

  it("shows a neutral empty impact state when no downstream learner completed a session", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getPublicProfile as jest.Mock).mockResolvedValue(baseProfile);
    (getCreatorImpact as jest.Mock).mockResolvedValue({
      distinctLearnersHelped: 0,
      notes: [{ ...baseImpact.notes[0], distinctLearnersHelped: 0 }],
    });

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "ok", profile: baseProfile }}
      />,
    );

    expect(await screen.findByText("No learners yet")).toBeInTheDocument();
    expect(screen.queryByText("0%")).not.toBeInTheDocument();
  });

  it("reloads impact data when the owner page is mounted again", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getPublicProfile as jest.Mock).mockResolvedValue(baseProfile);

    const firstRender = render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "ok", profile: baseProfile }}
      />,
    );
    expect(await screen.findByText("distinct learners helped")).toBeInTheDocument();
    firstRender.unmount();

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "ok", profile: baseProfile }}
      />,
    );
    expect(await screen.findByText("distinct learners helped")).toBeInTheDocument();

    expect(getCreatorImpact).toHaveBeenCalledTimes(2);
  });

  it("keeps the closing browse affordance out of the owner's profile view", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getPublicProfile as jest.Mock).mockResolvedValue({ ...baseProfile, isCurrentUser: true });

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "ok", profile: { ...baseProfile, isCurrentUser: true } }}
      />,
    );

    await waitFor(() => {
      expect(getPublicProfile).toHaveBeenCalledWith("user-1");
    });
    expect(screen.queryByRole("link", { name: /Browse more notes from/ })).not.toBeInTheDocument();
  });

  it("lets the owner load a private profile and manage it from the public page", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getPublicProfile as jest.Mock).mockResolvedValue({
      ...baseProfile,
        publicProfileVisible: false,
        totalShares: 0,
        totalViews: 0,
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

  it("does not show a back link for the owner (My Profile is a main page)", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (getPublicProfile as jest.Mock).mockResolvedValue(baseProfile);

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "ok", profile: baseProfile }}
      />,
    );

    expect(screen.queryByRole("link", { name: "Public Library" })).not.toBeInTheDocument();
  });

  it("shows a back link to Public Library for non-owners and opens notes from whole-card clicks", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "other-user" });
    (getPublicProfile as jest.Mock).mockResolvedValue(baseProfile);

    render(
      <PublicProfilePageClient
        userId="user-1"
        initialResult={{ status: "ok", profile: baseProfile }}
      />,
    );

    const backLink = screen.getByRole("link", { name: "Public Library" });
    expect(backLink).toHaveAttribute("href", "/public/library");

    const noteTitles = screen.getAllByText("Plant Cells");
    expect(noteTitles).not.toHaveLength(0);
    fireEvent.click(noteTitles[0].closest("[role='link']") as HTMLElement);

    expect(pushMock).toHaveBeenCalledWith(
      buildPublicLibraryNotePathFromSlug({ subject: "Biology", slug: "plant-cells" }),
    );
  });
});
