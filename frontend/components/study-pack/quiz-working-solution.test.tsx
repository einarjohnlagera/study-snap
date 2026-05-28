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
    const { container } = render(<QuizWorkingSolution workingSolution="P = IV = 5 x 2 = 10 W" planType="PRO" />);

    expect(screen.getByText("Working Solution")).toBeInTheDocument();
    expect(screen.getByText("P = IV = 5 x 2 = 10 W")).toBeInTheDocument();
    expect(screen.getByText("AI-generated — verify calculations")).toBeInTheDocument();
    expect(container.querySelector(".katex")).not.toBeInTheDocument();
  });

  it("renders the panel when alwaysShow is true regardless of planType", () => {
    render(<QuizWorkingSolution workingSolution="F = ma" planType="FREE" alwaysShow />);

    expect(screen.getByText("Working Solution")).toBeInTheDocument();
    expect(screen.getByText("F = ma")).toBeInTheDocument();
  });

  it("renders block LaTeX with KaTeX", () => {
    const { container } = render(
      <QuizWorkingSolution workingSolution={"Use power:\n$$P = \\frac{V^2}{R}$$"} planType="PRO" />,
    );

    expect(container.querySelector(".katex")).toBeInTheDocument();
    expect(screen.getByText(/Use power:/)).toBeInTheDocument();
  });

  it("renders inline LaTeX with KaTeX", () => {
    const { container } = render(
      <QuizWorkingSolution workingSolution={"Use $P = IV$ for power."} planType="PRO" />,
    );

    expect(container.querySelector(".katex")).toBeInTheDocument();
    expect(screen.getByText(/Use/)).toBeInTheDocument();
    expect(screen.getByText(/for power\./)).toBeInTheDocument();
  });

  it("renders mixed plain text and LaTeX segments", () => {
    const { container } = render(
      <QuizWorkingSolution workingSolution={"Step 1: $P = IV$\nStep 2: $$P = 10W$$"} planType="PRO" />,
    );

    expect(container.querySelectorAll(".katex")).toHaveLength(2);
    expect(screen.getByText(/Step 1:/)).toBeInTheDocument();
    expect(screen.getByText(/Step 2:/)).toBeInTheDocument();
  });

  it("renders bracket and parenthesis LaTeX delimiters", () => {
    const { container } = render(
      <QuizWorkingSolution workingSolution={"\\[F = ma\\]\nThen \\(a = F/m\\)."} planType="PRO" />,
    );

    expect(container.querySelectorAll(".katex")).toHaveLength(2);
    expect(screen.getByText(/Then/)).toBeInTheDocument();
  });

  it("falls back to plain text for invalid LaTeX without throwing", () => {
    const { container } = render(
      <QuizWorkingSolution workingSolution={"Invalid: $\\notacommand{$"} planType="PRO" />,
    );

    expect(container.querySelector(".katex")).not.toBeInTheDocument();
    expect(container).toHaveTextContent("Invalid: $\\notacommand{$");
  });
});
