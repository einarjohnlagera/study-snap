import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PublicSeoCopyCta } from "./public-seo-copy-cta";
import { buildLoginPath, getAuthUser } from "@/lib/auth";
import { copyNote } from "@/lib/api";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};

let searchParamsMock = new URLSearchParams();

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/public/library/science/cell-structure",
  useSearchParams: () => searchParamsMock,
}));

jest.mock("@/lib/auth", () => ({
  buildLoginPath: jest.fn(),
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  copyNote: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("PublicSeoCopyCta", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    searchParamsMock = new URLSearchParams();
    document.cookie = "notelib-copy-intent=; path=/; max-age=0; SameSite=Strict";
    (buildLoginPath as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (copyNote as jest.Mock).mockReset();
    (buildLoginPath as jest.Mock).mockImplementation(({ redirectTo }: { redirectTo?: string | null } = {}) => {
      return redirectTo ? `/login?redirect=${encodeURIComponent(redirectTo)}` : "/login";
    });
  });

  it("opens an auth modal for anonymous visitors before copying", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(<PublicSeoCopyCta noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: "Copy to My Library" }));

    expect(screen.getByRole("dialog", { name: "Save this note" })).toBeInTheDocument();
    expect(screen.getByText("Create a free account or log in to copy this note to your library.")).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
    expect(copyNote).not.toHaveBeenCalled();
  });

  it("routes auth-modal actions to login or signup with the copy intent redirect", () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(<PublicSeoCopyCta noteId="note-1" label="Create your own Study Pack" redirectTarget="generate" />);

    fireEvent.click(screen.getByRole("button", { name: "Create your own Study Pack" }));
    fireEvent.click(screen.getByRole("button", { name: "Log In" }));

    expect(pushMock).toHaveBeenCalledWith(
      "/login?redirect=%2Fpublic%2Flibrary%2Fscience%2Fcell-structure%3Fcopy%3D1%26intent%3Dgenerate",
    );

    pushMock.mockReset();

    fireEvent.click(screen.getByRole("button", { name: "Create your own Study Pack" }));
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));

    expect(pushMock).toHaveBeenCalledWith(
      "/signup?redirect=%2Fpublic%2Flibrary%2Fscience%2Fcell-structure%3Fcopy%3D1%26intent%3Dgenerate&intent=copy-note",
    );
    expect(document.cookie).toContain("notelib-copy-intent=note-1");
    expect(copyNote).not.toHaveBeenCalled();
  });

  it("copies immediately for authenticated visitors", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-1" });

    render(<PublicSeoCopyCta noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: "Copy to My Library" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1", { includeStudyPack: true });
      expect(pushMock).toHaveBeenCalledWith("/notes/copied-note-1?copied=1");
    });
  });

  it("copies and redirects into generation flow when requested", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-1" });

    render(<PublicSeoCopyCta noteId="note-1" label="Generate Study Pack" redirectTarget="generate" />);

    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1", { includeStudyPack: true });
      expect(pushMock).toHaveBeenCalledWith("/notes/copied-note-1?copied=1&generate=1");
    });
  });

  it("copies a ready Study Pack as-is and starts Quick Review for authenticated visitors", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-1", studyPackStatus: "STUDY_PACK_READY" });

    render(<PublicSeoCopyCta noteId="note-1" label="Quiz yourself on this note" redirectTarget="quick-review" />);

    fireEvent.click(screen.getByRole("button", { name: "Quiz yourself on this note" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1", { includeStudyPack: true });
      expect(pushMock).toHaveBeenCalledWith("/notes/copied-note-1?copied=1&startQuickReview=1");
    });
  });

  it("leaves an unready copied note on its detail page without generation or Quick Review", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-1", studyPackStatus: "DRAFT" });

    render(<PublicSeoCopyCta noteId="note-1" label="Quiz yourself on this note" redirectTarget="quick-review" />);

    fireEvent.click(screen.getByRole("button", { name: "Quiz yourself on this note" }));

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1", { includeStudyPack: true });
      expect(pushMock).toHaveBeenCalledWith("/notes/copied-note-1?copied=1");
    });
  });

  it("shows the existing copy error after an authenticated Quick Review copy fails", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockRejectedValue(new Error("Copy failed"));

    render(<PublicSeoCopyCta noteId="note-1" label="Quiz yourself on this note" redirectTarget="quick-review" />);

    fireEvent.click(screen.getByRole("button", { name: "Quiz yourself on this note" }));

    expect(await screen.findByText("Copy failed")).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("auto-copies after login when the copy query flag is present", async () => {
    searchParamsMock = new URLSearchParams("copy=1");
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-1" });

    render(<PublicSeoCopyCta noteId="note-1" />);

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1", { includeStudyPack: true });
      expect(replaceMock).toHaveBeenCalledWith("/notes/copied-note-1?copied=1");
    });
  });

  it("auto-copies into generation flow after login when the intent is generate", async () => {
    searchParamsMock = new URLSearchParams("copy=1&intent=generate");
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-1" });

    render(<PublicSeoCopyCta noteId="note-1" redirectTarget="generate" />);

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1", { includeStudyPack: true });
      expect(replaceMock).toHaveBeenCalledWith("/notes/copied-note-1?copied=1&generate=1");
    });
  });

  it("auto-copies into quick review after login when the intent is quick-review", async () => {
    searchParamsMock = new URLSearchParams("copy=1&intent=quick-review");
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-1", studyPackStatus: "STUDY_PACK_READY" });

    render(<PublicSeoCopyCta noteId="note-1" redirectTarget="quick-review" />);

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1", { includeStudyPack: true });
      expect(replaceMock).toHaveBeenCalledWith("/notes/copied-note-1?copied=1&startQuickReview=1");
    });
  });

  it("leaves an unready copied note on its detail page after login", async () => {
    searchParamsMock = new URLSearchParams("copy=1&intent=quick-review");
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockResolvedValue({ id: "copied-note-1", studyPackStatus: "FAILED" });

    render(<PublicSeoCopyCta noteId="note-1" redirectTarget="quick-review" />);

    await waitFor(() => {
      expect(copyNote).toHaveBeenCalledWith("note-1", { includeStudyPack: true });
      expect(replaceMock).toHaveBeenCalledWith("/notes/copied-note-1?copied=1");
    });
  });

  it("shows the existing copy error after an automatic post-login copy fails", async () => {
    searchParamsMock = new URLSearchParams("copy=1&intent=quick-review");
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    (copyNote as jest.Mock).mockRejectedValue(new Error("Copy failed"));

    render(<PublicSeoCopyCta noteId="note-1" redirectTarget="quick-review" />);

    expect(await screen.findByText("Copy failed")).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });
});
