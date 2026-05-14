import type { PaidPlanType, PlanType, ProfileType } from "@/lib/api";

export type PaywallContextType =
  | "GENERATE_STUDY_PACK_LIMIT"
  | "GENERATE_NOTE_LIMIT"
  | "QUIZ_LIMIT"
  | "ADAPTIVE_PRACTICE_LOCKED"
  | "EXPORT_LIMIT"
  | "BOARD_EXAM_MODE_LOCKED"
  | "LONG_EXAM_MODE_LOCKED"
  | "DIFFICULTY_SELECTION_LOCKED"
  | "OCR_LIMIT"
  | "QUIZ_GENERATION_LIMIT";

export type PaywallResumeAction =
  | "GENERATE_STUDY_PACK"
  | "GENERATE_NOTE"
  | "QUIZ"
  | "ADAPTIVE_PRACTICE"
  | "EXPORT"
  | "OCR";

export type PaywallContext = {
  type: PaywallContextType;
  remaining?: number;
  noteId?: string | null;
  returnPath?: string | null;
};

export type PaywallModalVariant =
  | "adaptive-practice"
  | "board-exam-mode"
  | "long-exam-mode"
  | "difficulty-selection"
  | "challenge-quiz-limit"
  | "quiz-generation-limit"
  | "study-pack-limit"
  | "ocr-limit"
  | "note-generation-limit"
  | "export-limit";

export type PaywallPresentation = {
  headline: string;
  body: string;
  feature: string;
  primaryPlanType: PaidPlanType;
  secondaryPlanType: "PLUS";
  primaryCtaLabel: string;
  secondaryCtaLabel: string;
  lastAction: PaywallResumeAction;
};

const PRIMARY_CTA_LABEL = "Continue with Pro";
const SECONDARY_CTA_LABEL = "Choose Plus";

function isBoardExamProfile(profileType: ProfileType | null | undefined) {
  return profileType === "BOARD_EXAM";
}

function isTeacherProfile(profileType: ProfileType | null | undefined) {
  return profileType === "TEACHER";
}

export function resolvePaywallContextTypeFromVariant(variant: PaywallModalVariant): PaywallContextType {
  switch (variant) {
    case "study-pack-limit":
      return "GENERATE_STUDY_PACK_LIMIT";
    case "note-generation-limit":
      return "GENERATE_NOTE_LIMIT";
    case "challenge-quiz-limit":
      return "QUIZ_LIMIT";
    case "adaptive-practice":
      return "ADAPTIVE_PRACTICE_LOCKED";
    case "export-limit":
      return "EXPORT_LIMIT";
    case "board-exam-mode":
      return "BOARD_EXAM_MODE_LOCKED";
    case "long-exam-mode":
      return "LONG_EXAM_MODE_LOCKED";
    case "difficulty-selection":
      return "DIFFICULTY_SELECTION_LOCKED";
    case "ocr-limit":
      return "OCR_LIMIT";
    case "quiz-generation-limit":
      return "QUIZ_GENERATION_LIMIT";
    default:
      return "GENERATE_STUDY_PACK_LIMIT";
  }
}

export function resolvePaywallPresentation(
  contextType: PaywallContextType,
  currentPlan: PlanType | null | undefined,
  profileType: ProfileType | null | undefined,
): PaywallPresentation {
  switch (contextType) {
    case "GENERATE_STUDY_PACK_LIMIT":
      return {
        headline: "You've reached your Study Pack limit",
        body: currentPlan === "PLUS"
          ? "Keep generating Study Packs and continue your review without interruptions by moving up to Pro."
          : "Generate more Study Packs and continue your review without interruptions.",
        feature: "study_pack_limit",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "GENERATE_STUDY_PACK",
      };
    case "GENERATE_NOTE_LIMIT":
      return {
        headline: "You've reached your note generation limit",
        body: currentPlan === "PLUS"
          ? "Keep generating note drafts from topics and move faster through your study flow with Pro."
          : "Create more note drafts from topics and keep building your study library faster.",
        feature: "note_generation_limit",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "GENERATE_NOTE",
      };
    case "QUIZ_LIMIT":
      return {
        headline: "You've reached your quiz limit",
        body: isBoardExamProfile(profileType)
          ? "Keep practicing and unlock Board Exam Mode for stricter review sessions with Pro."
          : isTeacherProfile(profileType)
            ? "Generate and practice with more quizzes so you can keep preparing review materials without breaking your flow."
            : "Keep practicing with more quizzes and continue testing what you know.",
        feature: isTeacherProfile(profileType) ? "quiz_generation_limit" : "quiz_limit",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
    case "ADAPTIVE_PRACTICE_LOCKED":
      return {
        headline: "Unlock Adaptive Practice",
        body: "Train on your weak concepts and improve faster with targeted quizzes.",
        feature: "adaptive",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "ADAPTIVE_PRACTICE",
      };
    case "EXPORT_LIMIT":
      return {
        headline: currentPlan === "PLUS" ? "You've used all your exports" : "You've reached your export limit",
        body: "Download your quizzes as printable exams for offline study.",
        feature: "export_limit",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "EXPORT",
      };
    case "BOARD_EXAM_MODE_LOCKED":
      return {
        headline: "Unlock Board Exam Mode",
        body: "Simulate exam-day pressure with stricter timed practice designed for serious review.",
        feature: "board_exam",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
    case "LONG_EXAM_MODE_LOCKED":
      return {
        headline: "Long Exam Mode",
        body: "Test your mastery with a comprehensive, full-length exam tailored to your notes. Pro unlocks fixed long exams, pause and resume, and mastery reports with domain breakdowns.",
        feature: "long_exam",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
    case "DIFFICULTY_SELECTION_LOCKED":
      return {
        headline: "Unlock Difficulty Selection",
        body: "Choose easier or harder question sets so each review session matches your confidence level.",
        feature: "difficulty",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
    case "OCR_LIMIT":
      return {
        headline: "You've reached your OCR limit",
        body: "Extract more text from images and files without retyping your notes.",
        feature: "ocr_limit",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "OCR",
      };
    case "QUIZ_GENERATION_LIMIT":
      return {
        headline: "You've reached your quiz generation limit",
        body: isTeacherProfile(profileType)
          ? "Generate more quizzes and export-ready classroom materials without breaking your teaching flow."
          : "Generate more quizzes and keep your review moving without hitting monthly limits.",
        feature: "quiz_generation_limit",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
    default:
      return {
        headline: "Unlock more of NoteLib",
        body: "Choose the plan that gives you more room to study, practice, and review without interruptions.",
        feature: "paywall",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
  }
}
