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
    docxExportsPerMonth?: number | null;
    pdfExportsPerMonth?: number | null;
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
    docxExportsUsed?: number;
    pdfExportsUsed?: number;
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
    docxExportsRemaining?: number | null;
    pdfExportsRemaining?: number | null;
  };
  features: {
    adaptivePracticeAvailable: boolean;
    interviewPracticeAvailable?: boolean;
    fileUploadAvailable: boolean;
    ocrAvailable: boolean;
    exportAvailable?: boolean;
  };
};
