import { render, screen, waitFor } from "@testing-library/react";
import { PublicNoteAuthorCard } from "./public-note-author-card";
import { getPublicCreatorSummary, getPublicProfileSummary, type PublicProfileSummaryResponse } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  getPublicCreatorSummary: jest.fn(),
  getPublicProfileSummary: jest.fn(),
}));

const publicProfile: PublicProfileSummaryResponse = {
  displayName: "Study Buddy",
  bio: "Biology notes and board-review practice.",
  publicNotesCount: 4,
};

describe("PublicNoteAuthorCard", () => {
  beforeEach(() => {
    (getPublicCreatorSummary as jest.Mock).mockReset();
    (getPublicProfileSummary as jest.Mock).mockReset();
  });

  it("shows public profile details and links to the creator profile", async () => {
    (getPublicCreatorSummary as jest.Mock).mockResolvedValue(publicProfile);

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
    expect(getPublicCreatorSummary).toHaveBeenCalledWith("studybuddy");
  });

  it("falls back to public note attribution when the profile is private", async () => {
    (getPublicProfileSummary as jest.Mock).mockRejectedValue(new Error("Public profile is private"));

    render(<PublicNoteAuthorCard ownerUserId="user-1" authorDisplayName="Study Buddy" />);

    await waitFor(() => {
      expect(getPublicProfileSummary).toHaveBeenCalledWith("user-1");
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
    expect(getPublicCreatorSummary).not.toHaveBeenCalled();
    expect(getPublicProfileSummary).not.toHaveBeenCalled();
  });
});
