import { render, screen } from "@testing-library/react";
import { StudyPackGrid } from "./study-pack-grid";
import type { NoteListItemResponse } from "@/lib/api";

describe("StudyPackGrid", () => {
  const notes: NoteListItemResponse[] = [
    {
      id: "note-1",
      title: "React Hooks",
      subject: "Web Dev",
      tags: ["react", "hooks"],
      contentPreview: "Hooks let you use state and lifecycle features in function components.",
      visibility: "PRIVATE",
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      quizCount: 8,
      updatedAt: "2026-03-21T10:00:00Z",
    },
  ];

  it("renders recent notes content", () => {
    render(
      <StudyPackGrid
        notes={notes}
        totalNotes={12}
        recentNoteMetaById={{ "note-1": { lastReviewedAt: "2026-03-21T10:30:00Z", quizCount: 8 } }}
      />,
    );

    expect(screen.getByText("Recent Notes")).toBeInTheDocument();
    expect(screen.getByText("React Hooks")).toBeInTheDocument();
    expect(screen.getByText("Web Dev")).toBeInTheDocument();
    expect(screen.getByText("12 saved")).toBeInTheDocument();
  });

  it("links each recent card to note detail", () => {
    render(
      <StudyPackGrid
        notes={notes}
        totalNotes={1}
        recentNoteMetaById={{ "note-1": { lastReviewedAt: null, quizCount: 8 } }}
      />,
    );

    const cardLink = screen.getByRole("link", { name: /react hooks/i });
    expect(cardLink).toHaveAttribute("href", "/notes/note-1?from=dashboard");
  });
});
