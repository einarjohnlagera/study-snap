import { fireEvent, render, screen } from "@testing-library/react";
import AuthPage from "./page";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
  refresh: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("next/image", () => ({
  __esModule: true,
  default: ({ alt }: { alt: string }) => <img alt={alt} />,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => null,
  LOGIN_REASON_QUERY_KEY: "reason",
  LOGIN_REASON_SESSION_EXPIRED: "SESSION_EXPIRED",
  LOGIN_REDIRECT_QUERY_KEY: "redirect",
  resolveAuthenticatedHome: () => "/dashboard",
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  login: jest.fn(),
  signup: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("AuthPage", () => {
  it("renders public footer legal links", () => {
    render(<AuthPage />);

    expect(screen.getByRole("link", { name: "Privacy Policy" })).toHaveAttribute("href", "/privacy");
    expect(screen.getByRole("link", { name: "Terms of Service" })).toHaveAttribute("href", "/terms");
    expect(screen.getByRole("link", { name: "Contact" })).toHaveAttribute("href", "mailto:support@mail.notelib.app");
  });

  it("shows signup mode while keeping legal links visible", () => {
    render(<AuthPage />);

    fireEvent.click(screen.getByRole("button", { name: "Sign up" }));

    expect(screen.getByRole("heading", { name: "Create your NoteLib account" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Privacy Policy" })).toBeInTheDocument();
  });
});
