import katex from "katex";
import type { ReactNode } from "react";
import type { QuizItem } from "@/lib/api";

type QuizWorkingSolutionProps = {
  workingSolution: string | null | undefined;
  alwaysShow?: boolean;
  planType?: string | null;
};

type MathDelimiter = {
  displayMode: boolean;
  end: string;
  start: string;
};

const BLOCK_DOLLAR_DELIMITER: MathDelimiter = { start: "$$", end: "$$", displayMode: true };
const BLOCK_BRACKET_DELIMITER: MathDelimiter = { start: "\\[", end: "\\]", displayMode: true };
const INLINE_DOLLAR_DELIMITER: MathDelimiter = { start: "$", end: "$", displayMode: false };
const INLINE_PAREN_DELIMITER: MathDelimiter = { start: "\\(", end: "\\)", displayMode: false };
const MATH_DELIMITERS = [
  BLOCK_DOLLAR_DELIMITER,
  BLOCK_BRACKET_DELIMITER,
  INLINE_DOLLAR_DELIMITER,
  INLINE_PAREN_DELIMITER,
];
const KATEX_ERROR_CLASS = "katex-error";

export function hasComputationalWorkingSolution(
  question: Pick<QuizItem, "questionType" | "workingSolution"> | null | undefined,
) {
  return question?.questionType === "COMPUTATIONAL" && Boolean(question.workingSolution?.trim());
}

function findDelimiterAt(text: string, index: number) {
  for (const delimiter of MATH_DELIMITERS) {
    if (delimiter === INLINE_DOLLAR_DELIMITER && text.startsWith(BLOCK_DOLLAR_DELIMITER.start, index)) {
      continue;
    }
    if (text.startsWith(delimiter.start, index)) {
      return delimiter;
    }
  }
  return null;
}

function findNextDelimiterIndex(text: string, fromIndex: number) {
  let nextIndex = -1;
  for (let index = fromIndex; index < text.length; index += 1) {
    if (findDelimiterAt(text, index)) {
      nextIndex = index;
      break;
    }
  }
  return nextIndex;
}

function renderPlainTextSegment(segment: string, key: string) {
  return (
    <span key={key} className="whitespace-pre-wrap">
      {segment}
    </span>
  );
}

function renderMathSegment(latex: string, delimiter: MathDelimiter, key: string) {
  try {
    const rendered = katex.renderToString(latex, {
      displayMode: delimiter.displayMode,
      throwOnError: false,
      output: "html",
    });
    if (rendered.includes(KATEX_ERROR_CLASS)) {
      return renderPlainTextSegment(delimiter.start + latex + delimiter.end, key);
    }
    const Component = delimiter.displayMode ? "div" : "span";
    return <Component key={key} dangerouslySetInnerHTML={{ __html: rendered }} />;
  } catch {
    return renderPlainTextSegment(delimiter.start + latex + delimiter.end, key);
  }
}

/**
 * Renders text that MAY contain LaTeX, for callers that already have their own wrapper element.
 *
 * Returns the raw string untouched when the text contains no math delimiters, which is the
 * overwhelming majority of questions and options. That matters: wrapping every plain string in an
 * extra span changes which element `getByText` resolves to, and would silently move styling like
 * `break-words` off the element tests assert against. Structure is only added where math exists.
 */
export function renderMathText(text: string): ReactNode {
  if (!MATH_DELIMITERS.some((delimiter) => text.includes(delimiter.start))) {
    return text;
  }
  return <>{renderWorkingSolution(text)}</>;
}

export function renderWorkingSolution(text: string): ReactNode[] {
  if (!MATH_DELIMITERS.some((delimiter) => text.includes(delimiter.start))) {
    return [renderPlainTextSegment(text, "plain-0")];
  }

  const nodes: ReactNode[] = [];
  let cursor = 0;
  let keyIndex = 0;
  while (cursor < text.length) {
    const delimiter = findDelimiterAt(text, cursor);
    if (!delimiter) {
      const nextDelimiterIndex = findNextDelimiterIndex(text, cursor);
      const plainEnd = nextDelimiterIndex === -1 ? text.length : nextDelimiterIndex;
      const plainText = text.slice(cursor, plainEnd);
      if (plainText) {
        nodes.push(renderPlainTextSegment(plainText, `plain-${keyIndex}`));
        keyIndex += 1;
      }
      cursor = plainEnd;
      continue;
    }

    const contentStart = cursor + delimiter.start.length;
    const contentEnd = text.indexOf(delimiter.end, contentStart);
    if (contentEnd === -1) {
      nodes.push(renderPlainTextSegment(text.slice(cursor), `plain-${keyIndex}`));
      break;
    }

    const latex = text.slice(contentStart, contentEnd);
    nodes.push(renderMathSegment(latex, delimiter, `math-${keyIndex}`));
    keyIndex += 1;
    cursor = contentEnd + delimiter.end.length;
  }
  return nodes;
}

export function QuizWorkingSolution({
  workingSolution,
  alwaysShow = false,
  planType = null,
}: Readonly<QuizWorkingSolutionProps>) {
  const trimmedSolution = workingSolution?.trim();

  if (!trimmedSolution) {
    return null;
  }
  if (!alwaysShow && planType !== "PRO") {
    return null;
  }

  return (
    <div className="space-y-2 rounded-md border border-blue-500/20 bg-blue-500/5 p-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
        Working Solution
      </p>
      <div className="break-words rounded-md border border-border bg-background/70 p-3 font-mono text-xs leading-relaxed text-foreground/80">
        {renderWorkingSolution(trimmedSolution)}
      </div>
      <p className="text-xs text-foreground/55">AI-generated — verify calculations</p>
    </div>
  );
}
