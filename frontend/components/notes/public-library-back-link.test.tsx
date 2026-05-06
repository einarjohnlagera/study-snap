import { render, screen, waitFor } from "@testing-library/react";
import { PublicLibraryBackLink } from "./public-library-back-link";
import { getAuthUser } from "@/lib/auth";

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

describe("PublicLibraryBackLink", () => {
  beforeEach(() => {
    (getAuthUser as jest.Mock).mockReset();
  });

  it("hides the link for anonymous visitors", async () => {
    (getAuthUser as jest.Mock).mockReturnValue(null);

    render(<PublicLibraryBackLink className="test-class" />);

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
});
