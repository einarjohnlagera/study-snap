import {
  getDisplayedQuizChoices,
  resolveQuizCorrectIndex,
  sanitizeQuizChoiceText,
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

  it("strips hardcoded choice labels before rendering displayed choices", () => {
    const displayedChoices = getDisplayedQuizChoices({
      question: "Which OOP principle hides implementation details?",
      choices: ["A. Encapsulation", "B) Abstraction", "C. Inheritance", "D) Polymorphism"],
      correctIndex: 0,
      explanation: "Encapsulation hides implementation details.",
    });

    expect(displayedChoices.map((choice) => choice.text)).toEqual(expect.arrayContaining([
      "Encapsulation",
      "Abstraction",
      "Inheritance",
      "Polymorphism",
    ]));
    expect(displayedChoices.some((choice) => /^[A-D][.)]/.test(choice.text))).toBe(false);
  });

  it("resolves legacy letter answers against sanitized choices", () => {
    expect(
      resolveQuizCorrectIndex({
        question: "Which OOP principle hides implementation details?",
        choices: ["A. Encapsulation", "B) Abstraction", "C. Inheritance", "D) Polymorphism"],
        correctIndex: -1,
        answer: "A",
        explanation: "Encapsulation hides implementation details.",
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

  it("normalizes legacy prefixed session selections into canonical choice indexes", () => {
    const quiz = [
      {
        question: "Which OOP principle hides implementation details?",
        choices: ["A. Encapsulation", "B) Abstraction", "C. Inheritance", "D) Polymorphism"],
        correctIndex: 0,
        explanation: "Encapsulation hides implementation details.",
      },
    ];

    expect(toSelectedChoiceIndexRecord({ 0: "A. Encapsulation" }, quiz)).toEqual({ 0: 0 });
    expect(toSelectedChoiceIndexRecord({ 0: "A" }, quiz)).toEqual({ 0: 0 });
    expect(toSelectedChoiceIndexRecord({ 0: "A)" }, quiz)).toEqual({ 0: 0 });
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

  it("sanitizes choice text without changing non-prefixed answers", () => {
    expect(sanitizeQuizChoiceText("A. Encapsulation")).toBe("Encapsulation");
    expect(sanitizeQuizChoiceText("B) Abstraction")).toBe("Abstraction");
    expect(sanitizeQuizChoiceText("Cost Accounting")).toBe("Cost Accounting");
  });
});
