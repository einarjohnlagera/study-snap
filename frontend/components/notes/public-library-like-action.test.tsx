import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PublicLibraryLikeAction } from "./public-library-like-action";
import { buildLoginPath, getAuthUser } from "@/lib/auth";
import { togglePublicNoteLike } from "@/lib/api";

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
  togglePublicNoteLike: jest.fn(),
}));

describe("PublicLibraryLikeAction", () => {
  beforeEach(() => {
    pushMock.mockReset();
    pathnameMock = "/public/library";
    window.history.replaceState({}, "", "/public/library");
    (buildLoginPath as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (togglePublicNoteLike as jest.Mock).mockReset();
    (buildLoginPath as jest.Mock).mockImplementation(({ redirectTo }: { redirectTo?: string | null } = {}) => {
      return redirectTo ? `/login?redirect=${encodeURIComponent(redirectTo)}` : "/login";
    });
  });

  it("toggles the public note like for authenticated users", async () => {
    const onLikeSuccess = jest.fn();
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (togglePublicNoteLike as jest.Mock).mockResolvedValue({ liked: true, likeCount: 24 });

    render(
      <PublicLibraryLikeAction
        noteId="note-1"
        likeCount={23}
        liked={false}
        onLikeSuccess={onLikeSuccess}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Like note" }));

    await waitFor(() => {
      expect(togglePublicNoteLike).toHaveBeenCalledWith("note-1");
      expect(onLikeSuccess).toHaveBeenCalledWith({ liked: true, likeCount: 24 });
    });
  });

  it("opens an auth modal for anonymous users before liking", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(
      <PublicLibraryLikeAction
        noteId="note-2"
        likeCount={0}
        liked={false}
        onLikeSuccess={jest.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Like note" }));

    expect(screen.getByRole("dialog", { name: "Like this note" })).toBeInTheDocument();
    expect(screen.getByText("Create an account or log in to like helpful notes in Public Library.")).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("routes auth-modal actions to login or signup with the current path as redirect", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(
      <PublicLibraryLikeAction
        noteId="note-3"
        likeCount={0}
        liked={false}
        onLikeSuccess={jest.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Like note" }));
    fireEvent.click(screen.getByRole("button", { name: "Log In" }));

    expect(pushMock).toHaveBeenCalledWith("/login?redirect=%2Fpublic%2Flibrary");

    pushMock.mockReset();

    fireEvent.click(screen.getByRole("button", { name: "Like note" }));
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));

    expect(pushMock).toHaveBeenCalledWith("/signup?redirect=%2Fpublic%2Flibrary");
  });
});
