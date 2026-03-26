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
  };
  usage: {
    studyPacksUsed: number;
    challengeQuizzesUsed: number;
    adaptivePracticeUsed: number;
    ocrUsed: number;
  };
  remaining: {
    studyPacksRemaining: number;
    challengeQuizzesRemaining: number;
    adaptivePracticeRemaining: number;
    ocrRemaining: number;
  };
  features: {
    adaptivePracticeAvailable: boolean;
    difficultySelectionAvailable: boolean;
    fileUploadAvailable: boolean;
    ocrAvailable: boolean;
  };
};
