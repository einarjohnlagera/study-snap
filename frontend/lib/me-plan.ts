"use client";

export type MePlanResponse = {
  plan: "FREE" | "PLUS" | "PRO";
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
    exportsPerMonth?: number | null;
  };
  usage: {
    studyPacksUsed: number;
    challengeQuizzesUsed: number;
    adaptivePracticeUsed: number;
    ocrUsed: number;
    noteGenerationsUsed?: number;
    exportsUsed?: number;
  };
  remaining: {
    studyPacksRemaining: number;
    challengeQuizzesRemaining: number;
    adaptivePracticeRemaining: number;
    ocrRemaining: number;
    noteGenerationsRemaining?: number;
    exportsRemaining?: number | null;
  };
  features: {
    adaptivePracticeAvailable: boolean;
    difficultySelectionAvailable: boolean;
    fileUploadAvailable: boolean;
    ocrAvailable: boolean;
    exportAvailable?: boolean;
  };
};
