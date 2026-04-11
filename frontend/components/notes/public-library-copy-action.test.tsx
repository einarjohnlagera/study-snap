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

  it("shows the copied-state badge and View Note action without generic open labels", () => {
    render(
      <PublicLibraryCopyAction
        noteId="note-2"
        isOwner={false}
        existingCopyNoteId="copied-note-2"
        onCopySuccess={jest.fn()}
      />,
    );

    expect(screen.getByText("Already in your library")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View Note" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Open in My Library" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "View Note" }));

    expect(pushMock).toHaveBeenCalledWith("/notes/copied-note-2?copied=1");
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

    fireEvent.click(screen.getByRole("button", { name: "Copy to My Library" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-3");
      expect(onCopySuccess).toHaveBeenCalledWith({ copiedNoteId: "copied-note-3" });
    });
  });

  it("redirects anonymous users to login before copying", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(
      <PublicLibraryCopyAction
        noteId="note-4"
        isOwner={false}
        onCopySuccess={jest.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Copy to My Library" }));

    expect(pushMock).toHaveBeenCalledWith(
      "/login?redirect=%2Fpublic%2Flibrary%3Fcopy%3D1%26intent%3Dlibrary",
    );
  });
});
