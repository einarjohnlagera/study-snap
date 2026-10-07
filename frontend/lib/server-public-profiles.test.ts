import { getServerPublicCreatorProfile, getServerPublicProfile } from "./server-public-profiles";

describe("server public profile reads", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);
  });
  afterEach(() => { globalThis.fetch = originalFetch; });

  it("caches both full and full-corpus Learning Focus reads for 300 seconds", async () => {
    await getServerPublicProfile("owner-id");
    await getServerPublicCreatorProfile("creator");
    expect(globalThis.fetch).toHaveBeenNthCalledWith(1,
      "http://localhost:8080/api/public/profile/owner-id",
      { method: "GET", next: { revalidate: 300 } });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(2,
      "http://localhost:8080/api/public/profile/owner-id/learning-focus",
      { method: "GET", next: { revalidate: 300 } });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(3,
      "http://localhost:8080/api/public/creator/creator",
      { method: "GET", next: { revalidate: 300 } });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(4,
      "http://localhost:8080/api/public/creator/creator/learning-focus",
      { method: "GET", next: { revalidate: 300 } });
  });
});
