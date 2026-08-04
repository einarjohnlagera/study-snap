import type { DomainContext, LearnerLevel, NoteTargetProfileType } from "@/lib/api";

const BULK_QUEUED_FLASH_KEY = "notelib.bulk.queuedFlash";
const BULK_RETRY_STASH_KEY = "notelib.bulk.retryTopics";

// One-shot flash passed from the bulk-generate page to the Library after redirect.
// sessionStorage (not a query param) because the Library rewrites its own URL from
// filter state, which would strip a param before it could be read.

export type BulkQueuedFlash = {
  queuedCount: number;
  resultId: string | null;
};

export type BulkGenerationRetryStash = {
  subject: string;
  courseProgram: string | null;
  domainContext: DomainContext | null;
  learnerLevel: LearnerLevel | null;
  targetProfileType: NoteTargetProfileType;
  makePublic: boolean;
  topics: string[];
};

export function setBulkQueuedFlash(count: number, resultId: string | null = null): void {
  try {
    globalThis.sessionStorage?.setItem(
      BULK_QUEUED_FLASH_KEY,
      JSON.stringify({ queuedCount: count, resultId }),
    );
  } catch {
    // sessionStorage may be unavailable (private mode, SSR) — the toast is non-critical.
  }
}

export function consumeBulkQueuedFlash(): BulkQueuedFlash | null {
  try {
    const raw = globalThis.sessionStorage?.getItem(BULK_QUEUED_FLASH_KEY);
    if (!raw) {
      return null;
    }
    globalThis.sessionStorage.removeItem(BULK_QUEUED_FLASH_KEY);
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed === "number") {
      return Number.isFinite(parsed) && parsed > 0 ? { queuedCount: parsed, resultId: null } : null;
    }
    if (!parsed || typeof parsed !== "object") {
      return null;
    }
    const queuedCount = Number((parsed as { queuedCount?: unknown }).queuedCount);
    const resultId = (parsed as { resultId?: unknown }).resultId;
    return Number.isFinite(queuedCount) && queuedCount > 0
      ? { queuedCount, resultId: typeof resultId === "string" && resultId ? resultId : null }
      : null;
  } catch {
    try {
      const raw = globalThis.sessionStorage?.getItem(BULK_QUEUED_FLASH_KEY);
      globalThis.sessionStorage?.removeItem(BULK_QUEUED_FLASH_KEY);
      const count = Number(raw);
      return Number.isFinite(count) && count > 0 ? { queuedCount: count, resultId: null } : null;
    } catch {
      return null;
    }
  }
}

export function setBulkGenerationRetryStash(stash: BulkGenerationRetryStash): void {
  try {
    globalThis.sessionStorage?.setItem(BULK_RETRY_STASH_KEY, JSON.stringify(stash));
  } catch {
    // Retry prefill is helpful but not critical.
  }
}

export function consumeBulkGenerationRetryStash(): BulkGenerationRetryStash | null {
  try {
    const raw = globalThis.sessionStorage?.getItem(BULK_RETRY_STASH_KEY);
    if (!raw) {
      return null;
    }
    globalThis.sessionStorage.removeItem(BULK_RETRY_STASH_KEY);
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") {
      return null;
    }
    const stash = parsed as Partial<BulkGenerationRetryStash>;
    const topics = Array.isArray(stash.topics)
      ? stash.topics.filter((topic): topic is string => typeof topic === "string" && topic.trim().length > 0)
      : [];
    if (
      typeof stash.subject !== "string"
      || typeof stash.targetProfileType !== "string"
      || typeof stash.makePublic !== "boolean"
      || topics.length === 0
    ) {
      return null;
    }
    return {
      subject: stash.subject,
      courseProgram: typeof stash.courseProgram === "string" ? stash.courseProgram : null,
      domainContext: typeof stash.domainContext === "string" ? stash.domainContext as DomainContext : null,
      learnerLevel: typeof stash.learnerLevel === "string" ? stash.learnerLevel as LearnerLevel : null,
      targetProfileType: stash.targetProfileType as NoteTargetProfileType,
      makePublic: stash.makePublic,
      topics,
    };
  } catch {
    return null;
  }
}
