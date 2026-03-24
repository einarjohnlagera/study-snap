import sitemap from "./sitemap";
import { getServerPublicNotes } from "@/lib/server-public-notes";

jest.mock("@/lib/server-public-notes", () => ({
  getServerPublicNotes: jest.fn(),
}));

describe("sitemap metadata route", () => {
  beforeEach(() => {
    (getServerPublicNotes as jest.Mock).mockReset();
  });

  it("includes only public static pages and public note URLs", async () => {
    (getServerPublicNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-1",
        title: "Cell Structure",
        subject: "Science",
        tags: [],
        contentPreview: "Cells",
        visibility: "PUBLIC",
        studyPackId: "pack-1",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 5,
        updatedAt: "2026-03-24T08:30:00Z",
      },
      {
        id: "note-2",
        title: "Journal Entries",
        subject: "Accounting",
        tags: [],
        contentPreview: "Entries",
        visibility: "PUBLIC",
        studyPackId: "pack-2",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 5,
        updatedAt: "2026-03-22T08:30:00Z",
      },
    ]);

    const entries = await sitemap();

    expect(entries).toEqual([
      { url: "https://www.notelib.app/" },
      { url: "https://www.notelib.app/pricing" },
      { url: "https://www.notelib.app/public/library" },
      {
        url: "https://www.notelib.app/public/library/science/cell-structure",
        lastModified: "2026-03-24T08:30:00Z",
      },
      {
        url: "https://www.notelib.app/public/library/accounting/journal-entries",
        lastModified: "2026-03-22T08:30:00Z",
      },
    ]);
  });
});
