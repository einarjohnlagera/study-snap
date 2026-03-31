import { fireEvent, render, screen } from "@testing-library/react";
import { PublicNoteOwnershipActions } from "./public-note-ownership-actions";

let currentAuthUser: { id: string } | null = null;

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => currentAuthUser),
}));

jest.mock("./public-seo-copy-cta", () => ({
  PublicSeoCopyCta: ({ label }: { label?: string }) => <button type="button">{label ?? "Make a Copy"}</button>,
}));

describe("PublicNoteOwnershipActions", () => {
  const clipboardWriteText = jest.fn();

  beforeEach(() => {
    clipboardWriteText.mockReset();
    currentAuthUser = null;
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: clipboardWriteText },
      configurable: true,
    });
  });

  it("shows owner actions and By You label for the current user's public note", async () => {
    currentAuthUser = { id: "user-1" };

    render(
      <PublicNoteOwnershipActions
        noteId="note-1"
        ownerUserId="user-1"
        official={false}
      />,
    );

    expect(screen.getByText("By You")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open Note" })).toHaveAttribute("href", "/notes/note-1");
    expect(screen.getByRole("button", { name: "Share" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Start Quick Review" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Challenge Quiz" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Share" }));
    expect(screen.getByText("Share this note")).toBeInTheDocument();
    expect(screen.getByText("Shareable URL")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Copy Link" }));
    expect(clipboardWriteText).toHaveBeenCalled();
    expect(await screen.findByRole("button", { name: "Copied" })).toBeInTheDocument();
    expect(screen.getByText("Link copied")).toBeInTheDocument();
  });

  it("shows copy action and community label for a non-owner public note", () => {
    currentAuthUser = { id: "user-1" };

    render(
      <PublicNoteOwnershipActions
        noteId="note-2"
        ownerUserId="user-2"
        official={false}
      />,
    );

    expect(screen.getByText("By Community")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Make a Copy" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Share" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Open Note" })).not.toBeInTheDocument();
  });

  it("shows NoteLib label for official public content", () => {
    render(
      <PublicNoteOwnershipActions
        noteId="note-3"
        ownerUserId="admin-1"
        official
      />,
    );

    expect(screen.getByText("By NoteLib")).toBeInTheDocument();
  });
});
