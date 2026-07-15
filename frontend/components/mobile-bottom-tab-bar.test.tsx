import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { MobileBottomTabBar } from "./mobile-bottom-tab-bar";

jest.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children, ...props }: { href: string; children: ReactNode }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

describe("MobileBottomTabBar", () => {
  it("renders exactly four icon-and-text tabs below the md breakpoint", () => {
    render(<MobileBottomTabBar pathname="/dashboard" profileType="BOARD_EXAM" />);

    const tabBar = screen.getByTestId("mobile-bottom-tab-bar");
    expect(tabBar).toHaveClass("md:hidden");
    expect(screen.getAllByRole("link")).toHaveLength(4);
    expect(screen.getByRole("link", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Library" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Review Sets" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Public Library" })).toBeInTheDocument();
  });

  it.each([
    ["/dashboard", "Dashboard"],
    ["/library", "Library"],
    ["/collections/collection-1", "Review Sets"],
    ["/public/library/biology", "Public Library"],
  ])("marks %s as the active route", (pathname, label) => {
    render(<MobileBottomTabBar pathname={pathname} profileType="BOARD_EXAM" />);

    expect(screen.getByRole("link", { name: label })).toHaveAttribute("aria-current", "page");
  });

  it("uses profile-aware collection navigation labels", () => {
    const { rerender } = render(<MobileBottomTabBar pathname="/collections" profileType="STUDENT" />);

    expect(screen.getByRole("link", { name: "Study Plans" })).toBeInTheDocument();

    rerender(<MobileBottomTabBar pathname="/collections" profileType="TEACHER" />);

    expect(screen.getByRole("link", { name: "Lesson Plans" })).toBeInTheDocument();
  });
});
