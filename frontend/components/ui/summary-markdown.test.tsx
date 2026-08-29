import { render, screen } from "@testing-library/react";
import { SummaryMarkdown } from "@/components/ui/summary-markdown";

it("renders inline math instead of printing the LaTeX source", () => {
  const { container } = render(
    <SummaryMarkdown content="The formula is $Q = \\frac{2}{3} C_d L$ for a weir." />,
  );

  expect(container.querySelector(".katex")).not.toBeNull();
  // The `$` delimiters must be CONSUMED by the tokenizer. Asserting the absence of the TeX source
  // would be wrong: KaTeX deliberately keeps it in a MathML <annotation> for assistive tech.
  expect(container.textContent ?? "").not.toContain("$");
  expect(container.textContent ?? "").toContain("for a weir");
});

it("survives underscores, which is the whole reason tokenization comes first", () => {
  // ⚠️ THE CASE THE DESIGN EXISTS FOR. `_` is markdown emphasis, so a text-scanning renderer sees
  // `$x_1 + x_2$` only AFTER `_1 + x_` has become <em> — the math is gone before it can be found.
  // remark-math tokenizes ahead of emphasis, so both subscripts survive.
  const { container } = render(<SummaryMarkdown content="Given $x_1 + x_2 = 10$, solve." />);

  expect(container.querySelector(".katex")).not.toBeNull();
  expect(container.querySelector("em")).toBeNull();
  expect(container.textContent ?? "").not.toContain("$");
});

it("renders display math as a block", () => {
  // ⚠️ Block form needs its own lines. `$$x$$` on ONE line is INLINE math by remark-math's spec, so
  // the first version of this test asserted display mode against content that is correctly inline.
  const { container } = render(<SummaryMarkdown content={"$$\n\\frac{a}{b}\n$$"} />);

  // ⚠️ Require .katex-display specifically. An OR against ".katex" passes even when displayMode is
  // hardcoded false, so it would pin that math rendered but not that BLOCK math rendered as a block.
  expect(container.querySelector(".katex-display")).not.toBeNull();
  expect(container.textContent ?? "").not.toContain("$$");
});

it("still renders ordinary markdown, and leaves non-math spans alone", () => {
  render(<SummaryMarkdown content="**Common Misconceptions:** discharge varies." />);

  expect(screen.getByText("Common Misconceptions:").tagName).toBe("STRONG");
  expect(screen.getByText(/discharge varies/)).toBeInTheDocument();
});
