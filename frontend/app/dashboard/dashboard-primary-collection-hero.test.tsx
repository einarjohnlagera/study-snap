import { render, screen } from "@testing-library/react";
import { DashboardPrimaryCollectionHero } from "./dashboard-primary-collection-hero";
import type { GoalCollectionDetailResponse } from "@/lib/api";

function child(id: string, title: string, term: { label: string; order: number } | null) {
  return {
    collectionId: id,
    title,
    description: null,
    termLabel: term?.label ?? null,
    termOrder: term?.order ?? null,
    itemCount: 3,
    overallReadinessPercentage: 0,
    masteredConcepts: 0,
    dueConcepts: 0,
    notPracticedConcepts: 4,
    totalConcepts: 4,
    todaysConceptBudget: null,
  };
}

function goal(children: ReturnType<typeof child>[]): GoalCollectionDetailResponse {
  return {
    collectionId: "goal-1",
    title: "BSCS First Year",
    courseProgram: null,
    childCount: children.length,
    overallReadinessPercentage: 0,
    masteredConcepts: 0,
    dueConcepts: 0,
    notPracticedConcepts: 8,
    totalConcepts: 8,
    children,
  } as unknown as GoalCollectionDetailResponse;
}

describe("DashboardPrimaryCollectionHero current step", () => {
  it("starts the first Subject in DISPLAY order for a termed Year, not sibling order", () => {
    render(
      <DashboardPrimaryCollectionHero
        profileType="STUDENT"
        goal={goal([
          child("s2", "Algorithms", { label: "Second Semester", order: 2 }),
          child("s1", "Programming I", { label: "First Semester", order: 1 }),
        ])}
      />,
    );

    expect(screen.getByText("Start Programming I")).toBeInTheDocument();
  });

  it("keeps sibling order when no child has a term (Review Set behaviour unchanged)", () => {
    render(
      <DashboardPrimaryCollectionHero
        profileType="STUDENT"
        goal={goal([child("b", "Second in siblings", null), child("a", "Third in siblings", null)])}
      />,
    );

    expect(screen.getByText("Start Second in siblings")).toBeInTheDocument();
  });
});
