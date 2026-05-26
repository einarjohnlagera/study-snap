import { render, screen } from "@testing-library/react";
import { QuizWorkingSolution } from "./quiz-working-solution";

describe("QuizWorkingSolution", () => {
  it("returns null when workingSolution is null", () => {
    const { container } = render(<QuizWorkingSolution workingSolution={null} planType="PRO" />);

    expect(container).toBeEmptyDOMElement();
  });

  it("returns null when planType is PLUS and alwaysShow is not set", () => {
    const { container } = render(<QuizWorkingSolution workingSolution="P = IV" planType="PLUS" />);

    expect(container).toBeEmptyDOMElement();
  });

  it("renders the panel and disclaimer when planType is PRO", () => {
    render(<QuizWorkingSolution workingSolution="P = IV = 5 x 2 = 10 W" planType="PRO" />);

    expect(screen.getByText("Working Solution")).toBeInTheDocument();
    expect(screen.getByText("P = IV = 5 x 2 = 10 W")).toBeInTheDocument();
    expect(screen.getByText("AI-generated — verify calculations")).toBeInTheDocument();
  });

  it("renders the panel when alwaysShow is true regardless of planType", () => {
    render(<QuizWorkingSolution workingSolution="F = ma" planType="FREE" alwaysShow />);

    expect(screen.getByText("Working Solution")).toBeInTheDocument();
    expect(screen.getByText("F = ma")).toBeInTheDocument();
  });
});
