import { fireEvent, render, screen } from "@testing-library/react";
import HelpPage from "./page";

const routerMock = { push: jest.fn(), replace: jest.fn(), refresh: jest.fn() };
let currentProfileType: string | null = "STUDENT";

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: jest.fn(() => true),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => ({ id: "user-1", profileType: currentProfileType })),
}));

describe("HelpPage", () => {
  const originalRequestAnimationFrame = globalThis.requestAnimationFrame;
  const originalCancelAnimationFrame = globalThis.cancelAnimationFrame;

  beforeEach(() => {
    currentProfileType = "STUDENT";
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    routerMock.refresh.mockReset();
    globalThis.requestAnimationFrame = ((callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    }) as typeof globalThis.requestAnimationFrame;
    globalThis.cancelAnimationFrame = jest.fn() as typeof globalThis.cancelAnimationFrame;
  });

  afterEach(() => {
    globalThis.requestAnimationFrame = originalRequestAnimationFrame;
    globalThis.cancelAnimationFrame = originalCancelAnimationFrame;
  });

  it("uses Board Exam guide copy that does not recommend Long Exam", () => {
    render(<HelpPage />);

    fireEvent.click(screen.getByRole("button", { name: /Board Exam Guide/i }));

    expect(screen.getByText("Practice with Challenge Quiz")).toBeInTheDocument();
    expect(screen.queryByText(["Practice with", "Long Exam"].join(" "))).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Switch Profile/i })).toHaveAttribute("href", "/profile#profile-type");
  });

  it("opens a profile-aware Study Plans guide linking to Collections", () => {
    render(<HelpPage />);

    fireEvent.click(screen.getByRole("button", { name: /Study Plans & Collections/i }));

    expect(screen.getByText(/What Study Plans are/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open Study Plans/i })).toHaveAttribute("href", "/collections");
  });

  it("hides Switch Profile on the matching profile guide and shows it on other profile guides", () => {
    render(<HelpPage />);

    fireEvent.click(screen.getByRole("button", { name: /Student Guide/i }));
    expect(screen.queryByRole("link", { name: /Switch Profile/i })).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Close"));
    fireEvent.click(screen.getByRole("button", { name: /Teacher Guide/i }));

    expect(screen.getByRole("link", { name: /Switch Profile/i })).toHaveAttribute("href", "/profile#profile-type");
    expect(screen.queryByRole("link", { name: /Browse Public Library/i })).not.toBeInTheDocument();
  });
});
