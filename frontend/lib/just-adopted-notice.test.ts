import { getJustAdoptedNotice, setJustAdoptedNotice } from "./just-adopted-notice";

describe("just-adopted notice", () => {
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

  it("returns true once after being set and then clears the flag", () => {
    setJustAdoptedNotice("collection-1");

    expect(getJustAdoptedNotice("collection-1")).toBe(true);
    expect(getJustAdoptedNotice("collection-1")).toBe(false);
  });

  it("returns false when no flag is set", () => {
    expect(getJustAdoptedNotice("collection-1")).toBe(false);
  });

  it("degrades to false when storage throws", () => {
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

    expect(() => setJustAdoptedNotice("collection-1")).not.toThrow();
    expect(getJustAdoptedNotice("collection-1")).toBe(false);
  });
});
