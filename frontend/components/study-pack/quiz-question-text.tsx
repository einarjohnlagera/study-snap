import React from "react";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";

// Question text can contain LaTeX (the model emits \( ... \) inline math for algebraic prompts).
// renderWorkingSolution is the repo's existing text+KaTeX renderer -- it was only wired to working
// solutions, so questions and options showed raw markup like \(\frac{x^3}{x-2}\). It falls back to
// plain text when a segment will not parse, so non-math questions are unaffected.

// Splits "Statement N: ..." patterns onto separate labeled lines.
// Falls back to newline-aware plain text when no Statement pattern is found.
const STATEMENT_RE = /(Statement\s+(?:\d+|[IVX]+)\s*:)/gi;

/**
 * Splits a trailing interrogative off the LAST statement body, for questions generated before the
 * prompt was taught to emit a real newline there.
 *
 * ⚠️ THIS IS A LEGACY-COMPATIBILITY RENDERING PATH, NOT A TEXT-REPAIR FRAMEWORK. NoteLib never
 * auto-regenerates Study Packs, so malformed assertion questions persist indefinitely and "waiting for
 * the corpus to age out" is not a real path. Do not generalize it, and do not add a second heuristic
 * beside it later without re-deciding.
 *
 * ⚠️ IT RETURNS NODES AND NEVER MUTATES ANYTHING. v0.110.1 shipped a sanitizer that re-ran on every
 * deserialization and progressively destroyed stored choice text; the rule that came out of it is that
 * a display path must never rewrite stored content. `QuizQuestionText` takes `text` as a prop and
 * returns nodes — keep it that way.
 *
 * Deliberately narrow, per the shape actually observed: the segment must carry no newline of its own
 * (a compliant question already renders correctly and must bypass this entirely), must end in a
 * sentence-final `?`, and must leave a non-empty statement body behind.
 */
function splitTrailingInterrogative(segment: string): readonly [string, string] | null {
  if (segment.includes("\n")) return null;
  const trimmed = segment.trim();
  if (!trimmed.endsWith("?")) return null;
  const match = /^([\s\S]*[.!?])\s+([^.!?]*\?)$/.exec(trimmed);
  if (!match) return null;
  const body = match[1].trim();
  const question = match[2].trim();
  if (!body || !question) return null;
  return [body, question] as const;
}

function renderLines(text: string): React.ReactNode {
  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
  if (lines.length <= 1) return renderMathText(text);
  return (
    <>
      {lines.map((line, i) => (
        <span key={i} className={i > 0 ? "mt-1 block" : undefined}>{renderMathText(line)}</span>
      ))}
    </>
  );
}

export function QuizQuestionText({ text }: { text: string }) {
  const parts = text.split(STATEMENT_RE);

  // parts.length < 3 means no Statement label was found
  if (parts.length < 3) {
    const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
    if (lines.length <= 1) return renderMathText(text);
    return (
      <>
        {lines.map((line, i) => (
          <span key={i} className={i > 0 ? "mt-1.5 block" : undefined}>{renderMathText(line)}</span>
        ))}
      </>
    );
  }

  const nodes: React.ReactNode[] = [];

  if (parts[0].trim()) {
    nodes.push(<span key="0">{renderLines(parts[0].trim())}</span>);
  }

  for (let i = 1; i + 1 < parts.length; i += 2) {
    // Only the final segment can carry the question the splitter would otherwise swallow: every
    // earlier one is bounded by the next Statement label.
    const isFinalSegment = i + 3 >= parts.length;
    const split = isFinalSegment ? splitTrailingInterrogative(parts[i + 1]) : null;
    nodes.push(
      <span key={i} className="mt-1.5 block">
        <span className="font-semibold">{parts[i]}</span>
        {renderLines(split ? split[0] : parts[i + 1])}
      </span>
    );
    if (split) {
      nodes.push(
        <span key={`${i}-question`} className="mt-1.5 block">{renderMathText(split[1])}</span>
      );
    }
  }

  return <>{nodes}</>;
}
