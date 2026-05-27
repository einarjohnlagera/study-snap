import { fireEvent, render, screen } from "@testing-library/react";
import { QuizMatchingGroup } from "./quiz-matching-group";

const matchingItems = [
  {
    question: "Pressure applied to a confined fluid is transmitted equally.",
    choices: ["Bernoulli's Principle", "Pascal's Law", "Archimedes' Principle", "Continuity Equation"],
    correctIndex: 1,
    questionFormat: "MATCHING" as const,
    questionGroup: "group-1",
    explanation: "Pascal's Law describes pressure transmission in confined fluids.",
  },
  {
    question: "Buoyant force equals the weight of fluid displaced.",
    choices: ["Bernoulli's Principle", "Pascal's Law", "Archimedes' Principle", "Continuity Equation"],
    correctIndex: 2,
    questionFormat: "MATCHING" as const,
    questionGroup: "group-1",
    explanation: "Archimedes' Principle describes buoyant force.",
  },
];

describe("QuizMatchingGroup", () => {
  it("renders shared options once and reports selections with original question indexes", () => {
    const handleSelectChoice = jest.fn();

    render(
      <QuizMatchingGroup
        items={matchingItems}
        groupStartIndex={4}
        selectedChoices={{}}
        revealAnswer={false}
        onSelectChoice={handleSelectChoice}
      />,
    );

    expect(screen.getByText("A.")).toBeInTheDocument();
    expect(screen.getAllByText("Bernoulli's Principle")).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: /Item 1 choice B/i }));

    expect(handleSelectChoice).toHaveBeenCalledWith(4, 1);
  });

  it("highlights correct and incorrect row choices on reveal", () => {
    render(
      <QuizMatchingGroup
        items={matchingItems}
        groupStartIndex={0}
        selectedChoices={{ 0: 0, 1: 2 }}
        revealAnswer
        onSelectChoice={() => undefined}
      />,
    );

    expect(screen.getByRole("button", { name: /Item 1 choice B.*Correct/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Item 1 choice A.*Incorrect/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Item 2 choice C.*Correct/i })).toBeInTheDocument();
  });
});
