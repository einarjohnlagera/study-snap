import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { act } from "react";
import { BulkRegenerateModal } from "./bulk-regenerate-modal";
import type { NoteRegenerationPreflightResponse } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  preflightNoteRegeneration: jest.fn(),
  bulkRegenerateNotes: jest.fn(),
  getBulkRegenerationReceipt: jest.fn(),
}));

const api = jest.requireMock("@/lib/api") as {
  preflightNoteRegeneration: jest.Mock;
  bulkRegenerateNotes: jest.Mock;
  getBulkRegenerationReceipt: jest.Mock;
};

function buildPreflight(
  overrides: Partial<NoteRegenerationPreflightResponse> = {},
): NoteRegenerationPreflightResponse {
  return {
    scope: "STUDY_PACK",
    requestedCount: 3,
    readyCount: 3,
    blockedCount: 0,
    notEligibleCount: 0,
    publicNotesAffected: 2,
    sharedQuizzesToDeactivate: 1,
    noteGenerationUnitsRequired: 3,
    noteGenerationUnitsRemaining: 10,
    studyPackUnitsRequired: 3,
    studyPackUnitsRemaining: 10,
    quotaExceeded: false,
    itemsToRemove: 0,
    maxBatchSize: 50,
    items: [],
    ...overrides,
  };
}

const NOTE_IDS = ["note-1", "note-2", "note-3"];

beforeEach(() => {
  jest.clearAllMocks();
  api.preflightNoteRegeneration.mockResolvedValue(buildPreflight());
});

describe("BulkRegenerateModal", () => {
  it("opens on the non-destructive scope and only warns about replacement once the combined scope is chosen", async () => {
    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);

    const studyPackCard = await screen.findByRole("radio", { name: /Rewrites the summary/i });
    expect(studyPackCard).toHaveAttribute("aria-checked", "true");

    // The public-note and shared-quiz consequences belong to the combined scope only: Study-Pack-only
    // regeneration does not replace the note text a shared quiz was built from.
    expect(screen.queryByText(/public note/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/shared quiz/i)).not.toBeInTheDocument();

    await act(async () => {
      fireEvent.click(screen.getByRole("radio", { name: /Rewrites each note itself/i }));
    });

    expect(await screen.findByText(/2 public notes will change/i)).toBeInTheDocument();
    expect(screen.getByText(/1 active shared quiz will be/i)).toBeInTheDocument();
  });

  it("blocks starting an over-quota batch and says how many to remove", async () => {
    api.preflightNoteRegeneration.mockResolvedValue(buildPreflight({
      quotaExceeded: true,
      noteGenerationUnitsRequired: 3,
      noteGenerationUnitsRemaining: 1,
      itemsToRemove: 2,
    }));

    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);

    expect(await screen.findByText(/Remove 2 notes to continue/i)).toBeInTheDocument();
    // Discriminating: a disabled-looking button that still fires would spend units the curator
    // has already been told they do not have.
    const start = screen.getByRole("button", { name: /Regenerate 3 notes/i });
    expect(start).toBeDisabled();
    await act(async () => {
      fireEvent.click(start);
    });
    expect(api.bulkRegenerateNotes).not.toHaveBeenCalled();
  });

  it("does not offer to start when nothing in the selection is ready", async () => {
    api.preflightNoteRegeneration.mockResolvedValue(buildPreflight({
      readyCount: 0,
      blockedCount: 3,
      items: [
        { noteId: "note-1", title: "Shear", readiness: "BLOCKED", reasonCode: "X", reason: "Already generating" },
      ],
    }));

    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);

    expect(await screen.findByRole("button", { name: /Regenerate 0 notes/i })).toBeDisabled();
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: /Show the 1 that won't run/i }));
    });
    expect(screen.getByText(/Already generating/i)).toBeInTheDocument();
  });

  it("reports a batch that stopped early as stopped rather than leaving progress running", async () => {
    api.bulkRegenerateNotes.mockResolvedValue({ batchId: "batch-1", scope: "STUDY_PACK", acceptedCount: 3 });
    api.getBulkRegenerationReceipt.mockResolvedValue({
      batchId: "batch-1",
      scope: "STUDY_PACK",
      totalCount: 3,
      regeneratedCount: 1,
      blockedCount: 0,
      failedCount: 0,
      notRunCount: 0,
      pendingCount: 2,
      finished: false,
      stale: true,
      retryableNoteIds: [],
      items: [],
    });

    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);
    const start = await screen.findByRole("button", { name: /Regenerate 3 notes/i });
    await act(async () => {
      fireEvent.click(start);
    });

    // The discriminating half: an unfinished batch normally says "it keeps running". A stale one
    // must NOT, or the curator waits forever on a batch a deploy already killed.
    expect(await screen.findByText(/stopped before finishing/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByText(/keeps running/i)).not.toBeInTheDocument();
    });
  });
});
