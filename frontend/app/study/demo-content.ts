import type { ReviewResponse } from "@/lib/api";

export const DEMO_GENERATION_DELAY_MS = 1200;

export const DEMO_NOTES = `Photosynthesis is the process plants use to convert sunlight into energy.
Plants absorb sunlight using chlorophyll in their leaves. Carbon dioxide and water are used to produce glucose, which provides energy for the plant. Oxygen is released as a byproduct.`;

export const DEMO_REVIEW_RESULT: ReviewResponse = {
  id: "demo-review",
  inputType: "text",
  extractedText: null,
  title: "Photosynthesis Fundamentals",
  summary:
    "Photosynthesis allows plants to convert sunlight into usable chemical energy. Chlorophyll helps plants capture light energy in their leaves. Plants combine carbon dioxide and water to produce glucose, which supports growth and metabolism. Oxygen is released as a byproduct of this process. This process is essential for both plant life and Earth's atmosphere.",
  keyConcepts: [
    "Photosynthesis",
    "Chlorophyll",
    "Sunlight absorption",
    "Carbon dioxide and water",
    "Glucose production",
    "Oxygen byproduct",
  ],
  quiz: [
    {
      question: "What is the main purpose of photosynthesis in plants?",
      choices: [
        "To absorb minerals from soil",
        "To convert sunlight into chemical energy",
        "To release carbon dioxide",
        "To store oxygen in roots",
      ],
      answer: "To convert sunlight into chemical energy",
      explanation:
        "Photosynthesis transforms light energy into glucose, which plants use for energy and growth.",
    },
    {
      question: "Which molecule helps plants absorb sunlight?",
      choices: ["Glucose", "Oxygen", "Chlorophyll", "Water"],
      answer: "Chlorophyll",
      explanation:
        "Chlorophyll is the pigment in leaves that captures light energy for photosynthesis.",
    },
    {
      question: "Which inputs are used to form glucose during photosynthesis?",
      choices: [
        "Oxygen and glucose",
        "Carbon dioxide and water",
        "Nitrogen and sunlight",
        "Chlorophyll and oxygen",
      ],
      answer: "Carbon dioxide and water",
      explanation:
        "Plants use carbon dioxide from air and water from roots to build glucose.",
    },
    {
      question: "What is released as a byproduct of photosynthesis?",
      choices: ["Oxygen", "Glucose", "Chlorophyll", "Carbon dioxide"],
      answer: "Oxygen",
      explanation:
        "As plants make glucose, oxygen is produced and released into the air.",
    },
    {
      question: "Why is glucose important for plants?",
      choices: [
        "It colors leaves green",
        "It provides energy for plant processes",
        "It replaces chlorophyll",
        "It removes oxygen from leaves",
      ],
      answer: "It provides energy for plant processes",
      explanation:
        "Glucose stores chemical energy that supports plant growth, repair, and metabolism.",
    },
  ],
  meta: {
    ocrConfidence: null,
    latencyMs: DEMO_GENERATION_DELAY_MS,
  },
};
