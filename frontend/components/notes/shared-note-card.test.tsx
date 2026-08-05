import { render, screen } from "@testing-library/react";
import { resolveCardExcerpt, SharedNoteCard } from "./shared-note-card";

describe("resolveCardExcerpt", () => {
  it("prefers the note preview when it clears the minimum length", () => {
    const excerpt = resolveCardExcerpt(
      "This note body is long enough to count as a real preview.",
      "A short summary.",
    );
    expect(excerpt).toEqual({
      kind: "note",
      text: "This note body is long enough to count as a real preview.",
    });
  });

  it("falls back to the summary when the note preview is an empty string", () => {
    const excerpt = resolveCardExcerpt("", "A study-ready summary.");
    expect(excerpt).toEqual({ kind: "summary", text: "A study-ready summary." });
  });

  it("falls back to the summary when the note preview is null or undefined", () => {
    expect(resolveCardExcerpt(null, "A study-ready summary.")).toEqual({
      kind: "summary",
      text: "A study-ready summary.",
    });
    expect(resolveCardExcerpt(undefined, "A study-ready summary.")).toEqual({
      kind: "summary",
      text: "A study-ready summary.",
    });
  });

  it("falls back to the summary when the note preview is a stub below the minimum length", () => {
    const excerpt = resolveCardExcerpt("Too short", "A study-ready summary.");
    expect(excerpt).toEqual({ kind: "summary", text: "A study-ready summary." });
  });

  it("treats exactly 40 characters as long enough", () => {
    const fortyChars = "a".repeat(40);
    expect(resolveCardExcerpt(fortyChars, "fallback")).toEqual({ kind: "note", text: fortyChars });
  });

  it("treats 39 characters as too short", () => {
    const thirtyNineChars = "a".repeat(39);
    expect(resolveCardExcerpt(thirtyNineChars, "fallback")).toEqual({ kind: "summary", text: "fallback" });
  });

  it("collapses internal whitespace before measuring length", () => {
    const spacedOut = "a  a  a  a  a  a  a  a  a  a  a  a  a  a  a  a  a  a  a  a"; // 20 letters, lots of whitespace
    // Collapsed: "a a a a a a a a a a a a a a a a a a a a" = 39 chars, still under the threshold.
    expect(resolveCardExcerpt(spacedOut, "fallback")).toEqual({ kind: "summary", text: "fallback" });
  });

  it("returns none when both the note and summary are empty", () => {
    expect(resolveCardExcerpt("", "")).toEqual({ kind: "none" });
  });

  it("returns none when both the note and summary are null or undefined", () => {
    expect(resolveCardExcerpt(null, null)).toEqual({ kind: "none" });
    expect(resolveCardExcerpt(undefined, undefined)).toEqual({ kind: "none" });
  });
});

describe("SharedNoteCard", () => {
  const longNotePreview = "This note body is long enough to count as a real preview for the card.";

  it("shows joined programs with an overflow count instead of the legacy program", () => {
    render(
      <SharedNoteCard
        title="Engineering Mathematics"
        courseProgram="Engineering"
        applicablePrograms={["Civil Engineering", "Electrical Engineering", "Mechanical Engineering"]}
        subject="Algebra"
        tags={[]}
        contentPreview={longNotePreview}
      />,
    );

    expect(screen.getByText("Civil Engineering")).toBeInTheDocument();
    expect(screen.getByText("Electrical Engineering")).toBeInTheDocument();
    expect(screen.queryByText("Mechanical Engineering")).not.toBeInTheDocument();
    expect(screen.getByText("+1")).toHaveAccessibleName("1 more applicable program");
    expect(screen.queryByText("Engineering")).not.toBeInTheDocument();
  });

  it("falls back to the legacy program when no joined programs are projected", () => {
    render(
      <SharedNoteCard
        title="Software Foundations"
        courseProgram="Software Engineering"
        applicablePrograms={[]}
        subject="Architecture"
        tags={[]}
        contentPreview={longNotePreview}
      />,
    );

    expect(screen.getByText("Software Engineering")).toBeInTheDocument();
  });

  it("renders the note preview without a Summary label when it wins the cascade", () => {
    render(
      <SharedNoteCard
        title="Cell Structure"
        subject="Biology"
        tags={[]}
        contentPreview={longNotePreview}
        summaryPreview="A short summary."
      />,
    );

    expect(screen.getByText(longNotePreview)).toBeInTheDocument();
    expect(screen.queryByText("A short summary.")).not.toBeInTheDocument();
    expect(screen.queryByText("Summary")).not.toBeInTheDocument();
  });

  it("renders the summary preview with a visible Summary label when it falls back", () => {
    render(
      <SharedNoteCard
        title="Cell Structure"
        subject="Biology"
        tags={[]}
        contentPreview=""
        summaryPreview="A study-ready summary of cell structure."
      />,
    );

    expect(screen.getByText("Summary")).toBeInTheDocument();
    expect(screen.getByText("A study-ready summary of cell structure.")).toBeInTheDocument();
  });

  it("renders no excerpt block when both the note and summary are empty", () => {
    const { container } = render(
      <SharedNoteCard title="Cell Structure" subject="Biology" tags={[]} contentPreview="" summaryPreview="" />,
    );

    expect(screen.queryByText("Summary")).not.toBeInTheDocument();
    expect(container.querySelectorAll("p").length).toBe(0);
  });

  it("falls back to Untitled note when the title is empty", () => {
    render(<SharedNoteCard title="" subject="Biology" tags={[]} contentPreview={longNotePreview} />);
    expect(screen.getByText("Untitled note")).toBeInTheDocument();
  });

  it("shows an overflow count when tags exceed the display limit", () => {
    render(
      <SharedNoteCard
        title="Cell Structure"
        subject="Biology"
        tags={["cells", "membranes", "organelles", "mitosis"]}
        contentPreview={longNotePreview}
        tagDisplayLimit={2}
      />,
    );

    expect(screen.getByText("cells")).toBeInTheDocument();
    expect(screen.getByText("membranes")).toBeInTheDocument();
    expect(screen.queryByText("organelles")).not.toBeInTheDocument();
    expect(screen.getByText("+2")).toBeInTheDocument();
  });

  it("shows a No tags placeholder when there are no tags", () => {
    render(<SharedNoteCard title="Cell Structure" subject="Biology" tags={[]} contentPreview={longNotePreview} />);
    expect(screen.getByText("No tags")).toBeInTheDocument();
  });

  it("renders view and copy counts when provided, but hides the metrics row when both are absent", () => {
    const { rerender } = render(
      <SharedNoteCard
        title="Cell Structure"
        subject="Biology"
        tags={[]}
        contentPreview={longNotePreview}
        viewCount={12}
        copyCount={5}
      />,
    );

    expect(screen.getByText("12 views")).toBeInTheDocument();
    expect(screen.getByText("5 copies")).toBeInTheDocument();

    rerender(
      <SharedNoteCard title="Cell Structure" subject="Biology" tags={[]} contentPreview={longNotePreview} />,
    );
    expect(screen.queryByText(/views|copies/)).not.toBeInTheDocument();
  });
});
