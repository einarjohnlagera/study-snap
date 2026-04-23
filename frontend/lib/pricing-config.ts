export type PricingDisplayRegion = "PH" | "DEFAULT";

export const pricingConfig = {
  free: {
    studyPacksPerMonth: 10,
    challengeQuizzesPerMonth: 5,
    ocrPerMonth: 20,
  },
  premium: {
    studyPacksPerMonth: 100,
    challengeQuizzesPerMonth: 50,
    adaptivePracticePerMonth: 30,
    ocrPerMonth: 100,
  },
  price: {
    PH: {
      currency: "PHP",
      monthly: 249,
      yearly: 2499,
    },
    DEFAULT: {
      currency: "USD",
      monthly: 4.99,
      yearly: 39.99,
    },
  },
  intro: {
    PH: { monthly: 199 },
    DEFAULT: { monthly: 3.99 },
  },
} as const;

export function resolvePricingDisplayRegion(region: string | null | undefined): PricingDisplayRegion {
  return region === "PH" ? "PH" : "DEFAULT";
}
