import {
  WEAK_CONCEPT_THRESHOLD,
  computeConceptBreakdown,
  computeScore,
  computeWeakConcepts,
  mapPerformanceLevel,
} from "@/lib/challenge-quiz-results";
import type { QuizItem } from "@/lib/api";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeItem(
  question: string,
  correctIndex: number,
  concept: string,
): QuizItem {
  return {
    question,
    choices: ["A answer", "B answer", "C answer", "D answer"],
    correctIndex,
    concept,
    explanation: "Explanation.",
  };
}

const QUIZ_3: QuizItem[] = [
  makeItem("Q1", 0, "Encapsulation"),
  makeItem("Q2", 1, "Inheritance"),
  makeItem("Q3", 0, "Encapsulation"),
];

const QUIZ_SINGLE: QuizItem[] = [makeItem("Single Q", 2, "Polymorphism")];

const QUIZ_MULTI_CONCEPT: QuizItem[] = [
  makeItem("Q1", 0, "Encapsulation"),
  makeItem("Q2", 1, "Inheritance"),
  makeItem("Q3", 0, "Encapsulation"),
  makeItem("Q4", 1, "Inheritance"),
  makeItem("Q5", 2, "Polymorphism"),
];

// ---------------------------------------------------------------------------
// computeScore
// ---------------------------------------------------------------------------

describe("computeScore", () => {
  it("counts all correct when every answer is right", () => {
    const selected = { 0: 0, 1: 1, 2: 0 };
    expect(computeScore(QUIZ_3, selected)).toEqual({
      correctAnswers: 3,
      totalQuestions: 3,
      scorePercentage: 100,
    });
  });

  it("counts zero correct when all answers are wrong", () => {
    const selected = { 0: 1, 1: 0, 2: 1 };
    expect(computeScore(QUIZ_3, selected)).toEqual({
      correctAnswers: 0,
      totalQuestions: 3,
      scorePercentage: 0,
    });
  });

  it("counts partial correct for mixed answers", () => {
    const selected = { 0: 0, 1: 0, 2: 0 }; // Q1 right (0→0✓), Q2 wrong (1→0✗), Q3 right (0→0✓)
    const result = computeScore(QUIZ_3, selected);
    expect(result.correctAnswers).toBe(2);
    expect(result.totalQuestions).toBe(3);
    expect(result.scorePercentage).toBe(67);
  });

  it("treats unanswered questions as incorrect", () => {
    // only Q1 answered
    expect(computeScore(QUIZ_3, { 0: 0 })).toEqual({
      correctAnswers: 1,
      totalQuestions: 3,
      scorePercentage: 33,
    });
  });

  it("handles a single-question quiz — correct", () => {
    expect(computeScore(QUIZ_SINGLE, { 0: 2 })).toEqual({
      correctAnswers: 1,
      totalQuestions: 1,
      scorePercentage: 100,
    });
  });

  it("handles a single-question quiz — wrong", () => {
    expect(computeScore(QUIZ_SINGLE, { 0: 0 })).toEqual({
      correctAnswers: 0,
      totalQuestions: 1,
      scorePercentage: 0,
    });
  });

  it("returns 0 percentage for an empty quiz", () => {
    expect(computeScore([], {})).toEqual({
      correctAnswers: 0,
      totalQuestions: 0,
      scorePercentage: 0,
    });
  });

  it("returns 0 percentage when no answers are selected", () => {
    expect(computeScore(QUIZ_3, {})).toEqual({
      correctAnswers: 0,
      totalQuestions: 3,
      scorePercentage: 0,
    });
  });

  it("scores multi-select questions using exact all-or-nothing matching", () => {
    const quiz: QuizItem[] = [
      {
        question: "Which properties describe acids?",
        choices: ["Donate protons", "Taste bitter", "Turn blue litmus red", "Release hydroxide ions"],
        correctIndex: 0,
        correctIndices: [0, 2],
        questionFormat: "MULTI_SELECT",
        concept: "Acids",
        explanation: "Acids donate protons and turn blue litmus red.",
      },
      makeItem("Single answer", 1, "MCQ"),
    ];

    expect(computeScore(quiz, { 1: 1 }, { 0: [2, 0] })).toEqual({
      correctAnswers: 2,
      totalQuestions: 2,
      scorePercentage: 100,
    });
    expect(computeScore(quiz, { 1: 1 }, { 0: [0] }).correctAnswers).toBe(1);
  });
});

// ---------------------------------------------------------------------------
// mapPerformanceLevel
// ---------------------------------------------------------------------------

describe("mapPerformanceLevel", () => {
  it("returns Excellent for 100%", () => {
    expect(mapPerformanceLevel(100)).toBe("Excellent");
  });

  it("returns Excellent for 90%", () => {
    expect(mapPerformanceLevel(90)).toBe("Excellent");
  });

  it("returns Good for 89%", () => {
    expect(mapPerformanceLevel(89)).toBe("Good");
  });

  it("returns Good for 75%", () => {
    expect(mapPerformanceLevel(75)).toBe("Good");
  });

  it("returns Fair for 74%", () => {
    expect(mapPerformanceLevel(74)).toBe("Fair");
  });

  it("returns Fair for 50%", () => {
    expect(mapPerformanceLevel(50)).toBe("Fair");
  });

  it("returns Needs Improvement for 49%", () => {
    expect(mapPerformanceLevel(49)).toBe("Needs Improvement");
  });

  it("returns Needs Improvement for 0%", () => {
    expect(mapPerformanceLevel(0)).toBe("Needs Improvement");
  });
});

// ---------------------------------------------------------------------------
// computeConceptBreakdown
// ---------------------------------------------------------------------------

describe("computeConceptBreakdown", () => {
  it("groups questions by concept and computes per-concept accuracy", () => {
    // Q1 (Encapsulation) correct, Q2 (Inheritance) correct, Q3 (Encapsulation) wrong
    const selected = { 0: 0, 1: 1, 2: 1 };
    const breakdown = computeConceptBreakdown(QUIZ_3, selected);

    const encapsulation = breakdown.find((s) => s.concept === "Encapsulation");
    expect(encapsulation).toEqual({
      concept: "Encapsulation",
      correctAnswers: 1,
      totalQuestions: 2,
      accuracyPercentage: 50,
    });

    const inheritance = breakdown.find((s) => s.concept === "Inheritance");
    expect(inheritance).toEqual({
      concept: "Inheritance",
      correctAnswers: 1,
      totalQuestions: 1,
      accuracyPercentage: 100,
    });
  });

  it("returns 100% for all-correct answers", () => {
    const selected = { 0: 0, 1: 1, 2: 0, 3: 1, 4: 2 };
    const breakdown = computeConceptBreakdown(QUIZ_MULTI_CONCEPT, selected);
    for (const stat of breakdown) {
      expect(stat.accuracyPercentage).toBe(100);
    }
  });

  it("returns 0% per concept for all-wrong answers", () => {
    const selected = { 0: 1, 1: 0, 2: 1, 3: 0, 4: 0 };
    const breakdown = computeConceptBreakdown(QUIZ_MULTI_CONCEPT, selected);
    for (const stat of breakdown) {
      expect(stat.accuracyPercentage).toBe(0);
    }
  });

  it("groups questions without a concept under Unknown", () => {
    const quizNoConcept: QuizItem[] = [
      {
        question: "Q?",
        choices: ["A", "B", "C", "D"],
        correctIndex: 0,
        explanation: "Exp.",
      },
    ];
    const breakdown = computeConceptBreakdown(quizNoConcept, { 0: 0 });
    expect(breakdown[0]?.concept).toBe("Unknown");
  });

  it("returns an empty array for an empty quiz", () => {
    expect(computeConceptBreakdown([], {})).toEqual([]);
  });

  it("sorts results alphabetically by concept", () => {
    const selected = { 0: 0, 1: 1, 2: 0, 3: 1, 4: 2 };
    const breakdown = computeConceptBreakdown(QUIZ_MULTI_CONCEPT, selected);
    const names = breakdown.map((s) => s.concept);
    expect(names).toEqual([...names].sort());
  });
});

// ---------------------------------------------------------------------------
// computeWeakConcepts
// ---------------------------------------------------------------------------

describe("computeWeakConcepts", () => {
  it(`flags concepts below ${WEAK_CONCEPT_THRESHOLD}% as weak`, () => {
    const breakdown = [
      { concept: "Encapsulation", correctAnswers: 1, totalQuestions: 3, accuracyPercentage: 33 },
      { concept: "Inheritance", correctAnswers: 1, totalQuestions: 2, accuracyPercentage: 50 },
      { concept: "Polymorphism", correctAnswers: 3, totalQuestions: 3, accuracyPercentage: 100 },
    ];
    expect(computeWeakConcepts(breakdown)).toEqual(["Encapsulation", "Inheritance"]);
  });

  it("excludes concepts at exactly the threshold", () => {
    const breakdown = [
      { concept: "Encapsulation", correctAnswers: 3, totalQuestions: 5, accuracyPercentage: 60 },
    ];
    expect(computeWeakConcepts(breakdown)).toEqual([]);
  });

  it("returns all concepts when all are weak", () => {
    const breakdown = [
      { concept: "A", correctAnswers: 0, totalQuestions: 2, accuracyPercentage: 0 },
      { concept: "B", correctAnswers: 0, totalQuestions: 1, accuracyPercentage: 0 },
    ];
    expect(computeWeakConcepts(breakdown)).toEqual(["A", "B"]);
  });

  it("returns an empty array when no concepts are weak", () => {
    const breakdown = [
      { concept: "X", correctAnswers: 4, totalQuestions: 5, accuracyPercentage: 80 },
    ];
    expect(computeWeakConcepts(breakdown)).toEqual([]);
  });

  it("returns an empty array for an empty breakdown", () => {
    expect(computeWeakConcepts([])).toEqual([]);
  });

  it("accepts a custom threshold", () => {
    const breakdown = [
      { concept: "Encapsulation", correctAnswers: 3, totalQuestions: 5, accuracyPercentage: 60 },
      { concept: "Inheritance", correctAnswers: 4, totalQuestions: 5, accuracyPercentage: 80 },
    ];
    // Custom threshold of 70 → both 60 and... wait, 60 < 70 so only Encapsulation
    expect(computeWeakConcepts(breakdown, 70)).toEqual(["Encapsulation"]);
  });
});

// ---------------------------------------------------------------------------
// Integration: end-to-end result derivation from quiz session
// ---------------------------------------------------------------------------

describe("challenge quiz result derivation (end-to-end)", () => {
  it("derives correct results for a mixed-answer session", () => {
    // Correct answers: Q1 (0), Q2 (1), Q3 (0), Q4 (1), Q5 (2)
    // User: Q1 correct, Q2 wrong, Q3 correct, Q4 wrong, Q5 correct → 3/5 = 60%
    const selected = { 0: 0, 1: 0, 2: 0, 3: 0, 4: 2 };
    const score = computeScore(QUIZ_MULTI_CONCEPT, selected);
    expect(score).toEqual({ correctAnswers: 3, totalQuestions: 5, scorePercentage: 60 });
    expect(mapPerformanceLevel(score.scorePercentage)).toBe("Fair");

    const breakdown = computeConceptBreakdown(QUIZ_MULTI_CONCEPT, selected);
    const encapsulation = breakdown.find((s) => s.concept === "Encapsulation");
    const inheritance = breakdown.find((s) => s.concept === "Inheritance");
    const polymorphism = breakdown.find((s) => s.concept === "Polymorphism");
    expect(encapsulation?.accuracyPercentage).toBe(100); // both correct
    expect(inheritance?.accuracyPercentage).toBe(0);     // both wrong
    expect(polymorphism?.accuracyPercentage).toBe(100);  // correct

    const weak = computeWeakConcepts(breakdown);
    expect(weak).toEqual(["Inheritance"]);
  });

  it("identifies no weak concepts when all answers are correct", () => {
    const selected = { 0: 0, 1: 1, 2: 0, 3: 1, 4: 2 };
    const score = computeScore(QUIZ_MULTI_CONCEPT, selected);
    expect(score.scorePercentage).toBe(100);
    expect(mapPerformanceLevel(100)).toBe("Excellent");
    const breakdown = computeConceptBreakdown(QUIZ_MULTI_CONCEPT, selected);
    expect(computeWeakConcepts(breakdown)).toEqual([]);
  });

  it("identifies all concepts as weak when all answers are wrong", () => {
    const selected = { 0: 1, 1: 0, 2: 1, 3: 0, 4: 0 };
    const score = computeScore(QUIZ_MULTI_CONCEPT, selected);
    expect(score.scorePercentage).toBe(0);
    expect(mapPerformanceLevel(0)).toBe("Needs Improvement");
    const breakdown = computeConceptBreakdown(QUIZ_MULTI_CONCEPT, selected);
    const weak = computeWeakConcepts(breakdown);
    expect(weak.length).toBe(breakdown.length);
  });
});
