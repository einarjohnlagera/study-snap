"use client";

export type MePlanResponse = {
  plan: "FREE" | "PREMIUM";
  usageCycle: {
    startsAt: string;
    endsAt: string;
  };
  limits: {
    studyPacksPerMonth: number;
    challengeQuizzesPerMonth: number;
    adaptivePracticePerMonth: number;
    ocrPerMonth: number;
    noteGenerationsPerMonth?: number;
  };
  usage: {
    studyPacksUsed: number;
    challengeQuizzesUsed: number;
    adaptivePracticeUsed: number;
    ocrUsed: number;
    noteGenerationsUsed?: number;
  };
  remaining: {
    studyPacksRemaining: number;
    challengeQuizzesRemaining: number;
    adaptivePracticeRemaining: number;
    ocrRemaining: number;
    noteGenerationsRemaining?: number;
  };
  features: {
    adaptivePracticeAvailable: boolean;
    difficultySelectionAvailable: boolean;
    fileUploadAvailable: boolean;
    ocrAvailable: boolean;
  };
};
