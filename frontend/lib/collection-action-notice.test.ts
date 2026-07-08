import { getCollectionActionNotice, setCollectionActionNotice } from "./collection-action-notice";

describe("collection action notice", () => {
  const originalSessionStorage = globalThis.sessionStorage;

  beforeEach(() => {
    Object.defineProperty(globalThis, "sessionStorage", {
      configurable: true,
      value: originalSessionStorage,
    });
    globalThis.sessionStorage.clear();
  });

  afterAll(() => {
    Object.defineProperty(globalThis, "sessionStorage", {
      configurable: true,
      value: originalSessionStorage,
    });
  });

  it("returns the message once after being set and then clears it", () => {
    setCollectionActionNotice("Review Set deleted.");

    expect(getCollectionActionNotice()).toBe("Review Set deleted.");
    expect(getCollectionActionNotice()).toBeNull();
  });

  it("returns null when no notice is set", () => {
    expect(getCollectionActionNotice()).toBeNull();
  });

  it("degrades to null when storage throws", () => {
    Object.defineProperty(globalThis, "sessionStorage", {
      configurable: true,
      value: {
        getItem: () => {
          throw new Error("storage unavailable");
        },
        removeItem: () => {
          throw new Error("storage unavailable");
        },
        setItem: () => {
          throw new Error("storage unavailable");
        },
      },
    });

    expect(() => setCollectionActionNotice("Review Set deleted.")).not.toThrow();
    expect(getCollectionActionNotice()).toBeNull();
  });
});
