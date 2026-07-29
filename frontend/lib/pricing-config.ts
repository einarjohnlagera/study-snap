import type { ProfileType } from "@/lib/api";

export type PricingDisplayRegion = "PH" | "DEFAULT";
export type PricingExportPlan = "FREE" | "PLUS" | "PRO";

export const pricingConfig = {
  free: {
    studyPacksPerMonth: 10,
    challengeQuizzesPerMonth: 20,
    adaptivePracticePerMonth: 3,
    askCompanionPerMonth: 0,
    ocrPerMonth: 20,
    docxExportsPerMonth: 2,
    teacherDocxExportsPerMonth: 10,
    pdfExportsPerMonth: 2,
    quizShareLinksPerMonth: 3,
  },
  plus: {
    studyPacksPerMonth: 50,
    challengeQuizzesPerMonth: 100,
    noteGenerationsPerMonth: 25,
    docxExportsPerMonth: 15,
    teacherDocxExportsPerMonth: null,
    pdfExportsPerMonth: 15,
    adaptivePracticePerMonth: 10,
    askCompanionPerMonth: 20,
    quizShareLinksPerMonth: 10,
  },
  pro: {
    studyPacksPerMonth: 100,
    challengeQuizzesPerMonth: 200,
    adaptivePracticePerMonth: 30,
    interviewPracticePerMonth: 10,
    askCompanionPerMonth: 20,
    longExamPerMonth: 12,
    boardExamPerMonth: 10,
    ocrPerMonth: 100,
    noteGenerationsPerMonth: 100,
    docxExportsPerMonth: null,
    teacherDocxExportsPerMonth: null,
    pdfExportsPerMonth: null,
    quizShareLinksPerMonth: null,
  },
  price: {
    PH: {
      currency: "PHP",
      plus: {
        monthly: 179,
        yearly: null,
      },
      pro: {
        monthly: 249,
        yearly: 1999,
      },
    },
    DEFAULT: {
      currency: "USD",
      plus: {
        monthly: 3.99,
        yearly: null,
      },
      pro: {
        monthly: 4.99,
        yearly: 39.99,
      },
    },
  },
  intro: {
    PH: {
      plus: { monthly: 149 },
      pro: { monthly: 199 },
    },
    DEFAULT: {
      plus: { monthly: null },
      pro: { monthly: null },
    },
  },
} as const;

export function resolvePricingDisplayRegion(region: string | null | undefined): PricingDisplayRegion {
  return region === "PH" ? "PH" : "DEFAULT";
}

export function resolveDocxExportDisplayLimit(
  plan: PricingExportPlan,
  profileType: ProfileType | null | undefined,
): number | null {
  const planPricing = pricingConfig[plan.toLowerCase() as Lowercase<PricingExportPlan>];
  return profileType === "TEACHER"
    ? planPricing.teacherDocxExportsPerMonth
    : planPricing.docxExportsPerMonth;
}

export function resolvePdfExportDisplayLimit(plan: PricingExportPlan): number | null {
  return pricingConfig[plan.toLowerCase() as Lowercase<PricingExportPlan>].pdfExportsPerMonth;
}
