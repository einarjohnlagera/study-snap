import React from "react";

// Splits "Statement N: ..." patterns onto separate labeled lines.
// Falls back to newline-aware plain text when no Statement pattern is found.
const STATEMENT_RE = /(Statement\s+(?:\d+|[IVX]+)\s*:)/gi;

function renderLines(text: string): React.ReactNode {
  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
  if (lines.length <= 1) return text;
  return (
    <>
      {lines.map((line, i) => (
        <span key={i} className={i > 0 ? "mt-1 block" : undefined}>{line}</span>
      ))}
    </>
  );
}

export function QuizQuestionText({ text }: { text: string }) {
  const parts = text.split(STATEMENT_RE);

  // parts.length < 3 means no Statement label was found
  if (parts.length < 3) {
    const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
    if (lines.length <= 1) return <>{text}</>;
    return (
      <>
        {lines.map((line, i) => (
          <span key={i} className={i > 0 ? "mt-1.5 block" : undefined}>{line}</span>
        ))}
      </>
    );
  }

  const nodes: React.ReactNode[] = [];

  if (parts[0].trim()) {
    nodes.push(<span key="0">{renderLines(parts[0].trim())}</span>);
  }

  for (let i = 1; i + 1 < parts.length; i += 2) {
    nodes.push(
      <span key={i} className="mt-1.5 block">
        <span className="font-semibold">{parts[i]}</span>
        {renderLines(parts[i + 1])}
      </span>
    );
  }

  return <>{nodes}</>;
}
