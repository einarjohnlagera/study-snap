import { fireEvent, render, screen } from "@testing-library/react";
import { Navbar } from "./navbar";

let currentPathname = "/";

jest.mock("next/navigation", () => ({
  usePathname: () => currentPathname,
}));

jest.mock("next/image", () => ({
  __esModule: true,
  default: ({ alt }: { alt: string }) => <span role="img" aria-label={alt} />,
}));

jest.mock("./theme-toggle", () => ({
  ThemeToggle: () => <button type="button" aria-label="Theme: System">Theme</button>,
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

    expect(screen.getByRole("img", { name: "NoteLib" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Home" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: "How it Works" })).toHaveAttribute("href", "/how-it-works");
    expect(screen.getByRole("link", { name: "Demo" })).toHaveAttribute("href", "/demo");
    expect(screen.getByRole("link", { name: "Explore" })).toHaveAttribute("href", "/explore");
    // Stage 2 (v0.84.0): the nav names Explore, not Public Library. /public/library is still a live
    // canonical route — only which Discovery System source the nav names first changed.
    expect(screen.queryByRole("link", { name: "Public Library" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Learn" })).toHaveAttribute("href", "/learn");
    expect(screen.getByRole("link", { name: "Pricing" })).toHaveAttribute("href", "/pricing");
    expect(screen.getAllByRole("link", { name: "Login" })[0]).toHaveAttribute("href", "/login");
    expect(screen.getAllByRole("link", { name: "Get Started" })[0]).toHaveAttribute("href", "/signup");
    expect(screen.getAllByRole("button", { name: "Theme: System" })[0]).toBeInTheDocument();
  });

  it("shows the same navigation in the mobile menu", () => {
    render(<Navbar />);

    expect(screen.getAllByRole("button", { name: "Theme: System" })).toHaveLength(2);
    expect(screen.getAllByRole("link", { name: "Get Started" })).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "Open navigation menu" }));

    expect(screen.getAllByRole("link", { name: "How it Works" })[1]).toHaveAttribute("href", "/how-it-works");
    expect(screen.getAllByRole("link", { name: "Demo" })[1]).toHaveAttribute("href", "/demo");
    expect(screen.getAllByRole("link", { name: "Explore" })[1]).toHaveAttribute("href", "/explore");
    expect(screen.getAllByRole("link", { name: "Login" })[1]).toHaveAttribute("href", "/login");
    expect(screen.getAllByRole("link", { name: "Get Started" })[1]).toHaveAttribute("href", "/signup");
    expect(screen.getAllByRole("button", { name: "Theme: System" })).toHaveLength(2);
    expect(screen.getAllByRole("link", { name: "Get Started" })).toHaveLength(2);
  });
});
