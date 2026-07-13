import { getPublicStudyPlanDetail } from "./api";

describe("getPublicStudyPlanDetail", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("loads the public detail endpoint without authentication headers", async () => {
    const payload = { id: "public-plan-1", items: [] };
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);
    globalThis.fetch = fetchMock;

    await expect(getPublicStudyPlanDetail("public-plan-1")).resolves.toEqual(payload);

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/collections/public/public-plan-1",
      { method: "GET" },
    );
  });
});
