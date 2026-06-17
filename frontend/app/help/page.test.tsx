import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import HelpPage from "./page";
import { getAuthUser } from "@/lib/auth";

const replaceMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

describe("HelpPage", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    replaceMock.mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT" });
    globalThis.history.replaceState(null, "", "/help");
  });

  it("renders the Bulk Generation guide from its card", async () => {
    render(<HelpPage />);

    fireEvent.click(screen.getByRole("button", { name: /Bulk Generation/i }));

    expect(await screen.findByRole("dialog", { name: "Bulk Generation" })).toBeInTheDocument();
    expect(screen.getByText(/turns a list of topics into separate notes/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open Bulk Generation" })).toHaveAttribute(
      "href",
      "/library/bulk-generate",
    );
  });

  it("opens the Bulk Generation guide from the hash deep link", async () => {
    globalThis.history.replaceState(null, "", "/help#bulk-generate");

    render(<HelpPage />);

    await waitFor(() => {
      expect(screen.getByRole("dialog", { name: "Bulk Generation" })).toBeInTheDocument();
    });
  });
});
