import { createProgramFamily, listProgramFamilies } from "./api";

/**
 * ⚠️ THE ONE TEST THAT EXECUTES THE PROGRAM-FAMILY ENDPOINTS' OWN REQUEST SHAPE.
 *
 * `admin-course-program-catalog-section.test.tsx` mocks `@/lib/api` wholesale, so nothing there runs
 * `buildAuthHeaders`, `fetchWithAuth`, or these URL strings — and the backend's own controller test for
 * this class reflects on annotations rather than issuing requests. Both halves can therefore be green
 * while disagreeing about the path or the headers, which is exactly how `v0.119.0` shipped a feature
 * whose JSON POSTs carried no `Content-Type` and were rejected with 415 before the controller ran.
 *
 * ⚠️ So the paths are asserted LITERALLY and must stay byte-identical to the backend's
 * `@GetMapping("/families")` / `@PostMapping("/families")` under `@RequestMapping("/course-program-catalog")`.
 */
describe("Program Family API", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const stubOk = (payload: unknown) => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);
  };

  it("reads families with one GET at the path the backend maps", async () => {
    const families = [{ id: "family-health", name: "Health Sciences" }];
    stubOk(families);

    await expect(listProgramFamilies()).resolves.toEqual(families);

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/course-program-catalog/families",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("creates a family with a POST that actually carries Content-Type and a JSON body", async () => {
    const created = { id: "family-health", name: "Health Sciences" };
    stubOk(created);

    await expect(createProgramFamily("Health Sciences")).resolves.toEqual(created);

    const [url, init] = (globalThis.fetch as jest.Mock).mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/course-program-catalog/families");
    expect(init.method).toBe("POST");
    // ⚠️ The v0.119.0 assertion. Without this header Spring answers 415 before the controller runs.
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
    // The backend binds a record with a single `name` field; the shape must match it exactly.
    expect(JSON.parse(init.body as string)).toEqual({ name: "Health Sciences" });
  });

  it("surfaces a readable duplicate failure rather than resolving silently", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 409,
      // ⚠️ The real backend error envelope nests under `error` — a flat fixture would make this test
      // pass against a client that ignored the message entirely.
      json: jest.fn().mockResolvedValue({
        error: {
          code: "PROGRAM_FAMILY_NAME_CONFLICT",
          message: 'A Program Family named "Engineering" already exists.',
          details: "Engineering",
        },
      }),
    } as unknown as Response);

    await expect(createProgramFamily("engineering")).rejects.toThrow("already exists");
  });
});
