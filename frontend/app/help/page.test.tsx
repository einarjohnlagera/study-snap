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

  it("renders the Learning Companion guide from its card", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });

    render(<HelpPage />);

    fireEvent.click(screen.getByRole("button", { name: /Learning Companion/i }));

    expect(await screen.findByRole("dialog", { name: "Learning Companion" })).toBeInTheDocument();
    expect(screen.getByText(/At most one eligible tip appears near Today's Focus/i)).toBeInTheDocument();
  });

  it("opens the Learning Companion guide from the hash deep link", async () => {
    globalThis.history.replaceState(null, "", "/help#learning-companion");

    render(<HelpPage />);

    await waitFor(() => {
      expect(screen.getByRole("dialog", { name: "Learning Companion" })).toBeInTheDocument();
    });
  });

  it("documents Primary Review Set and target-date pacing in the Study Plans guide", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "BOARD_EXAM" });

    render(<HelpPage />);

    fireEvent.click(screen.getByRole("button", { name: /Study Plans & Collections/i }));

    expect(await screen.findByRole("dialog", { name: "Study Plans & Collections" })).toBeInTheDocument();
    expect(screen.getByText("Your Primary Review Set")).toBeInTheDocument();
    expect(screen.getByText("Pacing to a target date")).toBeInTheDocument();
  });
});
