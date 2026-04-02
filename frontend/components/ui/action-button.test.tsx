import { render, screen } from "@testing-library/react";
import { ResponsiveActionButton, ResponsiveActionContent, ResponsiveActionLink } from "./action-button";

jest.mock("next/link", () => ({
  __esModule: true,
  default: ({
    href,
    children,
    className,
  }: {
    href: string;
    children: React.ReactNode;
    className?: string;
  }) => (
    <a href={href} className={className}>
      {children}
    </a>
  ),
}));

describe("ResponsiveAction* mobile label behavior", () => {
  it("shows button labels on mobile by default", () => {
    render(<ResponsiveActionButton action="share" label="Share" />);

    expect(screen.getByText("Share")).toHaveClass("inline");
  });

  it("shows link labels on mobile by default", () => {
    render(<ResponsiveActionLink href="/library" action="library" label="Library" />);

    expect(screen.getByText("Library")).toHaveClass("inline");
  });

  it("still supports icon-only exceptions when explicitly requested", () => {
    render(<ResponsiveActionContent action="edit" label="Edit" showTextOnMobile={false} />);

    expect(screen.getByText("Edit")).toHaveClass("hidden", "sm:inline");
  });
});
