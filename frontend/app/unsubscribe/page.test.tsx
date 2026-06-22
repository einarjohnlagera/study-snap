import { render, screen, waitFor } from "@testing-library/react";
import UnsubscribePage from "./page";
import { ApiRequestError, unsubscribeEmail } from "@/lib/api";

let currentToken: string | null = "unsubscribe-token";

jest.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) => (key === "token" ? currentToken : null),
  }),
}));

jest.mock("@/lib/api", () => {
  class ApiRequestError extends Error {
    code: string | null;
    status: number;

    constructor(message: string, options: { code?: string | null; status: number }) {
      super(message);
      this.name = "ApiRequestError";
      this.code = options.code ?? null;
      this.status = options.status;
    }
  }

  return {
    ApiRequestError,
    unsubscribeEmail: jest.fn(),
  };
});

describe("UnsubscribePage", () => {
  beforeEach(() => {
    currentToken = "unsubscribe-token";
    (unsubscribeEmail as jest.Mock).mockReset();
  });

  it("applies unsubscribe on load and shows confirmation", async () => {
    (unsubscribeEmail as jest.Mock).mockResolvedValue({
      category: "WEEKLY_SUMMARY",
      displayName: "Weekly summary",
      message: "You've been unsubscribed from Weekly summary emails.",
    });

    render(<UnsubscribePage />);

    await waitFor(() => {
      expect(unsubscribeEmail).toHaveBeenCalledWith("unsubscribe-token");
    });
    expect(await screen.findByRole("heading", { name: "You're unsubscribed" })).toBeInTheDocument();
    expect(screen.getByText("You've been unsubscribed from Weekly summary emails.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Manage all email preferences" }))
      .toHaveAttribute("href", "/settings?section=email-preferences");
  });

  it("shows invalid link state when the token is rejected", async () => {
    (unsubscribeEmail as jest.Mock).mockRejectedValue(
      new ApiRequestError("This unsubscribe link is invalid or has expired.", {
        code: "INVALID_UNSUBSCRIBE_TOKEN",
        status: 400,
      }),
    );

    render(<UnsubscribePage />);

    expect(await screen.findByRole("heading", { name: "Unsubscribe link issue" })).toBeInTheDocument();
    expect(screen.getByText("This unsubscribe link is invalid or has expired.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Manage all email preferences" }))
      .toHaveAttribute("href", "/settings?section=email-preferences");
  });

  it("shows invalid link state when the token is missing", async () => {
    currentToken = null;

    render(<UnsubscribePage />);

    expect(await screen.findByRole("heading", { name: "Unsubscribe link issue" })).toBeInTheDocument();
    expect(unsubscribeEmail).not.toHaveBeenCalled();
  });
});
