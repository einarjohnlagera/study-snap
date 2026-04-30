export type PricingDisplayRegion = "PH" | "DEFAULT";

export const pricingConfig = {
  free: {
    studyPacksPerMonth: 10,
    challengeQuizzesPerMonth: 5,
    ocrPerMonth: 20,
    exportsPerMonth: 2,
  },
  plus: {
    studyPacksPerMonth: 50,
    challengeQuizzesPerMonth: 25,
    noteGenerationsPerMonth: 25,
    exportsPerMonth: 15,
  },
  pro: {
    studyPacksPerMonth: 100,
    challengeQuizzesPerMonth: 50,
    adaptivePracticePerMonth: 30,
    ocrPerMonth: 100,
    noteGenerationsPerMonth: 100,
    exportsPerMonth: null,
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
