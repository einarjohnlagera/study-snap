export type SampleNotePreset = {
  key: string;
  title: string;
  subject: string;
  tags: string[];
  content: string;
};

export const PHOTOSYNTHESIS_SAMPLE_NOTE: SampleNotePreset = {
  key: "photosynthesis-basics",
  title: "Photosynthesis Basics",
  subject: "Biology",
  tags: ["Photosynthesis"],
  content:
    "Photosynthesis is the process by which plants convert light energy into chemical energy. Chlorophyll absorbs sunlight. Water and carbon dioxide are used to produce glucose. Oxygen is released as a byproduct.",
};

const SAMPLE_NOTE_PRESETS: Record<string, SampleNotePreset> = {
  [PHOTOSYNTHESIS_SAMPLE_NOTE.key]: PHOTOSYNTHESIS_SAMPLE_NOTE,
};

export function getSampleNotePreset(key: string | null): SampleNotePreset | null {
  if (!key) {
    return null;
  }
  return SAMPLE_NOTE_PRESETS[key] ?? null;
}
