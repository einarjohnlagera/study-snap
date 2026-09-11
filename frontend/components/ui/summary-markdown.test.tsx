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

// ⚠️ v0.141.0. `remark-math` tokenises DELIMITED math only, so an undelimited expression in a summary
// printed literally with its backslash visible — on every surface that uses this component. 36
// production summaries carry a backslash and no delimiter at all. The fix normalises before the
// tokenizer runs; it returns a display string and never writes back (the v0.110.1 rule).
it("renders undelimited math in a summary instead of printing the backslash", () => {
  const { container } = render(
    <SummaryMarkdown content={"The area is \\frac{1}{2}bh for a triangle."} />,
  );

  expect(container.querySelector(".katex")).not.toBeNull();
  expect(container.querySelector(".katex-html")?.textContent ?? "").not.toContain("\\frac");
  expect(container.textContent ?? "").toContain("for a triangle.");
});

// The counterpart guard: normalizeBareMath's first design rule is "NEVER make things worse", so prose
// that merely contains a backslash must survive untouched rather than being mangled into math.
it("leaves a non-math backslash in a summary alone", () => {
  const { container } = render(
    <SummaryMarkdown content={"Save it to C:\\Users\\notes for later."} />,
  );

  expect(container.querySelector(".katex")).toBeNull();
  expect(container.textContent ?? "").toContain("C:\\Users\\notes");
});
