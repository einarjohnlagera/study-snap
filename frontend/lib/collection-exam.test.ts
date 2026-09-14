import {
  canIncludeCollectionItemInPremiumExam,
  resolveCollectionScopedSourceNotes,
} from "@/lib/collection-exam";
import type { NoteCollectionDetail, NoteCollectionItem, NoteListItemResponse } from "@/lib/api";

function item(noteId: string, studyPackStatus: NoteCollectionItem["studyPackStatus"], studyPackDone: boolean | null) {
  return {
    noteId,
    position: Number(noteId),
    studyPackStatus,
    studyPackDone,
    studyPackId: `pack-${noteId}`,
    generatedQuizId: null,
  } as NoteCollectionItem;
}

function note(id: string, studyPackStatus: NoteListItemResponse["studyPackStatus"], studyPackDone: boolean | null) {
  return {
    id,
    studyPackStatus,
    studyPackDone,
    studyPackId: `pack-${id}`,
  } as NoteListItemResponse;
}

describe("artifact-first collection exam eligibility", () => {
  it.each(["GENERATING", "FAILED"] as const)(
    "includes a %s note when its prior Study Pack is DONE",
    (studyPackStatus) => {
      expect(canIncludeCollectionItemInPremiumExam(item("1", studyPackStatus, true))).toBe(true);
    },
  );

  it("filters on Study Pack status rather than Note lifecycle and retains the per-caller id requirement", () => {
    const items = [item("1", "STUDY_PACK_READY", true), item("2", "GENERATING", true), item("3", "FAILED", true)];
    const collection = { items } as NoteCollectionDetail;
    const notes = [note("1", "STUDY_PACK_READY", true), note("2", "GENERATING", true), note("3", "FAILED", true)];

    expect(resolveCollectionScopedSourceNotes(collection, notes, "1", { requireStudyPackId: true })
      .map((candidate) => candidate.id)).toEqual(["2", "3"]);

    notes[1].studyPackId = null;
    expect(resolveCollectionScopedSourceNotes(collection, notes, "1", { requireStudyPackId: true })
      .map((candidate) => candidate.id)).toEqual(["3"]);
    expect(resolveCollectionScopedSourceNotes(collection, notes, "1", { requireStudyPackId: false })
      .map((candidate) => candidate.id)).toEqual(["2", "3"]);
  });

  it("excludes a lifecycle-ready note whose Study Pack is not DONE", () => {
    expect(canIncludeCollectionItemInPremiumExam(item("1", "STUDY_PACK_READY", false))).toBe(false);
  });
});
