import { render, screen } from "@testing-library/react";
import { Button, buttonVariants } from "./button";

describe("Button", () => {
  it("uses the shared press-feedback motion class", () => {
    render(<Button type="button">Continue</Button>);

    expect(screen.getByRole("button", { name: "Continue" })).toHaveClass("motion-pressable");
  });

  it("renders a shared loading state and disables repeated clicks", () => {
    render(
      <Button type="button" loading loadingText="Saving changes...">
        Save
      </Button>,
    );

    const button = screen.getByRole("button", { name: "Saving changes..." });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
  });
});

describe("buttonVariants", () => {
  it("keeps a fixed height and no wrapping by default, so existing callers are unchanged", () => {
    const classes = buttonVariants({});
    expect(classes).toContain("whitespace-nowrap");
    expect(classes).toContain("h-10");
    expect(classes).not.toContain("whitespace-normal");
  });

  // ⚠️ The `not.toContain("whitespace-nowrap")` assertion is the load-bearing one, and it is why `wrap`
  // exists as an option rather than something a caller passes through `className`. `cn` is a PLAIN JOIN,
  // not tailwind-merge, so a caller adding `whitespace-normal` would leave BOTH classes in the list and
  // let stylesheet order decide. Asserting only that `whitespace-normal` is present would pass under that
  // bug and prove nothing.
  it("emits no conflicting nowrap class when wrapping is requested", () => {
    const classes = buttonVariants({ wrap: true });
    expect(classes).toContain("whitespace-normal");
    expect(classes).not.toContain("whitespace-nowrap");
  });

  it("swaps the fixed height for a minimum so a wrapped label can grow instead of overflowing", () => {
    const classes = buttonVariants({ wrap: true });
    expect(classes).toContain("min-h-10");
    expect(classes).not.toMatch(/(^|\s)h-10(\s|$)/);
  });

  it("applies the same rule to the small size", () => {
    const classes = buttonVariants({ size: "sm", wrap: true });
    expect(classes).toContain("min-h-9");
    expect(classes).not.toMatch(/(^|\s)h-9(\s|$)/);
  });
});
