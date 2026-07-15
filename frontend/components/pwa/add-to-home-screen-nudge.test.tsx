import { act, render, screen } from "@testing-library/react";
import { AddToHomeScreenNudge } from "./add-to-home-screen-nudge";

jest.mock("next/image", () => ({
  __esModule: true,
  default: () => <span />,
}));

describe("AddToHomeScreenNudge", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    window.localStorage.clear();
    window.sessionStorage.clear();
    window.localStorage.setItem("pwa-nudge-visit-count", "1");
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 390 });
    Object.defineProperty(navigator, "maxTouchPoints", { configurable: true, value: 1 });
    Object.defineProperty(navigator, "userAgent", { configurable: true, value: "iPhone" });
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      value: jest.fn().mockReturnValue({ matches: false }),
    });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("renders above the mobile bottom tab bar when both prompts are eligible", () => {
    render(<AddToHomeScreenNudge />);

    act(() => {
      jest.runOnlyPendingTimers();
    });

    const prompt = screen.getByText("Add NoteLib to your phone").parentElement?.parentElement;
    expect(prompt).toHaveClass("bottom-[calc(env(safe-area-inset-bottom)+5.25rem)]");
  });
});
