import { buildFlashcardDeck, buildMatchedFlashcards } from "./flashcards";
import type { QuizItem } from "@/lib/api";

const quiz: QuizItem[] = [
  {
    question: "What is a cell?",
    choices: ["A", "B", "C", "D"],
    correctIndex: 0,
    concept: " cell structure ",
    explanation: "Cells are the basic unit of life.",
  },
  {
    question: "What stores genetic information?",
    choices: ["A", "B", "C", "D"],
    correctIndex: 0,
    concept: "DNA",
    explanation: "DNA stores inherited genetic instructions.",
  },
];

describe("flashcard concept matching", () => {
  it("keeps Flashcards fallback cards while matching explanations fuzzily", () => {
    expect(buildFlashcardDeck(["Cell", "DNA", "Mitosis"], quiz)).toEqual([
      { concept: "Cell", explanation: "Cells are the basic unit of life." },
      { concept: "DNA", explanation: "DNA stores inherited genetic instructions." },
      { concept: "Mitosis", explanation: null },
    ]);
  });

  it("returns only explanation-backed cards for Memorization eligibility", () => {
    expect(buildMatchedFlashcards(["Cell", "DNA", "Mitosis"], quiz)).toEqual([
      { concept: "Cell", explanation: "Cells are the basic unit of life." },
      { concept: "DNA", explanation: "DNA stores inherited genetic instructions." },
    ]);
  });
});
