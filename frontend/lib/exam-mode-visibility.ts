import type { ProfileType } from "@/lib/api";

export type ExamModeId = "challenge" | "long_exam" | "board_exam";
export type PlanPremiumExamMode = "long_exam" | "board_exam" | "interview";

export type ExamModeCard = {
  id: ExamModeId;
  label: string;
  description: string;
  recommended: boolean;
  comingSoon: boolean;
};

const CHALLENGE_CARD: ExamModeCard = {
  id: "challenge",
  label: "Challenge Quiz",
  description: "Focused practice with flexible pacing.",
  recommended: true,
  comingSoon: false,
};

export function getAvailableExamModes(
  profileType: ProfileType | null | undefined,
): readonly ExamModeCard[] {
  if (profileType === "BOARD_EXAM") {
    return [
      { ...CHALLENGE_CARD, recommended: false },
      {
        id: "board_exam",
        label: "Board Exam Mode",
        description: "Simulate a realistic board-style exam experience.",
        recommended: true,
        comingSoon: false,
      },
    ];
  }

  if (profileType === "TEACHER") {
    return [CHALLENGE_CARD];
  }

  if (profileType === "PROFESSIONAL") {
    return [
      {
        ...CHALLENGE_CARD,
        label: "Certification Review",
        description: "Practice with real-world scenarios at your own pace.",
      },
      {
        id: "long_exam",
        label: "Full Practice Exam",
        description: "Comprehensive practice exam to test your certification readiness.",
        recommended: false,
        comingSoon: false,
      },
    ];
  }

  return [
    CHALLENGE_CARD,
    {
      id: "long_exam",
      label: "Long Exam Mode",
      description: "Comprehensive review across broader topics.",
      recommended: false,
      comingSoon: false,
    },
  ];
}

export function resolvePlanPremiumExamMode(
  profileType: ProfileType | null | undefined,
): PlanPremiumExamMode | null {
  if (profileType === "STUDENT") {
    return "long_exam";
  }
  if (profileType === "BOARD_EXAM") {
    return "board_exam";
  }
  if (profileType === "PROFESSIONAL") {
    return "interview";
  }
  return null;
}
