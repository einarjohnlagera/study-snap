import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import { renderExtractedMath } from "@/components/study-pack/quiz-working-solution";
import { cn } from "@/lib/utils";

/**
 * ⚠️ `remark-math` is here as a TOKENIZER, not a renderer, and the distinction is the whole design.
 *
 * <p>Summary is markdown, so the text-scanning approach the quiz surfaces use cannot work: `_` is
 * emphasis, so `$x_1 + x_2$` is already `<em>` by the time any scanner sees the text. Tokenization
 * has to happen first.
 *
 * <p>`remark-math` marks math nodes with `hName`/`hProperties`, so they arrive as
 * `<code class="language-math math-inline">` (and `<pre><code … math-display>`) carrying raw TeX.
 * Those are intercepted below and rendered through {@link renderExtractedMath} — the SAME KaTeX call
 * the quiz surfaces use. **`rehype-katex` is deliberately NOT used**: it would be a second rendering
 * configuration, free to drift from the first on `output`, `throwOnError` and the error fallback.
 */
const MATH_INLINE_CLASS = "math-inline";
const MATH_DISPLAY_CLASS = "math-display";

function extractMathLatex(children: React.ReactNode): string | null {
  if (typeof children === "string") return children;
  if (Array.isArray(children) && children.every((child) => typeof child === "string")) {
    return children.join("");
  }
  return null;
}

function hasClass(className: unknown, wanted: string): boolean {
  return typeof className === "string" && className.split(/\s+/).includes(wanted);
}

type SummaryMarkdownProps = {
  content: string;
  className?: string;
};

export function SummaryMarkdown({ content, className }: Readonly<SummaryMarkdownProps>) {
  return (
    <div className={cn("space-y-3 text-sm leading-relaxed text-foreground/80", className)}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        components={{
          p: ({ children }) => <p>{children}</p>,
          // ⚠️ remark-math emits math as <code class="language-math math-inline|math-display">, NOT
          // as a span/div. Display math additionally arrives wrapped in <pre>, which is unwrapped
          // below so KaTeX's own block element is not nested inside preformatted text.
          code: ({ children, className, ...rest }) => {
            const displayMath = hasClass(className, MATH_DISPLAY_CLASS);
            const latex = displayMath || hasClass(className, MATH_INLINE_CLASS)
              ? extractMathLatex(children)
              : null;
            return latex === null
              ? <code className={className} {...rest}>{children}</code>
              : renderExtractedMath(latex, displayMath, displayMath ? "summary-display-math" : "summary-inline-math");
          },
          pre: ({ children }) => <>{children}</>,
          strong: ({ children }) => (
            <strong className="font-semibold text-foreground">{children}</strong>
          ),
          table: ({ children }) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse">{children}</table>
            </div>
          ),
          thead: ({ children }) => <thead>{children}</thead>,
          tbody: ({ children }) => <tbody className="divide-y divide-border">{children}</tbody>,
          tr: ({ children }) => <tr>{children}</tr>,
          th: ({ children }) => (
            <th className="border-b border-border px-3 py-2 text-left text-xs font-semibold uppercase tracking-wide text-foreground/60">
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="px-3 py-2 text-foreground/80">{children}</td>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
