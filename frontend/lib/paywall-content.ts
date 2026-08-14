import type { PaidPlanType, PlanType, ProfileType } from "@/lib/api";

export type PaywallContextType =
  | "GENERATE_STUDY_PACK_LIMIT"
  | "GENERATE_NOTE_LIMIT"
  | "QUIZ_LIMIT"
  | "ADAPTIVE_PRACTICE_LOCKED"
  | "INTERVIEW_PRACTICE_LOCKED"
  | "EXPORT_LIMIT"
  | "BOARD_EXAM_MODE_LOCKED"
  | "LONG_EXAM_MODE_LOCKED"
  | "CONCEPT_REVIEW_TIMING_LOCKED"
  | "OCR_LIMIT"
  | "TEACHER_QUIZ_QUESTION_COUNT_LOCKED"
  | "TEACHER_EXAM_VERSIONS_LOCKED"
  | "QUIZ_GENERATION_LIMIT";

export type PaywallResumeAction =
  | "GENERATE_STUDY_PACK"
  | "GENERATE_NOTE"
  | "QUIZ"
  | "ADAPTIVE_PRACTICE"
  | "INTERVIEW_PRACTICE"
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
  | "interview-practice-limit"
  | "board-exam-mode"
  | "board-exam-limit"
  | "long-exam-mode"
  | "concept-timing-locked"
  | "challenge-quiz-limit"
  | "quiz-generation-limit"
  | "study-pack-limit"
  | "ocr-limit"
  | "note-generation-limit"
  | "export-limit"
  | "teacher-quiz-question-count"
  | "teacher-exam-versions";

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
    case "interview-practice-limit":
      return "INTERVIEW_PRACTICE_LOCKED";
    case "export-limit":
      return "EXPORT_LIMIT";
    case "board-exam-mode":
      return "BOARD_EXAM_MODE_LOCKED";
    case "board-exam-limit":
      return "QUIZ_LIMIT";
    case "long-exam-mode":
      return "LONG_EXAM_MODE_LOCKED";
    case "concept-timing-locked":
      return "CONCEPT_REVIEW_TIMING_LOCKED";
    case "ocr-limit":
      return "OCR_LIMIT";
    case "quiz-generation-limit":
      return "QUIZ_GENERATION_LIMIT";
    case "teacher-quiz-question-count":
      return "TEACHER_QUIZ_QUESTION_COUNT_LOCKED";
    case "teacher-exam-versions":
      return "TEACHER_EXAM_VERSIONS_LOCKED";
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
          ? "Pro keeps your study system growing — more of your notes turned into material you can review and practise from."
          : "Upgrading keeps your study system growing — more of your notes turned into material you can review and practise from.",
        feature: "study_pack_limit",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "GENERATE_STUDY_PACK",
      };
    case "GENERATE_NOTE_LIMIT":
      return {
        headline: "You've reached your topic note limit",
        body: currentPlan === "PLUS"
          ? "Pro keeps your library growing, so there is always something ready when you sit down to study."
          : "More topic notes means more of your library is ready when you sit down to study.",
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
          ? "Keep practising, and step up to exam-day conditions when you are ready for them."
          : isTeacherProfile(profileType)
            ? "Generate and practice with more quizzes so you can keep preparing review materials without breaking your flow."
            : "Keep practising so your progress stays current and your plan knows what to show you next.",
        feature: isTeacherProfile(profileType) ? "quiz_generation_limit" : "quiz_limit",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
    case "ADAPTIVE_PRACTICE_LOCKED":
      return {
        headline: currentPlan === "FREE"
          ? "You've used your free Adaptive Practice sessions"
          : "You've used your Adaptive Practice sessions",
        body: currentPlan === "FREE"
          ? "Free includes a monthly taste of targeted practice. More sessions means your weak areas close now instead of waiting for next month."
          : "More sessions means your weak areas close now instead of waiting for next month.",
        feature: "adaptive",
        primaryPlanType: currentPlan === "FREE" ? "PLUS" : "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "ADAPTIVE_PRACTICE",
      };
    case "INTERVIEW_PRACTICE_LOCKED":
      return {
        headline: "Prepare for the part that isn't a quiz",
        body: "Interview Practice adds scenario questions with feedback after every answer, and a readiness report that feeds back into what you study next.",
        feature: "interview_practice",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "INTERVIEW_PRACTICE",
      };
    case "EXPORT_LIMIT":
      return {
        headline: currentPlan === "PLUS" ? "You've used all your exports" : "You've reached your export limit",
        body: isTeacherProfile(profileType)
          ? "Move up for more DOCX quiz exports so you can keep printing exams for your class."
          : "Download your quizzes as printable exams for offline study.",
        feature: "export_limit",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "EXPORT",
      };
    case "BOARD_EXAM_MODE_LOCKED":
      return {
        headline: "Take your review all the way to exam day",
        body: "Board Exam Mode runs your own material under exam-day conditions, so by the time you sit the real thing, nothing about it is unfamiliar.",
        feature: "board_exam",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
    case "LONG_EXAM_MODE_LOCKED":
      return {
        headline: "Find out how ready you actually are",
        body: "The Long Exam tests everything you have studied in one full-length sitting and returns a mastery breakdown, so your next sessions go where they are needed.",
        feature: "long_exam",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
    case "CONCEPT_REVIEW_TIMING_LOCKED":
      return {
        headline: "Know what is slipping before you forget it",
        body: "See when you last got each concept right or wrong and which ones you keep missing, so every session starts where it matters most.",
        feature: "concept_review_timing",
        primaryPlanType: "PLUS",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "ADAPTIVE_PRACTICE",
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
    case "TEACHER_QUIZ_QUESTION_COUNT_LOCKED":
      return {
        headline: "Unlock longer teacher quizzes",
        body: "Plus unlocks 20- and 30-question quizzes so you can match chapter quizzes and longer unit assessments.",
        feature: "teacher_quiz_question_count",
        primaryPlanType: "PLUS",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: SECONDARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
    case "TEACHER_EXAM_VERSIONS_LOCKED":
      return {
        headline: "Unlock multiple exam versions",
        body: "Plus unlocks multiple exam versions for anti-cheating.",
        feature: "teacher_exam_versions",
        primaryPlanType: "PLUS",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: SECONDARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "EXPORT",
      };
    default:
      return {
        headline: "Keep your study system growing",
        body: "Choose the plan that gives you more room to study, practise, and review without interruptions.",
        feature: "paywall",
        primaryPlanType: "PRO",
        secondaryPlanType: "PLUS",
        primaryCtaLabel: PRIMARY_CTA_LABEL,
        secondaryCtaLabel: SECONDARY_CTA_LABEL,
        lastAction: "QUIZ",
      };
  }
}
