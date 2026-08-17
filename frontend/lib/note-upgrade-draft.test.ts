import { loadNoteUpgradeDraft } from "./note-upgrade-draft";

describe("note-upgrade-draft", () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
  });

  it("restores an old-shape draft while ignoring its retired audience key", () => {
    globalThis.localStorage.setItem("notelib.note-upgrade-draft:user-1", JSON.stringify({
      draft: {
        title: "Cell Biology",
        subject: "Biology",
        courseProgram: "Nursing",
        domainContext: "NURSING",
        learnerLevel: "COLLEGE",
        targetProfileType: "BOARD_TAKER",
        content: "Cell content",
        tags: ["cells"],
      },
      entryOption: "write",
      generateTopic: "",
      savedAtMs: 123,
    }));

    expect(loadNoteUpgradeDraft("user-1")).toEqual({
      draft: {
        title: "Cell Biology",
        subject: "Biology",
        courseProgram: "Nursing",
        domainContext: "NURSING",
        learnerLevel: "COLLEGE",
        content: "Cell content",
        tags: ["cells"],
      },
      entryOption: "write",
      generateTopic: "",
      savedAtMs: 123,
    });
  });
});
