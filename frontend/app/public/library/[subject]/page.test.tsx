import { render, screen } from "@testing-library/react";
import PublicLibrarySubjectPage, { generateMetadata } from "./page";
import { getServerPublicNotesBySubjectSlug, getServerPublicSubjects } from "@/lib/server-public-notes";

const notFoundMock = jest.fn(() => {
  throw new Error("NEXT_NOT_FOUND");
});

jest.mock("next/navigation", () => ({
  notFound: () => notFoundMock(),
}));

jest.mock("@/lib/server-public-notes", () => ({
  getServerPublicSubjects: jest.fn(),
  getServerPublicNotesBySubjectSlug: jest.fn(),
}));

const subjectEntry = {
  slug: "science",
  label: "Science",
  lastModified: "2026-03-24T08:30:00Z",
};

const subjectNotes = [
  {
    id: "note-1",
    title: "Cell Structure",
    subject: "Science",
    tags: ["biology", "cells"],
    contentPreview: "Cells are the basic unit of life.",
    summaryPreview: "Cells contain organelles that support life functions.",
    visibility: "PUBLIC",
    studyPackId: "pack-1",
    studyPackStatus: "STUDY_PACK_READY",
    quizCount: 5,
    updatedAt: "2026-03-24T08:30:00Z",
  },
];

describe("PublicLibrarySubjectPage", () => {
  beforeEach(() => {
    notFoundMock.mockClear();
    (getServerPublicSubjects as jest.Mock).mockReset();
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockReset();
  });

  it("renders a public subject index page with note links", async () => {
    (getServerPublicSubjects as jest.Mock).mockResolvedValue([subjectEntry]);
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue(subjectNotes);

    const { container } = render(
      await PublicLibrarySubjectPage({
        params: Promise.resolve({ subject: "science" }),
      }),
    );

    expect(screen.getByRole("heading", { name: "Science Notes" })).toBeInTheDocument();
    expect(screen.getByText("Browse all public subjects")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Public study notes" })).toBeInTheDocument();
    expect(screen.getAllByText("Science").length).toBeGreaterThan(0);
    expect(screen.getByText("Cells are the basic unit of life.")).toBeInTheDocument();
    expect(screen.getByText("Cells contain organelles that support life functions.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Cell Structure/i })).toHaveAttribute(
      "href",
      "/public/library/science/cell-structure",
    );

    const structuredData = container.querySelector("#public-library-subject-structured-data");
    expect(structuredData).not.toBeNull();
    expect(structuredData?.textContent).toContain('"@type":"CollectionPage"');
    expect(structuredData?.textContent).toContain('"name":"Science Notes | NoteLib Public Library"');
  });

  it("exports subject metadata with canonical and social preview fields", async () => {
    (getServerPublicSubjects as jest.Mock).mockResolvedValue([subjectEntry]);

    const metadata = await generateMetadata({
      params: Promise.resolve({ subject: "science" }),
    });

    expect(metadata).toMatchObject({
      title: "Science Notes and Study Packs | NoteLib Public Library",
      description: "Browse public Science notes, summaries, and practice questions shared by the NoteLib community.",
      alternates: {
        canonical: "https://notelib.app/public/library/science",
      },
    });
  });

  it("does not index missing subject pages", async () => {
    (getServerPublicSubjects as jest.Mock).mockResolvedValue([]);

    const metadata = await generateMetadata({
      params: Promise.resolve({ subject: "unknown" }),
    });

    expect(metadata.title).toBe("Public Subject Not Found | NoteLib");
    expect(metadata.robots).toEqual({ index: false, follow: false });
  });

  it("renders an empty state when a known subject has no public notes", async () => {
    (getServerPublicSubjects as jest.Mock).mockResolvedValue([subjectEntry]);
    (getServerPublicNotesBySubjectSlug as jest.Mock).mockResolvedValue([]);

    render(
      await PublicLibrarySubjectPage({
        params: Promise.resolve({ subject: "science" }),
      }),
    );

    expect(screen.getByText("No public notes yet")).toBeInTheDocument();
    expect(screen.getByText("There are no public notes for Science right now.")).toBeInTheDocument();
  });
});
