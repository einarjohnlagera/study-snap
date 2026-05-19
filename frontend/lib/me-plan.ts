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
    interviewPracticePerMonth?: number;
    longExamPerMonth?: number;
    boardExamPerMonth?: number;
    ocrPerMonth: number;
    noteGenerationsPerMonth?: number;
    exportsPerMonth?: number | null;
  };
  usage: {
    studyPacksUsed: number;
    challengeQuizzesUsed: number;
    adaptivePracticeUsed: number;
    interviewPracticeUsed?: number;
    longExamUsed?: number;
    boardExamUsed?: number;
    ocrUsed: number;
    noteGenerationsUsed?: number;
    exportsUsed?: number;
  };
  remaining: {
    studyPacksRemaining: number;
    challengeQuizzesRemaining: number;
    adaptivePracticeRemaining: number;
    interviewPracticeRemaining?: number;
    longExamRemaining?: number;
    boardExamRemaining?: number;
    ocrRemaining: number;
    noteGenerationsRemaining?: number;
    exportsRemaining?: number | null;
  };
  features: {
    adaptivePracticeAvailable: boolean;
    interviewPracticeAvailable?: boolean;
    difficultySelectionAvailable: boolean;
    fileUploadAvailable: boolean;
    ocrAvailable: boolean;
    exportAvailable?: boolean;
  };
};
