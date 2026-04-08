export const STUDY_PACK_GENERATION_MESSAGES = [
  "Building your Study Pack...",
  "Turning your notes into a summary, key concepts, and quiz...",
  "Preparing your reviewer...",
  "Almost there - organizing your study material...",
  "This can take a bit longer for larger notes.",
] as const;

export const STUDY_PACK_GENERATION_POLL_INTERVAL_MS = 3000;
export const STUDY_PACK_GENERATION_MESSAGE_ROTATION_MS = 4500;

export function resolveStudyPackGenerationMessage(index: number): string {
  const safeIndex = Number.isFinite(index) ? Math.max(0, index) : 0;
  return STUDY_PACK_GENERATION_MESSAGES[safeIndex % STUDY_PACK_GENERATION_MESSAGES.length]!;
}
