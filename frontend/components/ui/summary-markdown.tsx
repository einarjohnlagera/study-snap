import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import { renderExtractedMath } from "@/components/study-pack/quiz-working-solution";
import { normalizeBareMath } from "@/lib/math-normalization";
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
  // ⚠️ `remark-math` TOKENISES DELIMITED MATH ONLY — it has nothing to say about a bare `\frac{a}{b}`.
  // Without this, an undelimited expression in a summary reached the renderer as literal text and
  // printed with its backslash visible, on every surface that uses this component. 36 production
  // summaries carry a backslash and no delimiter at all.
  //
  // ⚠️ ONE LIMITATION, STATED RATHER THAN DISCOVERED: `normalizeBareMath` returns early on ANY
  // delimiter anywhere in the string it is given, and a summary is one long multi-paragraph string.
  // So a summary that already contains a single `$` is left entirely alone, including its bare
  // expressions elsewhere. That is the 36 measured above and no more — do not read this as covering
  // every summary. Splitting per paragraph to widen it would change what remark-gfm sees and is not
  // worth the blast radius.
  //
  // ⚠️ A SECOND LIMITATION, ADDED AFTER A COLD PASS FOUND IT: this normalises the WHOLE markdown
  // string, and markdown has literal-text regions that `normalizeBareMath` knows nothing about. A
  // fenced or inline code block containing a maths command is rewritten — `` `x^2 + y^2` `` comes out
  // as `$x^{2}$ + $y^{2}$`, and ```` ```x = \frac{a}{b}``` ```` gains delimiters it should not have.
  // LATENT, NOT LIVE: 0 of 7,583 production summaries and 0 of 91 companion rows contain a backtick
  // with no `$`. Tables, links, escaped characters, Windows paths and a literal \n all survive.
  // Recorded because the limitation above was documented carefully and this one was silent — if a
  // summary ever carries code, fix it by skipping code regions, not by narrowing the allowlist.
  //
  // ⚠️ Display-time only. It returns a string for rendering and never writes back — `v0.110.1`
  // shipped a sanitizer that re-ran on every deserialization and progressively destroyed stored text.
  const normalized = normalizeBareMath(content);
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
        {normalized}
      </ReactMarkdown>
    </div>
  );
}
