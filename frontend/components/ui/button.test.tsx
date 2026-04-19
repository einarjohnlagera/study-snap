import { render, screen } from "@testing-library/react";
import { Button } from "./button";

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
