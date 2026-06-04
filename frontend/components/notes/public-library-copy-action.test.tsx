import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PublicLibraryCopyAction } from "./public-library-copy-action";
import { buildLoginPath, getAuthUser } from "@/lib/auth";
import { copyNote, trackAnalyticsEvent } from "@/lib/api";

const pushMock = jest.fn();
let pathnameMock = "/public/library";

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
  usePathname: () => pathnameMock,
}));

jest.mock("@/lib/auth", () => ({
  buildLoginPath: jest.fn(),
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  copyNote: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("PublicLibraryCopyAction", () => {
  beforeEach(() => {
    pushMock.mockReset();
    pathnameMock = "/public/library";
    window.history.replaceState({}, "", "/public/library");
    document.cookie = "notelib-copy-intent=; path=/; max-age=0; SameSite=Strict";
    (buildLoginPath as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (copyNote as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (buildLoginPath as jest.Mock).mockImplementation(({ redirectTo }: { redirectTo?: string | null } = {}) => {
      return redirectTo ? `/login?redirect=${encodeURIComponent(redirectTo)}` : "/login";
    });
  });

  it("renders no inline action for the owner card", () => {
    const { container } = render(
      <PublicLibraryCopyAction
        noteId="note-1"
        isOwner
        onCopySuccess={jest.fn()}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("shows only the copied-note action without rendering status text in the action area", () => {
    render(
      <PublicLibraryCopyAction
        noteId="note-2"
        isOwner={false}
        existingCopyNoteId="copied-note-2"
        onCopySuccess={jest.fn()}
      />,
    );

    const button = screen.getByRole("button", { name: "Saved" });
    expect(button).toBeInTheDocument();
    expect(button).toBeDisabled();
    expect(screen.queryByText("Already in your library")).not.toBeInTheDocument();
    expect(screen.queryByText("In Library")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Open in My Library" })).not.toBeInTheDocument();
  });

  it("copies the note for authenticated users", async () => {
    const onCopySuccess = jest.fn();
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-3" });

    render(
      <PublicLibraryCopyAction
        noteId="note-3"
        isOwner={false}
        onCopySuccess={onCopySuccess}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-3");
      expect(onCopySuccess).toHaveBeenCalledWith(expect.objectContaining({ copiedNoteId: "copied-note-3" }));
    });
  });

  it("opens an auth modal for anonymous users before copying", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(
      <PublicLibraryCopyAction
        noteId="note-4"
        isOwner={false}
        onCopySuccess={jest.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByRole("dialog", { name: "Save this note" })).toBeInTheDocument();
    expect(screen.getByText("Create an account or log in to save notes to your library.")).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("routes auth-modal actions to login or signup with the copy intent redirect", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(
      <PublicLibraryCopyAction
        noteId="note-5"
        isOwner={false}
        onCopySuccess={jest.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByRole("button", { name: "Log In" }));

    expect(pushMock).toHaveBeenCalledWith(
      "/login?redirect=%2Fpublic%2Flibrary%3Fcopy%3D1%26intent%3Dlibrary",
    );

    pushMock.mockReset();

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));

    expect(pushMock).toHaveBeenCalledWith(
      "/signup?redirect=%2Fpublic%2Flibrary%3Fcopy%3D1%26intent%3Dlibrary",
    );
    expect(document.cookie).toContain("notelib-copy-intent=note-5");
  });
});
