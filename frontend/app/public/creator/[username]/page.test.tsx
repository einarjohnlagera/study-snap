import PublicCreatorPage, { generateMetadata } from "./page";
import { getServerPublicCreatorProfile } from "@/lib/server-public-profiles";

jest.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ back: jest.fn(), push: jest.fn() }),
}));

jest.mock("@/lib/auth", () => ({ getAuthUser: () => null }));

jest.mock("@/lib/server-public-profiles", () => ({
  getServerPublicCreatorProfile: jest.fn(),
}));

const params = (username: string) => ({ params: Promise.resolve({ username }) });

describe("PublicCreatorPage metadata and failure behaviour", () => {
  beforeEach(() => {
    (getServerPublicCreatorProfile as jest.Mock).mockReset();
  });

  it("returns the static unavailable title and noindex when the profile read fails, with no profile data", async () => {
    (getServerPublicCreatorProfile as jest.Mock).mockRejectedValue(new Error("upstream down"));

    const metadata = await generateMetadata(params("alice"));

    expect(metadata).toEqual({ title: "Profile temporarily unavailable | NoteLib", robots: { index: false } });
    expect(JSON.stringify(metadata)).not.toContain("upstream down");
  });

  it("keeps the ok, private and not-found metadata unchanged", async () => {
    (getServerPublicCreatorProfile as jest.Mock).mockResolvedValueOnce({
      status: "ok",
      profile: { displayName: "Alice", username: "alice", publicNotesCount: 1, totalCopies: 2 },
    });
    await expect(generateMetadata(params("alice"))).resolves.toMatchObject({
      title: "Alice (@alice) | NoteLib Public Profile",
      description: "Alice has shared 1 public note on NoteLib with 2 total copies.",
      alternates: { canonical: "https://www.notelib.app/public/creator/alice" },
      robots: { index: false, follow: true },
    });
    (getServerPublicCreatorProfile as jest.Mock).mockResolvedValueOnce({ status: "private" });
    await expect(generateMetadata(params("bob"))).resolves.toMatchObject({
      title: "This Profile Is Private | NoteLib", robots: { index: false, follow: false },
    });
    (getServerPublicCreatorProfile as jest.Mock).mockResolvedValueOnce({ status: "not_found" });
    await expect(generateMetadata(params("nobody"))).resolves.toMatchObject({
      title: "Public Creator Not Found | NoteLib", robots: { index: false, follow: false },
    });
  });

  it("still lets the page body fail so the route's error boundary renders", async () => {
    (getServerPublicCreatorProfile as jest.Mock).mockRejectedValue(new Error("upstream down"));

    await expect(PublicCreatorPage(params("alice"))).rejects.toThrow("upstream down");
  });
});
