import { render, screen, waitFor } from "@testing-library/react";
import { PublicNoteAuthorCard } from "./public-note-author-card";
import { getPublicCreatorProfile, getPublicProfile, type PublicProfileResponse } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  getPublicCreatorProfile: jest.fn(),
  getPublicProfile: jest.fn(),
}));

const publicProfile: PublicProfileResponse = {
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
  publicNotesCount: 4,
  totalCopies: 0,
  totalShares: 0,
  totalViews: 0,
  totalProfileShares: 0,
  notesBySubject: [],
  totalPublicSubjectCount: 0,
  publicNotes: [],
};

describe("PublicNoteAuthorCard", () => {
  beforeEach(() => {
    (getPublicCreatorProfile as jest.Mock).mockReset();
    (getPublicProfile as jest.Mock).mockReset();
  });

  it("shows public profile details and links to the creator profile", async () => {
    (getPublicCreatorProfile as jest.Mock).mockResolvedValue(publicProfile);

    render(
      <PublicNoteAuthorCard
        ownerUserId="user-1"
        authorDisplayName="Study Buddy"
        authorUsername="studybuddy"
      />,
    );

    expect(await screen.findByText("Biology notes and board-review practice.")).toBeInTheDocument();
    expect(screen.getByText("4 public notes")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View Study Buddy's public profile" }))
      .toHaveAttribute("href", "/public/creator/studybuddy");
    expect(getPublicCreatorProfile).toHaveBeenCalledWith("studybuddy");
  });

  it("falls back to public note attribution when the profile is private", async () => {
    (getPublicProfile as jest.Mock).mockRejectedValue(new Error("Public profile is private"));

    render(<PublicNoteAuthorCard ownerUserId="user-1" authorDisplayName="Study Buddy" />);

    await waitFor(() => {
      expect(getPublicProfile).toHaveBeenCalledWith("user-1");
    });
    expect(screen.getByText("Study Buddy")).toBeInTheDocument();
    expect(screen.queryByText("Biology notes and board-review practice.")).not.toBeInTheDocument();
    expect(screen.queryByText(/public notes/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View Study Buddy's public profile" }))
      .toHaveAttribute("href", "/public/profile/user-1");
  });

  it("omits the card when the public note has no author identity", () => {
    const { container } = render(<PublicNoteAuthorCard authorDisplayName="Study Buddy" />);

    expect(container).toBeEmptyDOMElement();
    expect(getPublicCreatorProfile).not.toHaveBeenCalled();
    expect(getPublicProfile).not.toHaveBeenCalled();
  });
});
