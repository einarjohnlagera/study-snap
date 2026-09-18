import { generateNoteFromTopic } from "./api";

describe("topic note generation API request shape", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  function mockSuccessfulFetch() {
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({ content: "Generated note" }),
    } as unknown as Response);
    globalThis.fetch = fetchMock;
    return fetchMock;
  }

  it("includes a trimmed subject in the generate-from-topic JSON body", async () => {
    const fetchMock = mockSuccessfulFetch();

    await generateNoteFromTopic(
      "Dosage Calculations",
      "Nursing",
      "NURSING",
      ["program-nursing"],
      "  Pharmacology  ",
    );

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/notes/generate");
    expect(JSON.parse(init.body as string)).toEqual({
      topic: "Dosage Calculations",
      courseProgramText: "Nursing",
      courseProgramIds: ["program-nursing"],
      domainContext: "NURSING",
      subject: "Pharmacology",
    });
  });

  it("omits a blank subject from the generate-from-topic JSON body", async () => {
    const fetchMock = mockSuccessfulFetch();

    await generateNoteFromTopic("Newton's Laws", "Engineering", undefined, undefined, "   ");

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({
      topic: "Newton's Laws",
      courseProgramText: "Engineering",
    });
  });
});
