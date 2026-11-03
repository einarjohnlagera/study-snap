// THROW-AWAY verification harness. Lives only on branch verify/v0179-cache and is NEVER merged.
// A fake catalog API whose state is a PURE FUNCTION OF THE WALL CLOCK, so every serverless instance agrees.
// All offsets are multiples of u (the page-class TTL, 120 s at scale 1) so a scaled local dry run is faithful.
import type { NoteListItemResponse, NoteCollectionSummary, PublicNoteDetailResponse } from "@/lib/api";

const scaleRaw = Number(process.env.PREVIEW_VERIFY_SCALE ?? "1");
export const SCALE = Number.isFinite(scaleRaw) && scaleRaw > 0 ? scaleRaw : 1;
export const U_MS = Math.round(120_000 * SCALE);
export const PERIOD_U = 15; // period = 15u (30 min at scale 1)
export const WITHDRAW_U = 4; // marker withdrawn, edit applied, plan withdrawn
export const FAIL_START_U = 10; // list and plan endpoints return 503
export const FAIL_END_U = 11;

type Shared = { instance: string; seq: Record<string, number>; calls: Record<string, number>; blocked: string[]; passthrough: number; installed: boolean };
const g = globalThis as unknown as { __pv?: Shared };
g.__pv ??= {
  instance: globalThis.crypto.randomUUID().replace(/-/g, "").slice(0, 6),
  seq: {}, calls: {}, blocked: [], passthrough: 0, installed: false,
};
export const shared: Shared = g.__pv;

export function schedule(now = Date.now()) {
  const periodMs = PERIOD_U * U_MS;
  const periodStart = Math.floor(now / periodMs) * periodMs;
  const tWithdraw = periodStart + WITHDRAW_U * U_MS;
  const failStart = periodStart + FAIL_START_U * U_MS;
  const failEnd = periodStart + FAIL_END_U * U_MS;
  return { now, u: U_MS, periodMs, periodStart, tWithdraw, failStart, failEnd,
    withdrawn: now >= tWithdraw, failing: now >= failStart && now < failEnd };
}

const SUBJECTS = ["Verify Alpha", "Verify Beta", "Verify Gamma", "Verify Delta"];
const PROGRAM = "Verify Program A";
const slugify = (v: string) => v.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
const uuid = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const abortError = () => new DOMException("The operation was aborted.", "AbortError");
/** Abort-aware sleep: a request whose signal aborts stops being answered, like a real upstream the client gave up on. */
const sleep = (ms: number, signal?: AbortSignal | null) => new Promise<void>((resolve, reject) => {
  if (signal?.aborted) { reject(abortError()); return; }
  const onAbort = () => { clearTimeout(timer); shared.calls.aborted = (shared.calls.aborted ?? 0) + 1; reject(abortError()); };
  const timer = setTimeout(() => { signal?.removeEventListener("abort", onAbort); resolve(); }, ms);
  signal?.addEventListener("abort", onAbort, { once: true });
});
/** Each page of the dedicated slow subject takes this long (NOT scaled: the deadlines under test are fixed constants). */
export const SLOW_PAGE_MS = 6_000;
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

function note(id: number, subject: string, title: string, preview: string, program: string | null, createdAt: string): NoteListItemResponse {
  return {
    id: uuid(id), ownerUserId: null, title, courseProgram: program, applicablePrograms: program ? [program] : [],
    subject, tags: [], contentPreview: preview, summaryPreview: "", visibility: "PUBLIC", studyPackId: null,
    studyPackStatus: "STUDY_PACK_READY", studyPackDone: true, quizCount: 0, keyConceptCount: 0, copyCount: 0,
    likeCount: 0, shareCount: 0, viewCount: 0, authorDisplayName: "Verify Author", authorUsername: null,
    isOfficialAuthor: false, isCurrentUser: false, createdAt, updatedAt: createdAt, likedByCurrentUser: false,
  };
}

// Static catalog: Gamma is large enough that its own walk and the whole catalog span several 50-note pages.
const STATIC: NoteListItemResponse[] = [];
SUBJECTS.forEach((label, s) => {
  const count = s === 2 ? 79 : 9;
  for (let i = 0; i < count; i += 1) {
    const created = new Date(Date.UTC(2030, 0, 1) - (s * 100 + i) * 3_600_000).toISOString();
    STATIC.push(note(s * 1000 + i, label, `${label} Note ${i}`, `Body text ${s}-${i}`, s === 1 ? PROGRAM : null, created));
  }
});
STATIC.push(note(5001, "Verify Beta", "Anchor Note Stable", "Anchor body", PROGRAM, "2029-12-31T00:00:00.000Z"));

function dynamicNotes(withdrawn: boolean): NoteListItemResponse[] {
  const out: NoteListItemResponse[] = [];
  if (!withdrawn) out.push(note(9001, "Verify Beta", "Withdrawal Marker Alpha", "MARKER-WITHDRAWABLE-TEXT", PROGRAM, "2031-01-03T00:00:00.000Z"));
  if (!withdrawn) out.push(note(9004, "Verify Alpha", "Withdrawal Marker Beta", "MARKER-WITHDRAWABLE-TEXT", null, "2031-01-03T00:00:00.000Z"));
  out.push(note(9002, "Verify Beta", withdrawn ? "Edit Probe New Title" : "Edit Probe Old Title",
    withdrawn ? "EDIT-NEW-TEXT" : "EDIT-OLD-TEXT", PROGRAM, "2031-01-02T00:00:00.000Z"));
  return out;
}

function list(url: URL): Response {
  const st = schedule();
  if (st.failing) return json({ error: "stub failure window" }, 503);
  const sp = url.searchParams;
  const subjectSlug = sp.get("subject") ? slugify(sp.get("subject") as string) : null;
  const programSlug = sp.get("courseProgram") ? slugify(sp.get("courseProgram") as string) : null;
  const page = Math.max(0, Number(sp.get("page") ?? "0") || 0);
  const size = Math.min(50, Math.max(1, Number(sp.get("pageSize") ?? "20") || 20));
  // ---- driver-triggered special subjects (not clock-driven); their notes carry "Strict Probe Note" so any leak is visible ----
  const strict = (p: number, i: number, tag: string) => note(20000 + p * 100 + i, `Verify ${tag}`,
    `Strict Probe Note ${tag} ${p}-${i}`, "strict body", null, new Date(Date.UTC(2031, 0, 1) - (p * 100 + i) * 60_000).toISOString());
  const strictPage = (tag: string) => Array.from({ length: size }, (_, i) => strict(page, i, tag));
  if (subjectSlug === "verify-nohasmore") {
    return json({ items: [strict(page, 0, "NoFlag")], total: 1, page, pageSize: size, totalMatching: 1 }); // hasMore omitted
  }
  if (subjectSlug === "verify-emptymore") {
    return json({ items: [], total: 0, page, pageSize: size, totalMatching: 0, hasMore: true });
  }
  if (subjectSlug === "verify-empty") {
    return json({ items: [], total: 0, page, pageSize: size, totalMatching: 0, hasMore: false });
  }
  if (subjectSlug === "verify-endless") {
    return json({ items: strictPage("Endless"), total: 999_999, page, pageSize: size, totalMatching: 999_999, hasMore: true });
  }
  if (subjectSlug === "verify-epsilon") {
    const pages = 20;
    return json({ items: strictPage("Epsilon"), total: pages * size, page, pageSize: size, totalMatching: pages * size, hasMore: page + 1 < pages });
  }
  const key = `${subjectSlug ?? "all"}|${programSlug ?? "-"}`;
  if (page === 0) shared.seq[key] = (shared.seq[key] ?? 0) + 1;
  const tag = subjectSlug ? `s-${subjectSlug}` : programSlug ? `p-${programSlug}` : "all";
  const probeSubject = SUBJECTS.find((label) => slugify(label) === subjectSlug) ?? "Verify Alpha";
  const probe = note(9003, probeSubject, `Probe ${shared.instance} ${shared.seq[key] ?? 1} ${tag}`, "probe body",
    programSlug ? PROGRAM : null, "2031-01-04T00:00:00.000Z");
  const all = [probe, ...dynamicNotes(st.withdrawn), ...STATIC].filter((n) =>
    (!subjectSlug || slugify(n.subject ?? "") === subjectSlug) &&
    (!programSlug || (n.applicablePrograms ?? []).some((p) => slugify(p) === programSlug)));
  all.sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : a.id < b.id ? -1 : 1));
  const items = all.slice(page * size, page * size + size);
  return json({ items, total: all.length, page, pageSize: size, totalMatching: all.length, hasMore: (page + 1) * size < all.length });
}

function detail(subject: string, slug: string): Response {
  if (slugify(subject) !== "verify-beta" || slug !== "anchor-note-stable") return json({ error: "not found" }, 404);
  const body: PublicNoteDetailResponse = {
    id: uuid(5001), ownerUserId: null, title: "Anchor Note Stable", subject: "Verify Beta", coursePrograms: [PROGRAM],
    tags: [], content: "Anchor content", contentPreview: "Anchor body", studyPackStatus: "STUDY_PACK_READY",
    studyPackDone: true, summary: "Anchor summary", keyConcepts: [], quiz: [], authorDisplayName: "Verify Author",
    authorUsername: null, isOfficialAuthor: false, isCurrentUser: false, updatedAt: "2029-12-31T00:00:00.000Z",
  };
  return json(body);
}

function plans(): Response {
  const st = schedule();
  if (st.failing) return json({ error: "stub failure window" }, 503);
  const plan = (id: number, title: string): NoteCollectionSummary => ({
    id: uuid(id), title, description: null, visibility: "PUBLIC", courseProgram: PROGRAM, sourcePlanId: null,
    parentCollectionId: null, itemCount: 3, readyCount: 3, childCount: 0, notesPracticed: 0,
    createdAt: "2030-01-01T00:00:00.000Z", updatedAt: "2030-01-01T00:00:00.000Z",
  });
  const out = [plan(7001, "Stable Plan")];
  if (!st.withdrawn) out.push(plan(7002, "Marker Plan"));
  return json(out);
}

/** Returns a Response for a stubbed API path, or null when the URL is not an API path this stub owns. */
export async function stubFetch(url: URL, signal?: AbortSignal | null): Promise<Response | null> {
  const path = url.pathname.replace(/^\/api(?=\/)/, "");
  let route: string | null = null;
  let make: (() => Response) | null = null;
  if (path === "/notes/public") { route = "list"; make = () => list(url); }
  else if (path.startsWith("/notes/public/seo/")) {
    const [, , , , subject, slug] = path.split("/");
    route = "detail"; make = () => detail(decodeURIComponent(subject ?? ""), decodeURIComponent(slug ?? ""));
  } else if (path === "/collections/public") { route = "plans"; make = plans; }
  else if (path === "/public/exam-goals/course-programs") { route = "mapping"; make = () => json({ ale: [PROGRAM] }); }
  else if (path === "/subjects") { route = "subjects"; make = () => json(SUBJECTS); }
  if (!route || !make) return null;
  shared.calls[route] = (shared.calls[route] ?? 0) + 1;
  const slow = route === "list" && url.searchParams.get("subject") === "verify-epsilon";
  if (slow) shared.calls["slow-page"] = (shared.calls["slow-page"] ?? 0) + 1;
  await sleep(slow ? SLOW_PAGE_MS : Math.max(5, Math.round(100 * SCALE)), signal);
  return make();
}
