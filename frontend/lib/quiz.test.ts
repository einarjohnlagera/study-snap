import {
  getDisplayedQuizChoices,
  isMultiSelectSelectionCorrect,
  resolveQuizCorrectIndex,
  resolveMultiSelectCorrectIndices,
  sanitizeQuizChoiceText,
  serializeSelectedChoiceIndexRecord,
  toSelectedMultiChoiceIndicesRecord,
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

  it("keeps True/False choices in original order", () => {
    const displayedChoices = getDisplayedQuizChoices({
      question: "Ohm's Law states that voltage is directly proportional to current.",
      choices: ["True", "False"],
      correctIndex: 0,
      questionFormat: "TRUE_FALSE",
      explanation: "Ohm's Law relates voltage, current, and resistance.",
    });

    expect(displayedChoices).toEqual([
      { displayIndex: 0, canonicalIndex: 0, label: "A", text: "True" },
      { displayIndex: 1, canonicalIndex: 1, label: "B", text: "False" },
    ]);
  });

  it("resolves multi-select fallback correct index from correctIndices", () => {
    const item = {
      question: "Which properties describe acids?",
      choices: ["Donate protons", "Taste bitter", "Turn blue litmus red", "Release hydroxide ions"],
      correctIndex: -1,
      correctIndices: [0, 2],
      questionFormat: "MULTI_SELECT" as const,
      explanation: "Acids donate protons and turn blue litmus red.",
    };

    expect(resolveQuizCorrectIndex(item)).toBe(0);
    expect(resolveMultiSelectCorrectIndices(item)).toEqual([0, 2]);
  });

  it("checks multi-select answers as all-or-nothing exact sets", () => {
    const item = {
      question: "Which properties describe acids?",
      choices: ["Donate protons", "Taste bitter", "Turn blue litmus red", "Release hydroxide ions"],
      correctIndex: 0,
      correctIndices: [0, 2],
      questionFormat: "MULTI_SELECT" as const,
      explanation: "Acids donate protons and turn blue litmus red.",
    };

    expect(isMultiSelectSelectionCorrect(item, [2, 0])).toBe(true);
    expect(isMultiSelectSelectionCorrect(item, [0, 1, 2])).toBe(false);
    expect(isMultiSelectSelectionCorrect(item, [0])).toBe(false);
    expect(isMultiSelectSelectionCorrect(item, [])).toBe(false);
  });

  it("normalizes multi-select session selections into canonical choice index arrays", () => {
    const quiz = [
      {
        question: "Which properties describe acids?",
        choices: ["A. Donate protons", "B) Taste bitter", "C. Turn blue litmus red", "D) Release hydroxide ions"],
        correctIndex: 0,
        correctIndices: [0, 2],
        questionFormat: "MULTI_SELECT" as const,
        explanation: "Acids donate protons and turn blue litmus red.",
      },
    ];

    expect(toSelectedMultiChoiceIndicesRecord({ 0: ["C. Turn blue litmus red", "A"] }, quiz)).toEqual({ 0: [0, 2] });
  });

  it("keeps standard MCQ deterministic shuffle unchanged", () => {
    const displayedChoices = getDisplayedQuizChoices({
      question: "What is the derivative of sin(x)?",
      choices: ["cos(x)", "-cos(x)", "-sin(x)", "tan(x)"],
      correctIndex: 0,
      explanation: "The derivative of sin(x) is cos(x).",
    });

    expect(displayedChoices.map((choice) => choice.canonicalIndex)).toEqual([0, 2, 3, 1]);
    expect(displayedChoices.map((choice) => choice.text)).toEqual(["cos(x)", "-sin(x)", "tan(x)", "-cos(x)"]);
  });

  it("sanitizes choice text without changing non-prefixed answers", () => {
    expect(sanitizeQuizChoiceText("A. Encapsulation")).toBe("Encapsulation");
    expect(sanitizeQuizChoiceText("B) Abstraction")).toBe("Abstraction");
    expect(sanitizeQuizChoiceText("Cost Accounting")).toBe("Cost Accounting");
  });
});
