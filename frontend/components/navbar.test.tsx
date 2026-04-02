import { fireEvent, render, screen } from "@testing-library/react";
import { Navbar } from "./navbar";

let currentPathname = "/";

jest.mock("next/navigation", () => ({
  usePathname: () => currentPathname,
}));

jest.mock("next/image", () => ({
  __esModule: true,
  default: ({ alt }: { alt: string }) => <img alt={alt} />,
}));

jest.mock("next/link", () => ({
  __esModule: true,
  default: ({
    href,
    children,
    className,
    onClick,
  }: {
    href: string;
    children: React.ReactNode;
    className?: string;
    onClick?: () => void;
  }) => (
    <a href={href} className={className} onClick={onClick}>
      {children}
    </a>
  ),
}));

describe("Navbar", () => {
  beforeEach(() => {
    currentPathname = "/";
  });

  it("renders the shared public navigation links", () => {
    render(<Navbar />);

    expect(screen.getByRole("link", { name: "Home" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: "Public Library" })).toHaveAttribute("href", "/public/library");
    expect(screen.getByRole("link", { name: "Learn" })).toHaveAttribute("href", "/learn");
    expect(screen.getByRole("link", { name: "Pricing" })).toHaveAttribute("href", "/pricing");
    expect(screen.getAllByRole("link", { name: "Login" })[0]).toHaveAttribute("href", "/login");
    expect(screen.getAllByRole("link", { name: "Get Started" })[0]).toHaveAttribute("href", "/signup");
  });

  it("shows the same navigation in the mobile menu", () => {
    render(<Navbar />);

    fireEvent.click(screen.getByRole("button", { name: "Open navigation menu" }));

    expect(screen.getAllByRole("link", { name: "Public Library" })[1]).toHaveAttribute("href", "/public/library");
    expect(screen.getAllByRole("link", { name: "Login" })[1]).toHaveAttribute("href", "/login");
    expect(screen.getAllByRole("link", { name: "Get Started" })[1]).toHaveAttribute("href", "/signup");
  });
});
