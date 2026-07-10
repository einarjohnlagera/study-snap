import { render, screen } from "@testing-library/react";
import { ReadinessSummary } from "./readiness-summary";

describe("ReadinessSummary", () => {
  it("renders the overall ring and subject readiness bars", () => {
    render(
      <ReadinessSummary
        overallReadinessPercentage={67}
        totalConcepts={6}
        masteredConcepts={4}
        dueConcepts={1}
        notPracticedConcepts={1}
        subjects={[
          {
            subject: "Biology",
            totalConcepts: 3,
            masteredConcepts: 2,
            dueConcepts: 1,
            notPracticedConcepts: 0,
            masteryPercentage: 67,
          },
          {
            subject: "Chemistry",
            totalConcepts: 3,
            masteredConcepts: 0,
            dueConcepts: 0,
            notPracticedConcepts: 3,
            masteryPercentage: 0,
          },
        ]}
      />,
    );

    expect(screen.getByText("67%")).toBeInTheDocument();
    expect(screen.getByText((content) => (
      content.includes("4 mastered") && content.includes("1 due") && content.includes("1 not started")
    ))).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Biology readiness" })).toHaveAttribute("aria-valuenow", "67");
    expect(screen.getByText("67% ready")).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Chemistry readiness" })).toHaveAttribute("aria-valuenow", "0");
    expect(screen.getByText("Not started")).toBeInTheDocument();
  });

  it("renders the empty readiness guidance state", () => {
    render(
      <ReadinessSummary
        overallReadinessPercentage={0}
        totalConcepts={0}
        masteredConcepts={0}
        dueConcepts={0}
        notPracticedConcepts={0}
        subjects={[]}
      />,
    );

    expect(screen.getByText("No readiness yet")).toBeInTheDocument();
    expect(screen.getAllByText("Generate Study Packs and practice to see readiness.")).toHaveLength(2);
  });

  it("renders compact readiness for note detail reuse", () => {
    render(
      <ReadinessSummary
        variant="compact"
        title="Note readiness"
        overallReadinessPercentage={50}
        totalConcepts={4}
        masteredConcepts={2}
        dueConcepts={1}
        notPracticedConcepts={1}
        subjects={[]}
      />,
    );

    expect(screen.getByText("Note readiness")).toBeInTheDocument();
    expect(screen.getByText((content) => (
      content.includes("50% ready") && content.includes("2/4 mastered") && content.includes("1 due")
    ))).toBeInTheDocument();
    expect(screen.getByText("1 not started")).toBeInTheDocument();
  });

  it("renders the compact countdown slot only when provided", () => {
    const { rerender } = render(
      <ReadinessSummary
        variant="compact"
        title="Goal readiness"
        overallReadinessPercentage={50}
        totalConcepts={4}
        masteredConcepts={2}
        dueConcepts={1}
        notPracticedConcepts={1}
        subjects={[]}
        countdown="3 weeks until Dec 1, 2026 · 11 concepts remaining"
      />,
    );

    expect(screen.getByText("3 weeks until Dec 1, 2026 · 11 concepts remaining")).toBeInTheDocument();

    rerender(
      <ReadinessSummary
        variant="compact"
        title="Goal readiness"
        overallReadinessPercentage={50}
        totalConcepts={4}
        masteredConcepts={2}
        dueConcepts={1}
        notPracticedConcepts={1}
        subjects={[]}
      />,
    );

    expect(screen.queryByText("3 weeks until Dec 1, 2026 · 11 concepts remaining")).not.toBeInTheDocument();
    expect(screen.getByText("1 not started")).toBeInTheDocument();
  });
});
