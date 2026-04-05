import { fireEvent, render, screen } from "@testing-library/react";
import { QuizChoiceList } from "./quiz-choice-list";

describe("QuizChoiceList", () => {
  it("renders letters from displayed order and keeps the order stable across rerenders", () => {
    const handleSelectChoice = jest.fn();
    const { rerender } = render(
      <QuizChoiceList
        questionKey="What is the derivative of sin(x)?"
        choices={["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"]}
        correctIndex={0}
        selectedChoiceIndex={null}
        revealAnswer={false}
        onSelectChoice={handleSelectChoice}
      />,
    );

    const firstRenderButtons = screen.getAllByRole("button");
    expect(firstRenderButtons).toHaveLength(4);
    expect(firstRenderButtons.map((button) => button.textContent)).toEqual(
      expect.arrayContaining([
        expect.stringMatching(/^A\./),
        expect.stringMatching(/^B\./),
        expect.stringMatching(/^C\./),
        expect.stringMatching(/^D\./),
      ]),
    );
    const firstRenderOrder = firstRenderButtons.map((button) => button.textContent);

    fireEvent.click(screen.getByRole("button", { name: /-sin\(x\)/i }));
    expect(handleSelectChoice).toHaveBeenCalledWith(2);

    rerender(
      <QuizChoiceList
        questionKey="What is the derivative of sin(x)?"
        choices={["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"]}
        correctIndex={0}
        selectedChoiceIndex={null}
        revealAnswer={false}
        onSelectChoice={handleSelectChoice}
      />,
    );

    expect(screen.getAllByRole("button").map((button) => button.textContent)).toEqual(firstRenderOrder);
  });

  it("keeps correctness aligned after display shuffling", () => {
    render(
      <QuizChoiceList
        questionKey="What is the derivative of sin(x)?"
        choices={["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"]}
        correctIndex={0}
        selectedChoiceIndex={3}
        revealAnswer
      />,
    );

    const choiceButtons = screen.getAllByRole("button");
    expect(choiceButtons.find((button) => button.textContent?.includes("cos(x)") && button.textContent?.includes("Correct"))).toBeTruthy();
    expect(choiceButtons.find((button) => button.textContent?.includes("tan(x)") && button.textContent?.includes("Incorrect"))).toBeTruthy();
  });
});
