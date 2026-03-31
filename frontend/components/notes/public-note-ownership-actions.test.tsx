import { fireEvent, render, screen } from "@testing-library/react";
import { PublicNoteOwnershipActions } from "./public-note-ownership-actions";
import { deleteNote } from "@/lib/api";

const pushMock = jest.fn();

let currentAuthUser: { id: string } | null = null;

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => currentAuthUser),
}));

jest.mock("@/lib/api", () => ({
  deleteNote: jest.fn(),
}));

jest.mock("./public-seo-copy-cta", () => ({
  PublicSeoCopyCta: ({ label }: { label?: string }) => <button type="button">{label ?? "Make a Copy"}</button>,
}));

describe("PublicNoteOwnershipActions", () => {
  const clipboardWriteText = jest.fn();

  beforeEach(() => {
    pushMock.mockReset();
    clipboardWriteText.mockReset();
    currentAuthUser = null;
    (deleteNote as jest.Mock).mockReset();
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
        studyPackStatus="STUDY_PACK_READY"
      />,
    );

    expect(screen.getByText("By You")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit Note" })).toHaveAttribute("href", "/notes/note-1");
    expect(screen.getByRole("link", { name: "Start Quick Review" })).toHaveAttribute("href", "/notes/note-1/quick-review");
    expect(screen.getByRole("link", { name: "Challenge Quiz" })).toHaveAttribute("href", "/notes/note-1/challenge-quiz");
    expect(screen.getByRole("button", { name: "Share" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Share" }));
    expect(clipboardWriteText).toHaveBeenCalled();
    expect(await screen.findByRole("button", { name: "Link Copied" })).toBeInTheDocument();
  });

  it("shows copy action and community label for a non-owner public note", () => {
    currentAuthUser = { id: "user-1" };

    render(
      <PublicNoteOwnershipActions
        noteId="note-2"
        ownerUserId="user-2"
        official={false}
        studyPackStatus="STUDY_PACK_READY"
      />,
    );

    expect(screen.getByText("By Community")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Make a Copy" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Share" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Edit Note" })).not.toBeInTheDocument();
  });

  it("shows NoteLib label for official public content", () => {
    render(
      <PublicNoteOwnershipActions
        noteId="note-3"
        ownerUserId="admin-1"
        official
        studyPackStatus="STUDY_PACK_READY"
      />,
    );

    expect(screen.getByText("By NoteLib")).toBeInTheDocument();
  });
});
