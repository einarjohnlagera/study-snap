import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PublicNoteAuthorLine, PublicNoteOwnershipActions } from "./public-note-ownership-actions";

let currentAuthUser: { id: string } | null = null;

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => currentAuthUser),
}));

const publicSeoCopyCtaMock = jest.fn(({ label }: { label?: string }) => <button type="button">{label ?? "Make a Copy"}</button>);

jest.mock("./public-seo-copy-cta", () => ({
  PublicSeoCopyCta: (props: {
    label?: string;
    redirectTarget?: "library" | "generate";
  }) => publicSeoCopyCtaMock(props),
}));

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

describe("PublicNoteOwnershipActions", () => {
  const clipboardWriteText = jest.fn();

  beforeEach(() => {
    clipboardWriteText.mockReset();
    publicSeoCopyCtaMock.mockClear();
    currentAuthUser = null;
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: clipboardWriteText },
      configurable: true,
    });
    window.history.replaceState({}, "", "/public/library");
  });

  it("shows owner actions for the current user's public note", async () => {
    currentAuthUser = { id: "user-1" };

    render(
      <PublicNoteOwnershipActions
        noteId="note-1"
        ownerUserId="user-1"
        isCurrentUser
        subject="Biology"
        title="Cell Structure"
      />,
    );

    expect(screen.getByRole("link", { name: "Open Note" })).toHaveAttribute("href", "/notes/note-1");
    expect(screen.getByRole("button", { name: "Share this note" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Start Quick Review" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Challenge Quiz" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Share this note" }));
    expect(screen.getByRole("heading", { name: "Share this note" })).toBeInTheDocument();
    expect(screen.getByText("Shareable URL")).toBeInTheDocument();
    await waitFor(() => {
      expect(clipboardWriteText).toHaveBeenCalledWith(
        `${window.location.origin}/public/library/biology/cell-structure`,
      );
      expect(screen.getByText("Copied ✓")).toBeInTheDocument();
    });
    expect(screen.getAllByText("Link copied to clipboard").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Copy Link" })).not.toBeInTheDocument();
  });

  it("shows By You for the current user's public note author line", () => {
    currentAuthUser = { id: "user-1" };

    render(
      <PublicNoteAuthorLine
        ownerUserId="user-1"
        authorDisplayName="Study Buddy"
        isOfficialAuthor={false}
        isCurrentUser
        subject="Biology"
      />,
    );

    expect(screen.getByRole("link", { name: "By You" })).toHaveAttribute("href", "/public/profile/user-1");
  });

  it("shows copy action and community label for a non-owner public note", () => {
    currentAuthUser = { id: "user-1" };

    render(
      <PublicNoteOwnershipActions
        noteId="note-2"
        ownerUserId="user-2"
        subject="Chemistry"
        title="Atomic Bonds"
      />,
    );

    expect(screen.getByText(/Copy this note to your library and get the full Study Pack instantly/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add to Library" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Copy as editable draft" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Share this note" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Open Note" })).not.toBeInTheDocument();
    expect(publicSeoCopyCtaMock).toHaveBeenCalledWith(
      expect.objectContaining({ label: "Add to Library", includeStudyPack: true }),
    );
  });

  it("shows display name with username and links attribution through the creator route", () => {
    render(
      <PublicNoteAuthorLine
        ownerUserId="user-2"
        authorDisplayName="Study Buddy"
        authorUsername="studybuddy"
        isOfficialAuthor={false}
        isCurrentUser={false}
        subject="Chemistry"
      />,
    );

    expect(screen.getByRole("link", { name: "By Study Buddy · @studybuddy" })).toHaveAttribute(
      "href",
      "/public/creator/studybuddy",
    );
  });

  it("shows NoteLib label and official badge for official public content", () => {
    render(
      <PublicNoteAuthorLine
        ownerUserId="admin-1"
        authorDisplayName="NoteLib"
        isOfficialAuthor
        isCurrentUser={false}
        subject="Biology"
      />,
    );

    expect(screen.getByRole("link", { name: "By NoteLib" })).toHaveAttribute("href", "/public/profile/admin-1");
    expect(screen.getByText("Official")).toBeInTheDocument();
  });
});
