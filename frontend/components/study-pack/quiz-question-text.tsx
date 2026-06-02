// Splits "Statement N: ..." patterns onto separate labeled lines.
// Falls back to plain text when no Statement pattern is found.
const STATEMENT_RE = /(Statement\s+(?:\d+|[IVX]+)\s*:)/gi;

export function QuizQuestionText({ text }: { text: string }) {
  const parts = text.split(STATEMENT_RE);

  // parts.length < 3 means no Statement label was found
  if (parts.length < 3) return <>{text}</>;

  const nodes: React.ReactNode[] = [];

  if (parts[0].trim()) {
    nodes.push(<span key="0">{parts[0].trim()}</span>);
  }

  for (let i = 1; i + 1 < parts.length; i += 2) {
    nodes.push(
      <span key={i} className="mt-1.5 block">
        <span className="font-semibold">{parts[i]}</span>
        {parts[i + 1]}
      </span>
    );
  }

  return <>{nodes}</>;
}
