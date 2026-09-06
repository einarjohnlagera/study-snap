import { getGoalChildItems } from "./api";

/**
 * ⚠️ THE ONE TEST THAT EXECUTES THE CLIENT'S OWN REQUEST SHAPE.
 *
 * The builder's component tests mock `@/lib/api` wholesale, so nothing there invokes the real
 * `getGoalChildItems` — its URL, its method, its parsing. That is the `v0.119.0` blind spot on the
 * client side: a backend MockMvc test pins `/collections/{id}/goal/child-items` and a component test
 * pins that the builder calls the function, and BOTH stay green while the two halves disagree about
 * the path and the feature 404s in production.
 *
 * ⚠️ So the URL is asserted here LITERALLY. It must stay byte-identical to the backend's
 * `@GetMapping("/{id}/goal/child-items")`.
 */
describe("goal child items API", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("reads the batch endpoint with one GET at the path the backend maps", async () => {
    const payload = [
      { collectionId: "child-1", items: [] },
      { collectionId: "child-2", items: [] },
    ];
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);

    await expect(getGoalChildItems("goal-1")).resolves.toEqual(payload);

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/collections/goal-1/goal/child-items",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("surfaces a readable error rather than an empty plan when the read fails", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);

    await expect(getGoalChildItems("goal-1")).rejects.toThrow("Could not load goal details.");
  });
});
