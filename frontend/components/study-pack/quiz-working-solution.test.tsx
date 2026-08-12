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

  // Stored content predates any instruction to emit delimiters, so bare LaTeX reached learners as
  // literal text with visible backslashes. These assert the repair end-to-end, through the real
  // renderer rather than the normalizer alone.
  it("renders bare LaTeX that has no delimiters at all", () => {
    const { container } = render(
      <QuizWorkingSolution workingSolution={"d = \\sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}"} planType="PRO" />,
    );

    expect(container.querySelector(".katex")).toBeInTheDocument();
    // KaTeX embeds the original TeX in a MathML annotation for accessibility, so assert against the
    // VISIBLE layer only — otherwise this passes or fails on markup the learner never sees.
    expect(container.querySelector(".katex-html")?.textContent ?? "").not.toContain("\\sqrt");
  });

  it("renders bare carets", () => {
    const { container } = render(
      <QuizWorkingSolution workingSolution={"x^2 + y^2 = 1"} planType="PRO" />,
    );

    expect(container.querySelector(".katex")).toBeInTheDocument();
  });

  it("leaves ordinary prose containing a backslash as plain text", () => {
    const { container } = render(
      <QuizWorkingSolution workingSolution={"Save the file to C:\\Users\\notes"} planType="PRO" />,
    );

    expect(container.querySelector(".katex")).not.toBeInTheDocument();
    expect(container).toHaveTextContent("Save the file to C:\\Users\\notes");
  });
});
