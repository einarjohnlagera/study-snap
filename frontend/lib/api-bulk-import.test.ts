import { importNotesBatch } from "./api";

describe("importNotesBatch", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    jest.restoreAllMocks();
  });

  it("posts every file under the repeated files field and parses the result", async () => {
    const payload = {
      created: [
        {
          noteId: "note-1",
          title: "Lecture One",
          fileName: "lecture-one.pdf",
          lowConfidence: false,
        },
      ],
      failed: [
        {
          fileName: "blank.txt",
          errorCode: "EMPTY_TEXT",
          message: "No readable text was found.",
        },
      ],
    };
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue(payload),
    } as unknown as Response);
    globalThis.fetch = fetchMock;
    const files = [
      new File(["lecture"], "lecture-one.pdf", { type: "application/pdf" }),
      new File([""], "blank.txt", { type: "text/plain" }),
    ];

    await expect(importNotesBatch(files)).resolves.toEqual(payload);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/notes/import-batch");
    expect(init.method).toBe("POST");
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).getAll("files")).toEqual(files);
    expect(new Headers(init.headers).has("Content-Type")).toBe(false);
  });

  it("surfaces the backend batch validation message", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: jest.fn().mockResolvedValue({
        error: {
          code: "INVALID_BULK_IMPORT_REQUEST",
          message: "You can import up to 20 files at once.",
        },
      }),
    } as unknown as Response);

    await expect(importNotesBatch([new File(["note"], "note.txt")])).rejects.toMatchObject({
      message: "You can import up to 20 files at once.",
      status: 400,
    });
  });
});
