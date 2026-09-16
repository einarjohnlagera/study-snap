import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { act } from "react";
import { BulkRegenerateModal } from "./bulk-regenerate-modal";
import type { NoteRegenerationPreflightResponse } from "@/lib/api";

jest.mock("@/lib/api", () => {
  class ApiRequestError extends Error {
    status: number;
    code: string | null;

    constructor(message: string, options: { status: number; code?: string | null }) {
      super(message);
      this.name = "ApiRequestError";
      this.status = options.status;
      this.code = options.code ?? null;
    }
  }

  return {
    ApiRequestError,
    preflightNoteRegeneration: jest.fn(),
    bulkRegenerateNotes: jest.fn(),
    getBulkRegenerationReceipt: jest.fn(),
    retryBulkRegeneration: jest.fn(),
  };
});

const api = jest.requireMock("@/lib/api") as {
  ApiRequestError: new (message: string, options: { status: number; code?: string | null }) => Error;
  preflightNoteRegeneration: jest.Mock;
  bulkRegenerateNotes: jest.Mock;
  getBulkRegenerationReceipt: jest.Mock;
  retryBulkRegeneration: jest.Mock;
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
  // The modal now persists an in-flight batch id so closing and reopening resumes the receipt rather
  // than offering to run the same selection again. That storage is real, so it must be cleared between
  // tests or a batch started in one test opens the next one straight into the progress view.
  globalThis.sessionStorage?.clear();
  api.preflightNoteRegeneration.mockResolvedValue(buildPreflight());
});

describe("BulkRegenerateModal", () => {
  it("warns about a shared-quiz deactivation on either scope, but public notes only on the combined scope", async () => {
    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);

    const studyPackCard = await screen.findByRole("radio", { name: /Rewrites the summary/i });
    expect(studyPackCard).toHaveAttribute("aria-checked", "true");

    // v0.151.0: saveStudyPack replaces a shared quiz's content on EITHER scope, so the warning must
    // show on the default Study-Pack-only scope too -- suppressing it here is the exact confirmation-
    // dialog inaccuracy that release fixed on the backend. The public-note warning stays combined-only:
    // Study-Pack-only regeneration genuinely does not replace the note text itself.
    expect(screen.queryByText(/public note/i)).not.toBeInTheDocument();
    expect(await screen.findByText(/1 active shared quiz will be/i)).toBeInTheDocument();

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

  it("discloses the Study Pack meter and warns on a shortfall it cannot refuse", async () => {
    // The soft floor: quotaExceeded is FALSE (that flag only reads the note-generation meter), so a
    // fixture asserting only quotaExceeded passes under the defect where nothing is said at all.
    api.preflightNoteRegeneration.mockResolvedValue(buildPreflight({
      scope: "STUDY_PACK",
      quotaExceeded: false,
      noteGenerationUnitsRequired: 0,
      studyPackUnitsRequired: 3,
      studyPackUnitsRemaining: 1,
    }));

    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);

    expect(await screen.findByText(/1 Study Pack generation left this cycle/i)).toBeInTheDocument();
    expect(screen.getByText(/will stop rather than regenerate/i)).toBeInTheDocument();
    // A soft floor never blocks the button.
    expect(screen.getByRole("button", { name: /Regenerate 3 notes/i })).toBeEnabled();
  });

  it("offers retry for the failed items only, and never for a batch whose failures are all blocked", async () => {
    api.bulkRegenerateNotes.mockResolvedValue({ batchId: "batch-1", scope: "STUDY_PACK", acceptedCount: 3 });
    api.retryBulkRegeneration.mockResolvedValue({ batchId: "batch-2", scope: "STUDY_PACK", acceptedCount: 1 });
    api.getBulkRegenerationReceipt.mockResolvedValue({
      batchId: "batch-1",
      scope: "STUDY_PACK",
      totalCount: 3,
      regeneratedCount: 1,
      blockedCount: 1,
      failedCount: 1,
      notRunCount: 0,
      pendingCount: 0,
      finished: true,
      stale: false,
      // ⚠️ ONE entry against three items. The discriminating half: a fixture where everything failed
      // would pass under a control that offers to re-run the whole batch, which would spend units on
      // notes that already regenerated.
      retryableNoteIds: ["note-3"],
      items: [],
    });

    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);
    const start = await screen.findByRole("button", { name: /Regenerate 3 notes/i });
    await act(async () => {
      fireEvent.click(start);
    });

    const retry = await screen.findByRole("button", { name: /Retry 1 failed/i });
    expect(screen.getByText(/Blocked notes are not retried/i)).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(retry);
    });
    // Addressed by BATCH id, never by a note list -- the server derives which items failed.
    expect(api.retryBulkRegeneration).toHaveBeenCalledWith("batch-1");
  });

  it("offers no retry when nothing failed", async () => {
    api.bulkRegenerateNotes.mockResolvedValue({ batchId: "batch-1", scope: "STUDY_PACK", acceptedCount: 3 });
    api.getBulkRegenerationReceipt.mockResolvedValue({
      batchId: "batch-1",
      scope: "STUDY_PACK",
      totalCount: 3,
      regeneratedCount: 3,
      blockedCount: 0,
      failedCount: 0,
      notRunCount: 0,
      pendingCount: 0,
      finished: true,
      stale: false,
      retryableNoteIds: [],
      items: [],
    });

    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);
    const start = await screen.findByRole("button", { name: /Regenerate 3 notes/i });
    await act(async () => {
      fireEvent.click(start);
    });

    expect(await screen.findByText(/Finished · 3 of 3 regenerated/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Retry/i })).not.toBeInTheDocument();
  });

  // ⚠️ v0.147.0 — the fix for the modal wedging permanently on a stale/expired batch id.
  const ACTIVE_BATCH_STORAGE_KEY = "notelib-bulk-regeneration-batch";

  it("treats a 404 on the receipt as terminal: stops polling, clears the stored batch, and returns to preflight", async () => {
    jest.useFakeTimers();
    try {
      api.bulkRegenerateNotes.mockResolvedValue({ batchId: "batch-1", scope: "STUDY_PACK", acceptedCount: 3 });
      api.getBulkRegenerationReceipt.mockRejectedValue(
        new api.ApiRequestError("That regeneration batch is no longer available.", {
          status: 404,
          code: "NOTE_BULK_REGENERATION_BATCH_NOT_FOUND",
        }),
      );

      render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);
      const start = await screen.findByRole("button", { name: /Regenerate 3 notes/i });
      await act(async () => {
        fireEvent.click(start);
      });

      // Discriminating: the curator is told WHY the view reset, using the backend's own sentence.
      expect(await screen.findByText(/no longer available/i)).toBeInTheDocument();
      // The stuck state is exactly this: the control that starts a new batch must be reachable again.
      expect(screen.getByRole("button", { name: /Regenerate 3 notes/i })).toBeInTheDocument();
      expect(globalThis.sessionStorage?.getItem(ACTIVE_BATCH_STORAGE_KEY)).toBeNull();

      const callsAfterFirst404 = api.getBulkRegenerationReceipt.mock.calls.length;
      await act(async () => {
        jest.advanceTimersByTime(5_000);
      });
      // The poll must actually have STOPPED, not merely be showing preflight while still ticking.
      expect(api.getBulkRegenerationReceipt).toHaveBeenCalledTimes(callsAfterFirst404);
    } finally {
      jest.useRealTimers();
    }
  });

  it("swallows a non-404 receipt failure and keeps polling on the next tick", async () => {
    jest.useFakeTimers();
    try {
      api.bulkRegenerateNotes.mockResolvedValue({ batchId: "batch-1", scope: "STUDY_PACK", acceptedCount: 3 });
      const finishedReceipt = {
        batchId: "batch-1",
        scope: "STUDY_PACK",
        totalCount: 3,
        regeneratedCount: 3,
        blockedCount: 0,
        failedCount: 0,
        notRunCount: 0,
        pendingCount: 0,
        finished: true,
        stale: false,
        retryableNoteIds: [],
        items: [],
      };
      // A fixture that mocks only a SUCCESSFUL receipt passes under both the defect and the fix and
      // proves nothing -- the first tick must actually reject, and transiently, before recovering.
      api.getBulkRegenerationReceipt
        .mockRejectedValueOnce(new Error("network blip"))
        .mockResolvedValueOnce(finishedReceipt);

      render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);
      const start = await screen.findByRole("button", { name: /Regenerate 3 notes/i });
      await act(async () => {
        fireEvent.click(start);
      });

      await waitFor(() => {
        expect(api.getBulkRegenerationReceipt).toHaveBeenCalledTimes(1);
      });
      // Still on the progress view -- a transient failure must not bounce the curator back to preflight.
      expect(screen.queryByRole("button", { name: /Regenerate 3 notes/i })).not.toBeInTheDocument();

      await act(async () => {
        jest.advanceTimersByTime(3_000);
      });

      expect(api.getBulkRegenerationReceipt).toHaveBeenCalledTimes(2);
      expect(await screen.findByText(/Finished · 3 of 3 regenerated/i)).toBeInTheDocument();
    } finally {
      jest.useRealTimers();
    }
  });

  it("opens straight into the progress view when a batch id is already in storage on mount", async () => {
    globalThis.sessionStorage?.setItem(ACTIVE_BATCH_STORAGE_KEY, "batch-1");
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
      stale: false,
      retryableNoteIds: [],
      items: [],
    });

    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);

    expect(await screen.findByText(/1 of 3 done/i)).toBeInTheDocument();
    // The preflight-only start control must NOT be reachable -- a stored batch id means this mount
    // is resuming a receipt, never re-offering to run the same selection again.
    expect(screen.queryByRole("button", { name: /Regenerate 3 notes/i })).not.toBeInTheDocument();
  });

  it("lets the curator start a new batch independent of the poll, and the next mount opens on preflight", async () => {
    globalThis.sessionStorage?.setItem(ACTIVE_BATCH_STORAGE_KEY, "batch-1");
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
      stale: false,
      retryableNoteIds: [],
      items: [],
    });

    const { unmount } = render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);
    await screen.findByText(/1 of 3 done/i);

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: /Start a new batch/i }));
    });

    expect(globalThis.sessionStorage?.getItem(ACTIVE_BATCH_STORAGE_KEY)).toBeNull();
    expect(await screen.findByRole("button", { name: /Regenerate 3 notes/i })).toBeInTheDocument();

    unmount();
    render(<BulkRegenerateModal isOpen noteIds={NOTE_IDS} onClose={jest.fn()} />);
    expect(await screen.findByRole("button", { name: /Regenerate 3 notes/i })).toBeInTheDocument();
  });
});
