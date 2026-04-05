import {
  getDisplayedQuizChoices,
  resolveQuizCorrectIndex,
  serializeSelectedChoiceIndexRecord,
  toSelectedChoiceIndexRecord,
} from "./quiz";

describe("quiz helpers", () => {
  it("restores correctIndex from legacy answer text when needed", () => {
    expect(
      resolveQuizCorrectIndex({
        question: "What controls the cell?",
        choices: ["Nucleus", "Cytoplasm", "Membrane", "Ribosome"],
        correctIndex: -1,
        answer: "Nucleus",
        explanation: "The nucleus controls cell activity.",
      }),
    ).toBe(0);
  });

  it("accepts legacy answerIndex-style payloads", () => {
    expect(
      resolveQuizCorrectIndex({
        question: "What controls the cell?",
        choices: ["Nucleus", "Cytoplasm", "Membrane", "Ribosome"],
        correctIndex: -1,
        answerIndex: 0,
        explanation: "The nucleus controls cell activity.",
      }),
    ).toBe(0);
  });

  it("normalizes legacy string session selections into canonical choice indexes", () => {
    const quiz = [
      {
        question: "What controls the cell?",
        choices: ["Nucleus", "Cytoplasm", "Membrane", "Ribosome"],
        correctIndex: 0,
        explanation: "The nucleus controls cell activity.",
      },
    ];

    expect(toSelectedChoiceIndexRecord({ 0: "Membrane" }, quiz)).toEqual({ 0: 2 });
    expect(serializeSelectedChoiceIndexRecord({ 0: 2 })).toEqual({ 0: 2 });
  });

  it("keeps displayed choice order deterministic for a question", () => {
    const quizItem = {
      question: "What is the derivative of sin(x)?",
      choices: ["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"],
      correctIndex: 0,
      explanation: "The derivative of sin(x) is cos(x).",
    };

    expect(getDisplayedQuizChoices(quizItem)).toEqual(getDisplayedQuizChoices(quizItem));
  });
});
