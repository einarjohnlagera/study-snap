import {
  STUDY_PACK_GENERATION_MESSAGES,
  resolveStudyPackGenerationMessage,
} from "@/lib/study-pack-generation";

describe("study-pack-generation", () => {
  it("cycles friendly Study Pack generation messages by index", () => {
    expect(resolveStudyPackGenerationMessage(0)).toBe(STUDY_PACK_GENERATION_MESSAGES[0]);
    expect(resolveStudyPackGenerationMessage(STUDY_PACK_GENERATION_MESSAGES.length)).toBe(STUDY_PACK_GENERATION_MESSAGES[0]);
    expect(resolveStudyPackGenerationMessage(Number.NaN)).toBe(STUDY_PACK_GENERATION_MESSAGES[0]);
  });
});
