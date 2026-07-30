import { render, screen, waitFor } from "@testing-library/react";
import { PublicLibraryBackLink } from "./public-library-back-link";
import { getAuthUser } from "@/lib/auth";

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

describe("PublicLibraryBackLink", () => {
  beforeEach(() => {
    (getAuthUser as jest.Mock).mockReset();
    globalThis.sessionStorage.clear();
  });

  it("hides the link for anonymous visitors", async () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(<PublicLibraryBackLink />);

    await waitFor(() => {
      expect(screen.queryByRole("link", { name: "Public Library" })).not.toBeInTheDocument();
    });
  });

  it("shows the link for authenticated visitors", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });

    render(<PublicLibraryBackLink />);

    expect(await screen.findByRole("link", { name: "Public Library" })).toHaveAttribute(
      "href",
      "/public/library",
    );
  });

  it("returns authenticated visitors to an Explore Notes context", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ id: "user-1" });
    globalThis.sessionStorage.setItem(
      "notelib_public_library_return_url",
      "/explore?tab=notes&subject=biology",
    );

    render(<PublicLibraryBackLink />);

    expect(await screen.findByRole("link", { name: "Explore" })).toHaveAttribute(
      "href",
      "/explore?tab=notes&subject=biology",
    );
  });
});
