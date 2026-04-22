import {
  createExamQuestionRefKey,
  createWholeNoteEntry,
  createExamSection,
  evenBalanceExamSections,
  smartBalanceExamSections,
} from "./exam-builder-order";

describe("exam-builder-order", () => {
  it("keeps Even Balance as the original equal-slice allocator", () => {
    const sections = [
      createExamSection(0, [
        createWholeNoteEntry("note-1", 4),
        createWholeNoteEntry("note-2", 4),
      ]),
      createExamSection(1),
      createExamSection(2),
    ];

    const balancedSections = evenBalanceExamSections(sections);

    expect(balancedSections.map((section) => (
      section.entries.flatMap((entry) => entry.questionRefs.map((questionRef) => `${questionRef.noteId}:${questionRef.questionIndex}`))
    ))).toEqual([
      ["note-1:0", "note-1:1", "note-1:2"],
      ["note-1:3", "note-2:0", "note-2:1"],
      ["note-2:2", "note-2:3"],
    ]);
  });

  it("applies Smart Balance deterministically without losing or duplicating questions", () => {
    const sections = [
      createExamSection(0, [
        createWholeNoteEntry("note-1", 4),
        createWholeNoteEntry("note-2", 4),
      ]),
      createExamSection(1),
      createExamSection(2),
    ];
    const questionMetadataByRefKey = {
      [createExamQuestionRefKey("note-1", 0)]: { concept: "Foundations" },
      [createExamQuestionRefKey("note-1", 1)]: { concept: "Foundations" },
      [createExamQuestionRefKey("note-1", 2)]: { concept: "Problem Solving" },
      [createExamQuestionRefKey("note-1", 3)]: { concept: "Application" },
      [createExamQuestionRefKey("note-2", 0)]: { concept: "Foundations" },
      [createExamQuestionRefKey("note-2", 1)]: { concept: "Problem Solving" },
      [createExamQuestionRefKey("note-2", 2)]: { concept: "Application" },
      [createExamQuestionRefKey("note-2", 3)]: { concept: "Application" },
    };

    const firstBalancedSections = smartBalanceExamSections(sections, {
      questionMetadataByRefKey,
      sectionIntents: ["FOUNDATIONAL", "PROBLEM_SOLVING", "APPLICATION"],
    });
    const secondBalancedSections = smartBalanceExamSections(sections, {
      questionMetadataByRefKey,
      sectionIntents: ["FOUNDATIONAL", "PROBLEM_SOLVING", "APPLICATION"],
    });

    const firstRefs = firstBalancedSections.map((section) => (
      section.entries.flatMap((entry) => entry.questionRefs.map((questionRef) => `${questionRef.noteId}:${questionRef.questionIndex}`))
    ));
    const secondRefs = secondBalancedSections.map((section) => (
      section.entries.flatMap((entry) => entry.questionRefs.map((questionRef) => `${questionRef.noteId}:${questionRef.questionIndex}`))
    ));

    expect(firstRefs).toEqual(secondRefs);
    expect(firstRefs.map((sectionRefs) => sectionRefs.length)).toEqual([3, 3, 2]);
    expect(firstRefs).toEqual([
      ["note-1:0", "note-2:1", "note-2:3"],
      ["note-1:1", "note-1:3", "note-2:2"],
      ["note-1:2", "note-2:0"],
    ]);
    expect(new Set(firstRefs.flat()).size).toBe(8);
  });
});
