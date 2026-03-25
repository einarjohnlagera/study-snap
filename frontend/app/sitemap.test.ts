import sitemap from "./sitemap";
import { getServerPublicNotes } from "@/lib/server-public-notes";

jest.mock("@/lib/server-public-notes", () => {
  const actual = jest.requireActual("@/lib/server-public-notes");
  return {
    ...actual,
    getServerPublicNotes: jest.fn(),
  };
});

describe("sitemap metadata route", () => {
  beforeEach(() => {
    (getServerPublicNotes as jest.Mock).mockReset();
  });

  it("includes only public SEO-safe pages, subject pages, and public note URLs", async () => {
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
      {
        url: "https://www.notelib.app/",
        changeFrequency: "weekly",
        priority: 1,
      },
      {
        url: "https://www.notelib.app/privacy",
        changeFrequency: "monthly",
        priority: 0.3,
      },
      {
        url: "https://www.notelib.app/terms",
        changeFrequency: "monthly",
        priority: 0.3,
      },
      {
        url: "https://www.notelib.app/public/library",
        changeFrequency: "daily",
        priority: 0.9,
      },
      {
        url: "https://www.notelib.app/public/library/accounting",
        lastModified: "2026-03-22T08:30:00Z",
        changeFrequency: "daily",
        priority: 0.8,
      },
      {
        url: "https://www.notelib.app/public/library/science",
        lastModified: "2026-03-24T08:30:00Z",
        changeFrequency: "daily",
        priority: 0.8,
      },
      {
        url: "https://www.notelib.app/public/library/accounting/journal-entries",
        lastModified: "2026-03-22T08:30:00Z",
        changeFrequency: "monthly",
        priority: 0.7,
      },
      {
        url: "https://www.notelib.app/public/library/science/cell-structure",
        lastModified: "2026-03-24T08:30:00Z",
        changeFrequency: "monthly",
        priority: 0.7,
      },
    ]);
  });
});
