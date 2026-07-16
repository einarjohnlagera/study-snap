import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { MobileBottomTabBar } from "./mobile-bottom-tab-bar";

let searchParams = new URLSearchParams();

jest.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
}));

jest.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children, ...props }: { href: string; children: ReactNode }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

describe("MobileBottomTabBar", () => {
  beforeEach(() => {
    searchParams = new URLSearchParams();
    window.sessionStorage.clear();
  });

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

  it("restores a valid private Library ref and falls back for an invalid one", () => {
    searchParams = new URLSearchParams({ ref: "/library?tags=Review" });
    const { rerender } = render(<MobileBottomTabBar pathname="/notes/note-1" profileType="STUDENT" />);

    expect(screen.getByRole("link", { name: "Library" })).toHaveAttribute("href", "/library?tags=Review");

    searchParams = new URLSearchParams({ ref: "https://notelib.app/library?tags=Review" });
    rerender(<MobileBottomTabBar pathname="/notes/note-1" profileType="STUDENT" />);

    expect(screen.getByRole("link", { name: "Library" })).toHaveAttribute("href", "/library");
  });

  it("restores a valid Public Library return URL and falls back for an invalid one", () => {
    window.sessionStorage.setItem("notelib_public_library_return_url", "/public/library?subject=biology");
    const { rerender } = render(<MobileBottomTabBar pathname="/notes/note-1" profileType="STUDENT" />);

    expect(screen.getByRole("link", { name: "Public Library" })).toHaveAttribute("href", "/public/library?subject=biology");

    window.sessionStorage.setItem("notelib_public_library_return_url", "https://notelib.app/public/library?subject=biology");
    rerender(<MobileBottomTabBar pathname="/notes/note-1" profileType="STUDENT" />);

    expect(screen.getByRole("link", { name: "Public Library" })).toHaveAttribute("href", "/public/library");
  });
});
