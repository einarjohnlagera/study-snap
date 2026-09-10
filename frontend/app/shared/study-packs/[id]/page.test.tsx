import { render, waitFor } from "@testing-library/react";
import SharedStudyPackPage from "./page";
import { getSharedStudyPack } from "@/lib/api";

// ⚠️ requireActual, because a jest.mock factory is an ALLOW-LIST. `@/lib/api` also exports
// ApiRequestError, which this page uses in an `instanceof` check — listing only getSharedStudyPack
// would blank the class and turn a 404 branch into a TypeError.
jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getSharedStudyPack: jest.fn(),
}));

// ⚠️ THE ROUTER AND PARAMS OBJECTS MUST BE STABLE ACROSS RENDERS. Returning a fresh object from the
// factory changes `router`'s identity every render, which re-creates the `loadStudyPack` useCallback,
// which re-fires the effect that depends on it — an endless load loop that returns the page to its
// `loading` branch on every frame. The symptom is a container holding only the BackLink, and it looks
// exactly like "the content never rendered".
const mockRouter = { push: jest.fn(), replace: jest.fn() };
const mockParams = { id: "pack-1" };

jest.mock("next/navigation", () => ({
  useParams: () => mockParams,
  useRouter: () => mockRouter,
}));

jest.mock("@/lib/route-guards", () => ({
  requireVerifiedOnboardedUser: () => true,
}));

const pack = (over: Partial<Record<string, unknown>> = {}) => ({
  id: "pack-1",
  title: "Weir Flow",
  ownerDisplayName: "A curator",
  summary: "No summary yet.",
  keyConcepts: [] as string[],
  fullNotes: null as string | null,
  noteId: "note-1",
  quiz: [] as unknown[],
  ...over,
});

beforeEach(() => {
  // ⚠️ mockReset, not clearAllMocks. clearAllMocks wipes recorded calls but LEAVES the resolved value
  // from the previous test in place, so a later test that re-mocks still races the stale promise.
  (getSharedStudyPack as jest.Mock).mockReset();
});

// ⚠️ v0.141.0. This page rendered summary, keyConcepts and fullNotes as raw `{value}` inside a
// `whitespace-pre-wrap` container — NO math rendering of any kind, while every sibling surface
// (study-pack results, the public library page, demo) already used SummaryMarkdown. 372 production
// summaries carry a backslash.
//
// ⚠️ This route's traffic is small and the release says so rather than overselling it: ONE
// linked-learner relationship and SIX share events in 90 days, and `share_token` is 0 across all
// 7,573 packs. It is fixed because it is the same defect class, not because it is urgent — and it
// is tested because a behaviour change with no test that runs it is the repo's recorded red flag.
it("renders maths in the summary instead of printing the LaTeX source", async () => {
  (getSharedStudyPack as jest.Mock).mockResolvedValue(
    pack({ summary: "The discharge is $Q = \\frac{2}{3} C_d L$ for a weir." }),
  );

  const { container } = render(<SharedStudyPackPage />);

  // ⚠️ Wait on THIS render's container, not on `screen`. `screen` queries document.body, so a
  // stale render from a previous test satisfies it while this one is still loading — which made
  // two of these tests assert against an empty container that had only the BackLink in it.
  // ⚠️ Wait on the ASSERTION, not on an intermediate state. This page runs a second load cycle that
  // briefly returns it to `loading`, which renders only the BackLink — so waiting for the title and
  // then asserting immediately caught the blank frame. waitFor retries until the content settles.
  await waitFor(() => expect(container.querySelector(".katex")).not.toBeNull());
  expect(container.querySelector(".katex-html")?.textContent ?? "").not.toContain("\\frac");
  expect(container.textContent ?? "").toContain("for a weir");
});

it("renders maths in key concepts and full notes", async () => {
  (getSharedStudyPack as jest.Mock).mockResolvedValue(
    pack({
      keyConcepts: ["Area is $\\frac{1}{2}bh$"],
      fullNotes: "Then $E = mc^2$ follows.",
    }),
  );

  const { container } = render(<SharedStudyPackPage />);

  // ⚠️ Wait on THIS render's container, not on `screen`. `screen` queries document.body, so a
  // stale render from a previous test satisfies it while this one is still loading — which made
  // two of these tests assert against an empty container that had only the BackLink in it.
  await waitFor(() => expect(container.querySelectorAll(".katex").length).toBeGreaterThanOrEqual(2));
  const visual = container.querySelector(".katex-html")?.textContent ?? "";
  expect(visual).not.toContain("\\frac");
  expect(container.textContent ?? "").toContain("follows.");
});

// The counterpart: a pack with no maths must render as ordinary prose, unchanged.
it("leaves a summary without maths completely alone", async () => {
  (getSharedStudyPack as jest.Mock).mockResolvedValue(
    pack({ summary: "Weirs measure open-channel flow." }),
  );

  const { container } = render(<SharedStudyPackPage />);

  // ⚠️ Wait on THIS render's container, not on `screen`. `screen` queries document.body, so a
  // stale render from a previous test satisfies it while this one is still loading — which made
  // two of these tests assert against an empty container that had only the BackLink in it.
  // ⚠️ A negative assertion needs a POSITIVE settle signal, or it passes on the blank loading frame
  // and proves nothing. Wait for the prose, then assert no math was produced from it.
  await waitFor(() => expect(container.textContent ?? "").toContain("Weirs measure open-channel flow."));
  expect(container.querySelector(".katex")).toBeNull();
  expect(container.textContent ?? "").toContain("Weirs measure open-channel flow.");
});
