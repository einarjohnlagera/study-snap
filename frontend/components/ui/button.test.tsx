import { render, screen } from "@testing-library/react";
import { Button } from "./button";

describe("Button", () => {
  it("uses the shared press-feedback motion class", () => {
    render(<Button type="button">Continue</Button>);

    expect(screen.getByRole("button", { name: "Continue" })).toHaveClass("motion-pressable");
  });
});
